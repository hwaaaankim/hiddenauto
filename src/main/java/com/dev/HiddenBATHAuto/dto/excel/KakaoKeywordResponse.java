package com.dev.HiddenBATHAuto.dto.excel;

import java.util.List;

import lombok.Data;

@Data
public class KakaoKeywordResponse {
    private List<KakaoKeywordDoc> documents;
    private KakaoMeta meta;
}
