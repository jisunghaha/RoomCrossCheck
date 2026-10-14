package com.roomcrosscheck.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/** 실제 인증과 업무 API를 구현하기 전까지 요청을 거부한다. */
@Configuration
public class SecurityConfiguration {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // CSRF 기본 보호를 유지하고 업무 경로는 아직 공개하지 않는다.
        return http.authorizeHttpRequests(requests -> requests.anyRequest().denyAll()).build();
    }
}
