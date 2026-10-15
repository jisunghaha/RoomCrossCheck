# 20건 평가 자료 준비

현재 준비 완료 **0/20건**. `npm run evaluation:check`는 네트워크 없이 CSV와 원본·정답 파일을 점검하고 `evaluation-readiness.json`을 갱신합니다. API를 호출하거나 정확도를 추정하지 않습니다.

`evaluation-20-template.csv`의 각 행에 다음을 채워 주세요.

| 열 | 필요한 값 |
|---|---|
| sampleId | 서로 다른 광고별 고유 ID. 기존 eval-001~020 유지 가능 |
| sampleType | 평가 표본은 representative. 기존 단순 합성 smoke는 포함하지 않음 |
| sourceFile | 저장소 기준 상대 경로의 광고 글 또는 이미지 파일 |
| privacyChecked | 개인정보 제거와 외부 전송 가능 여부를 사람이 확인한 후 yes |
| truthReviewedBy | 원본과 정답을 검토한 사람의 식별명·별칭 |
| expectedRawTextFile | 사람이 전사·확인한 광고 글의 UTF-8 파일 경로 |
| expectedAddress | 광고에 적힌 비식별 주소. 없거나 확인 불가하면 null |
| expectedDepositWon / expectedMonthlyRentWon / expectedMaintenanceFeeWon | 정수 원. 0은 0, 광고에 없으면 null |
| expectedAreaSqm | 숫자 ㎡. 광고에 없으면 null |
| expectedFloor | 정수 층. 지하 2층은 -2, 광고에 없으면 null |

예시 경로: `docs/sfr003/evaluation-data/eval-001.txt`, `docs/sfr003/evaluation-data/eval-001-truth.txt`. 이미지 광고는 원본 이미지와 전사 정답 파일을 각각 준비합니다. 아직 실제 자료 파일은 만들지 않았습니다.

빈칸은 정답 없음이 아니라 **미작성**입니다. 명시적인 `null`과 숫자 `0`을 구분합니다. 정답 자료·원본 파일·개인정보 확인·검토자·자료형 중 하나라도 빠지면 준비 완료로 집계하지 않습니다. 같은 내용의 광고 원본을 이름만 바꾸어 여러 행에 넣어도 중복으로 처리합니다. 점검 스크립트는 개인정보를 자동 탐지하거나 정답의 의미를 판정하지 않으므로 사람의 원본 검토가 필요합니다.

자료는 글·이미지, 누락값·0원, 금액 단위, 지하층, 소수 면적 등 다양한 경우를 포함해야 합니다. 광고 추출 정답과 건축물대장 공식 정보를 섞지 않습니다.

자료 준비 후에는 무료 계정·모델 한도를 확인하고 샘플별 요청 기록을 만들어 평가합니다. 이미 요청 기록이 있는 광고는 재호출하지 않고, 모델·프롬프트 버전·응답 상태·실패도 기록합니다. 429 또는 무료 사용 불확실 시 중단합니다. 기존 smoke-002~006은 재호출하지 않습니다.

평가 지표와 실패 처리 기준은 [검증 기록](VALIDATION.md)의 정확도 평가 항목을 따릅니다. 준비된 자료 수와 실제 평가 완료 수, 추출 성공률과 필드 정확도를 각각 보고합니다.
