package com.dev.HiddenBATHAuto.controller.page;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.dev.HiddenBATHAuto.dto.analytics.AnalyticsQueryRequest;
import com.dev.HiddenBATHAuto.dto.analytics.AnalyticsQueryResponse;
import com.dev.HiddenBATHAuto.service.analytics.AnalyticsExcelExporter;
import com.dev.HiddenBATHAuto.service.analytics.AnalyticsService;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
@RequestMapping("/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final AnalyticsExcelExporter excelExporter;

    @GetMapping
    public String analyticsPage() {
        return "administration/analytics/analytics";
    }

    @PostMapping("/api/query")
    @ResponseBody
    public AnalyticsQueryResponse query(@RequestBody AnalyticsQueryRequest req) {
        return analyticsService.query(req);
    }

    @PostMapping("/api/export/excel")
    public ResponseEntity<byte[]> exportExcel(@RequestBody AnalyticsQueryRequest req) {

        AnalyticsQueryResponse bundle = analyticsService.queryForExportBundle(req);

        byte[] bytes = excelExporter.export(
                "Analytics Export",
                bundle.getMeta(),
                bundle.getSummary(),
                bundle.getColumns(),
                bundle.getChart(),
                bundle.getRows()
        );

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"analytics.xlsx\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bytes);
    }

    // A4 보고서: 요약+그래프+데이터 테이블
    @PostMapping("/api/export/a4")
    public String exportA4(@RequestBody AnalyticsQueryRequest req, Model model) {
        AnalyticsQueryResponse bundle = analyticsService.queryForExportBundle(req);

        model.addAttribute("bundle", bundle);
        return "administration/analytics/analyticsA4";
    }
}