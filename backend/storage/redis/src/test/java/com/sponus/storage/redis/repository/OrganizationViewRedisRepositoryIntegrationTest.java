package com.sponus.storage.redis.repository;

import static org.assertj.core.api.Assertions.*;

import java.net.URI;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/** Uses a dedicated real Redis; REDIS_TEST_ADDRESS=redis://127.0.0.1:port. */
@EnabledIfEnvironmentVariable(named = "REDIS_TEST_ADDRESS", matches = ".+")
class OrganizationViewRedisRepositoryIntegrationTest {

	private static final String UPDATED = "organization:view:updated";
	private static final AtomicLong IDS = new AtomicLong(System.currentTimeMillis());
	private static final List<LettuceConnectionFactory> FACTORIES = new ArrayList<>();
	private static final List<OrganizationViewRedisRepository> REPOSITORIES = new ArrayList<>();
	private static RedisTemplate<String, Object> redis;
	private final Set<Long> organizationIds = new HashSet<>();

	@BeforeAll
	static void connect() {
		URI address = URI.create(System.getenv("REDIS_TEST_ADDRESS"));
		for (int i = 0; i < 3; i++) {
			LettuceConnectionFactory factory = new LettuceConnectionFactory(address.getHost(), address.getPort());
			factory.afterPropertiesSet();
			factory.start();
			FACTORIES.add(factory);
			RedisTemplate<String, Object> template = new RedisTemplate<>();
			template.setConnectionFactory(factory);
			template.setKeySerializer(new StringRedisSerializer());
			template.setValueSerializer(new StringRedisSerializer());
			template.afterPropertiesSet();
			REPOSITORIES.add(new OrganizationViewRedisRepository(template));
			redis = template;
		}
	}

	@AfterAll
	static void disconnect() {
		FACTORIES.forEach(LettuceConnectionFactory::destroy);
	}

	@AfterEach
	void cleanOwnKeys() {
		organizationIds.forEach(id -> {
			redis.delete(List.of(viewers(id), pending(id)));
			redis.opsForSet().remove(UPDATED, id.toString());
		});
	}

	@Test
	void concurrentDuplicateViewsAcrossThreeClientsCountOnce() throws Exception {
		long id = newOrganization();
		runConcurrently(200, index -> {
			REPOSITORIES.get(index % 3).addViewer(id, 123L);
			return null;
		});

		assertThat(redis.opsForSet().size(viewers(id))).isEqualTo(1L);
		assertThat(redis.opsForValue().get(pending(id))).isEqualTo("1");
		assertThat(REPOSITORIES.get(0).popUpdatedOrganizationIds()).containsExactly(id);
		assertThat(REPOSITORIES.get(1).popUpdatedOrganizationIds()).isEmpty();
	}

	@Test
	void concurrentNewViewersAreAllCounted() throws Exception {
		long id = newOrganization();
		runConcurrently(200, index -> {
			REPOSITORIES.get(index % 3).addViewer(id, (long)index);
			return null;
		});
		assertThat(redis.opsForSet().size(viewers(id))).isEqualTo(200L);
		assertThat(redis.opsForValue().get(pending(id))).isEqualTo("200");
	}

	@Test
	void concurrentCollectorsDoNotClaimTheSameSnapshot() throws Exception {
		Set<Long> expected = new HashSet<>();
		for (int i = 0; i < 30; i++) {
			long id = newOrganization();
			expected.add(id);
			REPOSITORIES.get(0).addViewer(id, 1L);
		}
		List<Set<Long>> results = runConcurrently(3,
			index -> REPOSITORIES.get(index).popUpdatedOrganizationIds());
		List<Long> claimed = results.stream().flatMap(Set::stream).toList();
		assertThat(claimed).doesNotHaveDuplicates().containsExactlyInAnyOrderElementsOf(expected);
	}

