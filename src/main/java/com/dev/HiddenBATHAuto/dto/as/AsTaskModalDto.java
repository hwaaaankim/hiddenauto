package com.dev.HiddenBATHAuto.dto.as;

import java.util.List;

import lombok.Data;

@Data
public class AsTaskModalDto {
    private Long id;

    private String companyName;
    private String requesterName;
    private String fullAddress;

    private String reason;
    private String productName;
    private String productSize;
    private String productColor;
    private String onsiteContact;

    // ✅ LocalDateTime -> String 으로 변경
    private String requestedAt;

    // ✅ 추가하신 관리자 메모
    private String adminMemo;

    private List<ImageDto> resultImages;

    @Data
    public static class ImageDto {
        private Long id;
        private String url;
        private String filename;
    }
}