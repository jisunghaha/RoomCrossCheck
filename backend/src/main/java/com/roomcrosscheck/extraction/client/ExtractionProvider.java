package com.roomcrosscheck.extraction.client;

import com.roomcrosscheck.extraction.dto.ExtractionResult;

public interface ExtractionProvider {
    ExtractionResult extract(String originalText, String addressHint,
            String unitLabel, byte[] image, String imageContentType);
}
