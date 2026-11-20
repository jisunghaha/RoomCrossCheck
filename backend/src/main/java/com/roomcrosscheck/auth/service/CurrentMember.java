package com.roomcrosscheck.auth.service;

import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * 현재 요청의 로그인 회원을 서버 세션에서 읽어 매물·방문·후기·신고 기능에 전달한다.
 * <p>요청 본문이나 헤더의 회원 번호·권한은 사용하지 않는다. 보안 필터가 먼저 인증을 요구하지만
 * 서비스 호출 경로가 바뀌어도 소유권 검사가 빠지지 않도록 이 계층에서 다시 확인한다.
 */
@Component
public class CurrentMember {
    /**
     * 로그인한 회원 정보를 조회한다.
     *
     * @return 인증된 회원, 비로그인이면 빈 값
     */
    public Optional<MemberPrincipal> find() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken
                || !(authentication.getPrincipal() instanceof MemberPrincipal member)
                || member.userId() == null || member.userId() <= 0) return Optional.empty();
        return Optional.of(member);
    }

    /**
     * 로그인한 회원 정보를 반환한다.
     *
     * @return 인증된 회원
     * @throws ResponseStatusException 비로그인이면 401
     */
    public MemberPrincipal require() {
        return find().orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."));
    }

    /**
     * 로그인한 회원의 내부 회원 번호를 반환한다.
     *
     * @return users.id 값
     * @throws ResponseStatusException 비로그인이면 401
     */
    public Long requireId() { return require().userId(); }
}
