package com.sponus.sponusbe.domain.propose.service;

import org.springframework.stereotype.Service;

import com.sponus.core.domain.announcement.Announcement;
import com.sponus.core.domain.announcement.repository.AnnouncementRepository;
import com.sponus.core.domain.organization.Organization;
import com.sponus.storage.redis.lock.DistributedLockExecutor;
import com.sponus.storage.redis.lock.LockAcquisitionException;
import com.sponus.sponusbe.domain.announcement.exception.AnnouncementErrorCode;
import com.sponus.sponusbe.domain.announcement.exception.AnnouncementException;
import com.sponus.sponusbe.domain.propose.dto.request.ProposeCreateRequest;
import com.sponus.sponusbe.domain.propose.dto.response.ProposeCreateResponse;
import com.sponus.sponusbe.domain.propose.exception.ProposeErrorCode;
import com.sponus.sponusbe.domain.propose.exception.ProposeException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * "따닥" (연속 클릭) 으로 동일 제휴 요청이 동시에 생성되는 것을 막는다.
 * 사용자 ID + 대상 기업 ID + 공고 ID 를 조합한 멱등 키로 Redisson 분산락을 잡은 뒤
 * 트랜잭션(ProposeService)을 실행하며, 락은 트랜잭션 커밋 이후에 해제된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProposeLockFacade {

	private static final String PROPOSE_LOCK_KEY_FORMAT = "propose:lock:%d:%d:%d";
	private static final long LOCK_WAIT_SECONDS = 3L;
	private static final long LOCK_LEASE_SECONDS = 10L;

	private final ProposeService proposeService;
	private final AnnouncementRepository announcementRepository;
	private final DistributedLockExecutor distributedLockExecutor;

	public ProposeCreateResponse createPropose(Organization applicant, ProposeCreateRequest request) {
		final Announcement announcement = announcementRepository.findById(request.announcementId())
			.orElseThrow(() -> new AnnouncementException(AnnouncementErrorCode.ANNOUNCEMENT_NOT_FOUND));

		final String lockKey = String.format(PROPOSE_LOCK_KEY_FORMAT,
			applicant.getId(), announcement.getCompany().getId(), request.announcementId());

		try {
			return distributedLockExecutor.execute(lockKey, LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS,
				() -> proposeService.createPropose(applicant, request));
		} catch (LockAcquisitionException e) {
			log.warn("[*] Duplicate propose request blocked by distributed lock: {}", lockKey);
			throw new ProposeException(ProposeErrorCode.PROPOSE_REQUEST_IN_PROGRESS);
		}
	}
}
