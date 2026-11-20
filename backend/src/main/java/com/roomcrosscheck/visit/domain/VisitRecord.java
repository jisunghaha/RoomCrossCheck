package com.roomcrosscheck.visit.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * SFR-010 매물 한 곳의 개인 방문 기록을 visit_records 테이블에 매핑한다.
 * <p>매물 ID를 기본키로 써서 매물당 한 건만 유지한다. 체크 결과의 키는 회원 체크리스트 항목 ID이며,
 * 항목을 지워도 과거 체크 값은 남겨 두고 화면에서 현재 항목만 표시한다. 공개 후기와 공유하지 않는다.
 */
@Entity
@Table(name = "visit_records", indexes = @Index(name = "idx_visit_records_owner", columnList = "owner_id"))
public class VisitRecord {
    @Id
    @Column(name = "listing_id", length = 36, nullable = false, updatable = false)
    public String listingId;

    /** 기록을 남긴 회원의 users.id. 매물 소유자와 같다. */
    @Column(name = "owner_id", nullable = false, updatable = false, columnDefinition = "bigint unsigned")
    public Long ownerId;

    /** 체크리스트 항목 ID별 확인 여부. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "json")
    public Map<String, Boolean> checks = new LinkedHashMap<>();

    @Column(length = 2000)
    public String note;

    /** 실제 또는 예정 방문일. 입력하지 않으면 null이다. */
    @Column(name = "visited_on")
    public LocalDate visitedOn;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;

    @Version
    public long version;

    protected VisitRecord() {}

    public VisitRecord(String listingId, Long ownerId) {
        this.listingId = listingId;
        this.ownerId = ownerId;
    }

    @PrePersist @PreUpdate
    protected void touch() { updatedAt = Instant.now(); }
}
