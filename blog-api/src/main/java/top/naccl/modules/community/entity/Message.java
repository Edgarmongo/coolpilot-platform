package top.naccl.modules.community.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.Date;

/**
 * @Description: 站内消息
 * @Author: Naccl
 * @Date: 2026-11-04
 */
@NoArgsConstructor
@Getter
@Setter
@ToString
public class Message {
	private Long id;
	private Long userId;
	/** SYSTEM / ORDER / DEMAND / COMMENT / REPLY */
	private String msgType;
	private String title;
	private String content;
	private String bizType;
	private Long bizId;
	private String linkUrl;
	private Boolean isRead;
	private Date readTime;
	private Date createTime;
}
