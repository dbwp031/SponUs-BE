package com.sponus.sponusbe.domain.propose.dto.response;

import java.time.LocalDate;

import com.sponus.core.domain.propose.Propose;
import com.sponus.core.domain.propose.ProposeStatus;

import lombok.Builder;

@Builder
public record ReceiveProposeGetResponse(
	Long id,
	Long organizationId,
	Long target,
	String organizationName,
	String organizationImageUrl,
	Long announcementId,
	String announcementTitle,
	ProposeStatus status,
	LocalDate createdAt
) {

	public static ReceiveProposeGetResponse from(Propose propose) {
		return ReceiveProposeGetResponse.builder()
			.id(propose.getId())
			.organizationId(propose.getOrganization().getId())
			.target(propose.getTarget().getId())
			.organizationName(propose.getOrganization().getName())
			.organizationImageUrl(propose.getOrganization().getImageUrl())
			.announcementId(propose.getAnnouncement().getId())
			.announcementTitle(propose.getAnnouncement().getTitle())
			.status(propose.getStatus())
			.createdAt(propose.getCreatedAt().toLocalDate())
			.build();
	}
}
