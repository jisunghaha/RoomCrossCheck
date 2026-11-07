package com.roomcrosscheck.listing.dto;

import com.roomcrosscheck.extraction.dto.ExtractionResult;
import com.roomcrosscheck.listing.domain.Advertisement;

import java.time.Instant;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

public record AdvertisementResponse(
    String id, Advertisement.Status status, String originalText, String addressHint,
    String unitLabel, String sourceUrl, String imageName, String imageContentType,
    Long imageSize, Instant createdAt, Instant confirmedAt,
    ExtractionResult extraction, ExtractionResult reviewed, String extractionError,
    Instant extractedAt, Instant reviewedAt, long reviewVersion
) {
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();
    public static AdvertisementResponse from(Advertisement ad) {
        return new AdvertisementResponse(ad.id, ad.status, ad.originalText, ad.addressHint,
            ad.unitLabel, ad.sourceUrl, ad.imageName, ad.imageContentType,
            ad.imageSize, ad.createdAt, ad.confirmedAt, read(ad.extractionJson),
            read(ad.reviewedJson), ad.extractionError, ad.extractedAt, ad.reviewedAt, ad.reviewVersion);
    }
    private static ExtractionResult read(String json) {
        if (json == null) return null;
        try { return MAPPER.readValue(json, ExtractionResult.class); }
        catch (Exception e) { throw new IllegalStateException("저장된 광고 추출 결과를 읽을 수 없습니다.", e); }
    }
}
