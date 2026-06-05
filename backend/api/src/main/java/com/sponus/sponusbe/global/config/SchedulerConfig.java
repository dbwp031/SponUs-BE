package com.sponus.sponusbe.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

import com.sponus.sponusbe.domain.organization.service.OrganizationViewSyncService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
@Configuration
public class SchedulerConfig {

	private final OrganizationViewSyncService organizationViewSyncService;

	// Redis 에 누적된 프로필 조회수를 1분 주기로 DB 에 일괄 반영한다. (Write-Behind)
	@Scheduled(fixedDelay = 1000L * 60)
	public void syncOrganizationViewCount() {
		organizationViewSyncService.syncViewCounts();
	}

	// 매일 0시에 중복 조회 dedup Set 을 초기화한다.
	@Scheduled(cron = "0 0 0 * * *")
	public void resetOrganizationViewers() {
		organizationViewSyncService.clearViewers();
	}
}
