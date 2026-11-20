# 회원 데이터·후기·신고 API

SFR-001 권한 보완, SFR-009~011 방문·비교, SFR-017~019 후기·통계·신고, PER-002 통계 캐싱의 서버 계약입니다. 프론트는 `VITE_DATA_MODE=listing-api`에서 이 API를 사용합니다.

## 공통 규칙

- 모든 `/api/**` 요청은 Spring Security를 거칩니다. 회원 식별은 서버 세션(`JSESSIONID`)만 사용하고, 요청 본문·헤더의 회원 번호나 권한은 무시합니다.
- GET이 아닌 요청은 `X-XSRF-TOKEN` 헤더가 필요합니다. 토큰은 `GET /api/auth/csrf`가 내려 주는 `XSRF-TOKEN` 쿠키 값입니다. 프론트의 `shared/lib/http.js`가 자동으로 붙입니다.
- 비로그인은 401, 권한 부족은 403입니다. 다른 회원의 매물·광고는 존재 여부를 숨기기 위해 404로 응답합니다.
- 오류 본문은 `{"message": "..."}` 형식입니다.

| 경로 | 비로그인 | 회원 | 관리자 |
|---|---|---|---|
| `GET /api/community/**`, `GET /api/reviews` | 조회 가능 | 조회 가능 | 조회 가능 |
| 매물·광고·건축물대장·방문 기록·체크리스트·비교 선택 | 401 | 본인 것만 | 본인 것만 |
| 후기 작성·삭제, 신고 접수 | 401 | 가능 (삭제는 본인 후기) | 가능 (모든 후기 삭제) |
| `/api/admin/**` | 401 | 403 | 가능 |

## 매물·광고 소유권 (SFR-001·002·008)

`listings.owner_id`, `advertisements.owner_id`에 등록한 회원 번호를 저장합니다. 응답에는 포함하지 않습니다.

- `GET /api/listings`: 본인 매물만 최신순으로 반환합니다.
- `GET/PATCH/DELETE /api/listings/{id}`, `POST /api/listings/{id}/official/search`, `PUT /api/listings/{id}/official`: 본인 매물이 아니면 404입니다. 건축물대장 외부 호출 전에 확인합니다.
- `DELETE /api/listings/{id}`: 그 매물의 방문 기록도 함께 삭제합니다. 공개 후기는 남습니다.
- `/api/advertisements/**`: 본인 광고만 조회·확정·추출·수정합니다. Gemini 호출 전에 확인합니다. 다른 회원이 이미 쓴 `Idempotency-Key`를 재사용하면 409입니다.
- 회원 연결 전에 만든 기존 매물·광고는 `owner_id`가 비어 있어 어느 회원에게도 보이지 않습니다. 필요하면 DB에서 소유자를 직접 지정합니다.

### 건물 키

`listings.building`은 같은 건물의 후기를 묶는 서버 생성 키입니다.

- 건축물대장 건물을 저장한 매물: `ledger:<건축물대장 PK>`
- 그 밖: `addr:` + 괄호 참고주소·공백·끝의 숫자/영문 동·층·호를 지운 주소
  - 예: `서울특별시 강남구 대치동 316 은마아파트 26동 808호` → `addr:서울특별시강남구대치동316은마아파트`
- 주소 표기가 다르면 같은 건물도 다른 키가 될 수 있습니다. 공식 조회를 저장하면 공식 건물 ID 기준으로 바뀝니다.
- 이번 변경 전에 만든 매물은 무작위 키를 유지하다가 수정하거나 공식 정보를 저장할 때 새 규칙으로 바뀝니다.

## 방문 기록 (SFR-010)

`visit_records` 테이블, 매물당 한 건입니다.

- `GET /api/visits` → `{ "<매물 ID>": { "checks": {"0": true, "c-1a2b": false}, "note": "", "visitedOn": "2026-11-20", "updatedAt": "..." } }`
- `PUT /api/listings/{id}/visit` 본문 `{ "checks": {...}, "note": "...", "visitedOn": "2026-11-20" | null }`
  - `checks` 키는 체크리스트 항목 ID(영문·숫자·하이픈 1~40자), 최대 50개
  - `note` 최대 2000자

## 체크리스트 구성 (SFR-009)

`member_preferences.checklist`에 회원별로 저장합니다.

