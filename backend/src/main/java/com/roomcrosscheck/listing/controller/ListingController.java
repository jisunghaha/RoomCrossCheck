package com.roomcrosscheck.listing.controller;

import com.roomcrosscheck.auth.service.CurrentMember;
import com.roomcrosscheck.listing.domain.Advertisement;
import com.roomcrosscheck.listing.domain.BuildingKey;
import com.roomcrosscheck.listing.domain.Listing;
import com.roomcrosscheck.listing.dto.ListingRequest;
import com.roomcrosscheck.listing.repository.AdvertisementRepository;
import com.roomcrosscheck.listing.repository.ListingRepository;
import com.roomcrosscheck.listing.service.ListingAccess;
import com.roomcrosscheck.visit.repository.VisitRecordRepository;

import jakarta.validation.Valid;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

/**
 * SFR-008 저장 매물의 등록·조회·수정·삭제 API.
 * <p>목록과 상세는 로그인 회원 본인의 매물만 반환한다. 소유자는 서버 세션으로 정하며 요청 본문으로 바꿀 수 없다.
 * 같은 회원이 같은 주소(동·호 포함)를 다시 저장하거나, 한 광고로 매물을 두 번 만들면 409로 거부한다.
 * DB 고유 제약도 같은 규칙을 강제하므로 동시 요청도 막힌다.
 */
@RestController
@RequestMapping("/api/listings")
public class ListingController {
    private final ListingRepository repository;
    private final ListingAccess access;
    private final CurrentMember member;
    private final VisitRecordRepository visits;
    private final AdvertisementRepository advertisements;

    public ListingController(ListingRepository repository, ListingAccess access, CurrentMember member,
            VisitRecordRepository visits, AdvertisementRepository advertisements) {
        this.repository = repository;
        this.access = access;
        this.member = member;
        this.visits = visits;
        this.advertisements = advertisements;
    }

    @GetMapping public List<Listing> all() {
        return repository.findAllByOwnerIdOrderByCreatedAtDesc(member.requireId());
    }
    @GetMapping("/{id}") public Listing one(@PathVariable String id) { return access.requireOwned(id); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public Listing create(@Valid @RequestBody ListingRequest request) {
        Long ownerId = member.requireId();
        String advertisementId = request.advertisementId() == null || request.advertisementId().isBlank()
            ? null : request.advertisementId();
        if (advertisementId != null) {
            Advertisement ad = advertisements.findById(advertisementId)
                .filter(found -> Objects.equals(found.ownerId, ownerId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "광고 원본을 찾지 못했습니다."));
            if (ad.status != Advertisement.Status.REVIEWED) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "추출 값을 확인·저장한 광고만 매물로 등록할 수 있습니다.");
            }
            if (repository.existsByAdvertisementId(advertisementId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "이 광고로 이미 등록한 매물이 있습니다.");
            }
        }
        if (repository.existsByOwnerIdAndAddressKey(ownerId, BuildingKey.ofFullAddress(request.address()))) {
            throw duplicateAddress();
        }
        return repository.saveAndFlush(new Listing(request, ownerId, advertisementId));
    }
    /** 수정 요청의 광고 ID는 무시한다. 주소를 바꿔 본인의 다른 매물과 겹치면 409다. */
    @PatchMapping("/{id}") public Listing update(@PathVariable String id, @Valid @RequestBody ListingRequest request) {
        Listing listing = access.requireOwned(id);
        if (repository.existsByOwnerIdAndAddressKeyAndIdNot(listing.ownerId, BuildingKey.ofFullAddress(request.address()), listing.id)) {
            throw duplicateAddress();
        }
        listing.update(request);
        return repository.saveAndFlush(listing);
    }
    private static ResponseStatusException duplicateAddress() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "같은 주소의 매물을 이미 저장했습니다. 기존 매물을 수정해 주세요.");
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
