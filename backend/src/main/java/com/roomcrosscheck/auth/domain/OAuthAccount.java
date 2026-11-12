package com.roomcrosscheck.auth.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;

/**
 * 구글 계정의 고유 식별자와 내부 회원을 oauth_accounts 테이블로 연결한다.
 * <p>이메일 대신 제공자와 sub 조합으로 외부 계정을 식별한다.
 * 토큰·비밀번호·클라이언트 보안 비밀은 저장하지 않는다.
 * 연결 정보를 삭제해도 연결된 회원을 자동으로 삭제하지 않는다.
 */
@Entity
@Table(name = "oauth_accounts", uniqueConstraints = {
    @UniqueConstraint(
        name = "uk_oauth_accounts_provider_subject",
        columnNames = {"provider", "provider_subject"}
    )
})
public class OAuthAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false, updatable = false, columnDefinition = "bigint unsigned")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "user_id",
        nullable = false,
        updatable = false,
        columnDefinition = "bigint unsigned",
        foreignKey = @ForeignKey(name = "fk_oauth_accounts_user")
    )
    private User user;

    @Column(nullable = false, updatable = false, length = 20)
    private String provider;

    @Column(name = "provider_subject", nullable = false, updatable = false, length = 255)
    private String providerSubject;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected OAuthAccount() {}

    /**
     * 내부 회원과 검증된 구글 고유 식별자를 연결한다.
     * @param user 서비스에서 먼저 저장한 내부 회원
     * @param providerSubject 서버에서 검증한 구글 sub 값
     * @throws NullPointerException 연결할 회원이 없는 경우
     * @throws IllegalArgumentException 식별자가 비어 있거나 저장 길이를 초과한 경우
     */
    public OAuthAccount(User user, String providerSubject) {
        this.user = Objects.requireNonNull(user, "연결할 회원이 필요합니다.");
        if (providerSubject == null || providerSubject.isBlank() || providerSubject.length() > 255) {
            throw new IllegalArgumentException("구글 계정 식별자를 확인해 주세요.");
        }
        this.provider = "GOOGLE";
        this.providerSubject = providerSubject;
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public String getProvider() { return provider; }
    public String getProviderSubject() { return providerSubject; }
    public Instant getCreatedAt() { return createdAt; }

    @PrePersist
    protected void initializeTimestamp() {
        createdAt = Instant.now();
    }
}
