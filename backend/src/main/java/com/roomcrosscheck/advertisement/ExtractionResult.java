package com.roomcrosscheck.advertisement;

import java.math.BigDecimal;

public record ExtractionResult(
    String rawText, String address, Long depositWon, Long monthlyRentWon,
    Long maintenanceFeeWon, BigDecimal areaSqm, Integer floor
) { }
