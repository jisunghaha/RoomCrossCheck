/**
 * 건물별 후기·통계 (SFR-011, SFR-012).
 * 기능별 저장·조회 인터페이스와 DB·캐시·파일 저장 구현. 저장 형식을 변환하고 업무 규칙은 domain에 위임한다. 외부 API 호출은 client에 둔다.
 * 건물에 연결한 8개 항목 평가와 공개 후기 통계를 관리한다. 미응답은 분모에서 제외하고 수정·삭제·숨김 시 캐시를 무효화한다.
 * <p>Implementation placeholder; no business API is implemented yet.</p>
 */
package com.roomcrosscheck.review.repository;
