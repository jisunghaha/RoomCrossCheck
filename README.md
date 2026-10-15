# RoomCrossCheck · 프론트엔드 개발 가이드

원룸 광고, 공식 건축물 정보, 방문 기록과 거주 후기를 함께 확인하고 관심 매물을 비교하는 서비스를 개발합니다. 이 README는 팀원이 기존 코드를 수정하고 새 기능을 연결할 때 참고하는 안내입니다.

**현재 저장소에는 React 프론트엔드와 로컬 개발용 Spring Boot·MySQL 매물 저장 API가 있습니다.** 후기·방문 API와 인증은 아직 구현되지 않았습니다. 화면의 기본 흐름을 출발점으로 삼고, 계획서의 요구사항에 맞춰 기능과 API를 확장합니다.

## 1. 개발 시작하기

Node.js 22.12 이상을 준비하고 저장소 최상위에서 실행합니다.

```bash
npm ci
cp .env.example .env
npm run dev
```

터미널에 표시되는 주소로 접속합니다. 기본 주소는 `http://127.0.0.1:4173`이며 HTML 파일을 직접 열어 실행하지 않습니다. React 19와 Vite 7을 사용하고 설치 버전은 `package-lock.json`을 따릅니다.

서버 없이 화면을 개발할 때는 `.env`에서 다음 설정을 유지합니다.

```dotenv
VITE_DATA_MODE=local
```

이 모드는 가상 초기 데이터와 브라우저 localStorage를 사용합니다. 같은 브라우저·같은 주소에서는 저장한 데이터가 유지되므로 `seed.js`를 수정해도 기존 저장 데이터가 우선합니다. 초기 예시로 다시 확인하려면 필요한 데이터를 먼저 보관한 뒤 개발자 도구에서 `oneroom.frontend.v1` 키를 삭제합니다.

## 2. 어떤 파일을 수정해야 하나요?

아래 경로는 모두 **현재 프론트엔드 내부 경로**입니다.

| 작업 | 시작할 파일 또는 폴더 |
|---|---|
| 관심 매물 목록·검색·필터·정렬 변경 | `src/pages/RoomsPage.jsx` |
| 매물 등록·수정 입력 항목 추가 | `src/components/RoomForm.jsx` |
| 매물 카드·상세 내용 변경 | `src/components/RoomCard.jsx`, `RoomDetail.jsx` |
| 비교표 항목·표시 방식 변경 | `src/pages/ComparePage.jsx` |
| 후기 화면·작성 폼 변경 | `src/pages/ReviewsPage.jsx`, `src/components/ReviewForm.jsx` |
| 방문 체크리스트·메모 변경 | `src/pages/VisitsPage.jsx` |
| 여러 화면에서 사용하는 데이터·저장 액션 추가 | `src/context/RoomContext.jsx` |
| 서버 요청·응답 변환 또는 로컬 저장 변경 | `src/services/repository.js` |
| 금액·대조·후기 통계 규칙 변경 | `src/lib/domain.js` |
| 기존 브라우저 저장 데이터 읽기·보정 | `src/lib/storage.js` |
| 개발용 예시 데이터 추가 | `src/data/seed.js` |
| 공통 메뉴·헤더·푸터 변경 | `src/components/Layout.jsx` |
| 공통 UI·모달 변경 | `src/components/UI.jsx`, `Modal.jsx` |
| 새 페이지·모달 연결 | `src/App.jsx`, `src/hooks/useHashRoute.js` |
| 스타일·모바일 화면 조정 | `src/styles.css` |
| 동작 검증 추가 | `tests/` |

페이지는 `useRooms()`로 공통 데이터와 액션을 사용합니다. 서버 호출을 각 화면에 직접 넣기보다 아래 흐름을 유지합니다.

```text
페이지·입력 폼
    ↓ 사용자 액션
RoomContext (공통 상태·로딩·저장 결과)
    ↓ 저장소 메서드
repository.js
    ├─ local 모드 → 브라우저 저장
    └─ api 모드   → Spring Boot API → MySQL (백엔드에서 구현)
```

입력 중인 값과 모달 열림 같은 화면 전용 상태는 해당 컴포넌트에 둡니다. 여러 페이지가 함께 사용하는 매물·후기·방문 데이터는 공통 상태에서 관리합니다.

## 3. 기존 기능에 새 항목 추가하기

예를 들어 매물에 **입주 가능일**을 추가한다면 다음 순서로 작업합니다.

