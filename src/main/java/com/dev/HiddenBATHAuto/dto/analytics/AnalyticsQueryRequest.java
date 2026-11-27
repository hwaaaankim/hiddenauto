package com.dev.HiddenBATHAuto.dto.analytics;

import java.time.LocalDate;
import java.util.List;

import lombok.Data;

@Data
public class AnalyticsQueryRequest {

    public enum PrimaryY { COMPANY, PRODUCT }

    private LocalDate fromDate;
    private LocalDate toDate;

    private PrimaryY primaryY;

    // COMPANY 모드일 때만 사용
    private AddressFilter addressFilter;
    private List<HourRange> hourRanges; // 복수 구간 허용

    // PRODUCT 모드일 때 사용: 어떤 레벨로 볼지
    // STANDARD: CATEGORY/SERIES/PRODUCT/COLOR
    // NONSTANDARD: SORT/SERIES/PRODUCT/COLOR
    private ProductBreakdown productBreakdown;

    // X축(지표) - COMPANY 모드에서는 metrics, PRODUCT 모드에서는 기본 3개 포함 가능
    private List<String> metrics; // "SALES","TASK_COUNT","QTY" (TASK_COUNT 키는 호환용이며 실제 집계는 개별 Order 건수)

    // 정렬
    private String sortKey; // "sales","taskCount","qty","companyName","ownerName","address",...
    private String sortDir; // "asc" | "desc"

    private String keyword;
    private List<String> recordKinds; // PAID/RETURN/COLLECTION/ADJUSTMENT; null = all
    private String deliveryView = "NONE"; // NONE / METHOD / COMPOSITION
    private String deliveryMethod = "ALL"; // ALL / UNASSIGNED / delivery method ID
    private String timeGrouping = "NONE"; // NONE/DAY/WEEK/MONTH
    private String chartType = "BAR"; // BAR/HORIZONTAL/LINE
    private List<String> chartMetrics;
    private int chartLimit = 10;
    private String chartSortDir = "desc";
    private boolean compactTable;

    // paging
    private int page = 0;
    private int size = 50;

    @Data
    public static class HourRange {
        private int startHour; // 0~23
        private int endHour;   // 0~23 (inclusive)
    }

    @Data
    public static class AddressFilter {
        private String province; // 예: "경기도"
        private String city;     // 예: "용인시" (없으면 null)
        private String district; // 예: "수지구" (없으면 null)
    }

    @Data
    public static class ProductBreakdown {
        private boolean includeStandard = true;
        private boolean includeNonStandard = true;

        // "CATEGORY","SERIES","PRODUCT","COLOR"
        private String standardLevel;

        // "SORT","SERIES","PRODUCT","COLOR"
        private String nonStandardLevel;
    }
}