	@Test
	void concurrentWritersAndCollectorsPreserveEveryUniqueView() throws Exception {
		long id = newOrganization();
		AtomicLong collected = new AtomicLong();
		CountDownLatch writersDone = new CountDownLatch(3);
		runConcurrently(6, index -> {
			OrganizationViewRedisRepository repository = REPOSITORIES.get(index % 3);
			if (index < 3) {
				try {
					for (long viewer = 0; viewer < 200; viewer++) {
						// All writers see the same viewers; only 200 unique views count.
						repository.addViewer(id, viewer);
					}
				} finally {
					writersDone.countDown();
				}
			} else {
				do {
					for (Long updatedId : repository.popUpdatedOrganizationIds()) {
						collected.addAndGet(repository.popPendingViewCount(updatedId));
					}
				} while (writersDone.getCount() > 0);
			}
			return null;
		});
		OrganizationViewRedisRepository repository = REPOSITORIES.get(0);
		for (Long updatedId : repository.popUpdatedOrganizationIds()) {
			collected.addAndGet(repository.popPendingViewCount(updatedId));
		}
		assertThat(collected.get()).isEqualTo(200);
		assertThat(redis.hasKey(pending(id))).isFalse();
		assertThat(redis.opsForSet().size(viewers(id))).isEqualTo(200);
	}

	@Test
	void viewsDuringCollectionRemainForTheNextBatchAndDailyDedupSurvives() {
		long id = newOrganization();
		OrganizationViewRedisRepository repository = REPOSITORIES.get(0);
		repository.addViewer(id, 1L);
		assertThat(repository.popUpdatedOrganizationIds()).containsExactly(id);
		assertThat(repository.popPendingViewCount(id)).isEqualTo(1);

		repository.addViewer(id, 1L); // Already counted today, even after the counter was drained.
		repository.addViewer(id, 2L);
		assertThat(repository.popUpdatedOrganizationIds()).containsExactly(id);
		assertThat(repository.popPendingViewCount(id)).isEqualTo(1);
		assertThat(redis.opsForSet().size(viewers(id))).isEqualTo(2L);
	}

	@Test
	void rollbackRestoreAddsToNewViewsAndSchedulesRetry() {
		long id = newOrganization();
		OrganizationViewRedisRepository repository = REPOSITORIES.get(0);
		repository.addViewer(id, 1L);
		repository.popUpdatedOrganizationIds();
		long consumed = repository.popPendingViewCount(id);
		repository.addViewer(id, 2L);
		repository.restorePendingViewCount(id, consumed);

		assertThat(repository.popUpdatedOrganizationIds()).containsExactly(id);
		assertThat(repository.popPendingViewCount(id)).isEqualTo(2);
		assertThat(redis.opsForSet().size(viewers(id))).isEqualTo(2L);
	}

	@Test
	void wrongCounterTypeDoesNotMarkViewerAsCounted() {
		long id = newOrganization();
		redis.opsForSet().add(pending(id), "wrong-type");
		assertThatThrownBy(() -> REPOSITORIES.get(0).addViewer(id, 1L)).isInstanceOf(RuntimeException.class);
		assertThat(redis.hasKey(viewers(id))).isFalse();
		assertThat(redis.opsForSet().isMember(UPDATED, Long.toString(id))).isFalse();
	}

	@Test
	void viewerExpiryIsMidnightButPendingCounterDoesNotExpire() {
		long id = newOrganization();
		REPOSITORIES.get(0).addViewer(id, 1L);
		long expectedRemaining = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault())
			.toEpochSecond() - System.currentTimeMillis() / 1000;
		assertThat(redis.getExpire(viewers(id), TimeUnit.SECONDS)).isBetween(expectedRemaining - 2, expectedRemaining);
		assertThat(redis.getExpire(pending(id), TimeUnit.SECONDS)).isEqualTo(-1);
	}

	private long newOrganization() {
		long id = IDS.incrementAndGet();
		organizationIds.add(id);
		return id;
	}

	private static String viewers(long id) {
		return "organization:view:viewers:" + id;
	}

	private static String pending(long id) {
		return "organization:view:pending:" + id;
	}

	private static <T> List<T> runConcurrently(int count, IndexedTask<T> task) throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(Math.min(count, 24));
		CountDownLatch start = new CountDownLatch(1);
		try {
			List<Future<T>> futures = new ArrayList<>();
			for (int i = 0; i < count; i++) {
				int index = i;
				futures.add(executor.submit((Callable<T>)() -> {
					start.await();
					return task.run(index);
				}));
			}
			start.countDown();
			List<T> results = new ArrayList<>();
			for (Future<T> future : futures) {
				results.add(future.get(30, TimeUnit.SECONDS));
			}
			return results;
		} finally {
			executor.shutdownNow();
			executor.awaitTermination(5, TimeUnit.SECONDS);
		}
	}

	@FunctionalInterface
	private interface IndexedTask<T> {
		T run(int index) throws Exception;
	}
}
