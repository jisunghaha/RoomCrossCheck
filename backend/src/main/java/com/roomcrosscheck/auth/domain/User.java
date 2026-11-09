package com.roomcrosscheck.auth.domain;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * 일반 회원의 식별 정보와 비밀번호 해시를 users 테이블에 저장한다.
 * <p>id는 로그인 아이디와 별개인 내부 회원 번호다.
 * 비밀번호 해시는 인증에만 사용하며 API 응답에 엔티티를 직접 노출하지 않는다.
 */
@Entity
@Table(name = "users", uniqueConstraints = {
    @UniqueConstraint(name = "uk_users_login_id", columnNames = "login_id"),
    @UniqueConstraint(name = "uk_users_email", columnNames = "email")
})
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false, updatable = false, columnDefinition = "bigint unsigned")
    private Long id;

    @Column(name = "login_id", nullable = false, updatable = false, length = 30)
    private String loginId;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(nullable = false, length = 30)
    private String nickname;

    @Column(nullable = false, length = 254)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private UserRole role = UserRole.USER;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected User() {}

    /** 검증·정규화된 가입 정보와 해시를 받아 일반 회원을 생성한다. */
    public User(String loginId, String passwordHash, String nickname, String email) {
        this.loginId = loginId;
        this.passwordHash = passwordHash;
        this.nickname = nickname;
        this.email = email;
    }

    public Long getId() { return id; }
    public String getLoginId() { return loginId; }
    public String getPasswordHash() { return passwordHash; }
    public String getNickname() { return nickname; }
    public String getEmail() { return email; }
    public UserRole getRole() { return role; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    @PrePersist
    protected void initializeTimestamps() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void updateTimestamp() { updatedAt = Instant.now(); }
}
