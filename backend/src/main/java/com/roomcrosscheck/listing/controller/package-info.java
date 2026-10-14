/**
 * 광고 원본·개인 매물 (SFR-002, SFR-004, SFR-009).
 * HTTP 요청의 진입점. 입력 형식을 검증하고 service에 위임한다. DB·외부 API 직접 접근과 업무 계산을 넣지 않는다.
 * 임시 광고와 확정 매물을 구분하고 원본·사용자 확정값을 보존한다. 저장·목록·상세·선택 삭제와 소유권을 관리한다.
 * <p>Implementation placeholder; no business API is implemented yet.</p>
 */
package com.roomcrosscheck.listing.controller;
