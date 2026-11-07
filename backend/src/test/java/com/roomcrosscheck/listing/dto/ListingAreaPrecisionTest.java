package com.roomcrosscheck.listing.dto;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class ListingAreaPrecisionTest {
    private ListingRequest request(BigDecimal area) {
        return new ListingRequest("정밀도 검증", "부산 가상 주소", null, null, 0L,
            area, "전용", -2, "원본", null);
    }

    @Test void acceptsRepresentableFourDecimalValuesAndTrailingZeroPadding() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertTrue(validator.validate(request(null)).isEmpty());
            for (String input : new String[]{"0", "19.212", "14.876", "23.7501", "19.21200", "99999.9999", "100000"}) {
                var area = new BigDecimal(input);
                var request = request(area);
                assertTrue(validator.validate(request).isEmpty(), input);
                assertEquals(0, area.compareTo(request.area()), input);
            }
        }
    }

    @Test void rejectsExcessPrecisionAndRangeWithoutSilentlyRounding() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            for (String input : new String[]{"19.21201", "14.8760325", "100000.0001", "-0.0001", "999999.9999", "1000000"}) {
                var area = new BigDecimal(input);
                var request = request(area);
                assertFalse(validator.validate(request).isEmpty(), input);
                assertEquals(0, area.compareTo(request.area()), input);
            }
        }
    }
}
