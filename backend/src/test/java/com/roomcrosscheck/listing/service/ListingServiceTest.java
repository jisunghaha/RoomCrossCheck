package com.roomcrosscheck.listing.service;

import com.roomcrosscheck.listing.domain.Advertisement;
import com.roomcrosscheck.listing.domain.Listing;
import com.roomcrosscheck.listing.dto.ListingRequest;
import com.roomcrosscheck.listing.repository.AdvertisementRepository;
import com.roomcrosscheck.listing.repository.ListingRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class ListingServiceTest {
    private final ListingRepository listings = mock(ListingRepository.class);
    private final AdvertisementRepository ads = mock(AdvertisementRepository.class);
    private final ListingService service = new ListingService(listings, ads);

    private ListingRequest request(String adId) {
        return new ListingRequest("  확인 매물  ", "부산 가상 주소", null, 440000L, 0L,
            new BigDecimal("23.75"), "미상", -2, "원본 광고", adId);
    }
    private Advertisement reviewedAd() {
        Advertisement ad = new Advertisement();
        ad.status = Advertisement.Status.REVIEWED;
        ad.reviewedJson = "{\"monthlyRentWon\":430000}";
        ad.extractionJson = "{\"monthlyRentWon\":420000}";
        when(ads.findForRegistration(ad.id)).thenReturn(Optional.of(ad));
        return ad;
    }
    @Test void manualRegistrationNeedsNoAdvertisement() {
        when(listings.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        Listing saved = service.create(request(null));
        assertNull(saved.advertisementId);
        assertEquals("확인 매물", saved.name);
        verifyNoInteractions(ads);
    }
    @Test void linksReviewedSourceWithoutOverwritingExtractionOrReview() {
        Advertisement ad = reviewedAd();
        when(listings.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        Listing saved = service.create(request(ad.id));
        assertEquals(ad.id, saved.advertisementId);
        assertEquals(440000L, saved.rent);
        assertNull(saved.deposit);
        assertEquals(0L, saved.fee);
        assertEquals(-2, saved.floor);
        assertEquals("{\"monthlyRentWon\":430000}", ad.reviewedJson);
        assertEquals("{\"monthlyRentWon\":420000}", ad.extractionJson);
    }
    @Test void unknownSourceIsNotSaved() {
        String id = "missing";
        when(ads.findForRegistration(id)).thenReturn(Optional.empty());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
            () -> service.create(request(id))).getStatusCode());
        verifyNoInteractions(listings);
    }
    @Test void everyUnreviewedStateIsRejected() {
        for (Advertisement.Status status : Advertisement.Status.values()) {
            if (status == Advertisement.Status.REVIEWED) continue;
            Advertisement ad = reviewedAd();
            ad.status = status;
            assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> service.create(request(ad.id))).getStatusCode());
        }
        verify(listings, never()).saveAndFlush(any());
    }
    @Test void reviewedStateWithoutSavedReviewIsRejected() {
        Advertisement ad = reviewedAd();
        ad.reviewedJson = null;
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
            () -> service.create(request(ad.id))).getStatusCode());
        verify(listings, never()).saveAndFlush(any());
    }
    @Test void duplicateRegistrationIsRejected() {
        Advertisement ad = reviewedAd();
        when(listings.existsByAdvertisementId(ad.id)).thenReturn(true);
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
            () -> service.create(request(ad.id))).getStatusCode());
        verify(listings, never()).saveAndFlush(any());
    }
    @Test void updatingValuesPreservesSourceWhenOldClientOmitsIt() {
        Advertisement ad = reviewedAd();
        Listing listing = new Listing(request(ad.id));
        when(listings.findById(listing.id)).thenReturn(Optional.of(listing));
        when(listings.save(listing)).thenReturn(listing);
        assertEquals(ad.id, service.update(listing.id, request(null)).advertisementId);
    }
    @Test void sourceCannotBeReplacedOrAddedThroughUpdate() {
        Listing listing = new Listing(request(null));
        when(listings.findById(listing.id)).thenReturn(Optional.of(listing));
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
            () -> service.update(listing.id, request("another-ad"))).getStatusCode());
        verify(listings, never()).save(any());
    }
}
