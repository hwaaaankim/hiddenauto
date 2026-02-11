package com.dev.HiddenBATHAuto.dto.productUpload;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

@Data
public class ProductImageUploadReport {

    private Summary summary = new Summary();

    private Section standard = new Section("STANDARD");
    private Section series = new Section("SERIES");
    private Section product = new Section("PRODUCT");

    private Missing missing = new Missing();

    @Data
    public static class Summary {
        private int totalFolders;
        private int updated;
        private int notMatchedFolders;
        private int errors;
    }

    @Data
    public static class Section {
        private String type;
        private List<Item> items = new ArrayList<>();
        public Section(String type) { this.type = type; }
    }

    @Data
    public static class Item {
        private String status;      // UPDATED / NOT_FOUND / NO_IMAGE / AMBIGUOUS / ERROR
        private String folderName;  // ZIP 폴더명
        private String matchedId;   // 엔티티 ID
        private String message;     // 상세 메시지
        private String newImageUrl; // 업로드 후 URL
    }

    @Data
    public static class Missing {
        // “업로드 후에도 이미지가 없는 엔티티” 목록 (표시 제한은 화면에서 처리)
        private List<String> standardNoImage = new ArrayList<>();
        private List<String> seriesNoImage = new ArrayList<>();
        private List<String> productNoImage = new ArrayList<>();
    }
}