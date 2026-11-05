package com.roomcrosscheck.community.service;

/**
 * 서버의 인증 정보를 커뮤니티 권한 검사에 전달하는 계약이다.
 * <p>인증 연동 계층에서 구현하며 요청 본문의 사용자 ID·역할을 신뢰해 생성하면 안 된다.
 * 사용자 ID는 로그인 아이디가 아닌 내부 회원 고유번호이며,
 * 객체 또는 유효한 회원 번호가 없으면 인증된 사용자로 취급하지 않는다.
 * 공통 역할 enum을 중복 정의하지 않고 관리자 여부만 전달받는다.
 */
public interface CurrentActor {
    Long userId();
    boolean administrator();

    /**
     * 요청 시점의 인증 정보를 제공하는 연동 지점이다.
     * 로그인 담당 코드에서 이 타입의 Spring Bean을 등록하고 서버의 인증 정보로 변환한다.
     * 개발용 고정 사용자나 클라이언트가 전달한 사용자 ID·권한을 공급하면 안 된다.
     */
    @FunctionalInterface
    interface Provider {
        /**
         * 현재 요청의 인증 사용자를 반환한다.
         *
         * @return 서버에서 확인한 사용자, 비로그인이면 null
         */
        CurrentActor current();
    }
}
