package com.sponus.sponusbe.domain.propose.service;

import java.io.IOException;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sponus.clients.fcm.FirebaseUtil;
import com.sponus.core.domain.announcement.Announcement;
import com.sponus.core.domain.announcement.repository.AnnouncementRepository;
import com.sponus.core.domain.notification.NotificationStatus;
import com.sponus.core.domain.organization.Organization;
import com.sponus.core.domain.organization.company.Company;
import com.sponus.core.domain.propose.Propose;
import com.sponus.core.domain.propose.repository.ProposeRepository;
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
@Transactional
@Service
public class ProposeService {

	private static final int DAILY_APPLY_LIMIT = 5;

	private final ProposeRepository proposeRepository;
	private final AnnouncementRepository announcementRepository;
	private final FirebaseUtil firebaseUtil;

	// 동아리가 기업의 제휴 공고글에 지원한다.
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
