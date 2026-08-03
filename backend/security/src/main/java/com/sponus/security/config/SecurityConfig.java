package com.sponus.security.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 인증/인가를 비활성화한 SecurityConfig.
 * 모든 요청을 permitAll 로 열고, JWT 로그인/필터·로그아웃 핸들러는 등록하지 않는다.
 * (관련 필터/유틸 클래스는 삭제하지 않고 그대로 두어 재활성화 시 복구가 쉽도록 한다.)
 * BCryptPasswordEncoder 빈은 회원가입(OrganizationService)에서 비밀번호 인코딩에 사용되므로 유지한다.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

	@Bean
	public BCryptPasswordEncoder bCryptPasswordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		http
			.cors(cors -> cors
				.configurationSource(CorsConfig.apiConfigurationSource()))
			.csrf(AbstractHttpConfigurer::disable)
			.formLogin(AbstractHttpConfigurer::disable)
			.httpBasic(AbstractHttpConfigurer::disable)
			.sessionManagement(session -> session
				.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(auth -> auth
				.anyRequest().permitAll());

		return http.build();
	}
}
