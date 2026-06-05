package com.sponus.storage.redis.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

@Configuration
public class RedissonConfig {

	private static final String REDIS_URL_FORMAT = "redis://%s:%d";

	@Value("${spring.data.redis.host}")
	private String redisHost;

	@Value("${spring.data.redis.port}")
	private int redisPort;

	// Redisson 은 빈 생성 시점에 즉시 접속하므로, Redis 없이 컨텍스트를 띄우는 환경(테스트)을 위해 지연 초기화한다.
	@Lazy
	@Bean(destroyMethod = "shutdown")
	public RedissonClient redissonClient() {
		Config config = new Config();
		config.useSingleServer().setAddress(String.format(REDIS_URL_FORMAT, redisHost, redisPort));
		return Redisson.create(config);
	}
}
