package com.sponus.sponusbe.domain.portfolio.exception;

import com.sponus.core.domain.common.BaseErrorCode;
import com.sponus.sponusbe.global.exception.CustomException;

public class PortfolioException extends CustomException {
	public PortfolioException(BaseErrorCode errorCode) {
		super(errorCode);
	}
}