1. **데이터 형식을 합의합니다.** 예: `availableFrom`을 `YYYY-MM-DD` 또는 `null`로 사용합니다. 백엔드 담당자와 요청·응답 필드, 필수 여부를 먼저 맞춥니다.
2. **입력 폼을 수정합니다.** `RoomForm.jsx`의 초기값, 입력 UI, 제출값에 필드를 추가합니다. 수정 화면에서도 기존 값이 표시되어야 합니다.
3. **저장 경로를 확인합니다.** `RoomContext.jsx`에서 전달한 값이 `repository.js`의 local/API 양쪽 구현을 통해 유지되는지 확인합니다. 서버가 반환하는 Room에도 새 필드가 있어야 합니다.
4. **필요한 화면에 표시합니다.** 상세·비교표에 항목을 추가하고, 필요하면 검색·필터도 수정합니다. 값이 없는 기존 매물에는 ‘정보 없음’을 표시합니다.
5. **예시·기존 데이터를 처리합니다.** `seed.js`에 예시를 추가합니다. 기존 저장 데이터에 필드가 없어도 동작하게 기본값을 적용하고, 구조 자체를 바꾸면 `storage.js`에서 변환합니다.
6. **사용 흐름을 확인합니다.** 등록 → 수정 → 새로고침 → 상세·비교 화면까지 값이 유지되는지 확인하고 관련 테스트를 추가합니다.

필드를 입력창에만 추가하면 저장·재조회·비교 과정에서 누락될 수 있으므로 요청부터 표시까지 한 흐름으로 확인합니다.

## 4. 새 화면이나 기능 추가하기

예를 들어 커뮤니티 게시글 기능을 추가할 때는 다음 순서로 진행합니다.

1. 게시글 목록·작성·수정·삭제의 범위와 API 계약을 정합니다. 로그인 필요 여부와 서버 권한 검증도 함께 정합니다.
2. `pages/CommunityPage.jsx`처럼 페이지를 만들고, 반복해서 쓰는 폼이나 목록 UI는 `components/`로 분리합니다.
3. `hooks/useHashRoute.js`의 허용 경로, `App.jsx`의 페이지 렌더링, `Layout.jsx`의 메뉴를 함께 연결합니다. 작성 모달을 쓰면 `App.jsx`의 모달 분기도 추가합니다.
4. 필요한 조회·저장 메서드를 `repository.js`에 추가합니다. 서버가 아직 없다면 local 구현과 예시 데이터로 먼저 화면을 연결합니다.
5. 공통 액션과 상태를 연결합니다. 작은 확장은 `RoomContext.jsx`를 활용하되, 게시글처럼 별도 영역이 커지면 전용 Context와 저장소 모듈로 나눕니다.
6. 로딩·빈 목록·실패·재시도·저장 중 상태를 구현하고, 성공 응답을 받은 뒤에만 목록을 갱신하거나 작성창을 닫습니다.

새 저장 기능은 현재의 중복 요청 방지와 실패 시 입력 유지 동작을 이어받도록 구현합니다. 데이터 영역을 추가하면 local 초기값과 API 초기 조회도 함께 확장합니다.

## 5. Spring Boot와 MySQL 연결하기

프론트는 MySQL에 직접 접속하지 않습니다. 백엔드가 인증·권한 확인과 DB 저장을 담당하고 프론트는 REST API를 호출합니다.

1. 백엔드 담당자와 [API_HANDOFF.md](./API_HANDOFF.md)의 엔드포인트·DTO를 확인합니다.
2. Spring Boot에 해당 API와 MySQL 저장 기능을 구현합니다.
3. 서버 응답이 합의한 형식과 다른 경우 `repository.js`에서 화면이 사용하는 형식으로 변환합니다.
4. 로그인·세션, CORS, CSRF 처리를 서버와 맞춥니다. 현재 API 어댑터는 쿠키를 포함해 요청하지만 로그인 흐름 자체는 별도 구현이 필요합니다.
5. `.env`를 변경하고 개발 서버를 재시작합니다.

```dotenv
VITE_DATA_MODE=api
VITE_API_BASE_URL=http://localhost:8080/api
```

배포할 때는 위 환경변수를 설정한 뒤 다시 빌드합니다. `VITE_` 변수는 브라우저에 공개되므로 MySQL 계정·비밀번호나 외부 서비스 비밀키를 넣지 않습니다.

