package com.roomcrosscheck.report.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.roomcrosscheck.auth.domain.User;
import com.roomcrosscheck.auth.domain.UserRole;
import com.roomcrosscheck.auth.service.MemberPrincipal;
import com.roomcrosscheck.community.repository.CommentRepository;
import com.roomcrosscheck.community.repository.PostRepository;
import com.roomcrosscheck.community.service.CommentService;
import com.roomcrosscheck.community.service.PostService;
import com.roomcrosscheck.report.domain.Report;
import com.roomcrosscheck.report.dto.ReportDto.*;
import com.roomcrosscheck.report.repository.ReportRepository;
import com.roomcrosscheck.review.domain.BuildingReview;
import com.roomcrosscheck.review.repository.BuildingReviewRepository;
import com.roomcrosscheck.review.service.ReviewService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

/** 신고 접수의 본인·중복·기타 사유 검사와, 관리자 처리 시 대상 삭제와 같은 대상 신고 일괄 종료를 검증한다. */
class ReportServiceTest {
    private final ReportRepository reports = mock(ReportRepository.class);
    private final BuildingReviewRepository reviews = mock(BuildingReviewRepository.class);
    private final ReviewService reviewService = mock(ReviewService.class);
    private ReportService service;
    private BuildingReview review;

    private static MemberPrincipal member(long id, UserRole role) {
        User user = new User("member" + id, "hash", "회원" + id, "m" + id + "@example.com");
        ReflectionTestUtils.setField(user, "id", id);
        ReflectionTestUtils.setField(user, "role", role);
        return new MemberPrincipal(user);
    }

    @BeforeEach void setup() {
        service = new ReportService(reports, mock(PostRepository.class), mock(CommentRepository.class), reviews,
            mock(PostService.class), mock(CommentService.class), reviewService);
        review = new BuildingReview();
        review.authorId = 1L;
        review.body = "벽이 얇아서   옆집 소리가 다 들려요";
        when(reviews.findById(review.id)).thenReturn(Optional.of(review));
        when(reports.saveAndFlush(any(Report.class))).thenAnswer(call -> call.getArgument(0));
    }

    private ReportRequest request(Report.Reason reason, String detail) {
        return new ReportRequest(Report.TargetType.REVIEW, review.id, null, reason, detail);
    }

    @Test void reportStoresServerSidePreviewAndRejectsSelfDuplicateAndMissingDetail() {
        var receipt = service.create(request(Report.Reason.ABUSE, " 욕설 "), member(2, UserRole.USER));
        assertEquals(Report.Status.PENDING, receipt.status());
        verify(reports).saveAndFlush(argThat(saved -> saved.targetAuthorId == 1L
            && saved.targetPreview.equals("벽이 얇아서 옆집 소리가 다 들려요") && saved.detail.equals("욕설")));

        var self = assertThrows(ResponseStatusException.class, () -> service.create(request(Report.Reason.SPAM, null), member(1, UserRole.USER)));
        assertEquals(HttpStatus.BAD_REQUEST, self.getStatusCode());
        var other = assertThrows(ResponseStatusException.class, () -> service.create(request(Report.Reason.OTHER, " "), member(2, UserRole.USER)));
        assertEquals(HttpStatus.BAD_REQUEST, other.getStatusCode());
        when(reports.existsByTargetTypeAndTargetIdAndReporterId(Report.TargetType.REVIEW, review.id, 3L)).thenReturn(true);
        var duplicate = assertThrows(ResponseStatusException.class, () -> service.create(request(Report.Reason.SPAM, null), member(3, UserRole.USER)));
        assertEquals(HttpStatus.CONFLICT, duplicate.getStatusCode());
    }

    @Test void adminDeleteRemovesTargetAndClosesEveryPendingReportForIt() {
        Report first = new Report(); first.targetType = Report.TargetType.REVIEW; first.targetId = review.id;
        Report second = new Report(); second.targetType = Report.TargetType.REVIEW; second.targetId = review.id;
        when(reports.findById(first.id)).thenReturn(Optional.of(first));
        when(reports.findAllByTargetTypeAndTargetIdAndStatus(Report.TargetType.REVIEW, review.id, Report.Status.PENDING))
            .thenReturn(List.of(first, second));
        MemberPrincipal admin = member(9, UserRole.ADMIN);

        service.resolve(first.id, new ResolveRequest(Action.DELETE_TARGET, "운영 정책 위반"), admin);
        verify(reviewService).delete(review.id, admin);
        for (Report report : List.of(first, second)) {
            assertEquals(Report.Status.ACCEPTED, report.status);
            assertEquals(9L, report.handlerId);
            assertEquals("운영 정책 위반", report.handlerNote);
        }
        var again = assertThrows(ResponseStatusException.class,
            () -> service.resolve(first.id, new ResolveRequest(Action.DISMISS, null), admin));
        assertEquals(HttpStatus.CONFLICT, again.getStatusCode());
        var notAdmin = assertThrows(ResponseStatusException.class,
            () -> service.resolve(second.id, new ResolveRequest(Action.DISMISS, null), member(2, UserRole.USER)));
        assertEquals(HttpStatus.FORBIDDEN, notAdmin.getStatusCode());
    }
}
