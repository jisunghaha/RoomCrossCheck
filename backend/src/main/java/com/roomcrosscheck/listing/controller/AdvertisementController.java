package com.roomcrosscheck.listing.controller;

import com.roomcrosscheck.extraction.dto.ExtractionResult;
import com.roomcrosscheck.extraction.service.ExtractionService;
import com.roomcrosscheck.listing.dto.AdvertisementResponse;
import com.roomcrosscheck.listing.service.AdvertisementService;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

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
    public AdvertisementResponse review(@PathVariable String id, @RequestBody ExtractionResult result) {
        return extraction.review(id, result);
    }
}
