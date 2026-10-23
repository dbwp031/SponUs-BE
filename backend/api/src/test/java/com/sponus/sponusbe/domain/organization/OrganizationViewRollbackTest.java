package com.sponus.sponusbe.domain.organization;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;

import com.sponus.core.domain.organization.repository.OrganizationRepository;
import com.sponus.sponusbe.domain.organization.service.OrganizationViewSyncService;
import com.sponus.storage.redis.repository.OrganizationViewRedisRepository;

/** Real JDBC commit/rollback boundaries; Redis recovery commands are verified separately against real Redis. */
class OrganizationViewRollbackTest {

	private OrganizationViewRedisRepository redis;
	private OrganizationRepository organizations;
	private OrganizationViewSyncService service;
	private JdbcTemplate jdbc;
	private TransactionTemplate transaction;
	private Map<Long, Long> pending;
	private Set<Long> updated;

	@BeforeEach
	void setUp() {
		JdbcDataSource dataSource = new JdbcDataSource();
		dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
		jdbc = new JdbcTemplate(dataSource);
		jdbc.execute("CREATE TABLE organization (id BIGINT PRIMARY KEY, view_count BIGINT NOT NULL)");
		jdbc.update("INSERT INTO organization VALUES (1, 100), (2, 200)");
		DataSourceTransactionManager manager = new DataSourceTransactionManager(dataSource);
		transaction = new TransactionTemplate(manager);
		redis = mock(OrganizationViewRedisRepository.class);
		organizations = mock(OrganizationRepository.class);
		pending = new ConcurrentHashMap<>();
		updated = new LinkedHashSet<>();
		when(redis.popUpdatedOrganizationIds()).thenAnswer(call -> {
			Set<Long> snapshot = new LinkedHashSet<>(updated);
			updated.clear();
			return snapshot;
		});
		when(redis.popPendingViewCount(anyLong())).thenAnswer(call -> {
			Long value = pending.remove(call.getArgument(0, Long.class));
			return value == null ? 0L : value;
		});
		doAnswer(call -> {
			long id = call.getArgument(0);
			long count = call.getArgument(1);
			if (count > 0) {
				pending.merge(id, count, Long::sum);
			}
			updated.add(id);
			return null;
		}).when(redis).restorePendingViewCount(anyLong(), anyLong());
		doAnswer(call -> {
			jdbc.update("UPDATE organization SET view_count = view_count + ? WHERE id = ?",
				call.getArgument(1, Long.class), call.getArgument(0, Long.class));
			return null;
		}).when(organizations).increaseViewCount(anyLong(), anyLong());
		ProxyFactory proxy = new ProxyFactory(new OrganizationViewSyncService(redis, organizations));
		proxy.setProxyTargetClass(true);
		TransactionInterceptor advice = new TransactionInterceptor();
		advice.setTransactionManager(manager);
		advice.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
		advice.afterPropertiesSet();
		proxy.addAdvice(advice);
		service = (OrganizationViewSyncService)proxy.getProxy();
	}

	@AfterEach
	void closeDatabase() {
		jdbc.execute("SHUTDOWN");
	}

	@Test
	void committedBatchIsNotRequeued() {
		addPending(1L, 3L);
		service.syncViewCounts();
		assertThat(count(1)).isEqualTo(103);
		assertThat(pending).isEmpty();
		assertThat(updated).isEmpty();
		verify(redis, never()).restorePendingViewCount(anyLong(), anyLong());
	}

	@Test
	void failedBatchRollsBackAllDbUpdatesAndCanBeRetriedOnce() {
		addPending(1L, 3L);
		addPending(2L, 7L);
		doThrow(new IllegalStateException("DB write failed"))
			.doAnswer(call -> {
				jdbc.update("UPDATE organization SET view_count = view_count + ? WHERE id = ?", 7L, 2L);
				return null;
			}).when(organizations).increaseViewCount(2L, 7L);
		assertThatThrownBy(service::syncViewCounts).isInstanceOf(IllegalStateException.class);
		assertThat(count(1)).isEqualTo(100);
		assertThat(count(2)).isEqualTo(200);
		assertThat(pending).containsEntry(1L, 3L).containsEntry(2L, 7L);

		service.syncViewCounts();
		assertThat(count(1)).isEqualTo(103);
		assertThat(count(2)).isEqualTo(207);
		assertThat(pending).isEmpty();
	}

	@Test
	void outerTransactionRollbackAfterMethodReturnsAlsoRestoresCounts() {
		addPending(1L, 3L);
		transaction.executeWithoutResult(status -> {
			service.syncViewCounts();
			assertThat(count(1)).isEqualTo(103);
			status.setRollbackOnly();
		});
		assertThat(count(1)).isEqualTo(100);
		assertThat(pending).containsEntry(1L, 3L);
		assertThat(updated).containsExactly(1L);
	}

	@Test
	void rollbackPreservesNewViewsAndOrganizationsNotYetProcessed() {
		addPending(1L, 3L);
		addPending(2L, 7L);
		doAnswer(call -> {
			addPending(1L, 2L); // New requests after GETDEL.
			throw new IllegalStateException("DB unavailable");
		}).when(organizations).increaseViewCount(1L, 3L);
		assertThatThrownBy(service::syncViewCounts).isInstanceOf(IllegalStateException.class);
		assertThat(pending).containsEntry(1L, 5L).containsEntry(2L, 7L);
		assertThat(updated).containsExactlyInAnyOrder(1L, 2L);
		verify(redis).restorePendingViewCount(2L, 0L);
	}

	private void addPending(long id, long count) {
		pending.merge(id, count, Long::sum);
		updated.add(id);
	}

	private long count(long id) {
		return jdbc.queryForObject("SELECT view_count FROM organization WHERE id = ?", Long.class, id);
	}
}
