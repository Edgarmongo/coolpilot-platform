package com.coolskyai.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;

/**
 * @Description: 取客户端 IP（用于按 IP 去重的场景：点赞、阅读、友链浏览）
 *
 * 默认用 TCP 对端地址，而不是 X-Forwarded-For：
 * 后者客户端可以随便写，换一个伪造头就换一个「新访客」，去重形同虚设。
 *
 * 只有部署在可信反代之后（且反代会覆盖该头，比如仓库里的 gateway）
 * 才把 security.trust-forwarded-for 打开。
 *
 * 抽成一个组件是因为这个判断已经出现在三处，
 * 分散写最容易出现的 bug 是「新加的接口忘了看开关」。
 *
 * @Author: CoolSkyAI
 * @Date: 2026-11-05
 */
@Component
public class ClientIpResolver {

	@Value("${security.trust-forwarded-for:false}")
	private boolean trustForwardedFor;

	public String resolve(HttpServletRequest request) {
		return trustForwardedFor ? IpAddressUtils.getIpAddress(request) : request.getRemoteAddr();
	}
}
