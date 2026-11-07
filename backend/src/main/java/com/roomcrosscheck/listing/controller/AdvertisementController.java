package com.roomcrosscheck.listing.controller;

import com.roomcrosscheck.extraction.dto.ExtractionResult;
import com.roomcrosscheck.extraction.client.ExtractionFailure;
import com.roomcrosscheck.extraction.service.ExtractionService;
import com.roomcrosscheck.listing.dto.AdvertisementResponse;
import com.roomcrosscheck.listing.service.AdvertisementService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/advertisements")
public class AdvertisementController {
    private final AdvertisementService service;
    private final ExtractionService extraction;
    public AdvertisementController(AdvertisementService service, ExtractionService extraction) {
        this.service = service;
        this.extraction = extraction;
    }

    @GetMapping public List<AdvertisementResponse> all() { return service.all(); }
    @GetMapping("/{id}") public AdvertisementResponse one(@PathVariable String id) { return service.one(id); }

    @PostMapping(consumes = "multipart/form-data") @ResponseStatus(HttpStatus.CREATED)
    public AdvertisementResponse create(@RequestHeader("Idempotency-Key") String key,
            @RequestParam(required = false) String text,
            @RequestParam(required = false) String addressHint,
            @RequestParam(required = false) String unitLabel,
            @RequestParam(required = false) String sourceUrl,
            @RequestParam(required = false) MultipartFile image) {
        return service.create(key, text, addressHint, unitLabel, sourceUrl, image);
    }
    @PostMapping("/{id}/confirm")
    public AdvertisementResponse confirm(@PathVariable String id) { return service.confirm(id); }

    @PostMapping("/{id}/extract")
    public AdvertisementResponse extract(@PathVariable String id) { return extraction.extract(id); }

    @PutMapping("/{id}/extraction")
    public AdvertisementResponse review(@PathVariable String id, @RequestBody ExtractionResult result,
            @RequestHeader(value = "X-Review-Version", required = false) Long version) {
        return extraction.review(id, result, version);
    }

    @GetMapping("/{id}/reviews")
    public List<ExtractionService.ReviewSnapshot> history(@PathVariable String id) {
        return extraction.history(id);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> error(ResponseStatusException error) {
        if (error instanceof ExtractionFailure failure) {
            return ResponseEntity.status(failure.getStatusCode())
                .body(Map.of("code", failure.code(), "message", failure.getReason()));
        }
        String message = error.getStatusCode().is4xxClientError() && error.getReason() != null
            ? error.getReason() : "요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.";
        return ResponseEntity.status(error.getStatusCode()).body(Map.of("message", message));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> conflict(DataIntegrityViolationException error) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", "저장 상태가 변경됐습니다. 최신값을 불러온 뒤 다시 확인해 주세요."));
    }

    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ResponseEntity<Map<String, String>> locked(PessimisticLockingFailureException error) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("message", "다른 저장 요청을 처리 중입니다. 잠시 후 다시 시도해 주세요."));
    }
}
