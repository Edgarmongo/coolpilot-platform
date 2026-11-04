package com.coolskyai.interceptor;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import com.coolskyai.modules.file.service.FileService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * @Description: 私有文件直链防护
 *
 * /image/** 是静态资源映射，不经过任何鉴权。
 * 如果私有文件（付费商品资源、用户上传的凭证）也落在同一个目录，
 * 任何人拿到 URL 就能下载，/file/token 的权益校验形同虚设。
 *
 * 因此私有文件统一存在 upload/_private/ 下，这里直接拒绝该前缀的直连请求，
 * 只允许通过 /file/download?token= 这条带校验的通道获取。
 *
 * @Author: CoolSkyAI
 * @Date: 2026-11-05
 */
@Component
public class ImageGuardInterceptor implements HandlerInterceptor {
	private static final String PRIVATE_SEGMENT = "/" + FileService.PRIVATE_PREFIX;

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
		String uri = request.getRequestURI();
		if (uri != null && uri.contains(PRIVATE_SEGMENT)) {
			response.setStatus(HttpServletResponse.SC_FORBIDDEN);
			response.setContentType("application/json;charset=UTF-8");
			response.getWriter().write("{\"code\":403,\"msg\":\"私有文件请通过下载凭证访问\",\"data\":null}");
			return false;
		}
		return true;
	}
}
