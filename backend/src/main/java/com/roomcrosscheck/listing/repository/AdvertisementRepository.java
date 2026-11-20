package com.roomcrosscheck.listing.repository;

import com.roomcrosscheck.listing.domain.Advertisement;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface AdvertisementRepository extends JpaRepository<Advertisement, String> {
    Optional<Advertisement> findByIdempotencyKey(String key);
    List<Advertisement> findAllByOwnerIdOrderByCreatedAtDesc(Long ownerId);
}
