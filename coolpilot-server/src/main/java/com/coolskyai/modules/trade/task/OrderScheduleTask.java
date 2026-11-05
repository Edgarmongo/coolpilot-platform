package com.coolskyai.modules.trade.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.coolskyai.modules.trade.service.OrderService;

/**
 * @Description: 交易相关的定时任务（由 schedule_job 表调度）
 *
 * beanName 必须与数据库里登记的一致：orderScheduleTask。
 * 方法签名不带参数，ScheduleRunnable 走的是 getDeclaredMethod(methodName)。
 *
 * @Author: CoolSkyAI
 * @Date: 2026-11-05
 */
@Component("orderScheduleTask")
public class OrderScheduleTask {

	private static final Logger log = LoggerFactory.getLogger(OrderScheduleTask.class);

	@Autowired
	private OrderService orderService;

	/**
	 * 关闭超时未支付订单
	 *
	 * 只影响「过了支付截止时间还没付」的订单；已付款、已退款的一律不碰。
	 * 关闭后同一商品可以重新下单（下单前会检查是否存在 PENDING_PAY 订单）。
	 */
	public void closeExpiredOrders() {
		int count = orderService.closeExpired();
		if (count > 0) {
			log.info("定时任务：关闭超时未支付订单 {} 笔", count);
		}
	}
}
