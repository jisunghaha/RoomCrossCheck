package com.roomcrosscheck.community.repository;

import com.roomcrosscheck.community.domain.PostLike;
import org.springframework.data.jpa.repository.JpaRepository;

/** 게시글 좋아요의 저장·조회·삭제와 개수 집계를 제공한다. */
public interface PostLikeRepository extends JpaRepository<PostLike, String> {

    /** 좋아요 여부 조회이며, 동시 등록의 중복 방지는 DB의 유일 제약이 담당한다. */
    boolean existsByPost_IdAndUserId(String postId, Long userId);

    long countByPost_Id(String postId);

    /**
     * 특정 사용자의 게시글 좋아요를 삭제한다.
     * Service 트랜잭션 안에서 인증된 사용자 ID로 호출해야 한다.
     *
     * @param postId 좋아요를 취소할 게시글 ID
     * @param userId 서버가 확인한 현재 사용자 ID
     * @return 삭제한 좋아요 수. 기존 좋아요가 없으면 0
     */
    long deleteByPost_IdAndUserId(String postId, Long userId);

    /**
     * 게시글에 속한 좋아요를 삭제한다.
     * 권한 검사 후 게시글 삭제와 동일한 Service 트랜잭션 안에서 호출해야 한다.
     *
     * @param postId 삭제 대상 게시글 ID
     * @return 삭제한 좋아요 수
     */
    long deleteByPost_Id(String postId);
}
