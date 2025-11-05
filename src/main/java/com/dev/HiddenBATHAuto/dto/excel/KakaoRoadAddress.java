package com.dev.HiddenBATHAuto.dto.excel;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class KakaoRoadAddress {
    @JsonProperty("address_name")
    private String addressName;
    @JsonProperty("region_1depth_name")
    private String region1depthName; // 도/특별/광역시
    @JsonProperty("region_2depth_name")
    private String region2depthName; // 시/군/구
    @JsonProperty("region_3depth_name")
    private String region3depthName; // 동/읍/면 (여기서는 구 수준으로 사용할 수 있음)
    @JsonProperty("road_name")
    private String roadName;
    @JsonProperty("underground_yn")
    private String undergroundYn;
    @JsonProperty("main_building_no")
    private String mainBuildingNo;
    @JsonProperty("sub_building_no")
    private String subBuildingNo;
    @JsonProperty("building_name")
    private String buildingName;
    @JsonProperty("zone_no")
    private String zoneNo; // 우편번호
}