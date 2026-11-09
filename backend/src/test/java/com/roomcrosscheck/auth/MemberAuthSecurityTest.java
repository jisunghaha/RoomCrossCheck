package com.roomcrosscheck.auth;

import com.roomcrosscheck.auth.config.SecurityConfiguration;
import com.roomcrosscheck.auth.controller.AuthController;
import com.roomcrosscheck.auth.domain.User;
import com.roomcrosscheck.auth.domain.UserRole;
import com.roomcrosscheck.auth.repository.UserRepository;
import com.roomcrosscheck.auth.service.*;
import com.roomcrosscheck.community.controller.CommunityController;
import com.roomcrosscheck.community.domain.Comment;
import com.roomcrosscheck.community.domain.Post;
import com.roomcrosscheck.community.repository.*;
import com.roomcrosscheck.community.service.*;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 실제 필터·서비스·검증기로 일반 회원 인증과 커뮤니티 소유권 연동을 검증한다. DB만 대역을 사용한다. */
@SpringBootTest(classes = MemberAuthSecurityTest.TestApp.class)
@AutoConfigureMockMvc
class MemberAuthSecurityTest {
    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class})
    @Import({SecurityConfiguration.class, AuthController.class,
        AuthService.class, MemberUserDetailsService.class, CommunityController.class,
        PostService.class, CommentService.class, PostLikeService.class})
    static class TestApp {
        @Bean UserRepository users() { return mock(UserRepository.class); }
        @Bean PostRepository posts() { return mock(PostRepository.class); }
        @Bean CommentRepository comments() { return mock(CommentRepository.class); }
        @Bean PostLikeRepository likes() { return mock(PostLikeRepository.class); }
    }

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PostRepository posts;
    @Autowired CommentRepository comments;
    @Autowired PostLikeRepository likes;
    @Autowired PasswordEncoder passwords;
    private final Map<Long, User> stored = new HashMap<>();
    private Post post;
    private Comment comment;

    @BeforeEach void setup() {
        reset(users);
        reset(posts);
        reset(comments);
        reset(likes);
        stored.clear();
        addUser(1L, "member1", UserRole.USER);
        addUser(2L, "member2", UserRole.USER);
        addUser(3L, "manager", UserRole.ADMIN);
        when(users.findByLoginId(anyString())).thenAnswer(call -> stored.values().stream()
            .filter(user -> user.getLoginId().equals(call.getArgument(0))).findFirst());
        when(users.findById(anyLong())).thenAnswer(call -> Optional.ofNullable(stored.get(call.<Long>getArgument(0))));
        when(users.existsByLoginId(anyString())).thenAnswer(call -> stored.values().stream()
            .anyMatch(user -> user.getLoginId().equals(call.getArgument(0))));
        when(users.existsByEmail(anyString())).thenAnswer(call -> stored.values().stream()
            .anyMatch(user -> user.getEmail().equals(call.getArgument(0))));
        when(users.saveAndFlush(any(User.class))).thenAnswer(call -> {
            User user = call.getArgument(0);
            long id = stored.size() + 1L;
            ReflectionTestUtils.setField(user, "id", id);
            stored.put(id, user);
            return user;
        });
        post = new Post(); post.authorId = 1L; post.title = "회원 글"; post.content = "본문";
        post.createdAt = Instant.now(); post.updatedAt = post.createdAt;
        when(posts.findById(post.id)).thenReturn(Optional.of(post));
        when(posts.saveAndFlush(any(Post.class))).thenAnswer(call -> call.getArgument(0));
        when(posts.findByIdForUpdate(post.id)).thenReturn(Optional.of(post));
        comment = new Comment(); comment.post = post; comment.authorId = 1L; comment.content = "댓글";
        comment.createdAt = Instant.now(); comment.updatedAt = comment.createdAt;
        when(comments.findByIdAndPost_Id(comment.id, post.id)).thenReturn(Optional.of(comment));
        when(comments.saveAndFlush(any(Comment.class))).thenAnswer(call -> call.getArgument(0));
    }

    private void addUser(Long id, String loginId, UserRole role) {
        User user = new User(loginId, passwords.encode("password-1234"), loginId, loginId + "@example.com");
        ReflectionTestUtils.setField(user, "id", id);
        ReflectionTestUtils.setField(user, "role", role);
        stored.put(id, user);
    }

    private MockHttpServletRequestBuilder secure(MockHttpServletRequestBuilder request, MockHttpSession session) throws Exception {
        var tokenRequest = get("/api/auth/csrf");
        if (session != null) tokenRequest.session(session);
        Cookie cookie = mvc.perform(tokenRequest).andExpect(status().isOk()).andReturn().getResponse().getCookie("XSRF-TOKEN");
        assertNotNull(cookie);
        request.cookie(cookie).header("X-XSRF-TOKEN", cookie.getValue());
        if (session != null) request.session(session);
        return request;
    }

    private MockHttpSession login(String username) throws Exception {
        return (MockHttpSession) mvc.perform(secure(post("/api/auth/login").param("username", username)
            .param("password", "password-1234"), null)).andExpect(status().isOk())
            .andReturn().getRequest().getSession(false);
    }

    private String postBody() { return "{\"title\":\"새 글\",\"content\":\"본문\"}"; }

    @Test void memberAuthenticationIsAvailableWithoutAProfileAndHeadersCannotAuthenticate() throws Exception {
        mvc.perform(get("/api/auth/config")).andExpect(status().isOk())
            .andExpect(jsonPath("$.memberEnabled").value(true))
            .andExpect(jsonPath("$.demoEnabled").doesNotExist());
        mvc.perform(get("/api/community/session").header("X-User-Id", "3").header("X-Role", "ADMIN"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.authenticated").value(false));
        mvc.perform(post("/api/community/posts").contentType("application/json").content(postBody()))
            .andExpect(status().isUnauthorized());
    }

    @Test void signupNormalizesInputsIgnoresPrivilegeInjectionAndDoesNotLogin() throws Exception {
        mvc.perform(secure(post("/api/auth/signup").contentType("application/json").content(
            """
            {"loginId":" NEW.USER ","password":"password-1234","nickname":" 새회원 ",
             "email":" NEW@EXAMPLE.COM ","id":999,"role":"ADMIN"}
            """), null))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(4))
            .andExpect(jsonPath("$.loginId").value("new.user")).andExpect(jsonPath("$.nickname").value("새회원"))
            .andExpect(jsonPath("$.email").value("new@example.com")).andExpect(jsonPath("$.role").value("USER"))
            .andExpect(jsonPath("$.passwordHash").doesNotExist()).andExpect(jsonPath("$.password").doesNotExist());
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        assertTrue(passwords.matches("password-1234", stored.get(4L).getPasswordHash()));
        MockHttpSession session = login("NEW.USER");
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(4));
    }

    @Test void signupRejectsMalformedInputDuplicatesAndMissingCsrf() throws Exception {
        String valid = """
            {"loginId":"member1","password":"password-1234","nickname":"회원","email":"member1@example.com"}
            """;
        mvc.perform(post("/api/auth/signup").contentType("application/json").content(valid)).andExpect(status().isForbidden());
        mvc.perform(secure(post("/api/auth/signup").contentType("application/json").content(valid), null))
            .andExpect(status().isConflict());
        for (String invalid : new String[]{"{}", "{", valid.replace("password-1234", "short"),
                valid.replace("member1@example.com", "invalid"), valid.replace("password-1234", "가".repeat(25))}) {
            mvc.perform(secure(post("/api/auth/signup").contentType("application/json").content(invalid), null))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        }
        verify(users, never()).saveAndFlush(any());
    }

    @Test void loginValidatesPasswordRotatesSessionAndErasesCredentials() throws Exception {
        for (String username : new String[]{"member1", "unknown", "admin"}) {
            mvc.perform(secure(post("/api/auth/login").param("username", username).param("password", "wrong-password"), null))
                .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/auth/login").param("username", "member1").param("password", "password-1234"))
            .andExpect(status().isForbidden());
        MockHttpSession before = new MockHttpSession(); String oldId = before.getId();
        MockHttpSession session = (MockHttpSession) mvc.perform(secure(post("/api/auth/login")
            .param("username", " MEMBER1 ").param("password", "password-1234"), before))
            .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
        assertNotNull(session);
        assertNotEquals(oldId, session.getId());
        assertEquals(1200, session.getMaxInactiveInterval());
        SecurityContext context = (SecurityContext) session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        assertNull(((MemberPrincipal) context.getAuthentication().getPrincipal()).getPassword());
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1)).andExpect(jsonPath("$.role").value("USER"))
            .andExpect(jsonPath("$.passwordHash").doesNotExist());
        mvc.perform(get("/api/community/session").session(session)).andExpect(jsonPath("$.userId").value(1));
    }

    @Test void memberCannotForgeAuthorOrModifyOthersAndOwnerCanEdit() throws Exception {
        mvc.perform(get("/api/community/posts/" + post.id)).andExpect(status().isOk());
        mvc.perform(secure(post("/api/community/posts").contentType("application/json").content(postBody()), null))
            .andExpect(status().isUnauthorized());
        MockHttpSession other = login("member2");
        mvc.perform(secure(post("/api/community/posts").contentType("application/json").content(
            "{\"title\":\"새 글\",\"content\":\"본문\",\"authorId\":3,\"role\":\"ADMIN\"}"), other))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.authorId").value(2));
        mvc.perform(secure(patch("/api/community/posts/" + post.id).contentType("application/json").content(postBody()), other))
            .andExpect(status().isForbidden());
        mvc.perform(secure(delete("/api/community/posts/" + post.id), other)).andExpect(status().isForbidden());
        mvc.perform(secure(post("/api/community/notices").contentType("application/json").content(postBody()), other))
            .andExpect(status().isForbidden());
        MockHttpSession owner = login("member1");
        mvc.perform(secure(patch("/api/community/posts/" + post.id).contentType("application/json").content(postBody()), owner))
            .andExpect(status().isOk());
        mvc.perform(post("/api/community/posts").session(owner).contentType("application/json").content(postBody()))
            .andExpect(status().isForbidden());
    }

    @Test void commentOwnershipAndAdministratorUseRealMemberIdentity() throws Exception {
        String path = "/api/community/posts/" + post.id + "/comments/" + comment.id;
        MockHttpSession other = login("member2");
        mvc.perform(secure(patch(path).contentType("application/json").content("{\"content\":\"남의 댓글\"}"), other))
            .andExpect(status().isForbidden());
        mvc.perform(secure(delete(path), other)).andExpect(status().isForbidden());
        MockHttpSession owner = login("member1");
        mvc.perform(secure(patch(path).contentType("application/json").content("{\"content\":\"내 댓글 수정\"}"), owner))
            .andExpect(status().isOk());
        MockHttpSession admin = login("manager");
        mvc.perform(get("/api/community/session").session(admin)).andExpect(jsonPath("$.administrator").value(true));
        mvc.perform(secure(post("/api/community/notices").contentType("application/json").content(postBody()), admin))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.notice").value(true));
        mvc.perform(secure(delete(path), admin)).andExpect(status().isNoContent());
    }

    @Test void likesUseAuthenticatedIdAndLogoutRemovesCommunityAccess() throws Exception {
        MockHttpSession member = login("member1");
        String path = "/api/community/posts/" + post.id + "/likes";
        when(likes.existsByPost_IdAndUserId(post.id, 1L)).thenReturn(false);
        mvc.perform(secure(put(path), member)).andExpect(status().isOk());
        var savedLike = org.mockito.ArgumentCaptor.forClass(com.roomcrosscheck.community.domain.PostLike.class);
        verify(likes).saveAndFlush(savedLike.capture());
        assertEquals(1L, savedLike.getValue().userId);
        mvc.perform(secure(delete(path), member)).andExpect(status().isOk());
        verify(likes).deleteByPost_IdAndUserId(post.id, 1L);
        mvc.perform(secure(post("/api/auth/logout"), member)).andExpect(status().isOk());
        assertTrue(member.isInvalid());
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/community/session")).andExpect(jsonPath("$.authenticated").value(false));
    }
}
