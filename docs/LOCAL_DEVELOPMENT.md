# 로컬 화면 실행

React 화면만 실행한다. 백엔드·DB·로그인 서버는 필요하지 않으며 예시 데이터와 브라우저 localStorage를 사용한다.

## 시작

Node.js 22.12 이상이 필요하다. 저장소 루트에서 실행한다.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\frontend\run-local.ps1
```

스크립트는 필요한 프론트 의존성을 설치하고 VITE_DATA_MODE=local로 실행한다. 주소는 http://127.0.0.1:4173 이며 종료는 터미널에서 Ctrl+C다. 백엔드를 실행하지 않는다.

스크립트 없이 frontend 폴더에서 실행할 수도 있다.

```powershell
npm.cmd ci
$env:VITE_DATA_MODE = 'local'
npm.cmd run dev
```

## 확인 범위

매물 등록·수정·삭제, 비교 화면, 후기 작성, 방문 체크·메모를 눌러볼 수 있다. 입력은 같은 브라우저·같은 주소의 localStorage에 저장된다. 다른 팀원이나 다른 브라우저와 자료가 공유되지 않는다. 초기 매물·공식 기록·후기는 가상 예시다.

백엔드에는 기존 기능별 패키지 구조와 핵심 7개 테이블 MySQL 설계만 준비되어 있다. 업무 API·인증·실제 DB 연결은 각 담당자가 이후 구현한다. [DB 설계](database/README.md), [담당 기능 위치](architecture/REQUIREMENTS.md), [한국어 주석 기준](development/COMMENTS.md)을 참조한다.
