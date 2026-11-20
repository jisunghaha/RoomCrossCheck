package com.roomcrosscheck.common;

import com.roomcrosscheck.listing.domain.BuildingKey;
import com.roomcrosscheck.listing.repository.ListingRepository;
import java.sql.Connection;
import java.util.List;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * MySQL에 외래키·고유 제약과 탈퇴용 NULL 허용 컬럼을 적용한다(SIR-001).
 * <p>JPA {@code ddl-auto=update}는 이미 있는 테이블에 새 제약을 확실히 추가하거나 NULL 여부를 바꾸지
 * 않으므로, 서버가 시작할 때 information_schema를 확인해 빠진 제약만 추가한다. 여러 번 실행해도 결과가 같다.
 * <p>탈퇴 규칙은 DB가 강제한다. 개인 자료(매물·광고·방문 기록·설정·좋아요·재설정 코드)는
 * {@code ON DELETE CASCADE}로 함께 지우고, 공개 자료(게시글·댓글·후기·신고 기록)는
 * {@code ON DELETE SET NULL}로 작성자만 비운다.
 * <p>기존 자료가 제약을 어기면 그 제약은 건너뛰고 경고만 남겨 서버 시작을 막지 않는다.
 * 존재하지 않는 회원·매물을 가리키는 고아 값은 NULL 허용 컬럼이면 NULL로, 아니면 행을 삭제한 뒤 적용한다.
 * MySQL이 아닌 DB(테스트용 대역 등)에서는 아무것도 하지 않는다.
 */
