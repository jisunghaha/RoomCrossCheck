package com.roomcrosscheck.auth.service;

import com.roomcrosscheck.auth.domain.User;
import com.roomcrosscheck.auth.domain.UserRole;
import com.roomcrosscheck.auth.dto.AuthDto.SignupRequest;
import com.roomcrosscheck.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 비밀번호 해시·DB 중복 경쟁·멀티바이트 비밀번호 제한과 인증 정보 소거를 검증한다. */
class AuthServiceTest {
    private final UserRepository users = mock(UserRepository.class);
    private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder();
    private final AuthService auth = new AuthService(users, passwords);
    private final SignupRequest request = new SignupRequest(" Member.One ", "password-1234", " 회원 ", " ONE@EXAMPLE.COM ");

    @BeforeEach void savedIdentity() {
        when(users.saveAndFlush(any(User.class))).thenAnswer(call -> {
            User user = call.getArgument(0);
            ReflectionTestUtils.setField(user, "id", 1L);
            return user;
        });
    }

    @Test void hashesPasswordAndSetsUserRoleWithoutKeepingPlaintext() {
        var member = auth.signup(request);
        var saved = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(users).saveAndFlush(saved.capture());
        assertEquals("member.one", member.loginId());
        assertEquals("one@example.com", member.email());
        assertEquals("회원", member.nickname());
        assertEquals(UserRole.USER, member.role());
        assertNotEquals(request.password(), saved.getValue().getPasswordHash());
        assertTrue(passwords.matches(request.password(), saved.getValue().getPasswordHash()));
        assertFalse(request.toString().contains(request.password()));
    }

    @Test void duplicateLoginIdAndEmailAreRejectedBeforeSave() {
        when(users.existsByLoginId("member.one")).thenReturn(true);
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> auth.signup(request)).getStatusCode().value());
        when(users.existsByLoginId("member.one")).thenReturn(false);
        when(users.existsByEmail("one@example.com")).thenReturn(true);
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> auth.signup(request)).getStatusCode().value());
        verify(users, never()).saveAndFlush(any());
    }

    @Test void databaseUniqueConstraintStillRejectsConcurrentRegistration() {
        doThrow(new DataIntegrityViolationException("duplicate")).when(users).saveAndFlush(any(User.class));
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> auth.signup(request)).getStatusCode().value());
    }

    @Test void rejectsPasswordsOverBcryptByteLimitInsteadOfTruncating() {
        var multibyte = new SignupRequest("member", "가".repeat(25), "회원", "one@example.com");
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> auth.signup(multibyte)).getStatusCode().value());
        verify(users, never()).saveAndFlush(any());
    }

    @Test void unknownCurrentMemberRequiresLoginAgain() {
        assertEquals(401, assertThrows(ResponseStatusException.class, () -> auth.me(77L)).getStatusCode().value());
        assertEquals(401, assertThrows(ResponseStatusException.class, () -> auth.me(null)).getStatusCode().value());
    }

    @Test void principalErasesHashAfterAuthentication() {
        User user = new User("member", passwords.encode("password-1234"), "회원", "one@example.com");
        ReflectionTestUtils.setField(user, "id", 5L);
        MemberPrincipal principal = new MemberPrincipal(user);
        assertNotNull(principal.getPassword());
        principal.eraseCredentials();
        assertNull(principal.getPassword());
        assertEquals(5L, principal.userId());
        assertFalse(principal.administrator());
        assertTrue(passwords.matches("password-1234", user.getPasswordHash()));
    }
}
