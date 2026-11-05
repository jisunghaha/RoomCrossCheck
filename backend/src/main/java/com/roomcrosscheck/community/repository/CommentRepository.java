package com.roomcrosscheck.community.repository;

import com.roomcrosscheck.community.domain.Comment;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** 게시글에 속한 댓글의 저장·조회·삭제를 제공한다. */
public interface CommentRepository extends JpaRepository<Comment, String> {

    /**
     * 해당 게시글의 댓글을 오래된 작성 순서로 조회한다.
     * 작성 시각이 같으면 ID로 순서를 구분한다.
     *
     * @param postId 댓글이 속한 게시글 ID
     * @param pageable 조회할 페이지와 페이지 크기
     * @return 댓글 목록과 전체 개수 등 페이지 정보
     */
    Page<Comment> findByPost_IdOrderByCreatedAtAscIdAsc(String postId, Pageable pageable);

    /** 댓글 ID와 게시글 ID를 함께 확인해 다른 게시글의 댓글이 조회되는 것을 방지한다. */
    Optional<Comment> findByIdAndPost_Id(String id, String postId);

    /**
     * 게시글에 속한 댓글을 삭제한다.
     * 권한 검사 후 게시글 삭제와 동일한 Service 트랜잭션 안에서 호출해야 한다.
     *
     * @param postId 삭제 대상 게시글 ID
     * @return 삭제한 댓글 수
     */
    long deleteByPost_Id(String postId);
}
