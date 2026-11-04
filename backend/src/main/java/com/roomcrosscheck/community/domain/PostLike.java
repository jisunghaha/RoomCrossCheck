package com.roomcrosscheck.community.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * SFR-016의 사용자별 게시글 좋아요를 좋아요 테이블에 매핑한다.
 * <p>게시글 ID와 사용자 ID의 유일 제약을 정의해 사용자별 게시글당 좋아요를 하나로 제한한다.
 */
@Entity
@Table(name = "community_post_likes", uniqueConstraints = {
    @UniqueConstraint(name = "uk_community_post_likes_post_user", columnNames = {"post_id", "user_id"})
})
public class PostLike {
    @Id
    @Column(length = 36, nullable = false, updatable = false)
    public String id = UUID.randomUUID().toString();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false, updatable = false,
        foreignKey = @ForeignKey(name = "fk_community_post_likes_post"))
    public Post post;

    /**
     * 좋아요를 등록한 사용자의 ID.
     * 사용자 테이블과의 외래키 연결은 없으며, JPA 갱신 대상에서 제외한다.
     */
    @Column(name = "user_id", nullable = false, updatable = false, length = 255)
    public String userId;

    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    @PrePersist
    protected void initializeTimestamp() {
        createdAt = Instant.now();
    }
}
