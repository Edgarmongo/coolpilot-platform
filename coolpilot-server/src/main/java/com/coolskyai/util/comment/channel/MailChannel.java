package com.coolskyai.util.comment.channel;

import org.springframework.boot.mail.autoconfigure.MailProperties;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import com.coolskyai.config.properties.BlogProperties;
import com.coolskyai.model.dto.Comment;
import com.coolskyai.util.MailUtils;
import com.coolskyai.util.comment.CommentUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * 邮件提醒：有新访客评论时发一封给博主自己
 *
 * 收件人取 spring.mail.username —— 这套站点的邮箱既是发信账号也是收信账号，
 * 不额外引入「通知邮箱」配置，少一个能配错的地方。
 *
 * @author: CoolSkyAI
 * @date: 2022-01-22
 */
@Lazy
@Component
public class MailChannel implements CommentNotifyChannel {

	private static final String OWNER_TEMPLATE = "owner.html";

	private final BlogProperties blogProperties;
	private final MailProperties mailProperties;
	private final MailUtils mailUtils;

	public MailChannel(BlogProperties blogProperties, MailProperties mailProperties, MailUtils mailUtils) {
		this.blogProperties = blogProperties;
		this.mailProperties = mailProperties;
		this.mailUtils = mailUtils;
	}

	@Override
	public void notifyMyself(Comment comment) {
		Map<String, Object> model = buildModel(comment);
		String subject = blogProperties.getName() + " 收到新评论";
		mailUtils.sendHtmlTemplateMail(model, mailProperties.getUsername(), subject, OWNER_TEMPLATE);
	}

	private Map<String, Object> buildModel(Comment comment) {
		Map<String, Object> model = new HashMap<>(16);
		model.put("title", CommentUtils.getPageTitle(comment));
		model.put("time", comment.getCreateTime());
		model.put("nickname", comment.getNickname());
		model.put("content", comment.getContent());
		model.put("ip", comment.getIp());
		model.put("email", comment.getEmail());
		model.put("status", comment.getPublished() ? "公开" : "待审核");
		model.put("url", blogProperties.getView() + CommentUtils.getPagePath(comment));
		model.put("manageUrl", blogProperties.getCms() + "/comments");
		return model;
	}
}
