package com.coolskyai.config;

import org.springframework.security.authentication.AccountExpiredException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;
import com.coolskyai.constant.JwtConstants;
import com.coolskyai.entity.LoginLog;
import com.coolskyai.entity.User;
import com.coolskyai.exception.BadRequestException;
import com.coolskyai.model.vo.Result;
import com.coolskyai.service.LoginLogService;
import com.coolskyai.util.IpAddressUtils;
import com.coolskyai.util.JacksonUtils;
import com.coolskyai.util.JwtUtils;
import com.coolskyai.util.StringUtils;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;

/**
 * @Description: JWT登录过滤器
 * @Author: CoolSkyAI
 * @Date: 2020-07-21
 */
public class JwtLoginFilter extends AbstractAuthenticationProcessingFilter {
	/** 登录请求路径 */
	private static final String LOGIN_PROCESSES_URL = "/admin/login";

	LoginLogService loginLogService;
	ThreadLocal<String> currentUsername = new ThreadLocal<>();

	protected JwtLoginFilter(AuthenticationManager authenticationManager, LoginLogService loginLogService) {
		// Spring Security 7 已废弃 AntPathRequestMatcher，这里改用函数式 RequestMatcher
		super(request -> LOGIN_PROCESSES_URL.equals(request.getServletPath()));
		setAuthenticationManager(authenticationManager);
		this.loginLogService = loginLogService;
	}

	@Override
	public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response)
			throws AuthenticationException, IOException {
		try {
			if (!"POST".equals(request.getMethod())) {
				throw new BadRequestException("请求方法错误");
			}
			User user = JacksonUtils.readValue(request.getInputStream(), User.class);
			//请求体为空或不是合法JSON时，readValue内部吞掉异常并返回null，
			//此处必须显式拦截，否则下面user.getUsername()会抛NPE，接口返回500而不是400
			if (user == null) {
				throw new BadRequestException("请求体格式错误");
			}
			if (StringUtils.isEmpty(user.getUsername()) || StringUtils.isEmpty(user.getPassword())) {
				throw new BadRequestException("用户名或密码不能为空");
			}
			currentUsername.set(user.getUsername());
			return getAuthenticationManager().authenticate(new UsernamePasswordAuthenticationToken(user.getUsername(), user.getPassword()));
		} catch (BadRequestException exception) {
			//非法请求不会进入登录成功/失败回调，需在此清空ThreadLocal，避免线程复用时的数据残留
			currentUsername.remove();
			response.setContentType("application/json;charset=utf-8");
			Result result = Result.create(400, "非法请求");
			PrintWriter out = response.getWriter();
			out.write(JacksonUtils.writeValueAsString(result));
			out.flush();
			out.close();
		}
		return null;
	}

	@Override
	protected void successfulAuthentication(HttpServletRequest request, HttpServletResponse response,
	                                        FilterChain chain, Authentication authResult) throws IOException {
		String jwt = JwtUtils.generateToken(JwtConstants.ADMIN_PREFIX + authResult.getName(), authResult.getAuthorities());
		response.setContentType("application/json;charset=utf-8");
		User user = (User) authResult.getPrincipal();
		user.setPassword(null);
		Map<String, Object> map = new HashMap<>(4);
		map.put("user", user);
		map.put("token", jwt);
		Result result = Result.ok("登录成功", map);
		PrintWriter out = response.getWriter();
		out.write(JacksonUtils.writeValueAsString(result));
		out.flush();
		out.close();
		LoginLog log = handleLog(request, true, "登录成功");
		loginLogService.saveLoginLog(log);
	}

	@Override
	protected void unsuccessfulAuthentication(HttpServletRequest request, HttpServletResponse response,
	                                          AuthenticationException exception) throws IOException {
		response.setContentType("application/json;charset=utf-8");
		String msg = exception.getMessage();
		//登录不成功时，会抛出对应的异常
		if (exception instanceof LockedException) {
			msg = "账号被锁定";
		} else if (exception instanceof CredentialsExpiredException) {
			msg = "密码过期";
		} else if (exception instanceof AccountExpiredException) {
			msg = "账号过期";
		} else if (exception instanceof DisabledException) {
			msg = "账号被禁用";
		} else if (exception instanceof BadCredentialsException) {
			msg = "用户名或密码错误";
		}
		PrintWriter out = response.getWriter();
		out.write(JacksonUtils.writeValueAsString(Result.create(401, msg)));
		out.flush();
		out.close();
		LoginLog log = handleLog(request, false, StringUtils.substring(msg, 0, 50));
		loginLogService.saveLoginLog(log);
	}

	/**
	 * 设置LoginLog对象属性
	 *
	 * @param request     请求对象
	 * @param status      登录状态
	 * @param description 操作描述
	 * @return
	 */
	private LoginLog handleLog(HttpServletRequest request, boolean status, String description) {
		String username = currentUsername.get();
		currentUsername.remove();
		String ip = IpAddressUtils.getIpAddress(request);
		String userAgent = request.getHeader("User-Agent");
		LoginLog log = new LoginLog(username, ip, status, description, userAgent);
		return log;
	}
}