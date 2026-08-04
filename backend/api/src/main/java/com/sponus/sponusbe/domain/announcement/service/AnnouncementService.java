package com.sponus.sponusbe.domain.announcement.service;

import static com.sponus.sponusbe.domain.announcement.exception.AnnouncementErrorCode.*;
import static com.sponus.sponusbe.domain.organization.exception.CompanyErrorCode.*;
import static com.sponus.sponusbe.domain.organization.exception.OrganizationErrorCode.*;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sponus.core.domain.announcement.Announcement;
import com.sponus.core.domain.announcement.repository.AnnouncementRepository;
import com.sponus.core.domain.organization.Organization;
import com.sponus.core.domain.organization.company.Company;
import com.sponus.core.domain.organization.repository.CompanyRepository;
import com.sponus.sponusbe.domain.announcement.cache.AnnouncementDetailCacheStore;
import com.sponus.sponusbe.domain.announcement.dto.request.AnnouncementCreateRequest;
import com.sponus.sponusbe.domain.announcement.dto.request.AnnouncementUpdateRequest;
import com.sponus.sponusbe.domain.announcement.dto.response.AnnouncementCreateResponse;
import com.sponus.sponusbe.domain.announcement.event.AnnouncementChangedEvent;
import com.sponus.sponusbe.domain.announcement.exception.AnnouncementException;
import com.sponus.sponusbe.domain.organization.exception.OrganizationException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
@Transactional
@Service
public class AnnouncementService {

	private final AnnouncementRepository announcementRepository;
	private final CompanyRepository companyRepository;
	private final AnnouncementDetailCacheStore cacheStore;
	private final ApplicationEventPublisher eventPublisher;

	public AnnouncementCreateResponse createAnnouncement(Organization authOrganization,
		AnnouncementCreateRequest request) {
		if (!authOrganization.isCompany()) {
			throw new OrganizationException(ORGANIZATION_UNAUTHORIZED_ONLY_COMPANY_AUTHORIZED);
		}
		Company company = companyRepository.findById(authOrganization.getId())
			.orElseThrow(() -> new OrganizationException(COMPANY_NOT_FOUND));

		Announcement announcement = announcementRepository.save(request.toEntity(company));
		return AnnouncementCreateResponse.from(announcement);
	}

	/**
	 * 공고 수정. 캐시를 선삭제(1차)하고 DB 를 수정한 뒤, 커밋 이후 무효화 이벤트를 발행한다.
	 * (리스너가 AFTER_COMMIT 즉시 삭제 + 지연 2차 삭제를 수행 → delayed double delete)
	 */
	public void updateAnnouncement(Organization authOrganization, Long announcementId,
		AnnouncementUpdateRequest request) {
		if (!authOrganization.isCompany()) {
			throw new OrganizationException(ORGANIZATION_UNAUTHORIZED_ONLY_COMPANY_AUTHORIZED);
		}
		Announcement announcement = announcementRepository.findById(announcementId)
			.orElseThrow(() -> new AnnouncementException(ANNOUNCEMENT_NOT_FOUND));
		if (!announcement.getCompany().getId().equals(authOrganization.getId())) {
			throw new OrganizationException(ORGANIZATION_UNAUTHORIZED);
		}

		// 1차: 캐시 선삭제 (best-effort)
		cacheStore.evict(announcementId);

		// DB 수정 (dirty checking → 커밋 시 반영)
		announcement.update(request.title(), request.content(), request.collaborationType(),
			request.startDate(), request.endDate());

		// 커밋 이후 무효화 (AFTER_COMMIT 리스너: 즉시 + 지연 2차 삭제)
		eventPublisher.publishEvent(new AnnouncementChangedEvent(announcementId));
	}
}
