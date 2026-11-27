package com.dev.HiddenBATHAuto.service.analytics;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.DayOfWeek;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.TreeMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.dev.HiddenBATHAuto.dto.analytics.AnalyticsQueryRequest;
import com.dev.HiddenBATHAuto.dto.analytics.AnalyticsMetric;
import com.dev.HiddenBATHAuto.dto.analytics.AnalyticsDetailsDtos;
import com.dev.HiddenBATHAuto.dto.analytics.AnalyticsQueryResponse;
import com.dev.HiddenBATHAuto.dto.analytics.ProductOptionNormalized;
import com.dev.HiddenBATHAuto.model.auth.Company;
import com.dev.HiddenBATHAuto.model.task.Order;
import com.dev.HiddenBATHAuto.model.task.OrderItem;
import com.dev.HiddenBATHAuto.model.task.OrderStatus;
import com.dev.HiddenBATHAuto.repository.analytics.AnalyticsOrderRepository;
import com.dev.HiddenBATHAuto.utils.KoreanAdministrativeRegionNormalizer;
import com.dev.HiddenBATHAuto.utils.ProductOptionJsonParser;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnalyticsService {
    private final AnalyticsOrderRepository analyticsOrderRepository;
    private final ProductOptionJsonParser optionJsonParser;
    private static final List<String> DEFAULT_METRICS = List.of("SALES", "SUPPLY_AMOUNT", "VAT_AMOUNT", "TASK_COUNT", "QTY");
    private static final String BASIS = "발주일 기준 · 개별 품목 1개 = 주문 1건 · 공급가액 + 부가세 = 상품 합계 · 배송·포장비 제외 · 취소 제외 · 0원 반품용 별도 · 회수 금액은 저장값 유지";

    public AnalyticsQueryResponse query(AnalyticsQueryRequest request) {
        return queryInternal(request, false);
    }

    public AnalyticsQueryResponse queryForExportBundle(AnalyticsQueryRequest request) {
        return queryInternal(request, true);
    }

    private AnalyticsQueryResponse queryInternal(AnalyticsQueryRequest input, boolean export) {
        AnalyticsQueryRequest req = normalize(input, export);
        List<Atom> atoms = loadAtoms(req);
        List<Map<String, Object>> rows = !"NONE".equals(req.getDeliveryView()) ? deliveryRows(atoms, req) : !"NONE".equals(req.getTimeGrouping()) ? timeRows(atoms, req) : req.getPrimaryY() == AnalyticsQueryRequest.PrimaryY.COMPANY
                ? companyRows(atoms, req) : productRows(atoms, req);
        sortRows(rows, req);
        for (int i = 0; i < rows.size(); i++) rows.get(i).put("rank", i + 1);
        int start = export ? 0 : (int) Math.min((long) req.getPage() * req.getSize(), rows.size());
        int end = export ? rows.size() : Math.min(start + req.getSize(), rows.size());
        Totals total = totals(atoms);
        long missingCategory = atoms.stream().filter(a -> text(a.norm.getTop()).isEmpty()).count();
        List<String> warnings = new ArrayList<>();
        if (total.values.get("adjustmentOrderCount") > 0) warnings.add("기타 조정 품목 " + total.values.get("adjustmentOrderCount")
                + "건이 있습니다. 수량 0 또는 양수 수량·음수 금액이므로 별도 조정으로 표시하고 저장 금액은 합계에 포함했습니다.");
        if (req.getPrimaryY() == AnalyticsQueryRequest.PrimaryY.PRODUCT && missingCategory > 0)
            warnings.add("분류 정보가 없는 품목 " + missingCategory + "건도 ‘분류 정보 없음’에 포함하여 집계했습니다.");
        return AnalyticsQueryResponse.builder()
                .columns(columns(req)).rows(new ArrayList<>(rows.subList(start, end))).totalRows(rows.size())
                .meta(meta(req)).summary(summary(atoms, req)).chart(chart(rows, req))
                .totals(AnalyticsQueryResponse.Totals.builder().sales(total.sales).taskCount(total.count).qty(total.qty)
                        .metrics(total.values).build())
                .warnings(warnings).build();
    }

    private List<Atom> loadAtoms(AnalyticsQueryRequest req) {
        LocalDateTime from = req.getFromDate() == null ? null : req.getFromDate().atStartOfDay();
        LocalDateTime to = req.getToDate() == null ? null : req.getToDate().plusDays(1).atStartOfDay();
        List<Order> source = analyticsOrderRepository.findAllForAnalytics(from, to, OrderStatus.CANCELED);
        Map<Long, Order> unique = new LinkedHashMap<>();
        for (Order order : source) {
            if (order != null && order.getId() != null) unique.putIfAbsent(order.getId(), order);
        }
        List<Atom> atoms = new ArrayList<>();
        for (Order order : unique.values()) {
            LocalDateTime date = order.getCreatedAt();
            if (date == null || order.getStatus() == OrderStatus.CANCELED
                    || from != null && date.isBefore(from) || to != null && !date.isBefore(to)) continue;
            if (!matchesHours(date, req.getHourRanges())) continue;
            if (!"ALL".equals(req.getDeliveryMethod()) && !deliveryKey(order).equals(req.getDeliveryMethod())) continue;
            Company company = company(order);
            if (!matchesAddress(company, req.getAddressFilter())) continue;
            if (req.getPrimaryY() == AnalyticsQueryRequest.PrimaryY.PRODUCT) {
                var breakdown = req.getProductBreakdown();
                if (order.isStandard() ? !breakdown.isIncludeStandard() : !breakdown.isIncludeNonStandard()) continue;
            }
            OrderItem item = order.getOrderItem();
            ProductOptionNormalized norm = optionJsonParser.parse(order.isStandard(), item == null ? null : item.getOptionJson());
            if (norm.getProduct() == null && item != null && !text(item.getProductName()).isEmpty()
                    && !"제품명없음".equals(item.getProductName())) norm.setProduct(item.getProductName().trim());
            // totalAmount is already the full line amount. Multiplying it by quantity again is incorrect.
            // Retain free/zero-value rows; never invent VAT or a missing historical amount.
            long sales = order.getTotalAmount();
            long supplyAmount = sales == 0 ? 0L : order.getSupplyPrice();
            Atom atom = new Atom(order, company, norm, sales, supplyAmount, Math.subtractExact(sales, supplyAmount));
            if (!req.getRecordKinds().contains(recordKind(atom))) continue;
            if (!req.getKeyword().isEmpty()) {
                String haystack = req.getPrimaryY() == AnalyticsQueryRequest.PrimaryY.COMPANY
                        ? (company == null ? "업체 정보 없음" : text(company.getCompanyName()) + " " + company.getId())
                        : String.join(" ", display(norm.getTop()), display(norm.getMid()), display(norm.getProduct()), display(norm.getColor()));
                if (!haystack.toLowerCase(Locale.ROOT).contains(req.getKeyword().toLowerCase(Locale.ROOT))) continue;
            }
            atoms.add(atom);
        }

        return atoms;
    }

    private AnalyticsQueryRequest normalize(AnalyticsQueryRequest input, boolean export) {
        if (input == null || input.getPrimaryY() == null) throw bad("업체별 또는 제품별을 먼저 선택해 주세요.");
        if (input.getFromDate() != null && input.getToDate() != null && input.getFromDate().isAfter(input.getToDate()))
            throw bad("발주 시작일은 종료일보다 늦을 수 없습니다.");
        for (LocalDate date : new LocalDate[] {input.getFromDate(), input.getToDate()})
            if (date != null && (date.getYear() < 1000 || date.getYear() > 9998)) throw bad("유효한 발주일을 입력해 주세요.");
        if (input.getPage() < 0 || !export && (input.getSize() < 1 || input.getSize() > 500))
            throw bad("조회 페이지와 페이지 크기를 확인해 주세요. 한 페이지는 1~500행입니다.");
        AnalyticsQueryRequest req = new AnalyticsQueryRequest();
        req.setFromDate(input.getFromDate()); req.setToDate(input.getToDate()); req.setPrimaryY(input.getPrimaryY());
        req.setPage(input.getPage()); req.setSize(export ? Integer.MAX_VALUE : input.getSize());
        List<String> metrics = input.getMetrics() == null || input.getMetrics().isEmpty() ? DEFAULT_METRICS : input.getMetrics();
        LinkedHashSet<String> selected = new LinkedHashSet<>();
        for (String metric : metrics) {
            try { AnalyticsMetric.valueOf(metric); } catch (IllegalArgumentException | NullPointerException e) { throw bad("지원하지 않는 지표입니다."); }
            selected.add(metric);
        }
        req.setMetrics(List.copyOf(selected));
        if (req.getPrimaryY() == AnalyticsQueryRequest.PrimaryY.COMPANY) {
            req.setAddressFilter(input.getAddressFilter());
            if (input.getHourRanges() != null) {
                if (input.getHourRanges().size() > 24) throw bad("시간대는 최대 24개 구간까지 지정해 주세요.");
                for (var hour : input.getHourRanges())
                    if (hour == null || hour.getStartHour() < 0 || hour.getStartHour() > 23
                            || hour.getEndHour() < 0 || hour.getEndHour() > 23) throw bad("시간대는 0~23시로 입력해 주세요.");
                req.setHourRanges(List.copyOf(input.getHourRanges()));
            }
        }
        var original = input.getProductBreakdown();
        var breakdown = new AnalyticsQueryRequest.ProductBreakdown();
        if (original != null) {
            breakdown.setIncludeStandard(original.isIncludeStandard());
            breakdown.setIncludeNonStandard(original.isIncludeNonStandard());
        }
        breakdown.setStandardLevel(level(original == null ? null : original.getStandardLevel(), true));
        breakdown.setNonStandardLevel(level(original == null ? null : original.getNonStandardLevel(), false));
        if (req.getPrimaryY() == AnalyticsQueryRequest.PrimaryY.PRODUCT
                && !breakdown.isIncludeStandard() && !breakdown.isIncludeNonStandard())
            throw bad("규격 또는 비규격 분류를 하나 이상 선택해 주세요.");
        req.setProductBreakdown(breakdown);
        req.setKeyword(text(input.getKeyword()));
        if (req.getKeyword().length() > 100) throw bad("검색어는 100자 이하로 입력해 주세요.");
        List<String> kinds = List.of("PAID", "RETURN", "COLLECTION", "ADJUSTMENT");
        List<String> selectedKinds = input.getRecordKinds() == null ? kinds : new ArrayList<>(new LinkedHashSet<>(input.getRecordKinds()));
        if (selectedKinds.isEmpty() || selectedKinds.stream().anyMatch(kind -> kind == null || !kinds.contains(kind))) throw bad("주문 유형을 하나 이상 올바르게 선택해 주세요.");
        req.setRecordKinds(List.copyOf(selectedKinds));
        String grouping = text(input.getTimeGrouping()).toUpperCase(Locale.ROOT);
        if (grouping.isEmpty()) grouping = "NONE";
        if (!List.of("NONE", "DAY", "WEEK", "MONTH").contains(grouping)) throw bad("지원하지 않는 기간별 보기입니다.");
        req.setTimeGrouping(grouping);
        String deliveryView = text(input.getDeliveryView()).toUpperCase(Locale.ROOT);
        if (deliveryView.isEmpty()) deliveryView="NONE";
        if (!List.of("NONE","METHOD","COMPOSITION").contains(deliveryView)) throw bad("배송수단 보기 방식을 확인해 주세요.");
        if (!"NONE".equals(deliveryView)) {
            if (!"NONE".equals(grouping)) throw bad("배송수단 비교와 기간 추이는 함께 사용할 수 없습니다. 보기 방식을 하나 선택해 주세요.");
            if (req.getPrimaryY()==AnalyticsQueryRequest.PrimaryY.PRODUCT && (!"CATEGORY".equals(breakdown.getStandardLevel()) || !"SORT".equals(breakdown.getNonStandardLevel())))
                throw bad("배송수단 비교는 제품 대분류 기준입니다. 시리즈·제품·색상 분류를 해제해 주세요.");
        }
        req.setDeliveryView(deliveryView);
        String deliveryMethod=text(input.getDeliveryMethod());if(deliveryMethod.isEmpty())deliveryMethod="ALL";
        if(!deliveryMethod.equals("ALL")&&!deliveryMethod.equals("UNASSIGNED")&&!deliveryMethod.matches("[1-9][0-9]{0,18}"))throw bad("배송수단 필터를 확인해 주세요.");
        req.setDeliveryMethod(deliveryMethod);
        String chartType = text(input.getChartType()).toUpperCase(Locale.ROOT);
        if (chartType.isEmpty()) chartType = "BAR";
        if (!List.of("BAR", "HORIZONTAL", "LINE").contains(chartType)) throw bad("지원하지 않는 그래프 형태입니다.");
        req.setChartType(chartType);
        List<String> chartMetrics = input.getChartMetrics() == null || input.getChartMetrics().isEmpty()
                ? List.of(req.getMetrics().get(0)) : new ArrayList<>(new LinkedHashSet<>(input.getChartMetrics()));
        if (chartMetrics.size() > 4) throw bad("그래프 지표는 최대 4개까지 선택해 주세요.");
        for (String code : chartMetrics) {
            AnalyticsMetric metric;
            try { metric = AnalyticsMetric.valueOf(code); } catch (Exception e) { throw bad("그래프 지표를 확인해 주세요."); }
            if (!req.getMetrics().contains(code)) throw bad("그래프 지표는 표에 추가된 지표 중에서 선택해 주세요.");
        }
        if ("COMPOSITION".equals(deliveryView) && chartMetrics.size()!=1) throw bad("배송수단 구성 그래프는 한 지표를 배송수단별로 비교합니다. 그래프 지표를 하나 선택해 주세요.");
        req.setChartMetrics(List.copyOf(chartMetrics));
        if (input.getChartLimit() < 0 || input.getChartLimit() > 100) throw bad("그래프 표시 범위는 0~100개입니다. 0은 기간 추이의 전체 구간입니다.");
        req.setChartLimit(input.getChartLimit() == 0 && "NONE".equals(grouping) ? 10 : input.getChartLimit());
        req.setChartSortDir("asc".equalsIgnoreCase(input.getChartSortDir()) ? "asc" : "desc");
        req.setCompactTable(input.isCompactTable());
        Set<String> allowed = new LinkedHashSet<>();
        for (AnalyticsMetric metric : AnalyticsMetric.values()) allowed.add(metric.key());
        if (!"NONE".equals(req.getTimeGrouping())) allowed.add("period");
        else allowed.addAll(req.getPrimaryY() == AnalyticsQueryRequest.PrimaryY.COMPANY
                ? List.of("companyName", "ownerName", "address") : List.of("type", "label"));
        if (!"NONE".equals(deliveryView)) allowed.add("deliveryName");
        String sort = text(input.getSortKey());
        if (sort.isEmpty() || "rank".equals(sort)) sort = "NONE".equals(req.getTimeGrouping()) ? metricKey(req.getMetrics().get(0)) : "period";
        if ("METHOD".equals(deliveryView)) allowed.removeAll(List.of("companyName","ownerName","address","type","label"));
        if (!allowed.contains(sort)) throw bad("현재 행 기준에서 사용할 수 없는 정렬 항목입니다.");
        req.setSortKey(sort);
        req.setSortDir("period".equals(sort) && text(input.getSortKey()).isEmpty() ? "asc" : "asc".equalsIgnoreCase(input.getSortDir()) ? "asc" : "desc");
        return req;
    }

    private String level(String value, boolean standard) {
        String normalized = text(value).toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) return standard ? "CATEGORY" : "SORT";
        if (!(standard ? List.of("CATEGORY", "SERIES", "PRODUCT", "COLOR") : List.of("SORT", "SERIES", "PRODUCT", "COLOR"))
                .contains(normalized)) throw bad("지원하지 않는 제품 분류 단계입니다.");
        return normalized;
    }

    private ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private String text(String value) { return value == null ? "" : value.trim(); }
    private String display(String value) { return text(value).isEmpty() ? "분류 정보 없음" : value.trim(); }
    private Company company(Order order) {
        return order.getTask() == null || order.getTask().getRequestedBy() == null ? null
                : order.getTask().getRequestedBy().getCompany();
    }
    private boolean matchesHours(LocalDateTime date, List<AnalyticsQueryRequest.HourRange> hours) {
        if (hours == null || hours.isEmpty()) return true;
        int hour = date.getHour();
        return hours.stream().anyMatch(range -> range.getStartHour() <= range.getEndHour()
                ? hour >= range.getStartHour() && hour <= range.getEndHour()
                : hour >= range.getStartHour() || hour <= range.getEndHour());
    }
    private boolean matchesAddress(Company company, AnalyticsQueryRequest.AddressFilter filter) {
        if (filter == null) return true;
        if (company == null) return text(filter.getProvince()).isEmpty() && text(filter.getCity()).isEmpty() && text(filter.getDistrict()).isEmpty();
        return (text(filter.getProvince()).isEmpty() || KoreanAdministrativeRegionNormalizer.canonicalProvinceName(filter.getProvince())
                        .equals(KoreanAdministrativeRegionNormalizer.canonicalProvinceName(company.getDoName())))
                && (text(filter.getCity()).isEmpty() || text(filter.getCity()).equals(text(company.getSiName())))
                && (text(filter.getDistrict()).isEmpty() || text(filter.getDistrict()).equals(text(company.getGuName())));
    }
    private String address(Company company) {
        if (company == null) return "지역 정보 없음";
        String result = List.of(text(company.getDoName()), text(company.getSiName()), text(company.getGuName())).stream()
                .filter(s -> !s.isEmpty()).collect(Collectors.joining(" "));
        return result.isEmpty() ? "지역 정보 없음" : result;
    }

    private Group companyGroup(Atom atom) {
        return new Group(new Key("COMPANY", List.of(atom.company == null ? "unknown" : String.valueOf(atom.company.getId()))),
                atom.company == null ? "업체 정보 없음" : display(atom.company.getCompanyName()));
    }
    private String companyLabel(Group group, Map<String, Long> counts) {
        return group.key.type.equals("COMPANY") && counts.getOrDefault(group.label, 0L) > 1
                ? group.label + " (업체 #" + group.key.path.get(0) + ")" : group.label;
    }
    private Group productGroup(Atom atom, String level, boolean includeType) {
        var norm = atom.norm;
        List<String> path = switch (level) {
            case "SERIES" -> List.of(display(norm.getTop()), display(norm.getMid()));
            case "PRODUCT" -> List.of(display(norm.getTop()), display(norm.getMid()), display(norm.getProduct()));
            case "COLOR" -> List.of(display(norm.getColor()));
            default -> List.of(display(norm.getTop()));
        };
        String type = atom.order.isStandard() ? "규격" : "비규격";
        String label = String.join(" / ", path);
        return new Group(new Key(type, path), includeType ? type + " · " + label : label);
    }
    private Map<Group, List<Atom>> groups(List<Atom> atoms, Function<Atom, Group> group) {
        Map<Group, List<Atom>> result = new LinkedHashMap<>();
        for (Atom atom : atoms) result.computeIfAbsent(group.apply(atom), ignored -> new ArrayList<>()).add(atom);
        return result;
    }
    private String recordKind(Atom atom) {
        long quantity = atom.order.getQuantity();
        if (quantity < 0) return "COLLECTION";
        if (quantity > 0 && atom.sales == 0) return "RETURN";
        if (quantity > 0 && atom.sales > 0) return "PAID";
        return "ADJUSTMENT";
    }
    private LocalDate period(LocalDate date, String grouping) {
        return switch (grouping) {
            case "WEEK" -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case "MONTH" -> date.withDayOfMonth(1);
            default -> date;
        };
    }
    private LocalDate nextPeriod(LocalDate date, String grouping) {
        return switch (grouping) { case "WEEK" -> date.plusWeeks(1); case "MONTH" -> date.plusMonths(1); default -> date.plusDays(1); };
    }
    private List<Map<String, Object>> timeRows(List<Atom> atoms, AnalyticsQueryRequest req) {
        Map<LocalDate, List<Atom>> grouped = new TreeMap<>();
        for (Atom atom : atoms) grouped.computeIfAbsent(period(atom.order.getCreatedAt().toLocalDate(), req.getTimeGrouping()), key -> new ArrayList<>()).add(atom);
        LocalDate from = req.getFromDate() == null ? (grouped.isEmpty() ? null : grouped.keySet().iterator().next()) : req.getFromDate();
        LocalDate to = req.getToDate() == null ? (grouped.isEmpty() ? null : ((TreeMap<LocalDate,List<Atom>>)grouped).lastKey()) : req.getToDate();
        if (from != null && to != null) {
            LocalDate start=period(from,req.getTimeGrouping()), end=period(to,req.getTimeGrouping());
            List<LocalDate> missing=new ArrayList<>();
            for(LocalDate date=start;!date.isAfter(end)&&missing.size()<=5000;date=nextPeriod(date,req.getTimeGrouping())) missing.add(date);
            if(missing.size()<=5000) for(LocalDate date:missing) grouped.putIfAbsent(date,List.of());
        }
        List<Map<String,Object>> rows=new ArrayList<>();
        for(var entry:grouped.entrySet()) {
            Map<String,Object> row=values(entry.getValue());
            row.put("period", "MONTH".equals(req.getTimeGrouping()) ? entry.getKey().format(DateTimeFormatter.ofPattern("yyyy-MM")) : entry.getKey().toString());
            row.put("detailScope", AnalyticsDetailsDtos.Scope.builder().mode("PERIOD").period((String)row.get("period")).build());
            rows.add(row);
        }
        return rows;
    }
    private void add(Map<String, Long> values, String key, long amount) {
        values.put(key, Math.addExact(values.get(key), amount));
    }
    private Totals totals(List<Atom> atoms) {
        Map<String, Long> values = new LinkedHashMap<>();
        for (AnalyticsMetric metric : AnalyticsMetric.values()) values.put(metric.key(), 0L);
        for (Atom atom : atoms) {
            long qty = atom.order.getQuantity(), amount = atom.sales;
            add(values, "sales", amount); add(values, "supplyAmount", atom.supplyAmount); add(values, "vatAmount", atom.vatAmount);
            add(values, "taskCount", 1); add(values, "qty", qty);
            if (qty < 0) {
                add(values, "collectionOrderCount", 1); add(values, "collectionQty", -qty); add(values, "collectionSales", amount);
            } else if (qty > 0 && amount == 0) {
                add(values, "returnOrderCount", 1); add(values, "returnQty", qty);
            } else if (qty > 0 && amount > 0) {
                add(values, "paidOrderCount", 1); add(values, "paidQty", qty); add(values, "paidSales", amount);
            } else {
                add(values, "adjustmentOrderCount", 1); add(values, "adjustmentQty", qty); add(values, "adjustmentSales", amount);
            }
            if (qty > 0) {
                add(values, "productionRequestQty", qty);
                if (atom.order.getStatus() != null && Set.of(OrderStatus.PRODUCTION_DONE, OrderStatus.DISPATCH_DONE, OrderStatus.DELIVERY_DONE).contains(atom.order.getStatus()))
                    add(values, "productionDoneQty", qty);
            }
        }
        values.put("productionPendingQty", values.get("productionRequestQty") - values.get("productionDoneQty"));
        return new Totals(values.get("sales"), values.get("taskCount"), values.get("qty"), values);
    }
    private List<Map<String, Object>> companyRows(List<Atom> atoms, AnalyticsQueryRequest req) {
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<Group, List<Atom>> grouped = groups(atoms, this::companyGroup);
        Map<String, Long> labels = grouped.keySet().stream().collect(Collectors.groupingBy(Group::label, Collectors.counting()));
        for (var entry : grouped.entrySet()) {
            Map<String, Object> row = values(entry.getValue());
            row.put("companyName", companyLabel(entry.getKey(), labels));
            row.put("companyId", entry.getValue().get(0).company == null ? null : entry.getValue().get(0).company.getId());
            row.put("detailScope", AnalyticsDetailsDtos.Scope.builder().mode("COMPANY").companyId((Long)row.get("companyId")).build());
            String owners = entry.getValue().stream().map(a -> a.order.getTask() == null || a.order.getTask().getRequestedBy() == null
                    ? "" : text(a.order.getTask().getRequestedBy().getName())).filter(s -> !s.isEmpty()).distinct().sorted()
                    .collect(Collectors.joining(", "));
            row.put("ownerName", owners.isEmpty() ? "-" : owners);
            if (req.getAddressFilter() != null) row.put("address", address(entry.getValue().get(0).company));
            rows.add(row);
        }
        return rows;
    }
    private List<Map<String, Object>> productRows(List<Atom> atoms, AnalyticsQueryRequest req) {
        var breakdown = req.getProductBreakdown();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (var entry : groups(atoms, a -> productGroup(a, a.order.isStandard() ? breakdown.getStandardLevel() : breakdown.getNonStandardLevel(), false)).entrySet()) {
            Map<String, Object> row = values(entry.getValue());
            row.put("type", entry.getKey().key.type); row.put("label", entry.getKey().label);
            row.put("detailScope", AnalyticsDetailsDtos.Scope.builder().mode("PRODUCT").standard("규격".equals(entry.getKey().key.type)).path(entry.getKey().key.path).build());
            rows.add(row);
        }
        return rows;
    }
    private Map<String, Object> values(List<Atom> atoms) {
        Totals total = totals(atoms);
        Map<String, Object> row = new LinkedHashMap<>();
        total.values.forEach(row::put);
        return row;
    }
    private long number(Object value) { return value instanceof Number n ? n.longValue() : 0; }
    private void sortRows(List<Map<String, Object>> rows, AnalyticsQueryRequest req) {
        String key = req.getSortKey();
        Comparator<Map<String, Object>> comparator = AnalyticsMetric.forKey(key) != null
                ? Comparator.comparingLong(row -> number(row.get(key)))
                : Comparator.comparing(row -> String.valueOf(row.getOrDefault(key, "")), String.CASE_INSENSITIVE_ORDER);
        if ("desc".equals(req.getSortDir())) comparator = comparator.reversed();
        comparator = comparator.thenComparing(row -> String.valueOf(row.getOrDefault("companyName", row.getOrDefault("type", ""))))
                .thenComparing(row -> String.valueOf(row.getOrDefault("label", row.getOrDefault("companyId", ""))));
        rows.sort(comparator);
    }
    private List<AnalyticsQueryResponse.ColumnMeta> columns(AnalyticsQueryRequest req) {
        List<AnalyticsQueryResponse.ColumnMeta> result = new ArrayList<>();
        result.add(column("rank", "순위", "현재 표 정렬 기준 순위입니다."));
        if (!"NONE".equals(req.getTimeGrouping())) {
            result.add(column("period", switch(req.getTimeGrouping()){case "WEEK"->"주 시작일";case "MONTH"->"월";default->"발주일";},
                    "선택한 업체/제품 대상의 기간별 합계입니다. 주별은 월요일 시작이며 입력한 발주일 범위 안의 데이터만 포함합니다."));
        } else if ("METHOD".equals(req.getDeliveryView())) {
            result.add(column("deliveryName", "배송수단", "실제 품목의 배송수단 ID로 구분하며 미지정도 포함합니다."));
        } else if (req.getPrimaryY() == AnalyticsQueryRequest.PrimaryY.COMPANY) {
            result.add(column("companyName", "업체명", "이름이 같아도 서로 다른 업체 ID는 합치지 않습니다."));
            result.add(column("ownerName", "요청자", "조회된 주문의 업체 소속 요청자입니다. 여러 명이면 함께 표시합니다."));
            if (req.getAddressFilter() != null) result.add(column("address", "지역(도/시/구)", "업체의 등록 주소 기준입니다."));
        } else {
            result.add(column("type", "구분", "규격과 비규격을 구별합니다."));
            result.add(column("label", "제품 기준", "선택한 분류 단계로 집계합니다. 같은 시리즈·제품명도 상위 분류가 다르면 구별합니다."));
        }
        if ("COMPOSITION".equals(req.getDeliveryView())) result.add(column("deliveryName", "배송수단", "각 업체 또는 제품 대분류 안의 배송수단 구성입니다."));
        for (String code : req.getMetrics()) {
            AnalyticsMetric metric = AnalyticsMetric.valueOf(code);
            result.add(AnalyticsQueryResponse.ColumnMeta.builder().key(metric.key()).label(metric.label())
                    .unit(metric.unit()).help(metric.help()).build());
        }
        return result;
    }
    private AnalyticsQueryResponse.ColumnMeta column(String key, String label, String help) {
        return AnalyticsQueryResponse.ColumnMeta.builder().key(key).label(label).help(help).build();
    }
    private String metricKey(String code) { return AnalyticsMetric.valueOf(code).key(); }
    private String metricLabel(String code) { return AnalyticsMetric.valueOf(code).label(); }
    private String sortLabel(String key) {
        AnalyticsMetric metric = AnalyticsMetric.forKey(key);
        if (metric != null) return metric.label();
        return switch (key) { case "deliveryName" -> "배송수단"; case "companyName" -> "업체명"; case "ownerName" -> "요청자"; case "address" -> "지역"; case "type" -> "구분"; case "period" -> "기간"; default -> "제품 기준"; };
    }
    private String levelLabel(String level) { return switch (level) {
        case "SERIES" -> "시리즈"; case "PRODUCT" -> "제품"; case "COLOR" -> "색상"; default -> "대분류";
    }; }
    private AnalyticsQueryResponse.Meta meta(AnalyticsQueryRequest req) {
        var filter = req.getAddressFilter();
        String addressText = filter == null ? "전체" : List.of(text(filter.getProvince()), text(filter.getCity()), text(filter.getDistrict()))
                .stream().filter(s -> !s.isEmpty()).collect(Collectors.joining(" "));
        String hour = req.getHourRanges() == null || req.getHourRanges().isEmpty() ? "전체" : req.getHourRanges().stream()
                .map(r -> r.getStartHour() + "~" + r.getEndHour() + "시").collect(Collectors.joining(", "));
        String dimensions = "제품 분류: 없음";
        if (req.getPrimaryY() == AnalyticsQueryRequest.PrimaryY.PRODUCT) {
            var b = req.getProductBreakdown(); List<String> parts = new ArrayList<>();
            if (b.isIncludeStandard()) parts.add("규격(" + levelLabel(b.getStandardLevel()) + ")");
            if (b.isIncludeNonStandard()) parts.add("비규격(" + levelLabel(b.getNonStandardLevel()) + ")");
            dimensions = "제품 분류: " + String.join(", ", parts);
        }
        return AnalyticsQueryResponse.Meta.builder()
                .periodText("발주일: " + (req.getFromDate() == null ? "과거 전체" : req.getFromDate()) + " ~ "
                        + (req.getToDate() == null ? "미래 전체" : req.getToDate()))
                .yAxisText("행 기준: " + (!"NONE".equals(req.getTimeGrouping())?"발주 기간":"METHOD".equals(req.getDeliveryView()) ? "배송수단별" : req.getPrimaryY() == AnalyticsQueryRequest.PrimaryY.COMPANY ? "업체별" : "제품별"))
                .ySubText("보조 조건: " + (filter == null && "전체".equals(hour) ? "없음" : (filter != null ? "주소 " : "") + (!"전체".equals(hour) ? "시간대" : "")))
                .xAxisText("선택 지표: " + req.getMetrics().stream().map(this::metricLabel).collect(Collectors.joining(", ")))
                .xDimText(dimensions).addressText("주소: " + (addressText.isEmpty() ? "전체" : addressText)).hourText("시간대: " + hour)
                .sortText("표 정렬: " + sortLabel(req.getSortKey()) + ("asc".equals(req.getSortDir()) ? " 오름차순" : " 내림차순"))
                .viewText("보기: " + (!"NONE".equals(req.getDeliveryView()) ? ("METHOD".equals(req.getDeliveryView())?"배송수단별 합계":"업체/제품별 배송수단 구성") : switch(req.getTimeGrouping()){case "DAY"->"일별 추이";case "WEEK"->"주별 추이";case "MONTH"->"월별 추이";default->"업체/제품 비교";}))
                .filterText("배송수단 필터: " + deliveryFilterText(req.getDeliveryMethod()) + " / 검색: " + (req.getKeyword().isEmpty()?"전체":req.getKeyword()) + " / 유형: " + req.getRecordKinds().stream().map(kind->switch(kind){case "PAID"->"유상 주문";case "RETURN"->"0원 반품용";case "COLLECTION"->"회수";default->"기타 조정";}).collect(Collectors.joining(", ")))
                .generatedAtText("조회시각: " + ZonedDateTime.now(ZoneId.of("Asia/Seoul")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + " KST")
                .compactTable(req.isCompactTable())
                .basisText(BASIS + " / 생산 완료는 조회 발주건의 현재 상태 기준이며 완료일 기준 실적이 아닙니다.").chartBasisText("그래프: " + req.getChartMetrics().stream().map(this::metricLabel).collect(Collectors.joining(" / ")) + " · " + ("NONE".equals(req.getTimeGrouping()) ? ("asc".equals(req.getChartSortDir())?"작은 값부터":"큰 값부터") : "시간순") + " / 5개 요약은 첫 그래프 지표의 " + ("NONE".equals(req.getTimeGrouping())&&"asc".equals(req.getChartSortDir())?"작은 값부터":"큰 값부터"))
                .build();
    }
    private AnalyticsQueryResponse.Summary summary(List<Atom> atoms, AnalyticsQueryRequest req) {
        LinkedHashSet<String> codes=new LinkedHashSet<>(req.getChartMetrics());codes.addAll(req.getMetrics());
        List<AnalyticsQueryResponse.ColumnMeta> summaryColumns=codes.stream().map(code->{var metric=AnalyticsMetric.valueOf(code);return AnalyticsQueryResponse.ColumnMeta.builder().key(metric.key()).label(metric.label()).unit(metric.unit()).help(metric.help()).build();}).toList();
        boolean ascending="NONE".equals(req.getTimeGrouping())&&"asc".equals(req.getChartSortDir());
        var builder = AnalyticsQueryResponse.Summary.builder().title(ascending?"하위 5개 요약":"상위 5개 요약").metricColumns(summaryColumns);
        if (req.getPrimaryY() == AnalyticsQueryRequest.PrimaryY.COMPANY) {
            return builder.topCompanies(top(atoms, this::companyGroup, req))
                    .topRegions(top(atoms, a -> new Group(new Key("REGION", List.of(address(a.company))), address(a.company)), req)).build();
        }
        return builder.topCategories(top(atoms, a -> productGroup(a, "CATEGORY", true), req))
                .topSeries(top(atoms, a -> productGroup(a, "SERIES", true), req))
                .topProducts(top(atoms, a -> productGroup(a, "PRODUCT", true), req)).build();
    }
    private List<AnalyticsQueryResponse.RankItem> top(List<Atom> atoms, Function<Atom, Group> group, AnalyticsQueryRequest req) {
        List<AnalyticsQueryResponse.RankItem> result = new ArrayList<>();
        Map<Group, List<Atom>> grouped = groups(atoms, group);
        Map<String, Long> labels = grouped.keySet().stream().collect(Collectors.groupingBy(Group::label, Collectors.counting()));
        for (var entry : grouped.entrySet()) {
            Totals total = totals(entry.getValue());
            result.add(AnalyticsQueryResponse.RankItem.builder().name(companyLabel(entry.getKey(), labels))
                    .sales(total.sales).taskCount(total.count).qty(total.qty).metrics(total.values).build());
        }
        String metric = metricKey(req.getChartMetrics().get(0));
        Comparator<AnalyticsQueryResponse.RankItem> rankOrder=Comparator.comparingLong(item->item.getMetrics().get(metric));
        if(!("NONE".equals(req.getTimeGrouping())&&"asc".equals(req.getChartSortDir())))rankOrder=rankOrder.reversed();
        result.sort(rankOrder.thenComparing(AnalyticsQueryResponse.RankItem::getName));
        List<AnalyticsQueryResponse.RankItem> limited = new ArrayList<>(result.subList(0, Math.min(5, result.size())));
        for (int i = 0; i < limited.size(); i++) limited.get(i).setRank(i + 1);
        return limited;
    }
    private AnalyticsQueryResponse.ChartData chart(List<Map<String, Object>> rows, AnalyticsQueryRequest req) {
        if(rows.isEmpty()) return null;
        if ("COMPOSITION".equals(req.getDeliveryView())) return deliveryChart(rows,req);
        String key=metricKey(req.getChartMetrics().get(0)); boolean trend=!"NONE".equals(req.getTimeGrouping());
        Comparator<Map<String,Object>> order=trend?Comparator.comparing(row->String.valueOf(row.get("period"))):Comparator.comparingLong(row->number(row.get(key)));
        if(!trend&&"desc".equals(req.getChartSortDir()))order=order.reversed();
        if(!trend)order=order.thenComparing(row->String.valueOf(row.getOrDefault("companyName",row.getOrDefault("label",""))));
        List<Map<String,Object>> sorted=rows.stream().sorted(order).toList();
        int limit=req.getChartLimit()==0?5000:req.getChartLimit(), start=trend?Math.max(0,sorted.size()-limit):0, end=Math.min(sorted.size(),start+limit);
        List<Map<String,Object>> selected=sorted.subList(start,end);
        List<String> labels=selected.stream().map(row->trend?String.valueOf(row.get("period")):"METHOD".equals(req.getDeliveryView())?String.valueOf(row.get("deliveryName")):req.getPrimaryY()==AnalyticsQueryRequest.PrimaryY.COMPANY?String.valueOf(row.get("companyName")):row.get("type")+" · "+row.get("label")).toList();
        String[] colors={"#2563eb","#0d9488","#d97706","#9333ea"};
        List<AnalyticsQueryResponse.ChartSeries> series=new ArrayList<>();
        for(int i=0;i<req.getChartMetrics().size();i++) {
            AnalyticsMetric metric=AnalyticsMetric.valueOf(req.getChartMetrics().get(i));
            series.add(AnalyticsQueryResponse.ChartSeries.builder().key(metric.key()).label(metric.label()).unit(metric.unit()).color(colors[i])
                    .values(selected.stream().map(row->(Number)row.get(metric.key())).toList()).build());
        }
        double max=series.stream().flatMap(s->s.getValues().stream()).mapToDouble(v->Math.abs(v.doubleValue())).max().orElse(0);
        String title=series.stream().map(AnalyticsQueryResponse.ChartSeries::getLabel).collect(Collectors.joining(" / "))
                +(trend?" · 기간 추이":" · "+("asc".equals(req.getChartSortDir())?"작은 값":"큰 값")+"부터 "+selected.size()+"개");
        var chart=AnalyticsQueryResponse.ChartData.builder().title(title).type(req.getChartType().toLowerCase(Locale.ROOT))
                .labels(labels).values(series.get(0).getValues()).series(series).unit(series.stream().map(AnalyticsQueryResponse.ChartSeries::getUnit).distinct().collect(Collectors.joining(" / ")))
                .valueKey(key).maxValue(max).totalPoints(rows.size())
                .note(trend&&selected.size()<rows.size()?"전체 "+rows.size()+"구간 중 최근 "+selected.size()+"구간 표시":"그래프 "+selected.size()+"개 / 표 전체 "+rows.size()+"행")
                .build();
        chart.setSvg(AnalyticsChartRenderer.svg(chart));
        chart.setSvgPages(AnalyticsChartRenderer.pages(chart));
        return chart;
    }
    public AnalyticsDetailsDtos.Response details(AnalyticsDetailsDtos.Request input) {
        if(input==null||input.getScope()==null)throw bad("상세 조회할 행을 선택해 주세요.");
        if(input.getPage()<0||input.getSize()<1||input.getSize()>200)throw bad("상세 목록의 페이지와 크기를 확인해 주세요.");
        AnalyticsQueryRequest req=normalize(input.getQuery(),false);
        var scope=input.getScope();String mode=text(scope.getMode());
        if(!List.of("ALL","COMPANY","PRODUCT","PERIOD").contains(mode))throw bad("상세 조회 범위를 확인해 주세요.");
        if("COMPANY".equals(mode)&&(req.getPrimaryY()!=AnalyticsQueryRequest.PrimaryY.COMPANY||!"NONE".equals(req.getTimeGrouping())||scope.getCompanyId()!=null&&scope.getCompanyId()<1))throw bad("업체 상세 범위를 확인해 주세요.");
        if("PRODUCT".equals(mode)&&(req.getPrimaryY()!=AnalyticsQueryRequest.PrimaryY.PRODUCT||!"NONE".equals(req.getTimeGrouping())||scope.getStandard()==null||scope.getPath()==null||scope.getPath().isEmpty()||scope.getPath().size()>3||scope.getPath().stream().anyMatch(v->v==null||v.length()>255)))throw bad("제품 상세 범위를 확인해 주세요.");
        if("PERIOD".equals(mode)&&("NONE".equals(req.getTimeGrouping())||scope.getPeriod()==null||!scope.getPeriod().matches("[0-9]{4}-[0-9]{2}(-[0-9]{2})?")))throw bad("기간 상세 범위를 확인해 주세요.");
        String kind=text(input.getKind());if(kind.isEmpty())kind="ALL";
        if(!List.of("ALL","PAID","RETURN","COLLECTION","ADJUSTMENT","PRODUCTION_REQUEST","PRODUCTION_DONE","PRODUCTION_PENDING").contains(kind))throw bad("상세 조회 유형을 확인해 주세요.");
        String keyword=text(input.getKeyword()).toLowerCase(Locale.ROOT);if(keyword.length()>100)throw bad("상세 검색어는 100자 이하입니다.");
        String selectedDay=text(input.getDay());if(!selectedDay.isEmpty())try{LocalDate.parse(selectedDay);}catch(Exception e){throw bad("상세 조회 날짜를 확인해 주세요.");}
        String sort=text(input.getSortKey());if(!List.of("date","company","product","quantity","supplyAmount","vatAmount","amount").contains(sort))throw bad("상세 목록 정렬을 확인해 주세요.");
        List<Atom> matching=new ArrayList<>();
        if(scope.getDeliveryMethod()!=null && !scope.getDeliveryMethod().matches("UNASSIGNED|[1-9][0-9]{0,18}"))throw bad("상세 배송수단을 확인해 주세요.");
        for(Atom atom:loadAtoms(req)) {
            if(scope.getDeliveryMethod()!=null&&!scope.getDeliveryMethod().equals(deliveryKey(atom.order)))continue;
            if("COMPANY".equals(mode)&&!java.util.Objects.equals(scope.getCompanyId(),atom.company==null?null:atom.company.getId()))continue;
            if("PRODUCT".equals(mode)) {
                var b=req.getProductBreakdown();var group=productGroup(atom,atom.order.isStandard()?b.getStandardLevel():b.getNonStandardLevel(),false);
                if(scope.getStandard()!=atom.order.isStandard()||!scope.getPath().equals(group.key.path))continue;
            }
            if("PERIOD".equals(mode)&&!periodLabel(atom.order.getCreatedAt().toLocalDate(),req.getTimeGrouping()).equals(scope.getPeriod()))continue;
            boolean positive=atom.order.getQuantity()>0, done=positive&&atom.order.getStatus()!=null&&Set.of(OrderStatus.PRODUCTION_DONE,OrderStatus.DISPATCH_DONE,OrderStatus.DELIVERY_DONE).contains(atom.order.getStatus());
            if(!switch(kind){case "ALL"->true;case "PRODUCTION_REQUEST"->positive;case "PRODUCTION_DONE"->done;case "PRODUCTION_PENDING"->positive&&!done;default->recordKind(atom).equals(kind);})continue;
            String haystack=(atom.order.getCreatedAt().toLocalDate()+" "+(atom.company==null?"업체 정보 없음":text(atom.company.getCompanyName()))+" "+display(atom.norm.getTop())+" "+display(atom.norm.getMid())+" "+display(atom.norm.getProduct())).toLowerCase(Locale.ROOT);
            if(!keyword.isEmpty()&&!haystack.contains(keyword))continue;
            matching.add(atom);
        }
        Map<String,Long> dayCounts=matching.stream().collect(Collectors.groupingBy(a->a.order.getCreatedAt().toLocalDate().toString(),TreeMap::new,Collectors.counting()));
        List<AnalyticsDetailsDtos.Day> days=dayCounts.entrySet().stream().sorted(Map.Entry.<String,Long>comparingByKey().reversed()).map(e->AnalyticsDetailsDtos.Day.builder().date(e.getKey()).count(e.getValue()).build()).toList();
        if(!selectedDay.isEmpty())matching=matching.stream().filter(a->a.order.getCreatedAt().toLocalDate().toString().equals(selectedDay)).collect(Collectors.toCollection(ArrayList::new));
        Comparator<Atom> comparator=switch(sort){case "quantity"->Comparator.comparingInt(a->a.order.getQuantity());case "amount"->Comparator.comparingLong(a->a.sales);case "supplyAmount"->Comparator.comparingLong(a->a.supplyAmount);case "vatAmount"->Comparator.comparingLong(a->a.vatAmount);case "company"->Comparator.comparing(a->a.company==null?"":text(a.company.getCompanyName()));case "product"->Comparator.comparing(a->display(a.norm.getProduct()));default->Comparator.comparing(a->a.order.getCreatedAt());};
        if(!"asc".equalsIgnoreCase(input.getSortDir()))comparator=comparator.reversed();
        matching.sort(comparator.thenComparing(a->a.order.getId()));
        Totals total=totals(matching);int start=(int)Math.min((long)input.getPage()*input.getSize(),matching.size()),end=Math.min(start+input.getSize(),matching.size());
        List<AnalyticsDetailsDtos.Row> rows=matching.subList(start,end).stream().map(a->AnalyticsDetailsDtos.Row.builder().orderId(a.order.getId()).date(a.order.getCreatedAt().toLocalDate().toString()).dateTime(a.order.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")))
                .companyId(a.company==null?null:a.company.getId()).companyName(a.company==null?"업체 정보 없음":display(a.company.getCompanyName())).type(a.order.isStandard()?"규격":"비규격").category(display(a.norm.getTop())).series(display(a.norm.getMid())).product(display(a.norm.getProduct())).color(text(a.norm.getColor())).size(a.norm.getRaw().get("사이즈") instanceof String value?value:"")
                .kind(switch(recordKind(a)){case "PAID"->"유상 주문";case "RETURN"->"0원 반품용";case "COLLECTION"->"회수";default->"기타 조정";}).kindCode(recordKind(a)).quantity(a.order.getQuantity()).supplyAmount(a.supplyAmount).vatAmount(a.vatAmount).amount(a.sales).deliveryMethod(deliveryName(a.order)).status(a.order.getStatus()==null?"상태 미등록":a.order.getStatus().getLabel()).build()).toList();
        return AnalyticsDetailsDtos.Response.builder().rows(rows).days(days).totals(total.values).totalRows(matching.size()).page(input.getPage()).size(input.getSize()).build();
    }
    private String periodLabel(LocalDate date,String grouping) { LocalDate start=period(date,grouping);return "MONTH".equals(grouping)?start.format(DateTimeFormatter.ofPattern("yyyy-MM")):start.toString(); }

    private String deliveryFilterText(String key) {
        if("ALL".equals(key))return "전체";if("UNASSIGNED".equals(key))return "배송수단 미지정";
        return deliveryOptions().stream().filter(d->key.equals(d.get("value"))).map(d->d.get("label")).findFirst().orElse("배송수단 #"+key);
    }
    private String deliveryKey(Order order) { return order.getDeliveryMethod()==null||order.getDeliveryMethod().getId()==null ? "UNASSIGNED" : String.valueOf(order.getDeliveryMethod().getId()); }
    private String deliveryName(Order order) { return order.getDeliveryMethod()==null ? "배송수단 미지정" : display(order.getDeliveryMethod().getMethodName())+" (#"+order.getDeliveryMethod().getId()+")"; }
    public List<Map<String,String>> deliveryOptions() {
        return analyticsOrderRepository.deliveryOptions().stream().map(d->Map.of("value",String.valueOf(d.getId()),"label",d.getMethodName()+" (#"+d.getId()+")")).toList();
    }
    private List<Map<String,Object>> deliveryRows(List<Atom> atoms,AnalyticsQueryRequest req) {
        Map<String,List<Atom>> byDelivery=atoms.stream().collect(Collectors.groupingBy(a->deliveryKey(a.order),LinkedHashMap::new,Collectors.toList()));
        List<Map<String,Object>> result=new ArrayList<>();
        for(var entry:byDelivery.entrySet()) {
            List<Map<String,Object>> rows;
            if("METHOD".equals(req.getDeliveryView())) {
                var row=values(entry.getValue());row.put("detailScope",AnalyticsDetailsDtos.Scope.builder().mode("ALL").build());rows=List.of(row);
            } else rows=req.getPrimaryY()==AnalyticsQueryRequest.PrimaryY.COMPANY?companyRows(entry.getValue(),req):productRows(entry.getValue(),req);
            for(var row:rows) {
                var scope=(AnalyticsDetailsDtos.Scope)row.get("detailScope");scope.setDeliveryMethod(entry.getKey());
                row.put("deliveryKey",entry.getKey());row.put("deliveryName",deliveryName(entry.getValue().get(0).order));
                row.put("primaryKey",scope.getMode()+":"+scope.getCompanyId()+":"+scope.getStandard()+":"+scope.getPath());
                row.put("primaryLabel",req.getPrimaryY()==AnalyticsQueryRequest.PrimaryY.COMPANY?row.get("companyName"):row.get("type")+" · "+row.get("label"));result.add(row);
            }
        }
        // The same company may appear under multiple methods; always retain its ID in chart labels.
        if(req.getPrimaryY()==AnalyticsQueryRequest.PrimaryY.COMPANY && "COMPOSITION".equals(req.getDeliveryView()))
            result.forEach(row->row.put("primaryLabel",row.get("companyName")+" (#"+(row.get("companyId")==null?"미지정":row.get("companyId"))+")"));
        return result;
    }
    private AnalyticsQueryResponse.ChartData deliveryChart(List<Map<String,Object>> rows,AnalyticsQueryRequest req) {
        AnalyticsMetric metric=AnalyticsMetric.valueOf(req.getChartMetrics().get(0));String key=metric.key();
        Map<String,List<Map<String,Object>>> grouped=rows.stream().collect(Collectors.groupingBy(r->String.valueOf(r.get("primaryKey")),LinkedHashMap::new,Collectors.toList()));
        Comparator<Map.Entry<String,List<Map<String,Object>>>> compare=Comparator.comparingLong(e->e.getValue().stream().mapToLong(r->number(r.get(key))).sum());
        if("desc".equals(req.getChartSortDir()))compare=compare.reversed();
        var selected=grouped.entrySet().stream().sorted(compare.thenComparing(Map.Entry::getKey)).limit(req.getChartLimit()).toList();
        List<String> methods=rows.stream().map(r->String.valueOf(r.get("deliveryKey"))).distinct().sorted().toList();
        String[] colors={"#2563eb","#0d9488","#d97706","#9333ea","#e11d48","#475569","#0284c7","#65a30d"};
        List<AnalyticsQueryResponse.ChartSeries> series=new ArrayList<>();
        for(int i=0;i<methods.size();i++) {
            String method=methods.get(i),label=rows.stream().filter(r->method.equals(r.get("deliveryKey"))).map(r->String.valueOf(r.get("deliveryName"))).findFirst().orElse(method);
            series.add(AnalyticsQueryResponse.ChartSeries.builder().key(method).label(label).unit(metric.unit()).color(colors[i%colors.length])
                .values(selected.stream().map(e->(Number)e.getValue().stream().filter(r->method.equals(r.get("deliveryKey"))).mapToLong(r->number(r.get(key))).sum()).toList()).build());
        }
        var chart=AnalyticsQueryResponse.ChartData.builder().title(metric.label()+" · 배송수단 구성").type(req.getChartType().toLowerCase(Locale.ROOT)).stacked(false)
            .labels(selected.stream().map(e->String.valueOf(e.getValue().get(0).get("primaryLabel"))).toList()).series(series).values(selected.stream().map(e->(Number)e.getValue().stream().mapToLong(r->number(r.get(key))).sum()).toList())
            .unit(metric.unit()).valueKey(key).totalPoints(grouped.size()).note("전체 "+grouped.size()+"개 업체/제품 중 "+selected.size()+"개 · 색상은 배송수단 · 표와 합계는 전체 포함").build();
        chart.setSvg(AnalyticsChartRenderer.svg(chart));chart.setSvgPages(AnalyticsChartRenderer.pages(chart));return chart;
    }
    private record Atom(Order order, Company company, ProductOptionNormalized norm, long sales, long supplyAmount, long vatAmount) {}
    private record Totals(long sales, long count, long qty, Map<String, Long> values) {}
    private record Key(String type, List<String> path) {}
    private record Group(Key key, String label) {}
}
