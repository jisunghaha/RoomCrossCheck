# 백엔드 연결 안내

현재 `src/context/RoomContext.jsx`의 saveRoom, deleteRoom, addReview, deleteReview, saveVisit을 API 호출로 교체하는 구조입니다. 페이지는 useRooms()로 상태와 액션을 사용하므로 UI 컴포넌트를 유지한 채 연결할 수 있습니다. 최초 목록 로딩은 Provider의 useEffect에서 구현하고 로딩·오류 상태를 함께 제공하세요. 아래는 제안 계약이며 서버가 실제로 구현되어 있지는 않습니다.

| 화면 동작 | 제안 API |
|---|---|
| 저장 매물 목록 | GET /api/listings?page=0&size=20 |
| 매물 등록 | POST /api/listings (Idempotency-Key 헤더) |
| 상세·수정·삭제 | GET/PATCH/DELETE /api/listings/{id} |
| OCR·AI 추출 요청 | POST /api/listings/{id}/extractions |
| 공식 기록 갱신 | POST /api/listings/{id}/official-lookups |
| 공식정보 대조 | POST /api/listings/{id}/comparisons |
| 매물 비교 | POST /api/listings/compare {ids: [...]} |
| 건물 후기 목록·작성 | GET/POST /api/buildings/{id}/reviews |
| 본인 후기 변경·삭제 | PATCH/DELETE /api/reviews/{id} |
| 항목 통계 | GET /api/buildings/{id}/review-statistics |
| 방문 기록 | GET/POST /api/listings/{id}/visits |

금액 DTO는 원 단위 정수 또는 null, 면적은 제곱미터와 면적 종류를 함께 보냅니다. 현재 프론트의 seedRooms는 개발 편의상 영문 키를 사용하며 ID는 문자열입니다. 건물 ID와 사용자 개인 매물 ID를 분리합니다.

외부 조회 상태(대기·성공·실패·자료 없음)와 항목별 대조 판정을 별도 모델로 확장하세요. 공식 기록에는 targetScope, 건물·호실 식별자, 원본 조회 시각, 출처와 규칙 버전을 반환하도록 합니다. 현재 화면은 가상 official 값 또는 미실시 상태만 지원합니다.

현재 localStorage는 인증이나 접근 제어를 제공하지 않습니다. 실제 로그인 후에는 서버 소유자 검증이 있는 API로 전환하고 토큰·개인 기록을 공용 캐시에 저장하지 않습니다. 세션 만료는 401, 무권한은 403, 저장 충돌은 409로 처리하고 오류 형식은 `{code, message, requestId}`로 통일하는 것을 권장합니다.

후기 추가·변경·삭제·숨김 후 서버에서 건물별 통계 캐시를 무효화하고 프론트에서도 재조회하세요. 공식 정보와 후기의 캐싱·정규화는 백엔드 구현 범위입니다.
