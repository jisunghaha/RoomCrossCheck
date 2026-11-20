package com.roomcrosscheck.auth.repository;

import com.roomcrosscheck.auth.domain.PasswordResetCode;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** 비밀번호 재설정 코드 저장소. 회원 번호 조건으로만 조회한다. */
public interface PasswordResetCodeRepository extends JpaRepository<PasswordResetCode, String> {
    Optional<PasswordResetCode> findFirstByUserIdOrderByCreatedAtDesc(Long userId);
    void deleteAllByUserId(Long userId);
}
