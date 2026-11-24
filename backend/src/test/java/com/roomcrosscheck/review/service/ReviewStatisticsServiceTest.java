package com.roomcrosscheck.review.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.roomcrosscheck.review.domain.BuildingReview;
import com.roomcrosscheck.review.repository.BuildingReviewRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 후기 통계가 null·범위 밖 점수를 제외하고, 캐시를 재사용하다가 작성·삭제·만료 시 다시 계산하는지 검증한다. */
class ReviewStatisticsServiceTest {
    private static BuildingReview review(Integer soundproof, Integer pressure) {
        BuildingReview review = new BuildingReview();
        Map<String, Integer> scores = new HashMap<>();
        scores.put("방음", soundproof);
        scores.put("수압", pressure);
        review.scores = scores;
        return review;
    }

    @Test void averagesOnlyValidScoresPerCategory() {
        var stats = ReviewStatisticsService.calculate(List.of(review(4, null), review(2, 9), review(null, 5)));
        assertEquals(3, stats.count());
        assertEquals(8, stats.items().size());
        assertEquals(2, stats.items().get(0).count());
        assertEquals(3.0, stats.items().get(0).value());
        assertEquals(1, stats.items().get(1).count());
        assertEquals(5.0, stats.items().get(1).value());
        assertNull(stats.items().get(2).value());
    }

    @Test void cacheIsReusedUntilEvictedOrExpired() {
        var repository = mock(BuildingReviewRepository.class);
        when(repository.findAllByBuildingKey("b")).thenReturn(List.of(review(5, 5)));
        var clock = new Clock() {
            Instant now = Instant.parse("2026-11-20T00:00:00Z");
            @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(java.time.ZoneId zone) { return this; }
            @Override public Instant instant() { return now; }
        };
        var service = new ReviewStatisticsService(repository, clock);
        assertEquals(1, service.statistics(List.of("b")).get("b").count());
        service.statistics(List.of("b"));
        verify(repository, times(1)).findAllByBuildingKey("b");

        when(repository.findAllByBuildingKey("b")).thenReturn(List.of(review(5, 5), review(1, 1)));
        service.evictAfterCommit("b");
        assertEquals(2, service.statistics(List.of("b")).get("b").count());
        verify(repository, times(2)).findAllByBuildingKey("b");

        clock.now = clock.now.plus(ReviewStatisticsService.TTL).plusSeconds(1);
        service.statistics(List.of("b"));
        verify(repository, times(3)).findAllByBuildingKey("b");
    }
}
