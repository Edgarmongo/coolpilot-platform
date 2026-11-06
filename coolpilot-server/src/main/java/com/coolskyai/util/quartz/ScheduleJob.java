package com.coolskyai.util.quartz;

import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.springframework.scheduling.quartz.QuartzJobBean;
import com.coolskyai.entity.ScheduleJobLog;
import com.coolskyai.service.ScheduleJobService;
import com.coolskyai.util.common.SpringContextUtils;

import java.util.Date;

/**
 * @Description: 定时任务执行与结果记录
 * @Author: CoolSkyAI
 * @Date: 2020-11-01
 *
 * 并发控制说明（CP-BACKEND-01）：
 * 1. 类上的 @DisallowConcurrentExecution 由 JobBuilder 写入 JobDetail，
 *    作用是「同一个 JobKey 的两次触发不重叠」。本项目的每个任务在
 *    schedule_job 里各有 jobId，ScheduleUtils 用 TASK_{jobId} 生成 JobKey，
 *    所以并发保护是**按任务**生效的 —— 任务 A 在跑不影响任务 B 同时跑，
 *    但同一个任务绝不会跑两份。
 * 2. 手动执行走 scheduler.triggerJob(同一个 JobKey)，与定时触发共用
 *    JobStore 的阻塞判断，不绕过上面的约束。
 * 3. 这里不再自己起线程池。Quartz 每次触发都会新建一个 Job 实例
 *    （Spring 的 JobFactory 走 createBean），实例里持有 ExecutorService
 *    等于每次执行泄漏一条常驻线程，且 newSingleThreadExecutor 起不到任何
 *    并发隔离作用。直接在当前 Quartz 工作线程里执行，异常照常抛出并记日志。
 * 4. 这条保护只在「单实例 + RAMJobStore」下成立。部署多副本时每个实例都会
 *    各自调度一遍，需要改成 JDBC JobStore + isClustered，见 ScheduleJobServiceImpl.init() 的启动日志。
 */
@Slf4j
@DisallowConcurrentExecution
public class ScheduleJob extends QuartzJobBean {

	@Override
	protected void executeInternal(JobExecutionContext context) {
		com.coolskyai.entity.ScheduleJob scheduleJob =
				(com.coolskyai.entity.ScheduleJob) context.getMergedJobDataMap().get(com.coolskyai.entity.ScheduleJob.JOB_PARAM_KEY);
		if (scheduleJob == null) {
			log.warn("定时任务缺少参数，跳过执行。trigger：{}", context.getTrigger() == null ? "-" : context.getTrigger().getKey());
			return;
		}
		//获取spring bean
		ScheduleJobService scheduleJobService = (ScheduleJobService) SpringContextUtils.getBean("scheduleJobServiceImpl");
		//数据库保存任务执行记录
		ScheduleJobLog jobLog = new ScheduleJobLog();
		jobLog.setJobId(scheduleJob.getJobId());
		jobLog.setBeanName(scheduleJob.getBeanName());
		jobLog.setMethodName(scheduleJob.getMethodName());
		jobLog.setParams(scheduleJob.getParams());
		jobLog.setCreateTime(new Date());
		//任务开始时间
		long startTime = System.currentTimeMillis();
		//执行任务
		log.info("任务准备执行，任务ID：{}", scheduleJob.getJobId());
		try {
			new ScheduleRunnable(scheduleJob.getBeanName(), scheduleJob.getMethodName(), scheduleJob.getParams()).run();
			//任务执行总时长
			long times = System.currentTimeMillis() - startTime;
			jobLog.setTimes((int) times);
			//任务执行结果
			jobLog.setStatus(true);
			log.info("任务执行成功，任务ID：{}，总共耗时：{} 毫秒", scheduleJob.getJobId(), times);
		} catch (Exception e) {
			//任务执行总时长
			long times = System.currentTimeMillis() - startTime;
			jobLog.setTimes((int) times);
			//任务执行结果
			jobLog.setStatus(false);
			//ScheduleRunnable 会把业务异常包一层 RuntimeException，直接 toString()
			//只剩「执行定时任务失败」，真正原因看不到
			jobLog.setError(describe(e));
			log.error("任务执行失败，任务ID：{}", scheduleJob.getJobId(), e);
		} finally {
			scheduleJobService.saveJobLog(jobLog);
		}
	}

	/**
	 * 异常摘要：外层类型 + 根因，够运维判断是哪一类问题，又不至于把整段堆栈塞进表里
	 */
	private static String describe(Throwable e) {
		Throwable root = e;
		while (root.getCause() != null && root.getCause() != root) {
			root = root.getCause();
		}
		if (root == e) {
			return e.toString();
		}
		return e.getClass().getSimpleName() + ": " + root;
	}
}
