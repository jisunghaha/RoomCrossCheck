package com.roomcrosscheck.auth.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roomcrosscheck.auth.dto.AuthDto.MessageResponse;
import com.roomcrosscheck.auth.service.MemberPrincipal;
import com.roomcrosscheck.community.service.CurrentActor;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 실제 회원 로그인·세션·CSRF와 모든 {@code /api} 경로의 인증 접근을 관리한다.
 * <p>공개 조회는 커뮤니티 글과 거주 후기로 한정한다. 매물·광고·건축물대장·방문 기록·비교 선택·신고는
 * 로그인을 요구하며, 각 서비스가 회원 번호로 소유권을 다시 확인한다. 관리자 API는 ADMIN 권한만 허용한다.
 * 쿠키 세션을 쓰므로 GET이 아닌 모든 요청에 CSRF 토큰이 필요하다.
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfiguration {
    @Bean
    PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean
    SecurityFilterChain memberSecurity(HttpSecurity http, ObjectMapper mapper) throws Exception {
        var csrf = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrf.setCookieCustomizer(cookie -> cookie.path("/").sameSite("Lax"));
        http.securityMatcher("/api/**")
            .csrf(config -> config.csrfTokenRepository(csrf).csrfTokenRequestHandler(new SpaCsrfHandler()))
            .requestCache(cache -> cache.disable())
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint((request, response, error) -> json(mapper, response, 401, "로그인이 필요합니다."))
                .accessDeniedHandler((request, response, error) -> {
                    var actor = SecurityContextHolder.getContext().getAuthentication();
                    boolean guest = actor == null || !actor.isAuthenticated() || actor instanceof AnonymousAuthenticationToken;
                    // 비로그인 쓰기는 CSRF 실패보다 로그인 필요를 먼저 안내한다. 가입·로그인 요청은 CSRF 오류로 남긴다.
                    boolean memberWrite = !request.getRequestURI().startsWith(request.getContextPath() + "/api/auth/")
                        && !"GET".equals(request.getMethod()) && !"HEAD".equals(request.getMethod());
                    int status = guest && memberWrite ? 401 : 403;
                    json(mapper, response, status, status == 401 ? "로그인이 필요합니다." : "권한 또는 보안 토큰을 확인해 주세요.");
                }))
            .authorizeHttpRequests(requests -> requests
                .requestMatchers(HttpMethod.GET, "/api/auth/config", "/api/auth/csrf", "/api/community/**",
                    "/api/reviews").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/signup", "/api/auth/login").permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/auth/me", "/api/auth/logout", "/api/community/**",
                    "/api/listings", "/api/listings/**", "/api/advertisements", "/api/advertisements/**",
                    "/api/visits", "/api/visit-checklist", "/api/comparison",
                    "/api/reviews", "/api/reviews/**", "/api/reports").authenticated()
                .anyRequest().denyAll())
            .sessionManagement(session -> session.sessionFixation(fixation -> fixation.changeSessionId()))
            .formLogin(login -> login.loginProcessingUrl("/api/auth/login")
                .successHandler((request, response, authentication) -> {
                    request.getSession().setMaxInactiveInterval(20 * 60);
                    json(mapper, response, 200, "로그인했습니다.");
                })
                .failureHandler((request, response, error) -> json(mapper, response, 401, "아이디 또는 비밀번호가 올바르지 않습니다.")))
            .logout(logout -> logout.logoutUrl("/api/auth/logout").invalidateHttpSession(true)
                .clearAuthentication(true).deleteCookies("JSESSIONID")
                .logoutSuccessHandler((request, response, authentication) -> json(mapper, response, 200, "로그아웃했습니다.")));
        return http.build();
    }

    /** 요청의 실제 인증 Principal만 커뮤니티에 전달하며 클라이언트의 회원 번호·권한은 신뢰하지 않는다. */
    @Bean
    CurrentActor.Provider memberActor() {
        return () -> {
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()
                    || !(authentication.getPrincipal() instanceof MemberPrincipal member)) return null;
            return member;
        };
    }

    private static void json(ObjectMapper mapper, HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        mapper.writeValue(response.getWriter(), new MessageResponse(status, message));
    }
}
