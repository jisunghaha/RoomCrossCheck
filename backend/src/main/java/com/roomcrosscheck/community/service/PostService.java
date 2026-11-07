package com.roomcrosscheck.community.service;

import com.roomcrosscheck.community.domain.Post;
import com.roomcrosscheck.community.dto.CommunityDto.PostRequest;
import com.roomcrosscheck.community.dto.CommunityDto.PostResponse;
import com.roomcrosscheck.community.repository.CommentRepository;
import com.roomcrosscheck.community.repository.PostLikeRepository;
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
 * 커뮤니티 게시글의 조회와 작성자·관리자 권한에 따른 변경을 처리한다.
 * <p>인증 정보가 없는 쓰기 요청은 401, 권한 부족은 403, 존재하지 않는 게시글은 404,
 * 잘못된 입력은 400 상태의 예외를 발생시킨다. 쓰기 요청은 인증 여부를 먼저 검사한다.
 * 데이터베이스 오류는 호출부로 전파하여 쓰기 트랜잭션이 롤백되도록 한다.
 */
@Service
@Transactional(readOnly = true)
public class PostService {
    private final PostRepository posts;
    private final CommentRepository comments;
    private final PostLikeRepository likes;

    public PostService(PostRepository posts, CommentRepository comments, PostLikeRepository likes) {
        this.posts = posts;
        this.comments = comments;
        this.likes = likes;
    }

    /**
     * 현재 저장된 게시글과 공지사항 전체를 최신순으로 페이지 조회한다.
     * 별도의 공개 범위 필터가 없으며 비로그인 사용자도 조회할 수 있다.
     *
     * @param page 0부터 시작하는 페이지 번호
     * @param size 페이지 크기, 1부터 100까지 허용
     * @return 게시글 목록과 페이지 정보
     */
    public Page<PostResponse> listPosts(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "페이지 범위를 확인해 주세요.");
        }
        return posts.findAllByOrderByCreatedAtDescIdDesc(PageRequest.of(page, size)).map(PostResponse::from);
    }

    /** 비로그인 사용자도 게시글과 공지사항의 상세를 조회할 수 있다. */
    public PostResponse getPost(String postId) {
        return PostResponse.from(findPost(postId));
    }

    /** 인증된 사용자의 번호를 작성자로 설정해 일반 게시글을 생성한다. */
    @Transactional
    public PostResponse createPost(PostRequest request, CurrentActor actor) {
        requireAuthenticated(actor);
        return create(request, actor.userId(), false);
    }

    /** 공지사항은 관리자만 생성할 수 있으며 일반 게시글 요청으로 공지 여부를 지정할 수 없다. */
    @Transactional
    public PostResponse createNotice(PostRequest request, CurrentActor actor) {
        requireAuthenticated(actor);
        if (!actor.administrator()) throw forbidden();
        return create(request, actor.userId(), true);
    }

    /**
     * 작성자 또는 관리자가 제목과 내용을 수정한다. 공지사항은 관리자만 수정한다.
     * 작성자와 공지 여부는 유지하고 권한 검사 실패 시 엔티티를 변경하지 않는다.
     */
    @Transactional
    public PostResponse updatePost(String postId, PostRequest request, CurrentActor actor) {
        requireAuthenticated(actor);
        Post post = findPost(postId);
        requireManage(post, actor);
        validate(request);
        post.title = request.title().strip();
        post.content = request.content();
        // 변경 사항을 반영해 @PreUpdate에서 갱신한 수정 시각을 응답에 포함한다.
        return PostResponse.from(posts.saveAndFlush(post));
    }

    /**
     * 작성자 또는 관리자가 게시글을 삭제한다. 공지사항은 관리자만 삭제한다.
     * 댓글·좋아요를 먼저 삭제하고 같은 트랜잭션에서 게시글을 삭제해 부분 삭제를 방지한다.
     */
    @Transactional
    public void deletePost(String postId, CurrentActor actor) {
        requireAuthenticated(actor);
        if (postId == null || postId.isBlank()) throw notFound();
        Post post = posts.findByIdForUpdate(postId).orElseThrow(PostService::notFound);
        requireManage(post, actor);
        comments.deleteByPost_Id(postId);
        likes.deleteByPost_Id(postId);
        // 외래키가 참조하는 게시글보다 자식 행의 삭제가 먼저 DB에 반영되도록 한다.
        posts.flush();
        posts.delete(post);
        posts.flush();
    }

    private PostResponse create(PostRequest request, Long authorId, boolean notice) {
        validate(request);
        Post post = new Post();
        post.authorId = authorId;
        post.title = request.title().strip();
        post.content = request.content();
        post.notice = notice;
        // 저장을 반영해 @PrePersist에서 설정한 작성·수정 시각을 응답에 포함한다.
        return PostResponse.from(posts.saveAndFlush(post));
    }

    private Post findPost(String postId) {
        if (postId == null || postId.isBlank()) throw notFound();
        return posts.findById(postId).orElseThrow(PostService::notFound);
    }

    private static void requireAuthenticated(CurrentActor actor) {
        if (actor == null || actor.userId() == null || actor.userId() <= 0) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }
    }

    private static void requireManage(Post post, CurrentActor actor) {
        if (actor.administrator()) return;
        if (post.notice || !Objects.equals(post.authorId, actor.userId())) throw forbidden();
    }

    private static void validate(PostRequest request) {
        // HTTP 입력 검증을 거치지 않는 호출에서도 잘못된 내용을 저장하지 않는다.
        if (request == null || request.title() == null || request.title().isBlank()
                || request.title().length() > 200 || request.content() == null || request.content().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "제목과 내용을 확인해 주세요.");
        }
        // MySQL TEXT의 한도는 글자 수가 아닌 바이트 수이므로 한글·이모지도 함께 고려한다.
        if (request.content().getBytes(StandardCharsets.UTF_8).length > 65_535) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "게시글 본문이 너무 깁니다.");
        }
    }

    private static ResponseStatusException forbidden() {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, "게시글을 관리할 권한이 없습니다.");
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "게시글을 찾지 못했습니다.");
    }
}
