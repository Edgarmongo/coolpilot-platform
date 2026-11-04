package com.coolskyai.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import com.coolskyai.config.properties.UploadProperties;
import com.coolskyai.interceptor.AccessLimitInterceptor;
import com.coolskyai.interceptor.ImageGuardInterceptor;

/**
 * @Description: 配置CORS跨域支持、拦截器
 * @Author: CoolSkyAI
 * @Date: 2020-07-22
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {
	@Autowired
	AccessLimitInterceptor accessLimitInterceptor;
	@Autowired
	ImageGuardInterceptor imageGuardInterceptor;
	@Autowired
	UploadProperties uploadProperties;

	/**
	 * 允许跨域的来源，逗号分隔；默认 * 保持旧行为。
	 * 生产环境应设为站点域名（见 docker-compose.prod.yml 的 CORS_ALLOWED_ORIGINS）。
	 */
	@Value("${cors.allowed-origins:*}")
	private String allowedOrigins;

	/**
	 * 跨域请求
	 *
	 * @param registry
	 */
	@Override
	public void addCorsMappings(CorsRegistry registry) {
		// 环境变量存在但为空时，@Value 会拿到空串而不是默认值，这里兜底回 *
		String[] origins = (allowedOrigins == null || allowedOrigins.trim().isEmpty())
				? new String[]{"*"}
				: allowedOrigins.split("\\s*,\\s*");
		/*
		 * R-10：默认值仍是 *，但生产通过环境变量收紧到站点域名。
		 * 认证走 Authorization 头而不是 Cookie，所以 * 不能带 allowCredentials，
		 * 也正因如此风险一直不高；但收紧后可以挡住「任意站点直接调接口」这类滥用。
		 */
		registry.addMapping("/**")
				.allowedOrigins(origins)
				.allowedHeaders("*")
				.allowedMethods("GET", "POST", "PUT", "DELETE", "HEAD", "OPTIONS")
				.maxAge(3600);
	}

	/**
	 * 请求拦截器
	 *
	 * @param registry
	 */
	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(accessLimitInterceptor);
		// /image/** 是静态资源，不走鉴权；这里单独挡住 _private/ 前缀
		registry.addInterceptor(imageGuardInterceptor)
				.addPathPatterns(uploadProperties.getAccessPath());
	}

	/**
	 * 本地静态资源路径映射
	 *
	 * @param registry
	 */
	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		registry.addResourceHandler(uploadProperties.getAccessPath()).addResourceLocations(uploadProperties.getResourcesLocations());
	}
}
