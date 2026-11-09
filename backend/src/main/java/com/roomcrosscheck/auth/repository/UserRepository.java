package com.roomcrosscheck.auth.repository;

import com.roomcrosscheck.auth.domain.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** 정규화한 로그인 아이디로 회원을 찾으며 중복 가입은 DB 고유 제약으로도 차단한다. */
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByLoginId(String loginId);
    boolean existsByLoginId(String loginId);
    boolean existsByEmail(String email);
}
