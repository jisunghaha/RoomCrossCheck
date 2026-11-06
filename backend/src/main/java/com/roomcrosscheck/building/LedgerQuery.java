package com.roomcrosscheck.building;

import jakarta.validation.constraints.*;

public record LedgerQuery(
    @NotNull @Pattern(regexp="[0-9]{10}") String legalCode,
    @NotNull @Pattern(regexp="[0-9]{1,4}") String bun,
    @NotNull @Pattern(regexp="[0-9]{1,4}") String ji,
    @NotNull @Pattern(regexp="[01]") String landType,
    @Size(max=80) String buildingId,
    @Size(max=40) String ho) {}
