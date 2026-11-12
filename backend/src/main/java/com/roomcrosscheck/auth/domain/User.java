package com.roomcrosscheck.auth.domain;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * 일반·소셜 회원의 공통 정보와 일반 로그인용 비밀번호 해시를 users 테이블에 저장한다.
 * <p>id는 로그인 아이디와 별개인 내부 회원 번호다.
 * 소셜 전용 회원은 loginId와 passwordHash가 없으며 외부 계정은 별도 테이블로 연결한다.
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

    // 소셜 전용 회원은 자체 아이디·비밀번호가 없으므로 필수 저장 제약을 제거한다.
    // 일반 회원가입의 필수 입력 검증은 기존 DTO와 Service에서 유지한다.
    @Column(name = "login_id", updatable = false, length = 30)
    private String loginId;

    @Column(name = "password_hash", length = 100)
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

    /**
     * 검증·정규화된 정보로 소셜 회원을 생성한다.
     * 자체 로그인 아이디와 비밀번호는 부여하지 않으며 권한은 USER로 유지한다.
     * @param nickname 서비스에서 검증한 표시 이름
     * @param email 서비스에서 검증·정규화한 이메일
     * @return 아직 저장되지 않은 소셜 회원
     */
    public static User socialMember(String nickname, String email) {
        User user = new User();
        user.nickname = nickname;
        user.email = email;
        return user;
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
