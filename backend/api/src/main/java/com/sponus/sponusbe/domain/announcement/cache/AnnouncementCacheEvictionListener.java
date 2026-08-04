package com.sponus.sponusbe.domain.announcement.cache;

import java.time.Instant;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.sponus.sponusbe.domain.announcement.event.AnnouncementChangedEvent;

import lombok.extern.slf4j.Slf4j;

/**
 * 공고 변경 시 커밋 이후 캐시를 무효화한다(delayed double delete).
 *
 * <ul>
 *   <li>AFTER_COMMIT 즉시 삭제 — 롤백 시 캐시만 지워지는 문제를 피한다.</li>
 *   <li>지연 2차 삭제 — 커밋~즉시삭제 사이에 느린 조회가 이전 값을 재삽입한 경우를 제거한다.</li>
 * </ul>
 * 삭제는 멱등하며, 예약 유실에 대비해 캐시 TTL 이 최종 안전장치다.
 */
@Slf4j
@Component
public class AnnouncementCacheEvictionListener {

	private final AnnouncementDetailCacheStore cacheStore;
	private final AnnouncementCacheProperties props;
	private final TaskScheduler scheduler;

	public AnnouncementCacheEvictionListener(
		AnnouncementDetailCacheStore cacheStore,
		AnnouncementCacheProperties props,
		@Qualifier(AnnouncementCacheSchedulerConfig.CACHE_SCHEDULER) TaskScheduler scheduler) {
		this.cacheStore = cacheStore;
		this.props = props;
		this.scheduler = scheduler;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onAnnouncementChanged(AnnouncementChangedEvent event) {
		Long id = event.announcementId();

		// 즉시 삭제
		cacheStore.evict(id);

		// 지연 2차 삭제 예약
		long delayMs = props.getDoubleDeleteDelayMs();
		try {
			scheduler.schedule(() -> {
				cacheStore.evict(id);
				log.info("[Cache] delayed double delete 완료 id={}", id);
			}, Instant.now().plusMillis(delayMs));
			log.info("[Cache] AFTER_COMMIT 무효화 id={}, {}ms 후 2차 삭제 예약", id, delayMs);
		} catch (Exception e) {
			log.warn("[Cache] 2차 삭제 예약 실패 id={} (TTL 로 최종 만료)", id, e);
		}
	}
}
