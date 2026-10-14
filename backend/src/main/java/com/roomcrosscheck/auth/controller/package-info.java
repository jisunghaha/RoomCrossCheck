/**
 * 회원 인증·접근 권한 (SFR-001).
 * HTTP 요청의 진입점. 입력 형식을 검증하고 service에 위임한다. DB·외부 API 직접 접근과 업무 계산을 넣지 않는다.
 * Google OAuth 로그인과 사용자 식별·역할. 개인 자료 소유권은 해당 매물·방문 유스케이스에서도 검증한다.
 * <p>패키지의 책임을 설명하며 실제 구현 범위는 개발 문서를 참조한다.</p>
 */
package com.roomcrosscheck.auth.controller;
