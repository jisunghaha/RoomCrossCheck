-- MySQL 8.4용 핵심 7개 테이블이다.
-- 실제 MySQL에는 아직 적용하지 않았다. 빈 DB에 한 번 적용한다.
SET NAMES utf8mb4;
SET time_zone = '+00:00';

-- 회원과 Google 식별자
CREATE TABLE users (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    google_id VARCHAR(255) COLLATE utf8mb4_bin NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(254),
    role ENUM('USER', 'ADMIN') NOT NULL DEFAULT 'USER',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 공개 후기는 개인 매물이 아닌 건물에 연결한다.
CREATE TABLE buildings (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    official_key VARCHAR(100) COLLATE utf8mb4_bin UNIQUE,
    name VARCHAR(200),
    address VARCHAR(500) NOT NULL,
    building_dong VARCHAR(100),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 개인 매물의 광고 원문과 사용자 확정값
CREATE TABLE rooms (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT UNSIGNED NOT NULL,
    building_id BIGINT UNSIGNED,
    name VARCHAR(200) NOT NULL,
    address VARCHAR(500) NOT NULL,
    unit_number VARCHAR(100),
    status ENUM('DRAFT', 'CONFIRMED') NOT NULL DEFAULT 'CONFIRMED',
    original_text MEDIUMTEXT,
    deposit BIGINT,
    rent BIGINT,
    fee BIGINT,
    area DECIMAL(10,4),
    area_type ENUM('EXCLUSIVE', 'SUPPLY', 'UNKNOWN') NOT NULL DEFAULT 'UNKNOWN',
    floor SMALLINT,
    tags JSON,
    options JSON,
    theme ENUM('sage', 'sand', 'clay') NOT NULL DEFAULT 'sage',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX ix_rooms_user (user_id, status, created_at),
    INDEX ix_rooms_building (building_id),
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (building_id) REFERENCES buildings(id),
    CONSTRAINT ck_rooms_deposit CHECK (deposit IS NULL OR deposit >= 0),
    CONSTRAINT ck_rooms_rent CHECK (rent IS NULL OR rent >= 0),
    CONSTRAINT ck_rooms_fee CHECK (fee IS NULL OR fee >= 0),
    CONSTRAINT ck_rooms_area CHECK (area IS NULL OR area >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 한 매물에 여러 방문 기록을 저장할 수 있다.
CREATE TABLE visits (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    room_id BIGINT UNSIGNED NOT NULL,
    visited_at DATETIME NOT NULL,
    note TEXT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX ix_visits_latest (room_id, visited_at, id),
    FOREIGN KEY (room_id) REFERENCES rooms(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 체크 항목은 표시 순서와 별개인 고유 키로 구분한다.
CREATE TABLE visit_check_items (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    visit_id BIGINT UNSIGNED NOT NULL,
    item_key VARCHAR(50) COLLATE utf8mb4_bin NOT NULL,
    label VARCHAR(200) NOT NULL,
    checked BOOLEAN NOT NULL DEFAULT FALSE,
    note TEXT,
    sort_order INT UNSIGNED NOT NULL DEFAULT 0,
    UNIQUE KEY uk_visit_item (visit_id, item_key),
    FOREIGN KEY (visit_id) REFERENCES visits(id) ON DELETE CASCADE,
    CONSTRAINT ck_visit_item_checked CHECK (checked IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 매물을 삭제해도 건물별 공개 후기는 유지한다.
CREATE TABLE reviews (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT UNSIGNED NOT NULL,
    building_id BIGINT UNSIGNED NOT NULL,
    period VARCHAR(100) NOT NULL,
    body TEXT NOT NULL,
    visibility ENUM('PUBLIC', 'HIDDEN', 'DELETED') NOT NULL DEFAULT 'PUBLIC',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX ix_reviews_building (building_id, visibility, created_at),
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (building_id) REFERENCES buildings(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 같은 후기·항목은 한 번만 저장하며 미응답은 NULL이다.
CREATE TABLE review_scores (
    review_id BIGINT UNSIGNED NOT NULL,
    category VARCHAR(20) NOT NULL,
    score TINYINT,
    PRIMARY KEY (review_id, category),
    FOREIGN KEY (review_id) REFERENCES reviews(id) ON DELETE CASCADE,
    CONSTRAINT ck_review_category CHECK (category IN ('방음', '수압', '채광', '환기', '주차', '관리', '벌레', '결로')),
    CONSTRAINT ck_review_score CHECK (score IS NULL OR score BETWEEN 1 AND 5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
