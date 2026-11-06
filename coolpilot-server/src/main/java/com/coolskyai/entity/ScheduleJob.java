package com.coolskyai.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.Date;

/**
 * @Description: 定时任务
 * @Author: CoolSkyAI
 * @Date: 2020-11-01
 */
@NoArgsConstructor
@Getter
@Setter
@ToString
public class ScheduleJob {
	public static final String JOB_PARAM_KEY = "JOB_PARAM_KEY"; //任务调度参数key

	private Long jobId;//任务id
	private String beanName;//spring bean名称
	private String methodName;//方法名
	private String params;//参数
	private String cron;//cron表达式
	private Boolean status;//任务状态
	private String remark;//备注
	private Date createTime;//创建时间

	/**
	 * 最近一次触发时间、下次触发时间。
	 *
	 * 不落库：这两个值来自 Quartz 的 Trigger（scheduler 运行时状态），
	 * 存表反而会出现「表里写的和实际调度不一致」。列表查询时现取现填。
	 */
	private Date lastFireTime;

	private Date nextFireTime;
}
