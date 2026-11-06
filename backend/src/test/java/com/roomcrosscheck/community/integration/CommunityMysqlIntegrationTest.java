package com.roomcrosscheck.community.integration;

import com.roomcrosscheck.community.domain.Post;
import com.roomcrosscheck.community.dto.CommunityDto.CommentRequest;
import com.roomcrosscheck.community.dto.CommunityDto.PostRequest;
import com.roomcrosscheck.community.repository.PostRepository;
import com.roomcrosscheck.community.service.CommentService;
import com.roomcrosscheck.community.service.CurrentActor;
import com.roomcrosscheck.community.service.PostLikeService;
import com.roomcrosscheck.community.service.PostService;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 임시 MySQL에서 실제 매핑·제약·트랜잭션·동시 요청을 검증한다.
 * <p>전용 환경변수가 없으면 실행하지 않으며 기존 애플리케이션 DB는 사용하지 않는다.
 */
@SpringBootTest(classes = CommunityMysqlIntegrationTest.TestConfiguration.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnabledIfEnvironmentVariable(named = "COMMUNITY_MYSQL_TEST_URL", matches = ".+")
class CommunityMysqlIntegrationTest {
    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = Post.class)
    @EnableJpaRepositories(basePackageClasses = PostRepository.class)
    @Import({PostService.class, CommentService.class, PostLikeService.class})
    static class TestConfiguration {}

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        String url = System.getenv("COMMUNITY_MYSQL_TEST_URL");
        if (url == null || !url.matches("jdbc:mysql://[^/]+/rc_community_test_[a-z0-9_]+(?:\\?.*)?")) {
            throw new IllegalArgumentException("전용 rc_community_test_ DB만 테스트할 수 있습니다.");
        }
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> System.getenv("COMMUNITY_MYSQL_TEST_USER"));
        registry.add("spring.datasource.password", () -> System.getenv("COMMUNITY_MYSQL_TEST_PASSWORD"));
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.open-in-view", () -> "false");
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "12");
        registry.add("spring.flyway.enabled", () -> "false");
        registry.add("spring.sql.init.mode", () -> "never");
    }

    @Autowired PostService posts;
    @Autowired CommentService comments;
    @Autowired PostLikeService likes;
    @Autowired PostRepository repository;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;

    /** 실제 인증에 등록하지 않고 Service 권한 검사에만 사용하는 테스트 정보다. */
    private record Actor(Long userId, boolean administrator) implements CurrentActor {}
    private final Actor owner = new Actor(15L, false);
    private final Actor other = new Actor(20L, false);
    private final Actor admin = new Actor(30L, true);

    @AfterEach void removeTestRows() {
        jdbc.execute("DROP TRIGGER IF EXISTS rc_community_test_delete_failure");
        jdbc.update("DELETE FROM community_comments");
        jdbc.update("DELETE FROM community_post_likes");
        jdbc.update("DELETE FROM community_posts");
    }

    @Test void schemaHasExpectedUserTypesForeignKeysAndUniqueConstraint() {
        for (String table : new String[] {"community_posts", "community_comments", "community_post_likes"}) {
            String column = table.equals("community_post_likes") ? "user_id" : "author_id";
            String type = jdbc.queryForObject("SELECT COLUMN_TYPE FROM information_schema.COLUMNS "
                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?", String.class, table, column);
            assertEquals("bigint unsigned", type);
        }
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.REFERENTIAL_CONSTRAINTS "
            + "WHERE CONSTRAINT_SCHEMA = DATABASE() AND REFERENCED_TABLE_NAME = 'community_posts'", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS "
            + "WHERE CONSTRAINT_SCHEMA = DATABASE() AND CONSTRAINT_NAME = 'uk_community_post_likes_post_user' "
            + "AND CONSTRAINT_TYPE = 'UNIQUE'", Integer.class));
    }

    @Test void postAndCommentRoundTripPreserveUnicodeDatesAndVersion() {
        var post = posts.createPost(new PostRequest("한글 게시글 🏠", "본문 😀"), owner);
        assertNotNull(post.createdAt());
        assertNotNull(post.updatedAt());
        assertEquals(post.title(), posts.getPost(post.id()).title());
        long version = repository.findById(post.id()).orElseThrow().version;
        var updated = posts.updatePost(post.id(), new PostRequest("수정 제목", "수정 본문"), owner);
        assertFalse(updated.updatedAt().isBefore(post.updatedAt()));
        assertEquals(version + 1, repository.findById(post.id()).orElseThrow().version);
        var comment = comments.createComment(post.id(), new CommentRequest("댓글 😀"), other);
        assertEquals(20L, comment.authorId());
        assertNotNull(comment.createdAt());
        assertEquals(comment.id(), comments.listComments(post.id(), 0, 20).getContent().getFirst().id());
        assertEquals("댓글 수정", comments.updateComment(post.id(), comment.id(), new CommentRequest("댓글 수정"), admin).content());
        comments.deleteComment(post.id(), comment.id(), other);
        assertEquals(0, comments.listComments(post.id(), 0, 20).getTotalElements());
    }

    @Test void rejectedWritesDoNotChangeStoredData() {
        var post = newPost();
        var comment = comments.createComment(post.id(), new CommentRequest("원본 댓글"), owner);
        assertStatus(401, () -> posts.createPost(new PostRequest("제목", "내용"), null));
        assertStatus(403, () -> posts.updatePost(post.id(), new PostRequest("변경", "내용"), other));
        assertStatus(403, () -> comments.updateComment(post.id(), comment.id(), new CommentRequest("변경"), other));
        assertEquals("제목", posts.getPost(post.id()).title());
        assertEquals("원본 댓글", comments.listComments(post.id(), 0, 20).getContent().getFirst().content());
        assertEquals(1, repository.count());
    }

    @Test void databaseRejectsDuplicateLikeAndMissingParent() {
        var post = newPost();
        likes.likePost(post.id(), owner);
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
            "INSERT INTO community_post_likes (id, post_id, user_id, created_at) VALUES (?, ?, ?, CURRENT_TIMESTAMP(6))",
            "duplicate-test", post.id(), owner.userId()));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
            "INSERT INTO community_comments (id, post_id, author_id, content, created_at, updated_at, version) "
            + "VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), 0)",
            "missing-parent", "missing-post", owner.userId(), "내용"));
        assertEquals(1, likes.getLikes(post.id(), null).count());
    }

    @Test void simultaneousLikesFromSameUserStoreExactlyOneRow() throws Exception {
        var post = newPost();
        parallel(8, index -> () -> {
            assertTrue(likes.likePost(post.id(), owner).liked());
            return null;
        });
        assertEquals(1, likes.getLikes(post.id(), owner).count());
        assertTrue(likes.getLikes(post.id(), owner).liked());
        assertEquals(0, likes.unlikePost(post.id(), owner).count());
        assertEquals(0, likes.unlikePost(post.id(), owner).count());
    }

    @Test void simultaneousDifferentUsersKeepSeparateLikes() throws Exception {
        var post = newPost();
        parallel(8, index -> () -> {
            likes.likePost(post.id(), new Actor(100L + index, false));
            return null;
        });
        assertEquals(8, likes.getLikes(post.id(), null).count());
        assertEquals(7, likes.unlikePost(post.id(), new Actor(100L, false)).count());
        assertTrue(likes.getLikes(post.id(), new Actor(101L, false)).liked());
    }

    @Test void deletingPostAlsoDeletesCommentsAndLikes() {
        var post = newPost();
        comments.createComment(post.id(), new CommentRequest("댓글"), other);
        likes.likePost(post.id(), other);
        posts.deletePost(post.id(), owner);
        assertEquals(0, repository.count());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM community_comments", Long.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM community_post_likes", Long.class));
    }

    @Test void failedPostDeletionRollsBackPreviouslyDeletedChildren() {
        var post = newPost();
        comments.createComment(post.id(), new CommentRequest("삭제 전 댓글"), other);
        likes.likePost(post.id(), other);
        // 마지막 게시글 삭제만 실패시켜 앞서 실행된 댓글·좋아요 삭제까지 복원되는지 확인한다.
        jdbc.execute("CREATE TRIGGER rc_community_test_delete_failure BEFORE DELETE ON community_posts "
            + "FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'test deletion failure'");
        try {
            assertThrows(RuntimeException.class, () -> posts.deletePost(post.id(), owner));
            assertEquals(post.id(), posts.getPost(post.id()).id());
            assertEquals(1, comments.listComments(post.id(), 0, 20).getTotalElements());
            assertEquals(1, likes.getLikes(post.id(), other).count());
        } finally {
            jdbc.execute("DROP TRIGGER rc_community_test_delete_failure");
        }
    }

    @Test void staleEntityCannotOverwriteNewerPost() {
        var post = newPost();
        Post stale = repository.findById(post.id()).orElseThrow();
        posts.updatePost(post.id(), new PostRequest("최신 제목", "최신 내용"), owner);
        stale.title = "오래된 수정";
        assertThrows(OptimisticLockingFailureException.class, () -> repository.saveAndFlush(stale));
        assertEquals("최신 제목", posts.getPost(post.id()).title());
    }

    @Test void outerTransactionRollbackRestoresAllDeletedRows() {
        var post = newPost();
        comments.createComment(post.id(), new CommentRequest("댓글"), other);
        likes.likePost(post.id(), other);
        var transaction = new TransactionTemplate(transactionManager);
        assertThrows(IllegalStateException.class, () -> transaction.execute(status -> {
            posts.deletePost(post.id(), owner);
            throw new IllegalStateException("테스트 롤백");
        }));
        assertEquals(1, repository.count());
        assertEquals(1, comments.listComments(post.id(), 0, 20).getTotalElements());
        assertEquals(1, likes.getLikes(post.id(), other).count());
    }

    private com.roomcrosscheck.community.dto.CommunityDto.PostResponse newPost() {
        return posts.createPost(new PostRequest("제목", "내용"), owner);
    }

    private static void parallel(int count, java.util.function.IntFunction<Callable<Void>> action) throws Exception {
        var ready = new CountDownLatch(count);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(count)) {
            var futures = new ArrayList<java.util.concurrent.Future<Void>>();
            for (int i = 0; i < count; i++) {
                var task = action.apply(i);
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("동시 실행 대기 초과");
                    return task.call();
                }));
            }
            try {
                assertTrue(ready.await(15, TimeUnit.SECONDS));
            } finally {
                start.countDown();
            }
            for (var future : futures) future.get(45, TimeUnit.SECONDS);
        }
    }

    private static void assertStatus(int expected, Runnable action) {
        var error = assertThrows(ResponseStatusException.class, action::run);
        assertEquals(expected, error.getStatusCode().value());
    }
}
