package com.sponus.storage.redis.lock;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class DistributedLockExecutor {

	private final RedissonClient redissonClient;

	public DistributedLockExecutor(@Lazy RedissonClient redissonClient) {
		this.redissonClient = redissonClient;
	}

	public <T> T execute(String key, long waitSeconds, long leaseSeconds, Supplier<T> task) {
		final RLock lock = redissonClient.getLock(key);
		boolean acquired = false;
		try {
			acquired = lock.tryLock(waitSeconds, leaseSeconds, TimeUnit.SECONDS);
			if (!acquired) {
				throw new LockAcquisitionException("Failed to acquire lock: " + key);
			}
			return task.get();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new LockAcquisitionException("Interrupted while acquiring lock: " + key);
		} finally {
			if (acquired && lock.isHeldByCurrentThread()) {
				lock.unlock();
			}
		}
	}
}
