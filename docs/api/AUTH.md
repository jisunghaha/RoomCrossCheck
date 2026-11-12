# 일반 회원 인증 API

일반 회원가입·로그인 API의 계약이다. 왼쪽 계정 카드의 인증 UI에서 사용하며 구글 OAuth는 후속 작업이다.

## 실행과 적용 범위

- MySQL users에 저장된 회원으로 인증하며 별도의 인증 프로필이나 고정 계정은 사용하지 않는다.
- 실제 회원 번호는 users.id다. 외부 데이터의 작성자 번호를 새 회원에 자동 배정하지 않는다.
- 기존 DB 연결 설정을 사용하며 새 환경변수나 API 키는 필요 없다.
- 이번 개발 작업에서 DB에 직접 접속하거나 서버를 재시작하지 않는다. 이후 새 백엔드를 실행하면
  기존 ddl-auto=update 설정에 따라 users 테이블 생성을 시도한다.
- 매물·광고·방문 API의 소유자 검사는 별도 통합 대상이다. 필터 범위는 auth·community API다.
  따라서 이 작업만으로 보고서 SFR-001 전체가 완료되는 것은 아니다.

## 회원 저장 구조

| 필드 | 의미·제약 |
|---|---|
| id | 자동 생성되는 Long 내부 번호, 로그인 아이디와 구분 |
| login_id | 일반 회원은 영문 소문자·숫자·밑줄·점·하이픈 4~30자, 유일. 소셜 전용 회원은 NULL 허용 |
| password_hash | 일반 회원은 BCrypt 해시, 원문 비밀번호를 저장하지 않음. 소셜 전용 회원은 NULL 허용 |
| nickname | 앞뒤 공백 제외 1~30자 |
| email | 형식 검증, 최대 254자, 유일 |
| role | USER 또는 ADMIN, 가입 시 서버에서 USER 고정 |
| created_at, updated_at | 서버에서 기록하는 가입·수정 시각 |

아이디·이메일은 앞뒤 공백 제거 후 소문자로 통일한다. 비밀번호 공백은 제거하지 않는다.
비밀번호는 8자 이상·UTF-8 72바이트 이하이며 초과하면 잘라서 저장하지 않고 거절한다.
이메일 소유권 확인·비밀번호 복구·탈퇴·관리자 생성 API는 이번 범위에 포함하지 않는다.
구글 로그인 추가 시 이메일이 같다는 이유만으로 기존 회원과 자동 연결하지 않는다.

### 소셜 계정 연결 엔티티 준비

`User.socialMember`는 자체 아이디·비밀번호 없이 USER 권한으로 공통 회원 정보를 생성한다.
일반 회원가입의 필수 입력 검증과 기존 생성자는 유지한다.
`OAuthAccount`는 `oauth_accounts`에 내부 회원 FK(`user_id`), 제공자(`provider`),
외부 고유번호(`provider_subject`, 구글 sub), 연결 시각(`created_at`)을 매핑한다.
제공자와 외부 고유번호 조합에 유일 제약을 두며 회원 자동 저장·자동 삭제는 전파하지 않는다.
토큰과 클라이언트 보안 비밀은 이 테이블에 저장하지 않는다.

현재는 엔티티 준비 단계이며 구글 회원 생성·인증·세션 연결은 아직 구현하지 않았다.
기존 DB의 `login_id`·`password_hash` NULL 허용 여부와 새 테이블·외래키 생성은
별도 DB 반영·검증이 필요하며, 이 작업에서 서버 재실행이나 DB 변경은 수행하지 않는다.

## API

| 메서드 | 경로 | 기능 |
|---|---|---|
| GET | /api/auth/config | memberEnabled=true |
| GET | /api/auth/csrf | XSRF-TOKEN 쿠키 준비 |
| POST | /api/auth/signup | JSON 회원가입, 성공 201, 자동 로그인하지 않음 |
| POST | /api/auth/login | 폼 로그인, 성공 200 |
| POST | /api/auth/logout | 세션 무효화·JSESSIONID 쿠키 삭제 |
| GET | /api/auth/me | 본인 회원 정보, 비로그인 401 |
| GET | /api/community/session | 기존 커뮤니티 인증 상태 계약 유지 |

인증 설정 응답은 아래와 같다.

    {"memberEnabled":true}

### 회원가입

Content-Type은 application/json이다.

    {"loginId":"member.one","password":"example-password-1234","nickname":"회원","email":"member@example.com"}

응답은 비밀번호와 해시를 포함하지 않는다.

    {"id":1,"loginId":"member.one","nickname":"회원","email":"member@example.com","role":"USER"}

클라이언트가 id·role을 함께 보내도 회원 번호·권한에 사용하지 않는다.
아이디·이메일 사전 중복 검사와 DB 유일 제약을 함께 사용하며 동시 중복 가입도 409로 변환한다.

