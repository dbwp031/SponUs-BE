package com.sponus.sponusbe.domain.announcement.service;

import static com.sponus.sponusbe.domain.organization.exception.CompanyErrorCode.*;
import static com.sponus.sponusbe.domain.organization.exception.OrganizationErrorCode.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sponus.core.domain.announcement.Announcement;
import com.sponus.core.domain.announcement.repository.AnnouncementRepository;
import com.sponus.core.domain.organization.Organization;
import com.sponus.core.domain.organization.company.Company;
import com.sponus.core.domain.organization.repository.CompanyRepository;
import com.sponus.sponusbe.domain.announcement.dto.request.AnnouncementCreateRequest;
import com.sponus.sponusbe.domain.announcement.dto.response.AnnouncementCreateResponse;
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
}
