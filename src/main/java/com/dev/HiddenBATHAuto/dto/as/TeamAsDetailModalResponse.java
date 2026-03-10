package com.dev.HiddenBATHAuto.dto.as;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TeamAsDetailModalResponse {

    private Long id;
    private String status;

    private String companyName;
    private String requesterName;
    private String fullAddress;

    private String reason;
    private String adminMemo;
    private String handlerMemo;
    private String visitPlannedTime;

    private String productName;
    private String productSize;
    private String productColor;

    private String onsiteContact;
    private String requestedAt;

    private List<ImageItem> resultImages = new ArrayList<>();

    @Getter
    @Setter
    public static class ImageItem {
        private Long id;
        private String filename;
        private String url;
    }
}