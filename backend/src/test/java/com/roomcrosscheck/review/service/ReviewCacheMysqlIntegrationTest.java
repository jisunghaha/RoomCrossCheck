package com.roomcrosscheck.review.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.roomcrosscheck.auth.domain.User;
import com.roomcrosscheck.auth.service.MemberPrincipal;
import com.roomcrosscheck.listing.domain.Listing;
import com.roomcrosscheck.listing.dto.ListingRequest;
import com.roomcrosscheck.listing.repository.ListingRepository;
import com.roomcrosscheck.review.domain.BuildingReview;
import com.roomcrosscheck.review.dto.ReviewDto.ReviewRequest;
import com.roomcrosscheck.review.repository.BuildingReviewRepository;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(classes = ReviewCacheMysqlIntegrationTest.TestConfiguration.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnabledIfEnvironmentVariable(named = "REVIEW_MYSQL_TEST_URL", matches = ".+")
class ReviewCacheMysqlIntegrationTest {
    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = {Listing.class, BuildingReview.class})
    @EnableJpaRepositories(basePackageClasses = {ListingRepository.class, BuildingReviewRepository.class})
    @Import({ReviewService.class, ReviewStatisticsService.class})
    static class TestConfiguration {}

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        String url = System.getenv("REVIEW_MYSQL_TEST_URL");
        if (url == null || !url.matches("jdbc:mysql://[^/]+/rc_review_test_[a-z0-9_]+")) {
            throw new IllegalArgumentException("전용 rc_review_test_ DB만 테스트할 수 있습니다.");
        }
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> System.getenv("REVIEW_MYSQL_TEST_USER"));
        registry.add("spring.datasource.password", () -> System.getenv("REVIEW_MYSQL_TEST_PASSWORD"));
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.open-in-view", () -> "false");
    }

    @Autowired ReviewService reviews;
    @Autowired ListingRepository listings;
    @Autowired PlatformTransactionManager transactionManager;

    @Test void readsDuringUncommittedCreateAndDeleteDoNotLeaveStaleStatistics() {
        var user = new User("cache001", "hash", "캐시 검증", "cache001@example.test");
        ReflectionTestUtils.setField(user, "id", 1L);
        MemberPrincipal author = new MemberPrincipal(user);
        var request = new ListingRequest("테스트 매물", "부산광역시 가상구 21", null, 300000L,
            null, null, null, null, null, null);
        String key = listings.saveAndFlush(new Listing(request, author.userId(), null)).building;
        var reviewRequest = new ReviewRequest(key, "2025년", "통계 캐시 갱신을 검증하는 테스트 후기", Map.of("방음", 4));
        var writer = new TransactionTemplate(transactionManager);

        try (var reader = Executors.newSingleThreadExecutor()) {
            assertEquals(0, readCount(reader, key));
            var review = writer.execute(status -> {
                var saved = reviews.create(reviewRequest, author);
                assertEquals(0, readCount(reader, key));
                return saved;
            });
            assertEquals(1, readCount(reader, key));

            writer.executeWithoutResult(status -> {
                reviews.delete(review.id(), author);
                assertEquals(1, readCount(reader, key));
                status.setRollbackOnly();
            });
            assertEquals(1, readCount(reader, key));

            writer.executeWithoutResult(status -> {
                reviews.delete(review.id(), author);
                assertEquals(1, readCount(reader, key));
            });
            assertEquals(0, readCount(reader, key));
        }
    }

    private int readCount(ExecutorService reader, String key) {
        try {
            return reader.submit(() -> {
                var result = reviews.list(List.of(key), null);
                int count = result.stats().get(key).count();
                assertEquals(result.reviews().size(), count);
                return count;
            }).get(10, TimeUnit.SECONDS);
        } catch (Exception error) {
            throw new AssertionError("후기 통계를 별도 트랜잭션에서 조회하지 못했습니다.", error);
        }
    }
}
