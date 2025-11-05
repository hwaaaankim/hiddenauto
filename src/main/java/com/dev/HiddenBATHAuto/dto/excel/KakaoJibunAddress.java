package com.dev.HiddenBATHAuto.dto.excel;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class KakaoJibunAddress {
    @JsonProperty("address_name")
    private String addressName;
    @JsonProperty("region_1depth_name")
    private String region1depthName;
    @JsonProperty("region_2depth_name")
    private String region2depthName;
    @JsonProperty("region_3depth_name")
    private String region3depthName;
}