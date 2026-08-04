package com.sponus.sponusbe.domain.announcement.cache;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import com.sponus.storage.redis.config.CacheRedisConfig;

/**
 * 공고 상세 캐시 계측. 여러 인스턴스 합산을 보기 위해 Redis 카운터로 집계한다.
 * 공고 ID 는 태그로 넣지 않는다(high-cardinality 회피). 메트릭 실패가 요청을 막지 않도록 예외를 삼킨다.
 */
@Component
public class AnnouncementCacheMetrics {

	private static final String PREFIX = "metrics:announcement.detail.cache.";

	private final RedisTemplate<String, String> redis;

	public AnnouncementCacheMetrics(
		@Qualifier(CacheRedisConfig.CACHE_REDIS_TEMPLATE) RedisTemplate<String, String> redis) {
		this.redis = redis;
	}

	public void recordHit() {
		inc("hit");
	}

	public void recordMiss() {
		inc("miss");
	}

	public void recordRedisFailure() {
		inc("redis-error");
	}

	public void recordLockAcquired() {
		inc("lock-acquired");
	}

	public void recordLockWait() {
		inc("lock-wait");
	}

	public void recordLockTimeout() {
		inc("lock-timeout");
	}

	public void recordDbLoad() {
		inc("db-load");
	}

	private void inc(String name) {
		try {
			redis.opsForValue().increment(PREFIX + name);
		} catch (Exception ignored) {
			// 메트릭은 best-effort — Redis 장애 시에도 요청 흐름을 방해하지 않는다.
		}
	}
}
