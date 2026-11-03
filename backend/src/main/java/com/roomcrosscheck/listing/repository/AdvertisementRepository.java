package com.roomcrosscheck.listing.repository;

import com.roomcrosscheck.listing.domain.Advertisement;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface AdvertisementRepository extends JpaRepository<Advertisement, String> {
    Optional<Advertisement> findByIdempotencyKey(String key);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select ad from Advertisement ad where ad.id = :id")
    Optional<Advertisement> findForRegistration(@Param("id") String id);
}
