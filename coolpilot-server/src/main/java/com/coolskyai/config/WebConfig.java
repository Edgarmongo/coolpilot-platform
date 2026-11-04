package com.coolskyai.config;

import org.springframework.beans.factory.annotation.Autowired;
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
	 * 跨域请求
	 *
	 * @param registry
	 */
	@Override
	public void addCorsMappings(CorsRegistry registry) {
		registry.addMapping("/**")
				.allowedOrigins("*")
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
