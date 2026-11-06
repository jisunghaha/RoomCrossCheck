package com.roomcrosscheck.listing.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "advertisements", uniqueConstraints = @UniqueConstraint(columnNames = "idempotency_key"))
public class Advertisement {
    public enum Status { DRAFT, EXTRACTION_PENDING, EXTRACTION_READY, EXTRACTION_FAILED, REVIEWED }

    @Id public String id = UUID.randomUUID().toString();
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
