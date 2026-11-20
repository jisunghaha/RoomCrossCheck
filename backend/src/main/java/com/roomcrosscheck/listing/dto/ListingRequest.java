package com.roomcrosscheck.listing.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record ListingRequest(
    @NotBlank @Size(max = 60) String name,
    @NotBlank @Size(max = 160) String address,
    @PositiveOrZero Long deposit,
    @PositiveOrZero Long rent,
    @PositiveOrZero Long fee,
    @DecimalMin("0.0") BigDecimal area,
    @Pattern(regexp = "전용|공급|미상") String areaType,
    @Min(-20) @Max(200) Integer floor,
    @Size(max = 10000) String original,
    /** 광고에서 시작한 등록이면 그 광고 ID. 생성할 때만 쓰고 수정 요청에서는 무시한다. */
    @Size(max = 36) String advertisementId
) {}
