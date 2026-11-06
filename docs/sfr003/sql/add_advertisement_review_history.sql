-- Only for a backed-up, approved local advertisements DB. Never run against team rooms/Flyway/shared DBs.
-- Inspect existing columns/tables first; do not reapply after Hibernate update has created them.
ALTER TABLE advertisements ADD COLUMN review_version BIGINT NOT NULL DEFAULT 0;
CREATE TABLE advertisement_reviews (
    id VARCHAR(255) NOT NULL PRIMARY KEY,
    advertisement_id VARCHAR(255) NOT NULL,
    review_version BIGINT NOT NULL,
    reviewed_json TEXT NOT NULL,
    reviewed_at DATETIME(6) NULL,
    CONSTRAINT uk_ad_review_version UNIQUE (advertisement_id, review_version),
    CONSTRAINT fk_ad_review_source FOREIGN KEY (advertisement_id) REFERENCES advertisements(id)
);
