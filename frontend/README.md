# 프론트엔드 개발

저장소 루트에서 `npm ci`로 workspace 의존성을 설치합니다. 루트의 `npm run dev`, `npm test`, `npm run build`는 이 frontend workspace를 호출합니다. frontend 폴더에서도 같은 명령을 사용할 수 있습니다.

| 작업 | 파일 |
|---|---|
| 앱 조합·등록 경로 | src/app/App.jsx, src/app/routing/useHashRoute.js |
| 상태·저장 오류·중복 저장 방지 | src/app/state/RoomContext.jsx |
| local/api/listing-api 경계 | src/app/data/repository.js |
| 기존 브라우저 저장 키 | src/app/data/localStorage.js |
| 단일 단계형 등록 | src/features/listing/pages/RegisterRoomPage.jsx |
| 원→만원→정수 원 변환·공용 입력 | src/features/listing/components/RoomForm.jsx |
| 광고 원본·추출·사용자 확인 | src/features/extraction/components/AdDraftPanel.jsx |
| 저장된 확인 값만 폼으로 전달 | src/features/extraction/model/reviewedAdvertisement.js |
| 스타일 | src/app/styles.css |

등록과 매물 수정이 같은 RoomForm을 사용합니다. 신규 등록은 onPreview로 값을 최종 확인 화면에 전달하며 즉시 저장하지 않습니다. 기존 매물 수정은 원래의 수정 모달에서 저장합니다. 서버 광고는 listing-api 모드에서만 제공하고, local 모드에서는 추출 결과를 만들지 않습니다.

`frontend/.env`의 공개 설정을 우선하고 기존 루트 `.env`의 두 VITE 설정을 호환합니다. API 키와 DB 정보는 프론트 설정에 넣지 않습니다. 매물·비교·후기·방문 UI와 서버 인증 상태를 사용하는 커뮤니티를 유지하며, 일반 회원가입·로그인 화면은 기존 `/api/auth`와 연결합니다.

`src/features/auth`는 왼쪽 계정 카드, 로그인·회원가입·내 정보 모달과 인증 API 호출을 관리합니다. 가입은 아이디·비밀번호·닉네임·이메일을 받으며 비밀번호 확인은 서버에 보내지 않습니다. 가입 후 로그인 화면으로 이동하고, 로그인 후 닉네임·내 정보·로그아웃을 표시합니다. 비밀번호와 세션 ID를 localStorage에 보관하지 않습니다. 이메일 소유권 인증·정보 수정·구글 로그인은 후속 작업입니다. [인증 API 문서](../docs/api/AUTH.md)를 참고하세요.

팀의 등록 화면 단계 구조와 RoomForm의 initialValues/onPreview 계약을 참고해 현재 서버 광고 흐름을 연결했습니다. 팀 화면에서 local 시연용인 임시 매물 저장·이미지 미리보기와 달리, 현재 광고 원본은 기존 백엔드에 보관합니다. /rooms의 DRAFT·CONFIRMED 및 사용자 소유권 연결은 합의 전이며, 현재 /listings 저장을 그 계약으로 표시하지 않습니다.

## 커뮤니티 — SFR-015·016

`src/features/community`에서 화면, API 호출과 스타일을 관리합니다. 기존 매물·후기·방문 기능과 별도로
커뮤니티 메뉴를 추가했으며 매물의 `local` 모드에서도 커뮤니티는 서버를 사용합니다.
기존 화면을 복사하거나 사용자·로그인 기능을 중복 구현하지 않았습니다.

화면 주소는 `#community`, `#community/new`, `#community/post/{id}`, `#community/post/{id}/edit`입니다.
목록·상세·공지사항, 게시글 작성·수정·삭제, 댓글과 좋아요를 지원하며 실패 시 입력 내용을 유지합니다.
`/api/community/session` 응답으로 버튼을 표시하고, 쓰기 권한은 백엔드 Service가 다시 확인합니다.
로그인 공급자 미연동 상태에서는 공개 조회만 사용할 수 있습니다. 테스트 사용자 상태는 테스트에서만 주입합니다.

커뮤니티 API는 같은 출처의 `/api/community`로 요청합니다. 개발 서버의 기존 `/api` 프록시를 사용하므로
백엔드를 실행해야 하며, 다른 포트라면 개발 서버 프로세스의 `DEV_API_TARGET`을 설정합니다.
배포 시에도 `/api`를 백엔드로 전달해야 합니다. 루트의 `VITE_API_BASE_URL`로 지정한 외부 주소로
커뮤니티 요청을 직접 전송하지 않습니다. 서버 오류 시 브라우저 예시 데이터로 대체하지 않습니다.
API 계약은 [커뮤니티 API 문서](../docs/api/COMMUNITY.md)를 참고하세요.

검증 명령은 `npm --workspace frontend run test -- --configLoader runner --maxWorkers=2`와
`npm --workspace frontend run build -- --configLoader runner`입니다. Windows PowerShell에서는 `npm.cmd`를 사용할 수 있습니다.
