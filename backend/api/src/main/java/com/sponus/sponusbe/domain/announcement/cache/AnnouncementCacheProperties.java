package com.sponus.sponusbe.domain.announcement.cache;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

/**
 * 공고 상세 캐시 관련 설정. (prefix: announcement.cache)
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "announcement.cache")
public class AnnouncementCacheProperties {

	// 캐시 TTL 기본값(초). 운영 기본 5분.
	private long ttlSeconds = 300;

	// TTL 지터 상한(초). 0~jitter 만큼 무작위 가산해 동시 만료를 분산. 0 이면 미적용.
	private long jitterSeconds = 60;

	// 재생성 락 lease(ms). DB조회 p99 + 변환 + 캐시저장 + 여유보다 길어야 함.
	private long lockLeaseMs = 3000;

	// 즉시 획득 실패 시 Redisson Pub/Sub 기반 락 대기 시간(ms). 초과 시 캐시 재확인 후 DB fallback.
	private long waitBudgetMs = 300;

	// 커밋 이후 delayed double delete 의 2차 삭제 지연(ms).
	private long doubleDeleteDelayMs = 1000;

	// 재생성 락 사용 여부. false 로 두면 완화 없이(모든 miss가 DB조회) stampede 재현.
	private boolean stampedeLockEnabled = true;

	// 실험용: DB 조회 비용(무거운 조회 흉내, ms). 운영 기본 0.
	private long simulatedDbCostMs = 0;
}
