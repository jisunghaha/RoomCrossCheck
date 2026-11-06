# SFR-002·003 팀 구조 연결 준비

확인일: 2026-10-15 (Asia/Seoul). 수정·커밋 브랜치: `feature/sfr-003-gemini-extraction`.

## 보존 및 비교 기준

기존 38개 미커밋 변경은 로컬 커밋 `2f483f4`로 먼저 보존했다. API 키·.env·빌드 산출물은 제외했다. 이 커밋의 실제 Gemini 성공 기록과 smoke-002~006은 수정하거나 재호출하지 않았다.

GitHub에서 조회한 스냅샷:

| 브랜치 | SHA | 확인한 범위 |
|---|---|---|
| main | 62402fe6095af55ad1e356a2dc29a2f35828ab25 | 현재 공통 기준 |
| feature/sfr-003-gemini-extraction | 43339213814992197e5fa282c80221e0292c2f31 | 원격의 기존 구현 |
| chore/split-frontend-backend | e88efc965cfbdc3a7501eb2eff6cbbeb2074bc1e | frontend/features 및 Java 기능별 패키지 |
| feat/core-database-schema | b9797df698d51ee47555e9a520605cbe844382bd | 7개 테이블·Flyway 관리 문서 |
| feat/google-login | e3678228aef43e477cdb3581414b9fb0b8345510 | 앞의 구조·스키마를 포함한 최신 단계형 등록 화면 |

브랜치 이름을 완료 여부로 해석하지 않았다. 최신 등록 화면은 local 시연용이며 사진은 미리보기만 제공했다. 로그인 화면은 시연 상태이고 실제 인증·세션·권한은 미구현이다. DB 문서는 V1 작성·정적 검토 상태이며 실제 MySQL 적용이나 업무 Repository 완료를 주장하지 않는다.

외부 브랜치는 읽기 전용으로 조회했다. SHA 객체만 `fetch --no-write-fetch-head`로 읽었고 다른 브랜치를 checkout·merge·rebase하거나 해당 참조에 커밋·푸시하지 않았다. 이 문서의 SHA가 비교 근거이며 다른 브랜치의 코드를 일괄 병합하지 않았다.

## 이번에 연결한 부분

1. 기존 frontend 소스를 `frontend/src/app`, `features`, `shared`로 이동하고 공개 진입점을 `/src/app/main.jsx`로 맞췄다. npm workspace와 루트 호환 명령을 사용한다. 기존 브라우저 저장 키와 데이터는 유지한다.
2. 신규 등록은 팀의 `#register`·단계형 등록·RoomForm initialValues/onPreview 방식에 맞춰 단일 RegisterRoomPage로 모았다. 별도 신규 등록 모달을 병행하지 않는다. 기존 매물 수정 모달은 유지한다.
3. 직접 입력과 기존 서버 광고 원본·추출·확인 흐름이 같은 매물 입력·최종 확인 화면으로 이어진다. 직접 입력에는 이미지·원문이 필요하지 않다. 광고의 미저장 수정값이나 AI 결과를 사용자 확인 값 대신 쓰지 않는다.
4. 마지막 확정 저장 전까지 매물 POST를 하지 않는다. 실패한 최종 저장의 값은 유지하고 수정·이전 단계로 돌아갈 수 있다. 서버에 보관한 원본·AI 값·광고 확인 값은 매물 폼 수정과 별개로 보존한다.
5. Java는 listing의 controller/service/domain/repository/dto와 extraction의 client/service/dto로 이동했다. 컴포넌트 스캔·실제 저장 모델·URL·Gemini 공급자 계약은 유지한다.
6. 소스 이동 후 이전 class가 jar에 남는 것을 확인해 백엔드 빌드를 clean test package로 바꿨다. root .env의 공개 VITE 설정 호환과 frontend/.env 우선순위를 제공하며 서버 키는 브라우저 define 대상에 포함하지 않는다.

