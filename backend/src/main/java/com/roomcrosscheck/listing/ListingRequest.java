package com.roomcrosscheck.listing;

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
    @Min(-10) @Max(200) Integer floor,
    @Size(max = 3000) String original
) {}
