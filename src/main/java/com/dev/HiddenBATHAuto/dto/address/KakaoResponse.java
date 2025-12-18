package com.dev.HiddenBATHAuto.dto.address;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class KakaoResponse {
    private List<KakaoDocument> documents;
}

