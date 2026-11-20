package com.roomcrosscheck.auth.service;

import com.roomcrosscheck.auth.domain.PasswordResetCode;
import com.roomcrosscheck.auth.domain.User;
import com.roomcrosscheck.auth.repository.PasswordResetCodeRepository;
import com.roomcrosscheck.auth.repository.UserRepository;
import com.roomcrosscheck.listing.repository.AdvertisementRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

/**
 * SFR-001 비밀번호 찾기·변경과 회원 탈퇴를 처리한다.
 * <p>비밀번호 찾기는 가입 여부를 드러내지 않도록 이메일 존재와 관계없이 같은 응답을 준다.
 * 탈퇴하면 users 행을 지우고, DB 외래키 규칙에 따라 개인 자료는 함께 삭제되며 공개 글은 작성자만 비워진다.
 * 광고 원본 이미지 파일은 DB 밖에 있으므로 커밋이 끝난 뒤 직접 지운다.
 */
@Service
@Transactional(readOnly = true)
public class AccountService {
    static final Duration CODE_TTL = Duration.ofMinutes(10);
    static final Duration RESEND_INTERVAL = Duration.ofSeconds(60);
    private static final Logger log = LoggerFactory.getLogger(AccountService.class);
    private final UserRepository users;
    private final PasswordResetCodeRepository codes;
    private final AdvertisementRepository advertisements;
    private final PasswordEncoder passwords;
    private final ResetCodeMailer mailer;
    private final Path storageDirectory;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    @Autowired
    public AccountService(UserRepository users, PasswordResetCodeRepository codes, AdvertisementRepository advertisements,
            PasswordEncoder passwords, ResetCodeMailer mailer,
            @Value("${ads.storage-dir:/tmp/roomcrosscheck-ads}") String storageDirectory) {
        this(users, codes, advertisements, passwords, mailer, storageDirectory, Clock.systemUTC());
    }

    AccountService(UserRepository users, PasswordResetCodeRepository codes, AdvertisementRepository advertisements,
            PasswordEncoder passwords, ResetCodeMailer mailer, String storageDirectory, Clock clock) {
        this.users = users;
        this.codes = codes;
        this.advertisements = advertisements;
        this.passwords = passwords;
        this.mailer = mailer;
        this.storageDirectory = Path.of(storageDirectory).toAbsolutePath().normalize();
        this.clock = clock;
    }

    /**
     * 가입 이메일로 6자리 인증 코드를 보낸다. 같은 회원은 60초에 한 번만 새 코드를 받는다.
     * 가입하지 않은 이메일이나 비밀번호가 없는 소셜 회원도 오류 없이 끝내 가입 여부를 드러내지 않는다.
     *
     * @param email 가입 이메일
     */
    @Transactional
    public void requestReset(String email) {
        var found = users.findByEmail(normalize(email)).filter(user -> user.getPasswordHash() != null);
        if (found.isEmpty()) return;
        User user = found.get();
        Instant now = clock.instant();
        var latest = codes.findFirstByUserIdOrderByCreatedAtDesc(user.getId());
        if (latest.isPresent() && latest.get().createdAt.plus(RESEND_INTERVAL).isAfter(now)) return;
        codes.deleteAllByUserId(user.getId());
        String code = String.format("%06d", random.nextInt(1_000_000));
        codes.saveAndFlush(new PasswordResetCode(user.getId(), passwords.encode(code), now, now.plus(CODE_TTL)));
        mailer.send(user.getId(), user.getEmail(), code);
    }

    /**
     * 인증 코드를 확인하고 새 비밀번호로 바꾼다. 틀리면 시도 횟수를 늘리고, 5번 틀리면 코드를 쓸 수 없다.
     *
     * @param email 가입 이메일
     * @param code 메일로 받은 6자리 코드
     * @param newPassword 새 비밀번호(8자 이상, UTF-8 72바이트 이하)
     * @throws ResponseStatusException 코드가 없거나 틀리거나 만료되면 400
     */
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public void confirmReset(String email, String code, String newPassword) {
        checkPassword(newPassword);
        var invalid = new ResponseStatusException(HttpStatus.BAD_REQUEST, "인증 코드가 올바르지 않거나 만료되었습니다. 코드를 다시 받아 주세요.");
        User user = users.findByEmail(normalize(email)).filter(found -> found.getPasswordHash() != null).orElseThrow(() -> invalid);
        PasswordResetCode saved = codes.findFirstByUserIdOrderByCreatedAtDesc(user.getId()).orElseThrow(() -> invalid);
        Instant now = clock.instant();
        if (!saved.usable(now)) throw invalid;
        if (!passwords.matches(code, saved.codeHash)) {
            saved.attempts++;
            codes.saveAndFlush(saved);
            throw invalid;
        }
        saved.usedAt = now;
        codes.save(saved);
        user.changePassword(passwords.encode(newPassword));
        users.saveAndFlush(user);
    }

    /**
     * 로그인한 회원의 비밀번호를 바꾼다.
     *
     * @throws ResponseStatusException 현재 비밀번호가 틀리면 400
     */
    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        checkPassword(newPassword);
        User user = users.findById(userId).orElseThrow(AccountService::unauthenticated);
        if (user.getPasswordHash() == null || !passwords.matches(currentPassword, user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "현재 비밀번호가 올바르지 않습니다.");
        }
        user.changePassword(passwords.encode(newPassword));
        users.saveAndFlush(user);
    }

    /**
     * 비밀번호를 확인한 뒤 회원을 탈퇴 처리한다.
     * 매물·광고·방문 기록·설정·좋아요·재설정 코드는 DB에서 함께 삭제되고, 게시글·댓글·후기는 작성자만 비워진다.
     *
     * @throws ResponseStatusException 비밀번호가 틀리면 400
     */
    @Transactional
    public void withdraw(Long userId, String password) {
        User user = users.findById(userId).orElseThrow(AccountService::unauthenticated);
        if (user.getPasswordHash() == null || !passwords.matches(password, user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "비밀번호가 올바르지 않습니다.");
        }
        List<String> images = advertisements.findAllByOwnerIdOrderByCreatedAtDesc(userId).stream()
            .map(ad -> ad.imageStorageKey).filter(key -> key != null && !key.isBlank()).toList();
        users.delete(user);
        users.flush();
        deleteFilesAfterCommit(images);
    }

    private void deleteFilesAfterCommit(List<String> keys) {
        if (keys.isEmpty()) return;
        Runnable delete = () -> {
            for (String key : keys) {
                Path file = storageDirectory.resolve(key).normalize();
                if (!file.startsWith(storageDirectory)) continue;
                try { Files.deleteIfExists(file); }
                catch (IOException error) { log.warn("탈퇴 회원의 광고 이미지 삭제 실패: {}", error.getClass().getSimpleName()); }
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { delete.run(); }
            });
        } else delete.run();
    }

    private static void checkPassword(String password) {
        if (password == null || password.length() < 8 || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "새 비밀번호는 8자 이상, UTF-8 기준 72바이트 이하로 입력해 주세요.");
        }
    }

    private static String normalize(String email) {
        return email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
    }

    private static ResponseStatusException unauthenticated() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
    }
}
