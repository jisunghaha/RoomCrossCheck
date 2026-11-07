package com.roomcrosscheck.extraction.client;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Public error codes and messages; never includes provider bodies or credentials. */
public final class ExtractionFailure extends ResponseStatusException {
    public enum Kind {
        CONFIGURATION_MISSING(HttpStatus.SERVICE_UNAVAILABLE, "Gemini 추출 설정이 없습니다. 서버 설정을 확인해 주세요."),
        AUTHENTICATION_FAILED(HttpStatus.BAD_GATEWAY, "AI 서비스 인증 또는 접근 권한을 확인해 주세요."),
        RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "AI 서비스 사용 한도에 도달했습니다. 잠시 후 다시 시도해 주세요."),
        PROVIDER_UNAVAILABLE(HttpStatus.BAD_GATEWAY, "AI 서비스가 요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요."),
        TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "AI 서비스 응답 시간이 초과됐습니다. 잠시 후 다시 시도해 주세요."),
        CONNECTION_FAILED(HttpStatus.BAD_GATEWAY, "AI 서비스에 연결하지 못했습니다. 잠시 후 다시 시도해 주세요."),
        INVALID_RESPONSE(HttpStatus.BAD_GATEWAY, "AI 응답 형식이 올바르지 않아 결과를 저장하지 않았습니다. 다시 시도해 주세요."),
        NO_READABLE_TEXT(HttpStatus.BAD_GATEWAY, "광고에서 읽을 수 있는 글자를 찾지 못했습니다. 더 선명한 이미지나 광고 글로 다시 등록해 주세요."),
        INVALID_VALUES(HttpStatus.BAD_GATEWAY, "AI가 허용 범위 밖의 값을 반환해 결과를 저장하지 않았습니다. 다시 시도해 주세요."),
        SOURCE_UNAVAILABLE(HttpStatus.INTERNAL_SERVER_ERROR, "저장된 광고 이미지를 읽지 못했습니다. 원본 보관 상태를 확인해 주세요."),
        INTERRUPTED(HttpStatus.BAD_GATEWAY, "AI 추출 요청이 중단됐습니다. 잠시 후 다시 시도해 주세요.");

        private final HttpStatus status;
        private final String message;
        Kind(HttpStatus status, String message) { this.status = status; this.message = message; }
    }

    private final Kind kind;
    public ExtractionFailure(Kind kind) { super(kind.status, kind.message); this.kind = kind; }
    public String code() { return kind.name(); }
}
