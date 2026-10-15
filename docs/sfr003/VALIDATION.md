# SFR-003 검증 기록

확인일: 2026-10-14 (Asia/Seoul). 작업 브랜치: `feature/sfr-003-gemini-extraction`.

## 시작 상태

- 작업 트리 깨끗함. 로컬 HEAD와 원격 기능 브랜치: `43339213814992197e5fa282c80221e0292c2f31`.
- 원격 main: `62402fe6095af55ad1e356a2dc29a2f35828ab25`.
- main 변경·병합·강제 푸시·PR·팀 리뷰 없음.
- 런타임 스킬에 따라 키 값 없이 주입 상태를 확인함. 최신 환경 상태에서 키 `ready`, 네트워크 정책 `enforced`. 키 값은 파일·코드·Git에 기록하지 않음.

## 실제 Gemini 검증

**실제 추출 성공: gemini-3.5-flash-lite에서 직접 REST 호출과 Spring Boot → 실제 Gemini → MySQL 경로가 모두 HTTP 200으로 통과했다.** 모델 조회 200만으로 생성 가능 여부를 판단했던 이전 상태에서 진전된 결과다. 무료 등급 근거는 사용자가 제공한 AI Studio 결제 등급 화면과 명시적 확인이다. 결제를 활성화하지 않았으며 기존 광고는 재호출하지 않았다.

| 근거 / 샘플 | 모델 | 응답 | 성공 여부 / 추출 항목 |
|---|---|---|---|
| 이전 채팅의 도구 실행 결과: 모델 목록 | 해당 없음 | 200 | 인증·목록 조회 성공, 추출 없음 |
| 이전 단건 가상 광고 (보증금 500만원·월세 45만원) | gemini-2.5-flash | v1beta 404, v1 404 | 실패. 신규 사용자 지원 종료 안내. 추출 항목 없음 |
| 같은 이전 광고, 이전 작업의 대체 모델 시도 | gemini-3.8-flash | 503, 재시도 503 | 서버 혼잡. 추출 항목 없음 |
| 이번 새 가상 샘플 synthetic-smoke-002 | gemini-3.8-flash-lite | 404 NOT_FOUND | 실패. 추출 항목 없음 |
| 이번 다른 새 가상 샘플 synthetic-smoke-003 | gemini-flash-latest | 503 UNAVAILABLE | 서버 혼잡. 추출 항목 없음 |
| 추가 새 가상 샘플 synthetic-smoke-004 | gemini-2.5-flash-lite | 404 NOT_FOUND | 신규 사용자 지원 종료. 3.5 Flash Lite로 교체 안내 |
| 추가 새 가상 샘플 synthetic-smoke-005 | gemini-3.5-flash-lite | 실제 Gemini 200 | 직접 REST 성공. 6개 구조 필드 정답 일치 |
| 추가 새 가상 샘플 synthetic-smoke-006 | gemini-3.5-flash-lite | Spring 추출 API 200 | 실제 Gemini 추출·MySQL 저장·재조회 성공. 6개 구조 필드 정답 일치 |

이전 작업의 원본 HTTP 응답 파일·추출 성공 파일은 저장소와 `/workspace/scratch`에서 찾지 못했다. 이전 상태는 이 채팅의 실행 결과에 근거한다. 이번 새 샘플은 개인정보가 없는 합성 문장이고, 각각 실제 생성 API에 한 번만 요청했다. 상태·입력·정답은 `smoke-002-result.json`부터 `smoke-006-result.json`까지 보존했다. 002~005는 현재 브랜치의 프롬프트를 사용하는 직접 REST 호출이며, 006은 현재 빌드한 Spring Boot 광고 API를 거쳐 실제 Gemini 응답을 MySQL에 저장하고 GET으로 재조회한 검증이다. 기존 샘플을 반복 요청하지 않았다.