API 모드에서 서버 요청이 실패하면 오류를 표시하며 브라우저 저장으로 자동 전환하지 않습니다. 기존 local 데이터도 서버로 자동 이관하지 않습니다. 실제 서버를 연결한 뒤 등록·수정·삭제·재조회와 로그인 만료·권한 오류를 확인합니다.

## 6. 계획서의 기능으로 확장할 때

아래는 앞으로 연결하거나 확장할 작업 방향입니다. 현재 화면이 계획서의 모든 요구사항을 완료한 상태는 아닙니다.

| 개발 영역 | 다음에 추가·변경할 내용 |
|---|---|
| 광고 등록·OCR | 이미지 업로드, 추출 진행·실패 상태, 원본과 추출값 확인 후 확정 |
| 공식 정보 대조 | 건축물대장 조회, 건물·호실 식별, 계획서의 7개 후보 항목과 근거·조회 상태 표시 |
| 개인 매물 관리 | 선택 삭제, 서버 목록 조회, 사용자별 접근 권한 |
| 방문 기록 | 사용자 점검 항목, 사진, 방문별 이력 저장 |
| 비교 화면 | 확장된 공식 대조·방문 결과·후기 통계를 같은 항목으로 비교 |
| 커뮤니티·후기 | 게시글·댓글·반응·신고, 서버 후기 통계 연결 |
| 회원 관리 | 구글 로그인, 로그인 상태 표시, 개인 자료·작성 기능 접근 제어 |

현재 공식 대조는 면적·층수 중심입니다. 현재 방문 기록은 매물당 한 기록을 덮어쓰므로 방문 이력을 추가하려면 데이터와 API도 확장해야 합니다. 거주 인증과 보상 기능은 계획서상 이번 학기 범위에서 제외되어 있습니다.

## 7. 변경할 때 유지할 데이터 기준

- 금액 저장·API 단위는 원, 면적은 ㎡입니다. 입력 화면의 만원 단위와 구분하고, 미입력 `null`과 숫자 `0`을 다르게 처리합니다.
- 광고와 공식 기록은 같은 대상·면적 기준일 때만 비교합니다. 공식 조회 실패와 비교할 값이 없는 상태를 구분합니다.
- 매물을 수정하면 이전 공식 결과가 여전히 유효한지 재확인합니다. 주소 문자열만으로 서로 다른 매물을 같은 건물에 합치지 않습니다.
- 후기 점수는 1~5이며 미응답은 평균 계산에서 제외합니다. 목록에 페이지네이션을 추가하면 일부 후기만으로 전체 평균을 계산하지 않도록 서버 통계 API를 연결합니다.
- 개인 매물 삭제 시 방문 기록은 함께 삭제하되 공개 후기는 보존합니다. 서버에서도 소유자·작성자 권한을 확인합니다.
- 방문 체크 항목은 현재 순서 인덱스로 저장됩니다. 순서를 바꾸거나 항목을 추가·삭제할 때 기존 기록이 다른 항목으로 해석되지 않도록 안정적인 항목 ID와 데이터 변환을 함께 설계합니다.

## 8. 팀 작업과 변경 확인

작업 시작 전에 최신 main을 받고 기능별 브랜치를 만듭니다. 아래 이름은 예시입니다.

```bash
git switch main
git pull --ff-only origin main
git switch -c feat/room-available-date
```

한 변경은 입력·저장·표시까지 확인할 수 있는 기능 단위로 묶습니다. `App.jsx`, `RoomContext.jsx`, `repository.js`, `styles.css`는 여러 기능이 함께 수정할 수 있으므로 작업 범위를 팀원과 맞춥니다. API 변경은 코드와 `API_HANDOFF.md`에 함께 반영합니다.

```bash
npm test
npm run build
```

동작을 바꾸면 관련 테스트를 추가·수정합니다. `tests/domain.test.js`는 계산 규칙, `tests/App.test.jsx`는 사용자 흐름, `tests/repository.test.jsx`는 저장소·API 실패 흐름을 확인하는 출발점입니다. 화면 변경은 브라우저에서 모바일 크기와 입력·모달 동작도 확인합니다. 실제 서버 연결은 프론트 테스트와 별도로 검증합니다.

코드·문서·설정 예시를 커밋하고 팀 리뷰 후 main에 합칩니다. `.env`, `node_modules/`, DB 비밀정보는 커밋하지 않습니다. 패키지를 추가하면 `package.json`과 `package-lock.json`을 함께 반영합니다.

