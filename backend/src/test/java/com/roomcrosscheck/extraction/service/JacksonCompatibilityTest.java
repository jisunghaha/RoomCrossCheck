package com.roomcrosscheck.extraction.service;

import com.roomcrosscheck.extraction.client.ExtractionProvider;
import com.roomcrosscheck.extraction.client.GeminiExtractionProvider;
import com.roomcrosscheck.extraction.dto.ExtractionResult;
import com.roomcrosscheck.listing.domain.Advertisement;
import com.roomcrosscheck.listing.dto.AdvertisementResponse;
import com.roomcrosscheck.listing.repository.AdvertisementRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class JacksonCompatibilityTest {
    @TempDir Path directory;

    @Test void bootMapperPreservesStoredExtractionAndReviewedValues() {
        var repository = mock(AdvertisementRepository.class);
        var ad = new Advertisement();
        ad.status = Advertisement.Status.EXTRACTION_READY;
        ad.createdAt = Instant.parse("2026-10-16T01:02:03Z");
        ad.extractionJson = """
            {"rawText":"월세 42만원","address":null,"depositWon":null,
             "monthlyRentWon":420000,"maintenanceFeeWon":0,"areaSqm":23.7500,"floor":-2}
            """;
        when(repository.findById(ad.id)).thenReturn(Optional.of(ad));
        when(repository.save(any(Advertisement.class))).thenAnswer(call -> call.getArgument(0));

        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
            .withUserConfiguration(ExtractionService.class, GeminiExtractionProvider.class)
            .withBean(AdvertisementRepository.class, () -> repository)
            .withPropertyValues("gemini.enabled=false", "ads.storage-dir=" + directory)
            .run(context -> {
                assertNull(context.getStartupFailure());
                var mapper = context.getBean(ObjectMapper.class);
                assertInstanceOf(JsonMapper.class, mapper);
                var unavailable = assertThrows(ResponseStatusException.class, () ->
                    context.getBean(ExtractionProvider.class).extract("광고", null, null, null, null));
                assertEquals(503, unavailable.getStatusCode().value());

                var corrected = new ExtractionResult("월세 42만원", null, null,
                    430000L, 0L, new BigDecimal("23.7500"), -2);
                var response = context.getBean(ExtractionService.class).review(ad.id, corrected);
                var json = mapper.readTree(mapper.writeValueAsString(response));
                assertTrue(json.path("extraction").has("depositWon"));
                assertTrue(json.path("extraction").path("depositWon").isNull());
                assertEquals(420000, json.path("extraction").path("monthlyRentWon").asLong());
                assertEquals(430000, json.path("reviewed").path("monthlyRentWon").asLong());
                assertEquals(0, json.path("reviewed").path("maintenanceFeeWon").asLong());
                assertEquals(-2, json.path("reviewed").path("floor").asInt());
                assertEquals(0, new BigDecimal("23.7500").compareTo(response.reviewed().areaSqm()));
                assertEquals("2026-10-16T01:02:03Z", json.path("createdAt").asString());
                var reloaded = mapper.readValue(mapper.writeValueAsString(response), AdvertisementResponse.class);
                assertEquals(response, reloaded);
            });
    }
}
