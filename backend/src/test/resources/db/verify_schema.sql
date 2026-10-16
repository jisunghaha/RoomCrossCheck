-- MySQL 8.4: run only in a separate test DB after V1.
-- Fixtures roll back; AUTO_INCREMENT values may advance.
SET NAMES utf8mb4;
SET time_zone = '+00:00';
DELIMITER $$
CREATE PROCEDURE verify_roomcrosscheck_schema()
BEGIN
    DECLARE u BIGINT UNSIGNED;
    DECLARE b BIGINT UNSIGNED;
    DECLARE room_id_value BIGINT UNSIGNED;
    DECLARE visit_id_value BIGINT UNSIGNED;
    DECLARE first_review BIGINT UNSIGNED;
    DECLARE review_id_value BIGINT UNSIGNED;
    DECLARE i INT DEFAULT 1;
    DECLARE n INT;
    DECLARE average_value DECIMAL(10,4);
    DECLARE rejected BOOLEAN;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
    START TRANSACTION;
    INSERT INTO users (google_id, name) VALUES (UUID(), 'schema-test');
    SET u = LAST_INSERT_ID();
    INSERT INTO buildings (address) VALUES ('schema-test address');
    SET b = LAST_INSERT_ID();
    INSERT INTO rooms (user_id, building_id, name, address, rent, fee)
    VALUES (u, b, 'test room', 'schema-test address', 350000, 0);
    SET room_id_value = LAST_INSERT_ID();
    SELECT COUNT(*) INTO n FROM rooms WHERE id = room_id_value AND deposit IS NULL AND fee = 0;
    IF n <> 1 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'NULL and zero must differ'; END IF;
    INSERT INTO visits (room_id, visited_at) VALUES (room_id_value, CURRENT_TIMESTAMP);
    SET visit_id_value = LAST_INSERT_ID();
    INSERT INTO visit_check_items (visit_id, item_key, label) VALUES (visit_id_value, 'water_pressure', '수압');
    SET rejected = FALSE;
    BEGIN
        DECLARE CONTINUE HANDLER FOR 1062 SET rejected = TRUE;
        INSERT INTO visit_check_items (visit_id, item_key, label) VALUES (visit_id_value, 'water_pressure', 'duplicate');
    END;
    IF NOT rejected THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Duplicate visit item must fail'; END IF;
    WHILE i <= 4 DO
        INSERT INTO reviews (user_id, building_id, period, body) VALUES (u, b, 'test', 'test review');
        SET review_id_value = LAST_INSERT_ID();
        IF i = 1 THEN SET first_review = review_id_value; END IF;
        INSERT INTO review_scores (review_id, category, score)
        VALUES (review_id_value, '방음', CASE WHEN i = 4 THEN NULL ELSE 2 * i - 1 END);
        SET i = i + 1;
    END WHILE;
    SELECT COUNT(s.score), AVG(s.score) INTO n, average_value
    FROM review_scores s JOIN reviews r ON r.id = s.review_id WHERE r.building_id = b AND r.visibility = 'PUBLIC';
    IF n <> 3 OR average_value <> 3 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Expected 3 valid responses and mean 3.0'; END IF;
    SET rejected = FALSE;
    BEGIN
        DECLARE CONTINUE HANDLER FOR 3819 SET rejected = TRUE;
        UPDATE review_scores SET score = 0 WHERE review_id = first_review;
    END;
    IF NOT rejected THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Score 0 must fail'; END IF;
    SET rejected = FALSE;
    BEGIN
        DECLARE CONTINUE HANDLER FOR 3819 SET rejected = TRUE;
        UPDATE review_scores SET score = 6 WHERE review_id = first_review;
    END;
    IF NOT rejected THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Score 6 must fail'; END IF;
    SET rejected = FALSE;
    BEGIN
        DECLARE CONTINUE HANDLER FOR 1062 SET rejected = TRUE;
        INSERT INTO review_scores (review_id, category, score) VALUES (first_review, '방음', 2);
    END;
    IF NOT rejected THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Duplicate review category must fail'; END IF;
    UPDATE reviews SET visibility = 'HIDDEN' WHERE id = first_review;
    SELECT COUNT(s.score), AVG(s.score) INTO n, average_value
    FROM review_scores s JOIN reviews r ON r.id = s.review_id WHERE r.building_id = b AND r.visibility = 'PUBLIC';
    IF n <> 2 OR average_value <> 4 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Hidden review must be excluded'; END IF;
    DELETE FROM rooms WHERE id = room_id_value;
    SELECT COUNT(*) INTO n FROM visits WHERE id = visit_id_value;
    IF n <> 0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Visits must cascade'; END IF;
    SELECT COUNT(*) INTO n FROM visit_check_items WHERE visit_id = visit_id_value;
    IF n <> 0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Visit items must cascade'; END IF;
    SELECT COUNT(*) INTO n FROM reviews WHERE building_id = b;
    IF n <> 4 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Public reviews must survive room deletion'; END IF;
    SELECT COUNT(*) INTO n FROM buildings WHERE id = b;
    IF n <> 1 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Building must survive room deletion'; END IF;
    ROLLBACK;
    SELECT 'PASS: scores, means, uniqueness and private/public deletion' AS result;
END$$
DELIMITER ;
CALL verify_roomcrosscheck_schema();
DROP PROCEDURE verify_roomcrosscheck_schema;
