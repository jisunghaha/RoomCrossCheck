# RoomCrossCheck

원룸 광고 원본을 저장하고, Gemini 추출값을 사용자가 확인·수정한 뒤 매물로 등록하는 React·Spring Boot·MySQL 로컬 개발 프로젝트입니다.

현재 SFR-003 기능 브랜치에서 팀과 같은 폴더 배치와 단일 단계형 등록 흐름으로 정리했습니다. 팀 브랜치를 병합한 것은 아니며, 팀의 `rooms` DB·실제 인증으로 전환한 상태도 아닙니다. 차이와 합의할 계약은 [통합 준비 기록](docs/sfr003/TEAM_INTEGRATION.md)을 참고하세요.

## 구조

```text
frontend/
  src/app/                   앱 조합·상태·라우팅·저장소·레이아웃
  src/features/listing/      매물 목록·단계형 등록·수정
  src/features/extraction/   광고 원본·Gemini 추출·확인 값
  src/features/review/       기존 후기 화면
  src/features/visit/        기존 방문 기록 화면
  src/features/comparison/   기존 비교 화면
  src/shared/                공통 UI·표시·계산
  tests/                     React 회귀 테스트
backend/
  src/main/java/com/roomcrosscheck/
    listing/                 controller/service/domain/repository/dto
    extraction/              client/service/dto
scripts/                     로컬 실행·브라우저·평가 준비 검증
```

## 설치와 실행

Node.js 22.12 이상, Python 3, Docker가 필요합니다. 저장소 루트에서 npm workspace 의존성을 설치합니다.

```sh
npm ci
npm run dev
```

기본은 브라우저 저장 `local` 모드입니다. 프론트 설정은 `frontend/.env.example`을 참고하세요. `frontend/.env`의 공개 설정을 우선하며, 기존 루트 `.env`의 `VITE_DATA_MODE`·`VITE_API_BASE_URL`도 호환합니다. 프로세스 환경변수가 가장 우선합니다. API 키·DB 비밀번호는 `VITE_` 변수나 프론트 파일에 넣지 않습니다. 기존 localStorage 키를 유지해 브라우저 자료를 보존합니다.

MySQL 매물·광고 API를 사용하려면 다음을 실행합니다.

```sh
./scripts/dev-up.sh
VITE_DATA_MODE=listing-api VITE_API_BASE_URL=/api npm run dev
```

`dev-up.sh`는 기존 루트 `.env`의 DB 설정을 보존하고 비어 있는 개발 DB 비밀번호만 생성합니다. 프론트는 기본 4173, 백엔드는 8080이며 `/api`를 프록시합니다. `ROOM_BACKEND_PORT`로 백엔드 포트를 변경하면 프론트의 `DEV_API_TARGET`도 해당 주소로 설정합니다. `frontend` 폴더에서 `npm run dev`, `npm test`, `npm run build`를 실행해도 됩니다. 루트 명령은 같은 프론트 workspace를 호출합니다.

## 등록 흐름

새 매물 등록은 `#register` 한 곳에서 진행합니다.

1. 직접 입력 또는 광고 글·사진에서 가져오기를 선택합니다.
2. 직접 입력은 광고 글·이미지 없이 주소부터 시작합니다. 서버 광고는 원본 임시 저장 → 확정 → 명시적 추출 → 사용자 확인 값 저장 순서입니다.
3. 확인한 광고 값 또는 직접 입력값을 같은 매물 폼에서 수정합니다. 금액은 화면에서 만원, 저장 시 정수 원이며 null과 0을 구분합니다.
4. 최종 확인 화면의 **확정 저장**에서만 매물이 생성됩니다. 실패하면 내용을 유지하고, 내용 수정·이전 단계로 돌아갈 수 있습니다.

광고 원본·AI 추출값·광고 확인 값·매물 폼에서 수정한 값은 별개입니다. 공식 건축물 정보에 광고 값을 넣지 않습니다. 직접 입력의 임시 매물 저장과 팀의 DRAFT 매물 이어가기는 서버 계약 합의 후 연결할 범위입니다. 광고 원본 임시 저장은 현재 동작합니다.

`listing-api` 모드는 현재 `listings`·`advertisements`를 사용하는 로컬 개발용 연결입니다. `api` 모드의 `/rooms` 등은 별도 서버 계약이며 팀의 실제 업무 API가 구현됐다는 뜻이 아닙니다. 로그인과 소유권 검증은 구현하지 않았고, 임의 사용자 ID를 요청에 넣지 않습니다. 후기·방문 기록은 listing-api 모드에서도 브라우저에 남습니다.

## Gemini와 프록시

외부 생성 요청은 기본적으로 꺼져 있습니다. 무료 계정·모델 한도를 확인한 경우에만 `GEMINI_ENABLED=true`로 활성화합니다. 키는 서버 환경으로만 전달하며 `.env`·시크릿은 Git에 넣지 않습니다. 기본 모델은 실제 성공 기록이 있는 `gemini-3.5-flash-lite`입니다. 기존 smoke-002~006 광고는 재호출하지 않습니다.

일반 `dev-up.sh`는 상속된 `HTTPS_PROXY`와 공개 CA `CODEX_PROXY_CERT` 또는 `JAVA_PROXY_CA_FILE`로 `.runtime/compose.java-runtime.json`을 생성합니다. 컨테이너 진입점이 Java 프록시·CA를 적용하고 TLS 검증을 유지합니다. API 키는 이 생성 파일에 넣지 않습니다. 프록시가 없으면 기본 Java 신뢰 설정을 사용합니다. Docker 설정·레지스트리 인증은 유지합니다.

```sh
docker compose -f compose.yaml -f .runtime/compose.java-runtime.json ps
docker compose -f compose.yaml -f .runtime/compose.java-runtime.json down
```

기본 down은 DB 볼륨을 삭제하지 않습니다. 백엔드는 패키지 이동 전의 클래스가 남지 않도록 clean 빌드합니다. 로컬 JPA ddl-auto=update는 팀의 Flyway V1을 대체하지 않습니다. 기존 DB 자동 변환·baseline·테이블 삭제는 수행하지 않습니다.

## 검증

```sh
npm test
npm run build
npm run test:runtime
./scripts/build-backend.sh
npm run test:browser
npm run evaluation:check
```

브라우저 검증은 시스템 Chromium을 사용합니다. 경로가 다르면 `CHROMIUM_PATH`를 설정하세요. 일반 Compose·실제 MySQL·로컬 추출 서버로 화면을 조작하며 실제 Gemini 생성 요청은 하지 않습니다. 테스트가 생성한 임시 프로젝트·볼륨만 정리합니다.

## 문서와 남은 작업

- [프론트 개발 위치](frontend/README.md)
- [백엔드 현재 구현 범위](backend/README.md)
- [API 계약](docs/api/API_HANDOFF.md)
- [팀 구조·DB·소유권 통합 제안](docs/sfr003/TEAM_INTEGRATION.md)
- [실제 Gemini·로컬 검증 기록](docs/sfr003/VALIDATION.md)
- [20건 평가 자료 준비](docs/sfr003/EVALUATION_INPUTS.md)

실제 Gemini 글 광고 smoke 성공은 기록돼 있지만 대표 광고 20건 정확도, 이미지 OCR, 실제 Gemini를 포함한 브라우저 전체 흐름은 별도 검증이 필요합니다. 현재 20건 자료 준비는 0/20입니다. 다른 브랜치나 main을 변경하지 않고 이 기능 브랜치에서 작업합니다.
