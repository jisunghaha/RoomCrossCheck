# DB migration

`V1__initial_schema.sql`은 MySQL 8.4 핵심 7개 테이블 생성 SQL이다. 기본 항목 정의 테이블과 V2 seed는 사용하지 않는다.

[실행 안내](../../../../../../docs/database/README.md), [ERD](../../../../../../docs/database/ERD.md), [컬럼 설명](../../../../../../docs/database/SCHEMA.md)을 참조한다.

아직 실제 서버에 적용하지 않은 초안이다. 빈 DB에 한 번 적용하고 이후 변경은 새 migration으로 추가한다. 이미 이전 스키마를 적용한 DB는 별도 변경 SQL이 필요하다. 현재 Spring Boot에 Flyway/JPA 연결이 없어 앱 시작 시 자동 실행되지 않는다.
