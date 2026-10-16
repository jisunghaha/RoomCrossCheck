/**
 * 관심 매물 비교 (SFR-008).
 * HTTP 요청의 진입점. 입력 형식을 검증하고 service에 위임한다. DB·외부 API 직접 접근과 업무 계산을 넣지 않는다.
 * 소유자의 서로 다른 매물 2~3개와 최신 방문·공식 대조·건물 후기 통계를 조회해 같은 항목으로 구성한다.
 * <p>패키지의 책임을 설명하며 실제 구현 범위는 개발 문서를 참조한다.</p>
 */
package com.roomcrosscheck.comparison.controller;
