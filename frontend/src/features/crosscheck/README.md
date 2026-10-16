# 광고·공식 기록 대조

계획서: SFR-006

동일 대상·기준의 7개 후보를 규칙으로 대조한다. 조회 상태와 항목별 결과를 분리하고 AI에 판단을 맡기지 않는다.

화면은 `pages/`, 해당 기능의 UI는 `components/`, React에 의존하지 않는 화면 계산은 `domain/`, 기능 상태는 필요할 때 `model/`에 둡니다. 서버 요청은 앱에서 주입한 저장소를 통해 수행합니다. 현재 없는 하위 폴더와 기능은 구현할 때 추가합니다.

서버 규칙과 연결: `backend/src/main/java/com/roomcrosscheck/crosscheck/` (저장소 루트 기준).
