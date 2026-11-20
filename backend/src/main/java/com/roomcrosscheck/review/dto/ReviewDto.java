package com.roomcrosscheck.review.dto;

import com.roomcrosscheck.review.domain.BuildingReview;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

/** 거주 후기·통계 API의 요청·응답 형식이다. */
public final class ReviewDto {
    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");

    private ReviewDto() {}

    /**
     * 후기 작성 요청.
     *
     * @param building 본인 매물의 건물 키
     * @param period 거주 시기, 최대 50자
     * @param body 10~1500자 후기
     * @param scores 항목별 1~5점 또는 null
     */
    public record ReviewRequest(
        @NotBlank @Size(max = 255) String building,
        @NotBlank @Size(max = 50) String period,
        @NotBlank @Size(min = 10, max = 1500) String body,
        @NotNull Map<String, Integer> scores
    ) {}

    /**
     * 공개 후기. 작성자 회원 번호 대신 요청자 본인 여부만 알려 준다.
     *
     * @param createdAt 한국 시간 기준 작성일(yyyy-MM-dd)
     * @param mine 로그인 회원이 작성했으면 true
     */
    public record ReviewResponse(String id, String building, String buildingLabel, String period, String body,
            Map<String, Integer> scores, String createdAt, boolean mine, boolean demo) {
        public static ReviewResponse from(BuildingReview review, Long viewerId) {
            return new ReviewResponse(review.id, review.buildingKey, review.buildingLabel, review.period, review.body,
                review.scores, review.createdAt.atZone(KOREA).toLocalDate().toString(),
                viewerId != null && viewerId.equals(review.authorId), false);
        }
    }

    /** 항목별 평균. value는 점수가 하나도 없으면 null이다. */
    public record CategoryStatistic(String label, int count, Double value) {}

    /** 건물 하나의 후기 수와 항목별 평균. */
    public record BuildingStatistics(int count, List<CategoryStatistic> items) {}

    /** 요청한 건물들의 후기 목록과 통계. */
    public record ReviewListResponse(List<ReviewResponse> reviews, Map<String, BuildingStatistics> stats) {}
}
