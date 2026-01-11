package com.dev.HiddenBATHAuto.dto.address;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class JusoAddrLinkResponse {

    @JsonProperty("results")
    private JusoResults results;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class JusoResults {

        @JsonProperty("common")
        private JusoCommon common;

        @JsonProperty("juso")
        private List<JusoItem> juso;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class JusoCommon {

        @JsonProperty("errorCode")
        private String errorCode;

        @JsonProperty("errorMessage")
        private String errorMessage;

        @JsonProperty("totalCount")
        private String totalCount;

        @JsonProperty("currentPage")
        private String currentPage;

        @JsonProperty("countPerPage")
        private String countPerPage;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class JusoItem {

        @JsonProperty("roadAddr")
        private String roadAddr;

        @JsonProperty("roadAddrPart1")
        private String roadAddrPart1;

        @JsonProperty("roadAddrPart2")
        private String roadAddrPart2;

        @JsonProperty("jibunAddr")
        private String jibunAddr;

        // ✅ 핵심: 우편번호
        @JsonProperty("zipNo")
        private String zipNo;

        @JsonProperty("siNm")
        private String siNm;

        @JsonProperty("sggNm")
        private String sggNm;

        @JsonProperty("emdNm")
        private String emdNm;

        @JsonProperty("liNm")
        private String liNm;

        @JsonProperty("admCd")
        private String admCd;

        @JsonProperty("rn")
        private String rn;

        @JsonProperty("buldMnnm")
        private String buldMnnm;

        @JsonProperty("buldSlno")
        private String buldSlno;

        @JsonProperty("bdNm")
        private String bdNm;
    }
}