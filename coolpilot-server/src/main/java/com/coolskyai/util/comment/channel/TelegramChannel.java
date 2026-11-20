package com.coolskyai.util.comment.channel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import com.coolskyai.config.properties.BlogProperties;
import com.coolskyai.config.properties.TelegramProperties;
import com.coolskyai.model.dto.Comment;
import com.coolskyai.util.StringUtils;
import com.coolskyai.util.comment.CommentUtils;
import com.coolskyai.util.telegram.TelegramUtils;

import java.text.SimpleDateFormat;
import java.util.Map;
import java.util.TimeZone;

/**
 * Telegram 提醒：把新评论推到博主的机器人会话里
 *
 * 构造时顺手注册 webhook —— 这个通道一旦装配就说明配置了机器人，
 * 不注册的话用户在 Telegram 里发的「快捷回复」永远收不到。
 *
 * 消息体用 Telegram 的 HTML 子集（b / code / pre / u / a），不要换成 Markdown，
 * 评论内容里带下划线、星号时两种模式的表现不一样，改过就会乱。
 *
 * @author: CoolSkyAI
 * @date: 2022-01-22
 */
@Lazy
@Component
public class TelegramChannel implements CommentNotifyChannel {

	private static final Logger log = LoggerFactory.getLogger(TelegramChannel.class);

	private static final String TIME_PATTERN = "yyyy-MM-dd HH:mm";
	private static final String TIME_ZONE = "Asia/Shanghai";

	private final TelegramUtils telegramUtils;
	private final BlogProperties blogProperties;
	private final TelegramProperties telegramProperties;
	private final SimpleDateFormat timeFormat;

	public TelegramChannel(TelegramUtils telegramUtils,
	                       BlogProperties blogProperties,
	                       TelegramProperties telegramProperties) {
		this.telegramUtils = telegramUtils;
		this.blogProperties = blogProperties;
		this.telegramProperties = telegramProperties;
		this.timeFormat = new SimpleDateFormat(TIME_PATTERN);
		this.timeFormat.setTimeZone(TimeZone.getTimeZone(TIME_ZONE));

		log.debug("Telegram 提醒通道已装配，注册 webhook");
		telegramUtils.setWebhook();
	}

	@Override
	public void notifyMyself(Comment comment) {
		String url = telegramProperties.getApi() + telegramProperties.getToken() + TelegramUtils.SEND_MESSAGE;
		Map<String, Object> body = telegramUtils.getMessageBody(buildContent(comment));
		telegramUtils.sendByAutoCheckReverseProxy(url, body);
	}

	/**
	 * 拼消息体。可选行（QQ、个人网站）为空时整行不出现，其它行固定顺序
	 */
	private String buildContent(Comment comment) {
		StringBuilder text = new StringBuilder(512);
		text.append("<b>您的文章<a href=\"").append(blogProperties.getView())
				.append(CommentUtils.getPagePath(comment)).append("\">《")
				.append(CommentUtils.getPageTitle(comment)).append("》</a>有了新的评论~</b>\n\n");

		text.append("<b>").append(comment.getNickname()).append("</b> 给您的评论：\n\n");
		text.append("<pre>").append(comment.getContent()).append("</pre>\n\n");

		text.append("<b>其他信息：</b>\n");
		text.append("评论ID：<code>").append(comment.getId()).append("</code>\n");
		text.append("IP：").append(comment.getIp()).append('\n');
		if (!StringUtils.isEmpty(comment.getQq())) {
			text.append("QQ：").append(comment.getQq()).append('\n');
		}
		text.append("时间：<u>").append(formatTime(comment)).append("</u>\n");
		text.append("邮箱：<code>").append(comment.getEmail()).append("</code>\n");
		if (!StringUtils.isEmpty(comment.getWebsite())) {
			text.append("网站：").append(comment.getWebsite()).append('\n');
		}
		text.append("状态：").append(comment.getPublished() ? "公开" : "待审核")
				.append(" [<a href=\"").append(blogProperties.getCms())
				.append("/blog/comment/list\">管理评论</a>]\n");
		return text.toString();
	}

	private String formatTime(Comment comment) {
		return comment.getCreateTime() == null ? "" : timeFormat.format(comment.getCreateTime());
	}
}
