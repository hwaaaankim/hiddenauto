package com.dev.HiddenBATHAuto.dto.analytics;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
public final class AnalyticsDetailsDtos {
 private AnalyticsDetailsDtos() {}
 @Data @Builder @NoArgsConstructor @AllArgsConstructor
 public static class Scope {
  private String mode; // ALL/COMPANY/PRODUCT/PERIOD
  private Long companyId;
  private Boolean standard;
  private List<String> path;
  private String period;
 }
 @Data public static class Request {
  private AnalyticsQueryRequest query;
  private Scope scope;
  private String kind="ALL";
  private String keyword;
  private String day;
  private String sortKey="date";
  private String sortDir="desc";
  private int page;
  private int size=50;
 }
 @Data @Builder public static class Row {
  private Long orderId;
  private String date;
  private String dateTime;
  private Long companyId;
  private String companyName;
  private String type;
  private String category;
  private String series;
  private String product;
  private String color;
  private String size;
  private String kind;
  private String kindCode;
  private long quantity;
  private long amount;
  private String status;
 }
 @Data @Builder public static class Day {
  private String date;
  private long count;
 }
 @Data @Builder public static class Response {
  private List<Row> rows;
  private List<Day> days;
  private long totalRows;
  private Map<String,Long> totals;
  private int page;
  private int size;
 }
}
