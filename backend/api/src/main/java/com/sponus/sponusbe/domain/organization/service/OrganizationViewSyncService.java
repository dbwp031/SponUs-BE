package com.sponus.sponusbe.domain.organization.service;

import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sponus.core.domain.organization.repository.OrganizationRepository;
import com.sponus.storage.redis.repository.OrganizationViewRedisRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrganizationViewSyncService {

	private final OrganizationViewRedisRepository organizationViewRedisRepository;
	private final OrganizationRepository organizationRepository;

	@Transactional
	public void syncViewCounts() {
		final Set<Long> organizationIds = organizationViewRedisRepository.popUpdatedOrganizationIds();
		for (Long organizationId : organizationIds) {
			final long pendingCount = organizationViewRedisRepository.popPendingViewCount(organizationId);
			if (pendingCount > 0) {
				organizationRepository.increaseViewCount(organizationId, pendingCount);
			}
		}
		if (!organizationIds.isEmpty()) {
			log.info("[*] Synced view counts of {} organizations to DB", organizationIds.size());
		}
	}

	public void clearViewers() {
		organizationViewRedisRepository.clearAllViewers();
		log.info("[*] Cleared daily profile view dedup sets");
	}
}
