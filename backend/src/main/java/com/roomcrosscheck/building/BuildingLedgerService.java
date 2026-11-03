package com.roomcrosscheck.building;

import com.fasterxml.jackson.databind.JsonNode;
import com.roomcrosscheck.listing.domain.Listing;
import com.roomcrosscheck.listing.repository.ListingRepository;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class BuildingLedgerService {
    public record Building(String id,String address,String jibunAddress,String name,String dong,String use,String approvedOn,Integer aboveGroundFloors,Integer undergroundFloors) {}
    public record Unit(String id,String dong,String ho,BigDecimal area,Integer floor,String reason) {}
    public record SearchResult(String searchId,List<Building> buildings,List<Unit> units,String message) {}
    private record Search(String listingId,long version,Instant expires,List<Building> buildings,List<Unit> units) {}
    private final Map<String,Search> searches=new ConcurrentHashMap<>();
    private final BuildingLedgerClient client;
    private final ListingRepository repository;
    public BuildingLedgerService(BuildingLedgerClient client,ListingRepository repository) { this.client=client; this.repository=repository; }

    public SearchResult search(String listingId,LedgerQuery query) {
        Listing listing=find(listingId);
        var buildings=client.fetch("getBrTitleInfo",query,null,null).stream().map(n -> new Building(
            text(n,"mgmBldrgstPk"),text(n,"newPlatPlc"),text(n,"platPlc"),text(n,"bldNm"),text(n,"dongNm"),
            text(n,"mainPurpsCdNm"),text(n,"useAprDay"),integer(n,"grndFlrCnt"),integer(n,"ugrndFlrCnt"))).toList();
        List<Unit> units=List.of();
        String message=buildings.isEmpty()?"해당 지번의 건축물대장 자료가 없습니다. 선택한 주소와 지번을 확인해 주세요.":"공식 주소와 건물 동을 확인하고 선택해 주세요.";
        if (query.buildingId()!=null && !query.buildingId().isBlank()) {
            Building selected=buildings.stream().filter(b -> b.id().equals(query.buildingId())).findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,"선택한 건물이 조회 지번에 없습니다."));
            buildings=List.of(selected);
            if (query.ho()!=null && !query.ho().isBlank()) {
                String ho=query.ho().trim();
                if (ho.matches("[0-9]+")) ho+="호";
                var rows=client.fetch("getBrExposPubuseAreaInfo",query,selected.dong(),ho);
                // Some ledgers store the unit name without the suffix.
                if (rows.isEmpty() && ho.endsWith("호")) rows=client.fetch("getBrExposPubuseAreaInfo",query,selected.dong(),ho.substring(0,ho.length()-1));
                units=units(rows,selected.dong(),query.ho());
                message=units.isEmpty()?"이 동·호실의 전유면적 자료가 없습니다. 건물 정보만 저장할 수 있으며 호실 면적·층수는 비교 불가로 표시됩니다.":"조회된 호실을 선택하세요. 면적과 층수는 선택한 호실의 전유 자료로만 비교합니다.";
            } else message="건물 자료를 확인했습니다. 호실을 입력해 조회하거나 건물 정보만 저장할 수 있습니다.";
        }
        searches.entrySet().removeIf(e -> e.getValue().expires().isBefore(Instant.now()));
        if (searches.size()>=500) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"조회 요청이 많습니다. 잠시 후 다시 시도해 주세요.");
        String id=UUID.randomUUID().toString();
        searches.put(id,new Search(listing.id,listing.version,Instant.now().plusSeconds(600),buildings,units));
        return new SearchResult(id,buildings,units,message);
    }

    public Listing save(String listingId,String searchId,String buildingId,String unitId) {
        Listing listing=find(listingId);
        Search search=searchId==null?null:searches.get(searchId);
        if (search==null || !search.listingId().equals(listingId) || search.expires().isBefore(Instant.now()))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"조회 결과가 만료되었습니다. 다시 조회해 주세요.");
        if (search.version()!=listing.version) throw new ResponseStatusException(HttpStatus.CONFLICT,"매물이 수정되었습니다. 변경된 정보로 다시 조회해 주세요.");
        Building b=search.buildings().stream().filter(x -> x.id().equals(buildingId)).findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,"조회한 건물을 선택해 주세요."));
        // Units belong to the one building selected during this search, never to a client-supplied value.
        Unit u=unitId==null || unitId.isBlank()?null:search.units().stream().filter(x -> x.id().equals(unitId)).findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,"조회한 호실을 선택해 주세요."));
        if (u!=null && search.buildings().size()!=1) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        listing.official=new OfficialRecord("국토교통부 건축HUB 건축물대장",Instant.now().toString(),b.id(),b.address(),b.jibunAddress(),b.name(),b.dong(),u==null?null:u.ho(),b.use(),b.approvedOn(),b.aboveGroundFloors(),b.undergroundFloors(),u==null?"건물":"호실",u==null?null:u.area(),"전용",u==null?null:u.floor(),addressMatches(listing.address,b.address(),b.jibunAddress()));
        Listing saved=repository.save(listing);
        searches.remove(searchId);
        return saved;
    }

    static List<Unit> units(List<JsonNode> rows,String dong,String ho) {
        var grouped=new LinkedHashMap<String,List<JsonNode>>();
        for (JsonNode n:rows) {
            if (!unitName(text(n,"dongNm")).equals(unitName(dong)) || !unitName(text(n,"hoNm")).equals(unitName(ho))) continue;
            if (!"1".equals(text(n,"exposPubuseGbCd"))) continue;
            String key=text(n,"mgmBldrgstPk");
            if (key.isBlank()) continue;
            grouped.computeIfAbsent(key,k -> new ArrayList<>()).add(n);
        }
        return grouped.entrySet().stream().map(e -> {
            var list=e.getValue(); BigDecimal area=BigDecimal.ZERO; boolean valid=true;
            Set<Integer> floors=new HashSet<>();
            for (var n:list) {
                try { BigDecimal a=new BigDecimal(text(n,"area")); if(a.signum()<=0) valid=false; area=area.add(a); }
                catch (NumberFormatException ex) { valid=false; }
                Integer f=integer(n,"flrNo"); String gb=text(n,"flrGbCd");
                if (f!=null && f>0 && Set.of("10","20").contains(gb)) floors.add("10".equals(gb)?-f:f);
                else floors.add(null);
            }
            Integer floor=floors.size()==1?floors.iterator().next():null;
            JsonNode first=list.get(0);
            return new Unit(e.getKey(),text(first,"dongNm"),text(first,"hoNm"),valid?area:null,floor,
                floor==null?"층 정보가 없거나 여러 층에 걸친 호실입니다.":"동일 호실의 전유면적 합계 · 공용면적 제외");
        }).toList();
    }
    static boolean addressMatches(String ad,String road,String jibun) {
        String a=normalizeAddress(ad);
        for (String official:List.of(road,jibun)) {
            String o=normalizeAddress(official);
            if (o.isBlank()) continue;
            if (a.equals(o)) return true;
            if (a.startsWith(o)) {
                String detail=a.substring(o.length());
                if (detail.matches("(?:[, ]*(?:지하)?[0-9A-Za-z-]+(?:동|층|호))+")) return true;
            }
        }
        return false;
    }
    private static String normalizeAddress(String s) {
        return s.trim().replaceFirst("^서울시?\\s","서울특별시 ").replaceFirst("^부산시?\\s","부산광역시 ")
            .replaceFirst("^경기\\s","경기도 ").replaceAll("\\([^)]*\\)","").replaceAll("번지","").replaceAll("\\s+","");
    }
    private static String unitName(String s) { return s.trim().replaceAll("[\\s동호]",""); }
    private Listing find(String id) { return repository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"매물을 찾지 못했습니다.")); }
    private static String text(JsonNode n,String key) { return n.path(key).asText("").trim(); }
    private static Integer integer(JsonNode n,String key) { try { return Integer.valueOf(text(n,key)); } catch(Exception e) { return null; } }
}
