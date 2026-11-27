package com.dev.HiddenBATHAuto.dto.analytics;
import java.util.List;
import lombok.Data;
import lombok.Builder;
@Data @Builder
public class AsAnalyticsResponse {
 private AnalyticsQueryResponse bundle;
 private List<String> insights;
 @Data public static class DetailsRequest {
  private AsAnalyticsRequest query;
  private List<String> groupValues=List.of();
  private String period;
  private int page;
  private int size=50;
 }
 @Data @Builder public static class DetailRow {
  private Long id;private String date;private String company;private String category;private String reason;
  private String subject;private String product;private String status;private Integer price;private String charge;private String payment;
 }
 @Data @Builder public static class Details {
  private List<DetailRow> rows;private long totalRows;private int page;private int size;private java.util.Map<String,Long> totals;
 }
}
