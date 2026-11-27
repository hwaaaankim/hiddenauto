package com.dev.HiddenBATHAuto.dto.analytics;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AnalyticsQueryResponse {

    private List<ColumnMeta> columns;          // ✅ 컬럼 메타(한글명/설명)
    private List<Map<String, Object>> rows;    // 데이터(키는 영문 유지)
    private long totalRows;

    private Meta meta;                         // ✅ 조회 조건 요약
    private Summary summary;                   // ✅ Top 5 요약

    private Totals totals;
    private List<String> warnings;

    private ChartData chart;                   // 그래프(옵션)

    /** Repeat identifying columns when a printed table needs several readable bands. */
    @JsonIgnore
    public List<List<ColumnMeta>> getReportColumnGroups() {
        var source = columns == null ? List.<ColumnMeta>of() : columns;
        if (source.size() <= 8) return List.of(source);
        var identifiers = source.stream().filter(c -> c.getUnit() == null).toList();
        var metrics = source.stream().filter(c -> c.getUnit() != null).toList();
        int perGroup = Math.max(1, Math.min(4, 8 - identifiers.size()));
        var result = new ArrayList<List<ColumnMeta>>();
        for (int start = 0; start < metrics.size(); start += perGroup) {
            var group = new ArrayList<>(identifiers);
            group.addAll(metrics.subList(start, Math.min(start + perGroup, metrics.size())));
            result.add(group);
        }
        return result.isEmpty() ? List.of(source) : result;
    }

    @Data
    @Builder
    public static class ColumnMeta {
        private String key;        // rows의 key
        private String label;      // 화면/엑셀/A4에 보여줄 한글 라벨
        private String unit;
        private String help;       // ? 도움말 문구
    }

    @Data
    @Builder
    public static class Meta {
        private String periodText;     // 예: "필터기간: 2026-02-01 ~ 2026-02-10" or "필터기간: 전체"
        private String yAxisText;      // 예: "Y축: 업체별" / "Y축: 제품별"
        private String ySubText;       // 예: "Y-Sub: 주소, 시간대" / "Y-Sub: 없음"
        private String xAxisText;      // 예: "X축 지표: 매출액, 주문건수"
        private String xDimText;       // 예: "제품 분류: 규격(CATEGORY), 비규격(SORT)" / "제품 분류: 없음"
        private String addressText;    // 예: "주소필터: 경기도 용인시 수지구" / "주소필터: 전체"
        private String hourText;       // 예: "시간대: 0-8, 10-16" / "시간대: 전체"
        private String basisText;
        private String chartBasisText;
        private String viewText;
        private String filterText;
        private String generatedAtText;
        private boolean compactTable;
        @com.fasterxml.jackson.annotation.JsonProperty("yAxisText") public String getYAxisText(){return yAxisText;}
        @com.fasterxml.jackson.annotation.JsonProperty("ySubText") public String getYSubText(){return ySubText;}
        @com.fasterxml.jackson.annotation.JsonProperty("xAxisText") public String getXAxisText(){return xAxisText;}
        @com.fasterxml.jackson.annotation.JsonProperty("xDimText") public String getXDimText(){return xDimText;}
        private String sortText;       // 예: "정렬: 매출액 내림차순"
    }

    @Data
    @Builder
    public static class Summary {
        private String title;
        private List<ColumnMeta> metricColumns;
        private List<RankItem> topCompanies;   // 업체별 Top 5 (COMPANY 모드일 때)
        private List<RankItem> topRegions;     // 지역 Top 5 (COMPANY 모드일 때 - province/city/district 기반)
        private List<RankItem> topCategories;  // 카테고리 Top 5 (PRODUCT 모드일 때 - norm.top)
        private List<RankItem> topSeries;      // 시리즈 Top 5 (PRODUCT 모드일 때 - norm.mid)
        private List<RankItem> topProducts;    // 제품 Top 5 (PRODUCT 모드일 때 - norm.product)

        @JsonIgnore
        public List<List<ColumnMeta>> getReportMetricGroups() {
            var source = metricColumns == null ? List.<ColumnMeta>of() : metricColumns;
            if (source.size() <= 4) return List.of(source);
            var result = new ArrayList<List<ColumnMeta>>();
            for (int start = 0; start < source.size(); start += 4) {
                result.add(source.subList(start, Math.min(start + 4, source.size())));
            }
            return result;
        }
    }

    @Data
    @Builder
    public static class RankItem {
        private int rank;
        private String name;
        private long sales;
        private long taskCount;
        private long qty;
        private Map<String, Long> metrics;
    }

    @Data
    @Builder
    public static class Totals {
        private long sales;
        // Legacy JSON key retained; the value is the number of distinct Order rows.
        private long taskCount;
        private long qty;
        private Map<String, Long> metrics;
    }

    @Data
    @Builder
    public static class ChartSeries {
        private String key;
        private String label;
        private String color;
        private String unit;
        private List<Number> values;
    }

    @Data
    @Builder
    public static class ChartData {
        private String title;
        private String type;
        private List<String> labels;
        private List<Number> values;
        private List<ChartSeries> series;
        private String svg;
        private List<String> svgPages;
        private int totalPoints;
        private String note;
        private String unit;
        private boolean stacked;
        private String valueKey;

        private double maxValue; // ✅ 추가 (0이면 막대 0%)
    }
}