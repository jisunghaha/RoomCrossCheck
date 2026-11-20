package com.roomcrosscheck.auth.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.roomcrosscheck.auth.domain.PasswordResetCode;
import com.roomcrosscheck.auth.domain.User;
import com.roomcrosscheck.auth.repository.PasswordResetCodeRepository;
import com.roomcrosscheck.auth.repository.UserRepository;
import com.roomcrosscheck.listing.domain.Advertisement;
import com.roomcrosscheck.listing.repository.AdvertisementRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

/** 비밀번호 찾기 코드의 발급·재요청 제한·시도 횟수·만료와 비밀번호 변경·탈퇴의 본인 확인을 검증한다. */
class AccountServiceTest {
    private final UserRepository users = mock(UserRepository.class);
    private final PasswordResetCodeRepository codes = mock(PasswordResetCodeRepository.class);
    private final AdvertisementRepository ads = mock(AdvertisementRepository.class);
    private final ResetCodeMailer mailer = mock(ResetCodeMailer.class);
    private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder(4);
    private final List<PasswordResetCode> stored = new ArrayList<>();
    private final MutableClock clock = new MutableClock();
    @TempDir Path storage;
    private AccountService service;
    private User user;

    static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-11-20T00:00:00Z");
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    @BeforeEach void setup() {
        service = new AccountService(users, codes, ads, passwords, mailer, storage.toString(), clock);
        user = new User("member1", passwords.encode("old-password-1"), "회원", "member@example.com");
        ReflectionTestUtils.setField(user, "id", 7L);
        when(users.findByEmail("member@example.com")).thenReturn(Optional.of(user));
        when(users.findById(7L)).thenReturn(Optional.of(user));
        when(codes.saveAndFlush(any())).thenAnswer(call -> { stored.add(call.getArgument(0)); return call.getArgument(0); });
        when(codes.findFirstByUserIdOrderByCreatedAtDesc(7L)).thenAnswer(call ->
            stored.isEmpty() ? Optional.empty() : Optional.of(stored.get(stored.size() - 1)));
        doAnswer(call -> { stored.clear(); return null; }).when(codes).deleteAllByUserId(7L);
    }

    private String requestCode() {
        service.requestReset(" Member@Example.com ");
        var code = ArgumentCaptor.forClass(String.class);
        verify(mailer, atLeastOnce()).send(eq(7L), eq("member@example.com"), code.capture());
        return code.getValue();
    }

    @Test void unknownEmailIsSilentAndResendIsLimitedToOncePerMinute() {
        service.requestReset("nobody@example.com");
        verifyNoInteractions(mailer);
        String first = requestCode();
        assertTrue(first.matches("\\d{6}"));
        assertNotEquals(first, stored.get(0).codeHash);
        service.requestReset("member@example.com");
        verify(mailer, times(1)).send(anyLong(), anyString(), anyString());
        clock.now = clock.now.plus(AccountService.RESEND_INTERVAL).plusSeconds(1);
        service.requestReset("member@example.com");
        verify(mailer, times(2)).send(anyLong(), anyString(), anyString());
        assertEquals(1, stored.size());
    }

    @Test void correctCodeChangesPasswordOnceAndWrongCodesLockAfterFiveTries() {
        String code = requestCode();
        String wrong = code.equals("000000") ? "111111" : "000000";
        for (int i = 0; i < PasswordResetCode.MAX_ATTEMPTS; i++) {
            var error = assertThrows(ResponseStatusException.class,
                () -> service.confirmReset("member@example.com", wrong, "new-password-1"));
            assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        }
        assertThrows(ResponseStatusException.class, () -> service.confirmReset("member@example.com", code, "new-password-1"));
        assertTrue(passwords.matches("old-password-1", user.getPasswordHash()));

        clock.now = clock.now.plusSeconds(61);
        String fresh = requestCode();
        service.confirmReset("member@example.com", fresh, "new-password-1");
        assertTrue(passwords.matches("new-password-1", user.getPasswordHash()));
        assertThrows(ResponseStatusException.class, () -> service.confirmReset("member@example.com", fresh, "another-pass-1"));
    }

    @Test void expiredCodeAndShortPasswordAreRejected() {
        String code = requestCode();
        assertThrows(ResponseStatusException.class, () -> service.confirmReset("member@example.com", code, "short"));
        clock.now = clock.now.plus(AccountService.CODE_TTL).plusSeconds(1);
        assertThrows(ResponseStatusException.class, () -> service.confirmReset("member@example.com", code, "new-password-1"));
        assertTrue(passwords.matches("old-password-1", user.getPasswordHash()));
    }

    @Test void changeAndWithdrawRequireTheCurrentPasswordAndWithdrawRemovesAdImages() throws Exception {
        assertThrows(ResponseStatusException.class, () -> service.changePassword(7L, "wrong-password", "new-password-1"));
        service.changePassword(7L, "old-password-1", "new-password-1");
        assertTrue(passwords.matches("new-password-1", user.getPasswordHash()));

        Advertisement ad = new Advertisement();
        ad.imageStorageKey = "ad-image.png";
        Files.writeString(storage.resolve("ad-image.png"), "image");
        when(ads.findAllByOwnerIdOrderByCreatedAtDesc(7L)).thenReturn(List.of(ad));
        assertThrows(ResponseStatusException.class, () -> service.withdraw(7L, "old-password-1"));
        verify(users, never()).delete(any());
        service.withdraw(7L, "new-password-1");
        verify(users).delete(user);
        assertFalse(Files.exists(storage.resolve("ad-image.png")));
    }
}
