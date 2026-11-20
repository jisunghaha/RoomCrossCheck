package com.roomcrosscheck.listing.controller;

import com.roomcrosscheck.auth.service.CurrentMember;
import com.roomcrosscheck.extraction.dto.ExtractionResult;
import com.roomcrosscheck.extraction.service.ExtractionService;
import com.roomcrosscheck.listing.dto.AdvertisementResponse;
import com.roomcrosscheck.listing.service.AdvertisementService;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

/**
 * SFR-002~005 광고 원본 저장·확정·AI 추출·확인 값 저장 API.
 * <p>모든 요청은 로그인 회원 본인의 광고에만 허용한다. Gemini 호출 전에 소유권을 확인해
 * 다른 회원이 외부 API 이용 한도를 소모하지 못하게 한다.
 */
@RestController
@RequestMapping("/api/advertisements")
public class AdvertisementController {
    private final AdvertisementService service;
    private final ExtractionService extraction;
    private final CurrentMember member;
    public AdvertisementController(AdvertisementService service, ExtractionService extraction, CurrentMember member) {
        this.service = service;
        this.extraction = extraction;
        this.member = member;
    }

    @GetMapping public List<AdvertisementResponse> all() { return service.all(member.requireId()); }
    @GetMapping("/{id}") public AdvertisementResponse one(@PathVariable String id) { return service.one(owned(id)); }

    @PostMapping(consumes = "multipart/form-data") @ResponseStatus(HttpStatus.CREATED)
    public AdvertisementResponse create(@RequestHeader("Idempotency-Key") String key,
            @RequestParam(required = false) String text,
            @RequestParam(required = false) String addressHint,
            @RequestParam(required = false) String unitLabel,
            @RequestParam(required = false) String sourceUrl,
            @RequestParam(required = false) MultipartFile image) {
        return service.create(member.requireId(), key, text, addressHint, unitLabel, sourceUrl, image);
    }
    @PostMapping("/{id}/confirm")
    public AdvertisementResponse confirm(@PathVariable String id) { return service.confirm(owned(id)); }

    @PostMapping("/{id}/extract")
    public AdvertisementResponse extract(@PathVariable String id) { return extraction.extract(owned(id)); }

    @PutMapping("/{id}/extraction")
    public AdvertisementResponse review(@PathVariable String id, @RequestBody ExtractionResult result) {
        return extraction.review(owned(id), result);
    }

    private String owned(String id) {
        service.requireOwned(id, member.requireId());
        return id;
    }
}
