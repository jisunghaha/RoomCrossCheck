package com.roomcrosscheck.advertisement;

public interface ExtractionProvider {
    ExtractionResult extract(String originalText, String addressHint,
            String unitLabel, byte[] image, String imageContentType);
}
