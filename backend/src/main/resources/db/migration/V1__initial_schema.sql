-- RoomCrossCheck core schema: MySQL 8.4, 7 tables.
-- Draft migration, not yet applied. Run once in an empty selected database.
SET NAMES utf8mb4;
SET time_zone = '+00:00';

-- 1. Google login and membership
CREATE TABLE users (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    google_id VARCHAR(255) COLLATE utf8mb4_bin NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(254),
    role ENUM('USER', 'ADMIN') NOT NULL DEFAULT 'USER',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 2. Shared building identity; public reviews belong to this table.
CREATE TABLE buildings (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    official_key VARCHAR(100) COLLATE utf8mb4_bin UNIQUE,
    name VARCHAR(200),
    address VARCHAR(500) NOT NULL,
    building_dong VARCHAR(100),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 3. Private room + advertisement text + user-confirmed values
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

-- 4. Several visits per private room
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

-- 5. Stable item identity, including user-added items; no template table.
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

-- 6. Public building reviews, independent of private rooms
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

-- 7. One score per review/category; NULL means unanswered/not applicable.
CREATE TABLE review_scores (
    review_id BIGINT UNSIGNED NOT NULL,
    category VARCHAR(20) NOT NULL,
    score TINYINT,
    PRIMARY KEY (review_id, category),
    FOREIGN KEY (review_id) REFERENCES reviews(id) ON DELETE CASCADE,
    CONSTRAINT ck_review_category CHECK (category IN ('방음', '수압', '채광', '환기', '주차', '관리', '벌레', '결로')),
    CONSTRAINT ck_review_score CHECK (score IS NULL OR score BETWEEN 1 AND 5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
