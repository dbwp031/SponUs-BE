package com.sponus.storage.redis;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories;

@Configuration
@EnableRedisRepositories("com.sponus.storage.redis")
public class CoreRedisConfig {
}
