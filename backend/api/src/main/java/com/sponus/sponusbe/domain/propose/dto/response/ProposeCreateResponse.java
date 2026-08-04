package com.sponus.sponusbe.domain.propose.dto.response;

import com.sponus.core.domain.propose.Propose;

import lombok.Builder;

@Builder
public record ProposeCreateResponse(
	Long id,
	Long organizationId,
	Long target,
	Long announcementId
) {

	public static ProposeCreateResponse from(Propose propose) {
		return ProposeCreateResponse.builder()
			.id(propose.getId())
			.organizationId(propose.getOrganization().getId())
			.target(propose.getTarget().getId())
			.announcementId(propose.getAnnouncement().getId())
			.build();
	}
}
