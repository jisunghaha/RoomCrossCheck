package com.roomcrosscheck.community.dto;

import com.roomcrosscheck.community.domain.Post;
import com.roomcrosscheck.community.domain.Comment;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 커뮤니티 API에서 사용하는 데이터 전달 타입을 모아 둔다. */
public final class CommunityDto {
    private CommunityDto() {}

    /**
     * 게시글의 제목과 내용을 전달한다.
     * 작성자 ID와 권한은 요청 데이터에 포함하지 않는다.
     *
     * @param title 게시글 제목, 최대 200자
     * @param content 게시글 본문
     */
    public record PostRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank String content
    ) {}

    /**
     * 댓글 내용을 전달한다.
     * 작성자 ID는 요청 데이터에 포함하지 않는다.
     *
     * @param content 댓글 본문
     */
    public record CommentRequest(
        @NotBlank String content
    ) {}

    /** 엔티티 자체를 노출하지 않고 게시글 화면에 필요한 정보를 전달한다. */
    public record PostResponse(
        String id, Long authorId, String title, String content,
        boolean notice, Instant createdAt, Instant updatedAt
    ) {
        public static PostResponse from(Post post) {
            return new PostResponse(post.id, post.authorId, post.title, post.content,
                post.notice, post.createdAt, post.updatedAt);
        }
    }

    /** 댓글의 게시글·작성자 번호와 내용을 전달하며 지연 로딩된 엔티티를 노출하지 않는다. */
    public record CommentResponse(
        String id, String postId, Long authorId, String content, Instant createdAt, Instant updatedAt
    ) {
        public static CommentResponse from(String postId, Comment comment) {
            return new CommentResponse(comment.id, postId, comment.authorId, comment.content,
                comment.createdAt, comment.updatedAt);
        }
    }

    /** 전체 좋아요 수와 현재 사용자의 등록 여부를 전달한다. 비로그인의 등록 여부는 false다. */
    public record LikeResponse(String postId, long count, boolean liked) {}

    /** 현재 요청의 인증 상태를 전달한다. 비로그인의 사용자 ID는 null이고 관리자 여부는 false다. */
    public record SessionResponse(boolean authenticated, Long userId, boolean administrator) {}

    /** Spring 내부 페이지 구현체에 의존하지 않는 목록 응답 형식이다. 페이지 번호는 0부터 시작한다. */
    public record PageResponse<T>(
        List<T> content, int number, int size, long totalElements, int totalPages, boolean first, boolean last
    ) {
        public static <T> PageResponse<T> from(Page<T> page) {
            return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.isFirst(), page.isLast());
        }
    }

    /** 커뮤니티 API의 오류 상태와 사용자에게 표시할 메시지다. */
    public record ErrorResponse(int status, String message) {}
}
