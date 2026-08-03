package com.sponus.security.annotation;

import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import com.sponus.core.domain.organization.Organization;
import com.sponus.core.domain.organization.repository.OrganizationRepository;
import com.sponus.security.jwt.exception.SecurityCustomException;
import com.sponus.security.jwt.exception.SecurityErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 인증 비활성화 상태에서 @AuthOrganization 을 해석한다.
 * SecurityContext(JWT) 대신 요청 헤더 "X-Organization-Id" 로 현재 조직을 지정한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Transactional
public class AuthOrganizationArgumentResolver implements HandlerMethodArgumentResolver {

	private static final String ORGANIZATION_ID_HEADER = "X-Organization-Id";

	private final OrganizationRepository organizationRepository;

	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		boolean hasParameterAnnotation = parameter.hasParameterAnnotation(AuthOrganization.class);
		boolean isOrganizationParameterType = parameter.getParameterType().isAssignableFrom(Organization.class);
		return hasParameterAnnotation && isOrganizationParameterType;
	}

	@Override
	public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
		NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
		String header = webRequest.getHeader(ORGANIZATION_ID_HEADER);

		if (header == null || header.isBlank()) {
			log.error("[*] Missing {} header (security disabled)", ORGANIZATION_ID_HEADER);
			throw new SecurityCustomException(SecurityErrorCode.TOKEN_NOT_FOUND);
		}

		final Long organizationId;
		try {
			organizationId = Long.parseLong(header.trim());
		} catch (NumberFormatException e) {
			log.error("[*] Invalid {} header: {}", ORGANIZATION_ID_HEADER, header);
			throw new SecurityCustomException(SecurityErrorCode.TOKEN_NOT_FOUND);
		}

		return organizationRepository.findById(organizationId)
			.orElseThrow(() -> new SecurityCustomException(SecurityErrorCode.TOKEN_ORGANIZATION_NOT_FOND));
	}
}
