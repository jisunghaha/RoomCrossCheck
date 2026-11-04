package com.roomcrosscheck.community.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * SFR-015의 게시글과 공지사항을 게시글 테이블에 매핑한다.
 * <p>작성자 ID, 제목, 내용, 공지 여부와 작성·수정 시각을 보관한다.
 */
@Entity
@Table(name = "community_posts", indexes = {
    @Index(name = "idx_community_posts_created", columnList = "created_at"),
    @Index(name = "idx_community_posts_author", columnList = "author_id")
})
public class Post {
    @Id
    @Column(length = 36, nullable = false, updatable = false)
    public String id = UUID.randomUUID().toString();

    /**
     * 게시글 작성자의 사용자 ID.
     * 사용자 테이블과의 외래키 연결은 없으며, JPA 갱신 대상에서 제외한다.
     */
    @Column(name = "author_id", nullable = false, updatable = false, length = 255)
    public String authorId;

    @Column(nullable = false, length = 200)
    public String title;

    @Column(nullable = false, columnDefinition = "text")
    public String content;

    /** 공지사항이면 true, 일반 게시글이면 false. */
    @Column(nullable = false)
    public boolean notice;

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
