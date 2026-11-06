package com.roomcrosscheck.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 시연 프로필이 없으면 비밀번호나 로그인 경로를 사용할 수 없는지 확인한다. */
@SpringBootTest(classes = DemoLoginSecurityTest.TestApp.class)
@AutoConfigureMockMvc
class DemoLoginDisabledTest {
    @Autowired MockMvc mvc;

    @Test void demoLoginIsDisabledByDefault() throws Exception {
        mvc.perform(get("/api/auth/config")).andExpect(status().isOk())
            .andExpect(jsonPath("$.demoEnabled").value(false));
        mvc.perform(get("/api/community/session")).andExpect(status().isOk())
            .andExpect(jsonPath("$.authenticated").value(false));
        mvc.perform(post("/api/auth/login").param("username", "admin").param("password", "test-admin-password"))
            .andExpect(status().isForbidden());
    }
}
