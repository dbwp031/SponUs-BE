package com.sponus.sponusbe.domain.announcement.dto.request;

import java.time.LocalDate;

import com.sponus.core.domain.organization.company.CollaborationTypeEnum;

// 부분 수정: null 필드는 기존 값 유지 (Announcement.update 에서 처리)
public record AnnouncementUpdateRequest(
	String title,
	String content,
	CollaborationTypeEnum collaborationType,
	LocalDate startDate,
	LocalDate endDate
) {
}
