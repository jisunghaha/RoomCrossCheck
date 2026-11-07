package com.roomcrosscheck.extraction.dto;

import java.util.Set;

public record FieldEvidence(String quote, String source, String status, String reason) {
    public static final Set<String> FIELDS = Set.of("address", "depositWon", "monthlyRentWon",
        "maintenanceFeeWon", "areaSqm", "floor", "areaType", "options");

    public FieldEvidence {
        if (status == null || !Set.of("PRESENT", "NOT_STATED", "UNCERTAIN").contains(status)
                || (source != null && !Set.of("INPUT_TEXT", "IMAGE_READ_TEXT").contains(source))
                || (quote != null && quote.length() > 500) || (reason != null && reason.length() > 200))
            throw new IllegalArgumentException("Invalid extraction evidence");
    }
}
