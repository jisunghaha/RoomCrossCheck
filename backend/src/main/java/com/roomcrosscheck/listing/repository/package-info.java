/**
 * 광고 원본·개인 매물 (SFR-002, SFR-004, SFR-009).
 * 기능별 저장·조회 인터페이스와 DB·캐시·파일 저장 구현. 저장 형식을 변환하고 업무 규칙은 domain에 위임한다. 외부 API 호출은 client에 둔다.
 * 임시 광고와 확정 매물을 구분하고 원본·사용자 확정값을 보존한다. 저장·목록·상세·선택 삭제와 소유권을 관리한다.
 * <p>Implementation placeholder; no business API is implemented yet.</p>
 */
package com.roomcrosscheck.listing.repository;
