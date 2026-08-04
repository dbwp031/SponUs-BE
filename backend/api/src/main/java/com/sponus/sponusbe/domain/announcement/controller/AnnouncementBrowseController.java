package com.sponus.sponusbe.domain.announcement.controller;

import static com.sponus.sponusbe.global.enums.ApiPath.*;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sponus.core.domain.common.ApiResponse;
import com.sponus.sponusbe.domain.announcement.dto.response.AnnouncementGetResponse;
import com.sponus.sponusbe.domain.announcement.service.AnnouncementQueryService;
import com.sponus.sponusbe.domain.organization.dto.request.PageCondition;
import com.sponus.sponusbe.domain.organization.dto.response.PageResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 공개 제휴 공고 브라우징 (동아리가 지원할 공고를 조회).
 */
@Slf4j
@RequiredArgsConstructor
@RequestMapping(ANNOUNCEMENTS_URI)
@RestController
public class AnnouncementBrowseController {

	private final AnnouncementQueryService announcementQueryService;

	@GetMapping
	public ApiResponse<PageResponse<AnnouncementGetResponse>> getAnnouncements(
		@ModelAttribute @Valid PageCondition pageCondition
	) {
		return ApiResponse.onSuccess(announcementQueryService.getAnnouncements(pageCondition));
	}

	@GetMapping("/{announcementId}")
	public ApiResponse<AnnouncementGetResponse> getAnnouncement(@PathVariable Long announcementId) {
		return ApiResponse.onSuccess(announcementQueryService.getAnnouncement(announcementId));
	}
}
