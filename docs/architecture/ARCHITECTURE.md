# 구조와 책임 설계

계획서 `4조_캡스톤디자인.hwp`를 기준으로 프론트엔드와 백엔드를 한 저장소에서 관리한다. 실행 환경과 의존성은 `frontend/`, `backend/`에 각각 둔다. 계획서의 기능 범위는 [요구사항 매핑](REQUIREMENTS.md), 현재 화면의 API는 [API 계약](../api/API_HANDOFF.md)을 참조한다.

## 현재 구현 범위

프론트엔드의 기존 화면·테스트는 이동하고 계산·저장소·UI 책임을 분리했다. 백엔드에는 Spring Boot 시작점, 기본 보안 구성과 기능별 패키지 설명만 있다. 업무 Controller·Service, DB 엔티티, 저장소·외부 API 구현는 아직 구현하지 않았다. 폴더 이름만으로 GRASP·SOLID 준수가 보장되는 것은 아니므로 아래 의존 방향과 구현 규칙을 적용한다.

## 프론트엔드

```text
frontend/src/
├── app/
│   ├── main.jsx                   # 앱 시작점
│   ├── App.jsx                    # 페이지·모달 조합
│   ├── layout/Layout.jsx          # 전체 메뉴·레이아웃
│   ├── routing/useHashRoute.js     # 화면 경로
│   ├── state/RoomContext.jsx       # 기존 매물·후기·방문 상태의 조정
│   ├── data/
│   │   ├── createRepository.js     # local/api 구현 선택
│   │   ├── apiRepository.js        # HTTP·응답 변환
│   │   ├── localRepository.js      # 로컬 저장 구현
│   │   ├── localStorage.js         # 저장 데이터 읽기·보정
│   │   └── seed.js                 # 개발 예시
│   └── styles.css
├── features/
│   ├── listing/                   # 매물 목록·폼·상세·카드·비용
│   ├── crosscheck/                # 대조 표시·화면 계산
│   ├── comparison/                # 비교 페이지·선택 규칙
│   ├── visit/                     # 방문 화면
│   ├── review/                    # 후기·평가·통계
│   ├── auth/                      # 이후 회원 화면
│   ├── extraction/                # 이후 OCR 확인 화면
│   ├── building/                  # 이후 주소·건물 확인 화면
│   ├── community/                 # 이후 게시글·댓글·반응
│   └── moderation/                # 이후 신고·관리 화면
└── shared/
    ├── ui/UI.jsx                  # Icon, Field, PageHeading
    ├── ui/Modal.jsx
    └── lib/money.js               # 금액 표시
```

`features/<기능>/pages`는 화면, `components`는 기능 UI, `domain`은 React와 저장 방식에 의존하지 않는 화면 계산이다. 아직 없는 기능은 README로 개발 위치를 남겼으며 실제 구현할 때 하위 폴더를 추가한다. 공통 UI가 기능의 계산을 import하지 않도록 AuditBadge, RatingGrid, RoomScene을 해당 기능으로 옮겼다.

화면 → 앱 상태·저장소 계약 → 선택한 저장소 구현 → REST API 순서로 연결한다. 여러 기능을 조합하는 `App`과 `RoomContext`는 `app/`에 둔다. 현재 기능들은 기존 통합 Context를 사용하며, 새로운 커뮤니티 상태까지 넣어 하나의 거대한 Context로 확장하지 않는다. 기능이 커지면 별도 상태와 저장소 계약을 분리하고 `app`에서 조합한다.

저장소 구현들은 `initialState`, `load`, `saveRoom`, `deleteRoom`, `addReview`, `deleteReview`, `saveVisit` 계약을 유지한다. 테스트는 Context에 저장소 구현을 주입할 수 있다. 기존 localStorage 키와 데이터, 화면 동작은 유지한다.

`domain`의 프론트 계산은 현재 화면용이다. 소유권 검증, 공식 대조의 최종 규칙, 공개 후기 통계와 데이터 무결성은 백엔드가 책임진다. 현재 면적·층수 대조와 매물당 한 방문 기록을 계획서 전체 기능으로 오해하지 않는다.

## 백엔드: 기능별 폴더 안의 익숙한 계층 이름

