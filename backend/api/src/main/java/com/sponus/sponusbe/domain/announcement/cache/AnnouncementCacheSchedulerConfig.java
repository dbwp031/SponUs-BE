package com.sponus.sponusbe.domain.announcement.cache;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * delayed double delete 의 2차(지연) 캐시 삭제를 실행하는 로컬 스케줄러.
 * 앱 재시작 시 예약 작업이 유실될 수 있으므로 캐시 TTL 이 최종 안전장치다.
 */
@Configuration
public class AnnouncementCacheSchedulerConfig {

	public static final String CACHE_SCHEDULER = "announcementCacheScheduler";

	@Bean(CACHE_SCHEDULER)
	public TaskScheduler announcementCacheScheduler() {
		ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
		scheduler.setPoolSize(2);
		scheduler.setThreadNamePrefix("ann-cache-evict-");
		scheduler.setRemoveOnCancelPolicy(true);
		scheduler.initialize();
		return scheduler;
	}
}
