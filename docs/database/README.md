# RoomCrossCheck 핵심 DB 설계

MySQL 8.4용 **7개 테이블**로 시작한다. 현재 스키마 초안만 작성했으며 실제 DB 서버 적용·Spring Boot 연결은 하지 않았다.

| 테이블 | 역할 |
|---|---|
| users | 회원·Google 로그인 식별자 |
| buildings | 확인한 건물 정보 |
| rooms | 개인 매물·광고 원문·확정값 |
| visits | 매물별 방문 이력·메모 |
| visit_check_items | 기본·사용자 추가 체크 항목 |
| reviews | 건물별 공개 후기 |
| review_scores | 후기의 8개 항목 점수 |

광고 원문은 rooms.original_text, Google 식별자는 users.google_id에 둔다. 옵션·태그는 작은 문자열 목록 JSON으로 매물에 함께 저장하고 Service가 배열 형식을 검증한다. 기본 점검 항목과 후기 항목은 [항목 정의](REFERENCE_VALUES.md)에 정리하며 별도 정의 테이블이나 seed migration을 만들지 않는다.

## 자료

- [ERD](ERD.md)
- [컬럼 설명](SCHEMA.md)
- [DB 생성 SQL](create_database.sql)
- [테이블 생성 SQL](../../backend/src/main/resources/db/migration/V1__initial_schema.sql)
- [조회 예시](queries.sql)
- [별도 테스트 DB용 검증 SQL](../../backend/src/test/resources/db/verify_schema.sql)

## 생성 순서

저장소 루트에서 설치된 MySQL CLI를 실행하거나 Workbench로 파일을 연다.

```powershell
mysql -h localhost -u <사용자명> -p --default-character-set=utf8mb4
```

MySQL 프롬프트:

```sql
SOURCE docs/database/create_database.sql;
SOURCE backend/src/main/resources/db/migration/V1__initial_schema.sql;
SHOW TABLES;
```

빈 DB에 V1을 한 번 적용한다. V2 기본 항목 SQL은 사용하지 않는다. 이미 다른 버전의 스키마를 적용한 DB에는 이 V1을 재실행하지 말고 기존 자료를 보존할 변경 migration을 별도로 만든다. 실제 서버·계정·비밀번호는 아직 생성하지 않았다. Flyway를 도입하기 전까지 앱 시작 시 SQL은 자동 실행되지 않는다.

## 유지하는 규칙

- 개인 매물과 방문은 소유자만 접근하며 서버에서 권한을 확인한다.
- 매물 삭제 시 visits와 visit_check_items를 CASCADE 삭제한다. 건물·공개 후기는 남는다.
- 후기는 건물에 연결하며 PUBLIC만 조회·통계에 포함한다. 숨김·삭제는 visibility 변경으로 처리한다.
- 점수는 1~5 또는 NULL이며 같은 후기·항목은 한 번만 저장한다. 미응답은 평균 분모에서 제외한다.
- 금액은 원, 면적은 ㎡다. NULL과 0을 구분한다. 금액의 JS 안전 정수 범위는 Service에서 검증한다.
- ID는 API에서 문자열로 반환한다. 시각은 UTC로 저장하고 화면에서 한국 시간으로 표시한다. 앱의 모든 DB 연결에 UTC 설정을 적용한다.
- 건물은 주소 문자열만으로 자동 병합하지 않는다. 식별 전 rooms.building_id는 NULL이며 후기는 확인한 건물 ID를 필수로 받는다.
- 체크 항목의 item_key는 배열 순서가 아니다. 기본 항목은 고정 key, 추가 항목은 UUID 등 고유 key를 사용하고 label은 당시 내용을 보존한다.

## 후속 구현

OCR 추출 작업·이미지/사진 파일·공식 조회 상태와 대조 근거·커뮤니티·댓글·반응·신고·요청 중복 방지는 해당 기능을 구현할 때 추가 migration으로 확장한다. 계획서에서 제외한 기능은 아니며 현재 DB의 구현 범위에서 미룬 것이다. Redis 연결과 실제 소유권·상태 전이·후기 통계 캐시 무효화도 후속 작업이다.

현재 화면의 금액·주소·원문·후기를 우선 연결한다. building=NULL 지원과 방문 이력 API는 [API 계약](../api/API_HANDOFF.md)을 함께 확장한다. official 결과는 조회 기능 구현 전에는 null로 반환한다. 별도 비교 테이블은 화면의 2~3개 선택 상태로 대체한다.

## 검증 범위

테이블·FK 생성 순서, 점수·중복 제약, 개인 자료와 공개 후기의 삭제 관계, 문서 링크를 정적으로 확인했다. 실제 MySQL DDL 실행과 검증 SQL 실행은 DB 서버가 준비된 후 진행한다. 검증 SQL은 별도 빈 테스트 DB에 V1을 적용한 다음 실행하며 테스트 자료는 ROLLBACK한다.
