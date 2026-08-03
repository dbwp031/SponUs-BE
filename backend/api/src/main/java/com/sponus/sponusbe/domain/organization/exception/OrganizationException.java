package com.sponus.sponusbe.domain.organization.exception;

import com.sponus.core.domain.common.BaseErrorCode;
import com.sponus.sponusbe.global.exception.CustomException;

public class OrganizationException extends CustomException {

	public OrganizationException(BaseErrorCode errorCode) {
		super(errorCode);
	}
}
