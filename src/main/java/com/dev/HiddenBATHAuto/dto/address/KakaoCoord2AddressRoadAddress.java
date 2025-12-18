package com.dev.HiddenBATHAuto.dto.address;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class KakaoCoord2AddressRoadAddress {
    @JsonProperty("address_name")
    private String addressName;

    @JsonProperty("zone_no")
    private String zoneNo;
}

