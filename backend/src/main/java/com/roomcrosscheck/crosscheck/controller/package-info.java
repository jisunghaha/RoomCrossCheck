/**
 * 광고·공식 기록 대조 (SFR-006).
 * HTTP 요청의 진입점. 입력 형식을 검증하고 service에 위임한다. DB·외부 API 직접 접근과 업무 계산을 넣지 않는다.
 * 동일 대상·기준의 7개 후보를 규칙으로 대조한다. 조회 상태와 항목별 결과를 분리하고 AI에 판단을 맡기지 않는다.
 * <p>Implementation placeholder; no business API is implemented yet.</p>
 */
package com.roomcrosscheck.crosscheck.controller;
