package com.roomcrosscheck.community.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * SFR-016의 게시글별 댓글을 댓글 테이블에 매핑한다.
 * <p>각 댓글은 하나의 게시글에 연결되며, 작성자 ID와 내용, 작성·수정 시각을 보관한다.
 */
@Entity
@Table(name = "community_comments", indexes = {
    @Index(name = "idx_community_comments_post_created", columnList = "post_id,created_at")
})
public class Comment {
    @Id
    @Column(length = 36, nullable = false, updatable = false)
    public String id = UUID.randomUUID().toString();

    /**
     * 댓글이 속한 게시글.
     * 외래키로 연결된 게시글이 없는 댓글의 저장을 방지하며, 댓글 삭제를 게시글에 전파하지 않는다.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false, updatable = false,
        foreignKey = @ForeignKey(name = "fk_community_comments_post"))
    public Post post;

    /**
     * 댓글 작성자의 사용자 ID.
     * 사용자 테이블과의 외래키 연결은 없으며, JPA 갱신 대상에서 제외한다.
     */
    @Column(name = "author_id", nullable = false, updatable = false, columnDefinition = "bigint unsigned")
    public Long authorId;

    @Column(nullable = false, columnDefinition = "text")
    public String content;

    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;

    /** 동시에 수정된 데이터의 충돌을 감지하기 위한 버전. */
    @Version
    public long version;

    @PrePersist
    protected void initializeTimestamps() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void updateTimestamp() {
        updatedAt = Instant.now();
    }
}
