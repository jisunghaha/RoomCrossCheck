/**
 * 건물별 후기·통계 (SFR-011, SFR-012).
 * 유스케이스·소유권·작업 순서·트랜잭션을 조정한다. 업무 판단은 domain에 위임하고 repository·client 인터페이스를 생성자로 주입받는다.
 * 건물에 연결한 8개 항목 평가와 공개 후기 통계를 관리한다. 미응답은 분모에서 제외하고 수정·삭제·숨김 시 캐시를 무효화한다.
 * <p>Implementation placeholder; no business API is implemented yet.</p>
 */
package com.roomcrosscheck.review.service;
