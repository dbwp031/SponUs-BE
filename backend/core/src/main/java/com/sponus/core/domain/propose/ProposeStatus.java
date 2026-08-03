package com.sponus.core.domain.propose;

public enum ProposeStatus {
	ACCEPTED, REJECTED, WAITING;

	public static ProposeStatus of(String input) {
		return ProposeStatus.valueOf(input.toUpperCase());
	}
}
