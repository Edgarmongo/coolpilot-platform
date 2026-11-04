package com.coolskyai.modules.notify.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.mail.autoconfigure.MailProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.coolskyai.config.properties.BlogProperties;
import com.coolskyai.entity.User;
import com.coolskyai.mapper.SiteSettingMapper;
import com.coolskyai.mapper.UserMapper;
import com.coolskyai.modules.community.service.MessageService;
import com.coolskyai.util.MailUtils;

import jakarta.annotation.PostConstruct;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * @Description: 统一通知出口（站内消息 + 邮件）
 *
 * 之前的问题是：`message` 表有、站内消息页也有，但没有任何业务代码往里写过 ——
 * 用户中心的消息标签永远是空的，订单和技术服务的状态变化只能靠用户自己回来看。
 * 这里把「发消息 + 发邮件」收敛成一个入口，业务侧只关心「通知谁、说什么」。
 *
 * 三条刻意的规则：
 *   1. 邮件永远在**事务提交后**才发。业务回滚了却已经发出「已发货」邮件，
 *      比不发更糟；站内消息跟随事务，回滚时一起消失，是一致的。
 *   2. 没有配置邮箱密码（本地开发/未上线的环境）时只写站内消息，不报错、不打日志刷屏。
 *   3. 邮件失败只记日志，绝不向上抛 —— 通知失败不能把已经成功的下单/验收带崩。
 *
 * @Author: CoolSkyAI
 * @Date: 2026-11-05
 */
@Service
public class NotifyService {

	private static final Logger log = LoggerFactory.getLogger(NotifyService.class);

	@Autowired
	private MessageService messageService;
	@Autowired
	private UserMapper userMapper;
	@Autowired
	private SiteSettingMapper siteSettingMapper;
	@Autowired
	private MailUtils mailUtils;
	@Autowired
	private MailProperties mailProperties;
	@Autowired
	private BlogProperties blogProperties;

	@Value("${notify.mail.enabled:true}")
	private boolean mailEnabled;

	@PostConstruct
	void checkConfig() {
		if (mailEnabled && !mailConfigured()) {
			log.info("邮件通知未配置（MAIL_PASSWORD 为空），当前仅写站内消息");
		}
	}

	/**
	 * 通知前台用户：写站内消息 + 发邮件（有邮箱时）
	 *
	 * @param path 站内相对路径（如 /me?tab=ent），邮件里会补成绝对地址
	 */
	public void toUser(Long userId, String msgType, String title, String content,
	                   String path, String bizType, Long bizId) {
		if (userId == null) {
			return;
		}
		writeMessage(userId, msgType, title, content, bizType, bizId, path);
		User user = userMapper.findById(userId);
		String email = user == null ? null : trim(user.getEmail());
		if (email == null) {
			return;
		}
		// 邮件在客户端打开，必须给绝对地址；站内消息用相对路径，前端直接路由跳转
		sendMail(email, title, content, absolute(blogProperties.getView(), path));
	}

	/**
	 * 通知平台所有者：写站内消息 + 发邮件到站点邮箱
	 *
	 * 收件地址取「站点设置 → 资料卡 → 邮箱」，没填就回落到发信账号。
	 *
	 * @param cmsPath 后台相对路径（如 /service/demand），邮件里会补上 CMS 域名
	 */
	public void toOwner(String msgType, String title, String content,
	                    String cmsPath, String bizType, Long bizId) {
		Long ownerId = userMapper.findOwnerIdByRole(com.coolskyai.constant.Role.OWNER);
		if (ownerId != null) {
			writeMessage(ownerId, msgType, title, content, bizType, bizId, cmsPath);
		}
		sendMail(ownerEmail(), title, content, absolute(blogProperties.getCms(), cmsPath));
	}

	/**
	 * 所有者收件地址：站点设置里的邮箱（去掉 mailto: 前缀），否则用发信账号
	 */
	private String ownerEmail() {
		String configured = trim(siteSettingMapper.getValueByNameEn("email"));
		if (configured != null) {
			String cleaned = configured.replaceFirst("(?i)^mailto:", "").trim();
			if (cleaned.contains("@")) {
				return cleaned;
			}
		}
		return trim(mailProperties.getUsername());
	}

	private void sendMail(String to, String title, String content, String linkUrl) {
		if (!mailEnabled || to == null || !to.contains("@") || !mailConfigured()) {
			return;
		}
		Map<String, Object> variables = new HashMap<>(8);
		variables.put("siteName", blogProperties.getName() == null ? "CoolSkyAI" : blogProperties.getName());
		variables.put("title", title);
		variables.put("content", content == null ? "" : content);
		variables.put("linkUrl", linkUrl == null ? "" : linkUrl);
		variables.put("time", new Date());

		String recipient = to;
		String subject = "【" + variables.get("siteName") + "】" + title;
		Runnable task = () -> mailUtils.sendHtmlTemplateMail(variables, recipient, subject, "notify.html");

		// 事务内延到提交后再发：避免「业务回滚，但邮件已经告诉用户成功了」
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					task.run();
				}
			});
		} else {
			task.run();
		}
	}

	/**
	 * 有没有可用的发信凭据。没密码时 JavaMailSender 会抛认证异常，
	 * 提前判断可以避免每个业务动作都刷一条异常栈。
	 */
	private boolean mailConfigured() {
		String password = mailProperties.getPassword();
		String host = mailProperties.getHost();
		return password != null && !password.trim().isEmpty()
				&& host != null && !host.trim().isEmpty();
	}

	/**
	 * 写站内消息。
	 *
	 * 单独兜一层异常：消息写失败（比如 title 超长）不应该让「确认收款」这种
	 * 已经完成的业务动作回滚 —— 通知是附属动作，不能反过来决定主流程成败。
	 */
	private void writeMessage(Long userId, String msgType, String title, String content,
	                          String bizType, Long bizId, String path) {
		try {
			messageService.send(userId, msgType, title, content, bizType, bizId, path);
		} catch (Exception e) {
			log.warn("写站内消息失败 userId={} title={}: {}", userId, title, e.getMessage());
		}
	}

	/**
	 * 把相对路径补成绝对地址；base 未配置时原样返回
	 */
	private String absolute(String base, String path) {
		if (path == null || path.isEmpty()) {
			return "";
		}
		if (path.startsWith("http://") || path.startsWith("https://")) {
			return path;
		}
		if (base == null || base.trim().isEmpty()) {
			return path;
		}
		String prefix = base.trim().replaceAll("/+$", "");
		return prefix + (path.startsWith("/") ? path : "/" + path);
	}

	private String trim(String value) {
		if (value == null) {
			return null;
		}
		String text = value.trim();
		return text.isEmpty() ? null : text;
	}
}