최종 기본 모델은 실제 성공을 확인한 `gemini-3.5-flash-lite`로 고정했다. 2.5 Flash Lite의 오류 응답이 이 모델을 대체 모델로 명시한 것이 선택 근거다. 3.8 Flash로 잠시 바꾼 검증되지 않은 기본값은 교체했다. 기존 로컬 `.env`에 모델이 명시되어 있으면 기본값보다 우선한다. 이번 환경의 `.env`에는 GEMINI_MODEL·GEMINI_ENABLED 항목이 없었다. 동일 샘플을 재요청하지 않고, 사용 가능 모델과 무료 등급이 확인된 경우에만 새로운 평가 샘플로 이어가야 한다.

클라우드의 Java 실행에는 HTTPS 프록시와 프록시 CA 신뢰 설정이 필요하다. Python의 HTTPS_PROXY·SSL_CERT_FILE만으로 Java HttpClient 설정이 되지는 않는다. 실제 통합 검증 스크립트는 상속된 프록시 주소를 JVM HTTPS 프록시 속성으로 적용하고, 기본 Java 신뢰 저장소에 제공된 공개 CA를 추가한 임시 신뢰 저장소를 사용했다. TLS 검증을 끄지 않았으며 실제 키는 환경에서만 전달했다. 이 설정은 검증 실행에 적용했고 일반 Compose 실행에 자동 추가한 것은 아니다.

후속 작업에서 일반 실행에도 연결했다. `dev-up.sh`가 `prepare-java-runtime.py`로 공개 설정을 생성하고, `backend/docker-entrypoint.sh`가 실제 컨테이너 시작 시 프록시·CA를 적용한다. 위 문장은 이전 smoke-006 실행 당시의 범위를 설명한다. 현재 일반 실행 범위는 아래 후속 검증을 참고한다.

006 결과: 주소 null, 보증금 9,000,000원, 월세 520,000원, 관리비 60,000원, 면적 23.75㎡, 층수 -2. DB 재조회 값도 같았다.

## 정확도 평가: 목표 20건, 완료 0건

저장소·공유 다운로드·첨부 React ZIP에서 개인정보 제거가 확인되고 사람이 정답을 검토한 20건 광고 자료를 찾지 못했다. 프론트 초기 가상 매물과 단위 테스트의 가짜 출력은 실제 추출 결과·평가 자료가 아니다. 성공한 합성 smoke 005·006은 각각 구조 필드 6/6개가 정답과 일치했고 rawText도 원문과 같았다. 이는 단순 합성 문장 2건의 동작 확인 결과이며 **대표 표본 20건 정확도는 미측정(N/A)**이다. 실패 샘플을 성공 결과로 꾸미거나 합성 2건을 20건 평가로 간주하지 않는다.

`evaluation-20-template.csv`의 20개 행은 빈 평가 양식이다. 빈칸은 미평가를 뜻한다. 실제 광고에 명시되지 않은 정답은 문자열 `null`로 기록하고 숫자 0과 구분한다.

필요 자료:

1. 개인정보 제거와 외부 전송 허용이 확인된 광고 20건(글 또는 이미지). 이름·연락처·개인 계정 URL·상세 거주 호수·이미지 메타데이터를 점검하고 주소가 필요한 평가라면 공개 광고의 적절한 비식별 주소를 사용한다.
2. 각 원본에 대한 사람이 검토한 정답: rawText, address, depositWon, monthlyRentWon, maintenanceFeeWon, areaSqm, floor. 원본 파일과 정답 검토자를 기록한다. 광고에 없는 정보는 추론하지 않는다.
3. 텍스트·이미지, 누락값·0원, 만원→원 변환, 지하층, 소수 면적 등을 포함한 표본 구성과 모델·프롬프트 버전. 합성 smoke와 대표 평가 표본을 분리한다.
4. 무료 사용 가능 모델·계정 한도 확인과 성공한 생성 응답. 샘플별 요청 기록으로 중복 호출을 방지하고 429·불확실한 무료 조건에서는 중단한다.

평가 기준:

