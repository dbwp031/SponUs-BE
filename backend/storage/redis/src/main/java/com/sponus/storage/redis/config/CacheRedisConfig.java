package com.sponus.storage.redis.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * 캐시 전용 String-String RedisTemplate.
 *
 * <p>기존 {@code redisTemplate}(RedisTemplate&lt;String,Object&gt;)은 Refresh Token / 인증 코드 /
 * FCM Token 에 쓰이므로 건드리지 않고, 공고 상세 캐시(JSON)와 재생성 락은 이 전용 템플릿을 사용한다.
 *
 * <p>캐시는 가용성을 낮추면 안 되므로 <b>짧은 command timeout</b> 을 가진 전용 커넥션을 사용한다.
 * Redis 장애 시 길게 blocking 되지 않고 빠르게 예외를 던져 호출자가 DB fallback 하도록 한다.
 * (전용 팩토리는 Bean 으로 노출하지 않아 기존 RedisConnectionFactory 오토와이어링과 충돌하지 않는다.)
 */
@Configuration
public class CacheRedisConfig {

	public static final String CACHE_REDIS_TEMPLATE = "announcementCacheRedisTemplate";

	@Value("${spring.data.redis.host}")
	private String host;

	@Value("${spring.data.redis.port}")
	private int port;

	@Value("${announcement.cache.redis-command-timeout-ms:250}")
	private long commandTimeoutMs;

	@Bean(CACHE_REDIS_TEMPLATE)
	public RedisTemplate<String, String> announcementCacheRedisTemplate() {
		LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
			.commandTimeout(Duration.ofMillis(commandTimeoutMs))
			.build();
		LettuceConnectionFactory connectionFactory =
			new LettuceConnectionFactory(new RedisStandaloneConfiguration(host, port), clientConfig);
		connectionFactory.afterPropertiesSet();
		connectionFactory.start();

		RedisTemplate<String, String> template = new RedisTemplate<>();
		template.setConnectionFactory(connectionFactory);
		StringRedisSerializer serializer = new StringRedisSerializer();
		template.setKeySerializer(serializer);
		template.setValueSerializer(serializer);
		template.setHashKeySerializer(serializer);
		template.setHashValueSerializer(serializer);
		template.afterPropertiesSet();
		return template;
	}
}
