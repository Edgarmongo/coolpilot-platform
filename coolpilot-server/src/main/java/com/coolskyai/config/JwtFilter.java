package com.coolskyai.config;

import io.jsonwebtoken.Claims;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.GenericFilterBean;
import com.coolskyai.constant.JwtConstants;
import com.coolskyai.constant.Role;
import com.coolskyai.model.vo.Result;
import com.coolskyai.modules.account.service.TokenVersionService;
import com.coolskyai.util.JacksonUtils;
import com.coolskyai.util.JwtUtils;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

/**
 * @Description: JWT请求过滤器
 *
 * 变更说明（V1.0）：
 * 原实现只处理 /admin 开头的路径，其余请求直接放行，导致前台访客 token 签发后
 * 没有任何地方消费，SecurityContext 始终为空，无法实现"我的收藏/订单/消息"等用户级功能。
 *
 * 现改为：所有路径都尝试解析 token。
 * - 命中合法业务 token（owner: / user: 前缀，兼容历史 admin: / visitor:）→ 写入 SecurityContext，
 *   并顺带把 authorities 里的历史角色值归一化为 ROLE_owner / ROLE_user
 * - 未携带 token → 放行，按匿名处理
 * - token 非法/过期/已吊销 → /admin 路径返回 403（保持原行为）；其他路径按匿名放行，
 *   避免把历史的"密码文章 token"（subject 是 blogId）误判为登录态
 *
 * 吊销（R-11）：token 里带 tv（身份版本号），与 Redis 里的当前版本不一致即视为失效。
 * 改密码、主动登出会 bump 版本，所有旧 token 当刻作废，不必等 3 天自然过期。
 *
 * @Author: CoolSkyAI
 * @Date: 2020-07-21
 */
public class JwtFilter extends GenericFilterBean {

	private final TokenVersionService tokenVersionService;

	public JwtFilter(TokenVersionService tokenVersionService) {
		this.tokenVersionService = tokenVersionService;
	}

	@Override
	public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
		HttpServletRequest request = (HttpServletRequest) servletRequest;
		HttpServletResponse response = (HttpServletResponse) servletResponse;

		boolean isAdminPath = request.getRequestURI().startsWith(request.getContextPath() + "/admin");
		String jwt = request.getHeader("Authorization");

		if (JwtUtils.judgeTokenIsExist(jwt)) {
			try {
				Claims claims = JwtUtils.getTokenBody(jwt);
				String subject = claims.getSubject();
				// 只有带业务前缀的 token 才代表登录身份；密码文章 token 的 subject 是文章ID，不参与身份识别
				if (JwtConstants.isAuthSubject(subject)) {
					long version = JwtUtils.tokenVersion(claims);
					if (version != tokenVersionService.current(subject)) {
						// 已吊销（改过密码 / 主动登出）：后台路径直接拒绝，前台按匿名处理
						if (isAdminPath) {
							writeInvalidToken(response);
							return;
						}
					} else {
						String authoritiesClaim = (String) claims.get("authorities");
						List<GrantedAuthority> authorities = AuthorityUtils
								.commaSeparatedStringToAuthorityList(
										Role.normalizeAuthorities(authoritiesClaim));
						UsernamePasswordAuthenticationToken authentication =
								new UsernamePasswordAuthenticationToken(subject, null, authorities);
						authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
						SecurityContextHolder.getContext().setAuthentication(authentication);
					}
				}
			} catch (Exception e) {
				// 后台路径保持原有行为：凭证失效立即拒绝
				if (isAdminPath) {
					writeInvalidToken(response);
					return;
				}
				// 前台路径：忽略非法 token，继续按匿名访问处理
			}
		}
		filterChain.doFilter(servletRequest, servletResponse);
	}

	private void writeInvalidToken(HttpServletResponse response) throws IOException {
		response.setContentType("application/json;charset=utf-8");
		Result result = Result.create(403, "凭证已失效，请重新登录！");
		PrintWriter out = response.getWriter();
		out.write(JacksonUtils.writeValueAsString(result));
		out.flush();
		out.close();
	}
}
