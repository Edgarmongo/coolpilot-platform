package com.coolskyai.config;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import com.coolskyai.model.vo.Result;
import com.coolskyai.util.JacksonUtils;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * @Description: 已登录但权限不足时返回统一的 JSON 结构
 * 例如前台用户（ROLE_user）持有效 token 访问 /admin/** 时，
 * 若不处理会落到 Spring Security 默认的 403 页面，前端拿不到 msg 字段。
 *
 * @Author: CoolSkyAI
 */
@Component
public class MyAccessDeniedHandler implements AccessDeniedHandler {
	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException exception)
			throws IOException {
		response.setContentType("application/json;charset=utf-8");
		PrintWriter out = response.getWriter();
		Result result = Result.create(403, "无权限访问");
		out.write(JacksonUtils.writeValueAsString(result));
		out.flush();
		out.close();
	}
}
