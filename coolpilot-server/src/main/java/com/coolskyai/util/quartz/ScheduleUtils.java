package com.coolskyai.util.quartz;

import org.quartz.CronScheduleBuilder;
import org.quartz.CronTrigger;
import org.quartz.JobBuilder;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import com.coolskyai.entity.ScheduleJob;

/**
 * @Description: 定时任务在 Quartz 里的增删改查
 *
 * key 统一用 TASK_{jobId}：jobId 来自 schedule_job 表，所以同一个任务无论
 * 改几次 cron，JobKey / TriggerKey 都不变，暂停与恢复才能对上号。
 *
 * 所有方法都把 SchedulerException 包成 RuntimeException 并带上动作说明 ——
 * 这些调用来自后台接口，调用方只关心「哪一步失败了」，不需要处理受检异常。
 *
 * @Author: CoolSkyAI
 * @Date: 2020-11-01
 */
public final class ScheduleUtils {

	private static final String JOB_PREFIX = "TASK_";

	private ScheduleUtils() {
	}

	public static TriggerKey getTriggerKey(Long jobId) {
		return TriggerKey.triggerKey(JOB_PREFIX + jobId);
	}

	public static JobKey getJobKey(Long jobId) {
		return JobKey.jobKey(JOB_PREFIX + jobId);
	}

	public static CronTrigger getCronTrigger(Scheduler scheduler, Long jobId) {
		try {
			return (CronTrigger) scheduler.getTrigger(getTriggerKey(jobId));
		} catch (SchedulerException e) {
			throw new RuntimeException("获取定时任务CronTrigger出现异常", e);
		}
	}

	/**
	 * 新建任务。任务定义（bean / 方法 / 参数）随 JobDataMap 一起存进 JobStore，
	 * 执行时由 ScheduleJob 取出来反射调用
	 */
	public static void createScheduleJob(Scheduler scheduler, ScheduleJob scheduleJob) {
		execute("创建定时任务失败", () -> {
			JobDetail jobDetail = JobBuilder.newJob(com.coolskyai.util.quartz.ScheduleJob.class)
					.withIdentity(getJobKey(scheduleJob.getJobId()))
					.build();
			jobDetail.getJobDataMap().put(ScheduleJob.JOB_PARAM_KEY, scheduleJob);
			CronTrigger trigger = TriggerBuilder.newTrigger()
					.withIdentity(getTriggerKey(scheduleJob.getJobId()))
					.withSchedule(cronOf(scheduleJob))
					.build();
			scheduler.scheduleJob(jobDetail, trigger);
			pauseIfDisabled(scheduler, scheduleJob);
		});
	}

	/**
	 * 改 cron：保留原 TriggerKey，重新构建后 reschedule
	 */
	public static void updateScheduleJob(Scheduler scheduler, ScheduleJob scheduleJob) {
		execute("更新定时任务失败", () -> {
			TriggerKey triggerKey = getTriggerKey(scheduleJob.getJobId());
			CronTrigger rebuilt = getCronTrigger(scheduler, scheduleJob.getJobId())
					.getTriggerBuilder()
					.withIdentity(triggerKey)
					.withSchedule(cronOf(scheduleJob))
					.build();
			rebuilt.getJobDataMap().put(ScheduleJob.JOB_PARAM_KEY, scheduleJob);
			scheduler.rescheduleJob(triggerKey, rebuilt);
			pauseIfDisabled(scheduler, scheduleJob);
		});
	}

	/**
	 * 立即执行一次，不影响原有 cron
	 */
	public static void run(Scheduler scheduler, ScheduleJob scheduleJob) {
		execute("立即执行定时任务失败", () -> {
			JobDataMap dataMap = new JobDataMap();
			dataMap.put(ScheduleJob.JOB_PARAM_KEY, scheduleJob);
			scheduler.triggerJob(getJobKey(scheduleJob.getJobId()), dataMap);
		});
	}

	public static void pauseJob(Scheduler scheduler, Long jobId) {
		execute("暂停定时任务失败", () -> scheduler.pauseJob(getJobKey(jobId)));
	}

	public static void resumeJob(Scheduler scheduler, Long jobId) {
		execute("恢复定时任务失败", () -> scheduler.resumeJob(getJobKey(jobId)));
	}

	public static void deleteScheduleJob(Scheduler scheduler, Long jobId) {
		execute("删除定时任务失败", () -> scheduler.deleteJob(getJobKey(jobId)));
	}

	// ---------------- 内部 ----------------

	@FunctionalInterface
	private interface QuartzOperation {
		void run() throws SchedulerException;
	}

	private static void execute(String failMessage, QuartzOperation operation) {
		try {
			operation.run();
		} catch (SchedulerException e) {
			throw new RuntimeException(failMessage, e);
		}
	}

	private static CronScheduleBuilder cronOf(ScheduleJob scheduleJob) {
		return CronScheduleBuilder.cronSchedule(scheduleJob.getCron())
				.withMisfireHandlingInstructionDoNothing();
	}

	/**
	 * status 为 null 时按「未启用」处理：宁可不跑，「以为它在跑」比不跑更难发现
	 */
	private static void pauseIfDisabled(Scheduler scheduler, ScheduleJob scheduleJob) throws SchedulerException {
		if (!Boolean.TRUE.equals(scheduleJob.getStatus())) {
			scheduler.pauseJob(getJobKey(scheduleJob.getJobId()));
		}
	}
}
