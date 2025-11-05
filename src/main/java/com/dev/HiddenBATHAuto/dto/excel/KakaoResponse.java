package com.dev.HiddenBATHAuto.dto.excel;

import java.util.List;

import lombok.Data;

@Data
public class KakaoResponse {
    private List<KakaoDocument> documents;
    private KakaoMeta meta;
}