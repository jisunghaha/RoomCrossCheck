package com.roomcrosscheck.common.validation;

import com.roomcrosscheck.extraction.dto.ExtractionResult;
import com.roomcrosscheck.listing.domain.Listing;
import com.roomcrosscheck.listing.dto.ListingRequest;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import java.util.List;
import java.util.Collections;
import static org.junit.jupiter.api.Assertions.*;

class OptionsContractTest {
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final String base = "\"name\":\"옵션 검증\",\"address\":\"부산 가상 주소\"";

    @Test void missingNullAndEmptyExtractionOptionsRemainDistinct() throws Exception {
        assertNull(mapper.readValue("{}", ExtractionResult.class).options());
        assertNull(mapper.readValue("{\"options\":null}", ExtractionResult.class).options());
        assertEquals(List.of(), mapper.readValue("{\"options\":[]}", ExtractionResult.class).options());
    }
    @Test void explicitOptionsAreTrimmedDeduplicatedAndNotExpanded() throws Exception {
        var result = mapper.readValue("{\"options\":[\" 풀옵션 \",\"냉장고\",\"냉장고\"]}", ExtractionResult.class);
        assertEquals(List.of("풀옵션", "냉장고"), result.options());
        assertThrows(UnsupportedOperationException.class, () -> result.options().add("추측한 가전"));
    }
    @Test void extractionAndListingRejectWrongJsonTypesAndInvalidItems() {
        for (String invalid : List.of("42", "{}", "\"냉장고\"", "[42]", "[null]", "[\"\"]", "[\" \"]", "[\"줄\\n바꿈\"]")) {
            assertThrows(Exception.class, () -> mapper.readValue("{\"options\":" + invalid + "}", ExtractionResult.class), invalid);
            assertThrows(Exception.class, () -> mapper.readValue("{" + base + ",\"options\":" + invalid + "}", ListingRequest.class), invalid);
        }
    }
    @Test void oversizedInputsAreRejectedBeforeSaving() {
        assertThrows(IllegalArgumentException.class, () -> OptionValues.normalize(Collections.nCopies(31, "냉장고")));
        assertThrows(IllegalArgumentException.class, () -> OptionValues.normalize(List.of("가".repeat(101))));
        assertEquals(30, OptionValues.normalize(java.util.stream.IntStream.range(0, 30).mapToObj(i -> "옵션" + i).toList()).size());
    }
    @Test void listingTracksPresenceWithoutAcceptingClientSuppliedPresenceFlag() throws Exception {
        var missing = mapper.readValue("{" + base + ",\"optionsProvided\":true}", ListingRequest.class);
        assertFalse(missing.optionsProvided());
        var explicitNull = mapper.readValue("{" + base + ",\"options\":null,\"optionsProvided\":false}", ListingRequest.class);
        assertTrue(explicitNull.optionsProvided());
        assertNull(explicitNull.options());
        var empty = mapper.readValue("{" + base + ",\"options\":[]}", ListingRequest.class);
        assertTrue(empty.optionsProvided());
        assertEquals(List.of(), empty.options());
    }
    @Test void legacyUpdatePreservesOptionsWhileExplicitNullAndEmptyUpdateThem() throws Exception {
        var provided = mapper.readValue("{" + base + ",\"options\":[\"세탁기\"]}", ListingRequest.class);
        var listing = new Listing(provided);
        listing.update(mapper.readValue("{" + base + "}", ListingRequest.class));
        assertEquals(List.of("세탁기"), listing.options);
        listing.update(mapper.readValue("{" + base + ",\"options\":null}", ListingRequest.class));
        assertNull(listing.options);
        listing.update(mapper.readValue("{" + base + ",\"options\":[]}", ListingRequest.class));
        assertEquals(List.of(), listing.options);
    }
}