- `GET /api/visit-checklist` → `{ "items": null }`이면 화면의 기본 9개 항목을 씁니다.
- `PUT /api/visit-checklist` 본문 `{ "items": [{ "id": "0", "label": "방음", "tip": "복도와 이웃집 소음" }, ...] }`
  - 1~30개, 이름 최대 30자, 안내 최대 80자, ID 중복 시 400
  - `{ "items": null }`이면 기본 항목으로 되돌립니다.
- 기본 항목 ID는 `"0"`~`"8"`로 이전 브라우저 기록의 체크 순번과 같습니다. 항목을 지워도 과거 체크 값은 남고 화면에서만 숨깁니다.

## 비교 선택 (SFR-011)

`member_preferences.comparison_listing_ids`에 저장합니다.

- `GET /api/comparison` → `{ "listingIds": [...] }` (삭제된 매물은 빼고 반환)
- `PUT /api/comparison` 본문 `{ "listingIds": [...] }`: 서로 다른 본인 매물 0~3개, 아니면 400

## 거주 후기·통계 (SFR-017·018, PER-002)

`building_reviews` 테이블입니다.

- `GET /api/reviews?building=<키>&building=<키>` (최대 50개, 비로그인 가능)
  - 응답 `{ "reviews": [...], "stats": { "<키>": { "count": 3, "items": [{ "label": "방음", "count": 2, "value": 3.5 }, ...] } } }`
  - 후기에는 작성자 번호 대신 요청자 본인 여부 `mine`만 담습니다. `buildingLabel`은 끝의 동·층·호를 지운 공개 주소입니다.
- `POST /api/reviews` 본문 `{ "building", "period", "body", "scores": { "방음": 1~5 | null, ... } }`
  - 본인이 저장한 매물의 건물 키만 허용합니다. 아니면 404입니다.
  - 평가 항목은 방음·수압·채광·환기·주차·관리·벌레·결로이며, 다른 항목이나 범위 밖 점수는 400입니다.
- `DELETE /api/reviews/{id}`: 작성자 또는 관리자만 삭제할 수 있습니다.
- 통계는 서버 메모리에 건물별로 10분 캐시합니다. 작성·삭제 시 해당 건물 캐시를 즉시 지웁니다. 서버를 재시작하면 다시 계산합니다.
- 시연 후기(`review-demo:` ID)는 서버에 저장하지 않고 브라우저에만 둡니다. 시연 후기가 있는 건물은 화면이 통계를 직접 계산합니다.

## 신고·관리자 처리 (SFR-019)

`reports` 테이블입니다. 한 회원은 같은 대상을 한 번만 신고할 수 있습니다.

- `POST /api/reports` 본문 `{ "targetType": "POST"|"COMMENT"|"REVIEW", "targetId", "postId"(댓글일 때), "reason": "SPAM"|"ABUSE"|"PRIVACY"|"FALSE_INFO"|"OTHER", "detail" }`
  - 본인 글 신고 400, `OTHER`인데 설명이 없으면 400, 대상 없음 404, 중복 409
  - 미리보기(200자)와 대상 작성자 번호는 서버가 대상에서 직접 읽어 저장합니다.
- `GET /api/admin/reports?status=PENDING|ACCEPTED|DISMISSED|ALL&page=0&size=20`: 관리자 목록, 같은 대상 신고 수·대상 존재 여부 포함
- `POST /api/admin/reports/{id}/resolve` 본문 `{ "action": "DELETE_TARGET"|"DISMISS", "note" }`
  - `DELETE_TARGET`: 대상을 기존 삭제 규칙(게시글의 댓글·좋아요 연쇄 삭제, 후기 통계 캐시 갱신)으로 지우고 같은 대상의 대기 신고를 모두 `ACCEPTED`로 닫습니다.
  - `DISMISS`: 같은 대상의 대기 신고를 모두 `DISMISSED`로 닫습니다.
  - 이미 처리된 신고는 409입니다.
- 관리자 계정을 만드는 API는 없습니다. DB에서 `users.role`을 `ADMIN`으로 바꿉니다.

## 스키마

로컬·운영 모두 JPA `ddl-auto=update`로 새 테이블(`visit_records`, `member_preferences`, `building_reviews`, `reports`)과 `owner_id` 컬럼을 추가합니다. 기존 데이터는 지우지 않습니다. 팀 Flyway DB로 옮길 때는 별도 마이그레이션이 필요합니다.
