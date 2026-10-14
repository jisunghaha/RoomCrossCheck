/**
 * 광고·공식 기록 대조 (SFR-006).
 * 유스케이스·소유권·작업 순서·트랜잭션을 조정한다. 업무 판단은 domain에 위임하고 repository·client 인터페이스를 생성자로 주입받는다.
 * 동일 대상·기준의 7개 후보를 규칙으로 대조한다. 조회 상태와 항목별 결과를 분리하고 AI에 판단을 맡기지 않는다.
 * <p>패키지의 책임을 설명하며 실제 구현 범위는 개발 문서를 참조한다.</p>
 */
package com.roomcrosscheck.crosscheck.service;
