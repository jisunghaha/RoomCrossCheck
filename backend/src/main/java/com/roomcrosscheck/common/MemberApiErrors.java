package com.roomcrosscheck.common;

import com.roomcrosscheck.listing.controller.AdvertisementController;
import com.roomcrosscheck.listing.controller.ListingController;
import com.roomcrosscheck.preference.controller.PreferenceController;
import com.roomcrosscheck.report.controller.ReportController;
import com.roomcrosscheck.review.controller.ReviewController;
import com.roomcrosscheck.visit.controller.VisitController;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

/**
 * 매물·광고·방문·체크리스트·비교·후기·신고 API의 오류를 {@code {"message": ...}} 형식으로 응답한다.
 * <p>커뮤니티와 건축물대장은 각자의 Controller 예외 처리를 유지하므로 대상에서 제외한다.
 * 입력값·SQL 상세는 응답에 넣지 않는다.
 */
@RestControllerAdvice(assignableTypes = {ListingController.class, AdvertisementController.class,
    VisitController.class, PreferenceController.class, ReviewController.class, ReportController.class})
public class MemberApiErrors {
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> status(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode()).body(Map.of("message",
            error.getReason() == null ? "요청을 처리하지 못했습니다." : error.getReason()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
        MethodArgumentTypeMismatchException.class, HandlerMethodValidationException.class, IllegalArgumentException.class})
    public ResponseEntity<Map<String, String>> invalid(Exception error) {
        return ResponseEntity.badRequest().body(Map.of("message", "요청 형식과 입력 내용을 확인해 주세요."));
    }

    @ExceptionHandler({OptimisticLockingFailureException.class, DataIntegrityViolationException.class})
    public ResponseEntity<Map<String, String>> conflict(Exception error) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message",
            "다른 변경과 충돌했습니다. 새로고침한 뒤 다시 시도해 주세요."));
    }
}
