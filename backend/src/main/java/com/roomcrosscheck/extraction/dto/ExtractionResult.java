package com.roomcrosscheck.extraction.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import com.roomcrosscheck.common.validation.OptionValues;
import com.roomcrosscheck.common.validation.OptionsDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

public record ExtractionResult(
    String rawText, String address, Long depositWon, Long monthlyRentWon,
    Long maintenanceFeeWon, BigDecimal areaSqm, Integer floor, String areaType,
    @JsonDeserialize(using = OptionsDeserializer.class) List<String> options,
    Map<String, FieldEvidence> evidence
) {
    public ExtractionResult {
        if (areaType == null) areaType = "미상";
        options = OptionValues.normalize(options);
        if (evidence != null) {
            if (!FieldEvidence.FIELDS.containsAll(evidence.keySet()) || evidence.values().stream().anyMatch(java.util.Objects::isNull))
                throw new IllegalArgumentException("Invalid extraction evidence fields");
            evidence = Map.copyOf(evidence);
        }
    }

    public ExtractionResult(String rawText, String address, Long depositWon, Long monthlyRentWon,
            Long maintenanceFeeWon, BigDecimal areaSqm, Integer floor, String areaType, List<String> options) {
        this(rawText, address, depositWon, monthlyRentWon, maintenanceFeeWon, areaSqm, floor, areaType, options, null);
    }

    public ExtractionResult withEvidence(Map<String, FieldEvidence> value) {
        return new ExtractionResult(rawText, address, depositWon, monthlyRentWon, maintenanceFeeWon,
            areaSqm, floor, areaType, options, value);
    }

    public ExtractionResult(String rawText, String address, Long depositWon, Long monthlyRentWon,
            Long maintenanceFeeWon, BigDecimal areaSqm, Integer floor, String areaType) {
        this(rawText, address, depositWon, monthlyRentWon, maintenanceFeeWon, areaSqm, floor, areaType, null);
    }
}
