package com.sponus.core.domain.announcement;

public enum AnnouncementStatus {
	RECRUITING, CLOSED;

	public static AnnouncementStatus of(String input) {
		return AnnouncementStatus.valueOf(input.toUpperCase());
	}
}
