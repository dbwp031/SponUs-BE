package com.sponus.sponusbe.domain.organization;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.sponus.core.domain.organization.Organization;
import com.sponus.core.domain.organization.club.Club;
import com.sponus.core.domain.organization.company.Company;
import com.sponus.core.domain.organization.repository.OrganizationRepository;
import com.sponus.storage.redis.repository.OrganizationViewRedisRepository;
import com.sponus.sponusbe.domain.organization.company.service.CompanyService;
import com.sponus.sponusbe.domain.organization.service.OrganizationViewSyncService;

@SpringBootTest
@ActiveProfiles("test")
class OrganizationViewSyncServiceTest {

	@Autowired
	CompanyService companyService;

	@Autowired
	OrganizationViewSyncService organizationViewSyncService;

	@Autowired
	OrganizationViewRedisRepository organizationViewRedisRepository;

	@Autowired
	OrganizationRepository organizationRepository;

	Company company;
	Organization viewer;

	@BeforeEach
	void init() {
		clearRedis();
		company = organizationRepository.save(
			new Company("sponus_company", "view_sync_company@gmail.com", "sponus1234#"));
		viewer = organizationRepository.save(
			new Club("sponus_club", "view_sync_club@gmail.com", "sponus1234#"));
	}

	@AfterEach
	void tearDown() {
		clearRedis();
		organizationRepository.deleteAll();
	}

	@Test
	void 동일_사용자의_중복_조회는_한_번만_집계되고_스케줄러가_DB에_일괄_반영한다() {
		// given : 같은 viewer 가 두 번 조회 (DB 쓰기 없이 Redis 에만 누적)
		companyService.getCompany(company.getId(), viewer);
		companyService.getCompany(company.getId(), viewer);

		// when : 스케줄러가 호출하는 동기화 로직 실행
		organizationViewSyncService.syncViewCounts();

		// then : dedup 되어 조회수는 1만 증가
		Organization synced = organizationRepository.findById(company.getId()).orElseThrow();
		Assertions.assertThat(synced.getViewCount()).isEqualTo(1);
	}

	@Test
	void 본인_프로필_조회는_조회수에_집계되지_않는다() {
		// given
		companyService.getCompany(company.getId(), company);

		// when
		organizationViewSyncService.syncViewCounts();

		// then
		Organization synced = organizationRepository.findById(company.getId()).orElseThrow();
		Assertions.assertThat(synced.getViewCount()).isZero();
	}

	private void clearRedis() {
		organizationViewRedisRepository.clearAllViewers();
		organizationViewRedisRepository.popUpdatedOrganizationIds()
			.forEach(organizationViewRedisRepository::popPendingViewCount);
	}
}
