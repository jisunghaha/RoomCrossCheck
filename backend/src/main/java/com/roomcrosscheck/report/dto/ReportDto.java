package com.roomcrosscheck.report.dto;

import com.roomcrosscheck.report.domain.Report;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/** 신고·관리자 처리 API의 요청·응답 형식이다. */
public final class ReportDto {
    private ReportDto() {}

    /**
     * 신고 요청.
     *
     * @param targetType POST, COMMENT, REVIEW
     * @param targetId 신고할 게시글·댓글·후기 ID
     * @param postId 댓글 신고일 때 게시글 ID
     * @param reason 신고 사유
     * @param detail 추가 설명, OTHER 사유면 필수, 최대 500자
     */
    public record ReportRequest(
        @NotNull Report.TargetType targetType,
        @NotBlank @Size(max = 36) String targetId,
        @Size(max = 36) String postId,
        @NotNull Report.Reason reason,
        @Size(max = 500) String detail
    ) {}

    /** 신고한 회원에게 돌려주는 접수 결과. 처리 담당자 정보는 포함하지 않는다. */
    public record ReportReceipt(String id, Report.Status status, Instant createdAt) {
        public static ReportReceipt from(Report report) {
            return new ReportReceipt(report.id, report.status, report.createdAt);
        }
    }

    /** 관리자 처리 방식. DELETE_TARGET은 대상 삭제 후 처리, DISMISS는 문제없음으로 반려한다. */
    public enum Action { DELETE_TARGET, DISMISS }

    public record ResolveRequest(@NotNull Action action, @Size(max = 300) String note) {}

    /**
     * 관리자용 신고 정보.
     *
     * @param targetExists 대상이 아직 남아 있으면 true
     * @param targetReportCount 같은 대상에 접수된 전체 신고 수
     */
    public record AdminReportResponse(String id, Report.TargetType targetType, String targetId, String postId,
            Report.Reason reason, String detail, String targetPreview, Long targetAuthorId, Long reporterId,
            Report.Status status, Instant createdAt, Instant handledAt, Long handlerId, String handlerNote,
            boolean targetExists, long targetReportCount) {
        public static AdminReportResponse from(Report report, boolean targetExists, long targetReportCount) {
            return new AdminReportResponse(report.id, report.targetType, report.targetId, report.postId,
                report.reason, report.detail, report.targetPreview, report.targetAuthorId, report.reporterId,
                report.status, report.createdAt, report.handledAt, report.handlerId, report.handlerNote,
                targetExists, targetReportCount);
        }
    }
}
