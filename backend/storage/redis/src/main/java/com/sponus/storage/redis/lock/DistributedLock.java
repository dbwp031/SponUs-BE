package com.sponus.storage.redis.lock;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * Redisson 기반 분산락. 메서드 실행을 key 기준으로 직렬화한다.
 * key 는 SpEL 로 평가되며 메서드 파라미터(#param)를 참조할 수 있다.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DistributedLock {

	// 락 식별 키 (SpEL). 예: "'PROPOSE:' + #applicant.id + ':' + #request.announcementId()"
	String key();

	TimeUnit timeUnit() default TimeUnit.SECONDS;

	// 락 획득을 기다리는 최대 시간
	long waitTime() default 5L;

	// 락 획득 후 자동 해제까지의 임대 시간
	long leaseTime() default 3L;
}
