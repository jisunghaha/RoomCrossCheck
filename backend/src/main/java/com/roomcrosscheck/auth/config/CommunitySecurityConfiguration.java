package com.roomcrosscheck.auth.config;

import com.roomcrosscheck.auth.domain.DemoAccount;
import com.roomcrosscheck.community.service.CurrentActor;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

/** 커뮤니티와 로그인 경로에만 세션·권한·CSRF 검사를 적용한다. 기존 매물 API의 인증 통합은 별도다. */
@Configuration(proxyBeanMethods = false)
public class CommunitySecurityConfiguration {
    @Bean
    PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean
    UserDetailsService demoAccounts(Environment environment, PasswordEncoder encoder) {
        if (!environment.acceptsProfiles(Profiles.of("demo-login")))
            return username -> { throw new UsernameNotFoundException("사용할 수 없는 계정입니다."); };
        if (environment.acceptsProfiles(Profiles.of("prod", "production")))
            throw new IllegalStateException("운영 프로필에서는 시연 로그인을 사용할 수 없습니다.");
        String url = environment.getProperty("spring.datasource.url", "");
        if (!url.matches("jdbc:mysql://[^/]+/rc_demo_[a-z0-9_]+(?:\\?.*)?"))
            throw new IllegalStateException("시연 로그인은 별도 rc_demo_ MySQL DB에서만 실행할 수 있습니다.");
        var passwords = List.of(requiredPassword(environment, "DEMO_MEMBER1_PASSWORD"),
            requiredPassword(environment, "DEMO_MEMBER2_PASSWORD"), requiredPassword(environment, "DEMO_ADMIN_PASSWORD"));
        if (passwords.stream().distinct().count() != 3)
            throw new IllegalStateException("시연 계정의 비밀번호는 서로 달라야 합니다.");
        var accounts = List.of(new DemoAccount(900000001L, "member1", encoder.encode(passwords.get(0)), false),
            new DemoAccount(900000002L, "member2", encoder.encode(passwords.get(1)), false),
            new DemoAccount(900000003L, "admin", encoder.encode(passwords.get(2)), true));
        return username -> accounts.stream().filter(account -> account.username().equals(username)).findFirst()
            .orElseThrow(() -> new UsernameNotFoundException("사용할 수 없는 계정입니다."));
    }

    private static String requiredPassword(Environment environment, String name) {
        String password = environment.getProperty(name);
        if (password == null || password.length() < 12 || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
            throw new IllegalStateException(name + "에 12자 이상·UTF-8 72바이트 이하 비밀번호가 필요합니다.");
        return password;
    }

    @Bean
    SecurityFilterChain communitySecurity(HttpSecurity http, Environment environment) throws Exception {
        boolean demo = environment.acceptsProfiles(Profiles.of("demo-login"));
        var csrf = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrf.setCookieCustomizer(cookie -> cookie.path("/").sameSite("Lax"));
        http.securityMatcher("/api/community/**", "/api/auth/**")
            .csrf(config -> config.csrfTokenRepository(csrf).csrfTokenRequestHandler(new SpaCsrfHandler()))
            .requestCache(cache -> cache.disable())
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint((request, response, error) -> json(response, 401, "로그인이 필요합니다."))
                .accessDeniedHandler((request, response, error) -> json(response, 403, "권한 또는 보안 토큰을 확인해 주세요.")))
            .authorizeHttpRequests(requests -> {
                requests.requestMatchers(HttpMethod.GET, "/api/auth/config", "/api/community/**").permitAll();
                if (demo) {
                    requests.requestMatchers(HttpMethod.GET, "/api/auth/csrf").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers("/api/community/**").authenticated();
                }
                requests.anyRequest().denyAll();
            });
        if (demo) http.formLogin(login -> login.loginProcessingUrl("/api/auth/login")
                .successHandler((request, response, authentication) -> json(response, 200, "로그인했습니다."))
                .failureHandler((request, response, exception) -> json(response, 401, "아이디 또는 비밀번호가 올바르지 않습니다.")))
            .logout(logout -> logout.logoutUrl("/api/auth/logout").invalidateHttpSession(true)
                .clearAuthentication(true).deleteCookies("JSESSIONID")
                .logoutSuccessHandler((request, response, authentication) -> json(response, 200, "로그아웃했습니다.")));
        return http.build();
    }

    @Bean
    @Profile("demo-login")
    CurrentActor.Provider communityActor() {
        return () -> {
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()
                    || !(authentication.getPrincipal() instanceof DemoAccount account)) return null;
            return new CurrentActor() {
                @Override public Long userId() { return account.userId(); }
                @Override public boolean administrator() { return account.administrator(); }
            };
        };
    }

    private static void json(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"message\":\"" + message + "\"}");
    }
}
