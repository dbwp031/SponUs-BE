package com.sponus.sponusbe.domain.announcement.controller;

import static com.sponus.sponusbe.global.enums.ApiPath.*;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sponus.core.domain.common.ApiResponse;
import com.sponus.core.domain.organization.Organization;
import com.sponus.security.annotation.AuthOrganization;
import com.sponus.sponusbe.domain.announcement.dto.request.AnnouncementCreateRequest;
import com.sponus.sponusbe.domain.announcement.dto.request.AnnouncementUpdateRequest;
import com.sponus.sponusbe.domain.announcement.dto.response.AnnouncementCreateResponse;
import com.sponus.sponusbe.domain.announcement.dto.response.AnnouncementGetResponse;
import com.sponus.sponusbe.domain.announcement.service.AnnouncementQueryService;
import com.sponus.sponusbe.domain.announcement.service.AnnouncementService;
import com.sponus.sponusbe.domain.organization.dto.request.PageCondition;
import com.sponus.sponusbe.domain.organization.dto.response.PageResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 기업 본인의 제휴 공고 관리 (생성/내 목록 조회).
 */
@Slf4j
@RequiredArgsConstructor
@RequestMapping(ANNOUNCEMENT_URI)
@RestController
public class AnnouncementController {

	private final AnnouncementService announcementService;
	private final AnnouncementQueryService announcementQueryService;

	@PostMapping
	public ApiResponse<AnnouncementCreateResponse> createAnnouncement(
		@AuthOrganization Organization authOrganization,
		@RequestBody @Valid AnnouncementCreateRequest request
	) {
		return ApiResponse.onSuccess(announcementService.createAnnouncement(authOrganization, request));
	}

	@GetMapping
	public ApiResponse<PageResponse<AnnouncementGetResponse>> getMyAnnouncements(
		@AuthOrganization Organization authOrganization,
		@ModelAttribute @Valid PageCondition pageCondition
	) {
		return ApiResponse.onSuccess(announcementQueryService.getMyAnnouncements(authOrganization, pageCondition));
	}

	// 공고 수정 → 커밋 이후 캐시 무효화(delayed double delete)
	@PatchMapping("/{announcementId}")
	public ApiResponse<Void> updateAnnouncement(
		@AuthOrganization Organization authOrganization,
		@PathVariable Long announcementId,
		@RequestBody AnnouncementUpdateRequest request
	) {
		announcementService.updateAnnouncement(authOrganization, announcementId, request);
		return ApiResponse.onSuccess(null);
	}
}
