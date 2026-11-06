package com.coolskyai.modules.auth.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.coolskyai.entity.User;
import com.coolskyai.exception.BadRequestException;
import com.coolskyai.mapper.SiteSettingMapper;
import com.coolskyai.mapper.UserMapper;
import com.coolskyai.service.RedisService;
import com.coolskyai.util.HashUtils;
import com.coolskyai.util.MailUtils;

import java.security.SecureRandom;
import java.util.regex.Pattern;

/**
 * @Description: 找回密码 / 修改邮箱的验证码
 *
 * 四个刻意的决定：
 *
 * 1. 只允许前台访客（ROLE_user）走这条路。站长账号的邮箱往往写在关于页、
 *    联系入口等公开位置，如果也能用来重置，等于给了一个「公开邮箱 → 管理员密码」
 *    的通道。站长忘密码走服务器改库或后台重置。
 *
 * 2. 防枚举：邮箱不存在时对外同样返回「已发送」，只有内部日志记,
 *    否则这个接口就成了「查某邮箱是否注册」的工具。
 *
 * 3. 邮件服务没配好时直接拒绝，而不是假装成功。
 *    当前 spring.mail.password 为空（MAIL_PASSWORD 未设置），真发不出去；
 *    如果也返回成功，用户会一直等一封永远不会来的邮件。
 *
 * 4. 验证码存 Redis 带 5 分钟 TTL，失败 5 次作废，60 秒内不可重复发。
 *    验证码只存明文 6 位数字：它是短时效凭证，加盐哈希反而让「输错重试」
 *    的比对变复杂，收益不抵成本。
 *
 * @Author: CoolSkyAI
 * @Date: 2026-11-06
 */
@Slf4j
@Service
public class PasswordResetService {

	private static final int CODE_TTL_SECONDS = 300;
	private static final int RESEND_SECONDS = 60;
	private static final int MAX_FAIL = 5;

	private static final String KEY_CODE = "pwd:code:";
	private static final String KEY_LIMIT = "pwd:limit:";
	private static final String KEY_FAIL = "pwd:fail:";

	private static final Pattern EMAIL = Pattern.compile("^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+$");

	private final SecureRandom random = new SecureRandom();

	@Autowired
	private UserMapper userMapper;
	@Autowired
	private SiteSettingMapper siteSettingMapper;
	@Autowired
	private RedisService redisService;
	@Autowired
	private MailUtils mailUtils;

	/** 空字符串表示邮件服务没配（compose 里默认 MAIL_PASSWORD 为空） */
	@Value("${spring.mail.password:}")
	private String mailPassword;

	/**
	 * 发送验证码。
	 *
	 * @param forEmailChange true 表示是登录用户改邮箱，此时不校验账号是否存在
	 */
	public void sendCode(String email, boolean forEmailChange) {
		String target = normalizeEmail(email);
		if (redisService.hasKey(KEY_LIMIT + target)) {
			throw new BadRequestException("验证码已发送，请 60 秒后再试");
		}
		requireMailConfigured();

		User user = userMapper.findVisitorByEmail(target);
		if (!forEmailChange && user == null) {
			// 防枚举：对外不暴露，仍占用限流键，避免被用来高频探测邮箱是否注册
			log.info("找回密码：邮箱 {} 未匹配到前台账号，对外仍返回成功", mask(target));
			redisService.saveObjectToValue(KEY_LIMIT + target, 1);
			redisService.expire(KEY_LIMIT + target, RESEND_SECONDS);
			return;
		}

		String code = String.format("%06d", random.nextInt(1_000_000));
		redisService.saveObjectToValue(KEY_CODE + target, code);
		redisService.expire(KEY_CODE + target, CODE_TTL_SECONDS);
		redisService.saveObjectToValue(KEY_FAIL + target, 0);
		redisService.expire(KEY_FAIL + target, CODE_TTL_SECONDS);
		redisService.saveObjectToValue(KEY_LIMIT + target, 1);
		redisService.expire(KEY_LIMIT + target, RESEND_SECONDS);

		String siteName = siteSettingMapper.getValueByNameEn("blogName");
		if (siteName == null || siteName.trim().isEmpty()) {
			siteName = "本站";
		}
		String subject = "【" + siteName + "】" + (forEmailChange ? "邮箱验证码" : "找回密码验证码");
		String content = "你正在" + (forEmailChange ? "绑定/修改邮箱" : "重置登录密码") + "，验证码："
				+ code + "\n\n验证码 5 分钟内有效，最多尝试 5 次。\n"
				+ "如果不是你本人操作，请忽略这封邮件。";
		sendMail(target, subject, content);
	}

