package com.coolskyai.util.quartz;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.quartz.CronScheduleBuilder;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.quartz.impl.StdSchedulerFactory;


import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CP-BACKEND-01：验证「同一个 JobKey 不并发、不同 JobKey 可并行」这个前提。
 *
 * 不启动 Spring、不连数据库：直接用 RAMJobStore 起一个裸调度器，
 * 复刻 ScheduleUtils 构建 JobDetail 的方式（JobBuilder.newJob(clazz)），
 * 确认注解确实被写进 JobDetail，并且手动 triggerJob 也受同一条约束。
 */
class QuartzConcurrencyTest {

	private static final AtomicInteger RUNNING = new AtomicInteger();
	private static final AtomicInteger MAX_RUNNING = new AtomicInteger();
	private static final AtomicInteger EXECUTIONS = new AtomicInteger();

	private Scheduler scheduler;

	/**
	 * 探针任务：和 ScheduleJob 一样标注解，跑 300ms，记录并发峰值
	 */
	@DisallowConcurrentExecution
	public static class ProbeJob implements Job {
		@Override
		public void execute(JobExecutionContext context) {
			EXECUTIONS.incrementAndGet();
			MAX_RUNNING.accumulateAndGet(RUNNING.incrementAndGet(), Math::max);
			try {
				Thread.sleep(300);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			} finally {
				RUNNING.decrementAndGet();
			}
		}
	}

	@BeforeEach
	void setUp() throws Exception {
		RUNNING.set(0);
		MAX_RUNNING.set(0);
		EXECUTIONS.set(0);

		Properties props = new Properties();
		props.setProperty("org.quartz.scheduler.instanceName", "cp-test-" + UUID.randomUUID());
		props.setProperty("org.quartz.threadPool.class", "org.quartz.simpl.SimpleThreadPool");
		props.setProperty("org.quartz.threadPool.threadCount", "10");
		props.setProperty("org.quartz.jobStore.class", "org.quartz.simpl.RAMJobStore");
		scheduler = new StdSchedulerFactory(props).getScheduler();
		scheduler.start();
	}

	@AfterEach
	void tearDown() throws Exception {
		if (scheduler != null) {
			scheduler.shutdown(true);
		}
	}

	@Test
	@DisplayName("同一个 JobKey 并发触发 5 次，任务串行执行且不重叠")
	void sameJobKeyRunsSerially() throws Exception {
		schedule("TASK_1");
		triggerConcurrently("TASK_1", 5);
		awaitIdle();

		assertThat(MAX_RUNNING.get()).as("同一 JobKey 的最大并发执行数").isEqualTo(1);
		assertThat(EXECUTIONS.get()).as("被真正执行的次数").isGreaterThanOrEqualTo(1);
		// 5 次触发是被排队后依次执行，还是被丢弃，决定了后台文案该怎么写
		System.out.println("[探针] 5 次并发触发，实际执行 " + EXECUTIONS.get() + " 次，最大并发 " + MAX_RUNNING.get());
	}

	@Test
	@DisplayName("不同 JobKey 同时触发，允许并行（注解按 JobKey 生效，不是按类）")
	void differentJobKeysRunInParallel() throws Exception {
		schedule("TASK_1");
		schedule("TASK_2");
		triggerConcurrently("TASK_1", 1);
		triggerConcurrently("TASK_2", 1);
		awaitIdle();

		assertThat(MAX_RUNNING.get()).as("两个不同任务的最大并发执行数").isGreaterThanOrEqualTo(2);
	}

	@Test
	@DisplayName("ScheduleJob 通过 JobBuilder 构建后，并发禁止标记确实落在 JobDetail 上")
	void annotationLandsOnJobDetail() {
		JobDetail detail = JobBuilder.newJob(ScheduleJob.class)
				.withIdentity(JobKey.jobKey("TASK_1")).build();

		// Quartz 2.5 起方法名是 isConcurrentExecutionDisallowed（旧版拼作 Exection）
		assertThat(detail.isConcurrentExecutionDisallowed()).isTrue();
	}

	@Test
	@DisplayName("ScheduleJob 不再持有线程池字段（每次触发都会新建实例，持有线程池等于泄漏线程）")
	void scheduleJobDoesNotHoldThreadPool() {
		Field[] fields = ScheduleJob.class.getDeclaredFields();
		assertThat(Arrays.stream(fields).map(Field::getType))
				.as("ScheduleJob 的字段类型")
				.noneMatch(type -> ExecutorService.class.isAssignableFrom(type));
	}

	// ---------------------------------------------------------------- helpers

	private void schedule(String jobKeyName) throws Exception {
		JobDetail job = JobBuilder.newJob(ProbeJob.class)
				.withIdentity(JobKey.jobKey(jobKeyName)).build();
		CronScheduleBuilder cron = CronScheduleBuilder.cronSchedule("0 0 3 * * ?");
		scheduler.scheduleJob(job, TriggerBuilder.newTrigger()
				.withIdentity(TriggerKey.triggerKey(jobKeyName))
				.withSchedule(cron).build());
	}

	private void triggerConcurrently(String jobKeyName, int times) throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(times);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<?>> futures = new ArrayList<>();
		for (int i = 0; i < times; i++) {
			futures.add(pool.submit(() -> {
				start.await();
				scheduler.triggerJob(JobKey.jobKey(jobKeyName));
				return null;
			}));
		}
		start.countDown();
		for (Future<?> future : futures) {
			future.get(10, TimeUnit.SECONDS);
		}
		pool.shutdown();
	}

	/**
	 * 等到没有任务在跑，且连续两次采样执行数不变（防止「排队中的触发」还没被消费就断言）
	 */
	private void awaitIdle() throws Exception {
		int lastExecutions = -1;
		for (int i = 0; i < 40; i++) {
			Thread.sleep(150);
			int executions = EXECUTIONS.get();
			if (RUNNING.get() == 0 && executions == lastExecutions) {
				return;
			}
			lastExecutions = executions;
		}
	}
}
