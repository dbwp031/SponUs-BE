package com.sponus.sponusbe.domain.propose.controller;

import static com.sponus.sponusbe.global.enums.ApiPath.*;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sponus.core.domain.common.ApiResponse;
import com.sponus.core.domain.organization.Organization;
import com.sponus.security.annotation.AuthOrganization;
import com.sponus.sponusbe.domain.organization.dto.request.PageCondition;
import com.sponus.sponusbe.domain.organization.dto.response.PageResponse;
import com.sponus.sponusbe.domain.propose.dto.request.ProposeCreateRequest;
import com.sponus.sponusbe.domain.propose.dto.response.ProposeCreateResponse;
import com.sponus.sponusbe.domain.propose.dto.response.ReceiveProposeGetResponse;
import com.sponus.sponusbe.domain.propose.dto.response.SendProposeGetResponse;
import com.sponus.sponusbe.domain.propose.service.ProposeLockFacade;
import com.sponus.sponusbe.domain.propose.service.ProposeQueryService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
@RequestMapping(PROPOSE_URI)
@RestController
public class ProposeController {

	private final ProposeLockFacade proposeLockFacade;
	private final ProposeQueryService proposequeryService;

	@PostMapping()
	public ApiResponse<ProposeCreateResponse> createPropose(
		@AuthOrganization Organization authOrganization,
		@RequestBody @Valid ProposeCreateRequest request
	) {
		return ApiResponse.onSuccess(proposeLockFacade.createPropose(authOrganization, request));
	}

	@GetMapping("/send")
	public ApiResponse<PageResponse<SendProposeGetResponse>> getSendPropose(
		@AuthOrganization Organization authOrganization,
		@ModelAttribute @Valid PageCondition pageCondition
	) {
		return ApiResponse.onSuccess(proposequeryService.getSendPropose(authOrganization, pageCondition));
	}

	@GetMapping("/receive")
	public ApiResponse<PageResponse<ReceiveProposeGetResponse>> getReceivedPropose(
		@AuthOrganization Organization authOrganization,
		@ModelAttribute @Valid PageCondition pageCondition
	) {
		return ApiResponse.onSuccess(proposequeryService.getReceivedPropose(authOrganization, pageCondition));
	}
}
