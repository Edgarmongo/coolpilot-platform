package top.naccl.modules.community.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.Date;

/**
 * @Description: 技术问题反馈
 * @Author: Naccl
 * @Date: 2026-11-04
 */
@NoArgsConstructor
@Getter
@Setter
@ToString
public class Feedback {
	private Long id;
	/** 提交人，未登录可为空 */
	private Long userId;
	private String content;
	/** 截图URL，逗号分隔 */
	private String images;
	private String contact;
	/** PENDING / PROCESSING / RESOLVED / CLOSED */
	private String status;
	private String reply;
	private Date replyTime;
	private Date createTime;
	private Date updateTime;
}
