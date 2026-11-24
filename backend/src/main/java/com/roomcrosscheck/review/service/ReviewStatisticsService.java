package com.roomcrosscheck.review.service;

import com.roomcrosscheck.review.domain.BuildingReview;
import com.roomcrosscheck.review.dto.ReviewDto.BuildingStatistics;
import com.roomcrosscheck.review.dto.ReviewDto.CategoryStatistic;
import com.roomcrosscheck.review.repository.BuildingReviewRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * SFR-018 건물별 후기 통계를 계산하고 PER-002에 따라 서버 메모리에 캐시한다.
 * <p>후기 작성·삭제가 커밋되면 해당 건물의 캐시를 지운다. 다른 서버 인스턴스의 변경은 반영되지 않으므로
 * 10분 만료로 보정한다. 서버를 재시작하면 캐시는 비며 다음 조회 때 다시 계산한다.
 */
@Service
public class ReviewStatisticsService {
    static final Duration TTL = Duration.ofMinutes(10);
    private static final int MAX_ENTRIES = 5000;
    private record Cached(BuildingStatistics value, Instant expires) {}

    private final BuildingReviewRepository reviews;
    private final Clock clock;
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    @Autowired
    public ReviewStatisticsService(BuildingReviewRepository reviews) { this(reviews, Clock.systemUTC()); }

    ReviewStatisticsService(BuildingReviewRepository reviews, Clock clock) {
        this.reviews = reviews;
        this.clock = clock;
    }

    /**
     * 건물별 통계를 반환한다. 유효한 캐시가 있으면 DB를 조회하지 않는다.
     *
     * @param buildingKeys 건물 키 목록
     * @return 건물 키 → 통계. 후기가 없는 건물도 0건 통계로 포함한다.
     */
    public Map<String, BuildingStatistics> statistics(Collection<String> buildingKeys) {
        Instant now = clock.instant();
        var result = new LinkedHashMap<String, BuildingStatistics>();
        for (String key : buildingKeys) {
            Cached cached = cache.get(key);
            if (cached == null || !cached.expires().isAfter(now)) {
                if (cache.size() >= MAX_ENTRIES) cache.clear();
                cached = cache.compute(key, (building, current) -> {
                    if (current != null && current.expires().isAfter(now)) return current;
                    return new Cached(calculate(reviews.findAllByBuildingKey(building)), now.plus(TTL));
                });
            }
            result.put(key, cached.value());
        }
        return result;
    }

    /** 작성·삭제 트랜잭션이 커밋된 뒤 해당 건물의 캐시를 지운다. */
    void evictAfterCommit(String buildingKey) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { cache.remove(buildingKey); }
            });
        } else {
            cache.remove(buildingKey);
        }
    }

    /**
     * 후기 목록으로 항목별 평균을 계산한다. 1~5 범위 밖이나 null 점수는 제외한다.
     *
     * @param rows 같은 건물의 후기
     * @return 후기 수와 항목별 평균
     */
    static BuildingStatistics calculate(List<BuildingReview> rows) {
        var items = new ArrayList<CategoryStatistic>();
        for (String category : BuildingReview.CATEGORIES) {
            int count = 0;
            int sum = 0;
            for (BuildingReview row : rows) {
                Integer score = row.scores == null ? null : row.scores.get(category);
                if (score != null && score >= 1 && score <= 5) { count++; sum += score; }
            }
            items.add(new CategoryStatistic(category, count, count == 0 ? null : (double) sum / count));
        }
        return new BuildingStatistics(rows.size(), List.copyOf(items));
    }
}