```text
com.roomcrosscheck/
├── RoomCrossCheckApplication.java
├── config/                        # 객체 연결·Spring·보안 설정
├── common/{error,security}/        # 최소 공통 계약
├── listing/
│   ├── controller/
│   ├── service/
│   ├── domain/
│   ├── repository/
│   └── dto/
├── review/                        # listing과 같은 다섯 계층
├── auth/                          # 다섯 계층 + client (Google 인증)
├── building/                      # 다섯 계층 + client (건축물대장)
├── extraction/                    # 다섯 계층 + client (OCR·AI)
├── crosscheck/
├── visit/
├── comparison/
├── community/
└── moderation/
```

각 기능에는 controller, service, domain, repository, dto가 있다. auth/building/extraction에는 외부 연동을 위한 client도 있다. HTTP DTO와 외부 호출은 각각 dto와 client에서 책임진다.

폴더 이름 자체가 GRASP를 보장하지 않는다. 한 기능의 변경을 같은 기능 폴더 안에 모으고 각 클래스의 책임·의존 방향으로 원칙을 적용한다.

```text
controller → service → domain
controller ↔ dto (HTTP 요청·응답 변환)
service → repository/client의 작은 인터페이스
저장·외부 연동 구현 → 해당 인터페이스 + domain
config → service + 기술 구현 (생성자 주입 구성)
```

- `controller`: HTTP 형식 검증·DTO 변환·Service 호출. DB·외부 API를 직접 호출하거나 업무 계산을 하지 않는다. 사용자 식별은 서버 인증 정보에서 얻는다.
- `service`: 유스케이스의 소유권·작업 순서·트랜잭션을 조정한다. 데이터에 관한 판단은 Domain 객체에 위임한다. Repository·Client 인터페이스에 의존한다.
- `domain`: 엔티티·값 객체·업무 정책. 필요한 데이터를 가진 객체가 검증과 상태 전이를 보호한다. Spring·HTTP·JPA·Redis·공급자 SDK에 의존하지 않는다.
- `repository`: 저장·조회 계약과 구현. DB·캐시·파일 저장 모델을 변환한다. 인터페이스는 기능 안에 두고 구현은 필요할 때 persistence/cache/storage 하위 패키지로 분리한다. 외부 API 호출은 Client의 책임이다.
- `dto`: HTTP 요청·응답 계약. 서버 관리 필드·소유자 정보와 사용자 입력을 구분하며 JPA 엔티티를 응답하지 않는다. Service는 HTTP DTO 대신 작업에 필요한 값이나 별도 입출력 모델을 받는다.
- `client`: 외부 API 인터페이스와 공급자별 구현. 제한 시간·연동 오류와 공급자 응답 변환을 맡고 업무 판단은 Domain에 위임한다.
- `config`: 생성자 주입으로 구현을 연결한다. 미구현 기능에 불필요한 업무 클래스를 미리 생성하지 않는다.

아래는 후기 기능을 구현할 때 사용할 수 있는 **예시 이름이며 현재 생성된 업무 클래스가 아니다**.

```text
review/controller/ReviewController.java
review/dto/CreateReviewRequest.java
review/dto/ReviewResponse.java
review/service/CreateReviewService.java
review/repository/ReviewRepository.java
review/repository/ReviewStatisticsCache.java
review/repository/persistence/ReviewJpaEntity.java
review/repository/persistence/JpaReviewRepositoryAdapter.java
review/repository/cache/RedisReviewStatisticsCache.java
review/domain/Review.java
review/domain/ReviewScore.java
review/domain/ReviewStatistics.java
```

`ReviewScore`가 1~5점 불변조건을, `ReviewStatistics`가 유효 점수의 분모·평균을 책임진다. `CreateReviewService`는 인증 사용자·건물 확인·저장·통계 무효화 순서를 조정한다. 공개 후기와 개인 매물의 생명주기는 분리한다. Service가 모든 규칙을 직접 계산하고 Domain이 getter/setter만 가진 객체가 되지 않도록 한다.

## GRASP 적용 기준

