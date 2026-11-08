package com.roomcrosscheck.auth.domain;

import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/** 별도 시연 환경에서 비밀번호 검증에 사용하는 계정이며 정식 users 회원이 아니다. */
public record DemoAccount(Long userId, String username, String password, boolean administrator) implements UserDetails {
    @Override public String getUsername() { return username; }
    @Override public String getPassword() { return password; }
    @Override public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(administrator ? "ROLE_ADMIN" : "ROLE_USER"));
    }
}
