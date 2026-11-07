package com.roomcrosscheck.listing.repository;

import com.roomcrosscheck.listing.domain.Listing;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ListingRepository extends JpaRepository<Listing, String> {}
