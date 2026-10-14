-- Bind authenticated identity/building ID as parameters in actual Repository code.
SET @current_user_id = 1;
SET @building_id = 1;

SELECT * FROM rooms
WHERE user_id = @current_user_id AND status = 'CONFIRMED'
ORDER BY created_at DESC, id DESC LIMIT 30;

-- Latest visit for each owned room
WITH ranked AS (
    SELECT v.*, ROW_NUMBER() OVER (
        PARTITION BY v.room_id ORDER BY v.visited_at DESC, v.id DESC
    ) AS visit_rank
    FROM visits v JOIN rooms r ON r.id = v.room_id
    WHERE r.user_id = @current_user_id
)
SELECT id, room_id, visited_at, note FROM ranked WHERE visit_rank = 1;

-- Missing category rows are filled by Service using the 8 category names.
-- COUNT(score) excludes NULL. PUBLIC reviews only.
SELECT s.category, COUNT(s.score) AS response_count,
       ROUND(AVG(s.score), 1) AS average_score
FROM review_scores s JOIN reviews r ON r.id = s.review_id
WHERE r.building_id = @building_id AND r.visibility = 'PUBLIC'
GROUP BY s.category;

SELECT COUNT(*) AS review_count FROM reviews
WHERE building_id = @building_id AND visibility = 'PUBLIC';
