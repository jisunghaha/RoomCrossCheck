package com.roomcrosscheck.auth;

import com.roomcrosscheck.auth.config.CommunitySecurityConfiguration;
import com.roomcrosscheck.auth.controller.DemoLoginController;
import com.roomcrosscheck.community.controller.CommunityController;
import com.roomcrosscheck.community.domain.Post;
import com.roomcrosscheck.community.repository.*;
import com.roomcrosscheck.community.service.*;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 실제 보안 필터·비밀번호 검증·세션·CSRF와 커뮤니티 Service의 소유권 검사를 함께 확인한다. */
@SpringBootTest(classes = DemoLoginSecurityTest.TestApp.class, properties = {
    "spring.datasource.url=jdbc:mysql://localhost/rc_demo_filter_test",
    "DEMO_MEMBER1_PASSWORD=test-member1-password", "DEMO_MEMBER2_PASSWORD=test-member2-password",
    "DEMO_ADMIN_PASSWORD=test-admin-password" })
@AutoConfigureMockMvc
@ActiveProfiles("demo-login")
class DemoLoginSecurityTest {
    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class})
    @Import({CommunitySecurityConfiguration.class, DemoLoginController.class, CommunityController.class,
        PostService.class, CommentService.class, PostLikeService.class})
    static class TestApp {
        @Bean PostRepository posts() { return mock(PostRepository.class); }
        @Bean CommentRepository comments() { return mock(CommentRepository.class); }
        @Bean PostLikeRepository likes() { return mock(PostLikeRepository.class); }
    }
    @Autowired MockMvc mvc;
    @Autowired PostRepository posts;
    private Post post;

    @BeforeEach void setup() {
        reset(posts);
        post = new Post(); post.authorId = 900000001L; post.title = "검증 글"; post.content = "본문";
        post.createdAt = Instant.now(); post.updatedAt = post.createdAt;
        when(posts.findById(post.id)).thenReturn(Optional.of(post));
        when(posts.saveAndFlush(any(Post.class))).thenAnswer(call -> call.getArgument(0));
    }

    private Cookie csrf(MockHttpSession session) throws Exception {
        var request = get("/api/auth/csrf");
        if (session != null) request.session(session);
        Cookie cookie = mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getCookie("XSRF-TOKEN");
        assertNotNull(cookie);
        return cookie;
    }

    private MockHttpServletRequestBuilder secure(MockHttpServletRequestBuilder request, MockHttpSession session) throws Exception {
        Cookie cookie = csrf(session);
        request.cookie(cookie).header("X-XSRF-TOKEN", cookie.getValue());
        if (session != null) request.session(session);
        return request;
    }

    private MockHttpSession login(String username, String password) throws Exception {
        return (MockHttpSession) mvc.perform(secure(post("/api/auth/login").param("username", username).param("password", password), null))
            .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }

    @Test void guestCannotForgeIdentityAndLoginRequiresCsrfAndCorrectPassword() throws Exception {
        mvc.perform(get("/api/auth/config")).andExpect(jsonPath("$.demoEnabled").value(true));
        mvc.perform(get("/api/community/session").header("X-User-Id", "900000003").header("X-Role", "ADMIN"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.authenticated").value(false));
        mvc.perform(get("/api/community/posts/" + post.id)).andExpect(status().isOk());
        mvc.perform(post("/api/auth/login").param("username", "admin").param("password", "test-admin-password"))
            .andExpect(status().isForbidden());
        mvc.perform(secure(post("/api/auth/login").param("username", "member1").param("password", "wrong-password"), null))
            .andExpect(status().isUnauthorized());
        mvc.perform(secure(post("/api/community/posts").contentType("application/json")
            .content("{\"title\":\"위조\",\"content\":\"본문\",\"authorId\":900000003,\"role\":\"ADMIN\"}"), null))
            .andExpect(status().isUnauthorized());
    }

    @Test void authenticatedMemberHasServerIdentityAndCannotModifyOthersOrCreateNotices() throws Exception {
        MockHttpSession member = login("member2", "test-member2-password");
        assertNotNull(member);
        mvc.perform(get("/api/community/session").session(member)).andExpect(status().isOk())
            .andExpect(jsonPath("$.userId").value(900000002)).andExpect(jsonPath("$.administrator").value(false));
        mvc.perform(secure(patch("/api/community/posts/" + post.id).contentType("application/json")
            .content("{\"title\":\"남의 글\",\"content\":\"변경\"}"), member)).andExpect(status().isForbidden());
        mvc.perform(secure(post("/api/community/notices").contentType("application/json")
            .content("{\"title\":\"공지\",\"content\":\"본문\"}"), member)).andExpect(status().isForbidden());
        mvc.perform(secure(post("/api/community/posts").contentType("application/json")
            .content("{\"title\":\"내 글\",\"content\":\"본문\",\"authorId\":900000003}"), member))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.authorId").value(900000002));
        mvc.perform(post("/api/community/posts").session(member).contentType("application/json")
            .content("{\"title\":\"토큰 없음\",\"content\":\"본문\"}")).andExpect(status().isForbidden());
    }

    @Test void ownerAndAdministratorCanEditAndLogoutInvalidatesTheSession() throws Exception {
        MockHttpSession owner = login("member1", "test-member1-password");
        mvc.perform(secure(patch("/api/community/posts/" + post.id).contentType("application/json")
            .content("{\"title\":\"내 글 수정\",\"content\":\"본문\"}"), owner)).andExpect(status().isOk());
        MockHttpSession admin = login("admin", "test-admin-password");
        mvc.perform(get("/api/community/session").session(admin)).andExpect(jsonPath("$.administrator").value(true));
        mvc.perform(secure(post("/api/community/notices").contentType("application/json")
            .content("{\"title\":\"관리자 공지\",\"content\":\"본문\"}"), admin)).andExpect(status().isCreated())
            .andExpect(jsonPath("$.notice").value(true));
        mvc.perform(secure(post("/api/auth/logout"), admin)).andExpect(status().isOk());
        assertTrue(admin.isInvalid());
        mvc.perform(get("/api/community/session")).andExpect(jsonPath("$.authenticated").value(false));
    }
}
