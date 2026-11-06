# 개발용 DB 변경 SQL

## 광고 확인값 버전·수정 이력 추가

`add_advertisement_review_history.sql`은 개발용 advertisements 전용이다. 공유/운영 DB나 팀 rooms·Flyway V1에는 적용하지 않는다.
앱의 JPA update도 스키마를 추가하므로 기존 DB는 앱 실행 전에 대상·백업·복구 가능성을 먼저 확인한다.

1. `SHOW COLUMNS FROM advertisements LIKE 'review_version';`과 `SHOW TABLES LIKE 'advertisement_reviews';`로 기존 구조를 확인한다.
2. 둘 다 없을 때만 승인된 개발 DB에 SQL 전체를 적용한다. 이미 둘 다 있으면 구조와 FK·유일 제약을 확인하고 재적용하지 않는다. 일부만 있거나 타입이 다르면 중단하고 검토한다.
3. 기존 광고 행 수·원본·추출 JSON·확인 JSON·저장 시각을 전후 비교한다. 기존 광고의 버전은 0이며 기존 JSON은 갱신하지 않는다.
4. MySQL DDL은 암묵적으로 커밋된다. 코드 복구 시 새 컬럼·이력을 삭제하지 말고 보존한다. 데이터 삭제를 되돌리기 절차로 사용하지 않는다.

이력 API와 이전 클라이언트 호환성은 [확인값 수정 계약](../REVIEW_HISTORY.md)을 따른다.

## listings 옵션 컬럼

이 폴더의 파일은 검토 가능한 개발용 SQL이며 자동 실행 또는 팀 Flyway migration이 아니다. 팀 rooms·V1 SQL을 변경하지 않는다. 이번 검증은 별도 임시 MySQL에서만 수행했고 공유 DB에는 적용하지 않았다.

## 적용 전

1. 접속 대상과 DB 이름을 확인하고 실제 인증·권한이 없는 개발용 listings DB인지 확인한다. 공유·운영 DB에 실행하지 않는다.
2. 기존 DB와 스키마를 백업하고 복구 가능한지 확인한다. MySQL ALTER TABLE은 DDL의 암묵적 커밋이 있으므로 트랜잭션 롤백에 의존하지 않는다.
3. SHOW COLUMNS FROM listings LIKE 'options';로 컬럼 유무·타입을 확인한다. 로컬 JPA update로 이미 JSON 컬럼이 추가됐다면 이 SQL은 실행하지 않는다. 기존 타입이 다르면 자동 변환하지 말고 검토한다.
4. 컬럼이 없는 승인된 대상에서만 add_listing_options.sql을 실행한다. 잠금·실행 시간을 고려하고 적용 전후 행 수·기존 값과 새 컬럼의 null 상태를 비교한다.

## 보존과 복구

새 컬럼은 nullable이며 기존 행을 빈 배열로 채우지 않는다. null은 미확인이고 빈 배열은 옵션 없음이다. 코드만 이전 버전으로 되돌릴 때는 새 컬럼을 그대로 두어 옵션 데이터를 보존한다. 컬럼 삭제를 자동 복구로 실행하지 않는다. DB 복원이 필요하면 추가 입력 유실까지 고려한 별도 승인과 복원 절차를 따른다.

## 면적 컬럼을 DECIMAL(10,4)로 변경할 때

upgrade_listing_area_precision.sql은 현재 개발용 listings 전용이며 공유/운영 DB나 팀 rooms·Flyway V1에 실행하지 않는다. 앱의 로컬 ddl-auto=update도 스키마를 바꿀 수 있으므로 기존 자료가 있는 DB는 앱 실행보다 먼저 확인한다.

1. 대상 DB·컬럼 타입·백업·복구 가능성을 확인한다. SHOW COLUMNS FROM listings LIKE 'area';로 현재 정밀도를 조회한다.
2. 다음 조회로 새 DB 표현 범위를 넘는 기존 값을 확인한다. 결과가 있으면 이행을 중단하고 원본과 대조한 처리 방안을 별도로 승인받는다. 임의로 자르거나 삭제하지 않는다.

```sql
SELECT id, area FROM listings WHERE ABS(area) > 999999.9999;
```

3. 업무 입력 범위 밖의 값도 별도로 검토한다. DB에 표현 가능하더라도 새 API는 0~100000㎡만 받는다. 기존 값은 자동 수정하지 않는다.

```sql
SELECT id, area FROM listings WHERE area < 0 OR area > 100000;
```

4. 소수 둘째 자리였던 정상 값이 변환 후 같음을 확인할 수 있도록 적용 전 행 수와 기존 값을 보관한다. 승인된 개발 DB에서만 SQL을 실행한다. SQL은 현재 세션을 엄격 모드로 만든 뒤 ALTER TABLE을 수행하므로 범위 초과를 조용히 잘라내지 않는다.
5. 적용 후 DECIMAL(10,4)·기존 값 보존·새 3/4자리 값의 POST/GET 일치를 확인한다. 이미 10,4면 재적용할 필요가 없다. MySQL DDL은 암묵적 커밋이므로 트랜잭션 롤백에 의존하지 않는다.

이전 코드로 되돌리더라도 컬럼을 10,2로 자동 축소하지 않는다. 새로 저장한 자릿수가 사라질 수 있다. 이미 과거에 반올림된 값은 컬럼 변경으로 복원되지 않으므로 광고/확인 원본을 근거로 별도 승인된 복구를 수행한다.
