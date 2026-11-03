package com.roomcrosscheck.extraction.dto;

import java.util.HashMap;
import java.util.Map;

public final class EvidencePolicy {
    private EvidencePolicy() {}

    public static ExtractionResult check(ExtractionResult result, String originalText, boolean hasImage) {
        if (result.evidence() == null) return result;
        Map<String, Object> values = new HashMap<>();
        values.put("address", result.address()); values.put("depositWon", result.depositWon());
        values.put("monthlyRentWon", result.monthlyRentWon()); values.put("maintenanceFeeWon", result.maintenanceFeeWon());
        values.put("areaSqm", result.areaSqm()); values.put("floor", result.floor());
        values.put("areaType", "미상".equals(result.areaType()) ? null : result.areaType());
        values.put("options", result.options());
        Map<String, FieldEvidence> checked = new HashMap<>();
        result.evidence().forEach((field, item) -> {
            String reference = "INPUT_TEXT".equals(item.source()) ? originalText
                : "IMAGE_READ_TEXT".equals(item.source()) && hasImage ? result.rawText() : null;
            boolean matches = item.quote() != null && !item.quote().isBlank()
                && reference != null && reference.contains(item.quote());
            boolean absent = "NOT_STATED".equals(item.status());
            if ((absent && values.get(field) == null || "UNCERTAIN".equals(item.status()))
                    && item.quote() == null && item.source() == null) {
                checked.put(field, item);
            } else if (!absent && matches && (!"PRESENT".equals(item.status()) || values.get(field) != null)) {
                checked.put(field, item);
            } else {
                checked.put(field, new FieldEvidence(null, null, "UNCERTAIN",
                    "근거 문구 또는 추출값과 상태의 관계를 확인하지 못했습니다."));
            }
        });
        return result.withEvidence(checked);
    }
}