## 9. 로컬 매물 DB 연결 (기본 구조)

`backend/`의 Spring Boot API는 `/api/listings`에 매물 등록·조회·수정·삭제를 제공합니다. Docker와 Node.js 22.12 이상이 필요합니다.

```bash
./scripts/dev-up.sh            # MySQL과 백엔드 시작, .env의 DB 비밀번호가 비어 있으면 생성
./scripts/install-frontend.sh  # npm 고정 버전 설치
VITE_DATA_MODE=listing-api VITE_API_BASE_URL=/api npm run dev
```

`listing-api`는 **매물만 MySQL**에 저장합니다. 후기·방문 기록은 브라우저에 남고, 기존 가상 매물은 이 모드에서 실제 DB 매물과 섞이지 않습니다. 기존 `local`과 전체 API 계약용 `api` 모드는 유지됩니다. 프론트엔드 개발 서버는 `/api`를 로컬 백엔드 8080 포트에 전달합니다. 로그인과 소유권 검증은 아직 없으므로 로컬 개발에서만 사용하세요.

```bash
npm test && npm run build
./scripts/build-backend.sh
curl http://127.0.0.1:8080/api/listings
docker compose down  # DB 데이터 볼륨 유지
```

금액은 원 단위 정수, 면적은 ㎡, 미입력은 `null`입니다. 광고 원문은 `original`에 보관하고 새 매물마다 별도 building ID를 발급합니다. `backend/src/main/resources/application.yml`의 JPA `ddl-auto=update`는 로컬 개발용입니다. 정식 운영 전에는 스키마 마이그레이션과 인증·소유권 검증이 필요합니다. 비밀번호가 담긴 `.env`와 빌드 산출물은 Git에서 제외합니다.

## 10. SFR-002 광고 원본 등록 (로컬 개발)

`listing-api` 모드의 매물 화면에 **광고 원본 등록** 영역이 있습니다. 글 또는 JPG·PNG 이미지(최대 10MB)를 입력하고 주소 힌트·동호수·출처 URL을 선택적으로 넣어 임시 저장할 수 있습니다. **원본 확정**은 상태를 `DRAFT`에서 `EXTRACTION_PENDING`으로 바꾸지만 OCR·AI를 실행하지는 않습니다. 기존 매물 등록과 광고 원본 등록은 아직 별도 흐름입니다. 예시 매물과 실제 DB 광고 원본을 섞어 표시하지 않습니다.

광고 메타데이터는 MySQL `advertisements` 테이블에 저장하고, 원본 이미지는 공개 경로가 아닌 Docker `room_uploads` 볼륨에 보관합니다. `docker compose down`은 두 볼륨을 유지합니다. `down -v`는 저장 데이터를 지우므로 팀 데이터를 다루는 환경에서 사용하지 마세요. 등록 요청은 `Idempotency-Key`를 사용해 같은 요청을 재시도해도 원본을 하나만 남기며, 같은 키로 다른 내용을 보내면 409를 반환합니다. 자세한 필드와 응답은 [API_HANDOFF.md](./API_HANDOFF.md)에 있습니다.

```bash
./scripts/dev-up.sh
VITE_DATA_MODE=listing-api VITE_API_BASE_URL=/api npm run dev
npm test
npm run build
./scripts/build-backend.sh
```

현재 광고 조회·확정 API에는 로그인과 사용자별 접근 제어가 없습니다. 외부 공개나 실사용 광고 업로드에는 사용하지 말고, SFR-001 인증·소유권 검증을 연결한 뒤 사용하세요.

## 11. SFR-003 Gemini 추출 (로컬 개발)

확정한 광고에서 **Gemini 추출 시작**을 누르면 광고 글과 저장된 이미지를 서버에서 Gemini API로 보내고, 광고 원문·주소·보증금·월세·관리비·면적·층수를 구조화합니다. 응답은 `EXTRACTION_READY`로 보관하며, 사용자가 원본과 비교해 값을 고쳐 **확인한 값 저장**을 눌러야 `REVIEWED`가 됩니다. **확인한 값으로 매물 등록**을 누르면 기존 매물 폼이 채워집니다. 이름·주소·값을 수정하고 저장해야 매물이 생성됩니다. 광고 값은 건축물대장 공식 정보로 사용하지 않습니다. 실패하면 `EXTRACTION_FAILED`가 표시됩니다. 이미 요청 기록이 있는 광고는 검증 목적으로 반복 호출하지 않습니다.

