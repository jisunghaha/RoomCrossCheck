package com.roomcrosscheck.visit.controller;

import com.roomcrosscheck.auth.service.CurrentMember;
import com.roomcrosscheck.listing.repository.ListingRepository;
import com.roomcrosscheck.listing.service.ListingAccess;
import com.roomcrosscheck.visit.domain.VisitRecord;
import com.roomcrosscheck.visit.dto.VisitDto.VisitRequest;
import com.roomcrosscheck.visit.dto.VisitDto.VisitResponse;
import com.roomcrosscheck.visit.repository.VisitRecordRepository;
import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * SFR-010 방문 결과 기록·조회 API.
 * <p>회원 본인 매물의 기록만 저장·조회한다. 저장은 매물당 한 건을 덮어쓰는 방식이다.
 */
@RestController
public class VisitController {
    private final VisitRecordRepository visits;
    private final ListingRepository listings;
    private final ListingAccess access;
    private final CurrentMember member;

    public VisitController(VisitRecordRepository visits, ListingRepository listings, ListingAccess access,
            CurrentMember member) {
        this.visits = visits;
        this.listings = listings;
        this.access = access;
        this.member = member;
    }

    /**
     * 회원의 방문 기록 전체를 매물 ID별로 반환한다.
     *
     * @return 매물 ID → 방문 기록. 현재 소유한 매물의 기록만 포함한다.
     */
    @GetMapping("/api/visits")
    public Map<String, VisitResponse> all() {
        Long ownerId = member.requireId();
        var owned = listings.findAllByOwnerIdOrderByCreatedAtDesc(ownerId).stream()
            .map(listing -> listing.id).collect(Collectors.toSet());
        var result = new LinkedHashMap<String, VisitResponse>();
        for (VisitRecord record : visits.findAllByOwnerId(ownerId)) {
            if (owned.contains(record.listingId)) result.put(record.listingId, VisitResponse.from(record));
        }
        return result;
    }

    /** 매물의 방문 기록을 만들거나 덮어쓴다. */
    @PutMapping("/api/listings/{id}/visit")
    @Transactional
    public VisitResponse save(@PathVariable String id, @Valid @RequestBody VisitRequest request) {
        var listing = access.requireOwned(id);
        VisitRecord record = visits.findById(listing.id).orElseGet(() -> new VisitRecord(listing.id, listing.ownerId));
        record.checks = new LinkedHashMap<>(request.checks());
        record.note = request.note() == null ? null : request.note().strip();
        record.visitedOn = request.visitedOn();
        return VisitResponse.from(visits.saveAndFlush(record));
    }
}
