package com.sponus.sponusbe.domain.announcement.dto.response;

import java.time.LocalDate;

import com.sponus.core.domain.announcement.Announcement;
import com.sponus.core.domain.announcement.AnnouncementStatus;
import com.sponus.core.domain.organization.company.CollaborationTypeEnum;

import lombok.Builder;

@Builder
public record AnnouncementGetResponse(
	Long id,
	Long companyId,
	String companyName,
	String companyImageUrl,
	String title,
	String content,
	CollaborationTypeEnum collaborationType,
	LocalDate startDate,
	LocalDate endDate,
	AnnouncementStatus status,
	int viewCount,
	LocalDate createdAt
) {
	public static AnnouncementGetResponse from(Announcement announcement) {
		return AnnouncementGetResponse.builder()
			.id(announcement.getId())
			.companyId(announcement.getCompany().getId())
			.companyName(announcement.getCompany().getName())
			.companyImageUrl(announcement.getCompany().getImageUrl())
			.title(announcement.getTitle())
			.content(announcement.getContent())
			.collaborationType(announcement.getCollaborationType())
			.startDate(announcement.getStartDate())
			.endDate(announcement.getEndDate())
			.status(announcement.getStatus())
			.viewCount(announcement.getViewCount())
			.createdAt(announcement.getCreatedAt() == null ? null : announcement.getCreatedAt().toLocalDate())
			.build();
	}
}