- 추출 성공률: JSON 스키마·자료형·허용 범위를 만족하는 응답 수 / 평가 요청 수. HTTP 실패도 분모에 포함한다.
- 6개 구조 필드 정확도: 성공 응답의 각 정규화 값과 사람이 검토한 광고 정답의 일치 수 / 해당 필드의 평가 가능한 성공 응답 수. HTTP 실패를 정확도 0으로 섞지 않고 성공률을 함께 제시한다.
- 주소는 앞뒤 공백 제거 및 연속 공백 통일 후 비교, 의미가 다른 주소는 오답. 금액은 정수 원, 면적은 숫자 20과 20.0을 동일하게 비교, 층수는 정수 비교. null과 0은 다르다.
- 누락 정답 null의 일치율과 광고에 명시된 값의 일치율을 별도로 보고하여 빈칸이 많은 표본에서 정확도가 부풀려지지 않게 한다.
- rawText는 사람의 전사 정답과 비교해 문자 오류율(CER: 삽입+삭제+치환 / 정답 문자 수)을 따로 기록한다. 공백·줄바꿈 정규화 규칙을 평가 전에 정한다. 빈 전사 정답은 CER에서 제외하고 건수를 기록한다.
- 건축물대장 공식 정보는 광고 정답으로 사용하지 않는다. 광고 추출 정확도와 사실 여부 대조는 별개다.

## 로컬 검증과 재현

최종 결과: React 22건 통과, Vite 빌드 성공, Spring Boot 6건 통과·패키징 성공, 실제 MySQL 로컬 smoke 통과. `git diff --check` 통과. 수정·추가한 텍스트 파일에서 주입된 키 값과 Gemini 키 형식 노출이 없는지 확인했다. 원격 기능 브랜치와 main SHA는 시작 시 값에서 바뀌지 않았다. 변경 사항은 기능 브랜치의 미커밋 작업 트리에 남겼다.

검증 명령:

```sh
npm test
npm run build
# 관리 환경: 캐시된 의존성으로 Maven test package 실행
docker --host=unix:///var/run/docker.sock run --rm --network none -u 1000:1000 \
  -v "$PWD/backend:/app" -w /app maven:3.9-eclipse-temurin-21 \
  mvn -o -B -Dmaven.repo.local=/app/.mvn-cache test package
python scripts/verify-sfr003-local.py
```

로컬 검증 스크립트의 기본 모드는 현재 빌드한 Spring Boot jar, 임시 MySQL 8.4 컨테이너, 루프백 가짜 Gemini 서버를 사용한다. 기본 모드에서는 실제 Gemini 키를 Java 프로세스에 전달하지 않고 가짜 키로 덮어쓰며 외부 Gemini 호출은 0건이다. DB 비밀번호는 메모리에서 생성하고 출력하지 않는다. 검증 후 생성한 임시 컨테이너만 정리한다. Docker·Java 21·현재 jar·캐시된 MySQL 이미지가 필요하다.

이번에 `python scripts/verify-sfr003-local.py --live-smoke --free-tier-confirmed`로 실제 통합 경로도 실행했다. 이 명시적 옵션은 기본 로컬 검증 후 새로운 합성 샘플 006으로 실제 추출을 한 번 수행한다. `smoke-006-result.json`이 이미 있으면 실행을 거부하므로 같은 샘플을 다시 호출하지 않는다. 실제 검증 기록을 삭제해 재실행하지 않는다. 향후 평가에는 별도 새 샘플과 요청 기록을 사용한다. 임시 서버는 검증 후 종료되며 앱 개발 서버가 계속 실행되는 것은 아니다.

검증 범위:

- React: REVIEWED 값만 폼 초기화, 원→만원→정수 원, null·0·지하층·긴 원문 유지, 미저장 확인 값 배제, 입력 수정, 실패 후 입력 유지, 사용자 저장 후 생성, 취소 시 미등록, 공식 정보 미주입.
- Spring Boot 가짜 provider 단위 테스트: 요청 형식·파싱·상태 저장과 오류 경로.
- 실제 MySQL 로컬 smoke: 광고 DRAFT→확정→가짜 추출→확인 저장→매물 등록→서버 재시작→광고와 매물 재조회. 중복 extract는 가짜 provider 재호출 없이 결과 재사용. 10,000자 원문·-20층 저장, 10,001자·-21층 거부. 공식정보 없음.
- 실제 Gemini: 합성 글 광고 005의 직접 호출과 006의 Spring Boot API→실제 Gemini→MySQL 저장·재조회 성공. React 화면 조작을 포함한 실제 외부 호출 전체 경로와 이미지 OCR 정확도는 미검증.

