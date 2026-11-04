package com.coolskyai.modules.entitlement.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.Date;

/**
 * @Description: 人工发放权益入参
 * @Author: Naccl
 * @Date: 2026-11-05
 */
@NoArgsConstructor
@Getter
@Setter
@ToString
public class GrantDTO {
	private Long userId;
	private Long productId;
	/** ACCESS / DOWNLOAD / LEARN，默认 ACCESS */
	private String entType;
	private Date startTime;
	/** 为空表示永久 */
	private Date expireTime;
	private String reason;
}
