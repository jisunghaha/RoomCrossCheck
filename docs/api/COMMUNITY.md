# 커뮤니티 API — SFR-015·016

기본 경로는 `/api/community`이다. 요청 본문은 JSON이며 작성자와 권한은 서버의 인증 정보로 설정한다.
현재 저장 구조에는 비공개 게시글 구분이 없어 게시글·공지사항·댓글 전체가 공개 조회 대상이다.

## 경로

| 메서드 | 경로 | 기능 | 권한 |
|---|---|---|---|
| GET | `/session` | 현재 요청의 인증 상태 | 전체 |
| GET | `/posts?page=0&size=20` | 게시글·공지사항 최신순 목록 | 전체 |
| GET | `/posts/{postId}` | 게시글·공지사항 상세 | 전체 |
| POST | `/posts` | 일반 게시글 작성 | 회원·관리자 |
| POST | `/notices` | 공지사항 작성 | 관리자 |
| PATCH | `/posts/{postId}` | 제목·내용 수정 | 작성자·관리자, 공지는 관리자만 |
| DELETE | `/posts/{postId}` | 댓글·좋아요와 함께 삭제 | 작성자·관리자, 공지는 관리자만 |
| GET | `/posts/{postId}/comments?page=0&size=20` | 댓글 작성순 목록 | 전체 |
| POST | `/posts/{postId}/comments` | 댓글 작성 | 회원·관리자 |
| PATCH | `/posts/{postId}/comments/{commentId}` | 댓글 내용 수정 | 댓글 작성자·관리자 |
| DELETE | `/posts/{postId}/comments/{commentId}` | 댓글 삭제 | 댓글 작성자·관리자 |
| GET | `/posts/{postId}/likes` | 좋아요 개수·본인 등록 여부 | 전체 |
| PUT | `/posts/{postId}/likes` | 본인 좋아요 등록, 반복 요청 시 상태 유지 | 회원·관리자 |
| DELETE | `/posts/{postId}/likes` | 본인 좋아요 취소, 반복 요청 시 상태 유지 | 회원·관리자 |

공지사항 수정·삭제도 `/posts/{postId}`를 사용한다. 관리자가 다른 작성자의 글·댓글을 수정해도 작성자는 변경되지 않는다.
좋아요 관리에는 다른 사용자의 번호를 받지 않으며 관리자도 본인의 좋아요만 취소한다.

## 요청·응답

게시글과 공지사항의 작성·수정 본문:

```json
{ "title": "제목", "content": "내용" }
```

댓글의 작성·수정 본문:

```json
{ "content": "댓글 내용" }
```

PATCH에서도 위 필드를 모두 전달한다. 빈 제목·내용은 허용하지 않으며 제목은 최대 200자이다.
본문은 UTF-8 기준 65,535바이트를 넘을 수 없다. `authorId`, `userId`, `role`, `notice`를 보내도
인증이나 작성자·공지 여부 설정에 사용하지 않는다. 공지는 관리자 전용 생성 경로로 작성한다.

목록 응답은 `content`, `number`, `size`, `totalElements`, `totalPages`, `first`, `last`를 포함한다.
`number`는 0부터 시작하며 `size`는 1~100이다. 빈 목록은 `content: []`와 전체 개수 0으로 응답한다.
존재하지 않는 게시글의 댓글·좋아요 조회는 빈 목록이 아닌 404이다.

게시글 응답은 `id`, `authorId`, `title`, `content`, `notice`, `createdAt`, `updatedAt`을 포함한다.
댓글 응답은 `id`, `postId`, `authorId`, `content`, `createdAt`, `updatedAt`을 포함한다.
좋아요 응답은 `postId`, `count`, `liked`를 포함한다. 비로그인 사용자의 `liked`는 false이다.
시각은 UTC 기준 ISO 8601 형식이다.

생성 성공은 201과 `Location` 헤더를 반환한다. 게시글·댓글 삭제는 본문 없는 204,
좋아요 등록·취소는 갱신된 좋아요 상태와 200을 반환한다.

