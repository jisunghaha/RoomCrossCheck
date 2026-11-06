package com.roomcrosscheck.listing.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** Immutable snapshots of confirmed values. Authentication identity is added only after team integration. */
@Entity
@Table(name = "advertisement_reviews", uniqueConstraints =
    @UniqueConstraint(columnNames = {"advertisement_id", "review_version"}))
public class AdvertisementReview {
    @Id public String id = UUID.randomUUID().toString();
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "advertisement_id", nullable = false, updatable = false)
    public Advertisement advertisement;
    @Column(name = "review_version", nullable = false, updatable = false) public long version;
    @Column(columnDefinition = "text", nullable = false, updatable = false) public String reviewedJson;
    @Column(updatable = false) public Instant reviewedAt;

    public static AdvertisementReview snapshot(Advertisement ad) {
        var entry = new AdvertisementReview();
        entry.advertisement = ad;
        entry.version = ad.reviewVersion;
        entry.reviewedJson = ad.reviewedJson;
        entry.reviewedAt = ad.reviewedAt;
        return entry;
    }
}
