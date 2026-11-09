package com.roomcrosscheck.auth.dto;

import com.roomcrosscheck.auth.domain.User;
import com.roomcrosscheck.auth.domain.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Locale;

/** 회원가입과 회원 조회의 공개 계약이며 비밀번호 해시는 응답에 포함하지 않는다. */
public final class AuthDto {
    private AuthDto() {}

    /**
     * 일반 회원가입 입력이다. 아이디·이메일은 소문자로 통일하고 비밀번호는 원문 그대로 검증한다.
     * <p>클라이언트의 회원 번호·권한·작성 시각은 가입 정보로 사용하지 않는다.
     *
     * @param loginId 영문 소문자·숫자·밑줄·점·하이픈으로 구성된 4~30자 아이디
     * @param password 8자 이상이며 UTF-8 기준 72바이트 이하인 비밀번호
     * @param nickname 앞뒤 공백을 제외한 1~30자 표시 이름
     * @param email 형식을 검증할 연락처 이메일이며 소유권 인증은 별도 기능이다
     */
    public record SignupRequest(
        @NotBlank @Pattern(regexp = "[a-z0-9_.-]{4,30}") String loginId,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank @Size(max = 30) String nickname,
        @NotBlank @Email @Size(max = 254) String email
    ) {
        public SignupRequest {
            loginId = normalize(loginId);
            nickname = nickname == null ? null : nickname.strip();
            email = normalize(email);
        }

        @Override public String toString() { return "SignupRequest[credentials omitted]"; }
    }

    /** 회원 본인에게 반환하는 정보이며 내부 번호와 로그인 아이디를 구분한다. */
    public record MemberResponse(Long id, String loginId, String nickname, String email, UserRole role) {
        public static MemberResponse from(User user) {
            return new MemberResponse(user.getId(), user.getLoginId(), user.getNickname(), user.getEmail(), user.getRole());
        }
    }

    /** 프론트에서 일반 회원 인증을 사용할 수 있는지 확인하는 응답이다. */
    public record ConfigResponse(boolean memberEnabled) {}

    /** 인증 API 오류와 인증 필터의 성공 응답에 사용하는 형식이다. */
    public record MessageResponse(int status, String message) {}

    private static String normalize(String value) {
        return value == null ? null : value.strip().toLowerCase(Locale.ROOT);
    }
}
