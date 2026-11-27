package com.dev.HiddenBATHAuto.dto.analytics;
import java.time.LocalDate;
import java.util.List;
import lombok.Data;
@Data
public class AsAnalyticsRequest {
    private LocalDate fromDate;
    private LocalDate toDate;
    private String primaryY="COMPANY";
    private List<String> dimensions=List.of();
    private String timeGrouping="NONE";
    private String companyKeyword="";
    private String category="ALL";
    private String reason="ALL";
    private String charge="ALL";
    private String payment="ALL";
    private List<String> statuses=List.of("REQUESTED","IN_PROGRESS","COMPLETED");
    private List<String> metrics=List.of("AS_COUNT","COMPLETED_COUNT","PENDING_COUNT","COST","UNCOLLECTED_COST");
    private List<String> chartMetrics=List.of("AS_COUNT");
    private String chartType="BAR";
    private int chartLimit=10;
    private String sortKey="asCount";
    private String sortDir="desc";
    private int page=0;
    private int size=50;
}
