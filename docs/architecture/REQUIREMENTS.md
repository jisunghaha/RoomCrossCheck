# 계획서 요구사항과 개발 위치

기준: 사용자가 제공한 `4조_캡스톤디자인.hwp`. 이 표는 구현 위치를 지정하며 요구사항 완료를 뜻하지 않는다. 백엔드 각 기능에는 controller/service/domain/repository/dto 패키지가 준비되어 있으며 auth/building/extraction에는 외부 API client도 있다.

| 요구사항 | 기능 | 프론트 `src/features/` | 백엔드 `com.roomcrosscheck` |
|---|---|---|---|
| SFR-001 | Google 인증·개인 자료 권한 | auth | auth, common/security, 각 기능의 소유권 검증 |
| SFR-002 | 광고 이미지·글 임시 등록 | listing | listing (원본·임시 상태·파일 저장 계약) |
| SFR-003 | OCR·AI 추출 | extraction | extraction (추출 정책·공급자 adapter) |
| SFR-004 | 추출값 수정·확정 | listing, extraction | listing (사용자 확정), extraction (추출값 보존) |
| SFR-005 | 건축물대장 조회 | building | building (식별·조회 상태·공공 API adapter) |
| SFR-006 | 7개 후보 대조·근거 | crosscheck | crosscheck (대상·기준별 규칙) |
| SFR-007 | 방문 체크리스트·확인 질문 | visit | visit (방문 이력·안정적 항목 ID·사진) |
| SFR-008 | 관심 매물 2~3개 비교 | comparison | comparison (기능별 조회 계약 조합) |
| SFR-009 | 매물 목록·상세·선택 삭제 | listing | listing (소유권·삭제 트랜잭션) |
| SFR-010 | 게시글·댓글·반응 | community | community (게시글과 후기 대상 댓글·반응 포함) |
| SFR-011 | 건물별 8개 항목 후기 | review | review (건물 연결·유효 점수·작성자 권한) |
| SFR-012 | 후기 통계·매물 연결 | review, listing, comparison | review (공개 점수 집계·조회 계약) |
| SFR-013 | 신고·관리자 처리 | moderation | moderation (신고·조치 이력·대상 기능에 상태 변경 요청) |
| PER-001 | 조회 성능·외부 응답 관리 | 기능별 로딩·오류 표시 | 기능별 조회 최적화·client 제한 시간, config |
| PER-002 | 공식 정보·후기 통계 Redis 캐시 | 건물 조회 시각·후기 표본 표시 | building/repository, review/repository |
| SIR-001 | 정규화·관계형 저장 | 입력값·출처 구분 | 각 기능 domain·repository/persistence 구현, resources/db/migration |
| SIR-002 | REST API 계약 | app/data 저장소 adapter | 기능별 dto·공통 오류, docs/api |

## 관계와 생명주기

- 사용자 → 개인 매물 → 여러 방문 기록·체크 항목·사진. 개인 자료 접근은 항상 소유자를 검증한다.
- 건물 → 공식 조회 자료, 공개 거주 후기 → 항목 점수. 개인 매물 삭제가 건물 후기 삭제로 이어지지 않는다.
- 광고 원본·추출값·사용자 확정값은 구분한다. 등록 식별키로 중복 생성과 외부 작업 중복을 막는다.
- 건물·층·전유부는 범위를 구분하고 주소 문자열만으로 병합하지 않는다.
- 게시글·후기 → 댓글·반응·신고. 같은 사용자·대상 반응 중복, 후기별 같은 항목 중복은 DB 제약으로도 보호한다.
- Redis에는 공식 조회와 후기 통계를 저장하며 개인 자료를 공용 키로 캐싱하지 않는다.

## 현재 화면 계약과 앞으로의 확장

[API_HANDOFF.md](../api/API_HANDOFF.md)는 현재 화면이 호출하는 계약이다. 방문 이력·업로드·확정·전체 대조·후기 서버 통계·커뮤니티를 구현할 때 기능별 요청·응답과 오류를 합의해 확장한다. 기존 프론트 계약을 구조 변경 과정에서 임의로 바꾸지 않는다. 실제 거주 인증·보상·광고 자동 크롤링은 현재 범위에서 제외한다.

## 데이터베이스 설계 연결

계획서의 관계형 저장 설계는 [MySQL 안내](../database/README.md)와 [ERD](../database/ERD.md)에 정리했다. 핵심 회원·매물·방문·후기의 7개 테이블 V1 SQL을 준비한 상태이며 서버 적용·Repository와 업무 API 구현 완료를 뜻하지 않는다.

OCR·공식 조회·대조·커뮤니티·신고·캐시·중복 요청 관련 저장 모델은 해당 기능 구현 시 새 migration으로 추가한다. 계획서의 요구사항 자체를 제외하는 것은 아니다.
