package com.sponus.sponusbe.domain.organization.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
		if (organizationIds.isEmpty()) {
			return;
		}
		final Map<Long, Long> consumedCounts = new LinkedHashMap<>();
		organizationIds.forEach(id -> consumedCounts.put(id, 0L));
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCompletion(int status) {
				if (status == STATUS_ROLLED_BACK) {
					consumedCounts.forEach((id, count) -> {
						try {
							organizationViewRedisRepository.restorePendingViewCount(id, count);
						} catch (RuntimeException e) {
							log.error("View count recovery failed: organizationId={}, count={}", id, count, e);
						}
					});
				} else if (status != STATUS_COMMITTED) {
					// Commit outcome is unknown: replay could double-count a committed update.
					log.error("View count transaction outcome unknown; reconcile consumed counts: {}", consumedCounts);
				}
			}
		});
		for (Long organizationId : organizationIds) {
			final long pendingCount = organizationViewRedisRepository.popPendingViewCount(organizationId);
			consumedCounts.put(organizationId, pendingCount);
			if (pendingCount > 0) {
				organizationRepository.increaseViewCount(organizationId, pendingCount);
			}
		}
		log.info("[*] Submitted view count updates for {} organizations", organizationIds.size());
	}
}
