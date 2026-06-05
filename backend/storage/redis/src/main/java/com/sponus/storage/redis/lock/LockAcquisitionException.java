package com.sponus.storage.redis.lock;

public class LockAcquisitionException extends RuntimeException {

	public LockAcquisitionException(String message) {
		super(message);
	}
}
