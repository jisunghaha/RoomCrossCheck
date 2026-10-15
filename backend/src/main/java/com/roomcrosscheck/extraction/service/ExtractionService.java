package com.roomcrosscheck.extraction.service;

import com.roomcrosscheck.extraction.client.ExtractionProvider;
import com.roomcrosscheck.extraction.dto.ExtractionResult;
import com.roomcrosscheck.listing.domain.Advertisement;
import com.roomcrosscheck.listing.dto.AdvertisementResponse;
import com.roomcrosscheck.listing.repository.AdvertisementRepository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ExtractionService {
    private final AdvertisementRepository repository;
    private final ExtractionProvider provider;
    private final ObjectMapper mapper;
    private final Path storageDirectory;
    private final Set<String> inProgress = ConcurrentHashMap.newKeySet();

    public ExtractionService(AdvertisementRepository repository, ExtractionProvider provider,
            ObjectMapper mapper, @Value("${ads.storage-dir:/tmp/roomcrosscheck-ads}") String storageDirectory) {
        this.repository = repository;
        this.provider = provider;
        this.mapper = mapper;
        this.storageDirectory = Path.of(storageDirectory).toAbsolutePath().normalize();
    }

    public AdvertisementResponse extract(String id) {
        if (!inProgress.add(id))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 추출 중입니다.");
        try {
            Advertisement ad = find(id);
            if (ad.status == Advertisement.Status.DRAFT)
                throw new ResponseStatusException(HttpStatus.CONFLICT, "먼저 원본을 확정해 주세요.");
            if (ad.status == Advertisement.Status.REVIEWED)
                throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 확인한 결과입니다.");
            if (ad.status == Advertisement.Status.EXTRACTION_READY) return AdvertisementResponse.from(ad);
            byte[] image = null;
            if (ad.imageStorageKey != null) {
                try { image = Files.readAllBytes(storageDirectory.resolve(ad.imageStorageKey)); }
                catch (IOException e) {
                    throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "저장된 광고 이미지를 읽지 못했습니다.");
                }
            }
            try {
                ExtractionResult result;
                try {
                    result = validate(provider.extract(ad.originalText, ad.addressHint,
                        ad.unitLabel, image, ad.imageContentType));
                } catch (ResponseStatusException e) {
                    if (e.getStatusCode() != HttpStatus.BAD_REQUEST) throw e;
                    throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "Gemini가 허용 범위 밖의 값을 반환했습니다.");
                }
                ad.extractionJson = json(result);
                ad.extractionError = null;
                ad.extractedAt = Instant.now();
                ad.status = Advertisement.Status.EXTRACTION_READY;
                return AdvertisementResponse.from(repository.save(ad));
            } catch (ResponseStatusException e) {
                if (e.getStatusCode() != HttpStatus.SERVICE_UNAVAILABLE) {
                    ad.status = Advertisement.Status.EXTRACTION_FAILED;
                    ad.extractionError = e.getReason();
                    repository.save(ad);
                }
                throw e;
            }
        } finally { inProgress.remove(id); }
    }

    public AdvertisementResponse review(String id, ExtractionResult input) {
        Advertisement ad = find(id);
        if (ad.status != Advertisement.Status.EXTRACTION_READY && ad.status != Advertisement.Status.REVIEWED)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "확인할 추출 결과가 없습니다.");
        ad.reviewedJson = json(validate(input));
        ad.reviewedAt = Instant.now();
        ad.status = Advertisement.Status.REVIEWED;
        return AdvertisementResponse.from(repository.save(ad));
    }

    private Advertisement find(String id) {
        return repository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "광고 원본을 찾지 못했습니다."));
    }

    private static ExtractionResult validate(ExtractionResult value) {
        if (value == null) throw invalid();
        length(value.rawText(), 10000);
        length(value.address(), 160);
        money(value.depositWon());
        money(value.monthlyRentWon());
        money(value.maintenanceFeeWon());
        if (value.areaSqm() != null && (value.areaSqm().signum() < 0
                || value.areaSqm().compareTo(new java.math.BigDecimal("100000")) > 0)) throw invalid();
        if (value.floor() != null && (value.floor() < -20 || value.floor() > 200)) throw invalid();
        return value;
    }
    private static void length(String value, int max) { if (value != null && value.length() > max) throw invalid(); }
    private static void money(Long value) { if (value != null && (value < 0 || value > 1_000_000_000_000L)) throw invalid(); }
    private static ResponseStatusException invalid() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "추출 결과의 값이 허용 범위를 벗어났습니다.");
    }
    private String json(ExtractionResult value) {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new IllegalStateException(e); }
    }
}