외부 API 사용은 기본적으로 **꺼져 있습니다**. 실제 무료 등급의 사용 가능 여부와 한도를 계정에서 확인한 뒤, 로컬 `.env`에만 다음 값을 설정하고 `./scripts/dev-up.sh`를 다시 실행하세요. API 키 전체 값을 채팅이나 Git에 올리지 마세요.

```dotenv
GEMINI_ENABLED=true
GEMINI_API_KEY=본인_키를_로컬에서만_입력
GEMINI_MODEL=gemini-3.5-flash-lite
```

`GEMINI_ENABLED=false` 또는 키가 비어 있으면 추출 API는 503을 반환하고 외부 요청을 보내지 않습니다. 무료 등급은 모델별 호출 한도가 있고 조건이 바뀔 수 있으므로, 실제 호출 전 AI Studio에서 해당 프로젝트의 결제 등급과 한도를 확인하세요. 한도 초과 시 429를 반환합니다. `gemini-3.5-flash-lite`로 개인정보 없는 합성 글 광고의 실제 REST 추출과 Spring Boot→Gemini→MySQL 저장·재조회가 성공했습니다. 대표 광고 20건의 정확도와 이미지 OCR은 아직 평가하지 않았습니다. 테스트 서버 주소는 `GEMINI_BASE_URL`로 바꿀 수 있지만 실제 키와 함께 신뢰할 수 없는 서버 주소를 사용하지 마세요. 상세 결과와 클라우드 Java 프록시·CA 설정 범위는 [검증 기록](docs/sfr003/VALIDATION.md)을 참고하세요.

### 일반 실행의 프록시·인증서 설정

`./scripts/dev-up.sh`는 모델 기본값 `gemini-3.5-flash-lite`를 사용하는 Compose 백엔드를 빌드하고 실행합니다. 클라우드에서는 상속된 `HTTPS_PROXY`와 공개 CA 파일 `CODEX_PROXY_CERT`(또는 `JAVA_PROXY_CA_FILE`)을 사용해 `.runtime/compose.java-runtime.json`을 생성합니다. 컨테이너 시작 시 Java HTTPS 프록시와 임시 신뢰 저장소에 적용하며 TLS 검증을 유지합니다. 프록시·CA 설정이 없는 일반 로컬 환경에서는 기본 Java 신뢰 설정을 사용합니다. API 키는 환경에서만 전달하며 생성된 runtime 설정 파일에는 넣지 않습니다. `DOCKER_CONFIG`와 기존 레지스트리 인증은 유지합니다.

runtime 설정이 생성된 뒤 스택 상태 확인·종료 시에도 같은 Compose 파일을 사용합니다. 기본 종료 명령은 DB 볼륨을 삭제하지 않습니다.

```sh
docker compose -f compose.yaml -f .runtime/compose.java-runtime.json ps
docker compose -f compose.yaml -f .runtime/compose.java-runtime.json down
```

키 없는 읽기 요청으로 일반 컨테이너의 Java 프록시·CA 통신을 확인했고, 새 실제 생성 요청은 하지 않았습니다. 설정에 문제가 있으면 CA 파일·프록시 형식·DNS를 점검합니다. 인증 정보가 포함된 프록시 URL은 현재 helper에서 지원하지 않으며 URL이나 비밀값을 오류 메시지로 출력하지 않습니다.

### 화면 흐름과 평가 준비 점검

```sh
npm test
npm run test:runtime
npm run test:browser
npm run evaluation:check
```

브라우저 검증에는 Docker, Python 3, 시스템 Chromium과 npm 의존성이 필요합니다. Chromium 경로가 다르면 `CHROMIUM_PATH`를 설정합니다. `test:browser`는 별도 Compose 프로젝트와 임시 DB 볼륨, 로컬 가짜 추출 서버를 사용해 **광고 등록 → 확정 → 추출 → 확인 값 수정·저장 → 매물 폼 수정·등록 → 새로고침**을 실제 Chromium에서 검증하고 테스트 스택만 정리합니다. 외부 Gemini 생성 요청은 0건입니다. 인증·공식정보 기능은 추가하지 않습니다.

20건 평가에 필요한 원본·정답·CSV 작성 규칙은 [평가 자료 준비](docs/sfr003/EVALUATION_INPUTS.md)에 있습니다. `evaluation:check`는 준비된 자료 수만 확인하며 API 호출이나 정확도 계산은 하지 않습니다.
