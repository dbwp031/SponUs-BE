package com.sponus.sponusbe.domain.announcement.dto.request;

import java.time.LocalDate;

import com.sponus.core.domain.announcement.Announcement;
import com.sponus.core.domain.announcement.AnnouncementStatus;
import com.sponus.core.domain.organization.company.CollaborationTypeEnum;
import com.sponus.core.domain.organization.company.Company;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AnnouncementCreateRequest(
	@NotBlank(message = "제목은 필수 입력 값입니다.")
	String title,

	String content,

	@NotNull(message = "제휴 종류는 필수 입력 값입니다.")
	CollaborationTypeEnum collaborationType,

	LocalDate startDate,

	LocalDate endDate
) {
	public Announcement toEntity(Company company) {
		return Announcement.builder()
			.company(company)
			.title(title)
			.content(content)
			.collaborationType(collaborationType)
			.startDate(startDate)
			.endDate(endDate)
			.status(AnnouncementStatus.RECRUITING)
			.build();
	}
}