@Component
@Order(0)
public class SchemaConstraints implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(SchemaConstraints.class);

    /** 외래키 정의. onDelete는 CASCADE 또는 SET NULL. */
    record ForeignKey(String name, String table, String column, String refTable, String onDelete) {}

    /** 고유 제약 정의. */
    record Unique(String name, String table, List<String> columns) {}

    /** 탈퇴한 회원의 공개 자료를 남기기 위해 NULL을 허용해야 하는 작성자 컬럼. */
    static final List<String[]> NULLABLE = List.of(
        new String[] {"community_posts", "author_id"},
        new String[] {"community_comments", "author_id"},
        new String[] {"building_reviews", "author_id"},
        new String[] {"reports", "reporter_id"});

    static final List<ForeignKey> FOREIGN_KEYS = List.of(
        new ForeignKey("fk_listings_owner", "listings", "owner_id", "users", "CASCADE"),
        new ForeignKey("fk_listings_advertisement", "listings", "advertisement_id", "advertisements", "SET NULL"),
        new ForeignKey("fk_advertisements_owner", "advertisements", "owner_id", "users", "CASCADE"),
        new ForeignKey("fk_visit_records_listing", "visit_records", "listing_id", "listings", "CASCADE"),
        new ForeignKey("fk_visit_records_owner", "visit_records", "owner_id", "users", "CASCADE"),
        new ForeignKey("fk_member_preferences_user", "member_preferences", "user_id", "users", "CASCADE"),
        new ForeignKey("fk_password_reset_codes_user", "password_reset_codes", "user_id", "users", "CASCADE"),
        new ForeignKey("fk_oauth_accounts_user", "oauth_accounts", "user_id", "users", "CASCADE"),
        new ForeignKey("fk_building_reviews_author", "building_reviews", "author_id", "users", "SET NULL"),
        new ForeignKey("fk_reports_reporter", "reports", "reporter_id", "users", "SET NULL"),
        new ForeignKey("fk_reports_target_author", "reports", "target_author_id", "users", "SET NULL"),
        new ForeignKey("fk_reports_handler", "reports", "handler_id", "users", "SET NULL"),
        new ForeignKey("fk_community_posts_author", "community_posts", "author_id", "users", "SET NULL"),
        new ForeignKey("fk_community_comments_author", "community_comments", "author_id", "users", "SET NULL"),
        new ForeignKey("fk_community_post_likes_user", "community_post_likes", "user_id", "users", "CASCADE"));

    static final List<Unique> UNIQUES = List.of(
        // 한 광고로는 매물을 하나만 만든다. 직접 입력 매물(NULL)은 여러 개여도 된다.
        new Unique("uk_listings_advertisement", "listings", List.of("advertisement_id")),
        // 같은 회원은 같은 주소(동·호 포함)의 매물을 하나만 저장한다.
        new Unique("uk_listings_owner_address", "listings", List.of("owner_id", "address_key")),
        // 같은 회원은 건물마다 후기를 하나만 쓴다. 탈퇴로 작성자가 NULL이 된 후기는 제외된다.
        new Unique("uk_building_reviews_author_building", "building_reviews", List.of("author_id", "building_key")));

    private final DataSource dataSource;
    private final JdbcTemplate jdbc;
    private final ListingRepository listings;

    public SchemaConstraints(DataSource dataSource, ListingRepository listings) {
        this.dataSource = dataSource;
        this.jdbc = new JdbcTemplate(dataSource);
        this.listings = listings;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!mysql()) return;
        backfillAddressKeys();
        for (String[] column : NULLABLE) allowNull(column[0], column[1]);
        for (ForeignKey key : FOREIGN_KEYS) apply(key);
        for (Unique unique : UNIQUES) apply(unique);
    }

    private boolean mysql() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.getMetaData().getDatabaseProductName().toLowerCase().contains("mysql");
        } catch (Exception error) {
            log.warn("DB 종류를 확인하지 못해 제약 적용을 건너뜁니다: {}", error.getClass().getSimpleName());
            return false;
        }
    }

    /** 주소 키 컬럼이 생기기 전에 저장한 매물의 값을 채운다. 고유 제약보다 먼저 실행한다. */
    void backfillAddressKeys() {
        var missing = listings.findAllByAddressKeyIsNull();
        for (var listing : missing) {
            jdbc.update("UPDATE listings SET address_key = ? WHERE id = ?", BuildingKey.ofFullAddress(listing.address), listing.id);
        }
        if (!missing.isEmpty()) log.info("기존 매물 {}건의 주소 키를 채웠습니다.", missing.size());
    }

    private boolean tableExists(String table) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.TABLES"
            + " WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?", Integer.class, table);
        return count != null && count > 0;
    }

    private void allowNull(String table, String column) {
        if (!tableExists(table)) return;
        var rows = jdbc.queryForList("SELECT IS_NULLABLE, COLUMN_TYPE FROM information_schema.COLUMNS"
            + " WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?", table, column);
        if (rows.isEmpty() || "YES".equals(rows.get(0).get("IS_NULLABLE"))) return;
        try {
            jdbc.execute("ALTER TABLE `" + table + "` MODIFY `" + column + "` " + rows.get(0).get("COLUMN_TYPE") + " NULL");
            log.info("{}.{} 컬럼이 NULL을 허용하도록 바꿨습니다.", table, column);
        } catch (Exception error) {
            log.warn("{}.{} NULL 허용 변경 실패: {}", table, column, error.getMessage());
        }
    }

    private void apply(ForeignKey key) {
        if (!tableExists(key.table()) || !tableExists(key.refTable())) return;
        var existing = jdbc.queryForList("SELECT DELETE_RULE FROM information_schema.REFERENTIAL_CONSTRAINTS"
            + " WHERE CONSTRAINT_SCHEMA = DATABASE() AND CONSTRAINT_NAME = ?", key.name());
        if (!existing.isEmpty() && key.onDelete().equals(existing.get(0).get("DELETE_RULE"))) return;
        try {
            if (!existing.isEmpty()) {
                // JPA가 만든 기존 외래키는 삭제 규칙이 없으므로(RESTRICT) 탈퇴 규칙에 맞게 다시 만든다.
                jdbc.execute("ALTER TABLE `" + key.table() + "` DROP FOREIGN KEY `" + key.name() + "`");
            }
            String orphan = "`" + key.column() + "` IS NOT NULL AND `" + key.column() + "` NOT IN (SELECT `id` FROM `"
                + key.refTable() + "`)";
            boolean nullable = "YES".equals(jdbc.queryForObject("SELECT IS_NULLABLE FROM information_schema.COLUMNS"
                + " WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                String.class, key.table(), key.column()));
            int cleaned = nullable
                ? jdbc.update("UPDATE `" + key.table() + "` SET `" + key.column() + "` = NULL WHERE " + orphan)
                : jdbc.update("DELETE FROM `" + key.table() + "` WHERE " + orphan);
            if (cleaned > 0) log.warn("{}.{}의 존재하지 않는 참조 {}건을 {}했습니다.", key.table(), key.column(), cleaned,
                nullable ? "NULL로 변경" : "삭제");
            jdbc.execute("ALTER TABLE `" + key.table() + "` ADD CONSTRAINT `" + key.name() + "` FOREIGN KEY (`"
                + key.column() + "`) REFERENCES `" + key.refTable() + "` (`id`) ON DELETE " + key.onDelete());
            log.info("외래키 {}를 적용했습니다.", key.name());
        } catch (Exception error) {
            log.warn("외래키 {} 적용 실패: {}", key.name(), error.getMessage());
        }
    }

    private void apply(Unique unique) {
        if (!tableExists(unique.table())) return;
        Integer present = jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.STATISTICS"
            + " WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND INDEX_NAME = ?",
            Integer.class, unique.table(), unique.name());
        if (present != null && present > 0) return;
        String columns = "`" + String.join("`, `", unique.columns()) + "`";
        String notNull = unique.columns().stream().map(c -> "`" + c + "` IS NOT NULL")
            .reduce((a, b) -> a + " AND " + b).orElse("1=1");
        Integer duplicates = jdbc.queryForObject("SELECT COUNT(*) FROM (SELECT 1 FROM `" + unique.table() + "` WHERE "
            + notNull + " GROUP BY " + columns + " HAVING COUNT(*) > 1) d", Integer.class);
        if (duplicates != null && duplicates > 0) {
            log.warn("고유 제약 {}를 건너뜁니다. 이미 중복된 값이 {}건 있어 정리가 필요합니다.", unique.name(), duplicates);
            return;
        }
        try {
            jdbc.execute("ALTER TABLE `" + unique.table() + "` ADD CONSTRAINT `" + unique.name() + "` UNIQUE (" + columns + ")");
            log.info("고유 제약 {}를 적용했습니다.", unique.name());
        } catch (Exception error) {
            log.warn("고유 제약 {} 적용 실패: {}", unique.name(), error.getMessage());
        }
    }
}
