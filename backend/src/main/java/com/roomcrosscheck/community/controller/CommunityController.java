package com.roomcrosscheck.community.controller;

import com.roomcrosscheck.community.dto.CommunityDto.*;
import com.roomcrosscheck.community.service.CommentService;
import com.roomcrosscheck.community.service.CurrentActor;
import com.roomcrosscheck.community.service.PostLikeService;
import com.roomcrosscheck.community.service.PostService;
import jakarta.validation.Valid;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

/**
 * SFR-015·016의 게시글·댓글·좋아요 API 요청과 응답을 처리한다.
 * <p>권한 판단은 Service에 위임하고 사용자 정보는 서버의 인증 공급자에서만 받는다.
 * 공급자가 없으면 비로그인으로 처리하므로 로그인 연동 전에는 공개 조회만 가능하다.
 * 예외 처리는 이 Controller에 한정하며 다른 기능의 응답에는 영향을 주지 않는다.
 */
@RestController
@RequestMapping("/api/community")
public class CommunityController {
    private static final Logger log = LoggerFactory.getLogger(CommunityController.class);
    private final PostService posts;
    private final CommentService comments;
    private final PostLikeService likes;
    private final ObjectProvider<CurrentActor.Provider> actorProviders;

    public CommunityController(PostService posts, CommentService comments, PostLikeService likes,
            ObjectProvider<CurrentActor.Provider> actorProviders) {
        this.posts = posts;
        this.comments = comments;
        this.likes = likes;
        this.actorProviders = actorProviders;
    }

    /** 화면의 버튼 표시를 위한 정보이며 실제 권한은 각 쓰기 요청에서 다시 검사한다. */
    @GetMapping("/session")
    public SessionResponse session() {
        CurrentActor actor = currentActor();
        boolean authenticated = actor != null && actor.userId() != null && actor.userId() > 0;
        return new SessionResponse(authenticated, authenticated ? actor.userId() : null,
            authenticated && actor.administrator());
    }

    /** 비로그인도 전체 게시글과 공지사항을 최신순으로 조회할 수 있다. */
    @GetMapping("/posts")
    public PageResponse<PostResponse> listPosts(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return PageResponse.from(posts.listPosts(page, size));
    }

    @GetMapping("/posts/{postId}")
    public PostResponse getPost(@PathVariable("postId") String postId) {
        return posts.getPost(postId);
    }

    @PostMapping("/posts")
    public ResponseEntity<PostResponse> createPost(@Valid @RequestBody PostRequest request) {
        PostResponse response = posts.createPost(request, currentActor());
        return ResponseEntity.created(URI.create("/api/community/posts/" + response.id())).body(response);
    }

    /** 공지사항 생성 경로를 구분하고 관리자 여부는 Service에서 확인한다. */
    @PostMapping("/notices")
    public ResponseEntity<PostResponse> createNotice(@Valid @RequestBody PostRequest request) {
        PostResponse response = posts.createNotice(request, currentActor());
        return ResponseEntity.created(URI.create("/api/community/posts/" + response.id())).body(response);
    }

    /** 공지사항도 같은 경로로 수정하며 일반 회원에게는 Service가 변경을 허용하지 않는다. */
    @PatchMapping("/posts/{postId}")
    public PostResponse updatePost(@PathVariable("postId") String postId, @Valid @RequestBody PostRequest request) {
        return posts.updatePost(postId, request, currentActor());
    }

    @DeleteMapping("/posts/{postId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePost(@PathVariable("postId") String postId) {
        posts.deletePost(postId, currentActor());
    }

    @GetMapping("/posts/{postId}/comments")
    public PageResponse<CommentResponse> listComments(@PathVariable("postId") String postId,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return PageResponse.from(comments.listComments(postId, page, size));
    }

    @PostMapping("/posts/{postId}/comments")
    public ResponseEntity<CommentResponse> createComment(@PathVariable("postId") String postId,
            @Valid @RequestBody CommentRequest request) {
        CommentResponse response = comments.createComment(postId, request, currentActor());
        return ResponseEntity.created(URI.create("/api/community/posts/" + postId + "/comments/" + response.id()))
            .body(response);
    }

    @PatchMapping("/posts/{postId}/comments/{commentId}")
    public CommentResponse updateComment(@PathVariable("postId") String postId,
            @PathVariable("commentId") String commentId, @Valid @RequestBody CommentRequest request) {
        return comments.updateComment(postId, commentId, request, currentActor());
    }

    @DeleteMapping("/posts/{postId}/comments/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteComment(@PathVariable("postId") String postId, @PathVariable("commentId") String commentId) {
        comments.deleteComment(postId, commentId, currentActor());
    }

    @GetMapping("/posts/{postId}/likes")
    public LikeResponse getLikes(@PathVariable("postId") String postId) {
        return likes.getLikes(postId, currentActor());
    }

    /** 반복 등록 요청에도 좋아요 상태를 유지하도록 PUT을 사용한다. */
    @PutMapping("/posts/{postId}/likes")
    public LikeResponse likePost(@PathVariable("postId") String postId) {
        return likes.likePost(postId, currentActor());
    }

    @DeleteMapping("/posts/{postId}/likes")
    public LikeResponse unlikePost(@PathVariable("postId") String postId) {
        return likes.unlikePost(postId, currentActor());
    }

    private CurrentActor currentActor() {
        CurrentActor.Provider provider = actorProviders.getIfAvailable();
        return provider == null ? null : provider.current();
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> businessError(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode()).body(new ErrorResponse(error.getStatusCode().value(),
            error.getReason() == null ? "커뮤니티 요청을 처리하지 못했습니다." : error.getReason()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
        MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorResponse> invalidInput(Exception error) {
        return error(HttpStatus.BAD_REQUEST, "요청 형식과 입력 내용을 확인해 주세요.");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> unsupportedMediaType(HttpMediaTypeNotSupportedException error) {
        return error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "요청 본문은 JSON 형식으로 보내 주세요.");
    }

    @ExceptionHandler({OptimisticLockingFailureException.class, DataIntegrityViolationException.class})
    public ResponseEntity<ErrorResponse> conflict(Exception error) {
        return error(HttpStatus.CONFLICT, "데이터가 변경되었거나 요청이 충돌했습니다. 새로 조회한 뒤 다시 시도해 주세요.");
    }

    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> temporarilyUnavailable(PessimisticLockingFailureException error) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, "다른 요청을 처리 중입니다. 잠시 후 다시 시도해 주세요.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> unexpectedError(Exception error) {
        // 본문·인증 토큰·SQL 상세를 기록하지 않고 오류 종류만 남긴다.
        log.error("커뮤니티 API 처리 실패: {}", error.getClass().getName());
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.");
    }

    private static ResponseEntity<ErrorResponse> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(status.value(), message));
    }
}
