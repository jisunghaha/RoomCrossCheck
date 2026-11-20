package com.roomcrosscheck.preference.controller;

import com.roomcrosscheck.auth.service.CurrentMember;
import com.roomcrosscheck.listing.repository.ListingRepository;
import com.roomcrosscheck.preference.domain.MemberPreference;
import com.roomcrosscheck.preference.domain.MemberPreference.ChecklistItem;
import com.roomcrosscheck.preference.dto.PreferenceDto.*;
import com.roomcrosscheck.preference.repository.MemberPreferenceRepository;
import jakarta.validation.Valid;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * SFR-009 방문 체크리스트 구성과 SFR-011 비교 대상 선택을 회원별로 저장하는 API.
 * <p>다른 기기·브라우저에서도 같은 구성을 쓰도록 서버에 보관한다. 비교 선택은 본인 매물만 허용한다.
 */
@RestController
public class PreferenceController {
    private final MemberPreferenceRepository preferences;
    private final ListingRepository listings;
    private final CurrentMember member;

    public PreferenceController(MemberPreferenceRepository preferences, ListingRepository listings,
            CurrentMember member) {
        this.preferences = preferences;
        this.listings = listings;
        this.member = member;
    }

    @GetMapping("/api/visit-checklist")
    public ChecklistResponse checklist() {
        return checklistResponse(preferences.findById(member.requireId()).orElse(null));
    }

    /** 체크리스트를 저장한다. 항목 ID가 겹치면 400, items가 null이면 기본 항목으로 되돌린다. */
    @PutMapping("/api/visit-checklist")
    @Transactional
    public ChecklistResponse saveChecklist(@Valid @RequestBody ChecklistRequest request) {
        List<ChecklistItem> items = request.items() == null ? null : request.items().stream()
            .map(item -> new ChecklistItem(item.id(), item.label().strip(), item.tip() == null ? "" : item.tip().strip()))
            .toList();
        if (items != null && items.stream().map(ChecklistItem::id).distinct().count() != items.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "체크리스트 항목 ID가 중복되었습니다.");
        }
        MemberPreference preference = load();
        preference.checklist = items;
        return checklistResponse(preferences.saveAndFlush(preference));
    }

    /** 비교 선택 중 삭제됐거나 소유하지 않은 매물은 제외하고 반환한다. */
    @GetMapping("/api/comparison")
    public ComparisonResponse comparison() {
        Long ownerId = member.requireId();
        var saved = preferences.findById(ownerId).map(value -> value.comparisonListingIds).orElse(null);
        if (saved == null || saved.isEmpty()) return new ComparisonResponse(List.of());
        Set<String> owned = listings.findAllByIdInAndOwnerId(saved, ownerId).stream()
            .map(listing -> listing.id).collect(Collectors.toSet());
        return new ComparisonResponse(saved.stream().filter(owned::contains).toList());
    }

    /** 비교 대상 매물 선택을 저장한다. 중복 또는 본인 매물이 아닌 ID가 있으면 400이다. */
    @PutMapping("/api/comparison")
    @Transactional
    public ComparisonResponse saveComparison(@Valid @RequestBody ComparisonRequest request) {
        Long ownerId = member.requireId();
        List<String> ids = request.listingIds();
        if (new HashSet<>(ids).size() != ids.size()
                || (!ids.isEmpty() && listings.findAllByIdInAndOwnerId(ids, ownerId).size() != ids.size())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "비교할 매물을 다시 선택해 주세요.");
        }
        MemberPreference preference = load();
        preference.comparisonListingIds = List.copyOf(ids);
        preferences.saveAndFlush(preference);
        return new ComparisonResponse(List.copyOf(ids));
    }

    private MemberPreference load() {
        Long ownerId = member.requireId();
        return preferences.findById(ownerId).orElseGet(() -> new MemberPreference(ownerId));
    }

    private static ChecklistResponse checklistResponse(MemberPreference preference) {
        if (preference == null || preference.checklist == null) return new ChecklistResponse(null);
        return new ChecklistResponse(preference.checklist.stream()
            .map(item -> new ChecklistItemResponse(item.id(), item.label(), item.tip())).toList());
    }
}
