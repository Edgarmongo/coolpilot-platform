package com.coolskyai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;

/**
 * @Description: Redis序列化配置
 * @Author: Naccl
 * @Date: 2020-09-27
 *
 * 升级说明：Spring Boot 4 默认使用 Jackson 3，
 * Spring Data Redis 4 中原 Jackson2JsonRedisSerializer 需要 Jackson 2 运行时依赖，
 * 这里改用与之对应的 Jackson 3 版本 JacksonJsonRedisSerializer，序列化后的 JSON 格式保持不变。
 */
@Configuration
public class RedisSerializeConfig {

	/**
	 * 使用JSON序列化方式
	 *
	 * @param redisConnectionFactory
	 * @return
	 */
	@Bean
	public RedisTemplate<Object, Object> jsonRedisTemplate(RedisConnectionFactory redisConnectionFactory) {
		RedisTemplate<Object, Object> template = new RedisTemplate<>();
		template.setConnectionFactory(redisConnectionFactory);
		JacksonJsonRedisSerializer<Object> serializer = new JacksonJsonRedisSerializer<>(Object.class);
		template.setDefaultSerializer(serializer);
		return template;
	}
}
