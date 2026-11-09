package com.roomcrosscheck.auth.service;

import com.roomcrosscheck.auth.repository.UserRepository;
import java.util.Locale;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 일반 로그인에서 정규화한 아이디로 DB 회원을 찾고 인증용 Principal로 변환한다. */
@Service
@Transactional(readOnly = true)
public class MemberUserDetailsService implements UserDetailsService {
    private final UserRepository users;

    public MemberUserDetailsService(UserRepository users) { this.users = users; }

    @Override public MemberPrincipal loadUserByUsername(String username) {
        String loginId = username == null ? "" : username.strip().toLowerCase(Locale.ROOT);
        if (!loginId.matches("[a-z0-9_.-]{4,30}")) throw missing();
        return users.findByLoginId(loginId).map(MemberPrincipal::new).orElseThrow(MemberUserDetailsService::missing);
    }

    private static UsernameNotFoundException missing() {
        return new UsernameNotFoundException("아이디 또는 비밀번호가 올바르지 않습니다.");
    }
}
