package com.dev.HiddenBATHAuto.dto.as;

import java.time.LocalDateTime;
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

    private LocalDateTime requestedAt;

    private List<ImageDto> resultImages;

    @Data
    public static class ImageDto {
        private Long id;
        private String url;
        private String filename;
    }
}