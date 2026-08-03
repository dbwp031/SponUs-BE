package com.sponus.security.jwt.dto;

public record JwtPair(
	String accessToken,
	String refreshToken
) {
}
