# RoomCrossCheck

원룸 매물의 광고·공식정보 대조와 거주 후기 비교를 위한 프로젝트입니다.

## 프론트엔드

React + Vite 기반 시제품은 [`frontend/`](frontend/)에 있습니다.

Node.js 22.12 이상 환경에서 실행합니다.

```bash
cd frontend
npm ci
npm run dev
```

```bash
npm test
npm run build
```

- 매물 등록·수정·삭제, 검색·필터·정렬
- 2~3개 매물 비교 및 공식정보 대조 결과 표시
- 8개 항목 거주 후기 작성·통계
- 방문 체크리스트와 메모 저장

현재는 가상 데이터와 브라우저 저장으로 동작합니다. 실제 로그인·OCR·공공 API·백엔드 연동은 포함하지 않습니다.

[프론트엔드 구조 및 실행 안내](frontend/README.md) · [백엔드 연결 안내](frontend/API_HANDOFF.md)
