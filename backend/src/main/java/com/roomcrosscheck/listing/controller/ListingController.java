package com.roomcrosscheck.listing.controller;

import com.roomcrosscheck.auth.service.CurrentMember;
import com.roomcrosscheck.listing.domain.Listing;
import com.roomcrosscheck.listing.dto.ListingRequest;
import com.roomcrosscheck.listing.repository.ListingRepository;
import com.roomcrosscheck.listing.service.ListingAccess;
import com.roomcrosscheck.visit.repository.VisitRecordRepository;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/**
 * SFR-008 저장 매물의 등록·조회·수정·삭제 API.
 * <p>목록과 상세는 로그인 회원 본인의 매물만 반환한다. 소유자는 서버 세션으로 정하며 요청 본문으로 바꿀 수 없다.
 */
@RestController
@RequestMapping("/api/listings")
public class ListingController {
    private final ListingRepository repository;
    private final ListingAccess access;
    private final CurrentMember member;
    private final VisitRecordRepository visits;

    public ListingController(ListingRepository repository, ListingAccess access, CurrentMember member,
            VisitRecordRepository visits) {
        this.repository = repository;
        this.access = access;
        this.member = member;
        this.visits = visits;
    }

    @GetMapping public List<Listing> all() {
        return repository.findAllByOwnerIdOrderByCreatedAtDesc(member.requireId());
    }
    @GetMapping("/{id}") public Listing one(@PathVariable String id) { return access.requireOwned(id); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public Listing create(@Valid @RequestBody ListingRequest request) {
        return repository.save(new Listing(request, member.requireId()));
    }
    @PatchMapping("/{id}") public Listing update(@PathVariable String id, @Valid @RequestBody ListingRequest request) {
        Listing listing = access.requireOwned(id);
        listing.update(request);
        return repository.save(listing);
    }
    /** 매물과 그 매물의 개인 방문 기록을 함께 삭제한다. 건물 단위 공개 후기는 유지한다. */
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void delete(@PathVariable String id) {
        Listing listing = access.requireOwned(id);
        visits.deleteById(listing.id);
        repository.delete(listing);
    }
}
