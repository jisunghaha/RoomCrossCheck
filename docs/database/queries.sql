-- MySQL 8.4용 조회 참고 SQL이다. 실행 전 아래 NULL에 조회할 ID를 직접 지정한다.
-- 향후 Repository에서는 세션의 인증 사용자 ID를 사용하고 바인딩 파라미터로 전달한다.
-- 이 파일은 Flyway migration이 아니며, 로그인이나 업무 API를 구현하지 않는다.
SET @current_user_id = NULL;
SET @building_id = NULL;
SET @room_id = NULL;

-- 개인 매물 첫 페이지. 같은 생성 시각에도 id로 정렬하여 순서를 고정한다.
-- NULL 금액은 미입력이며 조회 시 0으로 바꾸지 않는다. 금액 단위는 원, 면적은 제곱미터다.
SELECT id, building_id, name, address, deposit, rent, fee, area, area_type, floor, created_at
FROM rooms
WHERE user_id = @current_user_id AND status = 'CONFIRMED'
ORDER BY created_at DESC, id DESC
LIMIT 30;

-- 개인 매물 상세. 다른 사용자의 ID를 알고 있어도 이 소유자 조건을 생략하면 안 된다.
SELECT id, building_id, name, address, original_text, deposit, rent, fee,
       area, area_type, floor, tags, options, created_at, updated_at
FROM rooms
WHERE id = @room_id AND user_id = @current_user_id;

-- 소유한 매물별 가장 최근 방문을 구한다. 동일 방문 시각이면 id가 큰 기록을 선택한다.
-- 방문이 없는 매물에는 행이 없으며, 서비스 구현 시 이를 방문 기록 없음으로 표현한다.
WITH ranked AS (
    SELECT v.id, v.room_id, v.visited_at, v.note,
           ROW_NUMBER() OVER (
               PARTITION BY v.room_id ORDER BY v.visited_at DESC, v.id DESC
           ) AS visit_rank
    FROM visits v
    JOIN rooms r ON r.id = v.room_id
    WHERE r.user_id = @current_user_id
)
SELECT id, room_id, visited_at, note
FROM ranked
WHERE visit_rank = 1
ORDER BY room_id;

-- 한 매물의 전체 방문·체크 항목. 항목이 없는 방문도 LEFT JOIN으로 보존한다.
-- item_key는 식별자, sort_order는 화면 표시 순서이므로 서로 바꾸어 쓰지 않는다.
SELECT v.id AS visit_id, v.visited_at, v.note AS visit_note,
       i.item_key, i.label, i.checked, i.note AS item_note, i.sort_order
FROM visits v
JOIN rooms r ON r.id = v.room_id
LEFT JOIN visit_check_items i ON i.visit_id = v.id
WHERE r.id = @room_id AND r.user_id = @current_user_id
ORDER BY v.visited_at DESC, v.id DESC, i.sort_order, i.id;

-- 건물별 후기 통계. 점수가 없는 항목도 결과에 포함하도록 기준 8개 항목을 먼저 만든다.
-- 공개 후기만 집계하며 NULL은 평균 분모에서 제외한다. 응답 0건의 평균은 NULL이다.
-- 실제 서비스에서는 이 NULL을 N/A로 표시하며 0점으로 바꾸지 않는다.
WITH categories AS (
    SELECT '방음' AS category, 0 AS sort_order UNION ALL
    SELECT '수압', 1 UNION ALL SELECT '채광', 2 UNION ALL
    SELECT '환기', 3 UNION ALL SELECT '주차', 4 UNION ALL
    SELECT '관리', 5 UNION ALL SELECT '벌레', 6 UNION ALL SELECT '결로', 7
), public_scores AS (
    SELECT s.category, s.score
    FROM review_scores s
    JOIN reviews r ON r.id = s.review_id
    WHERE r.building_id = @building_id AND r.visibility = 'PUBLIC'
)
SELECT c.category, COUNT(s.score) AS response_count,
       ROUND(AVG(s.score), 1) AS average_score
FROM categories c
LEFT JOIN public_scores s ON s.category = c.category
GROUP BY c.category, c.sort_order
ORDER BY c.sort_order;

-- 전체 후기 수는 점수 행 개수와 다르다. 항목 점수를 작성하지 않은 공개 후기도 포함한다.
SELECT COUNT(*) AS review_count
FROM reviews
WHERE building_id = @building_id AND visibility = 'PUBLIC';
