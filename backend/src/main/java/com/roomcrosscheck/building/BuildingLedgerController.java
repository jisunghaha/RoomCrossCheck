package com.roomcrosscheck.building;

import com.roomcrosscheck.listing.domain.Listing;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;

@RestController
@RequestMapping("/api/listings/{id}/official")
public class BuildingLedgerController {
    private final BuildingLedgerService service;
    public BuildingLedgerController(BuildingLedgerService service) { this.service=service; }
    @PostMapping("/search")
    public BuildingLedgerService.SearchResult search(@PathVariable String id,@Valid @RequestBody LedgerQuery query) { return service.search(id,query); }
    public record Selection(@NotBlank String searchId,@NotBlank @Size(max=80) String buildingId,@Size(max=80) String unitId) {}
    @PutMapping
    public Listing save(@PathVariable String id,@Valid @RequestBody Selection selection) { return service.save(id,selection.searchId(),selection.buildingId(),selection.unitId()); }
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String,String>> error(ResponseStatusException e) { return ResponseEntity.status(e.getStatusCode()).body(Map.of("message",e.getReason()==null?"공식 조회에 실패했습니다.":e.getReason())); }
}
