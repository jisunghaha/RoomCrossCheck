# 로컬 실행

## 프론트엔드

Node.js 22.12 이상에서 프로젝트 루트의 PowerShell을 열고 실행한다.

```powershell
cd frontend
npm ci
npm run dev
```

`npm ci`는 처음 설치하거나 package-lock.json이 바뀌었을 때 실행한다. 이후에는 `frontend`에서 `npm run dev`만 실행한다. 접속 주소는 http://127.0.0.1:4173 이며 종료는 Ctrl+C다. 기존 `run-local.ps1`은 선택용 편의 스크립트다.

기본 저장 방식은 `local`이다. 기존 환경변수가 API 모드라면 `frontend/.env.local`의 `VITE_DATA_MODE=local`을 확인한다. 현재 터미널에서 명시하려면 아래처럼 실행한다.

```powershell
$env:VITE_DATA_MODE = 'local'
npm run dev
```

## 백엔드

Java 21을 준비하고 VS Code에서 `backend/src/main/java/com/roomcrosscheck/RoomCrossCheckApplication.java`의 main 위에 있는 Run을 누른다. 터미널의 `Started RoomCrossCheckApplication` 로그로 시작을 확인한다.

Maven 3.9 이상을 설치했다면 별도 PowerShell에서도 실행할 수 있다.

```powershell
cd backend
mvn spring-boot:run
```

기본 주소는 `127.0.0.1:8080`이다. 공개 업무 경로가 없으므로 브라우저에서 8080에 접속하면 접근 거부 응답이 나올 수 있다. 서버 시작 실패를 뜻하지 않는다. DB·로그인 없이 서버 프로세스를 실행한다.

## DB 준비

MySQL은 화면을 여는 데 필수가 아니다. DB 작업을 시작할 때 [DB 안내](database/README.md)에 따라 빈 `roomcrosscheck` DB를 만들고 `mvn -Pdb flyway:migrate`로 테이블을 적용한다. Flyway는 별도 Maven 도구이며 서버 실행 시 자동으로 DB에 접속하지 않는다.

## 현재 확인 범위

매물 등록·수정·삭제, 비교, 후기 작성, 방문 체크·메모는 기존 프론트 기능을 사용한다. 입력은 같은 브라우저·같은 주소의 localStorage에 저장되며 다른 팀원과 공유되지 않는다. 초기 매물·공식 기록·후기는 가상 예시다.

이번 구성은 로컬 실행과 DB 스키마 관리까지다. 업무 API·인증·화면 데이터의 MySQL 저장은 담당 기능을 개발할 때 연결한다. 현재 `VITE_DATA_MODE=api`로 전환하면 아직 구현하지 않은 API를 요청하게 된다.
