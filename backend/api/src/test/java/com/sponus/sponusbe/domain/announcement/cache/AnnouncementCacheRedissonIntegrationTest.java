package com.sponus.sponusbe.domain.announcement.cache;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.*;
import static org.mockito.Mockito.*;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.sponus.core.domain.announcement.Announcement;
import com.sponus.core.domain.announcement.repository.AnnouncementRepository;
import com.sponus.core.domain.organization.company.Company;
import com.sponus.core.domain.organization.repository.CompanyRepository;
import com.sponus.sponusbe.domain.announcement.dto.response.AnnouncementGetResponse;
import com.sponus.sponusbe.domain.announcement.service.AnnouncementQueryService;

/**
 * 외부 Redis를 사용하는 통합테스트. 실행 방법은 load-test/STAMPEDE.md 참고.
 * DB repository만 대역을 사용하며, 3개 독립 Redisson 클라이언트와 실제 Redis 캐시로 검증한다.
 */
@EnabledIfEnvironmentVariable(named = "REDIS_TEST_ADDRESS", matches = "redis://.+")
class AnnouncementCacheRedissonIntegrationTest {

	private static final List<RedissonClient> CLIENTS = new ArrayList<>();
	private static final AtomicLong IDS = new AtomicLong(System.currentTimeMillis());
	private static LettuceConnectionFactory connectionFactory;
	private static StringRedisTemplate redis;

	@BeforeAll
	static void connect() {
		URI address = URI.create(System.getenv("REDIS_TEST_ADDRESS"));
		for (int i = 0; i < 3; i++) {
			Config config = new Config().setThreads(2).setNettyThreads(2);
			config.useSingleServer().setAddress(address.toString())
				.setConnectionMinimumIdleSize(1).setConnectionPoolSize(8)
				.setSubscriptionConnectionMinimumIdleSize(1).setSubscriptionConnectionPoolSize(4)
				.setRetryAttempts(0);
			CLIENTS.add(Redisson.create(config));
		}
		connectionFactory = new LettuceConnectionFactory(address.getHost(), address.getPort());
		connectionFactory.afterPropertiesSet();
		connectionFactory.start();
		redis = new StringRedisTemplate(connectionFactory);
	}

	@AfterAll
	static void disconnect() {
		CLIENTS.forEach(RedissonClient::shutdown);
		if (connectionFactory != null) {
			connectionFactory.destroy();
		}
	}

	@Test
	void sixtySimultaneousMissesAcrossThreeClientsLoadDatabaseOnce() throws Exception {
		long id = IDS.incrementAndGet();
		AnnouncementRepository repository = mock(AnnouncementRepository.class);
		Announcement announcement = Announcement.builder().id(id).title("동시 요청 공고")
			.company(new Company("기업", "company@example.com", "password")).build();
		CountDownLatch allMissed = new CountDownLatch(60);
		AnnouncementCacheMetrics metrics = mock(AnnouncementCacheMetrics.class);
		doAnswer(invocation -> {
			allMissed.countDown();
			return null;
		}).when(metrics).recordMiss();
		when(repository.findWithCompanyById(id)).thenAnswer(invocation -> {
			// 모든 요청이 cold miss를 관측한 뒤 첫 조회 결과를 반환한다.
			assertThat(allMissed.await(5, TimeUnit.SECONDS)).isTrue();
			return Optional.of(announcement);
		});
		AnnouncementCacheProperties props = new AnnouncementCacheProperties();
		// CI 스케줄링 지연에 영향받지 않도록 넉넉히 설정. 성능 측정용 수치가 아니다.
		props.setWaitBudgetMs(10000);
		props.setLockLeaseMs(10000);
		List<AnnouncementQueryService> services = CLIENTS.stream()
			.map(client -> new AnnouncementQueryService(repository, mock(CompanyRepository.class),
				new AnnouncementDetailCacheStore(redis), new AnnouncementCacheLock(client), metrics, props))
			.toList();
		var executor = Executors.newFixedThreadPool(60);
		try {
			CountDownLatch start = new CountDownLatch(1);
			List<Future<AnnouncementGetResponse>> requests = new ArrayList<>();
			for (int i = 0; i < 60; i++) {
				AnnouncementQueryService service = services.get(i % 3);
				requests.add(executor.submit(() -> {
					start.await();
					return service.getAnnouncement(id);
				}));
			}
			start.countDown();
			for (var request : requests) {
				assertThat(request.get(15, TimeUnit.SECONDS)).isEqualTo(AnnouncementGetResponse.from(announcement));
			}
			verify(repository, times(1)).findWithCompanyById(id);
			verify(metrics, times(60)).recordMiss();
			verify(metrics, never()).recordLockTimeout();
			assertThat(CLIENTS.get(0).getLock(lockKey(id)).isLocked()).isFalse();
		} finally {
			executor.shutdownNow();
			executor.awaitTermination(5, TimeUnit.SECONDS);
			redis.delete(List.of("cache:v1:announcement:detail:" + id, lockKey(id)));
		}
	}

	@Test
	void expiredOwnerCannotUnlockSuccessor() throws InterruptedException {
		long id = IDS.incrementAndGet();
		AnnouncementCacheLock first = new AnnouncementCacheLock(CLIENTS.get(0));
		AnnouncementCacheLock second = new AnnouncementCacheLock(CLIENTS.get(1));
		assertThat(first.tryLock(id, Duration.ZERO, Duration.ofMillis(100))).isTrue();
		await().atMost(Duration.ofSeconds(3)).until(() -> !CLIENTS.get(0).getLock(lockKey(id)).isLocked());
		assertThat(second.tryLock(id, Duration.ZERO, Duration.ofSeconds(5))).isTrue();
		try {
			first.unlock(id);
			assertThat(CLIENTS.get(1).getLock(lockKey(id)).isHeldByCurrentThread()).isTrue();
		} finally {
			second.unlock(id);
		}
	}

	@Test
	void differentAnnouncementsDoNotBlockEachOther() throws InterruptedException {
		long firstId = IDS.incrementAndGet();
		long secondId = IDS.incrementAndGet();
		AnnouncementCacheLock first = new AnnouncementCacheLock(CLIENTS.get(0));
		AnnouncementCacheLock second = new AnnouncementCacheLock(CLIENTS.get(1));
		assertThat(first.tryLock(firstId, Duration.ZERO, Duration.ofSeconds(5))).isTrue();
		try {
			assertThat(second.tryLock(secondId, Duration.ZERO, Duration.ofSeconds(5))).isTrue();
			second.unlock(secondId);
		} finally {
			first.unlock(firstId);
		}
	}

	private static String lockKey(long id) {
		return "lock:cache:v2:announcement:detail:" + id;
	}
}