| 원칙 | 책임 배치 |
|---|---|
| Information Expert | 비용·점수·동일 대상/기준 규칙을 해당 도메인 객체와 정책에 둔다. 프론트 계산도 기능별 domain으로 분리한다. |
| Creator | 연관 데이터를 소유하는 도메인 객체나 생성 정책이 객체를 만들고, 유스케이스는 생성 과정과 저장을 조정한다. |
| Controller | HTTP Controller는 시스템 요청의 진입점이며 실제 작업 흐름은 Service 유스케이스에 위임한다. |
| Low Coupling | 화면과 DB, 업무 규칙과 OCR·공공 API 공급자를 직접 연결하지 않는다. |
| High Cohesion | 매물·방문·후기 등 기능별 응집도를 유지하고 기능 안에서 화면 요청·흐름·규칙·외부 연동을 분리한다. |
| Polymorphism | local/API 저장소는 같은 계약을 제공한다. 백엔드 공급자·저장소는 인터페이스의 구현을 교체한다. |
| Pure Fabrication | HTTP adapter, 저장소 adapter, 통계 캐시처럼 도메인 객체의 책임이 아닌 기술 작업을 별도 객체에 둔다. |
| Indirection | Context·저장소 계약과 Repository·Client 인터페이스를 통해 UI·업무 흐름·외부 구현 사이를 연결한다. |
| Protected Variations | 공급자 응답과 DB 형태를 adapter 안에서 변환해 외부 변경이 도메인과 화면으로 퍼지지 않게 한다. |

## 객체지향·SOLID 적용 기준

| 원칙 | 구현 규칙 |
|---|---|
| 캡슐화 | 필드를 모두 열어 놓고 Service가 직접 변경하지 않는다. 의미 있는 도메인 메서드가 검증 후 상태를 변경한다. |
| 단일 책임 (SRP) | HTTP, 작업 흐름, 업무 계산, DB·외부 연동을 각각의 계층과 객체로 나눈다. |
| 개방·폐쇄 (OCP) | OCR 공급자나 저장 방식 변경은 adapter 구현 추가와 구성 변경으로 처리한다. 실제 변형 지점에만 정책 인터페이스를 둔다. |
| 리스코프 치환 (LSP) | 같은 인터페이스의 구현은 성공·실패·누락값 계약을 동일하게 지킨다. API 실패를 local 저장 성공으로 바꾸지 않는다. |
| 인터페이스 분리 (ISP) | 모든 기능을 담은 백엔드 Repository를 만들지 않는다. Service가 실제로 필요한 작은 Repository·Client 인터페이스를 정의한다. |
| 의존성 역전 (DIP) | Service가 기술 구현 대신 Repository·Client 인터페이스에 의존하고, 기술 클래스가 그 인터페이스를 구현한다. 생성자 주입으로 연결한다. |
| 합성 우선 | 공통 BaseService·BaseEntity 상속을 먼저 만들지 않는다. 상태·규칙·adapter를 조합하고 필요한 도메인 관계만 모델링한다. |

## 기능 사이의 경계와 계획서 핵심 규칙

비교 기능은 listing·visit·crosscheck·review의 Service 조회 계약을 조합한다. 다른 기능의 DB Repository나 JPA 엔티티를 직접 가져오지 않는다. 신고 기능은 대상 기능에 숨김·복원을 요청하고, 후기 기능이 통계 무효화도 처리한다. 트랜잭션 이후 캐시 갱신이 필요한 경우 이벤트를 사용하되 기능 구현 전에 이벤트 버스를 미리 만들지 않는다.

공식 조회 상태 5종(미조회·조회 중·조회 완료·자료 없음·조회 실패)은 building에서 관리한다. 대조 결과 4종(일치·차이 있음·추가 확인 필요·비교 불가)은 crosscheck에서 관리한다. 실패는 대조 보류로 처리하며 비교 불가와 합치지 않는다. 7개 후보는 전용면적, 방 층수, 건물 층수, 용도, 주차, 승용승강기, 사용승인일이며 같은 대상·기준의 항목만 비교한다.

금액은 원, 면적은 ㎡와 면적 종류/범위를 보존하고 `null`과 `0`을 구분한다. 방문은 매물에, 공개 후기는 건물에 연결한다. 후기는 공개된 유효 점수만 집계한다. 개인정보·원본 파일은 소유자 권한을 검증하고 Google OAuth 비밀키·공공 API 키는 서버 환경변수에서 읽는다. 거주 인증과 보상은 이번 학기 범위에 포함하지 않는다.

## 검증

기존 프론트 사용자 흐름·domain·저장소 테스트와 Vite 빌드를 실행한다. 백엔드는 Spring context 시작 테스트와 Maven 패키징으로 골격을 검증한다. 업무 기능 구현 시 소유권, 조회/대조 상태 분리, 중복 요청, 같은 대상/단위, 삭제 관계, 캐시 무효화를 해당 기능 테스트로 검증한다.
