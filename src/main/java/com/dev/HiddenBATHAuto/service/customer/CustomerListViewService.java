package com.dev.HiddenBATHAuto.service.customer;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.dev.HiddenBATHAuto.dto.customer.CustomerPageDtos.AsListFilter;
import com.dev.HiddenBATHAuto.dto.customer.CustomerPageDtos.AsListRow;
import com.dev.HiddenBATHAuto.dto.customer.CustomerPageDtos.CategoryCount;
import com.dev.HiddenBATHAuto.dto.customer.CustomerPageDtos.SortSpec;
import com.dev.HiddenBATHAuto.dto.customer.CustomerPageDtos.TaskListFilter;
import com.dev.HiddenBATHAuto.dto.customer.CustomerPageDtos.TaskListRow;
import com.dev.HiddenBATHAuto.dto.customer.CustomerPageDtos.TaskOrderSummary;
import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.task.AsStatus;
import com.dev.HiddenBATHAuto.model.task.AsTask;
import com.dev.HiddenBATHAuto.model.task.Order;
import com.dev.HiddenBATHAuto.model.task.OrderItem;
import com.dev.HiddenBATHAuto.model.task.OrderStatus;
import com.dev.HiddenBATHAuto.model.task.Task;
import com.dev.HiddenBATHAuto.repository.as.AsTaskRepository;
import com.dev.HiddenBATHAuto.repository.as.AsTaskScheduleRepository;
import com.dev.HiddenBATHAuto.repository.order.TaskRepository;
import com.dev.HiddenBATHAuto.service.as.RegionLookupService;
import com.dev.HiddenBATHAuto.utils.OrderProductNameDisplayUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomerListViewService {

    private static final Set<Integer> ALLOWED_PAGE_SIZES = Set.of(30, 50, 70, 100, 200);

    private static final Set<String> AS_SORT_FIELDS = Set.of(
            "id", "customerName", "requestedAt", "scheduledDate", "processedAt", "price", "status", "productInfo");

    private static final Set<String> TASK_SORT_FIELDS = Set.of(
            "id", "ordererName", "createdAt", "deliveryDate", "totalPrice", "status", "deliveryMethod", "address");

    private static final List<String> TASK_CATEGORY_ORDER = List.of(
            "상부장", "하부장", "슬라이드장", "거울", "플랩장", "LED거울");

    private static final Set<String> COMPACT_PROVINCE_NAMES = Set.of(
            "서울", "부산", "대구", "인천", "광주", "대전", "울산", "세종",
            "경기", "강원", "충북", "충남", "전북", "전남", "경북", "경남", "제주");


    private final AsTaskRepository asTaskRepository;
    private final AsTaskScheduleRepository asTaskScheduleRepository;
    private final TaskRepository taskRepository;
    private final RegionLookupService regionLookupService;
    private final ObjectMapper objectMapper;

    public int normalizePageSize(int requestedSize) {
        return ALLOWED_PAGE_SIZES.contains(requestedSize) ? requestedSize : 30;
    }

    public int normalizePage(int requestedPage) {
        return Math.max(0, requestedPage);
    }

    public Page<AsListRow> searchAsList(Long companyId, AsListFilter filter) {
        List<AsListRow> rows = loadFilteredAsRows(companyId, filter);
        return paginate(rows, filter.getPage(), filter.getSize());
    }

    public List<AsListRow> searchAsListAll(Long companyId, AsListFilter filter) {
        return loadFilteredAsRows(companyId, filter);
    }

    private List<AsListRow> loadFilteredAsRows(Long companyId, AsListFilter filter) {
        Page<AsTask> rawPage = asTaskRepository.findByCompanyIdAndRequestedAtRange(
                companyId,
                null,
                null,
                Pageable.unpaged());

        List<AsTask> tasks = rawPage.getContent();
        Map<Long, LocalDate> scheduleMap = getAsScheduleDateMap(tasks);
        RegionFilter region = resolveRegionFilter(filter.getProvinceId(), filter.getCityId(), filter.getDistrictId());

        List<AsListRow> rows = tasks.stream()
                .filter(Objects::nonNull)
                .map(task -> buildAsRow(task, scheduleMap.get(task.getId())))
                .filter(row -> matchesAsText(row, filter))
                .filter(row -> matchesAsDate(row, filter))
                .filter(row -> matchesAsBilling(row, filter.getBillingType()))
                .filter(row -> matchesAsStatus(row, filter.getStatus()))
                .filter(row -> matchesAsRegion(row, region))
                .collect(Collectors.toCollection(ArrayList::new));

        rows.sort(buildAsComparator(parseSort(filter.getSort(), AS_SORT_FIELDS)));
        return rows;
    }

    public TaskListRow buildTaskListRow(Task task) {
        return buildTaskRow(task);
    }

    public Page<TaskListRow> searchTaskList(Long companyId, TaskListFilter filter) {
        List<TaskListRow> rows = loadFilteredTaskRows(companyId, filter);
        return paginate(rows, filter.getPage(), filter.getSize());
    }

    public List<TaskListRow> searchTaskListAll(Long companyId, TaskListFilter filter) {
        return loadFilteredTaskRows(companyId, filter);
    }

    private List<TaskListRow> loadFilteredTaskRows(Long companyId, TaskListFilter filter) {
        Page<Task> rawPage = taskRepository.findByCompanyIdAndCreatedAtBetween(
                companyId,
                null,
                null,
                Pageable.unpaged());

        RegionFilter region = resolveRegionFilter(filter.getProvinceId(), filter.getCityId(), filter.getDistrictId());

        List<TaskListRow> rows = rawPage.getContent().stream()
                .filter(Objects::nonNull)
                .map(this::buildTaskRow)
                .filter(row -> matchesTaskText(row, filter))
                .filter(row -> matchesTaskDate(row, filter))
                .filter(row -> matchesTaskStatus(row, filter.getStatus()))
                .filter(row -> matchesTaskCategory(row, filter.getCategory()))
                .filter(row -> matchesTaskRegion(row, region))
                .collect(Collectors.toCollection(ArrayList::new));

        rows.sort(buildTaskComparator(parseSort(filter.getSort(), TASK_SORT_FIELDS)));
        return rows;
    }

    private AsListRow buildAsRow(AsTask task, LocalDate scheduledDate) {
        Member handler = task.getAssignedHandler();

        boolean hasImages = task.getImages() != null && !task.getImages().isEmpty();
        boolean hasVideos = task.getVideos() != null && !task.getVideos().isEmpty();

        return AsListRow.builder()
                .asTask(task)
                .scheduledDate(scheduledDate)
                .handlerName(resolveHandlerName(handler))
                .handlerContact(resolveHandlerContact(handler))
                .productInfo(buildAsProductInfo(task))
                .hasImages(hasImages)
                .hasVideos(hasVideos)
                .build();
    }

    private TaskListRow buildTaskRow(Task task) {
        List<Order> orders = safeOrders(task).stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(Order::getId, Comparator.nullsLast(Long::compareTo)))
                .toList();

        Order representative = orders.isEmpty() ? null : orders.get(0);

        List<TaskOrderSummary> orderSummaries = orders.stream()
                .map(this::buildTaskOrderSummary)
                .toList();

        Map<String, Long> categoryCountMap = new LinkedHashMap<>();
        for (TaskOrderSummary summary : orderSummaries) {
            categoryCountMap.merge(safeText(summary.getCategoryName(), "미분류"), 1L, Long::sum);
        }

        List<CategoryCount> categoryCounts = categoryCountMap.entrySet().stream()
                .sorted(Comparator
                        .comparingInt((Map.Entry<String, Long> entry) -> categoryOrderIndex(entry.getKey()))
                        .thenComparing(Map.Entry::getKey))
                .map(entry -> new CategoryCount(entry.getKey(), entry.getValue()))
                .toList();

        StatusSummary statusSummary = summarizeOrderStatus(orders);

        return TaskListRow.builder()
                .task(task)
                .representativeOrder(representative)
                .ordererName(safeText(representative != null ? representative.getOrdererName() : null, "-"))
                .ordererPhone(safeText(representative != null ? representative.getOrdererPhone() : null, "-"))
                .orderCount(orders.size())
                .categoryCounts(categoryCounts)
                .orderSummaries(orderSummaries)
                .deliveryMethodName(resolveDeliveryMethodName(representative))
                .deliveryAddress(buildEffectiveDeliveryAddress(representative))
                .deliveryRegion(buildEffectiveDeliveryRegion(representative))
                .deliveryDate(representative != null ? representative.getPreferredDeliveryDate() : null)
                .statusKey(statusSummary.key())
                .statusLabel(statusSummary.label())
                .managerName(resolveManagerName(task.getManagedBy()))
                .vatIncludedTotalPrice(calculateVatIncludedTotalPrice(task.getTotalPrice()))
                .build();
    }

    private int calculateVatIncludedTotalPrice(int vatExcludedTotalPrice) {
        if (vatExcludedTotalPrice <= 0) {
            return Math.max(vatExcludedTotalPrice, 0);
        }
        return (int) Math.round(vatExcludedTotalPrice * 1.1d);
    }

    private TaskOrderSummary buildTaskOrderSummary(Order order) {
        if (order == null) {
            return TaskOrderSummary.builder()
                    .productName("-")
                    .size("-")
                    .color("-")
                    .deliveryMethodName("-")
                    .deliveryAddress("-")
                    .statusKey("NONE")
                    .statusLabel("-")
                    .build();
        }

        OrderItem item = order.getOrderItem();
        Map<String, Object> optionMap = parseOrderOptionMap(item != null ? item.getOptionJson() : null);
        OrderStatus status = order.getStatus();

        return TaskOrderSummary.builder()
                .orderId(order.getId())
                .categoryName(extractCategory(order, optionMap))
                .productName(resolveCustomerProductName(item, optionMap))
                .size(resolveCustomerProductSize(optionMap))
                .color(resolveCustomerProductColor(optionMap))
                .quantity(order.getQuantity())
                .deliveryMethodName(resolveDeliveryMethodName(order))
                .deliveryAddress(buildEffectiveDeliveryAddress(order))
                .deliveryDate(order.getPreferredDeliveryDate())
                .statusKey(status != null ? status.name() : "NONE")
                .statusLabel(status != null ? status.getLabel() : "-")
                .orderComment(safeText(order.getOrderComment(), "-"))
                .adminMemo(safeText(order.getAdminMemo(), "-"))
                .build();
    }

    private List<Order> safeOrders(Task task) {
        return task.getOrders() == null ? List.of() : task.getOrders();
    }

    private int categoryOrderIndex(String category) {
        int index = TASK_CATEGORY_ORDER.indexOf(category);
        return index >= 0 ? index : TASK_CATEGORY_ORDER.size();
    }

    private String extractCategory(Order order, Map<String, Object> optionMap) {
        if (order == null) {
            return "미분류";
        }

        Object categoryValue = optionMap != null ? optionMap.get("카테고리") : null;
        if (categoryValue != null && StringUtils.hasText(categoryValue.toString())) {
            return categoryValue.toString().trim();
        }

        if (order.getProductCategory() != null && StringUtils.hasText(order.getProductCategory().getName())) {
            return order.getProductCategory().getName().trim();
        }

        return "미분류";
    }

    private Map<String, Object> parseOrderOptionMap(String optionJson) {
        if (!StringUtils.hasText(optionJson)) {
            return Map.of();
        }

        try {
            return objectMapper.readValue(optionJson, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception ignore) {
            return Map.of();
        }
    }

    private String resolveCustomerProductName(OrderItem item, Map<String, Object> optionMap) {
        // 고객 발주 상세 화면과 동일하게 OrderItem.productName을 제품명의 기준값으로 사용합니다.
        // optionJson 제품명은 OrderItem.productName이 비어 있는 예외 데이터의 표시 보조값으로만 사용합니다.
        String itemProductName = item != null ? normalizeMeaningfulProductName(item.getProductName()) : null;
        if (StringUtils.hasText(itemProductName)) {
            return itemProductName;
        }

        String optionProductName = normalizeMeaningfulProductName(pickFirstOptionValue(optionMap, List.of(
                "제품명", "제품", "productName", "product", "ProductName", "Product", "product_name")));
        return safeText(optionProductName, "-");
    }

    private String normalizeMeaningfulProductName(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        String normalized = OrderProductNameDisplayUtil.toDisplayName(value);
        if (!StringUtils.hasText(normalized)
                || "-".equals(normalized)
                || "제품명없음".equals(normalized)
                || "제품없음".equals(normalized)) {
            return null;
        }
        return normalized;
    }

    private String resolveCustomerProductSize(Map<String, Object> optionMap) {
        String directSize = pickFirstOptionValue(optionMap, List.of(
                "사이즈", "규격", "크기", "제품사이즈", "제품규격", "규격사이즈",
                "productSize", "ProductSize", "size", "Size"));
        if (StringUtils.hasText(directSize)) {
            return directSize.trim();
        }

        List<String> dimensions = new ArrayList<>();
        String width = pickFirstOptionValue(optionMap, List.of("가로", "가로사이즈", "width", "Width"));
        String height = pickFirstOptionValue(optionMap, List.of("세로", "세로사이즈", "height", "Height"));
        String depth = pickFirstOptionValue(optionMap, List.of("깊이", "깊이사이즈", "depth", "Depth"));

        if (StringUtils.hasText(width)) {
            dimensions.add(width.trim());
        }
        if (StringUtils.hasText(height)) {
            dimensions.add(height.trim());
        }
        if (StringUtils.hasText(depth)) {
            dimensions.add(depth.trim());
        }

        return dimensions.isEmpty() ? "-" : String.join(" × ", dimensions);
    }

    private String resolveCustomerProductColor(Map<String, Object> optionMap) {
        return safeText(pickFirstOptionValue(optionMap, List.of(
                "색상", "컬러", "제품색상", "productColor", "ProductColor", "color", "Color")), "-");
    }

    private String pickFirstOptionValue(Map<String, Object> optionMap, List<String> keys) {
        if (optionMap == null || optionMap.isEmpty() || keys == null || keys.isEmpty()) {
            return null;
        }

        for (String key : keys) {
            Object value = optionMap.get(key);
            if (value != null && StringUtils.hasText(value.toString())) {
                return value.toString().trim();
            }
        }
        return null;
    }

    private StatusSummary summarizeOrderStatus(List<Order> orders) {
        LinkedHashSet<OrderStatus> statuses = orders.stream()
                .map(Order::getStatus)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (statuses.isEmpty()) {
            return new StatusSummary("NONE", "-");
        }

        if (statuses.size() == 1) {
            OrderStatus status = statuses.iterator().next();
            return new StatusSummary(status.name(), status.getLabel());
        }

        return new StatusSummary("MIXED", "혼합 상태");
    }

    private boolean matchesAsText(AsListRow row, AsListFilter filter) {
        String keyword = normalizeKeyword(filter.getKeyword());
        if (keyword == null) {
            return true;
        }

        String type = normalizeKeyword(filter.getTextType());
        AsTask task = row.getAsTask();

        if ("id".equals(type)) {
            try {
                String digits = keyword.replaceAll("[^0-9]", "");
                if (digits.isBlank()) {
                    return false;
                }
                return Objects.equals(task.getId(), Long.valueOf(digits));
            } catch (NumberFormatException e) {
                return false;
            }
        }
        if ("applicantName".equals(type)) {
            return containsIgnoreCase(task.getApplicantName(), keyword);
        }
        if ("subject".equals(type)) {
            return containsIgnoreCase(task.getSubject(), keyword);
        }
        if ("productName".equals(type)) {
            return containsIgnoreCase(task.getProductName(), keyword);
        }

        return containsIgnoreCase(task.getCustomerName(), keyword);
    }

    private boolean matchesAsDate(AsListRow row, AsListFilter filter) {
        LocalDate start = filter.getStartDate();
        LocalDate end = filter.getEndDate();
        if (start == null && end == null) {
            return true;
        }

        String type = normalizeKeyword(filter.getDateType());
        LocalDate target;

        if ("scheduled".equals(type)) {
            target = row.getScheduledDate();
        } else if ("processed".equals(type)) {
            target = toDate(row.getAsTask().getAsProcessDate());
        } else {
            target = toDate(row.getAsTask().getRequestedAt());
        }

        return isDateInRange(target, start, end);
    }

    private boolean matchesAsBilling(AsListRow row, String billingType) {
        String normalized = normalizeKeyword(billingType);
        if (normalized == null || "all".equals(normalized)) {
            return true;
        }

        boolean paid = row.getAsTask().getPrice() > 0;
        if ("paid".equals(normalized)) {
            return paid;
        }
        if ("free".equals(normalized)) {
            return !paid;
        }
        return true;
    }

    private boolean matchesAsStatus(AsListRow row, String statusValue) {
        String normalized = normalizeKeyword(statusValue);
        if (normalized == null || "all".equals(normalized)) {
            return true;
        }

        try {
            return row.getAsTask().getStatus() == AsStatus.valueOf(normalized.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    private boolean matchesAsRegion(AsListRow row, RegionFilter region) {
        if (!region.hasAny()) {
            return true;
        }

        AsTask task = row.getAsTask();
        return region.matches(task.getDoName(), task.getSiName(), task.getGuName());
    }

    private boolean matchesTaskText(TaskListRow row, TaskListFilter filter) {
        String keyword = normalizeKeyword(filter.getKeyword());
        if (keyword == null) {
            return true;
        }

        String type = normalizeKeyword(filter.getTextType());
        if ("productName".equals(type)) {
            return safeOrders(row.getTask()).stream()
                    .map(Order::getOrderItem)
                    .filter(Objects::nonNull)
                    .anyMatch(item -> containsIgnoreCase(item.getProductName(), keyword));
        }
        if ("ordererPhone".equals(type)) {
            return containsDigits(row.getOrdererPhone(), keyword);
        }
        if ("taskId".equals(type)) {
            try {
                String digits = keyword.replaceAll("[^0-9]", "");
                if (digits.isBlank()) {
                    return false;
                }
                return Objects.equals(row.getTask().getId(), Long.valueOf(digits));
            } catch (NumberFormatException e) {
                return false;
            }
        }
        if ("orderId".equals(type)) {
            try {
                Long orderId = Long.valueOf(keyword.replaceAll("[^0-9]", ""));
                return safeOrders(row.getTask()).stream()
                        .anyMatch(order -> Objects.equals(order.getId(), orderId));
            } catch (NumberFormatException e) {
                return false;
            }
        }

        return containsIgnoreCase(row.getOrdererName(), keyword);
    }

    private boolean matchesTaskDate(TaskListRow row, TaskListFilter filter) {
        LocalDate start = filter.getStartDate();
        LocalDate end = filter.getEndDate();
        if (start == null && end == null) {
            return true;
        }

        String dateType = normalizeKeyword(filter.getDateType());
        if ("delivery".equals(dateType)) {
            return isDateInRange(toDate(row.getDeliveryDate()), start, end);
        }

        return isDateInRange(toDate(row.getTask().getCreatedAt()), start, end);
    }

    private boolean matchesTaskStatus(TaskListRow row, String statusValue) {
        String normalized = normalizeKeyword(statusValue);
        if (normalized == null || "all".equals(normalized)) {
            return true;
        }

        try {
            OrderStatus wanted = OrderStatus.valueOf(normalized.toUpperCase(Locale.ROOT));
            return safeOrders(row.getTask()).stream().anyMatch(order -> order.getStatus() == wanted);
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    private boolean matchesTaskCategory(TaskListRow row, String category) {
        String normalized = normalizeKeyword(category);
        if (normalized == null || "all".equals(normalized)) {
            return true;
        }

        return row.getCategoryCounts().stream().anyMatch(item -> normalized.equals(item.getName()));
    }

    private boolean matchesTaskRegion(TaskListRow row, RegionFilter region) {
        if (!region.hasAny()) {
            return true;
        }

        for (Order order : safeOrders(row.getTask())) {
            if (order == null) {
                continue;
            }

            String doName = hasSiteAddress(order) ? order.getSiteDoName() : order.getDoName();
            String siName = hasSiteAddress(order) ? order.getSiteSiName() : order.getSiName();
            String guName = hasSiteAddress(order) ? order.getSiteGuName() : order.getGuName();

            if (region.matches(doName, siName, guName)) {
                return true;
            }
        }

        return false;
    }

    private Comparator<AsListRow> buildAsComparator(List<SortSpec> specs) {
        if (specs.isEmpty()) {
            return (left, right) -> {
                int compared = compareNullable(left.getAsTask().getRequestedAt(), right.getAsTask().getRequestedAt(), false);
                if (compared != 0) {
                    return compared;
                }
                return compareNullable(left.getAsTask().getId(), right.getAsTask().getId(), false);
            };
        }

        return (left, right) -> {
            for (SortSpec spec : specs) {
                int compared = compareAsField(left, right, spec);
                if (compared != 0) {
                    return compared;
                }
            }
            return compareNullable(left.getAsTask().getId(), right.getAsTask().getId(), false);
        };
    }

    private int compareAsField(AsListRow left, AsListRow right, SortSpec spec) {
        boolean asc = spec.isAscending();
        AsTask a = left.getAsTask();
        AsTask b = right.getAsTask();

        return switch (spec.getField()) {
            case "id" -> compareNullable(a.getId(), b.getId(), asc);
            case "customerName" -> compareNullable(normalizeForSort(a.getCustomerName()), normalizeForSort(b.getCustomerName()), asc);
            case "requestedAt" -> compareNullable(a.getRequestedAt(), b.getRequestedAt(), asc);
            case "scheduledDate" -> compareNullable(left.getScheduledDate(), right.getScheduledDate(), asc);
            case "processedAt" -> compareNullable(a.getAsProcessDate(), b.getAsProcessDate(), asc);
            case "price" -> compareNullable(a.getPrice(), b.getPrice(), asc);
            case "status" -> compareNullable(statusOrdinal(a.getStatus()), statusOrdinal(b.getStatus()), asc);
            case "productInfo" -> compareNullable(normalizeForSort(left.getProductInfo()), normalizeForSort(right.getProductInfo()), asc);
            default -> 0;
        };
    }

    private Comparator<TaskListRow> buildTaskComparator(List<SortSpec> specs) {
        if (specs.isEmpty()) {
            return (left, right) -> {
                int compared = compareNullable(left.getTask().getCreatedAt(), right.getTask().getCreatedAt(), false);
                if (compared != 0) {
                    return compared;
                }
                return compareNullable(left.getTask().getId(), right.getTask().getId(), false);
            };
        }

        return (left, right) -> {
            for (SortSpec spec : specs) {
                int compared = compareTaskField(left, right, spec);
                if (compared != 0) {
                    return compared;
                }
            }
            return compareNullable(left.getTask().getId(), right.getTask().getId(), false);
        };
    }

    private int compareTaskField(TaskListRow left, TaskListRow right, SortSpec spec) {
        boolean asc = spec.isAscending();

        return switch (spec.getField()) {
            case "id" -> compareNullable(left.getTask().getId(), right.getTask().getId(), asc);
            case "ordererName" -> compareNullable(normalizeForSort(left.getOrdererName()), normalizeForSort(right.getOrdererName()), asc);
            case "createdAt" -> compareNullable(left.getTask().getCreatedAt(), right.getTask().getCreatedAt(), asc);
            case "deliveryDate" -> compareNullable(left.getDeliveryDate(), right.getDeliveryDate(), asc);
            case "totalPrice" -> compareNullable(left.getTask().getTotalPrice(), right.getTask().getTotalPrice(), asc);
            case "status" -> compareNullable(taskStatusSortValue(left), taskStatusSortValue(right), asc);
            case "deliveryMethod" -> compareNullable(normalizeForSort(left.getDeliveryMethodName()), normalizeForSort(right.getDeliveryMethodName()), asc);
            case "address" -> compareNullable(normalizeForSort(left.getDeliveryAddress()), normalizeForSort(right.getDeliveryAddress()), asc);
            default -> 0;
        };
    }

    private Integer taskStatusSortValue(TaskListRow row) {
        if (row == null || "MIXED".equals(row.getStatusKey()) || row.getRepresentativeOrder() == null
                || row.getRepresentativeOrder().getStatus() == null) {
            return Integer.MAX_VALUE;
        }
        return row.getRepresentativeOrder().getStatus().ordinal();
    }

    private Integer statusOrdinal(AsStatus status) {
        return status == null ? Integer.MAX_VALUE : status.ordinal();
    }

    private <T extends Comparable<? super T>> int compareNullable(T left, T right, boolean asc) {
        if (left == null && right == null) {
            return 0;
        }
        if (left == null) {
            return 1;
        }
        if (right == null) {
            return -1;
        }

        int value = left.compareTo(right);
        return asc ? value : -value;
    }

    public List<SortSpec> parseAsSort(String raw) {
        return parseSort(raw, AS_SORT_FIELDS);
    }

    public List<SortSpec> parseTaskSort(String raw) {
        return parseSort(raw, TASK_SORT_FIELDS);
    }

    private List<SortSpec> parseSort(String raw, Set<String> allowedFields) {
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }

        LinkedHashMap<String, SortSpec> unique = new LinkedHashMap<>();
        for (String token : raw.split(",")) {
            if (!StringUtils.hasText(token)) {
                continue;
            }

            String[] parts = token.trim().split(":");
            if (parts.length != 2) {
                continue;
            }

            String field = parts[0].trim();
            String direction = parts[1].trim().toLowerCase(Locale.ROOT);
            if (!allowedFields.contains(field)) {
                continue;
            }
            if (!"asc".equals(direction) && !"desc".equals(direction)) {
                continue;
            }

            unique.put(field, new SortSpec(field, direction));
        }

        return new ArrayList<>(unique.values());
    }

    public List<String> describeAsFilters(AsListFilter filter, int totalCount) {
        List<String> descriptions = new ArrayList<>();
        descriptions.add("총 조회 건수: " + totalCount + "건");
        descriptions.add("페이지 표시 크기: " + filter.getSize() + "건");

        if (StringUtils.hasText(filter.getKeyword())) {
            descriptions.add("텍스트 검색: " + asTextTypeLabel(filter.getTextType()) + " = " + filter.getKeyword().trim());
        }
        if (filter.getStartDate() != null || filter.getEndDate() != null) {
            descriptions.add("날짜 검색: " + asDateTypeLabel(filter.getDateType()) + " / "
                    + formatRange(filter.getStartDate(), filter.getEndDate()));
        }
        if (StringUtils.hasText(filter.getBillingType()) && !"all".equals(filter.getBillingType())) {
            descriptions.add("유상/무상: " + ("paid".equals(filter.getBillingType()) ? "유상" : "무상"));
        }
        if (StringUtils.hasText(filter.getStatus()) && !"all".equals(filter.getStatus())) {
            descriptions.add("상태: " + asStatusLabel(filter.getStatus()));
        }

        String region = describeRegion(filter.getProvinceId(), filter.getCityId(), filter.getDistrictId());
        if (region != null) {
            descriptions.add("지역: " + region);
        }

        String sortDescription = describeSort(parseAsSort(filter.getSort()), true);
        descriptions.add("정렬: " + sortDescription);
        return descriptions;
    }

    public List<String> describeTaskFilters(TaskListFilter filter, int totalCount) {
        List<String> descriptions = new ArrayList<>();
        descriptions.add("총 조회 건수: " + totalCount + "건");
        descriptions.add("페이지 표시 크기: " + filter.getSize() + "건");

        if (StringUtils.hasText(filter.getKeyword())) {
            descriptions.add("텍스트 검색: " + taskTextTypeLabel(filter.getTextType()) + " = " + filter.getKeyword().trim());
        }
        if (filter.getStartDate() != null || filter.getEndDate() != null) {
            descriptions.add("날짜 검색: " + taskDateTypeLabel(filter.getDateType()) + " / "
                    + formatRange(filter.getStartDate(), filter.getEndDate()));
        }
        if (StringUtils.hasText(filter.getStatus()) && !"all".equals(filter.getStatus())) {
            descriptions.add("오더 상태: " + orderStatusLabel(filter.getStatus()));
        }
        if (StringUtils.hasText(filter.getCategory()) && !"all".equals(filter.getCategory())) {
            descriptions.add("제품 카테고리: " + filter.getCategory());
        }

        String region = describeRegion(filter.getProvinceId(), filter.getCityId(), filter.getDistrictId());
        if (region != null) {
            descriptions.add("배송 지역: " + region);
        }

        descriptions.add("정렬: " + describeSort(parseTaskSort(filter.getSort()), false));
        return descriptions;
    }

    private String describeSort(List<SortSpec> specs, boolean asPage) {
        if (specs.isEmpty()) {
            return asPage ? "신청일 내림차순(기본)" : "발주일 내림차순(기본)";
        }

        return specs.stream()
                .map(spec -> (asPage ? asSortLabel(spec.getField()) : taskSortLabel(spec.getField()))
                        + " " + (spec.isAscending() ? "오름차순" : "내림차순"))
                .collect(Collectors.joining(" > "));
    }

    private String describeRegion(Long provinceId, Long cityId, Long districtId) {
        List<String> parts = new ArrayList<>();
        String province = regionLookupService.getProvinceName(provinceId);
        String city = regionLookupService.getCityName(cityId);
        String district = regionLookupService.getDistrictName(districtId);

        if (StringUtils.hasText(province)) {
            parts.add(province);
        }
        if (StringUtils.hasText(city)) {
            parts.add(city);
        }
        if (StringUtils.hasText(district)) {
            parts.add(district);
        }

        return parts.isEmpty() ? null : String.join(" > ", parts);
    }

    private RegionFilter resolveRegionFilter(Long provinceId, Long cityId, Long districtId) {
        String province = regionLookupService.getProvinceName(provinceId);
        String city = regionLookupService.getCityName(cityId);
        String district = regionLookupService.getDistrictName(districtId);
        return new RegionFilter(province, city, district);
    }

    private Map<Long, LocalDate> getAsScheduleDateMap(List<AsTask> tasks) {
        List<Long> ids = tasks.stream()
                .map(AsTask::getId)
                .filter(Objects::nonNull)
                .toList();

        if (ids.isEmpty()) {
            return Map.of();
        }

        Map<Long, LocalDate> result = new HashMap<>();
        asTaskScheduleRepository.findSimpleByAsTaskIdIn(ids).forEach(value -> {
            if (value.getAsTaskId() != null) {
                result.put(value.getAsTaskId(), value.getScheduledDate());
            }
        });
        return result;
    }

    private String buildAsProductInfo(AsTask task) {
        String name = safeText(task.getProductName(), "-");
        String size = safeText(task.getProductSize(), "-");
        String color = safeText(task.getProductColor(), "-");
        return name + " (" + size + " / " + color + ")";
    }

    private String resolveHandlerName(Member member) {
        if (member == null) {
            return "담당자 배정 전";
        }
        if (StringUtils.hasText(member.getName())) {
            return member.getName().trim();
        }
        if (StringUtils.hasText(member.getUsername())) {
            return member.getUsername().trim();
        }
        return "이름 미등록";
    }

    private String resolveManagerName(Member member) {
        if (member == null) {
            return "미지정";
        }
        return resolveHandlerName(member);
    }

    private String resolveHandlerContact(Member member) {
        if (member == null) {
            return "-";
        }
        if (StringUtils.hasText(member.getPhone())) {
            return member.getPhone().trim();
        }
        if (StringUtils.hasText(member.getTelephone())) {
            return member.getTelephone().trim();
        }
        return "-";
    }

    private String resolveDeliveryMethodName(Order order) {
        if (order == null || order.getDeliveryMethod() == null || !StringUtils.hasText(order.getDeliveryMethod().getMethodName())) {
            return "-";
        }
        return order.getDeliveryMethod().getMethodName().trim();
    }

    private String buildEffectiveDeliveryAddress(Order order) {
        if (order == null) {
            return "-";
        }

        if (hasSiteAddress(order)) {
            return joinAddress(order.getSiteRoadAddress(), order.getSiteDetailAddress());
        }
        return joinAddress(order.getRoadAddress(), order.getDetailAddress());
    }

    private String buildEffectiveDeliveryRegion(Order order) {
        if (order == null) {
            return "-";
        }

        boolean site = hasSiteAddress(order);
        String doName = site ? order.getSiteDoName() : order.getDoName();
        String siName = site ? order.getSiteSiName() : order.getSiName();
        String guName = site ? order.getSiteGuName() : order.getGuName();
        String roadAddress = site ? order.getSiteRoadAddress() : order.getRoadAddress();

        String province = compactProvinceName(doName);
        if (!StringUtils.hasText(province) && looksLikeProvinceToken(siName)) {
            province = compactProvinceName(siName);
        }

        String city = StringUtils.hasText(siName) && !looksLikeProvinceToken(siName) ? siName.trim() : null;
        String district = StringUtils.hasText(guName) ? guName.trim() : null;

        // 고객 목록은 상세 주소가 아니라 "광역단위 + city 단위"까지만 표시합니다.
        // 서울/부산 같은 광역시는 구를, 경기/강원 같은 도 단위는 시/군을 우선합니다.
        String locality;
        if (isMetropolitanProvince(province)) {
            locality = firstDifferentRegion(province, district, city);
        } else {
            locality = firstDifferentRegion(province, city, district);
        }

        List<String> parsed = parseCompactRegionFromRoadAddress(roadAddress);
        if (!StringUtils.hasText(province) && !parsed.isEmpty() && looksLikeProvinceToken(parsed.get(0))) {
            province = compactProvinceName(parsed.get(0));
        }

        if (!StringUtils.hasText(locality)) {
            for (String parsedPart : parsed) {
                if (!sameRegionLabel(province, parsedPart)) {
                    locality = parsedPart;
                    break;
                }
            }
        }

        List<String> result = new ArrayList<>();
        if (StringUtils.hasText(province)) {
            result.add(province);
        }
        if (StringUtils.hasText(locality) && !sameRegionLabel(province, locality)) {
            result.add(locality);
        }

        if (result.isEmpty() && !parsed.isEmpty()) {
            return String.join(" ", parsed);
        }
        return result.isEmpty() ? "-" : String.join(" ", result);
    }

    private String firstDifferentRegion(String province, String... candidates) {
        if (candidates == null) {
            return null;
        }
        for (String candidate : candidates) {
            if (StringUtils.hasText(candidate) && !sameRegionLabel(province, candidate)) {
                return candidate.trim();
            }
        }
        return null;
    }

    private boolean isMetropolitanProvince(String compactProvince) {
        if (!StringUtils.hasText(compactProvince)) {
            return false;
        }
        return Set.of("서울", "부산", "대구", "인천", "광주", "대전", "울산", "세종")
                .contains(compactProvince.trim());
    }

    private List<String> parseCompactRegionFromRoadAddress(String roadAddress) {
        if (!StringUtils.hasText(roadAddress)) {
            return List.of();
        }

        String[] tokens = roadAddress.trim().replaceAll("\\s+", " ").split(" ");
        String province = null;
        String locality = null;
        String neighborhoodFallback = null;

        for (String rawToken : tokens) {
            if (!StringUtils.hasText(rawToken) || rawToken.matches("\\d{5}")) {
                continue;
            }

            String token = rawToken.trim().replaceAll("^[\\(\\[]|[\\)\\],]$", "");
            if (!StringUtils.hasText(token)) {
                continue;
            }

            if (!StringUtils.hasText(province) && looksLikeProvinceToken(token)) {
                province = compactProvinceName(token);
                continue;
            }

            if (isCityToken(token)) {
                // 도 단위 주소는 시가 city 단위이므로 여기서 확정합니다.
                if (!isMetropolitanProvince(province)) {
                    locality = token;
                    break;
                }
                if (!StringUtils.hasText(locality)) {
                    locality = token;
                }
                continue;
            }

            if (isDistrictToken(token)) {
                // 광역시는 구/군이 city 단위입니다. 도 단위에서 시가 없을 때도 군/구를 사용합니다.
                if (isMetropolitanProvince(province) || !StringUtils.hasText(locality)) {
                    locality = token;
                }
                break;
            }

            if (isNeighborhoodToken(token)) {
                neighborhoodFallback = token;
                break;
            }

            // 행정구역을 찾은 뒤 도로명/번지 영역으로 진입하면 더 이상 확장하지 않습니다.
            if (StringUtils.hasText(province) || StringUtils.hasText(locality)) {
                break;
            }
        }

        if (!StringUtils.hasText(locality)) {
            locality = neighborhoodFallback;
        }

        List<String> result = new ArrayList<>();
        if (StringUtils.hasText(province)) {
            result.add(province);
        }
        if (StringUtils.hasText(locality) && !sameRegionLabel(province, locality)) {
            result.add(locality);
        }
        return result;
    }

    private boolean looksLikeProvinceToken(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String normalized = value.trim();
        if (COMPACT_PROVINCE_NAMES.contains(normalized)) {
            return true;
        }
        return normalized.endsWith("특별시")
                || normalized.endsWith("광역시")
                || normalized.endsWith("특별자치시")
                || normalized.endsWith("특별자치도")
                || normalized.endsWith("도");
    }

    private boolean isCityToken(String value) {
        return StringUtils.hasText(value) && value.trim().endsWith("시") && !looksLikeProvinceToken(value);
    }

    private boolean isDistrictToken(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String normalized = value.trim();
        return normalized.endsWith("구") || normalized.endsWith("군");
    }

    private boolean isNeighborhoodToken(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String normalized = value.trim();
        return normalized.endsWith("동") || normalized.endsWith("읍") || normalized.endsWith("면") || normalized.endsWith("리");
    }

    private String compactProvinceName(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        return switch (value.trim()) {
            case "서울특별시" -> "서울";
            case "부산광역시" -> "부산";
            case "대구광역시" -> "대구";
            case "인천광역시" -> "인천";
            case "광주광역시" -> "광주";
            case "대전광역시" -> "대전";
            case "울산광역시" -> "울산";
            case "세종특별자치시" -> "세종";
            case "경기도" -> "경기";
            case "강원도", "강원특별자치도" -> "강원";
            case "충청북도" -> "충북";
            case "충청남도" -> "충남";
            case "전라북도", "전북특별자치도" -> "전북";
            case "전라남도" -> "전남";
            case "경상북도" -> "경북";
            case "경상남도" -> "경남";
            case "제주특별자치도" -> "제주";
            default -> value.trim();
        };
    }

    private boolean sameRegionLabel(String left, String right) {
        if (!StringUtils.hasText(left) || !StringUtils.hasText(right)) {
            return false;
        }
        return normalizeCompactRegionLabel(left).equals(normalizeCompactRegionLabel(right));
    }

    private String normalizeCompactRegionLabel(String value) {
        String normalized = Optional.ofNullable(compactProvinceName(value)).orElse(value).trim().replaceAll("\\s+", "");
        String[] suffixes = { "특별자치시", "특별자치도", "특별시", "광역시", "자치구", "자치군", "시", "구", "군", "도" };
        for (String suffix : suffixes) {
            if (normalized.endsWith(suffix) && normalized.length() > suffix.length()) {
                return normalized.substring(0, normalized.length() - suffix.length());
            }
        }
        return normalized;
    }

    private String lastOrNull(List<String> values) {
        return values == null || values.isEmpty() ? null : values.get(values.size() - 1);
    }

    private boolean hasSiteAddress(Order order) {
        return order != null && StringUtils.hasText(order.getSiteRoadAddress());
    }

    private String joinAddress(String road, String detail) {
        List<String> values = new ArrayList<>();
        if (StringUtils.hasText(road)) {
            values.add(road.trim());
        }
        if (StringUtils.hasText(detail)) {
            values.add(detail.trim());
        }
        return values.isEmpty() ? "-" : String.join(" ", values);
    }

    private boolean isDateInRange(LocalDate target, LocalDate start, LocalDate end) {
        if (target == null) {
            return false;
        }
        if (start != null && target.isBefore(start)) {
            return false;
        }
        if (end != null && target.isAfter(end)) {
            return false;
        }
        return true;
    }

    private LocalDate toDate(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.toLocalDate();
    }

    private boolean containsIgnoreCase(String source, String keyword) {
        if (!StringUtils.hasText(source)) {
            return false;
        }
        return source.toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT));
    }

    private boolean containsDigits(String source, String keyword) {
        String sourceDigits = Optional.ofNullable(source).orElse("").replaceAll("[^0-9]", "");
        String keywordDigits = Optional.ofNullable(keyword).orElse("").replaceAll("[^0-9]", "");
        return !keywordDigits.isBlank() && sourceDigits.contains(keywordDigits);
    }

    private String normalizeKeyword(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String normalizeForSort(String value) {
        return Optional.ofNullable(value).orElse("").trim().toLowerCase(Locale.ROOT);
    }

    private String safeText(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private String formatRange(LocalDate start, LocalDate end) {
        String from = start != null ? start.toString() : "처음부터";
        String to = end != null ? end.toString() : "이후 전체";
        return from + " ~ " + to;
    }

    private String asTextTypeLabel(String type) {
        return switch (Optional.ofNullable(type).orElse("customerName")) {
            case "id" -> "AS ID";
            case "applicantName" -> "신청자명";
            case "subject" -> "제목";
            case "productName" -> "제품명";
            default -> "고객명";
        };
    }

    private String taskTextTypeLabel(String type) {
        return switch (Optional.ofNullable(type).orElse("ordererName")) {
            case "productName" -> "제품명";
            case "ordererPhone" -> "주문자 연락처";
            case "taskId" -> "Task ID";
            case "orderId" -> "Order ID";
            default -> "주문자명";
        };
    }

    private String asDateTypeLabel(String type) {
        return switch (Optional.ofNullable(type).orElse("requested")) {
            case "scheduled" -> "방문예정일";
            case "processed" -> "처리일";
            default -> "신청일";
        };
    }

    private String taskDateTypeLabel(String type) {
        return "delivery".equals(type) ? "배송예정일" : "발주일";
    }

    private String asStatusLabel(String value) {
        try {
            return AsStatus.valueOf(value.toUpperCase(Locale.ROOT)).getLabelKr();
        } catch (Exception e) {
            return value;
        }
    }

    private String orderStatusLabel(String value) {
        try {
            return OrderStatus.valueOf(value.toUpperCase(Locale.ROOT)).getLabel();
        } catch (Exception e) {
            return value;
        }
    }

    private String asSortLabel(String field) {
        return switch (field) {
            case "id" -> "ID";
            case "customerName" -> "고객명";
            case "requestedAt" -> "신청일";
            case "scheduledDate" -> "방문예정일";
            case "processedAt" -> "처리일";
            case "price" -> "금액";
            case "status" -> "상태";
            case "productInfo" -> "제품정보";
            default -> field;
        };
    }

    private String taskSortLabel(String field) {
        return switch (field) {
            case "id" -> "Task ID";
            case "ordererName" -> "주문자명";
            case "createdAt" -> "발주일";
            case "deliveryDate" -> "배송예정일";
            case "totalPrice" -> "금액";
            case "status" -> "상태";
            case "deliveryMethod" -> "배송수단";
            case "address" -> "배송지주소";
            default -> field;
        };
    }

    private <T> Page<T> paginate(List<T> rows, int requestedPage, int requestedSize) {
        int size = normalizePageSize(requestedSize);
        int total = rows.size();
        int maxPage = total == 0 ? 0 : (total - 1) / size;
        int page = Math.min(normalizePage(requestedPage), maxPage);

        int fromIndex = Math.min(page * size, total);
        int toIndex = Math.min(fromIndex + size, total);
        List<T> content = rows.subList(fromIndex, toIndex);

        return new PageImpl<>(content, PageRequest.of(page, size), total);
    }

    private static String normalizeRegion(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        String normalized = value.trim().replaceAll("\\s+", "");
        String[] suffixes = {
                "특별자치도", "특별자치시", "특별시", "광역시", "자치도", "자치시", "자치구", "자치군", "도", "시", "군", "구"
        };

        for (String suffix : suffixes) {
            if (normalized.endsWith(suffix) && normalized.length() > suffix.length()) {
                return normalized.substring(0, normalized.length() - suffix.length());
            }
        }

        return normalized;
    }

    private record StatusSummary(String key, String label) {
    }

    private static final class RegionFilter {
        private final String province;
        private final String city;
        private final String district;

        private RegionFilter(String province, String city, String district) {
            this.province = normalizeRegion(province);
            this.city = normalizeRegion(city);
            this.district = normalizeRegion(district);
        }

        private boolean hasAny() {
            return province != null || city != null || district != null;
        }

        private boolean matches(String doName, String siName, String guName) {
            if (province != null && !Objects.equals(province, normalizeRegion(doName))) {
                return false;
            }
            if (city != null && !Objects.equals(city, normalizeRegion(siName))) {
                return false;
            }
            if (district != null && !Objects.equals(district, normalizeRegion(guName))) {
                return false;
            }
            return true;
        }
    }
}
