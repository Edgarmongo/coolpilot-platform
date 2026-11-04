package com.coolskyai.modules.service.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * @Description: 里程碑交付
 * @Author: CoolSkyAI
 * @Date: 2026-11-05
 */
@NoArgsConstructor
@Getter
@Setter
@ToString
public class DeliverDTO {
	private Long milestoneId;
	/** FILE / LINK / TEXT */
	private String deliveryType;
	private Long fileId;
	/** 交付说明或链接 */
	private String content;
	private String remark;
}
