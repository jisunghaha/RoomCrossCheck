package com.roomcrosscheck.listing.domain;

import com.roomcrosscheck.listing.dto.ListingRequest;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import com.roomcrosscheck.building.OfficialRecord;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 회원이 저장한 매물을 listings 테이블에 매핑한다.
 * <p>매물은 등록한 회원만 조회·수정할 수 있다. 회원 연결 전에 만든 기존 행은 소유자가 없어
 * 어느 회원에게도 표시되지 않는다. {@code building}은 같은 건물의 거주 후기를 묶는 서버 생성 키다.
 */
@Entity
@Table(name = "listings", indexes = @Index(name = "idx_listings_owner", columnList = "owner_id"))
// 외래키·고유 제약(소유자, 광고 1:1, 같은 회원의 같은 주소)은 common.SchemaConstraints가 DB에 적용한다.
public class Listing {
    @Id public String id;
    /** 등록한 회원의 users.id. 응답으로 노출하지 않으며 요청 본문으로 바꿀 수 없다. */
    @JsonIgnore
    @Column(name = "owner_id", updatable = false, columnDefinition = "bigint unsigned")
    public Long ownerId;
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
    /**
     * 매물을 만든 광고 원본 ID. 직접 입력한 매물은 null이다.
     * 한 광고로는 매물을 하나만 만들 수 있고(고유 제약), 광고가 지워지면 null이 된다.
     */
    @Column(name = "advertisement_id", length = 36, updatable = false)
    public String advertisementId;

    /** 같은 회원이 같은 주소(동·호 포함)를 두 번 저장하지 못하게 하는 정규화 주소. */
    @JsonIgnore
    @Column(name = "address_key")
    public String addressKey;

    /** 건축물대장 건물 ID 또는 정규화한 기본 주소로 만든 건물 키. 후기 묶음 기준이다. */
    @Column(nullable = false) public String building;
    @Version public long version;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition="json") public OfficialRecord official;

    protected Listing() {}

    /**
     * 로그인 회원의 새 매물을 만든다.
     *
     * @param r 검증된 매물 입력값
     * @param ownerId 서버 세션에서 확인한 회원 번호
     * @param advertisementId 소유권과 확인 상태를 검사한 광고 ID, 직접 입력이면 null
     */
    public Listing(ListingRequest r, Long ownerId, String advertisementId) {
        id = UUID.randomUUID().toString();
        this.ownerId = ownerId;
        this.advertisementId = advertisementId;
        createdAt = LocalDate.now();
        update(r);
    }

    /** 입력값을 반영하고, 주소가 바뀌었을 수 있으므로 공식 기록을 해제한 뒤 건물 키를 다시 계산한다. */
    public void update(ListingRequest r) {
        official = null;
        name = r.name().trim(); address = r.address().trim();
        deposit = r.deposit(); rent = r.rent(); fee = r.fee();
        area = r.area(); areaType = r.areaType(); floor = r.floor(); original = r.original();
        addressKey = BuildingKey.ofFullAddress(address);
        refreshBuilding();
    }

    /** 공식 건물이 저장돼 있으면 그 건물 ID를, 없으면 등록 주소로 건물 키를 정한다. */
    public void refreshBuilding() {
        building = official != null && official.buildingId() != null && !official.buildingId().isBlank()
            ? BuildingKey.ofLedger(official.buildingId()) : BuildingKey.ofAddress(address);
    }
}
