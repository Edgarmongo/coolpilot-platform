package com.coolskyai.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.springframework.stereotype.Repository;
import com.coolskyai.entity.ScheduleJobLog;

import java.util.List;

/**
 * @Description: 定时任务日志持久层接口
 * @Author: CoolSkyAI
 * @Date: 2020-11-01
 */
@Mapper
@Repository
public interface ScheduleJobLogMapper {
	List<ScheduleJobLog> getJobLogListByDate(@Param("startDate") String startDate,
											 @Param("endDate") String endDate,
											 @Param("jobId") Long jobId);

	int saveJobLog(ScheduleJobLog jobLog);

	int deleteJobLogByLogId(Long logId);

	/**
	 * 某个任务最近一次执行的时间。
	 *
	 * 用途：Quartz 重启后 previousFireTime 会清空，「上次执行」列会一直是空的；
	 * 日志表里其实有完整记录，用它兜底才符合运维的直觉。
	 */
	@Select("SELECT MAX(create_time) FROM schedule_job_log WHERE job_id = #{jobId}")
	java.util.Date getLastExecuteTime(@Param("jobId") Long jobId);
}
