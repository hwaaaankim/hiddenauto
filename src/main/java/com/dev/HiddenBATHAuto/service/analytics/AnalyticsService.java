package com.dev.HiddenBATHAuto.service.analytics;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dev.HiddenBATHAuto.dto.analytics.AnalyticsQueryRequest;
import com.dev.HiddenBATHAuto.dto.analytics.AnalyticsQueryResponse;
import com.dev.HiddenBATHAuto.dto.analytics.ProductOptionNormalized;
import com.dev.HiddenBATHAuto.model.auth.Company;
import com.dev.HiddenBATHAuto.model.task.Order;
import com.dev.HiddenBATHAuto.model.task.OrderItem;
import com.dev.HiddenBATHAuto.model.task.Task;
import com.dev.HiddenBATHAuto.repository.analytics.AnalyticsTaskRepository;
import com.dev.HiddenBATHAuto.utils.ProductOptionJsonParser;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnalyticsService {

    private final AnalyticsTaskRepository analyticsTaskRepository;
    private final ProductOptionJsonParser optionJsonParser;

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public AnalyticsQueryResponse query(AnalyticsQueryRequest req) {

        // =========================
        // 1) 기간 처리 (기존 로직 유지 + 안정화)
        // =========================
        LocalDate fromDate = req.getFromDate();
        LocalDate toDate = req.getToDate();

        LocalDateTime from = (fromDate == null)
                ? LocalDate.of(2000, 1, 1).atStartOfDay()
                : fromDate.atStartOfDay();

        LocalDateTime toExclusive = (toDate == null)
                ? LocalDate.of(2999, 12, 31).plusDays(1).atStartOfDay()
                : toDate.plusDays(1).atStartOfDay();

        if (from.isAfter(toExclusive)) {
            LocalDateTime tmp = from;
            from = toExclusive.minusDays(1);
            toExclusive = tmp.plusDays(1);
        }

        // =========================
        // 2) 원천 데이터 조회
        // =========================
        List<Task> tasks = analyticsTaskRepository.findAllForAnalytics(from, toExclusive);

        // 시간대 필터(요청 기준: Task.createdAt)
        tasks = filterByHourRanges(tasks, req.getHourRanges());

        // 주소 필터(Company 주소 기준) - COMPANY 모드일 때만
        if (req.getPrimaryY() == AnalyticsQueryRequest.PrimaryY.COMPANY && req.getAddressFilter() != null) {
            tasks = filterByCompanyAddress(tasks, req.getAddressFilter());
        }

        // =========================
        // 3) rows 생성 + columns 생성
        // =========================
        List<Map<String, Object>> fullRows;
        List<AnalyticsQueryResponse.ColumnMeta> columns;

        if (req.getPrimaryY() == AnalyticsQueryRequest.PrimaryY.COMPANY) {
            fullRows = buildCompanyRows(tasks, req);
            columns = companyColumns(req);
        } else {
            ProductBuildResult pr = buildProductRows(tasks, req);
            fullRows = pr.rows;
            columns = productColumns(req);
        }

        // =========================
        // 4) 정렬
        // =========================
        sortRows(fullRows, req.getSortKey(), req.getSortDir());

        long total = fullRows.size();

        // =========================
        // 5) 페이징
        // =========================
        int fromIdx = Math.min(req.getPage() * req.getSize(), fullRows.size());
        int toIdx = Math.min(fromIdx + req.getSize(), fullRows.size());
        List<Map<String, Object>> pageRows = fullRows.subList(fromIdx, toIdx);

        // “전체 정렬 기준 순위”
        for (int i = 0; i < pageRows.size(); i++) {
            pageRows.get(i).put("rank", fromIdx + i + 1);
        }

        // =========================
        // 6) meta/summary/chart 구성
        // =========================
        AnalyticsQueryResponse.Meta meta = buildMeta(req);
        AnalyticsQueryResponse.Summary summary = buildSummary(tasks, req);
        String chartKey = resolveChartValueKey(req);
        AnalyticsQueryResponse.ChartData chart = buildSimpleChart(fullRows, req, chartKey, 10);


        return AnalyticsQueryResponse.builder()
                .columns(columnsWithRankFirst(columns))
                .rows(pageRows)
                .totalRows(total)
                .meta(meta)
                .summary(summary)
                .chart(chart)
                .build();
    }

    // export는 페이징 없이 전체 rows 반환 + meta/summary/columns/chart 포함
    public AnalyticsQueryResponse queryForExportBundle(AnalyticsQueryRequest req) {
        AnalyticsQueryRequest copy = copyForExport(req);
        // query()는 pageRows만 내려주므로, export는 전체 rows를 직접 구성
        // => query() 내부 로직을 재사용하되 size를 MAX로 잡아 전체 rows를 받는 방식 유지
        copy.setPage(0);
        copy.setSize(Integer.MAX_VALUE);

        AnalyticsQueryResponse res = query(copy);

        // res.rows는 사실상 전체가 되지만, 안전하게 totalRows 만큼 있는지 신뢰하고 그대로 사용
        return res;
    }

    private AnalyticsQueryRequest copyForExport(AnalyticsQueryRequest req) {
        AnalyticsQueryRequest copy = new AnalyticsQueryRequest();
        copy.setFromDate(req.getFromDate());
        copy.setToDate(req.getToDate());
        copy.setPrimaryY(req.getPrimaryY());
        copy.setAddressFilter(req.getAddressFilter());
        copy.setHourRanges(req.getHourRanges());
        copy.setProductBreakdown(req.getProductBreakdown());
        copy.setMetrics(req.getMetrics());
        copy.setSortKey(req.getSortKey());
        copy.setSortDir(req.getSortDir());
        copy.setPage(0);
        copy.setSize(Integer.MAX_VALUE);
        return copy;
    }

    // --------------------
    // Filters
    // --------------------
    private List<Task> filterByHourRanges(List<Task> tasks, List<AnalyticsQueryRequest.HourRange> ranges) {
        if (ranges == null || ranges.isEmpty()) return tasks;

        return tasks.stream().filter(t -> {
            int hour = t.getCreatedAt().getHour();
            for (AnalyticsQueryRequest.HourRange r : ranges) {
                if (inHourRange(hour, r.getStartHour(), r.getEndHour())) return true;
            }
            return false;
        }).toList();
    }

    private boolean inHourRange(int hour, int start, int endInclusive) {
        if (start <= endInclusive) return hour >= start && hour <= endInclusive;
        return hour >= start || hour <= endInclusive; // wrap 지원
    }

    private List<Task> filterByCompanyAddress(List<Task> tasks, AnalyticsQueryRequest.AddressFilter f) {
        return tasks.stream().filter(t -> {
            Company c = (t.getRequestedBy() != null) ? t.getRequestedBy().getCompany() : null;
            if (c == null) return false;

            // ✅ Province 비교: "경기" = "경기도" 등 허용
            if (f.getProvince() != null) {
                String reqProv = normalizeProvinceName(f.getProvince());
                String compProv = normalizeProvinceName(c.getDoName());
                if (!reqProv.equals(compProv)) return false;
            }

            if (f.getCity() != null) {
                if (c.getSiName() == null) return false;
                if (!f.getCity().equals(c.getSiName())) return false;
            }

            if (f.getDistrict() != null) {
                if (c.getGuName() == null) return false;
                if (!f.getDistrict().equals(c.getGuName())) return false;
            }

            return true;
        }).toList();
    }

    /**
     * ✅ "경기" == "경기도", "강원" == "강원도" 등을 동일하게 맞추기 위한 정규화
     * - 기본 전략: 끝의 "도" 제거, 공백 제거
     * - 특별시/광역시 등은 "시/특별시/광역시/자치시/자치도" 표기를 단순화
     * - DB/입력 어느 쪽이 약칭/정식명이어도 매칭되도록 통일
     */
    private String normalizeProvinceName(String s) {
        if (s == null) return "";
        String v = s.trim();
        if (v.isEmpty()) return "";

        v = v.replace(" ", "");

        // 흔한 접미어 정리
        // "경기도" -> "경기", "강원도" -> "강원", ...
        if (v.endsWith("도")) v = v.substring(0, v.length() - 1);

        // "서울특별시" -> "서울", "부산광역시" -> "부산", "세종특별자치시" -> "세종"
        v = v.replace("특별자치시", "");
        v = v.replace("특별자치도", "");
        v = v.replace("특별시", "");
        v = v.replace("광역시", "");
        v = v.replace("자치시", "");
        v = v.replace("자치도", "");
        v = v.replace("시", ""); // 마지막까지 남은 "시" 제거(예: "서울시" 형태 대비)

        return v;
    }


 // --------------------
 // Rows builders
 // --------------------
 private List<Map<String, Object>> buildCompanyRows(List<Task> tasks, AnalyticsQueryRequest req) {

     Map<Long, List<Task>> byCompany = tasks.stream()
             .filter(t -> t.getRequestedBy() != null && t.getRequestedBy().getCompany() != null)
             .collect(Collectors.groupingBy(t -> t.getRequestedBy().getCompany().getId()));

     List<Map<String, Object>> rows = new ArrayList<>();

     for (var e : byCompany.entrySet()) {
         List<Task> companyTasks = e.getValue();
         Company company = companyTasks.get(0).getRequestedBy().getCompany();

         Map<String, Object> row = new LinkedHashMap<>();
         row.put("rank", 0); // placeholder
         row.put("companyName", company.getCompanyName());
         row.put("ownerName", companyTasks.get(0).getRequestedBy().getName());

         boolean includeAddressColumn = req.getAddressFilter() != null;
         if (includeAddressColumn) {
             row.put("address", buildCompanyAddress(company));
         }

         int sales = companyTasks.stream().mapToInt(Task::getTotalPrice).sum();
         long taskCount = companyTasks.size();
         int qty = companyTasks.stream()
                 .flatMap(t -> safeOrders(t).stream())
                 .mapToInt(Order::getQuantity)
                 .sum();

         /**
          * ✅ 핵심 안정화:
          * - row에는 차트/정렬을 위해 원본 값(sales/taskCount/qty)을 항상 넣습니다.
          * - 화면/엑셀/A4에서 어떤 컬럼을 보여줄지는 columns 메타(companyColumns)에서만 제어합니다.
          * - 이렇게 하면 metrics에 SALES가 빠져도 chart(valueKey="sales")가 0%가 되는 문제가 사라집니다.
          */
         row.put("sales", sales);
         row.put("taskCount", taskCount);
         row.put("qty", qty);

         rows.add(row);
     }

     return rows;
 }

    private String buildCompanyAddress(Company c) {
        List<String> parts = new ArrayList<>();
        if (c.getDoName() != null) parts.add(c.getDoName());
        if (c.getSiName() != null) parts.add(c.getSiName());
        if (c.getGuName() != null) parts.add(c.getGuName());
        return String.join(" ", parts);
    }

    private static class ProductBuildResult {
        List<Map<String, Object>> rows;
    }

    private ProductBuildResult buildProductRows(List<Task> tasks, AnalyticsQueryRequest req) {

        List<OrderRowAtom> atoms = buildAtoms(tasks);

        AnalyticsQueryRequest.ProductBreakdown pb = req.getProductBreakdown();
        if (pb == null) {
            pb = new AnalyticsQueryRequest.ProductBreakdown();
            pb.setIncludeStandard(true);
            pb.setIncludeNonStandard(true);
            pb.setStandardLevel("CATEGORY");
            pb.setNonStandardLevel("SORT");
        }

        final boolean includeStd = pb.isIncludeStandard();
        final boolean includeNon = pb.isIncludeNonStandard();

        List<OrderRowAtom> filtered = atoms.stream().filter(a -> {
            if (a.standard && !includeStd) return false;
            if (!a.standard && !includeNon) return false;
            return true;
        }).toList();

        final String stdLevel = pb.getStandardLevel();
        final String nonLevel = pb.getNonStandardLevel();

        Function<OrderRowAtom, String> groupKeyFn = a -> {
            if (a.standard) return "STD|" + keyByStandardLevel(a.norm, stdLevel);
            return "NON|" + keyByNonStandardLevel(a.norm, nonLevel);
        };

        Map<String, List<OrderRowAtom>> grouped = filtered.stream()
                .collect(Collectors.groupingBy(groupKeyFn));

        List<Map<String, Object>> rows = new ArrayList<>();

        for (var e : grouped.entrySet()) {
            List<OrderRowAtom> list = e.getValue();
            boolean standard = list.get(0).standard;

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("rank", 0);
            row.put("type", standard ? "규격" : "비규격");

            String label = standard
                    ? labelByStandardLevel(list.get(0).norm, pb.getStandardLevel())
                    : labelByNonStandardLevel(list.get(0).norm, pb.getNonStandardLevel());

            row.put("label", label);

            int sales = list.stream().mapToInt(a -> a.sales).sum();
            long taskCount = list.stream().mapToLong(a -> a.taskCount).sum();
            int qty = list.stream().mapToInt(a -> a.qty).sum();

            row.put("sales", sales);
            row.put("taskCount", taskCount);
            row.put("qty", qty);

            rows.add(row);
        }

        ProductBuildResult res = new ProductBuildResult();
        res.rows = rows;
        return res;
    }

    private List<OrderRowAtom> buildAtoms(List<Task> tasks) {
        List<OrderRowAtom> atoms = new ArrayList<>();

        for (Task t : tasks) {
            for (Order o : safeOrders(t)) {
                OrderItem oi = o.getOrderItem();
                String optionJson = (oi != null) ? oi.getOptionJson() : null;

                ProductOptionNormalized norm = optionJsonParser.parse(o.isStandard(), optionJson);

                atoms.add(OrderRowAtom.builder()
                        .taskId(t.getId())
                        .createdAt(t.getCreatedAt())
                        .sales(t.getTotalPrice())
                        .taskCount(1)
                        .qty(o.getQuantity())
                        .standard(o.isStandard())
                        .norm(norm)
                        .build());
            }
        }

        return atoms;
    }

    private String keyByStandardLevel(ProductOptionNormalized n, String level) {
        return switch (safe(level)) {
            case "CATEGORY" -> safe(n.getTop());
            case "SERIES" -> safe(n.getMid());
            case "PRODUCT" -> safe(n.getProduct());
            case "COLOR" -> safe(n.getColor());
            default -> safe(n.getTop());
        };
    }

    private String labelByStandardLevel(ProductOptionNormalized n, String level) {
        return keyByStandardLevel(n, level);
    }

    private String keyByNonStandardLevel(ProductOptionNormalized n, String level) {
        return switch (safe(level)) {
            case "SORT" -> safe(n.getTop());
            case "SERIES" -> safe(n.getMid());
            case "PRODUCT" -> safe(n.getProduct());
            case "COLOR" -> safe(n.getColor());
            default -> safe(n.getTop());
        };
    }

    private String labelByNonStandardLevel(ProductOptionNormalized n, String level) {
        return keyByNonStandardLevel(n, level);
    }

    private String safe(String s) {
        return (s == null || s.isBlank()) ? "-" : s;
    }

    private List<Order> safeOrders(Task t) {
        return (t.getOrders() == null) ? List.of() : t.getOrders();
    }

    // --------------------
    // Sorting
    // --------------------
    private void sortRows(List<Map<String, Object>> rows, String sortKey, String sortDir) {
        if (sortKey == null || sortKey.isBlank()) return;

        Comparator<Map<String, Object>> cmp = Comparator.comparing(m -> {
            Object v = m.get(sortKey);
            if (v == null) return 0d;
            if (v instanceof Number) return ((Number) v).doubleValue();
            return 0d;
        });

        // 숫자 아닌 텍스트 정렬은 별도로(회사명 등)
        if (!rows.isEmpty() && !(rows.get(0).get(sortKey) instanceof Number)) {
            cmp = Comparator.comparing(m -> String.valueOf(m.get(sortKey) == null ? "" : m.get(sortKey)));
        }

        if ("desc".equalsIgnoreCase(sortDir)) cmp = cmp.reversed();
        rows.sort(cmp);
    }

    // --------------------
    // Columns meta
    // --------------------
    private List<AnalyticsQueryResponse.ColumnMeta> columnsWithRankFirst(List<AnalyticsQueryResponse.ColumnMeta> cols) {
        // rank가 헤더 첫 번째로 오도록 강제
        List<AnalyticsQueryResponse.ColumnMeta> out = new ArrayList<>();
        out.add(AnalyticsQueryResponse.ColumnMeta.builder()
                .key("rank")
                .label("순위")
                .help("현재 정렬 기준으로 계산된 전체 순위입니다.")
                .build());
        for (AnalyticsQueryResponse.ColumnMeta c : cols) {
            if ("rank".equals(c.getKey())) continue;
            out.add(c);
        }
        return out;
    }

    private List<AnalyticsQueryResponse.ColumnMeta> companyColumns(AnalyticsQueryRequest req) {
        List<AnalyticsQueryResponse.ColumnMeta> cols = new ArrayList<>();

        cols.add(cm("companyName", "업체명", "업체별로 집계한 기준 업체명입니다."));
        cols.add(cm("ownerName", "담당/대표", "업체의 대표자(요청자) 기준으로 표시됩니다."));

        if (req.getAddressFilter() != null) {
            cols.add(cm("address", "지역(도/시/구)", "업체 주소의 도/시/구를 결합한 값입니다."));
        }

        Set<String> metrics = new HashSet<>(Optional.ofNullable(req.getMetrics()).orElse(List.of()));
        if (metrics.isEmpty()) metrics = new HashSet<>(List.of("SALES", "TASK_COUNT", "QTY"));

        if (metrics.contains("TASK_COUNT")) cols.add(cm("taskCount", "주문건수", "Task(업무) 건수 기준입니다."));
        if (metrics.contains("QTY")) cols.add(cm("qty", "주문수량", "Order.quantity 합계 기준입니다."));
        if (metrics.contains("SALES")) cols.add(cm("sales", "매출액", "Task.totalPrice 합계 기준입니다."));

        return cols;
    }

    private List<AnalyticsQueryResponse.ColumnMeta> productColumns(AnalyticsQueryRequest req) {
        return List.of(
                cm("type", "구분", "규격/비규격 구분입니다."),
                cm("label", "제품 기준", "선택한 분류 레벨(카테고리/시리즈/제품/색상)로 집계된 값입니다."),
                cm("sales", "매출액", "Task.totalPrice 합계 기준입니다."),
                cm("taskCount", "주문건수", "Task(업무) 건수 기준입니다."),
                cm("qty", "주문수량", "Order.quantity 합계 기준입니다.")
        );
    }

    private AnalyticsQueryResponse.ColumnMeta cm(String key, String label, String help) {
        return AnalyticsQueryResponse.ColumnMeta.builder()
                .key(key)
                .label(label)
                .help(help)
                .build();
    }

    // --------------------
    // Meta / Summary / Chart
    // --------------------
    private AnalyticsQueryResponse.Meta buildMeta(AnalyticsQueryRequest req) {
        String periodText;
        if (req.getFromDate() == null && req.getToDate() == null) {
            periodText = "필터기간: 전체";
        } else if (req.getFromDate() != null && req.getToDate() != null) {
            periodText = "필터기간: " + req.getFromDate().format(DTF) + " ~ " + req.getToDate().format(DTF);
        } else if (req.getFromDate() != null) {
            periodText = "필터기간: " + req.getFromDate().format(DTF) + " ~ (미래 전체)";
        } else {
            periodText = "필터기간: (과거 전체) ~ " + req.getToDate().format(DTF);
        }

        String yAxisText = "Y축: " + (req.getPrimaryY() == AnalyticsQueryRequest.PrimaryY.COMPANY ? "업체별" : "제품별");

        String ySubText;
        if (req.getPrimaryY() != AnalyticsQueryRequest.PrimaryY.COMPANY) {
            ySubText = "Y-Sub: 없음";
        } else {
            List<String> subs = new ArrayList<>();
            if (req.getAddressFilter() != null) subs.add("주소");
            if (req.getHourRanges() != null && !req.getHourRanges().isEmpty()) subs.add("시간대");
            ySubText = "Y-Sub: " + (subs.isEmpty() ? "없음" : String.join(", ", subs));
        }

        String xAxisText;
        List<String> metrics = Optional.ofNullable(req.getMetrics()).orElse(List.of());
        if (metrics.isEmpty()) {
            xAxisText = "X축 지표: 매출액, 주문건수, 주문수량(기본)";
        } else {
            xAxisText = "X축 지표: " + metrics.stream().map(this::metricKr).collect(Collectors.joining(", "));
        }

        String xDimText;
        if (req.getPrimaryY() != AnalyticsQueryRequest.PrimaryY.PRODUCT) {
            xDimText = "제품 분류: 없음";
        } else {
            AnalyticsQueryRequest.ProductBreakdown pb = req.getProductBreakdown();
            String std = (pb == null || pb.getStandardLevel() == null) ? "CATEGORY" : pb.getStandardLevel();
            String non = (pb == null || pb.getNonStandardLevel() == null) ? "SORT" : pb.getNonStandardLevel();
            xDimText = "제품 분류: 규격(" + std + "), 비규격(" + non + ")";
        }

        String addressText;
        if (req.getAddressFilter() == null) {
            addressText = "주소필터: 전체";
        } else {
            String p = req.getAddressFilter().getProvince();
            String c = req.getAddressFilter().getCity();
            String d = req.getAddressFilter().getDistrict();
            String v = String.join(" ", Arrays.asList(nz(p), nz(c), nz(d)).stream().filter(s -> !s.isBlank()).toList());
            addressText = "주소필터: " + (v.isBlank() ? "전체" : v);
        }

        String hourText;
        if (req.getHourRanges() == null || req.getHourRanges().isEmpty()) {
            hourText = "시간대: 전체";
        } else {
            String v = req.getHourRanges().stream()
                    .map(r -> r.getStartHour() + "-" + r.getEndHour())
                    .collect(Collectors.joining(", "));
            hourText = "시간대: " + v;
        }

        String sortText;
        if (req.getSortKey() == null || req.getSortKey().isBlank()) {
            sortText = "정렬: 없음";
        } else {
            String keyKr = sortKeyKr(req.getSortKey());
            String dirKr = "asc".equalsIgnoreCase(req.getSortDir()) ? "오름차순" : "내림차순";
            sortText = "정렬: " + keyKr + " " + dirKr;
        }

        return AnalyticsQueryResponse.Meta.builder()
                .periodText(periodText)
                .yAxisText(yAxisText)
                .ySubText(ySubText)
                .xAxisText(xAxisText)
                .xDimText(xDimText)
                .addressText(addressText)
                .hourText(hourText)
                .sortText(sortText)
                .build();
    }

    private String nz(String s) { return s == null ? "" : s.trim(); }

    private String metricKr(String m) {
        return switch (m) {
            case "SALES" -> "매출액";
            case "TASK_COUNT" -> "주문건수";
            case "QTY" -> "주문수량";
            default -> m;
        };
    }

    private String sortKeyKr(String key) {
        return switch (key) {
            case "sales" -> "매출액";
            case "taskCount" -> "주문건수";
            case "qty" -> "주문수량";
            case "companyName" -> "업체명";
            case "ownerName" -> "담당/대표";
            case "address" -> "지역";
            case "label" -> "제품 기준";
            case "type" -> "구분";
            case "rank" -> "순위";
            default -> key;
        };
    }

    private AnalyticsQueryResponse.Summary buildSummary(List<Task> tasks, AnalyticsQueryRequest req) {
        if (tasks == null || tasks.isEmpty()) return AnalyticsQueryResponse.Summary.builder().build();

        if (req.getPrimaryY() == AnalyticsQueryRequest.PrimaryY.COMPANY) {
            List<AnalyticsQueryResponse.RankItem> topCompanies = buildTopCompanies(tasks);
            List<AnalyticsQueryResponse.RankItem> topRegions = buildTopRegions(tasks);

            return AnalyticsQueryResponse.Summary.builder()
                    .topCompanies(topCompanies)
                    .topRegions(topRegions)
                    .build();
        }

        // PRODUCT 모드: atoms 기반으로 top(카테고리/시리즈/제품)
        List<OrderRowAtom> atoms = buildAtoms(tasks);
        List<AnalyticsQueryResponse.RankItem> topCategories = buildTopBy(atoms, a -> safe(a.norm.getTop()), "카테고리");
        List<AnalyticsQueryResponse.RankItem> topSeries = buildTopBy(atoms, a -> safe(a.norm.getMid()), "시리즈");
        List<AnalyticsQueryResponse.RankItem> topProducts = buildTopBy(atoms, a -> safe(a.norm.getProduct()), "제품");

        return AnalyticsQueryResponse.Summary.builder()
                .topCategories(topCategories)
                .topSeries(topSeries)
                .topProducts(topProducts)
                .build();
    }

    private List<AnalyticsQueryResponse.RankItem> buildTopCompanies(List<Task> tasks) {
        Map<String, List<Task>> byCompanyName = tasks.stream()
                .filter(t -> t.getRequestedBy() != null && t.getRequestedBy().getCompany() != null)
                .collect(Collectors.groupingBy(t -> t.getRequestedBy().getCompany().getCompanyName()));

        List<AnalyticsQueryResponse.RankItem> items = byCompanyName.entrySet().stream().map(e -> {
            int sales = e.getValue().stream().mapToInt(Task::getTotalPrice).sum();
            long taskCount = e.getValue().size();
            int qty = e.getValue().stream()
                    .flatMap(t -> safeOrders(t).stream())
                    .mapToInt(Order::getQuantity)
                    .sum();
            return AnalyticsQueryResponse.RankItem.builder()
                    .name(e.getKey() == null ? "-" : e.getKey())
                    .sales(sales).taskCount(taskCount).qty(qty)
                    .build();
        }).sorted(Comparator.comparingInt(AnalyticsQueryResponse.RankItem::getSales).reversed())
          .limit(5)
          .toList();

        return withRank(items);
    }

    private List<AnalyticsQueryResponse.RankItem> buildTopRegions(List<Task> tasks) {
        Map<String, List<Task>> byRegion = tasks.stream()
                .filter(t -> t.getRequestedBy() != null && t.getRequestedBy().getCompany() != null)
                .collect(Collectors.groupingBy(t -> buildCompanyAddress(t.getRequestedBy().getCompany())));

        List<AnalyticsQueryResponse.RankItem> items = byRegion.entrySet().stream().map(e -> {
            int sales = e.getValue().stream().mapToInt(Task::getTotalPrice).sum();
            long taskCount = e.getValue().size();
            int qty = e.getValue().stream()
                    .flatMap(t -> safeOrders(t).stream())
                    .mapToInt(Order::getQuantity)
                    .sum();
            return AnalyticsQueryResponse.RankItem.builder()
                    .name((e.getKey() == null || e.getKey().isBlank()) ? "-" : e.getKey())
                    .sales(sales).taskCount(taskCount).qty(qty)
                    .build();
        }).sorted(Comparator.comparingInt(AnalyticsQueryResponse.RankItem::getSales).reversed())
          .limit(5)
          .toList();

        return withRank(items);
    }

    private List<AnalyticsQueryResponse.RankItem> buildTopBy(List<OrderRowAtom> atoms, Function<OrderRowAtom, String> keyFn, String title) {
        Map<String, List<OrderRowAtom>> grouped = atoms.stream()
                .collect(Collectors.groupingBy(keyFn));

        List<AnalyticsQueryResponse.RankItem> items = grouped.entrySet().stream().map(e -> {
            int sales = e.getValue().stream().mapToInt(a -> a.sales).sum();
            long taskCount = e.getValue().stream().mapToLong(a -> a.taskCount).sum();
            int qty = e.getValue().stream().mapToInt(a -> a.qty).sum();
            return AnalyticsQueryResponse.RankItem.builder()
                    .name((e.getKey() == null || e.getKey().isBlank()) ? "-" : e.getKey())
                    .sales(sales).taskCount(taskCount).qty(qty)
                    .build();
        }).sorted(Comparator.comparingInt(AnalyticsQueryResponse.RankItem::getSales).reversed())
          .limit(5)
          .toList();

        return withRank(items);
    }

    private List<AnalyticsQueryResponse.RankItem> withRank(List<AnalyticsQueryResponse.RankItem> items) {
        List<AnalyticsQueryResponse.RankItem> out = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            AnalyticsQueryResponse.RankItem it = items.get(i);
            out.add(AnalyticsQueryResponse.RankItem.builder()
                    .rank(i + 1)
                    .name(it.getName())
                    .sales(it.getSales())
                    .taskCount(it.getTaskCount())
                    .qty(it.getQty())
                    .build());
        }
        return out;
    }

    private AnalyticsQueryResponse.ChartData buildSimpleChart(
            List<Map<String, Object>> fullRows,
            AnalyticsQueryRequest req,
            String valueKey,
            int topN
    ) {
        if (fullRows == null || fullRows.isEmpty()) return null;

        // ✅ 상위 N개 (정렬은 이미 fullRows가 sortRows()를 거친 뒤라고 가정)
        List<Map<String, Object>> top = fullRows.stream()
                .limit(topN)
                .toList();

        // ✅ 라벨
        List<String> labels = top.stream()
                .map(r -> {
                    if (req.getPrimaryY() == AnalyticsQueryRequest.PrimaryY.COMPANY) {
                        return String.valueOf(r.getOrDefault("companyName", "-"));
                    }
                    return String.valueOf(r.getOrDefault("label", "-"));
                })
                .toList();

        // ✅ 값
        List<Number> values = top.stream()
                .map(r -> {
                    Object v = r.get(valueKey);
                    if (v instanceof Number) return (Number) v;
                    return 0;
                })
                .toList();

        // ✅ 최대값(템플릿에서 max 계산하지 않도록 서버에서 계산)
        double maxValue = values.stream()
                .filter(v -> v != null)
                .mapToDouble(Number::doubleValue)
                .max()
                .orElse(0d);

        // ✅ 제목: valueKey에 맞춰 자동으로
        String metricKr = switch (valueKey) {
            case "taskCount" -> "주문건수";
            case "qty" -> "주문수량";
            case "sales" -> "매출";
            default -> valueKey;
        };

        String title = (req.getPrimaryY() == AnalyticsQueryRequest.PrimaryY.COMPANY)
                ? "상위 10개 업체 " + metricKr
                : "상위 10개 제품 기준 " + metricKr;

        return AnalyticsQueryResponse.ChartData.builder()
                .title(title)
                .type("bar")
                .labels(labels)
                .values(values)
                .valueKey(valueKey)
                .maxValue(maxValue) // ✅ 추가
                .build();
    }


    private String resolveChartValueKey(AnalyticsQueryRequest req) {
        List<String> metrics = Optional.ofNullable(req.getMetrics()).orElse(List.of());
        if (metrics == null || metrics.isEmpty()) return "sales";

        // 우선순위: SALES > TASK_COUNT > QTY
        if (metrics.contains("SALES")) return "sales";
        if (metrics.contains("TASK_COUNT")) return "taskCount";
        if (metrics.contains("QTY")) return "qty";

        return "sales";
    }
    // --------------------
    // Atom
    // --------------------
    @lombok.Data
    @lombok.Builder
    private static class OrderRowAtom {
        private Long taskId;
        private LocalDateTime createdAt;

        private int sales;
        private int taskCount;
        private int qty;

        private boolean standard;
        private ProductOptionNormalized norm;
    }
}