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

	// 조회자 Set은 자정 TTL로 만료된다. 전체 삭제는 자정 이후 새 조회 기록을 지울 수 있어 실행하지 않는다.
}