팀의 home·community·시연 로그인과 빈 기능 골격 전체를 복사하지 않았다. 이번 코드가 팀 최신 브랜치 전체의 대체본이거나 그대로 병합 가능한 결과는 아니다. 팀원 화면에 이 변경을 넣을 때는 아래 계약과 통합 diff를 함께 확인해야 한다.

## DB·API 차이와 결정할 계약

| 항목 | 현재 동작하는 개발 구현 | 팀 설계/계약 | 안전한 전환 제안 |
|---|---|---|---|
| 매물 ID | listings.id: UUID 문자열 | rooms.id: BIGINT UNSIGNED, API에서는 문자열 | Number 변환 없이 문자열 유지. 기존 UUID 자료를 옮길 때 별도 ID 매핑 계획 필요 |
| 사용자 소유권 | 인증·소유자 컬럼 없음 | rooms.user_id 필수, users FK | 서버 인증 컨텍스트에서 실제 users.id를 결정. 클라이언트 owner ID·고정 ID·가짜 로그인 금지 |
| 건물 식별 | 매물마다 만든 UUID grouping 값 | building_id는 미식별 시 NULL | UUID를 검증된 buildings.id로 간주하지 않음. 식별 전 NULL 지원과 후기·그룹 처리 계약 필요 |
| 광고 원본 | advertisements에 글·업로드 메타·비공개 파일 키 | rooms.original_text만 V1에 있음 | 원본과 추출 이력을 별도 추가 migration으로 보관. rooms에는 최종 확인 원문/값을 연결 |
| AI·확인 값 | extractionJson과 reviewedJson을 별도 보존 | V1에 별도 추출 작업 테이블 없음 | 공급자·모델·프롬프트 버전·상태·원본·확인값·시각을 저장할 모델 추가 합의 |
| 매물 등록 | POST /api/listings, 수정 PATCH | POST /api/rooms, 수정 PUT 계약 | 기존 경로 유지 후 새 endpoint/DTO를 별도 구현·검증하고 adapter 전환 |
| 상태 | 광고 DRAFT/EXTRACTION_PENDING/READY/FAILED/REVIEWED | rooms DRAFT/CONFIRMED | 광고 추출 상태와 매물 등록 상태를 별도 필드·생명주기로 유지 |
| 면적 종류 | 전용/공급/미상 | DB EXCLUSIVE/SUPPLY/UNKNOWN, DTO 한글 | DB↔DTO 변환은 서버 DTO 경계에서 수행 |
| 면적 정밀도 | DECIMAL(10,2)와 현재 폼 | 팀 DECIMAL(10,4) | 허용 정밀도·반올림 규칙·폼 step을 함께 결정 |
| 길이·범위 | 이름60·주소160·원문10000·층 -20~200 | 팀 이름200·주소500·원문MEDIUMTEXT·SMALLINT | DB 허용폭과 API 정책을 구분해 공통 DTO 검증 범위 확정 |
| DDL 관리 | 로컬 JPA ddl-auto=update | Flyway V1 7개 테이블 | V1 변경·자동 baseline 없이 합의 후 V2 이상과 실제 테스트 DB 검증 |

현재 API 어댑터와 팀의 기존 어댑터 모두 building을 문자열로 검증한다. 팀 문서에서 요구한 building=NULL은 아직 adapter·화면까지 구현된 계약이 아니다. DB 전환 때 조회·후기·비교에서 미식별 건물을 한 건물로 합치지 않도록 함께 다뤄야 한다.

## 팀에 제안할 결정 항목

