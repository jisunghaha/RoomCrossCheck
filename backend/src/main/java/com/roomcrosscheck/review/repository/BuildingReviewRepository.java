package com.roomcrosscheck.review.repository;

import com.roomcrosscheck.review.domain.BuildingReview;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** 거주 후기 저장소. */
public interface BuildingReviewRepository extends JpaRepository<BuildingReview, String> {
    List<BuildingReview> findAllByBuildingKeyInOrderByCreatedAtDesc(Collection<String> buildingKeys);
    List<BuildingReview> findAllByBuildingKey(String buildingKey);
    boolean existsByAuthorIdAndBuildingKey(Long authorId, String buildingKey);
}
