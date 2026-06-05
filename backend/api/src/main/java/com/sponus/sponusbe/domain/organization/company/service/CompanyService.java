package com.sponus.sponusbe.domain.organization.company.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sponus.core.domain.organization.company.CollaborationType;
import com.sponus.core.domain.organization.company.Company;
import com.sponus.core.domain.organization.company.CompanyType;
import com.sponus.core.domain.organization.Organization;
import com.sponus.core.domain.organization.repository.CollaborationRepository;
import com.sponus.core.domain.organization.repository.CompanyRepository;
import com.sponus.core.domain.organization.repository.CompanyTypeRepository;
import com.sponus.storage.redis.repository.OrganizationViewRedisRepository;
import com.sponus.sponusbe.domain.organization.company.dto.CompanyGetResponse;
import com.sponus.sponusbe.domain.organization.company.dto.CompanyUpdateRequest;
import com.sponus.sponusbe.domain.organization.exception.CompanyErrorCode;
import com.sponus.sponusbe.domain.organization.exception.OrganizationException;

import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class CompanyService {
	private final CompanyRepository companyRepository;
	private final CompanyTypeRepository companyTypeRepository;
	private final CollaborationRepository collaborationRepository;
	private final OrganizationViewRedisRepository organizationViewRedisRepository;

	public CompanyGetResponse getCompany(Long companyId) {
		final Company company = findCompanyById(companyId);
		return CompanyGetResponse.of(company);
	}

	// 조회수는 DB 를 직접 갱신하지 않고 Redis 에 누적한다. (Write-Behind, 스케줄러가 일괄 반영)
	public CompanyGetResponse getCompany(Long companyId, Organization viewer) {
		final Company company = findCompanyById(companyId);
		if (!company.getId().equals(viewer.getId())) {
			organizationViewRedisRepository.addViewer(company.getId(), viewer.getId());
		}
		return CompanyGetResponse.of(company);
	}

	public void updateCompany(Long companyId, CompanyUpdateRequest request) {
		final Company company = findCompanyById(companyId);
		company.updateInfo(
			request.name(),
			request.description(),
			request.imageUrl(),
			request.profileStatus(),
			request.sponsorshipContent()
		);
		request.companyTypes().forEach(type -> {
			final CompanyType companyType = new CompanyType(type);
			companyType.updateCompany(company);
			companyTypeRepository.save(companyType);
		});
		request.collaborationTypes().forEach(type -> {
			final CollaborationType collaborationType = new CollaborationType(type);
			collaborationType.updateCompany(company);
			collaborationRepository.save(collaborationType);
		});
	}

	private Company findCompanyById(Long companyId) {
		return companyRepository.findById(companyId)
			.orElseThrow(() -> new OrganizationException(CompanyErrorCode.COMPANY_NOT_FOUND));
	}
}