### 로그인·세션·CSRF

로그인은 Controller가 아니라 Spring Security 필터가 처리한다.
Content-Type은 application/x-www-form-urlencoded이며 JSON을 받는 API가 아니다.

    username=member.one&password=example-password-1234

username에는 users.login_id를 넣는다. 비밀번호는 인코딩된 폼 필드로 보내고 URL에 넣지 않는다.
성공 시 인증 필터는 status=200과 메시지를 반환하며 회원 정보는 GET /api/auth/me로 조회한다.
로그인 시 기존 세션 ID를 교체하고 비활동 만료 시간은 20분으로 설정한다.
세션의 Principal에는 내부 번호·아이디·권한만 남기며 비밀번호 해시는 인증 후 소거한다.
쿠키 세션을 사용하고 비밀번호·세션 ID를 localStorage에 저장하지 않는다.

가입·로그인·쓰기·로그아웃 전에 GET /api/auth/csrf로 쿠키를 받고 그 값을 X-XSRF-TOKEN 헤더로 전달한다.
쿠키와 헤더가 함께 필요하며 로그인·로그아웃 이후에는 새 CSRF 쿠키를 받아야 한다.
기본 실행에서 비로그인 커뮤니티 쓰기는 CSRF 오류에서도 401을 반환한다.
가입·로그인의 CSRF 누락은 403이다.

## 권한과 오류

인증된 MemberPrincipal을 CurrentActor.Provider로 전달한다. 작성자와 좋아요 사용자는 이 정보로 결정한다.
일반 회원은 본인 글·댓글만 관리하고 공지·전체 글·댓글 관리는 기존 Service의 관리자 검사를 따른다.
공개 글·댓글의 비로그인 조회와 기존 커뮤니티 경로는 유지한다.
일반 회원가입으로 관리자 계정을 만들 수 없다. 관리자는 승인된 별도 운영 절차가 필요하다.

| 상태 | 의미 |
|---|---|
| 400 | 가입 입력 형식·길이·비밀번호 바이트 제한 오류 |
| 401 | 로그인 실패·비로그인 본인 조회·비로그인 커뮤니티 쓰기 |
| 403 | CSRF 누락·불일치 또는 로그인 회원의 권한 부족 |
| 409 | 가입 아이디·이메일 중복 |
| 415 | 회원가입의 잘못된 Content-Type |
| 500 | 예상하지 못한 서버 오류, 내부 상세 반환 안 함 |

인증 필터와 인증 Controller의 메시지 응답 예:

    {"status":401,"message":"로그인이 필요합니다."}

존재하지 않는 아이디와 잘못된 비밀번호는 같은 실패 문구로 응답한다.
오류 응답과 로그에 비밀번호·입력 본문·SQL 상세를 기록하지 않는다.

## 검증 범위

AuthServiceTest는 해시·기본 권한·가입 중복·DB 제약 예외 변환·바이트 제한·인증 정보 소거를 검증한다.
MemberAuthSecurityTest는 실제 필터·Service·검증기를 통과하여 가입·비밀번호·세션 ID 교체·CSRF,
작성자 위조·글과 댓글 소유권·관리자 공지·좋아요 사용자 번호·로그아웃을 확인한다.
별도 프로필 없이 실제 회원 인증이 활성화되고 임의 헤더로 인증할 수 없는지도 확인한다.
이 테스트의 Repository는 대역이므로 실제 MySQL의 테이블 생성·유일 제약·트랜잭션 검증과
브라우저 UI 검증까지 완료한 것으로 간주하지 않는다.

## 참고

프론트엔드는 왼쪽 ‘나의 자취 준비’ 카드에서 로그인·회원가입 모달을 연다. 가입 시 아이디·비밀번호·닉네임·이메일을 보내며 비밀번호 확인은 화면에서만 검사한다.
가입 후 자동 로그인하지 않고 로그인 화면으로 이동한다. 로그인 후 같은 카드에 닉네임·내 정보·로그아웃을 표시하고 커뮤니티의 인증 상태를 다시 조회한다.
내 정보는 아이디·닉네임·이메일 조회만 제공한다. 이메일 소유권 인증과 정보 수정은 제공하지 않는다.
기존 하단 가상 사용자 표시와 오른쪽 상태 배지를 제거한 이유는 `Layout.jsx`의 해당 위치에 기록했다.

- [Spring Security 로그인](https://docs.spring.io/spring-security/reference/6.5/servlet/authentication/passwords/form.html)
- [Spring Security CSRF](https://docs.spring.io/spring-security/reference/6.5/servlet/exploits/csrf.html)
- [Java 21 Javadoc](https://docs.oracle.com/en/java/javase/21/docs/specs/javadoc/doc-comment-spec.html)
