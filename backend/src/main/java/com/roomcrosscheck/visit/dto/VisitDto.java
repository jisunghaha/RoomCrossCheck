package com.roomcrosscheck.visit.dto;

import com.roomcrosscheck.visit.domain.VisitRecord;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

/** 방문 기록 API의 요청·응답 형식이다. */
public final class VisitDto {
    private VisitDto() {}

    /**
     * 방문 기록 저장 요청.
     *
     * @param checks 체크리스트 항목 ID별 확인 여부, 최대 50개
     * @param note 방문 메모, 최대 2000자
     * @param visitedOn 방문일, 선택 입력
     */
    public record VisitRequest(
        @NotNull @Size(max = 50) Map<@NotNull @Pattern(regexp = "[A-Za-z0-9-]{1,40}") String, @NotNull Boolean> checks,
        @Size(max = 2000) String note,
        LocalDate visitedOn
    ) {}

    /** 저장된 방문 기록. 회원 번호는 포함하지 않는다. */
    public record VisitResponse(Map<String, Boolean> checks, String note, LocalDate visitedOn, Instant updatedAt) {
        public static VisitResponse from(VisitRecord record) {
            return new VisitResponse(record.checks, record.note == null ? "" : record.note, record.visitedOn,
                record.updatedAt);
        }
    }
}
