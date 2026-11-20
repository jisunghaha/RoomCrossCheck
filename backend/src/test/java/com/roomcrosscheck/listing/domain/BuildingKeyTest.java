package com.roomcrosscheck.listing.domain;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** 후기 묶음 기준인 건물 키와 공개 주소가 상세 동·호를 지우고 행정동 이름은 유지하는지 검증한다. */
class BuildingKeyTest {
    @Test void addressKeyIgnoresUnitDetailSpacingAndParentheses() {
        String key = BuildingKey.ofAddress("서울특별시 강남구 대치동 316 은마아파트 26동 808호");
        assertEquals("addr:서울특별시강남구대치동316은마아파트", key);
        assertEquals(key, BuildingKey.ofAddress("서울특별시  강남구 대치동 316 은마아파트 (참고) 31동 1203호"));
        assertEquals("addr:관악구신림동123-45", BuildingKey.ofAddress("관악구 신림동 123-45, 3층"));
    }

    @Test void publicLabelHidesUnitAndLedgerKeyUsesOfficialId() {
        assertEquals("서울특별시 강남구 대치동 316 은마아파트", BuildingKey.publicLabel("서울특별시 강남구 대치동 316 은마아파트 26동 808호"));
        assertEquals("ledger:102411671", BuildingKey.ofLedger(" 102411671 "));
    }
}
