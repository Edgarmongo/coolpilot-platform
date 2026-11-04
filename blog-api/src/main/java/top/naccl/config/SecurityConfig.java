package top.naccl.config;

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
import top.naccl.service.LoginLogService;
import top.naccl.service.impl.UserServiceImpl;

/**
 * @Description: Spring Security配置类
 * @Author: Naccl
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
						 * 任何 /admin 开头的路径下的请求都需要经过JWT验证，且仅限博主本人。
						 * 注意：这里不再对 visitor 角色开放读权限。
						 * 因为前台已支持访客自助注册（ROLE_visitor），若保留
						 * hasAnyRole("admin","visitor")，任何注册用户都能读取后台接口数据。
						 */
						.requestMatchers("/admin/**").hasRole("admin")
						/*
						 * 前台用户级接口：必须是已登录用户（博主或访客）。
						 * 注意必须用 hasAnyRole 而不是 hasRole("visitor")：
						 * 博主角色是 ROLE_admin，不包含 ROLE_visitor，但博主同样可以使用这些功能。
						 *
						 * 新增用户级接口时，务必在此处补充路径，否则默认会被 anyRequest().permitAll() 放行。
						 */
						.requestMatchers("/me", "/me/**").hasAnyRole("admin", "visitor")
						/*
						 * 以上是 V1.0 社区模块的用户级接口：收藏、点赞、站内消息。
						 * 新增同类接口时必须同步补充，否则会被 anyRequest().permitAll() 放行。
						 * /feedback 有意保持在下方放行列表中：允许未登录用户提交问题反馈。
						 */
						.requestMatchers("/favorite", "/favorite/**", "/favorites").hasAnyRole("admin", "visitor")
						.requestMatchers("/like", "/like/**").hasAnyRole("admin", "visitor")
						.requestMatchers("/messages", "/messages/**", "/message/**").hasAnyRole("admin", "visitor")
						//文件：换下载凭证、用户上传凭证都需要登录；/file/download 靠 token 自身校验，保持放行
						.requestMatchers("/file/token", "/file/upload").hasAnyRole("admin", "visitor")
						/*
						 * V1.1b 交易与权益：下单、支付、退款、我的权益都必须登录。
						 * 归属校验（只能看自己的订单）在 Service 层做，这里只挡未登录请求。
						 * /resource/access 有意放行：未登录时返回 NOT_LOGIN 而不是 401，
						 * 前端可以据此直接显示"登录后购买"。
						 */
						.requestMatchers("/order", "/orders", "/order/**").hasAnyRole("admin", "visitor")
						.requestMatchers("/payment", "/payment/**").hasAnyRole("admin", "visitor")
						.requestMatchers("/refund", "/refunds", "/refund/**").hasAnyRole("admin", "visitor")
						.requestMatchers("/entitlements").hasAnyRole("admin", "visitor")
						//其它路径全部放行（含 /visitor/register、/visitor/login、/feedback）
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
