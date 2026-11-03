package com.roomcrosscheck.listing.domain;

import com.roomcrosscheck.listing.dto.ListingRequest;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import com.roomcrosscheck.building.OfficialRecord;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "listings")
public class Listing {
    @Id public String id;
    @Column(nullable = false, length = 60) public String name;
    @Column(nullable = false, length = 160) public String address;
    public Long deposit;
    public Long rent;
    public Long fee;
    @Column(precision = 10, scale = 2) public BigDecimal area;
    @Column(length = 10) public String areaType;
    public Integer floor;
    @Column(length = 10000) public String original;
    @Column(nullable = false) public LocalDate createdAt;
    @Column(nullable = false) public String building;
    @Version public long version;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition="json") public OfficialRecord official;

    protected Listing() {}
    public Listing(ListingRequest r) {
        id = UUID.randomUUID().toString();
        building = UUID.randomUUID().toString();
        createdAt = LocalDate.now();
        update(r);
    }
    public void update(ListingRequest r) {
        official = null;
        name = r.name().trim(); address = r.address().trim();
        deposit = r.deposit(); rent = r.rent(); fee = r.fee();
        area = r.area(); areaType = r.areaType(); floor = r.floor(); original = r.original();
    }
}
