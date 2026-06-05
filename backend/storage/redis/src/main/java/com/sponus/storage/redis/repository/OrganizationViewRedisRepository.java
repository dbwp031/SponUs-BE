package com.sponus.storage.redis.repository;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 프로필 조회수를 Redis 에 누적하는 Write-Behind 저장소.
 * - viewers Set : 당일 중복 조회 dedup (자정 만료)
 * - pending    : 아직 DB 에 반영되지 않은 조회수 증가분
 * - updated Set: pending 이 존재하는 조직 ID 목록 (스케줄러가 drain)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrganizationViewRedisRepository {

	private static final String VIEWER_KEY_FORMAT = "organization:view:viewers:%d";
	private static final String PENDING_COUNT_KEY_FORMAT = "organization:view:pending:%d";
	private static final String UPDATED_KEY = "organization:view:updated";
	private static final String VIEWER_KEY_PATTERN = "organization:view:viewers:*";

	private final RedisTemplate<String, Object> redisTemplate;

	public void addViewer(Long organizationId, Long viewerId) {
		final String viewerKey = String.format(VIEWER_KEY_FORMAT, organizationId);
		final Long added = redisTemplate.opsForSet().add(viewerKey, viewerId.toString());
		redisTemplate.expireAt(viewerKey,
			LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant());
		if (added == null || added == 0) {
			return;
		}
		redisTemplate.opsForValue().increment(String.format(PENDING_COUNT_KEY_FORMAT, organizationId));
		redisTemplate.opsForSet().add(UPDATED_KEY, organizationId.toString());
	}

	public Set<Long> popUpdatedOrganizationIds() {
		final Set<Object> members = redisTemplate.opsForSet().members(UPDATED_KEY);
		if (members == null || members.isEmpty()) {
			return Set.of();
		}
		redisTemplate.opsForSet().remove(UPDATED_KEY, members.toArray());
		return members.stream()
			.map(member -> Long.parseLong(member.toString()))
			.collect(Collectors.toSet());
	}

	public long popPendingViewCount(Long organizationId) {
		final Object count = redisTemplate.opsForValue()
			.getAndDelete(String.format(PENDING_COUNT_KEY_FORMAT, organizationId));
		return count == null ? 0L : Long.parseLong(count.toString());
	}

	public void clearAllViewers() {
		final Set<String> keys = redisTemplate.keys(VIEWER_KEY_PATTERN);
		if (keys != null && !keys.isEmpty()) {
			redisTemplate.delete(keys);
		}
	}
}
