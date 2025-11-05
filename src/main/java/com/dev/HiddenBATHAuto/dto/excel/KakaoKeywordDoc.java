package com.dev.HiddenBATHAuto.dto.excel;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class KakaoKeywordDoc {
    @JsonProperty("place_name")
    private String placeName;
    @JsonProperty("road_address_name")
    private String roadAddressName; // ★ 도로명 주소
    @JsonProperty("address_name")
    private String addressName;     // ★ 지번 주소
    private String x;
    private String y;
}