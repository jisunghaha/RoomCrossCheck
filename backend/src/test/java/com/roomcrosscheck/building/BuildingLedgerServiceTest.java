package com.roomcrosscheck.building;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roomcrosscheck.listing.domain.Listing;
import com.roomcrosscheck.listing.dto.ListingRequest;
import com.roomcrosscheck.listing.repository.ListingRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BuildingLedgerServiceTest {
    final ObjectMapper mapper=new ObjectMapper();
    @Test void comparesEquivalentBaseAddressesButNotDifferentBuildingNumbers() {
        assertTrue(BuildingLedgerService.addressMatches("서울 강남구 삼성로 212, 26동 808호","서울특별시 강남구 삼성로 212 (대치동)","서울특별시 강남구 대치동 316번지"));
        assertFalse(BuildingLedgerService.addressMatches("서울 강남구 삼성로 2120","서울특별시 강남구 삼성로 212 (대치동)",""));
        assertFalse(BuildingLedgerService.addressMatches("서울 강남구 삼성로 213","서울특별시 강남구 삼성로 212 (대치동)",""));
        assertTrue(BuildingLedgerService.addressMatches("서울 강남구 대치동 316","","서울특별시 강남구 대치동 316번지"));
    }
    @Test void excludesCommonAreaAndOtherUnitsAndPreservesBasement() throws Exception {
        var rows=mapper.readTree("""
        [{"mgmBldrgstPk":"u1","dongNm":"A동","hoNm":"101호","exposPubuseGbCd":"1","area":20.5,"flrGbCd":"10","flrNo":1},
         {"mgmBldrgstPk":"u1","dongNm":"A동","hoNm":"101호","exposPubuseGbCd":"2","area":8,"flrGbCd":"20","flrNo":1},
         {"mgmBldrgstPk":"u2","dongNm":"B동","hoNm":"101호","exposPubuseGbCd":"1","area":99,"flrGbCd":"20","flrNo":1}]
        """);
        var list=new ArrayList<com.fasterxml.jackson.databind.JsonNode>(); rows.forEach(list::add);
        var units=BuildingLedgerService.units(list,"A","101");
        assertEquals(1,units.size()); assertEquals(new BigDecimal("20.5"),units.get(0).area()); assertEquals(-1,units.get(0).floor());
    }
    @Test void splitLevelUnitDoesNotInventOneFloorAndMissingAreaIsNotZero() throws Exception {
        var rows=mapper.readTree("""
        [{"mgmBldrgstPk":"u","dongNm":"1","hoNm":"201호","exposPubuseGbCd":"1","area":12,"flrGbCd":"20","flrNo":2},
         {"mgmBldrgstPk":"u","dongNm":"1","hoNm":"201호","exposPubuseGbCd":"1","area":"","flrGbCd":"20","flrNo":3}]
        """);
        var list=new ArrayList<com.fasterxml.jackson.databind.JsonNode>(); rows.forEach(list::add);
        var unit=BuildingLedgerService.units(list,"1","201").get(0);
        assertNull(unit.area()); assertNull(unit.floor());
    }
    @Test void forgedSelectionAndStaleSearchCannotOverwriteOfficialRecord() throws Exception {
        var client=mock(BuildingLedgerClient.class); var repo=mock(ListingRepository.class);
        Listing listing=new Listing(new ListingRequest("테스트","서울 강남구 테헤란로 152",null,null,null,new BigDecimal("20"),"전용",3,null));
        when(repo.findById(listing.id)).thenReturn(Optional.of(listing));
        when(client.lookup(anyString(),any(),isNull(),isNull())).thenReturn(new BuildingLedgerClient.Lookup(List.of(mapper.readTree("{\"mgmBldrgstPk\":\"b\",\"newPlatPlc\":\"서울특별시 강남구 테헤란로 152\"}")),java.time.Instant.parse("2026-11-03T00:00:00Z"),false));
        var service=new BuildingLedgerService(client,repo);
        var result=service.search(listing.id,new LedgerQuery("1168010100","737","0","0",null,null));
        assertThrows(ResponseStatusException.class,()->service.save(listing.id,result.searchId(),"forged",null));
        assertThrows(ResponseStatusException.class,()->service.save(listing.id,result.searchId(),"b","forged"));
        listing.version++;
        assertThrows(ResponseStatusException.class,()->service.save(listing.id,result.searchId(),"b",null));
        verify(repo,never()).save(any());
    }
    @Test void editingListingInvalidatesOfficialSnapshot() {
        var r=new ListingRequest("테스트","주소",null,null,null,null,"미상",null,null);
        Listing listing=new Listing(r);
        listing.official=new OfficialRecord("공식","날짜","id","주소","","","","","","",1,0,"건물",null,"전용",null,true);
        listing.update(r); assertNull(listing.official);
    }
}
