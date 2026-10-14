# 핵심 7개 테이블 컬럼 설명

[V1 SQL](../../backend/src/main/resources/db/migration/V1__initial_schema.sql)이 물리 정의의 기준이다. 식별자는 BIGINT UNSIGNED, 문자셋은 utf8mb4, 시각은 UTC다.

## users

| 컬럼 | 타입 | NULL 허용 | 의미 |
|---|---|---|---|
| `id` | `BIGINT UNSIGNED` | 아니오 | 고유 ID, API에서 문자열로 반환 |
| `google_id` | `VARCHAR(255)` | 아니오 | Google sub, 중복 불가·대소문자 구분 |
| `name` | `VARCHAR(100)` | 아니오 | 회원/건물/매물 표시 이름 |
| `email` | `VARCHAR(254)` | 예 | 이메일, Google ID와 별개 |
| `role` | `ENUM('USER', 'ADMIN')` | 아니오 | USER 또는 ADMIN |
| `created_at` | `DATETIME` | 아니오 | UTC 생성 시각 |
| `updated_at` | `DATETIME` | 아니오 | UTC 수정 시각 |

## buildings

| 컬럼 | 타입 | NULL 허용 | 의미 |
|---|---|---|---|
| `id` | `BIGINT UNSIGNED` | 아니오 | 고유 ID, API에서 문자열로 반환 |
| `official_key` | `VARCHAR(100)` | 예 | 확인한 공공 건물키, 없으면 NULL |
| `name` | `VARCHAR(200)` | 예 | 회원/건물/매물 표시 이름 |
| `address` | `VARCHAR(500)` | 아니오 | 주소 |
| `building_dong` | `VARCHAR(100)` | 예 | 건물 동 |
| `created_at` | `DATETIME` | 아니오 | UTC 생성 시각 |

## rooms

| 컬럼 | 타입 | NULL 허용 | 의미 |
|---|---|---|---|
| `id` | `BIGINT UNSIGNED` | 아니오 | 고유 ID, API에서 문자열로 반환 |
| `user_id` | `BIGINT UNSIGNED` | 아니오 | 소유자/작성자 회원 FK |
| `building_id` | `BIGINT UNSIGNED` | 예 | 건물 FK, 매물은 미식별 시 NULL 가능 |
| `name` | `VARCHAR(200)` | 아니오 | 회원/건물/매물 표시 이름 |
| `address` | `VARCHAR(500)` | 아니오 | 주소 |
| `unit_number` | `VARCHAR(100)` | 예 | 호수 |
| `status` | `ENUM('DRAFT', 'CONFIRMED')` | 아니오 | DRAFT 또는 CONFIRMED |
| `original_text` | `MEDIUMTEXT` | 예 | 광고 원문, 확정 입력값과 구분 |
| `deposit` | `BIGINT` | 예 | 보증금, 원 단위·미입력 NULL |
| `rent` | `BIGINT` | 예 | 월세, 원 단위·미입력 NULL |
| `fee` | `BIGINT` | 예 | 관리비, 원 단위·0과 NULL 구분 |
| `area` | `DECIMAL(10,4)` | 예 | 면적 ㎡, 소수 4자리 |
| `area_type` | `ENUM('EXCLUSIVE', 'SUPPLY', 'UNKNOWN')` | 아니오 | EXCLUSIVE / SUPPLY / UNKNOWN |
| `floor` | `SMALLINT` | 예 | 호실 층수, 지하는 음수 가능 |
| `tags` | `JSON` | 예 | 표시 태그 문자열 목록 JSON |
| `options` | `JSON` | 예 | 옵션 문자열 목록 JSON |
| `theme` | `ENUM('sage', 'sand', 'clay')` | 아니오 | sage / sand / clay |
| `created_at` | `DATETIME` | 아니오 | UTC 생성 시각 |
| `updated_at` | `DATETIME` | 아니오 | UTC 수정 시각 |

## visits

| 컬럼 | 타입 | NULL 허용 | 의미 |
|---|---|---|---|
| `id` | `BIGINT UNSIGNED` | 아니오 | 고유 ID, API에서 문자열로 반환 |
| `room_id` | `BIGINT UNSIGNED` | 아니오 | 개인 매물 FK |
| `visited_at` | `DATETIME` | 아니오 | UTC 방문 시각 |
| `note` | `TEXT` | 예 | 개인 방문/항목 메모 |
| `created_at` | `DATETIME` | 아니오 | UTC 생성 시각 |
| `updated_at` | `DATETIME` | 아니오 | UTC 수정 시각 |

## visit_check_items

| 컬럼 | 타입 | NULL 허용 | 의미 |
|---|---|---|---|
| `id` | `BIGINT UNSIGNED` | 아니오 | 고유 ID, API에서 문자열로 반환 |
| `visit_id` | `BIGINT UNSIGNED` | 아니오 | 방문 FK |
| `item_key` | `VARCHAR(50)` | 아니오 | 방문 내 고유 항목 key, 표시 순서와 별개 |
| `label` | `VARCHAR(200)` | 아니오 | 방문 당시 항목 이름 |
| `checked` | `BOOLEAN` | 아니오 | 확인 완료 여부 0/1 |
| `note` | `TEXT` | 예 | 개인 방문/항목 메모 |
| `sort_order` | `INT UNSIGNED` | 아니오 | 표시 순서 |

## reviews

| 컬럼 | 타입 | NULL 허용 | 의미 |
|---|---|---|---|
| `id` | `BIGINT UNSIGNED` | 아니오 | 고유 ID, API에서 문자열로 반환 |
| `user_id` | `BIGINT UNSIGNED` | 아니오 | 소유자/작성자 회원 FK |
| `building_id` | `BIGINT UNSIGNED` | 아니오 | 건물 FK, 매물은 미식별 시 NULL 가능 |
| `period` | `VARCHAR(100)` | 아니오 | 거주 시기 자유 형식 문자열 |
| `body` | `TEXT` | 아니오 | 후기 본문 |
| `visibility` | `ENUM('PUBLIC', 'HIDDEN', 'DELETED')` | 아니오 | PUBLIC / HIDDEN / DELETED |
| `created_at` | `DATETIME` | 아니오 | UTC 생성 시각 |
| `updated_at` | `DATETIME` | 아니오 | UTC 수정 시각 |

## review_scores

| 컬럼 | 타입 | NULL 허용 | 의미 |
|---|---|---|---|
| `review_id` | `BIGINT UNSIGNED` | 아니오 | 후기 FK |
| `category` | `VARCHAR(20)` | 아니오 | 방음·수압·채광·환기·주차·관리·벌레·결로 |
| `score` | `TINYINT` | 예 | 1~5 또는 미응답 NULL |

## 주요 제약

회원 Google ID, 건물 official_key, 방문별 item_key, 후기별 category의 중복을 막는다. 점수는 1~5 또는 NULL, 금액·면적은 음수가 아니다. 매물 삭제는 방문·항목만 연쇄 삭제하며 후기는 건물에 별도로 연결한다. 소유권·관리자 역할·JSON 목록 형식·금액 안전 정수 범위는 Service에서 검증한다.
