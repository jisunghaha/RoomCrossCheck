package com.roomcrosscheck.visit.repository;

import com.roomcrosscheck.visit.domain.VisitRecord;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** 방문 기록 저장소. 조회는 항상 회원 번호 조건을 붙인다. */
public interface VisitRecordRepository extends JpaRepository<VisitRecord, String> {
    List<VisitRecord> findAllByOwnerId(Long ownerId);
}
