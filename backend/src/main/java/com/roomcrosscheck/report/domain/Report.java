package com.roomcrosscheck.report.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * SFR-019 게시글·댓글·거주 후기 신고와 관리자 처리 결과를 reports 테이블에 매핑한다.
 * <p>대상이 삭제돼도 관리자가 내용을 확인할 수 있도록 신고 시점의 미리보기와 작성자 번호를 보관한다.
 * 한 회원은 같은 대상을 한 번만 신고할 수 있다.
 */
@Entity
@Table(name = "reports",
    uniqueConstraints = @UniqueConstraint(name = "uk_reports_target_reporter",
        columnNames = {"target_type", "target_id", "reporter_id"}),
    indexes = {
        @Index(name = "idx_reports_status_created", columnList = "status, created_at"),
        @Index(name = "idx_reports_target", columnList = "target_type, target_id")
    })
public class Report {
    public enum TargetType { POST, COMMENT, REVIEW }
    public enum Reason { SPAM, ABUSE, PRIVACY, FALSE_INFO, OTHER }
    /** PENDING 대기, ACCEPTED 대상 삭제로 처리, DISMISSED 문제없음으로 반려. */
    public enum Status { PENDING, ACCEPTED, DISMISSED }

    @Id
    @Column(length = 36, nullable = false, updatable = false)
    public String id = UUID.randomUUID().toString();

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 16, updatable = false)
    public TargetType targetType;

    @Column(name = "target_id", nullable = false, length = 36, updatable = false)
    public String targetId;

    /** 댓글 신고일 때 댓글이 속한 게시글 ID. 그 밖에는 null이다. */
    @Column(name = "post_id", length = 36, updatable = false)
    public String postId;

    /** 신고한 회원 users.id. 신고자가 탈퇴하면 null이 되고 신고 기록은 남는다. */
    @Column(name = "reporter_id", updatable = false, columnDefinition = "bigint unsigned")
    public Long reporterId;

    @Column(name = "target_author_id", updatable = false, columnDefinition = "bigint unsigned")
    public Long targetAuthorId;

    /** 신고 시점 대상 내용의 앞부분, 최대 200자. */
    @Column(name = "target_preview", nullable = false, length = 200, updatable = false)
    public String targetPreview;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16, updatable = false)
    public Reason reason;

    @Column(length = 500, updatable = false)
    public String detail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    public Status status = Status.PENDING;

    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    @Column(name = "handled_at")
    public Instant handledAt;

    @Column(name = "handler_id", columnDefinition = "bigint unsigned")
    public Long handlerId;

    @Column(name = "handler_note", length = 300)
    public String handlerNote;

    @Version
    public long version;

    @PrePersist
    protected void initialize() { createdAt = Instant.now(); }
}
