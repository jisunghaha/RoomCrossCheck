# 기본 항목

후기 category는 현재 프론트와 같은 한글 문자열을 저장한다. V1 CHECK로 8개 항목을 제한하며 정의 테이블은 없다.

방음, 수압, 채광, 환기, 주차, 관리, 벌레, 결로.

방문 생성 시 Service에서 다음 기본 항목을 visit_check_items에 복사한다. label·sort_order는 방문 당시 값이며 key 의미를 바꾸지 않는다.

| 현재 화면 인덱스 | item_key | label |
|---|---|---|
| 0 | soundproofing | 방음 |
| 1 | water_pressure | 수압 |
| 2 | sunlight | 채광 |
| 3 | ventilation | 환기 |
| 4 | parking | 주차 |
| 5 | management | 관리 |
| 6 | pests | 벌레 |
| 7 | condensation | 결로 |
| 8 | maintenance_items | 관리비 포함 항목 |

사용자 추가 항목은 UUID 등 별도 고유 item_key를 사용한다. 기존 checks의 숫자 키는 호환 adapter에서 위 key로 매핑한다. 새 방문 이력 API는 DB ID/item_key로 표현하고 표시 순서를 식별자로 사용하지 않는다. 항목 추가·의미 변경은 기존 기록을 보존하며 별도 데이터 변환을 설계한다.