## 인증 연결

2026-11-06 검토본: 별도 `demo-login` 프로필에서 서버 비밀번호 검증·세션을 연결했다. 정식 users가 아닌 시연 전용 계정이며 [범위·실행·보안 계약](DEMO_LOGIN.md)을 따른다. 아래 기본 계약은 정식 인증 통합에도 유지한다.

기본 프로필에는 로그인 구현을 추가하지 않았으며 `CurrentActor.Provider` Bean이 없으면 비로그인으로 처리한다.
로그인 담당 코드에서 이 인터페이스를 구현한 Bean을 등록하고, `current()` 호출 때마다 서버가 검증한
인증 정보의 회원 고유번호와 관리자 여부를 제공한다. 비로그인은 null로 반환한다.
사용자 번호는 `Long`이며 로그인 아이디 문자열이 아닌 `users.id`에 대응한다.

공급자는 요청 본문·임의 헤더·쿼리의 사용자 번호와 권한을 신뢰하면 안 된다.
고정 테스트 사용자 공급자는 운영에 등록하지 않는다. 실제 인증 연동 시 Security 경로 정책과
세션·CSRF 보호도 로그인 담당 코드에서 함께 확인해야 한다. 현재 API 테스트는 실제 Security 필터 연동을 검증하지 않는다.

`GET /session`은 `authenticated`, `userId`, `administrator`를 반환하며 비로그인은
`false`, `null`, `false`이다. 화면의 버튼 표시용이며 쓰기 요청의 권한 검사를 대체하지 않는다.

## 오류

오류 형식은 `{ "status": 403, "message": "..." }`이다.

| 상태 | 의미 |
|---|---|
| 400 | 잘못된 JSON·필수값·타입·입력 범위 |
| 401 | 인증 정보가 없는 쓰기 요청 |
| 403 | 작성자·관리자 권한 부족 |
| 404 | 존재하지 않거나 지정 게시글에 속하지 않는 리소스 |
| 409 | 동시 수정 또는 데이터 제약 충돌, 재조회 필요 |
| 415 | 지원하지 않는 요청 본문 형식 |
| 503 | 잠금 획득 실패, 잠시 후 재시도 |
| 500 | 예상하지 못한 서버 오류 |

요청 본문과 파라미터가 해석·검증되지 않으면 Service의 인증 검사보다 먼저 400 또는 415가 발생할 수 있다.
DB 제약 이름과 SQL·토큰·게시글 본문은 오류 응답에 포함하지 않는다.

## MySQL 통합 테스트

`CommunityMysqlIntegrationTest`는 실제 MySQL에 연결해 엔티티 저장·조회, 외래키와 좋아요 유일 제약,
동시 좋아요 등록, 삭제 실패 시 댓글·좋아요 롤백, 버전 충돌을 검증한다.
Service·Controller 테스트와 달리 Repository를 모의 객체로 대체하지 않는다.

전용 환경변수 `COMMUNITY_MYSQL_TEST_URL`, `COMMUNITY_MYSQL_TEST_USER`,
`COMMUNITY_MYSQL_TEST_PASSWORD`로 접속 정보를 제공한다. URL 환경변수가 없으면 이 테스트는 건너뛴다.
DB 이름은 `rc_community_test_`로 시작하는 영문 소문자·숫자·밑줄 조합만 허용한다.
테스트는 `create-drop`과 테스트 행 삭제를 사용하므로 새로 만든 임시 DB에서만 실행한다.
운영·개발 DB를 연결하거나 기존 `.env`의 접속 정보를 재사용하지 않는다.

전용 MySQL과 환경변수를 준비한 환경에서 백엔드 디렉터리의 실행 명령은 다음과 같다.

```sh
mvn -Dtest=PostServiceTest,CommentServiceTest,PostLikeServiceTest,CommunityControllerTest,CommunityMysqlIntegrationTest test
```

쓰기 테스트의 가짜 사용자 정보는 테스트 클래스 안에서만 생성하며 운영 인증 공급자로 등록하지 않는다.
