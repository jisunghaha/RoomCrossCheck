package com.roomcrosscheck.report.repository;

import com.roomcrosscheck.report.domain.Report;
import com.roomcrosscheck.report.domain.Report.Status;
import com.roomcrosscheck.report.domain.Report.TargetType;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** 신고 저장소. */
public interface ReportRepository extends JpaRepository<Report, String> {
    boolean existsByTargetTypeAndTargetIdAndReporterId(TargetType type, String targetId, Long reporterId);
    Page<Report> findAllByStatusOrderByCreatedAtDesc(Status status, Pageable pageable);
    Page<Report> findAllByOrderByCreatedAtDesc(Pageable pageable);
    List<Report> findAllByTargetTypeAndTargetIdAndStatus(TargetType type, String targetId, Status status);
    long countByTargetTypeAndTargetId(TargetType type, String targetId);
}
