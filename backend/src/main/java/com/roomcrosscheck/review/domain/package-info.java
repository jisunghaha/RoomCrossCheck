/**
 * 건물별 후기·통계 (SFR-011, SFR-012).
 * 엔티티·값 객체·업무 규칙. HTTP, Spring, JPA, Redis, 외부 API에 의존하지 않는다. 규칙에 필요한 정보를 가진 객체가 검증과 상태 변경을 담당한다.
 * 건물에 연결한 8개 항목 평가와 공개 후기 통계를 관리한다. 미응답은 분모에서 제외하고 수정·삭제·숨김 시 캐시를 무효화한다.
 * <p>Implementation placeholder; no business API is implemented yet.</p>
 */
package com.roomcrosscheck.review.domain;
