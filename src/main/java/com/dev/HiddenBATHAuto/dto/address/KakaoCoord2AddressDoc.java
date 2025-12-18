package com.dev.HiddenBATHAuto.dto.address;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class KakaoCoord2AddressDoc {
    @JsonProperty("road_address")
    private KakaoCoord2AddressRoadAddress roadAddress;

    @JsonProperty("address")
    private KakaoCoord2AddressAddress address;
}

