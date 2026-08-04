package com.sponus.sponusbe.domain.propose.service;

import java.io.IOException;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import com.sponus.clients.fcm.FirebaseUtil;
import com.sponus.core.domain.announcement.Announcement;
import com.sponus.core.domain.announcement.repository.AnnouncementRepository;
import com.sponus.core.domain.notification.NotificationStatus;
import com.sponus.core.domain.organization.Organization;
import com.sponus.core.domain.organization.company.Company;
import com.sponus.core.domain.propose.Propose;
import com.sponus.core.domain.propose.repository.ProposeRepository;
import com.sponus.storage.redis.lock.DistributedLock;
import com.sponus.sponusbe.domain.announcement.exception.AnnouncementErrorCode;
import com.sponus.sponusbe.domain.announcement.exception.AnnouncementException;
import com.sponus.sponusbe.domain.propose.dto.request.ProposeCreateRequest;
import com.sponus.sponusbe.domain.propose.dto.response.ProposeCreateResponse;
import com.sponus.sponusbe.domain.propose.exception.ProposeErrorCode;
import com.sponus.sponusbe.domain.propose.exception.ProposeException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
@Service
public class ProposeService {

	private static final int DAILY_APPLY_LIMIT = 5;

	private final ProposeRepository proposeRepository;
	private final AnnouncementRepository announcementRepository;
	private final FirebaseUtil firebaseUtil;

	/**
	 * 동아리가 기업의 제휴 공고글에 지원한다.
	 *
	 * <p>"따닥"(연속 클릭) 동시성 문제 방지: 지원자(동아리) ID 와 공고 ID 를 조합한 멱등 키로
	 * Redisson 분산락을 걸어 동일 요청을 직렬화한다. 공고는 소유 기업(대상)을 유일하게 결정하므로
	 * (동아리 ID + 공고 ID)만으로 (동아리 ID + 기업 ID + 공고 ID) 조합과 동일한 멱등성을 갖는다.
	 * 락 획득 후 기존 지원 존재 여부를 확인하여 없을 때만 생성한다.
	 */
	@DistributedLock(key = "'PROPOSE:' + #applicant.id + ':' + #request.announcementId()")
	public ProposeCreateResponse createPropose(Organization applicant, ProposeCreateRequest request) {

		if (!applicant.isClub())
			throw new ProposeException(ProposeErrorCode.ONLY_CLUB_CAN_APPLY);

		final Announcement announcement = announcementRepository.findById(request.announcementId())
			.orElseThrow(() -> new AnnouncementException(AnnouncementErrorCode.ANNOUNCEMENT_NOT_FOUND));

		if (!announcement.isRecruiting())
			throw new AnnouncementException(AnnouncementErrorCode.ANNOUNCEMENT_NOT_RECRUITING);

		final Company target = announcement.getCompany();

		if (applicant.getId().equals(target.getId()))
			throw new ProposeException(ProposeErrorCode.CANNOT_PROPOSE_TO_SELF);

		if (applicant.getImageUrl() == null || applicant.getImageUrl().isEmpty())
			throw new ProposeException(ProposeErrorCode.PROFILE_NOT_COMPLETED);

		if (Boolean.TRUE.equals(proposeRepository.existsByOrganizationAndAnnouncement(applicant, announcement)))
			throw new ProposeException(ProposeErrorCode.ALREADY_APPLIED);

		final Long count = proposeRepository.countProposesByOrganizationToday(applicant,
			LocalDateTime.now().toLocalDate().atStartOfDay());

		if (count >= DAILY_APPLY_LIMIT)
			throw new ProposeException(ProposeErrorCode.PROPOSE_LIMIT_ERROR);

		final Propose propose = proposeRepository.save(request.toEntity(applicant, announcement));
		try {
			firebaseUtil.sendMessageTo(
				target,
				applicant.getName() + "(으)로부터 공고 지원이 왔어요!",
				"「" + announcement.getTitle() + "」 공고를 확인해 보세요.",
				propose,
				NotificationStatus.RECEIVE
			);
		} catch (IOException ex) {
			log.error("[*] Failed to send notification to company: " + target.getName(), ex);
		}
		return ProposeCreateResponse.from(propose);
	}
}
