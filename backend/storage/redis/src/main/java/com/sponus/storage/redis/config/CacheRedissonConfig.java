package com.sponus.storage.redis.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 캐시 재생성 락 전용 클라이언트. Redis 장애 시 짧게 실패하고 DB로 우회하도록
 * 명령 재시도를 끈다. 기존 지원 생성용 Redisson 클라이언트 설정은 유지한다.
 */
@Configuration
public class CacheRedissonConfig {

	public static final String CACHE_REDISSON_CLIENT = "announcementCacheRedissonClient";

	@Bean(name = CACHE_REDISSON_CLIENT, destroyMethod = "shutdown")
	public RedissonClient announcementCacheRedissonClient(
		@Value("${spring.data.redis.host}") String host,
		@Value("${spring.data.redis.port}") int port,
		@Value("${announcement.cache.redis-command-timeout-ms:250}") int timeoutMs) {
		Config config = new Config();
		config.useSingleServer()
			.setAddress("redis://" + host + ":" + port)
			.setConnectTimeout(timeoutMs)
			.setTimeout(timeoutMs)
			.setRetryAttempts(0);
		return Redisson.create(config);
	}
}
