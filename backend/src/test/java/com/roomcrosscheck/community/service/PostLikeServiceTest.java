package com.roomcrosscheck.community.service;

import com.roomcrosscheck.community.domain.Post;
import com.roomcrosscheck.community.domain.PostLike;
import com.roomcrosscheck.community.repository.PostLikeRepository;
import com.roomcrosscheck.community.repository.PostRepository;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 좋아요의 사용자 격리, 반복 요청과 인증 정책을 검증한다. */
class PostLikeServiceTest {
    private final PostRepository posts = mock(PostRepository.class);
    private final PostLikeRepository likes = mock(PostLikeRepository.class);
    private final PostLikeService service = new PostLikeService(posts, likes);
    private final Post post = new Post();
    private record Actor(Long userId, boolean administrator) implements CurrentActor {}
    private final Actor member = new Actor(15L, false);
    private final Actor admin = new Actor(30L, true);

    @BeforeEach void setup() {
        when(posts.existsById(post.id)).thenReturn(true);
        when(posts.findByIdForUpdate(post.id)).thenReturn(Optional.of(post));
    }

    @Test void guestsCanReadCountWithoutPersonalLikeLookup() {
        when(likes.countByPost_Id(post.id)).thenReturn(3L);
        var response = service.getLikes(post.id, null);
        assertEquals(3, response.count());
        assertFalse(response.liked());
        verify(likes, never()).existsByPost_IdAndUserId(any(), any());
    }

    @Test void membersCanReadTheirLikeStatus() {
        when(likes.existsByPost_IdAndUserId(post.id, member.userId())).thenReturn(true);
        assertTrue(service.getLikes(post.id, member).liked());
    }

    @Test void guestsCannotRegisterOrCancelEvenWithAdminFlag() {
        for (Actor guest : new Actor[] {null, new Actor(null, true), new Actor(0L, true), new Actor(-1L, false)}) {
            assertStatus(401, () -> service.likePost(post.id, guest));
            assertStatus(401, () -> service.unlikePost(post.id, guest));
        }
        verifyNoInteractions(likes);
        verify(posts, never()).findByIdForUpdate(any());
    }

    @Test void registeringUsesServerProvidedIdentity() {
        service.likePost(post.id, member);
        var capture = ArgumentCaptor.forClass(PostLike.class);
        verify(likes).saveAndFlush(capture.capture());
        assertEquals(member.userId(), capture.getValue().userId);
        assertSame(post, capture.getValue().post);
    }

    @Test void repeatedRegistrationAndCancellationRemainIdempotent() {
        Set<Long> users = new HashSet<>();
        when(likes.existsByPost_IdAndUserId(any(), any())).thenAnswer(call -> users.contains(call.getArgument(1)));
        when(likes.saveAndFlush(any(PostLike.class))).thenAnswer(call -> {
            PostLike like = call.getArgument(0);
            assertTrue(users.add(like.userId));
            return like;
        });
        when(likes.countByPost_Id(post.id)).thenAnswer(call -> (long) users.size());
        when(likes.deleteByPost_IdAndUserId(any(), any())).thenAnswer(call -> users.remove(call.getArgument(1)) ? 1L : 0L);

        assertEquals(1, service.likePost(post.id, member).count());
        assertEquals(1, service.likePost(post.id, member).count());
        assertEquals(2, service.likePost(post.id, admin).count());
        var response = service.unlikePost(post.id, member);
        assertFalse(response.liked());
        assertEquals(1, response.count());
        assertEquals(1, service.unlikePost(post.id, member).count());
        assertTrue(service.getLikes(post.id, admin).liked());
        assertFalse(service.getLikes(post.id, member).liked());
        assertEquals(Set.of(admin.userId()), users);
        verify(likes, times(2)).saveAndFlush(any());
    }

    @Test void adminCancelsOnlyTheirOwnLike() {
        service.unlikePost(post.id, admin);
        verify(likes).deleteByPost_IdAndUserId(post.id, admin.userId());
        verify(likes, never()).deleteByPost_Id(post.id);
    }

    @Test void missingPostReturns404WithoutWriting() {
        assertStatus(404, () -> service.getLikes("missing", member));
        assertStatus(404, () -> service.likePost("missing", member));
        assertStatus(404, () -> service.unlikePost("missing", admin));
        verifyNoInteractions(likes);
    }

    @Test void databaseFailurePropagatesInsteadOfPretendingLikeSucceeded() {
        when(likes.saveAndFlush(any())).thenThrow(new IllegalStateException("DB 오류"));
        assertThrows(IllegalStateException.class, () -> service.likePost(post.id, member));
        verify(likes, never()).countByPost_Id(any());
    }

    @Test void cancellationFailurePropagates() {
        when(likes.deleteByPost_IdAndUserId(post.id, member.userId())).thenThrow(new IllegalStateException("DB 오류"));
        assertThrows(IllegalStateException.class, () -> service.unlikePost(post.id, member));
        verify(likes, never()).countByPost_Id(any());
    }

    private static void assertStatus(int expected, Runnable action) {
        var error = assertThrows(ResponseStatusException.class, action::run);
        assertEquals(expected, error.getStatusCode().value());
    }
}
