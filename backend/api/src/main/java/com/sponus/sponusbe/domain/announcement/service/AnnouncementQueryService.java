package com.sponus.sponusbe.domain.announcement.service;

import static com.sponus.sponusbe.domain.announcement.exception.AnnouncementErrorCode.*;
import static com.sponus.sponusbe.domain.organization.exception.CompanyErrorCode.*;
import static com.sponus.sponusbe.domain.organization.exception.OrganizationErrorCode.*;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.sponus.core.domain.announcement.repository.AnnouncementRepository;
import com.sponus.core.domain.organization.Organization;
import com.sponus.core.domain.organization.company.Company;
import com.sponus.core.domain.organization.repository.CompanyRepository;
import com.sponus.sponusbe.domain.announcement.cache.AnnouncementCacheLock;
import com.sponus.sponusbe.domain.announcement.cache.AnnouncementCacheMetrics;
import com.sponus.sponusbe.domain.announcement.cache.AnnouncementCacheProperties;
import com.sponus.sponusbe.domain.announcement.cache.AnnouncementDetailCacheStore;
import com.sponus.sponusbe.domain.announcement.dto.response.AnnouncementGetResponse;
import com.sponus.sponusbe.domain.announcement.exception.AnnouncementException;
import com.sponus.sponusbe.domain.organization.dto.request.PageCondition;
import com.sponus.sponusbe.domain.organization.dto.response.PageResponse;
import com.sponus.sponusbe.domain.organization.exception.OrganizationException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Service
public class AnnouncementQueryService {

	private static final long[] WAIT_BACKOFF_MS = {20, 50, 100};

	private final AnnouncementRepository announcementRepository;
	private final CompanyRepository companyRepository;

	private final AnnouncementDetailCacheStore cacheStore;
	private final AnnouncementCacheLock cacheLock;
	private final AnnouncementCacheMetrics metrics;
	private final AnnouncementCacheProperties props;

	// 공개 공고 목록 (동아리가 브라우징)
	public PageResponse<AnnouncementGetResponse> getAnnouncements(PageCondition pageCondition) {
		Pageable pageable = PageRequest.of(pageCondition.getPage() - 1, pageCondition.getSize());
		List<AnnouncementGetResponse> content = announcementRepository.findAllByOrderByCreatedAtDesc(pageable)
			.stream()
			.map(AnnouncementGetResponse::from)
			.toList();

		return PageResponse.of(
			PageableExecutionUtils.getPage(content, pageable, announcementRepository::count));
	}

	/**
	 * 공고 상세 — 수동 Cache Aside.
	 * hit 반환 → miss 시 공고별 재생성 락 획득 → double-check → DB 조회/캐시 저장 → 락 해제.
	 * 락 미획득 시 짧게 캐시가 채워지길 기다린 뒤 없으면 DB fallback. Redis 장애 시 즉시 DB fallback.
	 *
	 * <p>재생성 락 대기 동안 DB 커넥션을 잡지 않도록 트랜잭션을 열지 않는다(SUPPORTS).
	 */
	@Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
	public AnnouncementGetResponse getAnnouncement(Long announcementId) {
		// 1) 캐시 조회 (Redis 장애 → DB fallback)
		try {
			Optional<AnnouncementGetResponse> cached = cacheStore.get(announcementId);
			if (cached.isPresent()) {
				metrics.recordHit();
				return cached.get();
			}
		} catch (DataAccessException e) {
			metrics.recordRedisFailure();
			log.warn("[Cache] Redis 조회 실패 → DB fallback id={}", announcementId, e);
			return loadFromDb(announcementId);
		}
		metrics.recordMiss();

		// 완화 미사용(실험 모드): 모든 miss 가 DB 조회 → cache stampede 재현
		if (!props.isStampedeLockEnabled()) {
			return loadFromDbAndCache(announcementId);
		}

		// 2) 공고별 재생성 락 획득 시도
		String ownerToken = UUID.randomUUID().toString();
		boolean acquired;
		try {
			acquired = cacheLock.tryLock(announcementId, ownerToken, Duration.ofMillis(props.getLockLeaseMs()));
		} catch (DataAccessException e) {
			metrics.recordRedisFailure();
			return loadFromDb(announcementId);
		}

		if (acquired) {
			metrics.recordLockAcquired();
			try {
				// 3) double-check: 대기 중 다른 인스턴스가 이미 채웠을 수 있음
				Optional<AnnouncementGetResponse> afterLock = cacheStore.get(announcementId);
				if (afterLock.isPresent()) {
					return afterLock.get();
				}
				return loadFromDbAndCache(announcementId);
			} catch (DataAccessException e) {
				metrics.recordRedisFailure();
				return loadFromDb(announcementId);
			} finally {
				cacheLock.unlock(announcementId, ownerToken);
			}
		}

		// 4) 락 미획득 → 캐시가 채워지길 짧게 대기 → 없으면 DB fallback
		metrics.recordLockWait();
		Optional<AnnouncementGetResponse> populated = waitForCache(announcementId);
		if (populated.isPresent()) {
			return populated.get();
		}
		metrics.recordLockTimeout();
		return loadFromDb(announcementId);
	}