남은 작업: 대표 평가 자료 20건, 이미지 OCR 정확도, React 화면을 통한 실제 추출 전체 흐름, 일반 Compose 실행의 클라우드 Java 프록시·CA 설정, 광고-매물 ID 영구 연결 및 중복 등록 방지, 운영 DB 마이그레이션. 로그인은 구현하지 않았다. 다른 모델의 503은 해결된 것이 아니라 성공 모델을 선택해 추출 경로를 확인한 것이다.

## 미커밋 작업 후속 검증 (2026-10-14, Asia/Seoul)

재개 시 기존 미커밋 변경을 확인·보존했고 기능 브랜치와 원격 SHA는 시작 기록과 일치했다. smoke-002~006을 읽었으며 같은 광고로 실제 API를 재호출하지 않았다. 새 Gemini 생성 요청도 0건이다.

### 일반 실행 설정

- `scripts/dev-up.sh`에서 공개 프록시·CA runtime 설정을 생성하고 두 Compose 파일로 백엔드를 실행한다. 기존 DOCKER_CONFIG와 레지스트리 인증을 덮어쓰지 않는다.
- `prepare-java-runtime.py`는 프록시 호스트·포트·공개 CA 경로만 생성하고 API 키는 직렬화하지 않는다. 중첩 Docker에서 프록시 호스트를 해석하도록 host mapping을 제공한다. 인증 정보가 포함된 프록시 URL·없는 CA 파일은 비밀값을 출력하지 않고 거부한다.
- `backend/docker-entrypoint.sh`는 이미지에 CA를 굽지 않고 읽기 전용 CA 마운트를 기본 Java 신뢰 저장소에 추가한다. TLS 검증은 유지한다. 프록시가 없는 로컬 환경은 기본 Java 설정을 사용한다.
- 일반 실행으로 생성한 컨테이너의 실제 JVM 인수에서 proxy/trust 설정을 확인했다. 해당 인수를 사용한 Java 읽기 전용 모델 메타데이터 요청은 JSON 응답 HTTP 403을 받았다. API 키를 보내지 않아 인증이 거부된 것이며 HTTPS CONNECT·TLS·CA 통신은 성공했다. 광고 생성 요청이나 키 인증 재검증으로 간주하지 않는다.
- 기본 모델은 실제 성공 기록이 있는 gemini-3.5-flash-lite. GEMINI_ENABLED는 기본 false를 유지한다. runtime 설정과 기존 키·계정의 준비 상태는 별도로 확인한다.

### 실제 Chromium 화면 흐름

`npm run test:browser`는 일반 dev-up.sh, Docker 진입점, Vite, Spring Boot, 실제 MySQL 8.4, 로컬 가짜 추출 서버를 사용했다. headless Chromium에서 버튼·입력을 실제로 조작했다.

1. 광고 글 임시 저장·확정 → 추출 결과 표시. 확인 저장 전에는 매물 등록 버튼 없음.
2. 추출 월세 420,000원을 광고 확인 단계에서 430,000원으로 수정·저장.
3. 기존 매물 폼에 월세 43만원, 관리비 0원, 보증금 빈칸, 주소 빈칸, 층수 -2, 면적 종류 미상 표시. 버튼 클릭만으로 DB 매물 생성 없음.
4. 사용자가 이름·주소를 입력하고 월세를 44만원으로 다시 수정·저장.
5. 실제 MySQL에 rent=440000, fee=0, deposit=null, area=23.75, floor=-2 저장. 새로고침 후 화면과 API 재조회 값 유지.
6. 광고 AI 결과 420000과 광고 확인 값 430000은 유지되고, 매물 저장 값 440000과 구분됨. 공식 정보는 주입되지 않으며 상세 화면은 공식 조회 미실시 표시.

