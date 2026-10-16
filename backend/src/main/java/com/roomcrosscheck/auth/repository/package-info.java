/**
 * 회원 인증·접근 권한 (SFR-001).
 * 기능별 저장·조회 인터페이스와 DB·캐시·파일 저장 구현. 저장 형식을 변환하고 업무 규칙은 domain에 위임한다. 외부 API 호출은 client에 둔다.
 * Google OAuth 로그인과 사용자 식별·역할. 개인 자료 소유권은 해당 매물·방문 유스케이스에서도 검증한다.
 * <p>패키지의 책임을 설명하며 실제 구현 범위는 개발 문서를 참조한다.</p>
 */
package com.roomcrosscheck.auth.repository;
