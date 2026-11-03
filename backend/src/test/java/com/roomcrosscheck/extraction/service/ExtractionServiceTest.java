package com.roomcrosscheck.extraction.service;

import com.roomcrosscheck.extraction.client.ExtractionProvider;
import com.roomcrosscheck.extraction.dto.ExtractionResult;
import com.roomcrosscheck.listing.domain.Advertisement;
import com.roomcrosscheck.listing.repository.AdvertisementRepository;

import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ExtractionServiceTest {
    @TempDir Path directory;

    @Test void extractsStoredImageThenPersistsUserCorrection() throws Exception {
        var repo = mock(AdvertisementRepository.class);
        var provider = mock(ExtractionProvider.class);
        var ad = new Advertisement();
        ad.status = Advertisement.Status.EXTRACTION_PENDING;
        ad.originalText = "보증금 500만원";
        ad.imageStorageKey = "stored.png";
        ad.imageContentType = "image/png";
        Files.write(directory.resolve("stored.png"), new byte[]{1, 2, 3});
        when(repo.findById(ad.id)).thenReturn(Optional.of(ad));
        when(repo.save(any(Advertisement.class))).thenAnswer(call -> call.getArgument(0));
        when(provider.extract(eq(ad.originalText), isNull(), isNull(),
                argThat(bytes -> java.util.Arrays.equals(bytes, new byte[]{1, 2, 3})), eq("image/png")))
            .thenReturn(new ExtractionResult("보증금 500만원", null, 5_000_000L, null, null, null, null, null));
        var service = new ExtractionService(repo, provider, JsonMapper.builder().build(), directory.toString());

        var ready = service.extract(ad.id);
        assertEquals(Advertisement.Status.EXTRACTION_READY, ready.status());
        assertEquals(5_000_000L, ready.extraction().depositWon());
        assertEquals(ready.id(), service.extract(ad.id).id());
        verify(provider, times(1)).extract(any(), any(), any(), any(), any());

        var corrected = new ExtractionResult("보증금 500만원", "서울시", 5_000_000L, 420_000L, null, null, null, null);
        var reviewed = service.review(ad.id, corrected);
        assertEquals(Advertisement.Status.REVIEWED, reviewed.status());
        assertEquals(420_000L, reviewed.reviewed().monthlyRentWon());
        assertEquals(5_000_000L, reviewed.extraction().depositWon());
        assertThrows(ResponseStatusException.class, () -> service.extract(ad.id));
    }

    @Test void failedExtractionCanBeRetriedWithoutLosingOriginal() {
        var repo = mock(AdvertisementRepository.class);
        var provider = mock(ExtractionProvider.class);
        var ad = new Advertisement();
        ad.status = Advertisement.Status.EXTRACTION_PENDING;
        ad.originalText = "월세 42만원";
        when(repo.findById(ad.id)).thenReturn(Optional.of(ad));
        when(repo.save(any(Advertisement.class))).thenAnswer(call -> call.getArgument(0));
        when(provider.extract(any(), any(), any(), any(), any()))
            .thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS, "한도 초과"))
            .thenReturn(new ExtractionResult("월세 42만원", null, null, 420_000L, null, null, null, null));
        var service = new ExtractionService(repo, provider, JsonMapper.builder().build(), directory.toString());
        assertThrows(ResponseStatusException.class, () -> service.extract(ad.id));
        assertEquals(Advertisement.Status.EXTRACTION_FAILED, ad.status);
        assertEquals("월세 42만원", ad.originalText);
        assertEquals(Advertisement.Status.EXTRACTION_READY, service.extract(ad.id).status());
    }

    @Test void preservesExtractedAreaTypeSeparatelyFromUserCorrection() {
        var repo = mock(AdvertisementRepository.class);
        var provider = mock(ExtractionProvider.class);
        var ad = new Advertisement();
        ad.status = Advertisement.Status.EXTRACTION_PENDING;
        ad.originalText = "전용면적 23㎡";
        when(repo.findById(ad.id)).thenReturn(Optional.of(ad));
        when(repo.save(any(Advertisement.class))).thenAnswer(call -> call.getArgument(0));
        when(provider.extract(any(), any(), any(), any(), any()))
            .thenReturn(new ExtractionResult(ad.originalText, null, null, null, null,
                new java.math.BigDecimal("23"), null, "전용"));
        var service = new ExtractionService(repo, provider, JsonMapper.builder().build(), directory.toString());
        assertEquals("전용", service.extract(ad.id).extraction().areaType());
        var corrected = new ExtractionResult(ad.originalText, null, null, null, null,
            new java.math.BigDecimal("23"), null, "공급");
        var response = service.review(ad.id, corrected);
        assertEquals("전용", response.extraction().areaType());
        assertEquals("공급", response.reviewed().areaType());
    }

    @Test void invalidAreaTypeFromProviderFailsWithoutSavingAnExtraction() {
        var repo = mock(AdvertisementRepository.class);
        var provider = mock(ExtractionProvider.class);
        var ad = new Advertisement();
        ad.status = Advertisement.Status.EXTRACTION_PENDING;
        ad.originalText = "면적 23㎡";
        when(repo.findById(ad.id)).thenReturn(Optional.of(ad));
        when(repo.save(any(Advertisement.class))).thenAnswer(call -> call.getArgument(0));
        when(provider.extract(any(), any(), any(), any(), any()))
            .thenReturn(new ExtractionResult(ad.originalText, null, null, null, null,
                new java.math.BigDecimal("23"), null, "주거용"));
        var service = new ExtractionService(repo, provider, JsonMapper.builder().build(), directory.toString());
        assertEquals(502, assertThrows(ResponseStatusException.class,
            () -> service.extract(ad.id)).getStatusCode().value());
        assertNull(ad.extractionJson);
        assertEquals("면적 23㎡", ad.originalText);
        assertEquals(Advertisement.Status.EXTRACTION_FAILED, ad.status);
    }

    @Test void invalidUserAreaTypeDoesNotOverwriteSavedReview() {
        var repo = mock(AdvertisementRepository.class);
        var ad = new Advertisement();
        ad.status = Advertisement.Status.REVIEWED;
        ad.reviewedJson = "{\"areaType\":\"전용\"}";
        when(repo.findById(ad.id)).thenReturn(Optional.of(ad));
        var service = new ExtractionService(repo, mock(ExtractionProvider.class),
            JsonMapper.builder().build(), directory.toString());
        var invalid = new ExtractionResult("원본", null, null, null, null, null, null, "주거용");
        assertEquals(400, assertThrows(ResponseStatusException.class,
            () -> service.review(ad.id, invalid)).getStatusCode().value());
        assertEquals("{\"areaType\":\"전용\"}", ad.reviewedJson);
        verify(repo, never()).save(any());
    }
}
