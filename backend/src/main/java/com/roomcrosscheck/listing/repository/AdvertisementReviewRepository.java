package com.roomcrosscheck.listing.repository;

import com.roomcrosscheck.listing.domain.AdvertisementReview;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AdvertisementReviewRepository extends JpaRepository<AdvertisementReview, String> {
    List<AdvertisementReview> findTop20ByAdvertisement_IdOrderByVersionDesc(String advertisementId);
}
