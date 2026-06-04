package com.sponus.sponusbe.domain.propose.dto.request;

import com.sponus.core.domain.announcement.Announcement;
import com.sponus.core.domain.organization.Organization;
import com.sponus.core.domain.propose.Propose;

import jakarta.validation.constraints.NotNull;

public record ProposeCreateRequest(
	@NotNull(message = "지원할 공고 ID는 필수입니다.")
	Long announcementId
) {
	// 지원자(동아리)가 공고에 지원. target 은 공고 소유 기업으로 유도한다.
	public Propose toEntity(Organization applicant, Announcement announcement) {
		return Propose.builder()
			.organization(applicant)
			.target(announcement.getCompany())
			.announcement(announcement)
			.build();
	}
}
