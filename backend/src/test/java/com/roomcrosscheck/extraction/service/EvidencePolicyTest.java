package com.roomcrosscheck.extraction.service;

import com.roomcrosscheck.extraction.dto.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class EvidencePolicyTest {
    private ExtractionResult result(Map<String, FieldEvidence> evidence) {
        return new ExtractionResult("월세 35만원 관리비 0원", null, null, 350000L, 0L,
            null, null, "미상", null, evidence);
    }
    @Test void verifiesTextAndImageReferencesWithoutInventingEvidenceForLegacy() {
        assertNull(EvidencePolicy.check(result(null), "월세 35만원", false).evidence());
        var source = Map.of("monthlyRentWon", new FieldEvidence("월세 35만원", "INPUT_TEXT", "PRESENT", null),
            "maintenanceFeeWon", new FieldEvidence("관리비 0원", "IMAGE_READ_TEXT", "PRESENT", null),
            "depositWon", new FieldEvidence(null, null, "NOT_STATED", null));
        var checked = EvidencePolicy.check(result(source), "월세 35만원", true);
        assertEquals("PRESENT", checked.evidence().get("monthlyRentWon").status());
        assertEquals("PRESENT", checked.evidence().get("maintenanceFeeWon").status());
        assertEquals("NOT_STATED", checked.evidence().get("depositWon").status());
        assertEquals(0L, checked.maintenanceFeeWon());
        assertEquals("UNCERTAIN", EvidencePolicy.check(result(source), "월세 35만원", false)
            .evidence().get("maintenanceFeeWon").status());
    }
    @Test void rejectsFalseQuotesAndContradictoryStatesWithoutChangingAiValues() {
        var source = Map.of("monthlyRentWon", new FieldEvidence("월세 99만원", "INPUT_TEXT", "PRESENT", null),
            "maintenanceFeeWon", new FieldEvidence(null, null, "NOT_STATED", null),
            "areaType", new FieldEvidence("월세", "INPUT_TEXT", "PRESENT", null));
        var checked = EvidencePolicy.check(result(source), "월세 35만원", false);
        checked.evidence().values().forEach(item -> { assertEquals("UNCERTAIN", item.status()); assertNull(item.quote()); });
        assertEquals(350000L, checked.monthlyRentWon());
        assertThrows(UnsupportedOperationException.class, () -> checked.evidence().clear());
    }
    @Test void storesAndReadsEvidenceAndKeepsOldJsonCompatible() {
        var mapper = JsonMapper.builder().build();
        var value = result(Map.of("monthlyRentWon", new FieldEvidence("월세 35만원", "INPUT_TEXT", "PRESENT", null)));
        assertEquals(value, mapper.readValue(mapper.writeValueAsString(value), ExtractionResult.class));
        var old = mapper.readValue("{\"rawText\":\"광고\",\"monthlyRentWon\":350000}", ExtractionResult.class);
        assertNull(old.evidence()); assertEquals("미상", old.areaType());
        assertThrows(IllegalArgumentException.class, () -> result(Map.of("unknown", new FieldEvidence(null,null,"NOT_STATED",null))));
        assertThrows(IllegalArgumentException.class, () -> new FieldEvidence("x".repeat(501),"INPUT_TEXT","PRESENT",null));
        assertThrows(IllegalArgumentException.class, () -> new FieldEvidence(null,null,"CONFIDENT",null));
    }
}
