package com.sponus.storage.redis.repository;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 프로필 조회수를 Redis 에 누적하는 Write-Behind 저장소.
 * - viewers Set : 당일 중복 조회 dedup (자정 만료)
 * - pending    : 아직 DB 에 반영되지 않은 조회수 증가분
 * - updated Set: pending 이 존재하는 조직 ID 목록 (스케줄러가 drain)
 * 조회 기록과 대상 목록 수거/복구는 Lua로 원자적으로 실행한다.
 * 전역 updated 키를 함께 쓰므로 standalone/Sentinel용이며 Redis Cluster의 cross-slot 실행은 지원하지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrganizationViewRedisRepository {

	private static final String VIEWER_KEY_FORMAT = "organization:view:viewers:%d";
	private static final String PENDING_COUNT_KEY_FORMAT = "organization:view:pending:%d";
	private static final String UPDATED_KEY = "organization:view:updated";
	private static final String VIEWER_KEY_PATTERN = "organization:view:viewers:*";
	private static final RedisScript<Long> ADD_VIEWER = RedisScript.of(
		new ClassPathResource("redis/organization-view/add-viewer.lua"), Long.class);
	@SuppressWarnings("rawtypes")
	private static final RedisScript<List> POP_UPDATED = RedisScript.of(
		new ClassPathResource("redis/organization-view/pop-updated.lua"), List.class);
	private static final RedisScript<Long> RESTORE_PENDING = RedisScript.of(
		new ClassPathResource("redis/organization-view/restore-pending.lua"), Long.class);

	private final RedisTemplate<String, Object> redisTemplate;

	public void addViewer(Long organizationId, Long viewerId) {
		final String viewerKey = String.format(VIEWER_KEY_FORMAT, organizationId);
		final ZoneId zone = ZoneId.systemDefault();
		final long expiresAt = LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toEpochSecond();
		redisTemplate.execute(ADD_VIEWER,
			List.of(viewerKey, String.format(PENDING_COUNT_KEY_FORMAT, organizationId), UPDATED_KEY),
			viewerId.toString(), Long.toString(expiresAt), organizationId.toString());
	}

	public Set<Long> popUpdatedOrganizationIds() {
		final List<?> members = redisTemplate.execute(POP_UPDATED, List.of(UPDATED_KEY));
		if (members == null || members.isEmpty()) {
			return Set.of();
		}
		return members.stream()
			.map(member -> Long.parseLong(member.toString()))
			.collect(Collectors.toSet());
	}

	public long popPendingViewCount(Long organizationId) {
		final Object count = redisTemplate.opsForValue()
			.getAndDelete(String.format(PENDING_COUNT_KEY_FORMAT, organizationId));
		return count == null ? 0L : Long.parseLong(count.toString());
	}

	/** DB 롤백 시 증가분을 새 요청의 증가분에 합치고 집계 대상도 다시 등록한다. */
	public void restorePendingViewCount(Long organizationId, long count) {
		if (count < 0) {
			throw new IllegalArgumentException("Pending view count must not be negative");
		}
		redisTemplate.execute(RESTORE_PENDING,
			List.of(String.format(PENDING_COUNT_KEY_FORMAT, organizationId), UPDATED_KEY),
			Long.toString(count), organizationId.toString());
	}

	/** 테스트/점검용. 운영 중 일일 초기화는 조회자 Set의 자정 TTL로 처리한다. */
	public void clearAllViewers() {
		final Set<String> keys = redisTemplate.keys(VIEWER_KEY_PATTERN);
		if (keys != null && !keys.isEmpty()) {
			redisTemplate.delete(keys);
		}
	}
}
