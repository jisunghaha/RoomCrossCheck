# RoomCrossCheck 백엔드

Java 21 · Spring Boot · Maven 기본 골격이다. 업무 API, Google OAuth 로그인, MySQL, Redis, OCR·공공 API 구현은 앞으로 추가한다. 서버가 시작돼도 현재 프론트의 API 모드와 연결할 업무 엔드포인트는 없다.

## 실행과 검증

Java 21과 Maven 3.9 이상이 필요하다. Maven Wrapper는 아직 포함하지 않았다.

```powershell
cd backend
mvn test
mvn package
mvn spring-boot:run
```

VS Code에서 `RoomCrossCheckApplication.java`의 main을 실행해도 된다. 주소는 `127.0.0.1:8080`이며 `SERVER_PORT` 환경변수로 포트를 바꿀 수 있다. `RoomCrossCheckApplicationTests`는 외부 서비스 없이 Spring context가 시작되는지 검증한다. 공개 업무 경로는 아직 없으므로 브라우저의 접근 거부 응답은 서버 시작 실패를 뜻하지 않는다.

## 구현 위치

```text
src/main/java/com/roomcrosscheck/
├── RoomCrossCheckApplication.java
├── config/SecurityConfiguration.java
├── common/{error,security}/
└── {auth,listing,extraction,building,crosscheck,visit,comparison,community,review,moderation}/
    ├── controller/       # HTTP 요청 처리·Service 위임
    ├── service/          # 작업 흐름·권한·트랜잭션
    ├── domain/           # 객체의 불변조건·업무 규칙
    ├── repository/       # 저장·조회 계약과 구현
    ├── dto/              # HTTP 요청·응답 데이터
    └── client/           # auth, building, extraction의 외부 API 연동
```

모든 기능에는 controller/service/domain/repository/dto 패키지가 있다. 외부 API가 필요한 auth(로그인), building(건축물대장), extraction(OCR·AI)에만 client 패키지를 준비했다. `package-info.java`는 패키지 책임과 요구사항을 설명하며 실제 업무 클래스는 기능 구현 시 추가한다.

Controller → Service → Domain 방향으로 책임을 나누고 Service가 필요한 Repository·Client 인터페이스를 생성자로 주입받는다. 인터페이스는 해당 기능의 repository/client에 두고 기술 구현은 필요한 시점에 그 아래 persistence/cache/storage 또는 공급자별 하위 패키지에 둔다. Spring Data Repository나 공급자 SDK에 Service를 직접 결합하지 않는다.

Domain 객체가 자기 데이터의 유효성·상태 전이를 검증한다. Service는 소유권과 작업 흐름을 조정하며, Controller는 HTTP 변환을 맡는다. DTO는 요청과 응답을 구분한다. JPA를 연결할 때 저장 엔티티는 repository/persistence에 두고 순수 domain과 분리한다. 기능 사이에서는 다른 기능의 Service 조회 계약을 사용하며 DB Repository를 직접 공유하지 않는다.

## 설정과 외부 연동

기본 서버 의존성은 웹·입력 검증·보안·테스트다. Maven의 `db` 프로필에만 Flyway와 MySQL 드라이버를 두어 스키마를 별도 명령으로 관리한다. `mvn -Pdb flyway:migrate` 사용법은 [DB 안내](../docs/database/README.md)를 참조한다. 일반 앱 실행은 DB에 접속하지 않는다. JPA·로그인·Redis 등 업무 의존성은 해당 기능 개발 시 추가한다.

`SecurityConfiguration`은 모든 요청을 거부하고 CSRF 기본 보호를 유지한다. SFR-001 구현 시 공지·공개 게시글·공개 후기 열람 경로를 허용하고 개인 자료 API는 인증·소유권을 검증한다. 관리자 기능은 별도 역할을 확인한다. 세션·CORS·CSRF 계약을 프론트와 합의한다.

DB·Redis·Google·외부 API 비밀키는 서버 환경변수로 읽는다. `.env`는 Spring Boot가 자동으로 읽지 않으므로 쉘이나 실행 구성에 서버 변수를 설정한다. 개인 자료나 외부 서비스 실제 데이터를 시작 테스트의 전제 조건으로 만들지 않는다.

- [GRASP·SOLID와 의존 방향](../docs/architecture/ARCHITECTURE.md)
- [요구사항별 기능 위치](../docs/architecture/REQUIREMENTS.md)
- [현재 프론트 API 계약](../docs/api/API_HANDOFF.md)

## 데이터베이스 설계

[MySQL 설계 안내](../docs/database/README.md)에 ERD·데이터 사전·Flyway 적용 순서가 있다. 기존 외부 서비스 없는 시작 테스트는 유지한다. SQL 자체를 검증할 때는 별도 테스트 DB에 Flyway로 V1을 적용하고 `src/test/resources/db/verify_schema.sql`을 실행한다. 이 SQL 검증은 Maven 시작 테스트에서 자동 실행되지 않는다.
