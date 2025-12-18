package com.dev.HiddenBATHAuto.dto.address;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class KakaoDocument {

    @JsonProperty("address_name")
    private String addressName;

    @JsonProperty("road_address")
    private KakaoRoadAddress roadAddress;

    @JsonProperty("address")
    private KakaoJibunAddress address;

    // ✅ 추가: 주소검색 결과에도 x,y가 옵니다 (coord2address 보강에 사용)
    @JsonProperty("x")
    private String x;

    @JsonProperty("y")
    private String y;
}

