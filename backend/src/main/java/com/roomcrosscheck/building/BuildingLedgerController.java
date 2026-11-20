package com.roomcrosscheck.building;

import com.roomcrosscheck.listing.domain.Listing;
import com.roomcrosscheck.listing.service.ListingAccess;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;

/**
 * SFR-006 건축물대장 조회와 공식 기록 저장 API.
 * <p>매물 소유자만 조회·저장할 수 있다. 외부 API 호출 전에 소유권을 확인해 인증키 이용 한도를 보호한다.
 */
@RestController
@RequestMapping("/api/listings/{id}/official")
public class BuildingLedgerController {
    private final BuildingLedgerService service;
    private final ListingAccess access;
    public BuildingLedgerController(BuildingLedgerService service, ListingAccess access) { this.service=service; this.access=access; }
    @PostMapping("/search")
    public BuildingLedgerService.SearchResult search(@PathVariable String id,@Valid @RequestBody LedgerQuery query) { access.requireOwned(id); return service.search(id,query); }
    public record Selection(@NotBlank String searchId,@NotBlank @Size(max=80) String buildingId,@Size(max=80) String unitId) {}
    @PutMapping
    public Listing save(@PathVariable String id,@Valid @RequestBody Selection selection) { access.requireOwned(id); return service.save(id,selection.searchId(),selection.buildingId(),selection.unitId()); }
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String,String>> error(ResponseStatusException e) { return ResponseEntity.status(e.getStatusCode()).body(Map.of("message",e.getReason()==null?"공식 조회에 실패했습니다.":e.getReason())); }
}
