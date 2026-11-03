package com.roomcrosscheck.extraction.dto;

import java.math.BigDecimal;
import java.util.List;
import com.roomcrosscheck.common.validation.OptionValues;
import com.roomcrosscheck.common.validation.OptionsDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

public record ExtractionResult(
    String rawText, String address, Long depositWon, Long monthlyRentWon,
    Long maintenanceFeeWon, BigDecimal areaSqm, Integer floor, String areaType,
    @JsonDeserialize(using = OptionsDeserializer.class) List<String> options
) {
    public ExtractionResult {
        if (areaType == null) areaType = "미상";
        options = OptionValues.normalize(options);
    }

    public ExtractionResult(String rawText, String address, Long depositWon, Long monthlyRentWon,
            Long maintenanceFeeWon, BigDecimal areaSqm, Integer floor, String areaType) {
        this(rawText, address, depositWon, monthlyRentWon, maintenanceFeeWon, areaSqm, floor, areaType, null);
    }
}
