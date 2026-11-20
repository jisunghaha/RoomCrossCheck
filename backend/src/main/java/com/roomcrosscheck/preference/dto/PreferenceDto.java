package com.roomcrosscheck.preference.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 체크리스트 구성·비교 선택 API의 요청·응답 형식이다. */
public final class PreferenceDto {
    private PreferenceDto() {}

    /** 체크리스트 항목 입력값. ID는 기존 체크 결과와 연결되므로 화면에서 만든 값을 유지한다. */
    public record ChecklistItemRequest(
        @NotNull @Pattern(regexp = "[A-Za-z0-9-]{1,40}") String id,
        @NotBlank @Size(max = 30) String label,
        @Size(max = 80) String tip
    ) {}

    /**
     * 체크리스트 저장 요청.
     *
     * @param items 1~30개 항목, null이면 기본 체크리스트로 되돌린다
     */
    public record ChecklistRequest(@Size(min = 1, max = 30) List<@Valid @NotNull ChecklistItemRequest> items) {}

    /** 저장한 체크리스트. items가 null이면 기본 항목을 쓴다. */
    public record ChecklistResponse(List<ChecklistItemResponse> items) {}

    public record ChecklistItemResponse(String id, String label, String tip) {}

    /** 비교 대상 매물 선택. 서로 다른 본인 매물 0~3개. */
    public record ComparisonRequest(@NotNull @Size(max = 3) List<@NotBlank @Size(max = 36) String> listingIds) {}

    public record ComparisonResponse(List<String> listingIds) {}
}
