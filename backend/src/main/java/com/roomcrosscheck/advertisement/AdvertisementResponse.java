package com.roomcrosscheck.advertisement;

import java.time.Instant;

public record AdvertisementResponse(
    String id, Advertisement.Status status, String originalText, String addressHint,
    String unitLabel, String sourceUrl, String imageName, String imageContentType,
    Long imageSize, Instant createdAt, Instant confirmedAt
) {
    public static AdvertisementResponse from(Advertisement ad) {
        return new AdvertisementResponse(ad.id, ad.status, ad.originalText, ad.addressHint,
            ad.unitLabel, ad.sourceUrl, ad.imageName, ad.imageContentType,
            ad.imageSize, ad.createdAt, ad.confirmedAt);
    }
}
