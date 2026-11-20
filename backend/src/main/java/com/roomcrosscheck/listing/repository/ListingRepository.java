package com.roomcrosscheck.listing.repository;

import com.roomcrosscheck.listing.domain.Listing;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** 매물 저장소. 회원 기능은 소유자 조건이 붙은 조회 메서드만 사용한다. */
public interface ListingRepository extends JpaRepository<Listing, String> {
    List<Listing> findAllByOwnerIdOrderByCreatedAtDesc(Long ownerId);
    Optional<Listing> findByIdAndOwnerId(String id, Long ownerId);
    List<Listing> findAllByIdInAndOwnerId(Collection<String> ids, Long ownerId);
    boolean existsByOwnerIdAndBuilding(Long ownerId, String building);
    Optional<Listing> findFirstByOwnerIdAndBuildingOrderByCreatedAtDesc(Long ownerId, String building);
}
