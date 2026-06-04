package com.sponus.sponusbe.domain.announcement.dto.response;

import com.sponus.core.domain.announcement.Announcement;

import lombok.Builder;

@Builder
public record AnnouncementCreateResponse(
	Long id,
	Long companyId
) {
	public static AnnouncementCreateResponse from(Announcement announcement) {
		return AnnouncementCreateResponse.builder()
			.id(announcement.getId())
			.companyId(announcement.getCompany().getId())
			.build();
	}
}
