package com.roomcrosscheck.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/** Authentication and public endpoint rules will be added with SFR-001. */
@Configuration
public class SecurityConfiguration {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // Retain CSRF protection. No business endpoints are exposed by this skeleton.
        return http.authorizeHttpRequests(requests -> requests.anyRequest().denyAll()).build();
    }
}
