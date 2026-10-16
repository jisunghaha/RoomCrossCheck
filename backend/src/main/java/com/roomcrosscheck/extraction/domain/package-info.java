/**
 * OCR·AI 정보 추출 (SFR-003).
 * 엔티티·값 객체·업무 규칙. HTTP, Spring, JPA, Redis, 외부 API에 의존하지 않는다. 규칙에 필요한 정보를 가진 객체가 검증과 상태 변경을 담당한다.
 * 광고 인식·구조화·정규화와 추출 상태를 관리한다. 없는 값을 추측하지 않고 실패에도 원본을 유지한다.
 * <p>패키지의 책임을 설명하며 실제 구현 범위는 개발 문서를 참조한다.</p>
 */
package com.roomcrosscheck.extraction.domain;
