# 백엔드 현재 구현 범위

Java 21·Spring Boot·MySQL 로컬 개발 API입니다. 루트의 `scripts/build-backend.sh`가 Maven clean test package를 실행하고 `scripts/dev-up.sh`가 DB와 백엔드를 기동합니다. 공개 CA·프록시는 런타임에서 적용합니다.

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
