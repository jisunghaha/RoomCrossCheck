# 원룸체크 · React 프론트엔드

기존 원룸체크 화면과 기능을 React 컴포넌트로 전환한 프로젝트입니다. React 19, Vite 7, CSS를 사용합니다. 테스트는 Vitest와 React Testing Library로 구성합니다. 정확한 설치 버전은 package-lock.json에 고정되어 있습니다.

## 실행

Node.js 22.12 이상을 설치하고 이 폴더에서 실행하세요.

```bash
npm ci
npm run dev
```

터미널에 표시되는 주소(기본 http://127.0.0.1:4173)를 엽니다. HTML 파일 더블 클릭은 지원하지 않습니다.

```bash
npm test          # 계산 규칙 및 React 사용자 흐름 테스트
npm run build     # dist/에 배포용 파일 생성
npm run preview   # 빌드 결과 확인
```

## 구조

```text
src/
  main.jsx                    React 진입점, StrictMode, Context Provider
  App.jsx                     페이지 전환과 모달 상태
  components/
    Layout.jsx                사이드바, 헤더, 푸터
    UI.jsx                    아이콘, 제목, 상태 배지, 평점, 공간 일러스트
    RoomCard.jsx              매물 카드와 비교 선택
    RoomDetail.jsx            원문, 대조 결과, 후기 요약
    RoomForm.jsx              매물 등록·수정 controlled form
    ReviewForm.jsx            항목형 후기 controlled form
    Modal.jsx                 dialog 생명주기, Escape, 접근성 이름
  pages/
    RoomsPage.jsx             관심 매물, 검색, 필터, 정렬
    ComparePage.jsx           2~3개 매물 비교표
    ReviewsPage.jsx           건물별 후기와 평균
    VisitsPage.jsx            방문 체크리스트와 메모
  context/RoomContext.jsx     매물·후기·방문 공통 상태, 저장/삭제 액션
  hooks/useHashRoute.js       URL hash 라우팅 (#rooms 등)
  lib/domain.js               금액, 정보 대조, 후기 통계 계산
  lib/storage.js              기존 브라우저 데이터 불러오기
  data/seed.js                비식별 가상 매물·후기
  styles.css                 기존 디자인, 모바일 대응
index.html                   Vite 진입 HTML
vite.config.js               Vite/React/Vitest 설정
API_HANDOFF.md               추후 백엔드 연결 안내
```

수정할 화면은 pages/, 반복 UI는 components/에서 찾으면 됩니다. 신규 필드는 RoomForm과 RoomContext, 비교표에 함께 반영합니다. 화면을 HTML 문자열로 주입하지 않고 JSX, props, useState, Context와 React 이벤트로 구현했습니다. 동적 사용자 텍스트는 React 기본 이스케이프를 사용합니다.

## 구현 범위

매물 등록·수정·삭제, 검색·필터·정렬, 2~3개 비교, 대조 근거 표시, 항목형 후기 작성·삭제·통계 갱신, 개인 방문 기록을 제공합니다. 브라우저 localStorage에 저장하며 원래 프로토타입의 `oneroom.frontend.v1` 키를 유지해 같은 사이트의 기존 데이터가 이어집니다. 비교 선택은 새로고침 시 초기화됩니다. 방문 기록은 저장 버튼을 눌러야 반영됩니다.

초기 매물·공식 기록·후기는 가상 데이터입니다. 이미지 대신 CSS 공간 일러스트를 사용합니다. Google Fonts 연결 실패 시 시스템 글꼴로 표시합니다.

실제 로그인·권한, 이미지 업로드, OCR·AI, 건축물대장 API, DB·Redis, 커뮤니티·신고 관리자·거주 인증은 구현하지 않았습니다. 서버 저장이나 사용자 간 공유·동기화도 제공하지 않습니다. 최신 요구사항 전체의 완료본이 아니라 프론트엔드 기본 구현입니다.

## 계산과 연결 기준

- 금액은 원, 면적은 ㎡로 저장합니다. 입력 화면은 만원이며 미입력 null과 0원을 구분합니다.
- 월세와 관리비가 모두 있어야 월 고정비를 계산합니다.
- 대조는 같은 호실·같은 면적 종류만 비교합니다. 면적 오차는 0.5㎡, 층수는 동일값입니다. 일치율 분모는 비교 가능한 항목 수이며 분모 0은 N/A입니다.
- 매물 편집 시 기존 공식 기록을 해제하여 재확인 전 이전 판정이 유지되지 않게 합니다.
- 후기 점수는 1~5점이며 높을수록 양호합니다. 미응답을 평균 분모에서 제외합니다. 1~2건은 표본 부족으로 표시합니다.
- 주소 문자열만으로 다른 매물을 같은 건물에 합치지 않습니다. 새 매물에는 새 building ID가 부여됩니다. 실제 API 연결 시 명시적인 건물 선택 과정이 필요합니다.
- 개인 매물 삭제는 개인 방문 기록만 함께 삭제합니다. 공개 후기는 보존됩니다. 현재 후기 화면은 저장한 매물의 건물만 표시합니다.

## 테스트

계산 규칙 5개와 React 사용자 흐름 4개를 제공합니다. 등록·수정·재로드, 비교 최소 선택 수·대조 결과, 후기 생성·삭제, 방문 기록 저장과 매물 삭제 범위를 확인합니다. dialog는 jsdom에서 생명주기 메서드만 보완합니다. 외부 서비스나 실제 부하 성능을 검증한 결과는 아닙니다.
