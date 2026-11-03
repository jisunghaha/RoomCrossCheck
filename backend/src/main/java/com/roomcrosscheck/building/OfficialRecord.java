package com.roomcrosscheck.building;

import java.math.BigDecimal;

public record OfficialRecord(String source, String date, String buildingId,
    String address, String jibunAddress, String buildingName, String dong, String ho,
    String use, String approvedOn, Integer aboveGroundFloors, Integer undergroundFloors,
    String scope, BigDecimal area, String areaType, Integer floor, boolean addressMatch) {}
