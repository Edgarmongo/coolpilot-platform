package com.coolskyai.util.quartz;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.quartz.Scheduler;
import org.quartz.Trigger;
import org.quartz.impl.StdSchedulerFactory;
import com.coolskyai.entity.ScheduleJob;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 用内存 JobStore 起一个不启动的 Quartz 调度器，只验证「key 命名 + 增删改暂停恢复」。
 * 不 start()：任务不会真正触发，也就不需要 Spring 上下文。
 */
class ScheduleUtilsTest {

	private Scheduler scheduler;

	@BeforeEach
	void setUp() throws Exception {
		Properties props = new Properties();
		props.setProperty("org.quartz.scheduler.instanceName", "schedule-utils-test");
		props.setProperty("org.quartz.threadPool.threadCount", "1");
		props.setProperty("org.quartz.jobStore.class", "org.quartz.simpl.RAMJobStore");
		scheduler = new StdSchedulerFactory(props).getScheduler();
	}

	@AfterEach
	void tearDown() throws Exception {
		scheduler.shutdown(true);
	}

	private ScheduleJob job(long id, String cron, boolean enabled) {
		ScheduleJob job = new ScheduleJob();
		job.setJobId(id);
		job.setCron(cron);
		job.setStatus(enabled);
		job.setBeanName("demoTask");
		job.setMethodName("run");
		return job;
	}

	@Test
	void key命名带TASK前缀() {
		assertThat(ScheduleUtils.getJobKey(7L).getName()).isEqualTo("TASK_7");
		assertThat(ScheduleUtils.getTriggerKey(7L).getName()).isEqualTo("TASK_7");
	}

	@Test
	void 创建启用任务后处于正常状态() throws Exception {
		ScheduleUtils.createScheduleJob(scheduler, job(1L, "0 0 3 * * ?", true));

		assertThat(scheduler.checkExists(ScheduleUtils.getJobKey(1L))).isTrue();
		assertThat(scheduler.getTriggerState(ScheduleUtils.getTriggerKey(1L)))
				.isEqualTo(Trigger.TriggerState.NORMAL);
	}

	@Test
	void 创建停用任务后直接暂停() throws Exception {
		ScheduleUtils.createScheduleJob(scheduler, job(2L, "0 0 3 * * ?", false));

		assertThat(scheduler.getTriggerState(ScheduleUtils.getTriggerKey(2L)))
				.isEqualTo(Trigger.TriggerState.PAUSED);
	}

	@Test
	void 更新会替换cron表达式() throws Exception {
		ScheduleUtils.createScheduleJob(scheduler, job(3L, "0 0 3 * * ?", true));
		ScheduleUtils.updateScheduleJob(scheduler, job(3L, "0 30 4 * * ?", true));

		assertThat(ScheduleUtils.getCronTrigger(scheduler, 3L).getCronExpression())
				.isEqualTo("0 30 4 * * ?");
	}

	@Test
	void 暂停与恢复可以来回切() throws Exception {
		ScheduleUtils.createScheduleJob(scheduler, job(4L, "0 0 3 * * ?", true));

		ScheduleUtils.pauseJob(scheduler, 4L);
		assertThat(scheduler.getTriggerState(ScheduleUtils.getTriggerKey(4L)))
				.isEqualTo(Trigger.TriggerState.PAUSED);

		ScheduleUtils.resumeJob(scheduler, 4L);
		assertThat(scheduler.getTriggerState(ScheduleUtils.getTriggerKey(4L)))
				.isEqualTo(Trigger.TriggerState.NORMAL);
	}

	@Test
	void 删除后job与trigger都不存在() throws Exception {
		ScheduleUtils.createScheduleJob(scheduler, job(5L, "0 0 3 * * ?", true));
		ScheduleUtils.deleteScheduleJob(scheduler, 5L);

		assertThat(scheduler.checkExists(ScheduleUtils.getJobKey(5L))).isFalse();
		assertThat(scheduler.getTrigger(ScheduleUtils.getTriggerKey(5L))).isNull();
	}

	@Test
	void 不同任务是不同的jobKey() throws Exception {
		ScheduleUtils.createScheduleJob(scheduler, job(6L, "0 0 3 * * ?", true));
		ScheduleUtils.createScheduleJob(scheduler, job(7L, "0 0 4 * * ?", true));

		assertThat(scheduler.checkExists(ScheduleUtils.getJobKey(6L))).isTrue();
		assertThat(scheduler.checkExists(ScheduleUtils.getJobKey(7L))).isTrue();

		// 暂停 6 不应影响 7
		ScheduleUtils.pauseJob(scheduler, 6L);
		assertThat(scheduler.getTriggerState(ScheduleUtils.getTriggerKey(7L)))
				.isEqualTo(Trigger.TriggerState.NORMAL);
	}
}
