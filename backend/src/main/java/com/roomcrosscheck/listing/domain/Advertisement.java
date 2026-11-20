package com.roomcrosscheck.listing.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * SFR-002 광고 원본과 추출·확인 값을 advertisements 테이블에 매핑한다.
 * <p>광고는 올린 회원만 조회·추출·수정할 수 있다. 회원 연결 전의 기존 행은 소유자가 없어 표시되지 않는다.
 */
@Entity
@Table(name = "advertisements", uniqueConstraints = @UniqueConstraint(columnNames = "idempotency_key"),
    indexes = @Index(name = "idx_advertisements_owner", columnList = "owner_id"))
public class Advertisement {
    public enum Status { DRAFT, EXTRACTION_PENDING, EXTRACTION_READY, EXTRACTION_FAILED, REVIEWED }

    @Id public String id = UUID.randomUUID().toString();
    /** 광고를 올린 회원의 users.id. 응답 DTO에 포함하지 않는다. */
    @Column(name = "owner_id", updatable = false, columnDefinition = "bigint unsigned") public Long ownerId;
    @Column(name = "idempotency_key", nullable = false, length = 128) public String idempotencyKey;
    @Column(nullable = false, length = 64) public String requestHash;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) public Status status = Status.DRAFT;
    @Column(columnDefinition = "text") public String originalText;
    @Column(length = 160) public String addressHint;
    @Column(length = 40) public String unitLabel;
    @Column(length = 1000) public String sourceUrl;
    @Column(length = 255) public String imageName;
    @Column(length = 80) public String imageContentType;
    @Column(length = 80) public String imageStorageKey;
    public Long imageSize;
    @Column(nullable = false) public Instant createdAt = Instant.now();
    public Instant confirmedAt;
    @Column(columnDefinition = "text") public String extractionJson;
    @Column(columnDefinition = "text") public String reviewedJson;
    @Column(length = 255) public String extractionError;
    public Instant extractedAt;
    public Instant reviewedAt;
}
