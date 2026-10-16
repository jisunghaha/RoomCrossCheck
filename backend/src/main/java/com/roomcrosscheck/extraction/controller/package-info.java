/**
 * OCR·AI 정보 추출 (SFR-003).
 * HTTP 요청의 진입점. 입력 형식을 검증하고 service에 위임한다. DB·외부 API 직접 접근과 업무 계산을 넣지 않는다.
 * 광고 인식·구조화·정규화와 추출 상태를 관리한다. 없는 값을 추측하지 않고 실패에도 원본을 유지한다.
 * <p>패키지의 책임을 설명하며 실제 구현 범위는 개발 문서를 참조한다.</p>
 */
package com.roomcrosscheck.extraction.controller;