	/**
	 * 校验验证码并重置密码
	 */
	public void resetPassword(String email, String code, String newPassword) {
		String target = normalizeEmail(email);
		if (code == null || !code.matches("\\d{6}")) {
			throw new BadRequestException("请输入 6 位数字验证码");
		}
		checkPassword(newPassword);

		Object stored = redisService.getObjectByValue(KEY_CODE + target, String.class);
		if (stored == null) {
			throw new BadRequestException("验证码已过期，请重新获取");
		}
		int fails = readFails(target);
		if (fails >= MAX_FAIL) {
			throw new BadRequestException("验证码错误次数过多，请重新获取");
		}
		if (!String.valueOf(stored).equals(code)) {
			bumpFails(target, fails);
			throw new BadRequestException("验证码不正确，还可尝试 " + (MAX_FAIL - fails - 1) + " 次");
		}

		User user = userMapper.findVisitorByEmail(target);
		if (user == null) {
			throw new BadRequestException("账号不存在，请联系站长");
		}
		userMapper.updatePassword(user.getId(), HashUtils.getBC(newPassword));
		redisService.deleteCacheByKey(KEY_CODE + target);
		redisService.deleteCacheByKey(KEY_FAIL + target);
		log.info("找回密码成功：user={}", user.getId());
	}

	/**
	 * 校验验证码（修改邮箱时用），不消费
	 */
	public void verifyCode(String email, String code) {
		String target = normalizeEmail(email);
		if (code == null || !code.matches("\\d{6}")) {
			throw new BadRequestException("请输入 6 位数字验证码");
		}
		Object stored = redisService.getObjectByValue(KEY_CODE + target, String.class);
		if (stored == null) {
			throw new BadRequestException("验证码已过期，请重新获取");
		}
		int fails = readFails(target);
		if (fails >= MAX_FAIL) {
			throw new BadRequestException("验证码错误次数过多，请重新获取");
		}
		if (!String.valueOf(stored).equals(code)) {
			bumpFails(target, fails);
			throw new BadRequestException("验证码不正确");
		}
		redisService.deleteCacheByKey(KEY_CODE + target);
		redisService.deleteCacheByKey(KEY_FAIL + target);
	}

	public static void checkPassword(String password) {
		if (password == null || password.length() < 6 || password.length() > 32) {
			throw new BadRequestException("密码长度需在 6-32 位之间");
		}
	}

	// ---------------- 内部 ----------------

	private String normalizeEmail(String email) {
		String value = email == null ? "" : email.trim().toLowerCase();
		if (!EMAIL.matcher(value).matches()) {
			throw new BadRequestException("请填写正确的邮箱地址");
		}
		if (value.length() > 255) {
			throw new BadRequestException("邮箱地址过长");
		}
		return value;
	}

	/**
	 * 邮件服务没配就直接拒绝：假装成功只会让用户等一封永远不来的邮件
	 */
	private void requireMailConfigured() {
		if (mailPassword == null || mailPassword.trim().isEmpty()) {
			// 这段话同时服务「找回密码」和「改邮箱」两个场景，所以不写死"找回密码"
			throw new BadRequestException("邮件服务尚未配置，暂时无法发送验证码。找回密码请联系站长在后台重置");
		}
	}

	private void sendMail(String to, String subject, String content) {
		try {
			mailUtils.sendSimpleMail(to, subject, content);
		} catch (Exception e) {
			log.error("验证码邮件发送失败，收件人={}", mask(to), e);
			throw new BadRequestException("邮件发送失败，请稍后重试或联系站长");
		}
	}

	private int readFails(String email) {
		Object value = redisService.getObjectByValue(KEY_FAIL + email, Integer.class);
		if (value == null) {
			return 0;
		}
		return Integer.parseInt(String.valueOf(value));
	}

	private void bumpFails(String email, int current) {
		redisService.saveObjectToValue(KEY_FAIL + email, current + 1);
		redisService.expire(KEY_FAIL + email, CODE_TTL_SECONDS);
	}

	/** 日志里不打完整邮箱 */
	private String mask(String email) {
		int at = email.indexOf('@');
		if (at <= 1) {
			return "***";
		}
		return email.charAt(0) + "***" + email.substring(at);
	}
}
