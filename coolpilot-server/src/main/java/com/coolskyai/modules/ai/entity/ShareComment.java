package com.coolskyai.modules.ai.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.Date;

/**
 * @Description: 分享页评论（comment 表中 target_type='SHARE' 的那部分）
 *
 * 只映射需要的列：邮箱、IP、QQ、个人网站这些是文章评论的字段，
 * 分享页既不展示也不收集，映射出来只会增加「不小心返回给前端」的机会。
 *
 * @Author: CoolSkyAI
 * @Date: 2026-11-05
 */
@NoArgsConstructor
@Getter
@Setter
@ToString
public class ShareComment {
	private Long id;
	/** 评论对象：ai_message.id */
	private Long targetId;
	/** 评论人账号ID（分享页只允许登录后评论，所以必不为空） */
	private Long userId;
	private String nickname;
	private String avatar;
	private String content;
	private Date createTime;
	/** 父评论ID，-1 为顶级 */
	private Long parentCommentId;
	/** 平台所有者的发言，前端加标记 */
	private Boolean adminComment;
	/** 仅写入用：评论人邮箱，便于以后接回复提醒 */
	private String email;
	/** 仅写入用：评论来源 IP（comment.ip 允许为空，但审核时有用） */
	private String ip;
}
