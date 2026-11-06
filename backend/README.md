# 백엔드 현재 구현 범위

Java 21·Spring Boot 4.1.1·MySQL 8.4 로컬 개발 API입니다. 루트의 `scripts/build-backend.sh`가 Maven clean test package를 실행하고 `scripts/dev-up.sh`가 DB와 백엔드를 기동합니다. 공개 CA·프록시는 런타임에서 적용합니다.

팀의 Spring Boot 기준에 맞춰 Web MVC starter와 Jackson 3을 사용합니다. 기존 추출 JSON·API 필드·원 단위·null/0 구분은 유지합니다. 2026-11-02에 백엔드 테스트 8건과 clean 패키징, 임시 MySQL에서 원본 등록·추출 응답 처리·확인값 저장·서버 재시작 후 재조회를 검증했습니다. [검증 기록](../docs/sfr003/VALIDATION.md)을 참고하세요.

패키지 배치는 팀의 기능별 구조에 맞췄습니다.

- listing/controller: ListingController, AdvertisementController
- listing/service: AdvertisementService
- listing/domain: Listing, Advertisement
- listing/repository: ListingRepository, AdvertisementRepository
- listing/dto: ListingRequest, AdvertisementResponse
- extraction/client: ExtractionProvider, GeminiExtractionProvider
- extraction/service: ExtractionService
- extraction/dto: ExtractionResult

기존 URL·상태·검증·현재 테이블을 유지했습니다. 패키지 이동은 팀의 `rooms` DB, Flyway, 인증·소유권 구현을 도입한 것이 아닙니다. 구체적인 차이와 적용 전 합의는 [통합 제안](../docs/sfr003/TEAM_INTEGRATION.md)에 있습니다.

팀의 V1 SQL을 복사·수정·적용하지 않았으며 기존 `listings`·`advertisements` 자료를 임의로 `rooms`에 이관하지 않습니다. 사용자 ID를 요청·엔티티에 임의로 추가하지 않았고 인증을 흉내 내지 않았습니다. 기존 API는 로그인 미연결의 로컬 개발 범위입니다.

패키지를 바꾼 뒤 이전 class가 jar에 남으면 같은 엔티티·컨트롤러가 중복 스캔될 수 있어 clean 빌드를 사용합니다. API 응답과 원 단위/null/0 검증은 기존 테스트·MySQL·브라우저 검증으로 확인합니다.

## 2026-11-03 광고–매물 연결

ListingService가 최종 생성 시 선택 advertisementId의 광고를 잠그고 REVIEWED·확인값·중복 여부를 검증한다. listings.advertisement_id에 nullable UNIQUE와 advertisements FK를 두며, 수정에서는 원본 연결을 보존한다. 기존 직접 입력·이전 PATCH 요청도 유지한다. 적용 범위는 기존 개발용 JPA 모델이며 팀 Flyway V1과 rooms 모델은 변경하지 않는다. 테스트는 16건 통과했고 별도 실제 MySQL에서 기존 매물·동시 등록·재시작 보존을 검증했다.

## 면적 종류 호환성

ExtractionResult에 areaType(전용·공급·미상)을 추가했다. 구버전 추출·확인 JSON의 항목 생략/null은 미상으로 읽으며 저장 JSON을 일괄 갱신하지 않는다. 공급자의 허용 밖 값은 502, 사용자 확인 요청의 허용 밖 값은 400으로 거부한다. 기존 광고 JSON 컬럼과 매물 areaType 컬럼을 사용하며 DB 구조는 바꾸지 않는다.

## 옵션 배열 저장

ExtractionResult.options와 listings.options(JSON)을 연결했다. 공통 역직렬화에서 문자열 배열·크기·길이·제어문자를 검증하고 공백·중복을 정리한다. ListingRequest는 기존 생성자와 접근자를 유지하면서 setter 호출 여부로 생략과 명시적 null을 구분한다. 이 구분 때문에 DTO를 record에서 클래스로 변경했으며 기존 검증 제약은 동일하다. 기존 PATCH의 옵션 생략은 저장값을 보존한다. 적용 전 절차는 docs/sfr003/sql/README.md를 참조한다.

## 최종 매물 면적 정밀도

listings.area는 DECIMAL(10,4)이며 ListingRequest는 0~100000㎡·유효 소수 4자리로 검증한다. 원값을 반올림하지 않고 초과 요청은 거부한다. 추출·확인 JSON의 정밀도는 별도로 보존한다. 기존 정수부 범위 축소에 대비해 MySQL 연결의 기존 sql_mode에 STRICT_ALL_TABLES를 추가하고 Hibernate 스키마 오류 시 시작을 중단한다. 기존 DB는 앱을 시작하기 전에 docs/sfr003/sql/README.md의 사전 검사·백업·SQL 이행을 따라야 한다. 단순히 컬럼을 넓혀도 과거에 반올림된 값이 자동 복원되지는 않는다.

## 확인값 수정 이력·저장 충돌

광고 확인값 저장은 `X-Review-Version` 헤더로 이전 화면의 버전을 확인하고, 광고 행 잠금 안에서 현재 값·버전·이력을 원자적으로 저장한다. `advertisement_reviews`는 버전별 확인값을 보관하며 조회 API는 최근 20건을 반환한다. 기존 마지막 확인값은 최초 수정 때 버전 0으로 보존한다. 개발용 스키마 추가와 이전 클라이언트 호환성은 [SFR-005 계약](../docs/sfr003/REVIEW_HISTORY.md)을 따른다.
