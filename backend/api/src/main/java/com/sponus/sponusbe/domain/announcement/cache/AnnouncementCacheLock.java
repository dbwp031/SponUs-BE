package com.sponus.sponusbe.domain.announcement.cache;

import java.time.Duration;
import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import com.sponus.storage.redis.config.CacheRedisConfig;

/**
 * 공고별 캐시 재생성 락. {@code SET key token NX PX lease} 로 획득하고,
 * 소유자 토큰을 Lua 로 확인한 뒤 삭제해 만료 후 남의 락을 지우는 사고를 막는다.
 *
 * <p>결제/재고 정합성용 분산락이 아니라 중복 DB 조회를 줄이기 위한 best-effort 락이다.
 * 락이 완벽히 동작하지 않아 DB 조회가 몇 번 더 되더라도 데이터 정합성은 깨지지 않는다.
 */
@Component
public class AnnouncementCacheLock {

	private static final String LOCK_PREFIX = "lock:cache:v1:announcement:detail:";

	// 소유자 토큰이 일치할 때만 삭제 (안전한 락 해제)
	private static final RedisScript<Long> UNLOCK_SCRIPT = RedisScript.of(
		"if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
		Long.class);

	private final RedisTemplate<String, String> redis;

	public AnnouncementCacheLock(
		@Qualifier(CacheRedisConfig.CACHE_REDIS_TEMPLATE) RedisTemplate<String, String> redis) {
		this.redis = redis;
	}

	// SET NX PX — Redis 장애 시 DataAccessException 전파(호출자가 DB fallback)
	public boolean tryLock(Long announcementId, String ownerToken, Duration lease) {
		Boolean acquired = redis.opsForValue().setIfAbsent(lockKey(announcementId), ownerToken, lease);
		return Boolean.TRUE.equals(acquired);
	}

	public void unlock(Long announcementId, String ownerToken) {
		try {
			redis.execute(UNLOCK_SCRIPT, List.of(lockKey(announcementId)), ownerToken);
		} catch (Exception e) {
			// 해제 실패해도 lease TTL 로 자동 만료 — best-effort
		}
	}

	private String lockKey(Long announcementId) {
		return LOCK_PREFIX + announcementId;
	}
}
