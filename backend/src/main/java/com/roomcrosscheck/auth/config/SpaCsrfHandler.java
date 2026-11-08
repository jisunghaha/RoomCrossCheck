package com.roomcrosscheck.auth.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.function.Supplier;
import org.springframework.security.web.csrf.*;
import org.springframework.util.StringUtils;

/** 쿠키의 원본 토큰과 응답의 마스킹 토큰을 구분해 SPA의 CSRF 보호를 유지한다. */
final class SpaCsrfHandler implements CsrfTokenRequestHandler {
    private final CsrfTokenRequestHandler plain = new CsrfTokenRequestAttributeHandler();
    private final CsrfTokenRequestHandler masked = new XorCsrfTokenRequestAttributeHandler();

    @Override public void handle(HttpServletRequest request, HttpServletResponse response, Supplier<CsrfToken> token) {
        masked.handle(request, response, token);
        token.get();
    }

    @Override public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken token) {
        return (StringUtils.hasText(request.getHeader(token.getHeaderName())) ? plain : masked)
            .resolveCsrfTokenValue(request, token);
    }
}
