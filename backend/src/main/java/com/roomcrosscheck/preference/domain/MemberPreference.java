package com.roomcrosscheck.preference.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 회원별 화면 구성 값을 member_preferences 테이블에 매핑한다.
 * <p>SFR-009 방문 체크리스트 항목 구성과 SFR-011 비교 대상 매물 선택을 보관한다.
 * 체크리스트가 null이면 화면의 기본 항목을 사용한다는 뜻이다.
 */
@Entity
@Table(name = "member_preferences")
public class MemberPreference {
    /** users.id와 같은 값을 기본키로 쓴다. */
    @Id
    @Column(name = "user_id", nullable = false, updatable = false, columnDefinition = "bigint unsigned")
    public Long userId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    public List<ChecklistItem> checklist;

    /** 비교 화면에서 선택한 매물 ID, 선택 순서대로 최대 3개. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "comparison_listing_ids", columnDefinition = "json")
    public List<String> comparisonListingIds;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;

    @Version
    public long version;

    protected MemberPreference() {}

    public MemberPreference(Long userId) { this.userId = userId; }

    @PrePersist @PreUpdate
    protected void touch() { updatedAt = Instant.now(); }

    /**
     * 방문 체크리스트 항목.
     *
     * @param id 체크 결과를 연결하는 고정 ID
     * @param label 화면에 표시할 항목 이름
     * @param tip 확인 방법 안내, 없으면 빈 문자열
     */
    public record ChecklistItem(String id, String label, String tip) {}
}
