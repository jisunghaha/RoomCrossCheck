package com.roomcrosscheck.community.controller;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.roomcrosscheck.community.domain.Comment;
import com.roomcrosscheck.community.domain.Post;
import com.roomcrosscheck.community.repository.CommentRepository;
import com.roomcrosscheck.community.repository.PostLikeRepository;
import com.roomcrosscheck.community.repository.PostRepository;
import com.roomcrosscheck.community.service.CommentService;
import com.roomcrosscheck.community.service.CurrentActor;
import com.roomcrosscheck.community.service.PostLikeService;
import com.roomcrosscheck.community.service.PostService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 실제 Service와 모의 Repository로 API 경로·입력 검증·권한 응답을 함께 검증한다. */
class CommunityControllerTest {
    private final PostRepository posts = mock(PostRepository.class);
    private final CommentRepository comments = mock(CommentRepository.class);
    private final PostLikeRepository likes = mock(PostLikeRepository.class);
    private final Post post = new Post();
    private final Comment comment = new Comment();
    private final String body = "{\"title\":\"제목\",\"content\":\"내용\"}";
    private MockMvc mvc;
    private CurrentActor actor;
    private record Actor(Long userId, boolean administrator) implements CurrentActor {}

    @BeforeEach void setup() {
        post.authorId = 15L;
        post.title = "기존 게시글";
        post.content = "게시글 본문";
        post.createdAt = Instant.parse("2026-11-05T00:00:00Z");
        post.updatedAt = post.createdAt;
        comment.post = post;
        comment.authorId = 15L;
        comment.content = "기존 댓글";
        comment.createdAt = post.createdAt;
        comment.updatedAt = post.createdAt;
        when(posts.findById(post.id)).thenReturn(Optional.of(post));
        when(posts.findByIdForUpdate(post.id)).thenReturn(Optional.of(post));
        when(posts.existsById(post.id)).thenReturn(true);
        when(comments.findByIdAndPost_Id(comment.id, post.id)).thenReturn(Optional.of(comment));
        when(posts.findAllByOrderByCreatedAtDescIdDesc(any(Pageable.class)))
            .thenAnswer(call -> new PageImpl<>(List.of(post), call.getArgument(0), 1));
        when(comments.findByPost_IdOrderByCreatedAtAscIdAsc(eq(post.id), any(Pageable.class)))
            .thenAnswer(call -> new PageImpl<>(List.of(comment), call.getArgument(1), 1));
        when(posts.saveAndFlush(any(Post.class))).thenAnswer(call -> call.getArgument(0));
        when(comments.saveAndFlush(any(Comment.class))).thenAnswer(call -> call.getArgument(0));
        mvc = buildMvc(false);
    }

