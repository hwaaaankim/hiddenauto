package com.dev.HiddenBATHAuto.dto.production;

import java.util.ArrayList;
import java.util.List;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class StickerPrintDto {

    private Long orderId;

    private String companyName;
    private boolean standard;

    private String modelName;

    private String colorDisplay;   // ✅ "HW (히든 화이트)" 형태
    private String size;

    private List<String> optionFlags;

    private String adminMemo;
    private String adminImageUrl;

    @Builder.Default
    private List<String> debugRawKeys = new ArrayList<>();
}