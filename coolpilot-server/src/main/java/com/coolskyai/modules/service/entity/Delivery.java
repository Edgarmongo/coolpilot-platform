package com.coolskyai.modules.service.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.Date;

/**
 * @Description: 服务交付物
 * @Author: CoolSkyAI
 * @Date: 2026-11-05
 */
@NoArgsConstructor
@Getter
@Setter
@ToString
public class Delivery {
	private Long id;
	private Long serviceOrderId;
	private Long projectId;
	private Long milestoneId;
	/** FILE / LINK / TEXT */
	private String deliveryType;
	private Long fileId;
	private String content;
	private Long deliveredBy;
	private Date deliveredTime;
	private Date acceptedTime;
	/** DELIVERED / ACCEPTED / REJECTED */
	private String status;

	/** 展示用：文件名 / 下载地址 */
	private String fileName;
}
