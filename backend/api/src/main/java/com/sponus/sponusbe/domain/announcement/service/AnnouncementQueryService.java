package com.sponus.sponusbe.domain.announcement.service;

import static com.sponus.sponusbe.domain.announcement.exception.AnnouncementErrorCode.*;
import static com.sponus.sponusbe.domain.organization.exception.CompanyErrorCode.*;
import static com.sponus.sponusbe.domain.organization.exception.OrganizationErrorCode.*;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sponus.core.domain.announcement.repository.AnnouncementRepository;
import com.sponus.core.domain.organization.Organization;
import com.sponus.core.domain.organization.company.Company;
import com.sponus.core.domain.organization.repository.CompanyRepository;
import com.sponus.sponusbe.domain.announcement.dto.response.AnnouncementGetResponse;
import com.sponus.sponusbe.domain.announcement.exception.AnnouncementException;
import com.sponus.sponusbe.domain.organization.dto.request.PageCondition;
import com.sponus.sponusbe.domain.organization.dto.response.PageResponse;
import com.sponus.sponusbe.domain.organization.exception.OrganizationException;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Transactional(readOnly = true)
@Service
public class AnnouncementQueryService {

	private final AnnouncementRepository announcementRepository;
	private final CompanyRepository companyRepository;

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

	// 공고 상세
	public AnnouncementGetResponse getAnnouncement(Long announcementId) {
		return announcementRepository.findById(announcementId)
			.map(AnnouncementGetResponse::from)
			.orElseThrow(() -> new AnnouncementException(ANNOUNCEMENT_NOT_FOUND));
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
}
