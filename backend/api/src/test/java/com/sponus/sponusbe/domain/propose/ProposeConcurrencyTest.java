package com.sponus.sponusbe.domain.propose;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import com.sponus.clients.fcm.FirebaseUtil;
import com.sponus.core.domain.announcement.Announcement;
import com.sponus.core.domain.announcement.AnnouncementStatus;
import com.sponus.core.domain.announcement.repository.AnnouncementRepository;
import com.sponus.core.domain.organization.club.Club;
import com.sponus.core.domain.organization.company.Company;
import com.sponus.core.domain.organization.enums.ProfileStatus;
import com.sponus.core.domain.organization.repository.OrganizationRepository;
import com.sponus.core.domain.propose.repository.ProposeRepository;
import com.sponus.sponusbe.domain.propose.dto.request.ProposeCreateRequest;
import com.sponus.sponusbe.domain.propose.service.ProposeLockFacade;

@SpringBootTest
@ActiveProfiles("test")
class ProposeConcurrencyTest {

	@Autowired
	ProposeLockFacade proposeLockFacade;

	@Autowired
	ProposeRepository proposeRepository;

	@Autowired
	AnnouncementRepository announcementRepository;

	@Autowired
	OrganizationRepository organizationRepository;

	@MockBean
	FirebaseUtil firebaseUtil;

	Club applicant;
	Announcement announcement;

	@BeforeEach
	void init() {
		Company target = new Company("sponus_company", "concurrency_company@gmail.com", "sponus1234#");
		organizationRepository.save(target);

		applicant = new Club("sponus_club", "concurrency_club@gmail.com", "sponus1234#");
		applicant.updateInfo("sponus_club", "동아리 소개", "https://image.url/club.png", 10, ProfileStatus.ACTIVE);
		organizationRepository.save(applicant);

		announcement = announcementRepository.save(Announcement.builder()
			.company(target)
			.title("제휴 공고")
			.content("제휴 공고 내용")
			.status(AnnouncementStatus.RECRUITING)
			.build());
	}

	@AfterEach
	void tearDown() {
		proposeRepository.deleteAll();
		announcementRepository.deleteAll();
		organizationRepository.deleteAll();
	}

	@Test
	void 동시에_같은_제휴_요청이_들어와도_하나만_생성된다() throws InterruptedException {
		// given
		final int threadCount = 2;
		final ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
		final CountDownLatch startLatch = new CountDownLatch(1);
		final CountDownLatch doneLatch = new CountDownLatch(threadCount);
		final AtomicInteger successCount = new AtomicInteger();

		// when : 두 요청이 동시에 진입 (따닥)
		for (int i = 0; i < threadCount; i++) {
			executorService.submit(() -> {
				try {
					startLatch.await();
					proposeLockFacade.createPropose(applicant, new ProposeCreateRequest(announcement.getId()));
					successCount.incrementAndGet();
				} catch (Exception ignored) {
					// 락 대기 후 중복 확인에 걸리면 ALREADY_APPLIED, 락 획득 실패 시 PROPOSE_REQUEST_IN_PROGRESS
				} finally {
					doneLatch.countDown();
				}
			});
		}
		startLatch.countDown();
		doneLatch.await(30, TimeUnit.SECONDS);
		executorService.shutdown();

		// then : 제휴 요청은 단 하나만 생성된다
		Assertions.assertThat(successCount.get()).isEqualTo(1);
		Assertions.assertThat(proposeRepository.count()).isEqualTo(1);
	}
}
