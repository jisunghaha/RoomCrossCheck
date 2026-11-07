package com.roomcrosscheck.extraction.service;

import com.roomcrosscheck.extraction.client.ExtractionProvider;
import com.roomcrosscheck.extraction.client.ExtractionFailure;
import static com.roomcrosscheck.extraction.client.ExtractionFailure.Kind.*;
import com.roomcrosscheck.extraction.dto.ExtractionResult;
import com.roomcrosscheck.listing.domain.Advertisement;
import com.roomcrosscheck.listing.dto.AdvertisementResponse;
import com.roomcrosscheck.listing.repository.AdvertisementRepository;
import com.roomcrosscheck.listing.domain.AdvertisementReview;
import com.roomcrosscheck.listing.repository.AdvertisementReviewRepository;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Set;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ExtractionService {
    private final AdvertisementRepository repository;
    private final AdvertisementReviewRepository reviews;
    private final ExtractionProvider provider;
    private final ObjectMapper mapper;
    private final Path storageDirectory;
    private final Set<String> inProgress = ConcurrentHashMap.newKeySet();

    public ExtractionService(AdvertisementRepository repository, AdvertisementReviewRepository reviews, ExtractionProvider provider,
            ObjectMapper mapper, @Value("${ads.storage-dir:/tmp/roomcrosscheck-ads}") String storageDirectory) {
        this.repository = repository;
        this.reviews = reviews;
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
            try {
                byte[] image = null;
                if (ad.imageStorageKey != null) {
                    try { image = Files.readAllBytes(storageDirectory.resolve(ad.imageStorageKey)); }
                    catch (IOException e) { throw new ExtractionFailure(SOURCE_UNAVAILABLE); }
                }
                ExtractionResult result;
                try {
                    result = validate(provider.extract(ad.originalText, ad.addressHint,
                        ad.unitLabel, image, ad.imageContentType));
                } catch (ResponseStatusException e) {
                    if (e.getStatusCode() != HttpStatus.BAD_REQUEST) throw e;
                    throw new ExtractionFailure(INVALID_VALUES);
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

    @Transactional
    public AdvertisementResponse review(String id, ExtractionResult input, Long expectedVersion) {
        if (expectedVersion == null)
            throw new ResponseStatusException(HttpStatus.PRECONDITION_REQUIRED, "최신 광고를 불러온 뒤 다시 저장해 주세요.");
        if (expectedVersion < 0) throw invalid();
        Advertisement ad = repository.findForRegistration(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "광고 원본을 찾지 못했습니다."));
        if (ad.reviewVersion != expectedVersion)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "다른 화면에서 확인값을 변경했습니다. 최신값을 불러온 뒤 다시 확인해 주세요.");
        if (ad.status != Advertisement.Status.EXTRACTION_READY && ad.status != Advertisement.Status.REVIEWED)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "확인할 추출 결과가 없습니다.");
        // Evidence belongs to the immutable AI result, never to user-supplied corrections.
        String corrected = json(validate(input).withEvidence(null));
        // Preserve the last value from before history tracking without inventing older edits or timestamps.
        if (ad.reviewVersion == 0 && ad.reviewedJson != null)
            reviews.save(AdvertisementReview.snapshot(ad));
        ad.reviewedJson = corrected;
        ad.reviewedAt = Instant.now();
        ad.reviewVersion++;
        ad.status = Advertisement.Status.REVIEWED;
        repository.saveAndFlush(ad);
        reviews.saveAndFlush(AdvertisementReview.snapshot(ad));
        return AdvertisementResponse.from(ad);
    }

    public record ReviewSnapshot(long version, Instant reviewedAt, ExtractionResult reviewed) {}

    @Transactional(readOnly = true)
    public List<ReviewSnapshot> history(String id) {
        find(id);
        return reviews.findTop20ByAdvertisement_IdOrderByVersionDesc(id).stream().map(entry -> {
            try { return new ReviewSnapshot(entry.version, entry.reviewedAt,
                mapper.readValue(entry.reviewedJson, ExtractionResult.class).withEvidence(null)); }
            catch (JacksonException e) { throw new IllegalStateException("저장된 수정 이력을 읽을 수 없습니다.", e); }
        }).toList();
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
        if (!Set.of("전용", "공급", "미상").contains(value.areaType())) throw invalid();
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
        catch (JacksonException e) { throw new IllegalStateException(e); }
    }
}
