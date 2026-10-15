# DB migration

`V1__initial_schema.sql`은 MySQL 8.4 핵심 7개 테이블의 기준이다. 테이블 관계와 기존 SQL은 유지한다.

`backend`에서 `mvn -Pdb flyway:migrate`를 실행하면 적용되지 않은 버전만 순서대로 실행하고 `flyway_schema_history`에 기록한다. 접속 환경변수와 DB 생성 순서는 [DB 안내](../../../../../../docs/database/README.md)를 참조한다.

Flyway는 Maven의 별도 `db` 프로필에만 있다. 일반 Spring Boot 실행에는 DB 의존성을 추가하지 않으며 자동 migration도 수행하지 않는다. 로그인·업무 저장소 구현은 이후 작업이다.

적용한 V1은 주석을 포함해 수정하지 않는다. 이후 구조 변경은 V2, V3 등 새 migration으로 작성한다. 기존 DB에 수동으로 V1을 적용했다면 곧바로 migrate하지 말고 스키마와 이력을 확인한다. 자동 baseline과 clean은 사용하지 않는다.
