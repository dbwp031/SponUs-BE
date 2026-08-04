package com.sponus.sponusbe.domain.announcement.cache;

import java.time.Duration;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sponus.sponusbe.domain.announcement.dto.response.AnnouncementGetResponse;
import com.sponus.storage.redis.config.CacheRedisConfig;

import lombok.extern.slf4j.Slf4j;

/**
 * 공고 상세 캐시 저장소 (Cache Aside).
 *
 * <p>키: {@code cache:v1:announcement:detail:{id}} — v1 은 DTO 스키마 버전. 스키마 변경 시 v2 로 전환.
 * 값: {@link AnnouncementGetResponse} JSON. JPA 엔티티가 아닌 DTO 를 저장한다.
 *
 * <p>Redis 연결 실패는 {@code DataAccessException} 으로 상위에 전파하여 호출자가 DB fallback 하도록 한다.
 * 역직렬화 실패(스키마 불일치 등)는 miss 로 처리한다.
 */
@Slf4j
@Component
public class AnnouncementDetailCacheStore {

	private static final String KEY_PREFIX = "cache:v1:announcement:detail:";

	private final RedisTemplate<String, String> redis;
	private final ObjectMapper objectMapper = JsonMapper.builder()
		.addModule(new JavaTimeModule())
		.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
		.build();

	public AnnouncementDetailCacheStore(
		@Qualifier(CacheRedisConfig.CACHE_REDIS_TEMPLATE) RedisTemplate<String, String> redis) {
		this.redis = redis;
	}

	public Optional<AnnouncementGetResponse> get(Long announcementId) {
		String json = redis.opsForValue().get(key(announcementId));   // Redis 장애 시 DataAccessException 전파
		if (json == null) {
			return Optional.empty();
		}
		try {
			return Optional.of(objectMapper.readValue(json, AnnouncementGetResponse.class));
		} catch (Exception e) {
			log.warn("[Cache] 역직렬화 실패 → miss 처리 id={}", announcementId, e);
			return Optional.empty();
		}
	}

	public void put(Long announcementId, AnnouncementGetResponse response, Duration ttl) {
		try {
			redis.opsForValue().set(key(announcementId), objectMapper.writeValueAsString(response), ttl);
		} catch (Exception e) {
			log.warn("[Cache] 저장 실패(무시하고 DB 결과 반환) id={}", announcementId, e);
		}
	}

	public void evict(Long announcementId) {
		try {
			redis.delete(key(announcementId));
		} catch (Exception e) {
			// 삭제 실패는 기록만 — TTL 이 최종 안전장치. 멱등하므로 재시도/지연삭제로 보완.
			log.warn("[Cache] 무효화(삭제) 실패 id={}", announcementId, e);
		}
	}

	private String key(Long announcementId) {
		return KEY_PREFIX + announcementId;
	}
}
