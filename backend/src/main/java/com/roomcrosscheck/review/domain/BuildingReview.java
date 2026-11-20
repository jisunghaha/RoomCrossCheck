package com.roomcrosscheck.review.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * SFR-017 건물별 거주 후기를 building_reviews 테이블에 매핑한다.
 * <p>같은 {@code building_key}의 후기를 모아 SFR-018 통계를 만든다. 거주 사실은 인증하지 않는다.
 * 점수는 8개 항목별 1~5점이며 모름·해당 없음은 null로 저장해 평균에서 제외한다.
 */
@Entity
@Table(name = "building_reviews", indexes = {
    @Index(name = "idx_building_reviews_building", columnList = "building_key"),
    @Index(name = "idx_building_reviews_author", columnList = "author_id")
})
public class BuildingReview {
    /** 통계와 화면이 함께 쓰는 평가 항목. 점수가 높을수록 양호하다. */
    public static final List<String> CATEGORIES = List.of("방음", "수압", "채광", "환기", "주차", "관리", "벌레", "결로");

    @Id
    @Column(length = 36, nullable = false, updatable = false)
    public String id = UUID.randomUUID().toString();

    @Column(name = "building_key", nullable = false, updatable = false)
    public String buildingKey;

    /** 상세 동·호를 뺀 공개용 건물 주소. */
    @Column(name = "building_label", nullable = false, length = 160)
    public String buildingLabel;

    @Column(name = "author_id", nullable = false, updatable = false, columnDefinition = "bigint unsigned")
    public Long authorId;

    @Column(nullable = false, length = 50)
    public String period;

    @Column(nullable = false, columnDefinition = "text")
    public String body;

    /** 항목 이름 → 1~5점 또는 null. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "json")
    public Map<String, Integer> scores = new LinkedHashMap<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    @PrePersist
    protected void initialize() { createdAt = Instant.now(); }
}
