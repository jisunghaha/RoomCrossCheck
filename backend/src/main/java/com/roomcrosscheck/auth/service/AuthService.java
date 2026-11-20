package com.roomcrosscheck.auth.service;

import com.roomcrosscheck.auth.domain.User;
import com.roomcrosscheck.auth.dto.AuthDto.MemberResponse;
import com.roomcrosscheck.auth.dto.AuthDto.SignupRequest;
import com.roomcrosscheck.auth.repository.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.nio.charset.StandardCharsets;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ResponseStatusException;

/** 실제 회원의 가입·본인 조회를 담당하며 비밀번호 검증과 세션 생성은 Security에 위임한다. */
@Service
@Validated
@Transactional(readOnly = true)
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder passwords;

    public AuthService(UserRepository users, PasswordEncoder passwords) {
        this.users = users;
        this.passwords = passwords;
    }

    /**
     * 입력을 검증한 뒤 BCrypt 해시와 기본 USER 권한으로 회원을 저장한다.
     * <p>사전 중복 검사 이후 동시 가입이 발생해도 DB 제약 위반을 409로 변환한다.
     *
     * @param request 검증할 가입 정보
     * @return 비밀번호가 없는 가입 회원 정보이며 자동 로그인은 수행하지 않는다
     * @throws ResponseStatusException 비밀번호 바이트 제한 위반은 400, 가입 정보 중복은 409
     */
    @Transactional
    public MemberResponse signup(@NotNull @Valid SignupRequest request) {
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "비밀번호는 UTF-8 기준 72바이트 이하여야 합니다.");
        }
        if (users.existsByLoginId(request.loginId()) || users.existsByEmail(request.email())) {
            throw duplicate();
        }
        User user = new User(request.loginId(), passwords.encode(request.password()), request.nickname(), request.email());
        try {
            return MemberResponse.from(users.saveAndFlush(user));
        } catch (DataIntegrityViolationException error) {
            throw duplicate();
        }
    }

    /** 인증에서 얻은 내부 번호로만 본인 정보를 조회한다. 클라이언트가 지정한 번호를 받지 않는다. */
    public MemberResponse me(Long userId) {
        if (userId == null || userId <= 0) throw unauthenticated();
        return users.findById(userId).map(MemberResponse::from).orElseThrow(AuthService::unauthenticated);
    }

    private static ResponseStatusException duplicate() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "이미 사용 중인 가입 정보입니다.");
    }

    private static ResponseStatusException unauthenticated() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
    }
}
