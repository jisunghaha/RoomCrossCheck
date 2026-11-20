package com.roomcrosscheck.review.service;

import com.roomcrosscheck.auth.service.MemberPrincipal;
import com.roomcrosscheck.listing.domain.BuildingKey;
import com.roomcrosscheck.listing.domain.Listing;
import com.roomcrosscheck.listing.repository.ListingRepository;
import com.roomcrosscheck.review.domain.BuildingReview;
import com.roomcrosscheck.review.dto.ReviewDto.ReviewListResponse;
import com.roomcrosscheck.review.dto.ReviewDto.ReviewRequest;
import com.roomcrosscheck.review.dto.ReviewDto.ReviewResponse;
import com.roomcrosscheck.review.repository.BuildingReviewRepository;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * SFR-017 거주 후기의 조회·작성·삭제를 처리한다.
 * <p>조회는 비로그인도 가능하다. 작성은 본인이 저장한 매물의 건물에 대해서만 허용해 임의 건물 키로
 * 후기를 쌓지 못하게 한다. 삭제는 작성자 또는 관리자만 할 수 있다.
 */
@Service
@Transactional(readOnly = true)
public class ReviewService {
    static final int MAX_BUILDINGS = 50;
    private final BuildingReviewRepository reviews;
    private final ListingRepository listings;
    private final ReviewStatisticsService statistics;

    public ReviewService(BuildingReviewRepository reviews, ListingRepository listings,
            ReviewStatisticsService statistics) {
        this.reviews = reviews;
        this.listings = listings;
        this.statistics = statistics;
    }

    /**
     * 건물들의 후기와 통계를 조회한다.
     *
     * @param buildingKeys 1~50개 건물 키
     * @param viewerId 로그인 회원 번호, 비로그인이면 null
     * @return 최신순 후기 목록과 건물별 통계
     */
    public ReviewListResponse list(List<String> buildingKeys, Long viewerId) {
        var keys = new LinkedHashSet<String>();
        for (String key : buildingKeys == null ? List.<String>of() : buildingKeys) {
            if (key != null && !key.isBlank() && key.length() <= 255) keys.add(key);
        }
        if (keys.isEmpty()) return new ReviewListResponse(List.of(), Map.of());
        if (keys.size() > MAX_BUILDINGS) throw bad("한 번에 조회할 수 있는 건물은 50개까지입니다.");
        var rows = reviews.findAllByBuildingKeyInOrderByCreatedAtDesc(keys).stream()
            .map(review -> ReviewResponse.from(review, viewerId)).toList();
        return new ReviewListResponse(rows, statistics.statistics(keys));
    }

    /**
     * 본인 매물의 건물에 후기를 작성한다.
     *
     * @param request 검증된 후기 입력
     * @param author 로그인 회원
     * @return 저장한 후기
     * @throws ResponseStatusException 점수 형식 오류 400, 본인 매물의 건물이 아니면 404, 같은 건물에 이미 쓴 후기가 있으면 409
     */
    @Transactional
    public ReviewResponse create(ReviewRequest request, MemberPrincipal author) {
        Listing listing = listings.findFirstByOwnerIdAndBuildingOrderByCreatedAtDesc(author.userId(), request.building())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "후기를 남길 건물을 내 매물에서 선택해 주세요."));
        if (reviews.existsByAuthorIdAndBuildingKey(author.userId(), listing.building)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이 건물에는 이미 후기를 남겼습니다. 기존 후기를 삭제한 뒤 다시 작성해 주세요.");
        }
        var scores = new LinkedHashMap<String, Integer>();
        for (var entry : request.scores().entrySet()) {
            if (!BuildingReview.CATEGORIES.contains(entry.getKey())) throw bad("알 수 없는 평가 항목입니다.");
            Integer score = entry.getValue();
            if (score != null && (score < 1 || score > 5)) throw bad("점수는 1~5점으로 입력해 주세요.");
        }
        for (String category : BuildingReview.CATEGORIES) scores.put(category, request.scores().get(category));
        BuildingReview review = new BuildingReview();
        review.buildingKey = listing.building;
        review.buildingLabel = buildingLabel(listing);
        review.authorId = author.userId();
        review.period = request.period().strip();
        review.body = request.body().strip();
        review.scores = scores;
        BuildingReview saved = reviews.saveAndFlush(review);
        statistics.evict(saved.buildingKey);
        return ReviewResponse.from(saved, author.userId());
    }

    /**
     * 후기를 삭제한다. 신고 처리에서도 관리자 권한으로 호출한다.
     *
     * @param id 후기 ID
     * @param actor 로그인 회원
     * @throws ResponseStatusException 없으면 404, 작성자·관리자가 아니면 403
     */
    @Transactional
    public void delete(String id, MemberPrincipal actor) {
        BuildingReview review = reviews.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "후기를 찾지 못했습니다."));
        if (!actor.administrator() && !Objects.equals(review.authorId, actor.userId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본인이 작성한 후기만 삭제할 수 있습니다.");
        }
        reviews.delete(review);
        reviews.flush();
        statistics.evict(review.buildingKey);
    }

    private static String buildingLabel(Listing listing) {
        var official = listing.official;
        if (official != null && official.buildingId() != null) {
            String address = official.address() != null && !official.address().isBlank()
                ? official.address() : official.jibunAddress();
            if (address != null && !address.isBlank()) return BuildingKey.publicLabel(address);
        }
        String label = BuildingKey.publicLabel(listing.address);
        return label.isBlank() ? "주소 미상 건물" : label;
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
