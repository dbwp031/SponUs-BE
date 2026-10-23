package com.sponus.sponusbe.domain.announcement.cache;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.redisson.api.RedissonClient;
import org.redisson.client.RedisException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import com.sponus.storage.redis.config.CacheRedissonConfig;

import lombok.extern.slf4j.Slf4j;

/**
 * 공고별 Redisson RLock. 경합 시 Pub/Sub 알림과 TTL을 이용해 제한된 시간 동안 기다린다.
 * 획득/해제는 같은 요청 스레드에서 수행하며, 명시적 lease를 사용하므로 watchdog은 적용되지 않는다.
 *
 * <p>결제/재고 정합성용 분산락이 아니라 중복 DB 조회를 줄이기 위한 best-effort 락이다.
 * 락이 완벽히 동작하지 않아 DB 조회가 몇 번 더 되더라도 데이터 정합성은 깨지지 않는다.
 */
@Component
@Slf4j
public class AnnouncementCacheLock {

	// 기존 SET NX 문자열 키와 Redisson hash 키의 타입 충돌을 피한다.
	private static final String LOCK_PREFIX = "lock:cache:v2:announcement:detail:";

	private final RedissonClient redisson;

	public AnnouncementCacheLock(
		@Qualifier(CacheRedissonConfig.CACHE_REDISSON_CLIENT) RedissonClient redisson) {
		this.redisson = redisson;
	}

	public boolean tryLock(Long announcementId, Duration wait, Duration lease) throws InterruptedException {
		return redisson.getLock(lockKey(announcementId))
			.tryLock(wait.toMillis(), lease.toMillis(), TimeUnit.MILLISECONDS);
	}

	public void unlock(Long announcementId) {
		try {
			// Redisson이 소유권을 원자적으로 확인한다. forceUnlock은 사용하지 않는다.
			redisson.getLock(lockKey(announcementId)).unlock();
		} catch (IllegalMonitorStateException e) {
			log.warn("[Cache] 재생성 락 lease 만료 또는 소유권 상실 id={}", announcementId);
		} catch (RedisException e) {
			// 캐시 결과 반환을 막지 않는다. 남은 락은 lease TTL로 만료된다.
			log.warn("[Cache] 재생성 락 해제 실패 id={}", announcementId, e);
		}
	}

	private String lockKey(Long announcementId) {
		return LOCK_PREFIX + announcementId;
	}
}
