package com.coolskyai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import com.coolskyai.constant.Role;
import com.coolskyai.constant.SecurityPaths;
import com.coolskyai.service.LoginLogService;
import com.coolskyai.service.impl.UserServiceImpl;

/**
 * @Description: Spring Security配置类
 * @Author: CoolSkyAI
 * @Date: 2020-07-19
 *
 * 升级说明：Spring Security 6 起移除了 WebSecurityConfigurerAdapter，
 * 改为声明 SecurityFilterChain / AuthenticationManager 两个 Bean；
 * antMatchers 亦更名为 requestMatchers。
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {
	private final UserServiceImpl userService;
	private final LoginLogService loginLogService;
	private final MyAuthenticationEntryPoint myAuthenticationEntryPoint;
	private final MyAccessDeniedHandler myAccessDeniedHandler;

	public SecurityConfig(UserServiceImpl userService,
	                      LoginLogService loginLogService,
	                      MyAuthenticationEntryPoint myAuthenticationEntryPoint,
	                      MyAccessDeniedHandler myAccessDeniedHandler) {
		this.userService = userService;
		this.loginLogService = loginLogService;
		this.myAuthenticationEntryPoint = myAuthenticationEntryPoint;
		this.myAccessDeniedHandler = myAccessDeniedHandler;
	}

	@Bean
	public BCryptPasswordEncoder bCryptPasswordEncoder() {
		return new BCryptPasswordEncoder();
	}

	/**
	 * UserServiceImpl 实现了 UserDetailsService 且注册为 Bean，
	 * Spring Security 会自动据此装配 DaoAuthenticationProvider，
	 * 并配合上面的 PasswordEncoder 完成密码校验，无需再手动 configure(AuthenticationManagerBuilder)。
	 */
	@Bean
	public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
		return configuration.getAuthenticationManager();
	}

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http, AuthenticationManager authenticationManager) throws Exception {
		http
				//禁用 csrf 防御
				.csrf(AbstractHttpConfigurer::disable)
				//开启跨域支持
				.cors(cors -> {
				})
				//基于Token，不创建会话
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth
						//放行获取网页标题后缀的请求
						.requestMatchers("/admin/webTitleSuffix").permitAll()
						/*
						 * 后台管理接口：仅平台所有者（ROLE_owner）。
						 *
						 * 绝不能再对 ROLE_user 开放任何 /admin/** 读权限：
						 * 前台支持自助注册，一旦放行，任何注册用户都能读到后台数据。
						 */
						.requestMatchers(SecurityPaths.OWNER_ONLY).hasAuthority(Role.OWNER)
						/*
						 * 前台用户级接口：登录即可（所有者与前台用户同权）。
						 *
						 * 路径清单集中在 SecurityPaths.AUTHENTICATED，新增接口只改那一处；
						 * 漏加的请求会被 anyRequest().permitAll() 静默放行，这是历史踩过的坑。
						 */
						.requestMatchers(SecurityPaths.AUTHENTICATED).hasAnyAuthority(Role.OWNER, Role.USER)
						//其它路径全部放行（含 /visitor/register、/visitor/login、/feedback、/resource/access）
						.anyRequest().permitAll()
				)
				//自定义JWT过滤器
				.addFilterBefore(new JwtLoginFilter(authenticationManager, loginLogService), UsernamePasswordAuthenticationFilter.class)
				.addFilterBefore(new JwtFilter(), UsernamePasswordAuthenticationFilter.class)
				//未登录时返回“请登录”，已登录但权限不足返回“无权限访问”，均保持统一 JSON 结构
				.exceptionHandling(exception -> exception
						.authenticationEntryPoint(myAuthenticationEntryPoint)
						.accessDeniedHandler(myAccessDeniedHandler));
		return http.build();
	}
}
