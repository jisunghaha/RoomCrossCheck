package com.roomcrosscheck.auth.controller;

import com.roomcrosscheck.auth.dto.AuthDto.*;
import com.roomcrosscheck.auth.service.AccountService;
import com.roomcrosscheck.auth.service.AuthService;
import com.roomcrosscheck.auth.service.MemberPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.validation.method.MethodValidationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * 회원가입·본인 조회·비밀번호 찾기·비밀번호 변경·탈퇴를 제공한다.
 * <p>로그인·로그아웃 요청 자체는 Security 필터에서 처리한다. 회원 번호는 세션의 인증 정보에서만 읽는다.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private static final Logger log = LoggerFactory.getLogger(AuthController.class);
    private static final MessageResponse RESET_SENT = new MessageResponse(200,
        "입력한 이메일로 가입한 계정이 있으면 인증 코드를 보냈습니다. 10분 안에 입력해 주세요.");
    private final AuthService auth;
    private final AccountService accounts;

    public AuthController(AuthService auth, AccountService accounts) {
        this.auth = auth;
        this.accounts = accounts;
    }

    /** 가입 여부와 관계없이 같은 안내를 돌려준다. */
    @PostMapping("/password-reset/request")
    public MessageResponse requestReset(@Valid @RequestBody PasswordResetRequest request) {
        accounts.requestReset(request.email());
        return RESET_SENT;
    }

    @PostMapping("/password-reset/confirm")
    public MessageResponse confirmReset(@Valid @RequestBody PasswordResetConfirm request) {
        accounts.confirmReset(request.email(), request.code(), request.newPassword());
        return new MessageResponse(200, "비밀번호를 바꿨습니다. 새 비밀번호로 로그인해 주세요.");
    }

    @PostMapping("/password")
    public MessageResponse changePassword(Authentication authentication, @Valid @RequestBody PasswordChangeRequest request) {
        accounts.changePassword(member(authentication).userId(), request.currentPassword(), request.newPassword());
        return new MessageResponse(200, "비밀번호를 바꿨습니다.");
    }

    /** 탈퇴가 끝나면 현재 세션을 끝내고 세션 쿠키를 지운다. */
    @PostMapping("/withdraw")
    public MessageResponse withdraw(Authentication authentication, @Valid @RequestBody WithdrawRequest request,
            HttpServletRequest http, HttpServletResponse response) {
        accounts.withdraw(member(authentication).userId(), request.password());
        new SecurityContextLogoutHandler().logout(http, response, authentication);
        var cookie = new jakarta.servlet.http.Cookie("JSESSIONID", "");
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
        return new MessageResponse(200, "탈퇴했습니다. 이용해 주셔서 감사합니다.");
    }

    private static MemberPrincipal member(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof MemberPrincipal member)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        return member;
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public MemberResponse signup(@Valid @RequestBody SignupRequest request) { return auth.signup(request); }

    @GetMapping("/me")
    public MemberResponse me(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof MemberPrincipal member)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        return auth.me(member.userId());
    }

    @GetMapping("/config")
    public ConfigResponse config() { return new ConfigResponse(true); }

    @GetMapping("/csrf")
    public Map<String, Boolean> csrf() { return Map.of("ready", true); }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<MessageResponse> businessError(ResponseStatusException error) {
        int status = error.getStatusCode().value();
        return ResponseEntity.status(status).body(new MessageResponse(status, error.getReason()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
        ConstraintViolationException.class, MethodValidationException.class})
    public ResponseEntity<MessageResponse> invalidInput(Exception error) {
        return ResponseEntity.badRequest().body(new MessageResponse(400, "입력한 정보의 형식과 길이를 확인해 주세요."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<MessageResponse> unexpectedError(Exception error) {
        // 비밀번호·입력값·SQL 상세가 로그에 남지 않도록 오류 종류만 기록한다.
        log.error("회원 API 처리 실패: {}", error.getClass().getName());
        return ResponseEntity.internalServerError().body(new MessageResponse(500, "회원 요청을 처리하지 못했습니다."));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<MessageResponse> unsupportedMediaType(HttpMediaTypeNotSupportedException error) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
            .body(new MessageResponse(415, "회원가입은 JSON 형식으로 요청해 주세요."));
    }
}
