package com.roomcrosscheck.community.service;

import com.roomcrosscheck.community.domain.Post;
import com.roomcrosscheck.community.dto.CommunityDto.PostRequest;
import com.roomcrosscheck.community.repository.CommentRepository;
import com.roomcrosscheck.community.repository.PostLikeRepository;
import com.roomcrosscheck.community.repository.PostRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 로그인 구현 없이 게시글의 사용자별 권한과 저장 경계를 검증한다. */
class PostServiceTest {
    private final PostRepository posts = mock(PostRepository.class);
    private final CommentRepository comments = mock(CommentRepository.class);
    private final PostLikeRepository likes = mock(PostLikeRepository.class);
    private final PostService service = new PostService(posts, comments, likes);
    private final PostRequest request = new PostRequest("새 제목", "새 내용");

    /** 테스트에서만 사용하는 인증 정보이며 운영 인증 공급자로 등록하지 않는다. */
    private record Actor(Long userId, boolean administrator) implements CurrentActor {}
    private final Actor owner = new Actor(15L, false);
    private final Actor other = new Actor(20L, false);
    private final Actor admin = new Actor(30L, true);

    @BeforeEach
    void configureSave() {
        when(posts.saveAndFlush(any(Post.class))).thenAnswer(call -> call.getArgument(0));
    }

    @Test void publicListAndDetailDoNotRequireActor() {
        Post post = existing(false);
        when(posts.findAllByOrderByCreatedAtDescIdDesc(PageRequest.of(0, 20)))
            .thenReturn(new PageImpl<>(List.of(post)));
        assertEquals(post.id, service.listPosts(0, 20).getContent().getFirst().id());
        assertEquals(post.content, service.getPost(post.id).content());
        post.notice = true;
        assertTrue(service.getPost(post.id).notice());
    }

    @Test void missingPostsReturn404() {
        assertStatus(404, () -> service.getPost("missing"));
        assertStatus(404, () -> service.updatePost("missing", request, owner));
        assertStatus(404, () -> service.deletePost("missing", admin));
        verifyNoInteractions(comments, likes);
    }

    @Test void guestsCannotWriteEvenWithAnAdminFlag() {
        for (Actor guest : new Actor[] {null, new Actor(null, false), new Actor(null, true), new Actor(0L, true)}) {
            assertStatus(401, () -> service.createPost(request, guest));
            assertStatus(401, () -> service.createNotice(request, guest));
            assertStatus(401, () -> service.updatePost("post", request, guest));
            assertStatus(401, () -> service.deletePost("post", guest));
        }
        verify(posts, never()).saveAndFlush(any());
        verify(posts, never()).findById(any());
        verifyNoInteractions(comments, likes);
    }

    @Test void memberCreatesPostWithServerProvidedAuthor() {
        var response = service.createPost(new PostRequest("  제목  ", "내용"), owner);
        assertEquals(15L, response.authorId());
        assertEquals("제목", response.title());
        assertFalse(response.notice());
        assertNotNull(response.id());
    }

    @Test void onlyAdminCanCreateNotice() {
        assertStatus(403, () -> service.createNotice(request, owner));
        verify(posts, never()).saveAndFlush(any());
        var response = service.createNotice(request, admin);
        assertTrue(response.notice());
        assertEquals(admin.userId(), response.authorId());
    }

    @Test void ownerCanUpdateWithoutChangingOwnership() {
        Post post = existing(false);
        var response = service.updatePost(post.id, request, owner);
        assertEquals(request.title(), response.title());
        assertEquals(owner.userId(), response.authorId());
        assertEquals(post.createdAt, response.createdAt());
        assertFalse(response.notice());
    }

    @Test void otherMemberCannotUpdateOrDelete() {
        Post post = existing(false);
        assertStatus(403, () -> service.updatePost(post.id, request, other));
        assertStatus(403, () -> service.deletePost(post.id, other));
        assertEquals("기존 제목", post.title);
        verify(posts, never()).saveAndFlush(any());
        verify(posts, never()).delete(any());
        verifyNoInteractions(comments, likes);
    }

    @Test void adminCanManageAnotherAuthorsPost() {
        Post post = existing(false);
        assertEquals(owner.userId(), service.updatePost(post.id, request, admin).authorId());
        service.deletePost(post.id, admin);
        verify(posts).delete(post);
    }

    @Test void noticeOwnerWithoutAdminRoleCannotManageNotice() {
        Post post = existing(true);
        assertStatus(403, () -> service.updatePost(post.id, request, owner));
        assertStatus(403, () -> service.deletePost(post.id, owner));
        verify(posts, never()).saveAndFlush(any());
        verifyNoInteractions(comments, likes);
    }

    @Test void adminCanUpdateAndDeleteNotice() {
        Post post = existing(true);
        assertTrue(service.updatePost(post.id, request, admin).notice());
        service.deletePost(post.id, admin);
        verify(posts).delete(post);
    }

    @Test void ownerDeletionRemovesChildrenBeforePost() {
        Post post = existing(false);
        service.deletePost(post.id, owner);
        var ordered = inOrder(comments, likes, posts);
        ordered.verify(posts).findByIdForUpdate(post.id);
        ordered.verify(comments).deleteByPost_Id(post.id);
        ordered.verify(likes).deleteByPost_Id(post.id);
        ordered.verify(posts).flush();
        ordered.verify(posts).delete(post);
        ordered.verify(posts).flush();
    }

    @Test void childDeletionFailureStopsPostDeletion() {
        Post post = existing(false);
        when(likes.deleteByPost_Id(post.id)).thenThrow(new IllegalStateException("DB 오류"));
        assertThrows(IllegalStateException.class, () -> service.deletePost(post.id, owner));
        verify(posts, never()).delete(any());
    }

    @Test void invalidInputDoesNotModifyExistingPost() {
        Post post = existing(false);
        for (PostRequest invalid : new PostRequest[] {null, new PostRequest(" ", "내용"),
                new PostRequest("가".repeat(201), "내용"), new PostRequest("제목", " "),
                new PostRequest("제목", "가".repeat(21846))}) {
            assertStatus(400, () -> service.createPost(invalid, owner));
            assertStatus(400, () -> service.updatePost(post.id, invalid, owner));
        }
        assertEquals("기존 제목", post.title);
        verify(posts, never()).saveAndFlush(any());
    }

    @Test void invalidPagingReturns400() {
        assertStatus(400, () -> service.listPosts(-1, 20));
        assertStatus(400, () -> service.listPosts(0, 0));
        assertStatus(400, () -> service.listPosts(0, 101));
    }

    private Post existing(boolean notice) {
        Post post = new Post();
        post.authorId = owner.userId();
        post.title = "기존 제목";
        post.content = "기존 내용";
        post.notice = notice;
        post.createdAt = Instant.parse("2026-11-04T00:00:00Z");
        when(posts.findById(post.id)).thenReturn(Optional.of(post));
        when(posts.findByIdForUpdate(post.id)).thenReturn(Optional.of(post));
        return post;
    }

    private static void assertStatus(int expected, Runnable action) {
        var error = assertThrows(ResponseStatusException.class, action::run);
        assertEquals(expected, error.getStatusCode().value());
    }
}
