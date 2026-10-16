/**
 * OCR·AI 정보 추출 (SFR-003).
 * 기능별 저장·조회 인터페이스와 DB·캐시·파일 저장 구현. 저장 형식을 변환하고 업무 규칙은 domain에 위임한다. 외부 API 호출은 client에 둔다.
 * 광고 인식·구조화·정규화와 추출 상태를 관리한다. 없는 값을 추측하지 않고 실패에도 원본을 유지한다.
 * <p>패키지의 책임을 설명하며 실제 구현 범위는 개발 문서를 참조한다.</p>
 */
package com.roomcrosscheck.extraction.repository;
