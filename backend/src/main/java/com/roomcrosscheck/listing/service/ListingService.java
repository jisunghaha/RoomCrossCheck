package com.roomcrosscheck.listing.service;

import com.roomcrosscheck.listing.domain.Advertisement;
import com.roomcrosscheck.listing.domain.Listing;
import com.roomcrosscheck.listing.dto.ListingRequest;
import com.roomcrosscheck.listing.repository.AdvertisementRepository;
import com.roomcrosscheck.listing.repository.ListingRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ListingService {
    private final ListingRepository listings;
    private final AdvertisementRepository advertisements;

    public ListingService(ListingRepository listings, AdvertisementRepository advertisements) {
        this.listings = listings;
        this.advertisements = advertisements;
    }

    @Transactional
    public Listing create(ListingRequest request) {
        if (request.advertisementId() != null) {
            Advertisement ad = advertisements.findForRegistration(request.advertisementId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "광고 원본을 찾을 수 없습니다."));
            if (ad.status != Advertisement.Status.REVIEWED || ad.reviewedJson == null || ad.reviewedJson.isBlank()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "광고 추출값을 확인하고 저장한 뒤 등록해 주세요.");
            }
            if (listings.existsByAdvertisementId(ad.id)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 매물로 등록된 광고입니다. 목록에서 확인해 주세요.");
            }
        }
        return listings.saveAndFlush(new Listing(request));
    }

    @Transactional
    public Listing update(String id, ListingRequest request) {
        Listing listing = listings.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (request.advertisementId() != null && !request.advertisementId().equals(listing.advertisementId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "등록된 매물의 광고 원본 연결은 변경할 수 없습니다.");
        }
        listing.update(request);
        return listings.save(listing);
    }
}
