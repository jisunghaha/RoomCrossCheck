# Spring Boot · MySQL 연결 계약

## 구조와 전환

React 화면 → RoomContext → repository.js → Spring Boot REST API → MySQL.
MySQL 접속 계정은 Spring Boot 서버 설정에만 둡니다. 브라우저에 공개되는 VITE_ 변수에는 API 주소와 모드만 넣습니다.

1. `frontend/`에서 `.env.example`을 `.env`로 복사합니다.
2. 서버 구현 전에는 `VITE_DATA_MODE=local`을 유지합니다.
3. 아래 API를 Spring Boot에서 구현합니다. 서버에서 JPA 또는 MyBatis로 MySQL을 연결합니다.
4. `VITE_DATA_MODE=api`, `VITE_API_BASE_URL=http://localhost:8080/api`로 바꿉니다.
5. Vite를 재시작합니다. 배포용 파일은 환경 설정 후 다시 빌드합니다.

API 모드는 localStorage를 읽거나 쓰지 않고, 가상 데이터를 화면에 대신 표시하지 않습니다. 로컬 데이터 자동 이관은 제공하지 않습니다. 서버 ID와 서버가 반환한 저장 결과를 사용합니다. 화면 코드는 DB 테이블 구조에 의존하지 않습니다. 아래 계약을 변경하면 `frontend/src/app/data/createRepository.js`의 API 어댑터에서 DTO를 변환하세요.

## 현재 어댑터가 호출하는 API

응답은 `{data: ...}`로 감싸지 않은 JSON입니다. ID는 DB에서 BIGINT를 사용해도 JSON에서는 문자열로 반환합니다. 목록에 페이지 객체를 반환하려면 어댑터도 함께 수정합니다.

| 메서드 | 경로 (기본 주소 뒤) | 요청 | 성공 응답 |
|---|---|---|---|
| GET | /rooms | 없음 | Room[] |
| POST | /rooms | RoomInput | 201 Room |
| PUT | /rooms/{id} | RoomInput | 200 Room |
| DELETE | /rooms/{id} | 없음 | 204 (본문 없음) |
| GET | /reviews | 없음 | Review[] |
| POST | /reviews | ReviewInput | 201 Review |
| DELETE | /reviews/{id} | 없음 | 204 |
| GET | /visits | 없음 | {"room-id": Visit, ...} |
| PUT | /rooms/{id}/visit | Visit | 200 Visit |

GET /rooms와 /visits는 로그인 사용자의 자료만 반환합니다. GET /reviews는 현재 화면에 필요한 건물별 공개 후기 목록을 반환합니다. 현재 화면의 통계는 반환된 전체 후기로 계산하므로 페이지네이션 도입 시 서버 통계 API도 연결해야 합니다.

## DTO

RoomInput 예시 (생성·수정에서 서버 관리 필드와 소유자 ID를 보내지 않음):

```json
{"name":"관심 매물","address":"사용자가 확인한 주소","deposit":5000000,"rent":420000,"fee":50000,"area":23.1,"areaType":"전용","floor":3,"original":"광고 원문"}
```

Room 응답은 위 필드에 다음 필드를 포함합니다:

```json
{"id":"42","building":"17","tags":[],"theme":"sage","official":null,"createdAt":"2026-10-14"}
```

두 객체를 합친 것이 완전한 Room입니다. 금액은 원 단위 숫자 또는 null, 면적은 ㎡ 또는 null, 층수도 숫자 또는 null입니다. null과 0을 구분합니다. 면적 종류는 `전용`, `공급`, `미상` 등 기존 폼 값을 유지합니다. 날짜는 YYYY-MM-DD입니다. theme은 sage/sand/clay 중 하나를 권장합니다.

공식 자료가 있으면 `official`은 `{area, areaType, floor, scope, source, date}`입니다. 현재 대조 화면은 면적·층수 중심의 기존 구현이며 계획서의 7개 항목 전체 API를 구현한 것은 아닙니다. 서버는 사용자가 보낸 값으로 official을 덮어쓰지 않으며, 매물 수정 시 기존 공식 결과를 해제하거나 유효성을 재검증해야 합니다. 건물 연결은 서버에서 확인하고, 주소 문자열만으로 임의 병합하지 않습니다.

