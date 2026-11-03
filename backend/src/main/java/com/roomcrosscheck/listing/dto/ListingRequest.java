package com.roomcrosscheck.listing.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.roomcrosscheck.common.validation.OptionValues;
import com.roomcrosscheck.common.validation.OptionsDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

public final class ListingRequest {
    @NotBlank @Size(max = 60) private final String name;
    @NotBlank @Size(max = 160) private final String address;
    @PositiveOrZero private final Long deposit;
    @PositiveOrZero private final Long rent;
    @PositiveOrZero private final Long fee;
    @DecimalMin("0.0") private final BigDecimal area;
    @Pattern(regexp = "전용|공급|미상") private final String areaType;
    @Min(-20) @Max(200) private final Integer floor;
    @Size(max = 10000) private final String original;
    @Pattern(regexp = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}") private final String advertisementId;
    private List<String> options;
    @JsonIgnore private boolean optionsProvided;

    @JsonCreator
    public ListingRequest(
            @JsonProperty("name") String name,
            @JsonProperty("address") String address,
            @JsonProperty("deposit") Long deposit,
            @JsonProperty("rent") Long rent,
            @JsonProperty("fee") Long fee,
            @JsonProperty("area") BigDecimal area,
            @JsonProperty("areaType") String areaType,
            @JsonProperty("floor") Integer floor,
            @JsonProperty("original") String original,
            @JsonProperty("advertisementId") String advertisementId) {
        this.name = name;
        this.address = address;
        this.deposit = deposit;
        this.rent = rent;
        this.fee = fee;
        this.area = area;
        this.areaType = areaType;
        this.floor = floor;
        this.original = original;
        this.advertisementId = advertisementId;
    }

    @JsonSetter("options")
    @JsonDeserialize(using = OptionsDeserializer.class)
    public void setOptions(List<String> options) {
        this.options = OptionValues.normalize(options);
        this.optionsProvided = true;
    }

    public String name() { return name; }
    public String address() { return address; }
    public Long deposit() { return deposit; }
    public Long rent() { return rent; }
    public Long fee() { return fee; }
    public BigDecimal area() { return area; }
    public String areaType() { return areaType; }
    public Integer floor() { return floor; }
    public String original() { return original; }
    public String advertisementId() { return advertisementId; }
    public List<String> options() { return options; }
    @JsonIgnore public boolean optionsProvided() { return optionsProvided; }
}
