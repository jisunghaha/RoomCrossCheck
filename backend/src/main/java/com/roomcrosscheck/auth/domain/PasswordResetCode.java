package com.roomcrosscheck.auth.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * 비밀번호 찾기용 이메일 인증 코드를 password_reset_codes 테이블에 매핑한다.
 * <p>코드 원문은 저장하지 않고 BCrypt 해시만 보관한다. 회원당 유효한 코드는 하나이며 새로 요청하면
 * 이전 코드를 지운다. 10분이 지나거나 5번 틀리면 쓸 수 없다. 회원이 탈퇴하면 함께 삭제된다.
 */
@Entity
@Table(name = "password_reset_codes", indexes = @Index(name = "idx_password_reset_codes_user", columnList = "user_id"))
public class PasswordResetCode {
    public static final int MAX_ATTEMPTS = 5;

    @Id
    @Column(length = 36, nullable = false, updatable = false)
    public String id = UUID.randomUUID().toString();

    @Column(name = "user_id", nullable = false, updatable = false, columnDefinition = "bigint unsigned")
    public Long userId;

    @Column(name = "code_hash", nullable = false, length = 100, updatable = false)
    public String codeHash;

    @Column(name = "expires_at", nullable = false, updatable = false)
    public Instant expiresAt;

    /** 틀린 코드 입력 횟수. */
    @Column(nullable = false)
    public int attempts;

    @Column(name = "used_at")
    public Instant usedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    protected PasswordResetCode() {}

    public PasswordResetCode(Long userId, String codeHash, Instant createdAt, Instant expiresAt) {
        this.userId = userId;
        this.codeHash = codeHash;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    /** 아직 쓰지 않았고, 만료 전이며, 시도 횟수가 남아 있으면 true. */
    public boolean usable(Instant now) {
        return usedAt == null && expiresAt.isAfter(now) && attempts < MAX_ATTEMPTS;
    }
}
