package com.roomcrosscheck.community.service;

import com.roomcrosscheck.community.domain.Post;
import com.roomcrosscheck.community.domain.PostLike;
import com.roomcrosscheck.community.dto.CommunityDto.LikeResponse;
import com.roomcrosscheck.community.repository.PostLikeRepository;
import com.roomcrosscheck.community.repository.PostRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * 게시글 좋아요 조회와 인증된 사용자의 등록·취소를 처리한다.
 * <p>인증되지 않은 쓰기는 401, 없는 게시글은 404 상태의 예외를 발생시킨다.
 * 관리자도 자신의 좋아요만 등록·취소하며 사용자 번호는 서버 인증 정보에서 받는다.
 */
@Service
@Transactional(readOnly = true)
public class PostLikeService {
    private final PostRepository posts;
    private final PostLikeRepository likes;

    public PostLikeService(PostRepository posts, PostLikeRepository likes) {
        this.posts = posts;
        this.likes = likes;
    }

    /** 비로그인도 전체 개수를 조회할 수 있으며 본인의 좋아요 여부는 false로 반환한다. */
    public LikeResponse getLikes(String postId, CurrentActor actor) {
        if (postId == null || postId.isBlank() || !posts.existsById(postId)) throw notFound();
        boolean liked = authenticated(actor) && likes.existsByPost_IdAndUserId(postId, actor.userId());
        return new LikeResponse(postId, likes.countByPost_Id(postId), liked);
    }

    /**
     * 현재 사용자의 좋아요를 등록한다. 이미 등록되어 있으면 기존 상태를 유지한다.
     * 게시글 행 잠금 후 존재 여부를 검사해 동시 등록·취소를 직렬화하고,
     * DB의 게시글·사용자 유일 제약을 중복 저장의 최종 방어로 유지한다.
     */
    @Transactional
    public LikeResponse likePost(String postId, CurrentActor actor) {
        requireAuthenticated(actor);
        Post post = lockPost(postId);
        if (!likes.existsByPost_IdAndUserId(postId, actor.userId())) {
            PostLike like = new PostLike();
            like.post = post;
            like.userId = actor.userId();
            likes.saveAndFlush(like);
        }
        return new LikeResponse(postId, likes.countByPost_Id(postId), true);
    }

    /** 현재 사용자의 좋아요만 취소한다. 기존 좋아요가 없어도 취소 상태를 반환한다. */
    @Transactional
    public LikeResponse unlikePost(String postId, CurrentActor actor) {
        requireAuthenticated(actor);
        lockPost(postId);
        likes.deleteByPost_IdAndUserId(postId, actor.userId());
        // 삭제를 DB에 반영한 후 남은 개수를 조회한다.
        likes.flush();
        return new LikeResponse(postId, likes.countByPost_Id(postId), false);
    }

    private Post lockPost(String postId) {
        if (postId == null || postId.isBlank()) throw notFound();
        return posts.findByIdForUpdate(postId).orElseThrow(PostLikeService::notFound);
    }

    private static boolean authenticated(CurrentActor actor) {
        return actor != null && actor.userId() != null && actor.userId() > 0;
    }

    private static void requireAuthenticated(CurrentActor actor) {
        if (!authenticated(actor)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "게시글을 찾지 못했습니다.");
    }
}
