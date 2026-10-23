package com.sponus.sponusbe.domain.announcement.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.client.RedisException;
import org.springframework.data.redis.RedisConnectionFailureException;

import com.sponus.core.domain.announcement.Announcement;
import com.sponus.core.domain.announcement.repository.AnnouncementRepository;
import com.sponus.core.domain.organization.company.Company;
import com.sponus.core.domain.organization.repository.CompanyRepository;
import com.sponus.sponusbe.domain.announcement.cache.AnnouncementCacheLock;
import com.sponus.sponusbe.domain.announcement.cache.AnnouncementCacheMetrics;
import com.sponus.sponusbe.domain.announcement.cache.AnnouncementCacheProperties;
import com.sponus.sponusbe.domain.announcement.cache.AnnouncementDetailCacheStore;
import com.sponus.sponusbe.domain.announcement.dto.response.AnnouncementGetResponse;

@ExtendWith(MockitoExtension.class)
class AnnouncementQueryServiceTest {

	private static final long ID = 1L;
	private static final Duration LEASE = Duration.ofMillis(3000);
	private static final Duration WAIT = Duration.ofMillis(300);

	@Mock AnnouncementRepository repository;
	@Mock CompanyRepository companyRepository;
	@Mock AnnouncementDetailCacheStore cache;
	@Mock AnnouncementCacheLock lock;
	@Mock AnnouncementCacheMetrics metrics;

	private AnnouncementQueryService service;
	private AnnouncementGetResponse response;
	private Announcement announcement;

	@BeforeEach
	void setUp() {
		service = new AnnouncementQueryService(repository, companyRepository, cache, lock, metrics,
			new AnnouncementCacheProperties());
		announcement = Announcement.builder().id(ID).title("공고")
			.company(new Company("기업", "company@example.com", "password")).build();
		response = AnnouncementGetResponse.from(announcement);
	}

	@Test
	void cacheHitDoesNotAcquireLockOrLoadDatabase() {
		when(cache.get(ID)).thenReturn(Optional.of(response));

		assertThat(service.getAnnouncement(ID)).isEqualTo(response);

		verifyNoInteractions(lock, repository);
	}

	@Test
	void doubleCheckAvoidsDatabaseAfterImmediateAcquisition() throws InterruptedException {
		when(cache.get(ID)).thenReturn(Optional.empty(), Optional.of(response));
		when(lock.tryLock(ID, Duration.ZERO, LEASE)).thenReturn(true);

		assertThat(service.getAnnouncement(ID)).isEqualTo(response);

		verifyNoInteractions(repository);
		verify(lock).unlock(ID);
	}

	@Test
	void waitingRequestUsesCacheFilledByPreviousOwner() throws InterruptedException {
		when(cache.get(ID)).thenReturn(Optional.empty(), Optional.of(response));
		when(lock.tryLock(ID, Duration.ZERO, LEASE)).thenReturn(false);
		when(lock.tryLock(ID, WAIT, LEASE)).thenReturn(true);

		assertThat(service.getAnnouncement(ID)).isEqualTo(response);

		verifyNoInteractions(repository);
		verify(metrics).recordLockWait();
		verify(lock).unlock(ID);
	}

	@Test
	void cacheIsPopulatedBeforeUnlocking() throws InterruptedException {
		when(cache.get(ID)).thenReturn(Optional.empty());
		when(lock.tryLock(ID, Duration.ZERO, LEASE)).thenReturn(true);
		when(repository.findWithCompanyById(ID)).thenReturn(Optional.of(announcement));

		assertThat(service.getAnnouncement(ID)).isEqualTo(response);

		var order = inOrder(repository, cache, lock);
		order.verify(repository).findWithCompanyById(ID);
		order.verify(cache).put(eq(ID), eq(response), any(Duration.class));
		order.verify(lock).unlock(ID);
	}

	@Test
	void lockTimeoutFallsBackToDatabaseWithoutUnlocking() throws InterruptedException {
		when(cache.get(ID)).thenReturn(Optional.empty());
		when(repository.findWithCompanyById(ID)).thenReturn(Optional.of(announcement));

		assertThat(service.getAnnouncement(ID)).isEqualTo(response);

		verify(lock).tryLock(ID, WAIT, LEASE);
		verify(metrics).recordLockTimeout();
		verify(lock, never()).unlock(anyLong());
		verify(cache, never()).put(anyLong(), any(), any());
	}

	@Test
	void lockTimeoutStillReturnsCacheIfItWasJustFilled() throws InterruptedException {
		when(cache.get(ID)).thenReturn(Optional.empty(), Optional.of(response));

		assertThat(service.getAnnouncement(ID)).isEqualTo(response);

		verifyNoInteractions(repository);
		verify(lock, never()).unlock(anyLong());
	}

	@Test
	void redissonFailureFallsBackToDatabase() throws InterruptedException {
		when(cache.get(ID)).thenReturn(Optional.empty());
		when(lock.tryLock(ID, Duration.ZERO, LEASE)).thenThrow(new RedisException("unavailable"));
		when(repository.findWithCompanyById(ID)).thenReturn(Optional.of(announcement));

		assertThat(service.getAnnouncement(ID)).isEqualTo(response);

		verify(metrics).recordRedisFailure();
		verify(lock, never()).unlock(anyLong());
	}

	@Test
	void cacheFailureFallsBackWithoutAcquiringLock() {
		when(cache.get(ID)).thenThrow(new RedisConnectionFailureException("unavailable"));
		when(repository.findWithCompanyById(ID)).thenReturn(Optional.of(announcement));

		assertThat(service.getAnnouncement(ID)).isEqualTo(response);

		verifyNoInteractions(lock);
	}

	@Test
	void interruptionPreservesFlagAndDoesNotUnlock() throws InterruptedException {
		when(cache.get(ID)).thenReturn(Optional.empty());
		when(lock.tryLock(ID, Duration.ZERO, LEASE)).thenReturn(false);
		when(lock.tryLock(ID, WAIT, LEASE)).thenThrow(new InterruptedException());
		when(repository.findWithCompanyById(ID)).thenReturn(Optional.of(announcement));
		try {
			assertThat(service.getAnnouncement(ID)).isEqualTo(response);
			assertThat(Thread.currentThread().isInterrupted()).isTrue();
			verify(lock, never()).unlock(anyLong());
		} finally {
			Thread.interrupted();
		}
	}

	@Test
	void databaseFailureStillReleasesAcquiredLock() throws InterruptedException {
		when(cache.get(ID)).thenReturn(Optional.empty());
		when(lock.tryLock(ID, Duration.ZERO, LEASE)).thenReturn(true);
		when(repository.findWithCompanyById(ID)).thenThrow(new IllegalStateException("database unavailable"));

		assertThatThrownBy(() -> service.getAnnouncement(ID)).isInstanceOf(IllegalStateException.class);

		verify(lock).unlock(ID);
	}
}
