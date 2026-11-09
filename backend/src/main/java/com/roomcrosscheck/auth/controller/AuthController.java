package com.roomcrosscheck.auth.controller;

import com.roomcrosscheck.auth.dto.AuthDto.*;
import com.roomcrosscheck.auth.service.AuthService;
import com.roomcrosscheck.auth.service.MemberPrincipal;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.Authentication;
import org.springframework.validation.method.MethodValidationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** 회원가입·본인 조회를 제공하고 로그인·로그아웃 요청 자체는 Security 필터에서 처리한다. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private static final Logger log = LoggerFactory.getLogger(AuthController.class);
    private final AuthService auth;

    public AuthController(AuthService auth) { this.auth = auth; }

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
        return ResponseEntity.badRequest().body(new MessageResponse(400, "가입 정보의 형식과 길이를 확인해 주세요."));
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