	// 기업 본인의 공고 목록
	public PageResponse<AnnouncementGetResponse> getMyAnnouncements(Organization authOrganization,
		PageCondition pageCondition) {
		if (!authOrganization.isCompany()) {
			throw new OrganizationException(ORGANIZATION_UNAUTHORIZED_ONLY_COMPANY_AUTHORIZED);
		}
		Company company = companyRepository.findById(authOrganization.getId())
			.orElseThrow(() -> new OrganizationException(COMPANY_NOT_FOUND));

		Pageable pageable = PageRequest.of(pageCondition.getPage() - 1, pageCondition.getSize());
		List<AnnouncementGetResponse> content = announcementRepository.findByCompanyOrderByCreatedAtDesc(
				company, pageable)
			.stream()
			.map(AnnouncementGetResponse::from)
			.toList();

		return PageResponse.of(
			PageableExecutionUtils.getPage(content, pageable, () -> announcementRepository.countByCompany(company)));
	}

	// ── 내부 헬퍼 ───────────────────────────────────────────────

	private AnnouncementGetResponse loadFromDbAndCache(Long announcementId) {
		AnnouncementGetResponse response = loadFromDb(announcementId);
		cacheStore.put(announcementId, response, cacheTtl());   // put 실패는 store 내부에서 무시(DB 결과 반환)
		return response;
	}

	private AnnouncementGetResponse loadFromDb(Long announcementId) {
		metrics.recordDbLoad();
		sleepSimulatedCost();
		// company 를 fetch join 으로 함께 로드 → 트랜잭션 밖에서도 DTO 변환 가능(LazyInitialization 방지)
		return announcementRepository.findWithCompanyById(announcementId)
			.map(AnnouncementGetResponse::from)
			.orElseThrow(() -> new AnnouncementException(ANNOUNCEMENT_NOT_FOUND));
	}

	// 락 미획득 요청: 락을 다시 잡지 않고 캐시가 채워졌는지만 짧고 제한된 시간 동안 확인
	private Optional<AnnouncementGetResponse> waitForCache(Long announcementId) {
		long budgetNs = props.getWaitBudgetMs() * 1_000_000L;
		long start = System.nanoTime();
		int step = 0;
		while (System.nanoTime() - start < budgetNs) {
			long base = WAIT_BACKOFF_MS[Math.min(step, WAIT_BACKOFF_MS.length - 1)];
			try {
				Thread.sleep(base + ThreadLocalRandom.current().nextLong(10));
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return Optional.empty();
			}
			step++;
			try {
				Optional<AnnouncementGetResponse> cached = cacheStore.get(announcementId);
				if (cached.isPresent()) {
					return cached;
				}
			} catch (DataAccessException e) {
				metrics.recordRedisFailure();
				return Optional.empty();   // Redis 장애 → 호출자가 DB fallback
			}
		}
		return Optional.empty();
	}

	// TTL = 기본값 + 0~jitter(초) 무작위 → 동시 만료 분산
	private Duration cacheTtl() {
		long jitter = props.getJitterSeconds() > 0
			? ThreadLocalRandom.current().nextLong(props.getJitterSeconds() + 1)
			: 0;
		return Duration.ofSeconds(props.getTtlSeconds() + jitter);
	}

	private void sleepSimulatedCost() {
		if (props.getSimulatedDbCostMs() > 0) {
			try {
				Thread.sleep(props.getSimulatedDbCostMs());
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
	}
}
