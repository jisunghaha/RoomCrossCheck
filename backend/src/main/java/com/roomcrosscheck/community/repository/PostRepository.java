package com.roomcrosscheck.community.repository;

import com.roomcrosscheck.community.domain.Post;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 게시글의 저장·조회·삭제와 목록 페이징을 제공한다. */
public interface PostRepository extends JpaRepository<Post, String> {

    /**
     * 게시글을 최신 작성 순서로 조회한다.
     * 작성 시각이 같으면 ID로 순서를 구분한다.
     *
     * @param pageable 조회할 페이지와 페이지 크기
     * @return 게시글 목록과 전체 개수 등 페이지 정보
     */
    Page<Post> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);

    /**
     * 게시글 삭제와 댓글·좋아요 변경을 게시글 단위로 직렬화한다.
     * 쓰기 트랜잭션 안에서 자식 데이터에 접근하기 전에 호출해야 한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Post p where p.id = :id")
    Optional<Post> findByIdForUpdate(@Param("id") String id);
}
