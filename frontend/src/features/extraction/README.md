# OCR·AI 정보 추출

계획서: SFR-003

광고 인식·구조화·정규화와 추출 상태를 관리한다. 없는 값을 추측하지 않고 실패에도 원본을 유지한다.

화면은 `pages/`, 해당 기능의 UI는 `components/`, React에 의존하지 않는 화면 계산은 `domain/`, 기능 상태는 필요할 때 `model/`에 둡니다. 서버 요청은 앱에서 주입한 저장소를 통해 수행합니다. 현재 없는 하위 폴더와 기능은 구현할 때 추가합니다.

서버 규칙과 연결: `backend/src/main/java/com/roomcrosscheck/extraction/` (저장소 루트 기준).