    private MockMvc buildMvc(boolean withProvider) {
        var beans = new DefaultListableBeanFactory();
        if (withProvider) beans.registerSingleton("testActorProvider", (CurrentActor.Provider) () -> actor);
        var controller = new CommunityController(new PostService(posts, comments, likes),
            new CommentService(posts, comments), new PostLikeService(posts, likes),
            beans.getBeanProvider(CurrentActor.Provider.class));
        var mapper = new ObjectMapper().findAndRegisterModules()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        var validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        return MockMvcBuilders.standaloneSetup(controller).setValidator(validator)
            .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper)).build();
    }

    @Test void publicEndpointsWorkWithoutAuthenticationProvider() throws Exception {
        mvc.perform(get("/api/community/posts")).andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].id").value(post.id))
            .andExpect(jsonPath("$.number").value(0)).andExpect(jsonPath("$.totalElements").value(1));
        post.notice = true;
        mvc.perform(get(postPath())).andExpect(status().isOk()).andExpect(jsonPath("$.notice").value(true));
        mvc.perform(get(postPath() + "/comments")).andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].postId").value(post.id));
        mvc.perform(get(postPath() + "/likes")).andExpect(status().isOk())
            .andExpect(jsonPath("$.liked").value(false)).andExpect(jsonPath("$.count").value(0));
        mvc.perform(get("/api/community/session")).andExpect(status().isOk())
            .andExpect(jsonPath("$.authenticated").value(false)).andExpect(jsonPath("$.administrator").value(false));
    }

    @Test void allWritesAre401WithoutProvider() throws Exception {
        for (var request : List.of(json(post("/api/community/posts"), body),
                json(post("/api/community/notices"), body), json(patch(postPath()), body), delete(postPath()),
                json(post(postPath() + "/comments"), "{\"content\":\"댓글\"}"),
                json(patch(commentPath()), "{\"content\":\"댓글\"}"), delete(commentPath()),
                put(postPath() + "/likes"), delete(postPath() + "/likes"))) {
            mvc.perform(request).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("로그인이 필요합니다."));
        }
        verify(posts, never()).saveAndFlush(any());
        verify(comments, never()).saveAndFlush(any());
        verify(likes, never()).saveAndFlush(any());
    }

    @Test void clientSuppliedIdentityDoesNotAuthenticate() throws Exception {
        mvc.perform(json(post("/api/community/posts"),
                "{\"title\":\"제목\",\"content\":\"내용\",\"authorId\":15,\"userId\":15,\"role\":\"ADMIN\"}")
                .header("X-User-Id", "15").header("X-Role", "ADMIN")
                .param("userId", "15").requestAttr("actor", new Actor(15L, true)))
            .andExpect(status().isUnauthorized());
        verify(posts, never()).saveAndFlush(any());
    }

    @Test void memberCreatesPostAndCommentWithServerIdentity() throws Exception {
        actor = new Actor(15L, false);
        mvc = buildMvc(true);
        mvc.perform(json(post("/api/community/posts"),
                "{\"title\":\"제목\",\"content\":\"내용\",\"authorId\":999,\"notice\":true}"))
            .andExpect(status().isCreated()).andExpect(header().exists("Location"))
            .andExpect(jsonPath("$.authorId").value(15)).andExpect(jsonPath("$.notice").value(false));
        mvc.perform(json(post(postPath() + "/comments"), "{\"content\":\"댓글\",\"authorId\":999}"))
            .andExpect(status().isCreated()).andExpect(header().exists("Location"))
            .andExpect(jsonPath("$.authorId").value(15)).andExpect(jsonPath("$.postId").value(post.id));
    }

    @Test void sessionReadsProviderOnEveryRequestAndRejectsIncompleteIdentity() throws Exception {
        actor = new Actor(15L, false);
        mvc = buildMvc(true);
        mvc.perform(get("/api/community/session")).andExpect(jsonPath("$.userId").value(15));
        actor = new Actor(30L, true);
        mvc.perform(get("/api/community/session")).andExpect(jsonPath("$.userId").value(30))
            .andExpect(jsonPath("$.administrator").value(true));
        actor = new Actor(null, true);
        mvc.perform(get("/api/community/session")).andExpect(jsonPath("$.authenticated").value(false))
            .andExpect(jsonPath("$.administrator").value(false));
        mvc.perform(json(post("/api/community/posts"), body)).andExpect(status().isUnauthorized());
    }

    @Test void membersCannotManageOtherAuthorsOrNotices() throws Exception {
        actor = new Actor(20L, false);
        mvc = buildMvc(true);
        mvc.perform(json(patch(postPath()), body)).andExpect(status().isForbidden());
        mvc.perform(delete(postPath())).andExpect(status().isForbidden());
        mvc.perform(json(patch(commentPath()), "{\"content\":\"댓글\"}")).andExpect(status().isForbidden());
        mvc.perform(delete(commentPath())).andExpect(status().isForbidden());
        mvc.perform(json(post("/api/community/notices"), body)).andExpect(status().isForbidden());
        actor = new Actor(15L, false);
        post.notice = true;
        mvc.perform(json(patch(postPath()), body)).andExpect(status().isForbidden());
        mvc.perform(delete(postPath())).andExpect(status().isForbidden());
    }

    @Test void ownerCanUpdateAndDeletePostAndComment() throws Exception {
        actor = new Actor(15L, false);
        mvc = buildMvc(true);
        mvc.perform(json(patch(postPath()), body)).andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("제목"));
        mvc.perform(json(patch(commentPath()), "{\"content\":\"수정 댓글\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.content").value("수정 댓글"));
        mvc.perform(delete(commentPath())).andExpect(status().isNoContent()).andExpect(content().string(""));
        mvc.perform(delete(postPath())).andExpect(status().isNoContent()).andExpect(content().string(""));
    }

    @Test void adminCanCreateNoticeAndManageOtherAuthors() throws Exception {
        actor = new Actor(30L, true);
        mvc = buildMvc(true);
        mvc.perform(json(post("/api/community/notices"), body)).andExpect(status().isCreated())
            .andExpect(jsonPath("$.notice").value(true));
        post.notice = true;
        mvc.perform(json(patch(postPath()), body)).andExpect(status().isOk());
        mvc.perform(json(patch(commentPath()), "{\"content\":\"관리자 수정\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.authorId").value(15));
        mvc.perform(delete(commentPath())).andExpect(status().isNoContent());
        mvc.perform(delete(postPath())).andExpect(status().isNoContent());
    }

    @Test void likeRoutesRegisterAndCancelForAuthenticatedUser() throws Exception {
        actor = new Actor(15L, false);
        mvc = buildMvc(true);
        mvc.perform(put(postPath() + "/likes")).andExpect(status().isOk())
            .andExpect(jsonPath("$.liked").value(true));
        mvc.perform(delete(postPath() + "/likes")).andExpect(status().isOk())
            .andExpect(jsonPath("$.liked").value(false));
        verify(likes).deleteByPost_IdAndUserId(post.id, 15L);
    }

    @Test void missingResourcesAndWrongCommentParentReturn404() throws Exception {
        mvc.perform(get("/api/community/posts/missing")).andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404));
        mvc.perform(get("/api/community/posts/missing/comments")).andExpect(status().isNotFound());
        mvc.perform(get("/api/community/posts/missing/likes")).andExpect(status().isNotFound());
        actor = new Actor(30L, true);
        mvc = buildMvc(true);
        mvc.perform(delete(postPath() + "/comments/missing")).andExpect(status().isNotFound());
        Post another = new Post();
        when(posts.findByIdForUpdate(another.id)).thenReturn(Optional.of(another));
        mvc.perform(delete("/api/community/posts/" + another.id + "/comments/" + comment.id))
            .andExpect(status().isNotFound());
    }

    @Test void invalidRequestsReturnConsistent400() throws Exception {
        actor = new Actor(15L, false);
        mvc = buildMvc(true);
        for (var request : List.of(json(post("/api/community/posts"), "{\"title\":\" \",\"content\":\"내용\"}"),
                json(post(postPath() + "/comments"), "{\"content\":\"\"}"),
                json(post("/api/community/posts"), "{"), get("/api/community/posts?page=bad"),
                get("/api/community/posts?size=101"), get(postPath() + "/comments?page=-1"))) {
            mvc.perform(request).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400)).andExpect(jsonPath("$.message").isNotEmpty());
        }
        verify(posts, never()).saveAndFlush(any());
        verify(comments, never()).saveAndFlush(any());
    }

    @Test void unsupportedContentTypeReturns415() throws Exception {
        mvc.perform(post("/api/community/posts").contentType(MediaType.TEXT_PLAIN).content("text"))
            .andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.status").value(415));
    }

    @Test void conflictsAndLockFailuresAreMappedWithoutLeakingDetails() throws Exception {
        actor = new Actor(15L, false);
        mvc = buildMvc(true);
        when(posts.saveAndFlush(any())).thenThrow(new OptimisticLockingFailureException("internal SQL"));
        mvc.perform(json(patch(postPath()), body)).andExpect(status().isConflict())
            .andExpect(jsonPath("$.status").value(409));
        doThrow(new DataIntegrityViolationException("private constraint")).when(posts).saveAndFlush(any());
        var response = mvc.perform(json(post("/api/community/posts"), body)).andExpect(status().isConflict())
            .andReturn().getResponse().getContentAsString();
        assertFalse(response.contains("private constraint"));
        when(posts.findByIdForUpdate(post.id)).thenThrow(new CannotAcquireLockException("database lock details"));
        mvc.perform(put(postPath() + "/likes")).andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.status").value(503));
    }

    @Test void unexpectedFailureReturnsGeneric500() throws Exception {
        when(posts.existsById(post.id)).thenThrow(new IllegalStateException("private DB information"));
        var response = mvc.perform(get(postPath() + "/likes")).andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.status").value(500)).andReturn().getResponse().getContentAsString();
        assertFalse(response.contains("private DB information"));
    }

    private String postPath() { return "/api/community/posts/" + post.id; }
    private String commentPath() { return postPath() + "/comments/" + comment.id; }
    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).characterEncoding("UTF-8").content(body);
    }
}
