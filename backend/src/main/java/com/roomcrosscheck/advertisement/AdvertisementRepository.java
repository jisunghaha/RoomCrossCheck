package com.roomcrosscheck.advertisement;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface AdvertisementRepository extends JpaRepository<Advertisement, String> {
    Optional<Advertisement> findByIdempotencyKey(String key);
}
