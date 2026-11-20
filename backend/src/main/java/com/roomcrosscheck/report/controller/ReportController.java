package com.roomcrosscheck.report.controller;

import com.roomcrosscheck.auth.service.CurrentMember;
import com.roomcrosscheck.community.dto.CommunityDto.PageResponse;
import com.roomcrosscheck.report.domain.Report;
import com.roomcrosscheck.report.dto.ReportDto.*;
import com.roomcrosscheck.report.service.ReportService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * SFR-019 신고 접수 API와 관리자 신고 처리 API.
 * <p>{@code /api/admin/**}는 보안 필터에서 ADMIN 권한을 요구하고, 처리 서비스에서 다시 확인한다.
 */
@RestController
public class ReportController {
    private final ReportService service;
    private final CurrentMember member;

    public ReportController(ReportService service, CurrentMember member) {
        this.service = service;
        this.member = member;
    }

    @PostMapping("/api/reports") @ResponseStatus(HttpStatus.CREATED)
    public ReportReceipt create(@Valid @RequestBody ReportRequest request) {
        return service.create(request, member.require());
    }

    /** status를 생략하거나 ALL이면 전체 신고를 조회한다. */
    @GetMapping("/api/admin/reports")
    public PageResponse<AdminReportResponse> list(
            @RequestParam(name = "status", defaultValue = "PENDING") String status,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        member.require();
        Report.Status filter = "ALL".equals(status) ? null : Report.Status.valueOf(status);
        return PageResponse.from(service.list(filter, page, size));
    }

    @PostMapping("/api/admin/reports/{id}/resolve")
    public AdminReportResponse resolve(@PathVariable String id, @Valid @RequestBody ResolveRequest request) {
        return service.resolve(id, request, member.require());
    }
}
