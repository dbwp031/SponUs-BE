package com.sponus.storage.redis.lock;

import java.lang.reflect.Method;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * @DistributedLock 이 붙은 메서드에 Redisson 분산락을 적용한다.
 * 락 획득 → (새 트랜잭션) 로직 실행 → 커밋 → 락 해제 순서로 동작한다.
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class DistributedLockAop {

	private static final String LOCK_KEY_PREFIX = "LOCK:";

	private final RedissonClient redissonClient;
	private final AopForTransaction aopForTransaction;

	@Around("@annotation(com.sponus.storage.redis.lock.DistributedLock)")
	public Object lock(ProceedingJoinPoint joinPoint) throws Throwable {
		MethodSignature signature = (MethodSignature)joinPoint.getSignature();
		Method method = signature.getMethod();
		DistributedLock distributedLock = method.getAnnotation(DistributedLock.class);

		String key = LOCK_KEY_PREFIX + CustomSpringELParser.getDynamicValue(
			signature.getParameterNames(), joinPoint.getArgs(), distributedLock.key());

		RLock rLock = redissonClient.getLock(key);

		boolean locked = false;
		try {
			locked = rLock.tryLock(distributedLock.waitTime(), distributedLock.leaseTime(),
				distributedLock.timeUnit());
			if (!locked) {
				log.warn("[DistributedLock] 락 획득 실패 key={}", key);
				throw new IllegalStateException("요청이 처리 중입니다. 잠시 후 다시 시도해 주세요.");
			}
			log.info("[DistributedLock] 락 획득 key={}", key);
			return aopForTransaction.proceed(joinPoint);
		} finally {
			if (locked && rLock.isHeldByCurrentThread()) {
				rLock.unlock();
				log.info("[DistributedLock] 락 해제 key={}", key);
			}
		}
	}
}
