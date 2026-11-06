import request from '@/util/request'

/**
 * 定时任务列表（分页）。任务本身来自 schedule_job 表，
 * 「上次 / 下次执行」由后端从 Quartz 的 Trigger 现取。
 */
export function getJobs(params) {
	return request({url: 'jobs', method: 'GET', params})
}

/**
 * 立即执行一次。后端走 scheduler.triggerJob，与 cron 触发是同一个任务。
 * 接口只负责提交，执行结果要去日志里看。
 */
export function runJob(jobId) {
	return request({url: 'job/run', method: 'POST', params: {jobId}})
}

/**
 * 启用 / 禁用
 */
export function updateJobStatus(jobId, status) {
	return request({url: 'job/status', method: 'PUT', params: {jobId, status}})
}

/**
 * 执行日志。传 jobId 只看某个任务，不传则是全部任务
 */
export function getJobLogs(params) {
	return request({url: 'job/logs', method: 'GET', params})
}

export function deleteJobLog(logId) {
	return request({url: 'job/log', method: 'DELETE', params: {logId}})
}
