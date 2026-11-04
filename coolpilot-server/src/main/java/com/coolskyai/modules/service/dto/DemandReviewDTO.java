package com.coolskyai.modules.service.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * @Description: 需求审核
 * @Author: CoolSkyAI
 * @Date: 2026-11-05
 */
@NoArgsConstructor
@Getter
@Setter
@ToString
public class DemandReviewDTO {
	/** true=通过（进入待报价），false=拒绝 */
	private Boolean approve;
	/** 拒绝原因，approve=false 时必填 */
	private String reason;
}
