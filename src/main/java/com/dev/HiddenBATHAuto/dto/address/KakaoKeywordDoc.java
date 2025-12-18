package com.dev.HiddenBATHAuto.dto.address;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class KakaoKeywordDoc {

    // x/y 좌표
    private String x;
    private String y;

    @JsonProperty("road_address_name")
    private String roadAddressName;

    @JsonProperty("address_name")
    private String addressName;
}


