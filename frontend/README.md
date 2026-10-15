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

`frontend/.env`의 공개 설정을 우선하고 기존 루트 `.env`의 두 VITE 설정을 호환합니다. API 키와 DB 정보는 프론트 설정에 넣지 않습니다. 팀의 home·community·시연 로그인 기능은 이번 작업에서 복사하거나 구현하지 않았습니다. 원래 매물·비교·후기·방문 UI를 유지했습니다.

팀의 등록 화면 단계 구조와 RoomForm의 initialValues/onPreview 계약을 참고해 현재 서버 광고 흐름을 연결했습니다. 팀 화면에서 local 시연용인 임시 매물 저장·이미지 미리보기와 달리, 현재 광고 원본은 기존 백엔드에 보관합니다. /rooms의 DRAFT·CONFIRMED 및 사용자 소유권 연결은 합의 전이며, 현재 /listings 저장을 그 계약으로 표시하지 않습니다.
