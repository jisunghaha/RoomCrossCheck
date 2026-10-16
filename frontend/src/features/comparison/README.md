# 관심 매물 비교

계획서: SFR-008

소유자의 서로 다른 매물 2~3개와 최신 방문·공식 대조·건물 후기 통계를 조회해 같은 항목으로 구성한다.

화면은 `pages/`, 해당 기능의 UI는 `components/`, React에 의존하지 않는 화면 계산은 `domain/`, 기능 상태는 필요할 때 `model/`에 둡니다. 서버 요청은 앱에서 주입한 저장소를 통해 수행합니다. 현재 없는 하위 폴더와 기능은 구현할 때 추가합니다.

서버 규칙과 연결: `backend/src/main/java/com/roomcrosscheck/comparison/` (저장소 루트 기준).
