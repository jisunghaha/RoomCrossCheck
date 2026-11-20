package com.roomcrosscheck.listing.service;

import com.roomcrosscheck.auth.service.CurrentMember;
import com.roomcrosscheck.listing.domain.Listing;
import com.roomcrosscheck.listing.repository.ListingRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * 매물 ID로 접근하는 모든 기능(매물·건축물대장·방문 기록)의 소유권을 확인한다.
 * <p>다른 회원의 매물과 소유자가 없는 기존 매물은 존재 여부를 드러내지 않도록 404로 응답한다.
 */
@Component
public class ListingAccess {
    private final ListingRepository listings;
    private final CurrentMember member;

    public ListingAccess(ListingRepository listings, CurrentMember member) {
        this.listings = listings;
        this.member = member;
    }

    /**
     * 현재 회원이 소유한 매물을 반환한다.
     *
     * @param listingId 매물 ID
     * @return 소유한 매물
     * @throws ResponseStatusException 비로그인이면 401, 없거나 다른 회원의 매물이면 404
     */
    public Listing requireOwned(String listingId) {
        Long ownerId = member.requireId();
        return listings.findByIdAndOwnerId(listingId, ownerId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "매물을 찾지 못했습니다."));
    }
}
