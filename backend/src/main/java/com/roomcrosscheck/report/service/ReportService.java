package com.roomcrosscheck.report.service;

import com.roomcrosscheck.auth.service.MemberPrincipal;
import com.roomcrosscheck.community.repository.CommentRepository;
import com.roomcrosscheck.community.repository.PostRepository;
import com.roomcrosscheck.community.service.CommentService;
import com.roomcrosscheck.community.service.PostService;
import com.roomcrosscheck.report.domain.Report;
import com.roomcrosscheck.report.domain.Report.Status;
import com.roomcrosscheck.report.domain.Report.TargetType;
import com.roomcrosscheck.report.dto.ReportDto.*;
import com.roomcrosscheck.report.repository.ReportRepository;
import com.roomcrosscheck.review.repository.BuildingReviewRepository;
import com.roomcrosscheck.review.service.ReviewService;
import java.time.Instant;
import java.util.Objects;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * SFR-019 신고 접수와 관리자 처리를 담당한다.
 * <p>대상 작성자와 미리보기는 서버가 대상에서 직접 읽으며 클라이언트 값을 쓰지 않는다.
 * 관리자 처리는 같은 대상의 대기 중 신고를 한꺼번에 같은 결과로 닫는다. 대상 삭제는 각 기능의
 * 삭제 서비스를 관리자 권한으로 호출하므로 댓글·좋아요 연쇄 삭제와 통계 캐시 갱신 규칙을 그대로 따른다.
 */
@Service
@Transactional(readOnly = true)
public class ReportService {
    private static final int PREVIEW_LENGTH = 200;
    private final ReportRepository reports;
    private final PostRepository posts;
    private final CommentRepository comments;
    private final BuildingReviewRepository reviews;
    private final PostService postService;
    private final CommentService commentService;
    private final ReviewService reviewService;

    public ReportService(ReportRepository reports, PostRepository posts, CommentRepository comments,
            BuildingReviewRepository reviews, PostService postService, CommentService commentService,
            ReviewService reviewService) {
        this.reports = reports;
        this.posts = posts;
        this.comments = comments;
        this.reviews = reviews;
        this.postService = postService;
        this.commentService = commentService;
        this.reviewService = reviewService;
    }

    private record Target(Long authorId, String preview, String postId) {}

    /**
     * 신고를 접수한다.
     *
     * @param request 검증된 신고 입력
     * @param reporter 로그인 회원
     * @return 접수 결과
     * @throws ResponseStatusException 대상 없음 404, 본인 글 신고·설명 누락 400, 중복 신고 409
     */
    @Transactional
    public ReportReceipt create(ReportRequest request, MemberPrincipal reporter) {
        String detail = request.detail() == null || request.detail().isBlank() ? null : request.detail().strip();
        if (request.reason() == Report.Reason.OTHER && detail == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "기타 사유는 설명을 입력해 주세요.");
        }
        Target target = target(request.targetType(), request.targetId(), request.postId());
        if (target == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "신고할 대상을 찾지 못했습니다.");
        if (Objects.equals(target.authorId(), reporter.userId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "본인이 작성한 내용은 신고할 수 없습니다.");
        }
        if (reports.existsByTargetTypeAndTargetIdAndReporterId(request.targetType(), request.targetId(), reporter.userId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 신고한 내용입니다.");
        }
        Report report = new Report();
        report.targetType = request.targetType();
        report.targetId = request.targetId();
        report.postId = target.postId();
        report.reporterId = reporter.userId();
        report.targetAuthorId = target.authorId();
        report.targetPreview = target.preview();
        report.reason = request.reason();
        report.detail = detail;
        return ReportReceipt.from(reports.saveAndFlush(report));
    }

    /**
     * 관리자용 신고 목록을 최신순으로 조회한다.
     *
     * @param status 처리 상태, null이면 전체
     * @param page 0부터 시작하는 페이지
     * @param size 1~50
     * @return 신고 목록
     */
    public Page<AdminReportResponse> list(Status status, int page, int size) {
        if (page < 0 || size < 1 || size > 50) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "페이지 범위를 확인해 주세요.");
        }
        var pageable = PageRequest.of(page, size);
        Page<Report> rows = status == null ? reports.findAllByOrderByCreatedAtDesc(pageable)
            : reports.findAllByStatusOrderByCreatedAtDesc(status, pageable);
        return rows.map(report -> AdminReportResponse.from(report,
            target(report.targetType, report.targetId, report.postId) != null,
            reports.countByTargetTypeAndTargetId(report.targetType, report.targetId)));
    }

    /**
     * 신고를 처리한다. 대상 삭제는 대상이 이미 없으면 삭제 없이 처리 완료로 기록한다.
     *
     * @param id 신고 ID
     * @param request 처리 방식과 메모
     * @param admin 관리자 회원
     * @return 처리한 신고
     * @throws ResponseStatusException 없으면 404, 이미 처리됐으면 409
     */
    @Transactional
    public AdminReportResponse resolve(String id, ResolveRequest request, MemberPrincipal admin) {
        if (!admin.administrator()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "관리자만 처리할 수 있습니다.");
        Report report = reports.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "신고를 찾지 못했습니다."));
        if (report.status != Status.PENDING) throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 처리된 신고입니다.");
        boolean delete = request.action() == Action.DELETE_TARGET;
        if (delete && target(report.targetType, report.targetId, report.postId) != null) {
            switch (report.targetType) {
                case POST -> postService.deletePost(report.targetId, admin);
                case COMMENT -> commentService.deleteComment(report.postId, report.targetId, admin);
                case REVIEW -> reviewService.delete(report.targetId, admin);
            }
        }
        String note = request.note() == null || request.note().isBlank() ? null : request.note().strip();
        Instant now = Instant.now();
        for (Report pending : reports.findAllByTargetTypeAndTargetIdAndStatus(report.targetType, report.targetId, Status.PENDING)) {
            pending.status = delete ? Status.ACCEPTED : Status.DISMISSED;
            pending.handledAt = now;
            pending.handlerId = admin.userId();
            pending.handlerNote = note;
        }
        reports.flush();
        return AdminReportResponse.from(report, !delete && target(report.targetType, report.targetId, report.postId) != null,
            reports.countByTargetTypeAndTargetId(report.targetType, report.targetId));
    }

    private Target target(TargetType type, String targetId, String postId) {
        return switch (type) {
            case POST -> posts.findById(targetId)
                .map(post -> new Target(post.authorId, preview(post.title + " · " + post.content), null)).orElse(null);
            case COMMENT -> (postId == null ? comments.findById(targetId) : comments.findByIdAndPost_Id(targetId, postId))
                .map(comment -> new Target(comment.authorId, preview(comment.content), comment.post.id)).orElse(null);
            case REVIEW -> reviews.findById(targetId)
                .map(review -> new Target(review.authorId, preview(review.body), null)).orElse(null);
        };
    }

    private static String preview(String value) {
        String text = value == null ? "" : value.replaceAll("\\s+", " ").strip();
        return text.length() > PREVIEW_LENGTH ? text.substring(0, PREVIEW_LENGTH - 1) + "…" : text;
    }
}