1. **원본 소유자와 생성 시점:** 광고 업로드부터 서버 인증 사용자에게 귀속할지, 추후 연결 정책이 있는지 결정한다. 현재는 로그인 미구현이므로 광고를 임의 users.id에 연결하지 않는다. 인증 미연결 상황의 거부 상태·오류 메시지도 정한다.
2. **추가 migration 모델:** 예를 들어 광고 원본/파일 테이블과 추출 작업 테이블을 V2 이상으로 추가하고 room_id는 BIGINT FK로 연결한다. 원본 ID의 UUID 유지 여부·추출 이력 횟수·첨부 저장·보존/삭제 정책을 합의한다. 이 제안 SQL을 실행하거나 V1을 복사·수정하지 않았다.
3. **최종 확정 계약:** POST /api/rooms에 확인한 광고 ID를 받을지 별도 confirm endpoint를 둘지 정한다. 서버가 실제 소유권과 REVIEWED 상태를 검증하고, room 저장과 연결을 같은 트랜잭션으로 처리해야 한다. 같은 광고의 중복 최종 등록에 대한 멱등성 키/제약과 응답을 정한다.
4. **확정값 의미:** 광고 사용자 확인 값과 이후 매물 폼 수정값을 모두 보존할지, 확정 시 reviewed를 갱신할지 합의한다. 현재 구현은 각각 별개이며 AI 원본을 덮어쓰지 않는다.
5. **전환 자료:** 기존 listings UUID·광고 자료를 이관할지 개발 데이터로 별도 보존할지 결정한다. 소유자가 없는 자료를 임의 사용자에게 귀속시키지 않는다. 기존 DB에 V1을 재실행하거나 삭제하지 않는다.

이 결정을 위해 지금 다른 브랜치나 main을 수정할 필요는 없다. 계약이 정해지면 SFR-003 브랜치에서 adapter·추가 migration·테스트를 준비하고, 팀 확인 이후 별도 승인된 통합 절차를 진행한다.

## 검증과 남은 범위

새 구조에서 React 테스트·빌드, Java clean 테스트·패키징, 실제 MySQL·Chromium 흐름을 검증한다. 입력→미리보기에서는 저장하지 않고, 최종 저장·새로고침과 광고 AI/확인/매물 수정값 분리를 확인한다. 외부 Gemini 생성 요청은 0건이며 기존 smoke-002~006을 재사용하지 않는다. 실제 Gemini 성공 근거는 기존 005/006 기록이다.

검증 결과: React 24건·Spring Boot 6건·Python 6건 통과, npm ci·React 빌드·Java clean package 성공. Chromium에서 #register → 광고 선택·원본 저장·확정·로컬 추출 → 광고 확인 값 430000 저장 → 공용 폼 44만원으로 수정 → 최종 확인 전 DB 매물 0건 → 확정 저장 → 새로고침·MySQL 재조회 rent=440000을 확인했다. 광고 추출 원본 420000·광고 확인 값 430000은 그대로 보존됐다. 보증금 null·관리비 0·지하 -2층·소수 면적·공식 미조회 상태를 유지했다. 단계별 뒤로 이동·실패 입력 보존·직접 입력의 원문 선택도 React 테스트로 확인했다. 테스트 스택은 정리했다.

Java 패키지 이동 직후 이전 빌드 산출물이 남는 문제를 찾아 clean 빌드로 제거했고, 최종 jar의 이전 advertisement 패키지 클래스 수는 0이다. npm workspace 의존성 설치 후 새 환경에서 테스트를 다시 실행했다. [브라우저 결과](browser-flow-result.json)는 이 마지막 통과 실행 기준이다.

20건 평가 자료는 0/20이고, 이미지 OCR·실제 Gemini가 포함된 화면 전체 흐름은 미검증이다. 팀 DB 전환·소유권·영구 광고-매물 연결·중복 최종 등록 방지는 위 계약 합의 후 작업이다. 테스트용 추출 서버의 결과를 실제 Gemini 정확도로 계산하지 않는다.

사용자 작업 규칙: main·다른 브랜치 수정, 병합·재배치, 푸시·PR·팀 리뷰 요청 금지. 추론 수준이나 모델을 높일 필요가 있다고 판단되면 사용자에게 먼저 알리고 임의로 변경하지 않는다. 이번 작업에서는 변경 필요성을 판단하지 않았으며 임의 전환하지 않았다.