ReviewInput은 `{building, period, body, scores}`입니다. scores는 `frontend/src/features/review/domain/categories.js`의 categories 문자열을 키로, 1~5 또는 null을 값으로 사용합니다. 서버는 점수 범위를 검증합니다. Review 응답은 입력에 `{id, demo:false, createdAt}`를 포함합니다. 예시 전체 구조는 `frontend/src/app/data/seed.js`에 있습니다.

Visit은 `{checks: {"0":true,"1":false}, note:"현장 메모"}`입니다. 현재 체크리스트 순서가 저장 키이므로 기존 순서를 변경할 때는 저장 데이터 마이그레이션 또는 안정적인 항목 ID로의 전환이 필요합니다. 현재는 매물당 방문 기록 하나를 덮어쓰며 방문 이력 테이블을 연결하려면 API와 화면을 확장해야 합니다.

## 저장·오류·권한

저장 응답을 받은 뒤 React 상태를 갱신하고 완료 메시지를 표시합니다. 저장 중에는 중복 변경 요청을 막습니다. 실패하면 기존 데이터와 폼 입력을 유지합니다. 초기 조회 실패에는 재시도를 제공합니다. 요청 제한 시간은 15초이며 자동 재전송은 하지 않습니다. 타임아웃은 서버의 미저장을 보장하지 않으므로 사용자는 조회 후 반영 여부를 확인해야 합니다. 운영 환경에서는 POST의 멱등성 키 계약을 추가하는 것이 좋습니다.

401 로그인 필요 / 403 무권한 / 404 없음 / 409 충돌 / 429 요청 제한 / 기타 서버 오류를 표시합니다. 에러 본문은 `{code,message,requestId}`를 권장하지만 현재 화면은 상태코드 기준 안내를 사용합니다.

fetch는 `credentials: include`를 사용합니다. 실제 로그인 화면과 인증 흐름은 아직 미구현입니다. 세션 쿠키 인증 사용 시 Spring Security의 CSRF 토큰 발급·헤더 계약을 정하고 request 함수에 연결해야 합니다. CSRF 보호를 단순히 끄는 방식으로 연결하지 마세요. 별도 origin 개발 서버를 쓰면 Spring Boot CORS에 정확한 프론트 origin과 allowCredentials를 설정합니다. 같은 origin의 /api 프록시 배포도 가능합니다.

백엔드는 모든 변경 API에서 소유자·작성자 권한을 검증합니다. 개인 매물 삭제와 연결 방문 기록 삭제는 트랜잭션으로 처리하고 공개 후기는 보존합니다. 클라이언트 demo 필드는 권한 검증을 대체하지 않습니다.

## MySQL 설계 연결 지점

users, buildings, rooms(user_id/building_id), visits(room_id), reviews(user_id/building_id), review_scores(review_id/category)로 분리할 수 있습니다. 공식 기록은 별도 테이블로 출처와 조회 시각을 보존합니다. 금액은 정수형, 면적은 DECIMAL을 고려하고 DTO에서는 JS 숫자 범위를 검증합니다. 이 문서는 연결 계약이며 실제 DDL·서버·DB 생성은 포함하지 않습니다.

## 검증 범위

아래 npm 명령은 `frontend/`에서 실행합니다. 백엔드는 현재 기본 골격만 있으며 이 문서의 업무 API는 앞으로 구현합니다.

`npm test`: 기존 사용자 흐름과 계산, API 요청/응답·401 실패, 로컬 저장 실패, 서버 저장 실패 시 입력 보존, 초기 조회 재시도를 검증합니다.
`npm run build`: 배포 빌드를 검증합니다.
실제 Spring Boot/MySQL 연결, 인증, CORS·CSRF 설정, 실서버 동시성 검증은 서버 구현 후 수행해야 합니다.
