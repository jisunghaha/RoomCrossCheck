package com.roomcrosscheck.community.service;

import com.roomcrosscheck.community.domain.Comment;
import com.roomcrosscheck.community.domain.Post;
import com.roomcrosscheck.community.dto.CommunityDto.CommentRequest;
import com.roomcrosscheck.community.repository.CommentRepository;
import com.roomcrosscheck.community.repository.PostRepository;
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

/** 댓글의 사용자별 권한과 게시글 소속 검사를 검증한다. */
class CommentServiceTest {
    private final PostRepository posts = mock(PostRepository.class);
    private final CommentRepository comments = mock(CommentRepository.class);
    private final CommentService service = new CommentService(posts, comments);
    private final Post post = new Post();
    private final CommentRequest request = new CommentRequest("댓글 내용");
    private record Actor(Long userId, boolean administrator) implements CurrentActor {}
    private final Actor owner = new Actor(15L, false);
    private final Actor other = new Actor(20L, false);
    private final Actor admin = new Actor(30L, true);

    @BeforeEach void setup() {
        when(posts.existsById(post.id)).thenReturn(true);
        when(posts.findByIdForUpdate(post.id)).thenReturn(Optional.of(post));
        when(comments.saveAndFlush(any(Comment.class))).thenAnswer(call -> call.getArgument(0));
    }

    @Test void guestsCanReadComments() {
        Comment comment = existing();
        when(comments.findByPost_IdOrderByCreatedAtAscIdAsc(post.id, PageRequest.of(0, 20)))
            .thenReturn(new PageImpl<>(List.of(comment)));
        var response = service.listComments(post.id, 0, 20).getContent().getFirst();
        assertEquals(post.id, response.postId());
        assertEquals(comment.id, response.id());
        assertEquals(owner.userId(), response.authorId());
    }

    @Test void guestsCannotCreateUpdateOrDelete() {
        for (Actor guest : new Actor[] {null, new Actor(null, true), new Actor(0L, false), new Actor(-1L, true)}) {
            assertStatus(401, () -> service.createComment(post.id, request, guest));
            assertStatus(401, () -> service.updateComment(post.id, "comment", request, guest));
            assertStatus(401, () -> service.deleteComment(post.id, "comment", guest));
        }
        verify(posts, never()).findByIdForUpdate(any());
        verifyNoInteractions(comments);
    }

    @Test void membersCanCommentOnNoticesWithTheirOwnIdentity() {
        post.notice = true;
        var response = service.createComment(post.id, request, owner);
        assertNotNull(response.id());
        assertEquals(owner.userId(), response.authorId());
        assertEquals(post.id, response.postId());
        assertEquals(request.content(), response.content());
    }

    @Test void ownerCanUpdateAndDeleteOwnComment() {
        Comment comment = existing();
        var response = service.updateComment(post.id, comment.id, request, owner);
        assertEquals(request.content(), response.content());
        assertEquals(owner.userId(), response.authorId());
        assertSame(post, comment.post);
        service.deleteComment(post.id, comment.id, owner);
        verify(comments).delete(comment);
        verify(posts, never()).delete(any());
    }

    @Test void otherMembersCannotChangeComment() {
        Comment comment = existing();
        assertStatus(403, () -> service.updateComment(post.id, comment.id, request, other));
        assertStatus(403, () -> service.deleteComment(post.id, comment.id, other));
        assertEquals("기존 댓글", comment.content);
        verify(comments, never()).saveAndFlush(any());
        verify(comments, never()).delete(any());
    }

    @Test void adminsCanManageAnotherAuthorsComment() {
        Comment comment = existing();
        assertEquals(owner.userId(), service.updateComment(post.id, comment.id, request, admin).authorId());
        service.deleteComment(post.id, comment.id, admin);
        verify(comments).delete(comment);
    }

    @Test void missingPostReturns404ForReadsAndWrites() {
        assertStatus(404, () -> service.listComments("missing", 0, 20));
        assertStatus(404, () -> service.createComment("missing", request, owner));
        assertStatus(404, () -> service.updateComment("missing", "comment", request, admin));
        assertStatus(404, () -> service.deleteComment("missing", "comment", admin));
        verifyNoInteractions(comments);
    }

    @Test void missingOrWrongPostCommentReturns404() {
        Comment comment = existing();
        Post another = new Post();
        when(posts.findByIdForUpdate(another.id)).thenReturn(Optional.of(another));
        assertStatus(404, () -> service.updateComment(another.id, comment.id, request, owner));
        assertStatus(404, () -> service.deleteComment(another.id, comment.id, admin));
        assertStatus(404, () -> service.deleteComment(post.id, "missing", owner));
        assertEquals("기존 댓글", comment.content);
        verify(comments, never()).delete(any());
    }

    @Test void invalidContentDoesNotModifyComment() {
        Comment comment = existing();
        for (CommentRequest invalid : new CommentRequest[] {null, new CommentRequest(null),
                new CommentRequest("  "), new CommentRequest("가".repeat(21846))}) {
            assertStatus(400, () -> service.createComment(post.id, invalid, owner));
            assertStatus(400, () -> service.updateComment(post.id, comment.id, invalid, owner));
        }
        assertEquals("기존 댓글", comment.content);
        verify(comments, never()).saveAndFlush(any());
    }

    @Test void invalidPagingReturns400() {
        assertStatus(400, () -> service.listComments(post.id, -1, 20));
        assertStatus(400, () -> service.listComments(post.id, 0, 0));
        assertStatus(400, () -> service.listComments(post.id, 0, 101));
    }

    @Test void databaseFailureIsNotReportedAsSuccess() {
        when(comments.saveAndFlush(any())).thenThrow(new IllegalStateException("DB 오류"));
        assertThrows(IllegalStateException.class, () -> service.createComment(post.id, request, owner));
    }

    private Comment existing() {
        Comment comment = new Comment();
        comment.post = post;
        comment.authorId = owner.userId();
        comment.content = "기존 댓글";
        when(comments.findByIdAndPost_Id(comment.id, post.id)).thenReturn(Optional.of(comment));
        return comment;
    }

    private static void assertStatus(int expected, Runnable action) {
        var error = assertThrows(ResponseStatusException.class, action::run);
        assertEquals(expected, error.getStatusCode().value());
    }
}
