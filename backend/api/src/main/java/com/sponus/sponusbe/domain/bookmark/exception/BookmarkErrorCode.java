package com.sponus.sponusbe.domain.bookmark.exception;

import org.springframework.http.HttpStatus;

import com.sponus.core.domain.common.ApiResponse;
import com.sponus.core.domain.common.BaseErrorCode;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum BookmarkErrorCode implements BaseErrorCode {

	BOOKMARK_ERROR(HttpStatus.BAD_REQUEST, "BMK4000", "북마크 관련 에러"),
	CANNOT_BOOKMARK_TO_SELF(HttpStatus.BAD_REQUEST, "PROP4011", "본인에게는 북마크할 수 없습니다.");

	private final HttpStatus httpStatus;
	private final String code;
	private final String message;

	@Override
	public ApiResponse<Void> getErrorResponse() {
		return ApiResponse.onFailure(code, message);
	}
}
