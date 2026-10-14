package com.roomcrosscheck.listing;

import jakarta.validation.Valid;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@RestController
@RequestMapping("/api/listings")
public class ListingController {
    private final ListingRepository repository;
    public ListingController(ListingRepository repository) { this.repository = repository; }

    @GetMapping public List<Listing> all() {
        return repository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
    }
    @GetMapping("/{id}") public Listing one(@PathVariable String id) { return find(id); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public Listing create(@Valid @RequestBody ListingRequest request) {
        return repository.save(new Listing(request));
    }
    @PatchMapping("/{id}") public Listing update(@PathVariable String id, @Valid @RequestBody ListingRequest request) {
        Listing listing = find(id);
        listing.update(request);
        return repository.save(listing);
    }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        if (!repository.existsById(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        repository.deleteById(id);
    }
    private Listing find(String id) {
        return repository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
