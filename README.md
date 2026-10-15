# RoomCrossCheck

원룸 광고, 공식 건축물 정보, 개인 방문 기록과 공개 거주 후기를 모아 관심 매물 2~3개를 비교하는 서비스입니다. `4조_캡스톤디자인.hwp`의 기능 범위를 기준으로 React 프론트엔드와 Spring Boot 백엔드를 분리합니다.

```text
RoomCrossCheck/
├── frontend/                 # React · Vite, 화면과 브라우저 상태
│   ├── src/app/              # 앱 시작, 라우팅, 전체 상태, 저장소 구성
│   ├── src/features/         # 기능별 화면·컴포넌트·화면 계산
│   ├── src/shared/           # 공통 UI·표시 유틸리티
│   └── tests/                # 기존 사용자 흐름과 저장소 테스트
├── backend/                  # Java 21 · Spring Boot, 업무 규칙과 외부 연동
│   └── src/main/java/com/roomcrosscheck/
│       ├── config/           # Spring 구성과 의존성 연결
│       ├── common/           # 공통 오류 등 최소 공통 계약
│       └── <기능>/           # controller / service / domain / repository / dto
│                            # 외부 API가 필요한 기능은 client 포함
└── docs/
    ├── architecture/         # GRASP·SOLID, 책임과 의존 방향
    ├── api/                  # 프론트·백엔드 API 계약
    └── database/             # MySQL ERD·데이터 사전·생성 안내
```

프론트엔드는 기존 화면과 local/API 저장 모드를 유지합니다. 백엔드는 시작 가능한 기본 골격이며 업무 API·로그인·DB 저장·외부 연동은 이후 구현합니다. DB는 핵심 7개 테이블 설계를 유지하고 별도 Flyway 명령으로 관리합니다. 로컬 화면과 서버 실행에는 MySQL이 필요하지 않습니다.

## 로컬 화면만 실행

백엔드·DB 없이 React 예시 화면을 실행합니다.

```powershell
cd frontend
npm ci       # 처음 설치할 때
npm run dev
```

주소는 **http://127.0.0.1:4173** 입니다. 입력은 각자의 브라우저에 저장하며 팀원과 공유하지 않습니다. 백엔드 업무 기능·DB 연결은 담당자가 이후 구현합니다. [로컬 실행 안내](docs/LOCAL_DEVELOPMENT.md)를 참조하세요.

## 프론트엔드 실행

Node.js 22.12 이상에서 실행합니다.

```powershell
cd frontend
npm ci
npm run dev
```

기본 주소는 `http://127.0.0.1:4173`입니다. 백엔드 구현 전에는 `VITE_DATA_MODE=local`을 사용합니다.

```powershell
# frontend 폴더에서
npm test
npm run build
```

## 백엔드 실행

VS Code에서 Java 21 환경으로 `backend/src/main/java/com/roomcrosscheck/RoomCrossCheckApplication.java`의 Run을 누르면 됩니다. 터미널에서는 Maven 3.9 이상으로 아래 명령을 사용합니다. Maven Wrapper는 아직 포함하지 않습니다.

```powershell
cd backend
mvn test
mvn spring-boot:run
```

기본 포트는 8080입니다. 현재 모든 HTTP 요청은 거부하도록 구성되어 있으며 인증·권한 정책과 업무 API를 구현한 뒤 필요한 공개 경로를 허용합니다. 프론트의 `api` 모드는 [API 계약](docs/api/API_HANDOFF.md)에 정의한 업무 API 구현 후 연결합니다.

## 개발 문서

- [로컬 화면 실행](docs/LOCAL_DEVELOPMENT.md)
- [한국어 주석 작성 기준](docs/development/COMMENTS.md)
- [전체 구조와 GRASP·객체지향 원칙](docs/architecture/ARCHITECTURE.md)
- [계획서 요구사항별 개발 위치](docs/architecture/REQUIREMENTS.md)
- [프론트엔드 개발 가이드](frontend/README.md)
- [백엔드 개발 가이드](backend/README.md)
- [현재 프론트엔드 API 계약](docs/api/API_HANDOFF.md)
- [MySQL 설계·ERD·생성 SQL](docs/database/README.md)

`chore/split-frontend-backend`에서 구조를 정리하고 기능별 브랜치에서 구현합니다. 의존 경로가 바뀌면 관련 테스트와 문서를 함께 갱신합니다. 개인 매물을 삭제해도 건물에 연결한 공개 후기는 유지하며, 조회 실패와 비교할 값이 없는 상태는 구분합니다.
