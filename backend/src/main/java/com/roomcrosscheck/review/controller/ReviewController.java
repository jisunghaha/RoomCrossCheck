package com.roomcrosscheck.review.controller;

import com.roomcrosscheck.auth.service.CurrentMember;
import com.roomcrosscheck.auth.service.MemberPrincipal;
import com.roomcrosscheck.review.dto.ReviewDto.ReviewListResponse;
import com.roomcrosscheck.review.dto.ReviewDto.ReviewRequest;
import com.roomcrosscheck.review.dto.ReviewDto.ReviewResponse;
import com.roomcrosscheck.review.service.ReviewService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * SFR-017·018 거주 후기와 건물별 통계 API.
 * <p>{@code GET /api/reviews?building=키}는 비로그인도 조회할 수 있다. 작성·삭제는 로그인이 필요하다.
 */
@RestController
@RequestMapping("/api/reviews")
public class ReviewController {
    private final ReviewService service;
    private final CurrentMember member;

    public ReviewController(ReviewService service, CurrentMember member) {
        this.service = service;
        this.member = member;
    }

    @GetMapping
    public ReviewListResponse list(@RequestParam(name = "building", required = false) List<String> buildings) {
        return service.list(buildings, member.find().map(MemberPrincipal::userId).orElse(null));
    }

    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public ReviewResponse create(@Valid @RequestBody ReviewRequest request) {
        return service.create(request, member.require());
    }

    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) { service.delete(id, member.require()); }
}
