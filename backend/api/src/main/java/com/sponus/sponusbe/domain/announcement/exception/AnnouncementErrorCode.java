package com.sponus.sponusbe.domain.announcement.exception;

import org.springframework.http.HttpStatus;

import com.sponus.core.domain.common.ApiResponse;
import com.sponus.core.domain.common.BaseErrorCode;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum AnnouncementErrorCode implements BaseErrorCode {
	ANNOUNCEMENT_ERROR(HttpStatus.BAD_REQUEST, "ANN4000", "공고 관련 에러"),
	ANNOUNCEMENT_NOT_RECRUITING(HttpStatus.CONFLICT, "ANN4009", "모집이 마감된 공고입니다."),
	ANNOUNCEMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "ANN4040", "존재하지 않는 공고입니다.");

	private final HttpStatus httpStatus;
	private final String code;
	private final String message;

	@Override
	public ApiResponse<Void> getErrorResponse() {
		return ApiResponse.onFailure(code, message);
	}
}
