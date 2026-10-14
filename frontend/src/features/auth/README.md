# 회원 인증·접근 권한

계획서: SFR-001

Google OAuth 로그인과 사용자 식별·역할. 개인 자료 소유권은 해당 매물·방문 유스케이스에서도 검증한다.

화면은 `pages/`, 해당 기능의 UI는 `components/`, React에 의존하지 않는 화면 계산은 `domain/`, 기능 상태는 필요할 때 `model/`에 둡니다. 서버 요청은 앱에서 주입한 저장소를 통해 수행합니다. 현재 없는 하위 폴더와 기능은 구현할 때 추가합니다.

서버 규칙과 연결: `backend/src/main/java/com/roomcrosscheck/auth/` (저장소 루트 기준).
