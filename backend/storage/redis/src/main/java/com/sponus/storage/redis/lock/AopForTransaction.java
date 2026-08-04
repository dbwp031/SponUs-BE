package com.sponus.storage.redis.lock;

import org.aspectj.lang.ProceedingJoinPoint;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 분산락 획득 이후 별도의 새 트랜잭션에서 대상 로직을 실행한다.
 * 락 → 트랜잭션 시작 → 커밋 → 락 해제 순서를 보장하여, 커밋 전에 락이 풀려
 * 다른 요청이 끼어드는 것을 방지한다.
 */
@Component
public class AopForTransaction {

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public Object proceed(ProceedingJoinPoint joinPoint) throws Throwable {
		return joinPoint.proceed();
	}
}
