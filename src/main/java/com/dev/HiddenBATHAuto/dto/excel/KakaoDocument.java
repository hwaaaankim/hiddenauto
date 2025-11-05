package com.dev.HiddenBATHAuto.dto.excel;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class KakaoDocument {
    // address_type: "ROAD_ADDR" | "REGION_ADDR" | "ROAD" / "JIBUN" 등 버전에 따라
    @JsonProperty("address_type")
    private String addressType;

    // 도로명/지번 공통
    @JsonProperty("address_name")
    private String addressName;

    private Double x;
    private Double y;

    // 도로명 주소 블록
    @JsonProperty("road_address")
    private KakaoRoadAddress roadAddress;

    // 지번 주소 블록
    private KakaoJibunAddress address;
}
