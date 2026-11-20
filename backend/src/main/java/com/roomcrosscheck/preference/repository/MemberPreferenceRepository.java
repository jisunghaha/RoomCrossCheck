package com.roomcrosscheck.preference.repository;

import com.roomcrosscheck.preference.domain.MemberPreference;
import org.springframework.data.jpa.repository.JpaRepository;

/** 회원별 체크리스트·비교 선택 저장소. 기본키가 회원 번호다. */
public interface MemberPreferenceRepository extends JpaRepository<MemberPreference, Long> {}
