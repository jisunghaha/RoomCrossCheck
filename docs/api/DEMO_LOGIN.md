# 커뮤니티 시연용 로그인 — 검토용

2026-11-06. 기준은 `feature/sfr015-016-community`의 `f311482`이며 작업 브랜치는 `feature/sfr-001-community-demo-login`이다.
실제 비밀번호 검증·세션·CSRF와 커뮤니티 권한을 연결하는 시연 보완이다. 사용자에게 정식 인증 담당을 배정하거나 SFR-001 전체 개발완료로 표시하는 작업이 아니다.
기존 `feature/sfr-003-gemini-extraction`의 최신 SFR-004·005 변경을 이 브랜치에 병합하지 않았다. 원래 브랜치와 팀 브랜치는 보존했다.

## 구현 범위

- Spring Boot 3.5.7·Java 21 유지, Spring Security 추가.
- 기본 실행에서는 시연 로그인이 꺼지고 공개 커뮤니티 조회만 가능하다.
- `demo-login` 프로필은 `rc_demo_`로 시작하는 별도 MySQL DB에서만 실행한다. prod·production 프로필과 함께 켜면 시작을 거부한다.
- member1·member2·admin 계정은 실행 환경의 서로 다른 비밀번호로 검증하며, 메모리에는 BCrypt 해시로 등록한다. 기본 비밀번호는 없다.
- 회원2는 회원1의 글·댓글을 변경할 수 없다. 공지는 관리자만 작성·변경한다. 작성자와 권한을 요청 본문·임의 헤더에서 받지 않는다.
- 인증은 서버 세션을 사용한다. 세션 ID 쿠키는 HttpOnly·SameSite=Lax이고, 비밀번호·세션 토큰은 localStorage에 넣지 않는다.
- 로그인·로그아웃·커뮤니티 쓰기에 CSRF를 적용한다. XSRF-TOKEN 쿠키와 X-XSRF-TOKEN 헤더는 기존 커뮤니티 클라이언트 방식과 맞췄다. 로그인·로그아웃 후 토큰을 다시 발급받는다.
- 세션은 20분 비활동 시 만료되며 로그아웃 때 무효화한다. 서버 재시작 시 세션은 사라지지만 MySQL 게시글·댓글·좋아요는 유지된다.

## 정식 회원과의 구분

시연 계정의 내부 번호는 member1=900000001, member2=900000002, admin=900000003이다.
이 번호는 **시연 전용**이며 팀 `users.id`가 아니다. 비밀번호 확인에 성공한 서버 Principal에서만 CurrentActor로 변환한다.
정식 인증으로 채택할 때는 이 계정 공급자를 제거하고 실제 users·검증된 인증 정보에 연결해야 한다.
시연 DB의 글·댓글을 팀 DB로 자동 이관하거나 실제 회원 소유로 간주하지 않는다.

Google OAuth, 회원가입·탈퇴·복구, 실제 회원 테이블, 로그인 시도 제한, 전체 매물 API의 소유권 검증, 운영 HTTPS 배포는 포함하지 않는다.
보안 필터의 적용 범위는 `/api/auth/**`와 `/api/community/**`다. 기존 매물·광고 API는 여전히 로컬 개발 범위이며 실제 사용자에게 공개 배포하면 안 된다.
시연 프로젝트는 로컬 loopback 주소에서만 사용한다. Docker 내부 서버는 0.0.0.0이지만 호스트 공개 포트는 기존 Compose의 127.0.0.1 바인딩을 유지한다.

## API

| 경로 | 동작 |
|---|---|
| GET /api/auth/config | demoEnabled 반환 |
| GET /api/auth/csrf | 시연 프로필에서 CSRF 쿠키 발급 |
| POST /api/auth/login | username·password를 form-urlencoded로 검증 |
| POST /api/auth/logout | 세션·인증 해제 |
| GET /api/community/session | 기존 계약의 authenticated·userId·administrator 반환 |

로그인 실패는 계정 존재 여부를 구분하지 않는 401, CSRF 누락·불일치는 403이다. 비로그인 쓰기는 보안 토큰 검사가 먼저 실패하면 403, 토큰이 유효하면 401이다.
공개 글·댓글 조회는 계속 가능하다. 프로필이 꺼져 있으면 로그인·CSRF 발급·로그아웃 경로는 차단된다.

## 사용자 컴퓨터에서 실행

[시연 실행 안내](../../시연_실행안내.md)를 따른다. 별도 폴더·Compose 프로젝트·DB·비밀번호를 사용한다.
`.runtime/demo-login.env`는 최초 실행 시 생성하며 Git과 배포 압축에서 제외한다. 기존 파일은 재생성하지 않아 DB 연결을 유지한다.
실제 Gemini·건축물대장 API는 시연 Compose에서 꺼 두었다.

## 검증 기준

실제 Spring Security 필터 테스트에서 로그인 실패·CSRF·Principal 연결·작성자 권한·관리자 공지·로그아웃을 확인한다.
별도 임시 MySQL에서 커뮤니티 통합 테스트와 HTTP 권한 검증, Chromium에서 계정별 화면·글·댓글·좋아요·새로고침·로그아웃을 검증한다.
실제 사용자의 정식 회원 인증 완료를 뜻하지 않으며, 다른 컴퓨터의 설치 상태까지 이 환경에서 보증하지 않는다.

## 참고한 공식 문서

- [Spring Security SPA CSRF](https://docs.spring.io/spring-security/reference/6.5/servlet/exploits/csrf.html#csrf-integration-javascript-spa)
- [Java 21 Javadoc](https://docs.oracle.com/en/java/javase/21/docs/specs/javadoc/doc-comment-spec.html)
- [Google JavaScript JSDoc](https://google.github.io/styleguide/jsguide.html#jsdoc)

## 2026-11-06 검증 결과

프론트 54건·백엔드 83건·런타임 6건 통과. 백엔드 83건에는 별도 임시 MySQL의 커뮤니티 통합 테스트 10건과 실제 보안 필터 테스트가 포함되며 건너뛴 테스트는 없었다.
임시 MySQL의 트리거 실패 검증을 위해 해당 일회용 컨테이너의 binary log만 비활성화했으며 공유 DB 설정은 변경하지 않았다.
HTTP 검증에서 위조 사용자·권한 차단, CSRF 누락 차단, 회원별 소유권, 관리자 공지, 댓글 수정, 좋아요 반복 요청, 로그아웃과 재시작 보존을 확인했다.
Chromium에서 비밀번호 오류, 회원별 글·댓글·좋아요, 본인 댓글 수정, 관리자 공지, 세션 쿠키 HttpOnly·SameSite, 새로고침·로그아웃, 모바일 로그인 화면을 확인했다. 페이지 JavaScript 오류는 0건이었다.
외부 Gemini 생성 요청과 건축물대장 API 요청은 모두 0건이다.

제공할 `demo-login-up.sh`로 별도 Docker Compose의 MySQL·백엔드를 기동하고,
`demo-login-front.sh`의 Vite 프록시를 통해 실제 member1 로그인·세션 조회까지 확인했다.
종료 스크립트를 실행하고 검증에서 생성한 프로젝트의 임시 볼륨만 정리했다.
압축에는 소스·실행 안내·검증 기록만 포함하며 실제 비밀번호·환경 파일·DB 자료·빌드 캐시는 제외한다.
