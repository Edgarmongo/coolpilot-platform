package com.coolskyai.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.quartz.CronTrigger;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SchedulerMetaData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.coolskyai.entity.ScheduleJob;
import com.coolskyai.entity.ScheduleJobLog;
import com.coolskyai.exception.PersistenceException;
import com.coolskyai.mapper.ScheduleJobLogMapper;
import com.coolskyai.mapper.ScheduleJobMapper;
import com.coolskyai.service.ScheduleJobService;
import com.coolskyai.util.quartz.ScheduleUtils;

import jakarta.annotation.PostConstruct;
import java.util.Date;
import java.util.List;

/**
 * @Description: 定时任务业务层实现
 * @Author: CoolSkyAI
 * @Date: 2020-11-01
 */
@Slf4j
@Service
public class ScheduleJobServiceImpl implements ScheduleJobService {
	@Autowired
	Scheduler scheduler;
	@Autowired
	ScheduleJobMapper schedulerJobMapper;
	@Autowired
	ScheduleJobLogMapper scheduleJobLogMapper;

	/**
	 * 项目启动时，初始化定时器
	 */
	@PostConstruct
	public void init() {
		List<ScheduleJob> scheduleJobList = getJobList();
		for (ScheduleJob scheduleJob : scheduleJobList) {
			CronTrigger cronTrigger = ScheduleUtils.getCronTrigger(scheduler, scheduleJob.getJobId());
			//如果不存在，则创建
			if (cronTrigger == null) {
				ScheduleUtils.createScheduleJob(scheduler, scheduleJob);
			} else {
				ScheduleUtils.updateScheduleJob(scheduler, scheduleJob);
			}
		}
		logSchedulerProfile();
	}

	/**
	 * 把调度器的存储方式打到启动日志里。
	 *
	 * 任务防重跑依赖两个前提：JobStore 在内存里（调度状态只有本进程知道）、
	 * 且只有一个实例。这两点不写在配置文件里，只能从运行时读出来。
	 * 将来加副本部署时，这段日志会直接暴露「每个实例都在自己调度」的问题。
	 */
	private void logSchedulerProfile() {
		try {
			SchedulerMetaData meta = scheduler.getMetaData();
			boolean clustered = meta.isJobStoreClustered();
			log.info("Quartz 调度器就绪：jobStore={}，持久化={}，集群={}，实例={}",
					meta.getJobStoreClass().getSimpleName(), meta.isJobStoreSupportsPersistence(),
					clustered, meta.getSchedulerInstanceId());
			if (!clustered) {
				log.warn("Quartz 未开启集群：任务只在当前实例内串行（@DisallowConcurrentExecution）。"
						+ "部署多个副本时每个副本都会各自执行一遍，需改用 JDBC JobStore + isClustered。");
			}
		} catch (SchedulerException e) {
			log.warn("读取 Quartz 调度器信息失败：{}", e.getMessage());
		}
	}

	@Override
	public List<ScheduleJob> getJobList() {
		List<ScheduleJob> jobs = schedulerJobMapper.getJobList();
		for (ScheduleJob job : jobs) {
			fillFireTimes(job);
		}
		return jobs;
	}

	/**
	 * 从 Quartz 的 Trigger 取「上次 / 下次触发时间」。
	 *
	 * 这两个值属于调度器的运行时状态，不在表里。取不到（任务刚建、
	 * 已暂停、调度器还没起来）就留空，前端显示「—」，不影响列表其它列。
	 */
	private void fillFireTimes(ScheduleJob job) {
		try {
			CronTrigger trigger = ScheduleUtils.getCronTrigger(scheduler, job.getJobId());
			Date last = trigger == null ? null : trigger.getPreviousFireTime();
			if (last == null) {
				// 重启后 Quartz 的 previousFireTime 是空的，用日志里的最近一次兜底，
				// 否则「上次执行」永远显示「—」，而日志里明明有记录
				last = scheduleJobLogMapper.getLastExecuteTime(job.getJobId());
			}
			job.setLastFireTime(last);
			// 暂停的 trigger 仍会算出「下次执行时间」，照搬会出现
			// 「状态是禁用、下次执行却有值」的矛盾，这里按状态置空
			job.setNextFireTime(trigger != null && Boolean.TRUE.equals(job.getStatus())
					? trigger.getNextFireTime() : null);
		} catch (Exception e) {
			log.debug("读取任务 {} 的触发时间失败：{}", job.getJobId(), e.getMessage());
		}
	}

	@Transactional(rollbackFor = Exception.class)
	@Override
	public void saveJob(ScheduleJob scheduleJob) {
		if (schedulerJobMapper.saveJob(scheduleJob) != 1) {
			throw new PersistenceException("添加失败");
		}
		ScheduleUtils.createScheduleJob(scheduler, scheduleJob);
	}

	@Transactional(rollbackFor = Exception.class)
	@Override
	public void updateJob(ScheduleJob scheduleJob) {
		if (schedulerJobMapper.updateJob(scheduleJob) != 1) {
			throw new PersistenceException("更新失败");
		}
		ScheduleUtils.updateScheduleJob(scheduler, scheduleJob);
	}

	@Transactional(rollbackFor = Exception.class)
	@Override
	public void deleteJobById(Long jobId) {
		ScheduleUtils.deleteScheduleJob(scheduler, jobId);
		if (schedulerJobMapper.deleteJobById(jobId) != 1) {
			throw new PersistenceException("删除失败");
		}
	}

	@Override
	public void runJobById(Long jobId) {
		ScheduleUtils.run(scheduler, schedulerJobMapper.getJobById(jobId));
	}

	@Transactional(rollbackFor = Exception.class)
	@Override
	public void updateJobStatusById(Long jobId, Boolean status) {
		if (status) {
			ScheduleUtils.resumeJob(scheduler, jobId);
		} else {
			ScheduleUtils.pauseJob(scheduler, jobId);
		}
		if (schedulerJobMapper.updateJobStatusById(jobId, status) != 1) {
			throw new PersistenceException("修改失败");
		}
	}

	@Override
	public List<ScheduleJobLog> getJobLogListByDate(String startDate, String endDate, Long jobId) {
		return scheduleJobLogMapper.getJobLogListByDate(startDate, endDate, jobId);
	}

	@Transactional(rollbackFor = Exception.class)
	@Override
	public void saveJobLog(ScheduleJobLog jobLog) {
		if (scheduleJobLogMapper.saveJobLog(jobLog) != 1) {
			throw new PersistenceException("日志添加失败");
		}
	}

	@Transactional(rollbackFor = Exception.class)
	@Override
	public void deleteJobLogByLogId(Long logId) {
		if (scheduleJobLogMapper.deleteJobLogByLogId(logId) != 1) {
			throw new PersistenceException("日志删除失败");
		}
	}
}