최종 결과는 `browser-flow-result.json`, 화면 캡처는 `.runtime/sfr003-browser-flow.png`에 있다. 브라우저 JS 오류 0, 가짜 추출 서버 호출 1, 실제 Gemini 생성 요청 0. 테스트가 만든 임시 Compose 컨테이너·네트워크·DB 볼륨은 정리했고 기존 사용자 스택은 변경하지 않았다. 검증 중 호스트 javac가 없는 환경을 발견해 컴파일을 기존 Maven 컨테이너에서 수행하도록 수정한 뒤 최종 검증을 통과했다.

### 20건 평가 준비

`npm run evaluation:check` 결과 준비 완료 **0/20**, 추가 필요 20건, 정확도 null(N/A). 저장소·공유 다운로드·첨부 React ZIP에서 별도 평가용 원본·검토된 정답 자료를 찾지 못했다. 20행 CSV는 양식이며 성공한 합성 smoke 두 건도 대표 표본으로 집계하지 않았다.

오프라인 점검은 원본·전사 정답 파일, 대표 표본 구분, 사람의 개인정보 확인과 정답 검토자, 6개 정답의 자료형·범위, 고유 ID·원본 내용 중복을 확인한다. 빈칸은 정답 null로 처리하지 않는다. 의미상 정확도·개인정보 자동 검사는 하지 않는다. 필요한 파일과 작성 규칙은 `EVALUATION_INPUTS.md`, 행별 누락 항목은 `evaluation-readiness.json`에 있다.

### 최종 검증 및 남은 범위

- React 22건 통과, Vite 빌드 성공.
- Spring Boot 6건 통과·패키징 성공, 일반 Compose 빌드·기동 성공.
- runtime·평가 준비 Python 테스트 6건 통과.
- 실제 Chromium 전체 흐름·MySQL 저장/재조회·일반 Java 프록시/CA 통신 통과.
- 이전 실제 Gemini smoke-005/006 성공 기록 유지. 이번 화면 검증은 로컬 provider 응답을 사용했으며 실제 Gemini가 포함된 Chromium 전체 흐름과 이미지 OCR은 미검증.

현재 남은 작업은 대표 자료 20건 확보·평가, 이미지 OCR, 실제 외부 생성 응답을 포함한 화면 검증, 광고-매물 영구 ID 연결·중복 등록 방지, 운영 DB 마이그레이션이다. 로그인·main 수정·병합·푸시·PR 생성은 하지 않았다. 변경은 기능 브랜치의 미커밋 작업 트리에 있다.

## 수정·추가 파일

- UI 연결: `src/App.jsx`, `src/pages/RoomsPage.jsx`, `src/components/AdDraftPanel.jsx`, `src/components/RoomForm.jsx`, `src/lib/advertisement.js`.
- 회귀·로컬 통합 검증: `tests/advertisement-registration.test.jsx`, `scripts/verify-sfr003-local.py`.
- 원문·층수 제한 정합성: `backend/src/main/java/com/roomcrosscheck/listing/Listing.java`, `backend/src/main/java/com/roomcrosscheck/listing/ListingRequest.java`.
- 모델 기본값: `backend/src/main/java/com/roomcrosscheck/advertisement/GeminiExtractionProvider.java`, `backend/src/main/resources/application.yml`, `compose.yaml`, `.env.example`.
- 계약·검증·평가 자료: `README.md`, `API_HANDOFF.md`, `docs/sfr003/VALIDATION.md`, `docs/sfr003/evaluation-20-template.csv`, `docs/sfr003/smoke-002-result.json`부터 `docs/sfr003/smoke-006-result.json`.

후속 추가·수정: `backend/Dockerfile`, `backend/docker-entrypoint.sh`, `scripts/dev-up.sh`, `scripts/prepare-java-runtime.py`, `scripts/verify-sfr003-browser.py`, `scripts/verify-sfr003-browser.mjs`, `scripts/check-sfr003-evaluation.py`, `tests/java-runtime.test.py`, `tests/evaluation-readiness.test.py`, `compose.yaml`, `vite.config.js`, `package.json`, `package-lock.json`, `.gitignore`, 위 문서와 `EVALUATION_INPUTS.md`, `browser-flow-result.json`, `evaluation-readiness.json`.
