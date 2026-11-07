package com.roomcrosscheck.community.service;

import com.roomcrosscheck.community.domain.Comment;
import com.roomcrosscheck.community.domain.Post;
import com.roomcrosscheck.community.dto.CommunityDto.CommentRequest;
import com.roomcrosscheck.community.dto.CommunityDto.CommentResponse;
import com.roomcrosscheck.community.repository.CommentRepository;
import com.roomcrosscheck.community.repository.PostRepository;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * 게시글별 댓글 조회와 작성자·관리자 권한에 따른 변경을 처리한다.
 * <p>인증되지 않은 쓰기는 401, 권한 부족은 403, 없는 게시글·댓글은 404,
 * 잘못된 내용·페이지 범위는 400 상태의 예외를 발생시킨다. 쓰기는 인증을 먼저 확인한다.
 */
@Service
@Transactional(readOnly = true)
public class CommentService {
    private final PostRepository posts;
    private final CommentRepository comments;

    public CommentService(PostRepository posts, CommentRepository comments) {
        this.posts = posts;
        this.comments = comments;
    }

    /**
     * 비로그인 사용자도 댓글을 오래된 작성 순서로 페이지 조회할 수 있다.
     *
     * @param postId 댓글이 속한 게시글 ID
     * @param page 0부터 시작하는 페이지 번호
     * @param size 페이지 크기, 1부터 100까지 허용
     * @return 해당 게시글의 댓글 목록과 페이지 정보
     */
    public Page<CommentResponse> listComments(String postId, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "페이지 범위를 확인해 주세요.");
        }
        if (postId == null || postId.isBlank() || !posts.existsById(postId)) throw postNotFound();
        return comments.findByPost_IdOrderByCreatedAtAscIdAsc(postId, PageRequest.of(page, size))
            .map(comment -> CommentResponse.from(postId, comment));
    }

    /** 서버가 확인한 사용자를 작성자로 설정하며 공지사항에도 댓글을 작성할 수 있다. */
    @Transactional
    public CommentResponse createComment(String postId, CommentRequest request, CurrentActor actor) {
        requireAuthenticated(actor);
        validate(request);
        Post post = lockPost(postId);
        Comment comment = new Comment();
        comment.post = post;
        comment.authorId = actor.userId();
        comment.content = request.content();
        return CommentResponse.from(postId, comments.saveAndFlush(comment));
    }

    /** 댓글 작성자 또는 관리자만 내용을 수정하며 작성자와 소속 게시글은 유지한다. */
    @Transactional
    public CommentResponse updateComment(String postId, String commentId, CommentRequest request, CurrentActor actor) {
        requireAuthenticated(actor);
        lockPost(postId);
        Comment comment = findComment(postId, commentId);
        requireManage(comment, actor);
        validate(request);
        comment.content = request.content();
        // 수정 콜백에서 갱신한 시각을 응답에 포함한다.
        return CommentResponse.from(postId, comments.saveAndFlush(comment));
    }

    /** 댓글 작성자 또는 관리자만 삭제할 수 있으며 연결된 게시글은 삭제하지 않는다. */
    @Transactional
    public void deleteComment(String postId, String commentId, CurrentActor actor) {
        requireAuthenticated(actor);
        lockPost(postId);
        Comment comment = findComment(postId, commentId);
        requireManage(comment, actor);
        comments.delete(comment);
        comments.flush();
    }

    private Post lockPost(String postId) {
        if (postId == null || postId.isBlank()) throw postNotFound();
        return posts.findByIdForUpdate(postId).orElseThrow(CommentService::postNotFound);
    }

    private Comment findComment(String postId, String commentId) {
        if (commentId == null || commentId.isBlank()) throw commentNotFound();
        // 댓글 번호만으로 조회하면 다른 게시글의 댓글을 변경할 수 있으므로 소속도 확인한다.
        return comments.findByIdAndPost_Id(commentId, postId).orElseThrow(CommentService::commentNotFound);
    }

    private static void requireAuthenticated(CurrentActor actor) {
        if (actor == null || actor.userId() == null || actor.userId() <= 0) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }
    }

    private static void requireManage(Comment comment, CurrentActor actor) {
        if (!actor.administrator() && !Objects.equals(comment.authorId, actor.userId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "댓글을 관리할 권한이 없습니다.");
        }
    }

    private static void validate(CommentRequest request) {
        if (request == null || request.content() == null || request.content().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "댓글 내용을 입력해 주세요.");
        }
        // MySQL TEXT의 바이트 한도를 적용해 한글·이모지가 포함된 내용도 검사한다.
        if (request.content().getBytes(StandardCharsets.UTF_8).length > 65_535) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "댓글 내용이 너무 깁니다.");
        }
    }

    private static ResponseStatusException postNotFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "게시글을 찾지 못했습니다.");
    }

    private static ResponseStatusException commentNotFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "댓글을 찾지 못했습니다.");
    }
}
