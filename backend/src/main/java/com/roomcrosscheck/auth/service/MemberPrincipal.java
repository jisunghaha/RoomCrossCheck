package com.roomcrosscheck.auth.service;

import com.roomcrosscheck.auth.domain.User;
import com.roomcrosscheck.auth.domain.UserRole;
import com.roomcrosscheck.community.service.CurrentActor;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * DB에서 확인한 회원의 인증 정보와 커뮤니티 권한을 연결한다.
 * <p>인증 후에는 비밀번호 해시를 지우며 JPA 엔티티를 세션에 보관하지 않는다.
 * API 응답으로 직접 직렬화하지 않고 회원 정보 DTO를 사용한다.
 */
public final class MemberPrincipal implements UserDetails, CredentialsContainer, CurrentActor, Serializable {
    private static final long serialVersionUID = 1L;
    private final Long userId;
    private final String loginId;
    private final UserRole role;
    private String passwordHash;

    public MemberPrincipal(User user) {
        this.userId = user.getId();
        this.loginId = user.getLoginId();
        this.role = user.getRole();
        this.passwordHash = user.getPasswordHash();
    }

    @Override public Long userId() { return userId; }
    @Override public boolean administrator() { return role == UserRole.ADMIN; }
    @Override public String getUsername() { return loginId; }
    @Override public String getPassword() { return passwordHash; }
    @Override public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }
    @Override public void eraseCredentials() { passwordHash = null; }
}
