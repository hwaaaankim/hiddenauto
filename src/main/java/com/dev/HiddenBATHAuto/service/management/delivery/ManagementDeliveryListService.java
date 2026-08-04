package com.dev.HiddenBATHAuto.service.management.delivery;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.dev.HiddenBATHAuto.dto.management.delivery.ManagementDeliveryListDtos.FilterItem;
import com.dev.HiddenBATHAuto.dto.management.delivery.ManagementDeliveryListDtos.GroupRow;
import com.dev.HiddenBATHAuto.dto.management.delivery.ManagementDeliveryListDtos.ImageRow;
import com.dev.HiddenBATHAuto.dto.management.delivery.ManagementDeliveryListDtos.OrderRow;
import com.dev.HiddenBATHAuto.dto.management.delivery.ManagementDeliveryListDtos.SearchCondition;
import com.dev.HiddenBATHAuto.dto.management.delivery.ManagementDeliveryListDtos.SearchResult;
import com.dev.HiddenBATHAuto.model.auth.Company;
import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.auth.TeamCategory;
import com.dev.HiddenBATHAuto.model.caculate.DeliveryMethod;
import com.dev.HiddenBATHAuto.model.task.Order;
import com.dev.HiddenBATHAuto.model.task.OrderImage;
import com.dev.HiddenBATHAuto.model.task.OrderItem;
import com.dev.HiddenBATHAuto.model.task.OrderStatus;
import com.dev.HiddenBATHAuto.repository.auth.MemberRepository;
import com.dev.HiddenBATHAuto.repository.auth.TeamCategoryRepository;
import com.dev.HiddenBATHAuto.repository.caculate.DeliveryMethodRepository;
import com.dev.HiddenBATHAuto.repository.order.OrderRepository;
import com.dev.HiddenBATHAuto.utils.DeliveryAddressNormalizationUtil;
import com.dev.HiddenBATHAuto.utils.DeliveryAddressNormalizationUtil.AddressValue;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

/**
 * 관리자 배송관리 화면 전용 조회/묶음 서비스입니다.
 *
 * 중요:
 * - Order 단위 DB 페이징 후 화면에서 묶으면 페이지 경계에서 동일 묶음이 분리됩니다.
 * - 따라서 모든 필터를 DB에 먼저 적용한 뒤 서버에서
 *   동일업체 + 동일실배송지 + 동일배송수단 + 동일배송일 기준으로 묶고,
 *   마지막에 묶음 단위로 페이징합니다.
 */
@Service
@RequiredArgsConstructor
public class ManagementDeliveryListService {

    private static final DateTimeFormatter VIEW_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private static final Set<Integer> ALLOWED_PAGE_SIZES = Set.of(100, 200, 300, 400, 500);
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "orderId",
            "company",
            "createdDate",
            "deliveryDate",
            "handler",
            "orderCount",
            "status"
    );

    private final OrderRepository orderRepository;
    private final TeamCategoryRepository teamCategoryRepository;
    private final MemberRepository memberRepository;
    private final DeliveryMethodRepository deliveryMethodRepository;
    private final ObjectMapper objectMapper;

    public SearchCondition resolveCondition(
            Long categoryId,
            Long assignedMemberId,
            String status,
            Long deliveryMethodId,
            String dateType,
            LocalDate startDate,
            LocalDate endDate,
            Long orderIdFrom,
            Long orderIdTo,
            String productName,
            String companyName,
            String sortField,
            String sortDir,
            Integer page,
            Integer size
    ) {
        validatePositiveId(orderIdFrom, "오더 ID FROM");
        validatePositiveId(orderIdTo, "오더 ID TO");

        if (orderIdFrom != null && orderIdTo != null && orderIdFrom > orderIdTo) {
            throw new IllegalArgumentException(
                    "오더 ID TO는 FROM보다 크거나 같아야 합니다. 단건 조회는 FROM과 TO를 같은 값으로 입력해 주세요."
            );
        }

        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("종료일은 시작일보다 빠를 수 없습니다.");
        }

        String normalizedDateType = "created".equalsIgnoreCase(safeText(dateType))
                ? "created"
                : "preferred";

        ResolvedStatus resolvedStatus = resolveStatus(status);
        String normalizedSortField = normalizeSortField(sortField);
        String normalizedSortDir = "asc".equalsIgnoreCase(safeText(sortDir)) ? "asc" : "desc";
        int normalizedSize = normalizePageSize(size);
        int normalizedPage = Math.max(page == null ? 0 : page, 0);

        return new SearchCondition(
                categoryId,
                assignedMemberId,
                resolvedStatus.status(),
                resolvedStatus.viewValue(),
                deliveryMethodId,
                normalizedDateType,
                startDate,
                endDate,
                orderIdFrom,
                orderIdTo,
                normalizeNullableText(productName),
                normalizeNullableText(companyName),
                normalizedSortField,
                normalizedSortDir,
                normalizedPage,
                normalizedSize
        );
    }

    @Transactional(readOnly = true)
    public SearchResult search(SearchCondition condition) {
        List<GroupRow> allGroups = findAllGroups(condition);
        int totalGroups = allGroups.size();
        int totalPages = totalGroups == 0
                ? 0
                : (int) Math.ceil(totalGroups / (double) condition.size());
        int safePage = totalPages == 0
                ? 0
                : Math.min(condition.page(), totalPages - 1);
        int fromIndex = Math.min(safePage * condition.size(), totalGroups);
        int toIndex = Math.min(fromIndex + condition.size(), totalGroups);
        List<GroupRow> pageContent = allGroups.subList(fromIndex, toIndex);

        Page<GroupRow> page = new PageImpl<>(
                List.copyOf(pageContent),
                PageRequest.of(safePage, condition.size()),
                totalGroups
        );

        long filteredOrderCount = allGroups.stream()
                .mapToLong(GroupRow::orderCount)
                .sum();

        return new SearchResult(
                page,
                filteredOrderCount,
                buildFilterItems(condition)
        );
    }

    public List<FilterItem> getFilterItems(SearchCondition condition) {
        return buildFilterItems(condition);
    }

    @Transactional(readOnly = true)
    public List<GroupRow> findAllGroups(SearchCondition condition) {
        LocalDateTime start = condition.startDate() != null
                ? condition.startDate().atStartOfDay()
                : null;
        LocalDateTime endExclusive = condition.endDate() != null
                ? condition.endDate().plusDays(1).atStartOfDay()
                : null;

        List<Order> orders = orderRepository.findManagementDeliveryListOrders(
                condition.categoryId(),
                condition.assignedMemberId(),
                condition.status(),
                condition.deliveryMethodId(),
                condition.dateType(),
                start,
                endExclusive,
                condition.orderIdFrom(),
                condition.orderIdTo(),
                condition.productName(),
                condition.companyName()
        );

        LinkedHashMap<String, GroupAccumulator> grouped = new LinkedHashMap<>();

        for (Order order : orders) {
            if (order == null || order.getId() == null) {
                continue;
            }

            GroupKey key = buildGroupKey(order);
            GroupAccumulator accumulator = grouped.computeIfAbsent(
                    key.value(),
                    ignored -> new GroupAccumulator(key)
            );
            accumulator.add(order);
        }

        List<GroupRow> groups = new ArrayList<>(grouped.size());
        int sequence = 1;

        for (GroupAccumulator accumulator : grouped.values()) {
            groups.add(toGroupRow(accumulator, sequence++));
        }

        groups.sort(buildGroupComparator(condition.sortField(), condition.sortDir()));
        return List.copyOf(groups);
    }

    private GroupKey buildGroupKey(Order order) {
        Company company = resolveCompany(order);
        String companyKey;

        if (company != null && company.getId() != null) {
            companyKey = "COMPANY:" + company.getId();
        } else if (company != null && StringUtils.hasText(company.getCompanyName())) {
            companyKey = "COMPANY-NAME:" + normalizeGroupingText(company.getCompanyName());
        } else {
            companyKey = "MISSING-COMPANY-ORDER:" + order.getId();
        }

        AddressValue address = resolveActualAddress(order);
        String addressKey = StringUtils.hasText(address.key())
                ? address.key()
                : "MISSING-ADDRESS-ORDER:" + order.getId();

        DeliveryMethod deliveryMethod = order.getDeliveryMethod();
        String methodKey;

        if (deliveryMethod != null && deliveryMethod.getId() != null) {
            methodKey = "METHOD:" + deliveryMethod.getId();
        } else if (deliveryMethod != null && StringUtils.hasText(deliveryMethod.getMethodName())) {
            methodKey = "METHOD-NAME:" + normalizeGroupingText(deliveryMethod.getMethodName());
        } else {
            methodKey = "MISSING-METHOD-ORDER:" + order.getId();
        }

        LocalDate deliveryDate = order.getPreferredDeliveryDate() != null
                ? order.getPreferredDeliveryDate().toLocalDate()
                : null;
        String dateKey = deliveryDate != null
                ? deliveryDate.toString()
                : "MISSING-DATE-ORDER:" + order.getId();

        return new GroupKey(
                companyKey + "|" + addressKey + "|" + methodKey + "|" + dateKey,
                address,
                deliveryDate
        );
    }

    private GroupRow toGroupRow(GroupAccumulator accumulator, int sequence) {
        List<Order> groupOrders = accumulator.orders.stream()
                .sorted(Comparator.comparing(Order::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        List<OrderRow> orderRows = groupOrders.stream()
                .map(this::toOrderRow)
                .toList();

        List<Long> orderIds = groupOrders.stream()
                .map(Order::getId)
                .filter(Objects::nonNull)
                .toList();

        String orderIdsText = orderIds.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(", "));

        Company company = groupOrders.stream()
                .map(this::resolveCompany)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);

        LinkedHashSet<String> requesterNames = groupOrders.stream()
                .map(this::resolveRequesterName)
                .filter(this::isMeaningfulText)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        LinkedHashSet<String> handlerNames = groupOrders.stream()
                .map(this::resolveHandlerName)
                .filter(this::isMeaningfulText)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        StatusSummary statusSummary = resolveStatusSummary(groupOrders);
        List<ImageRow> images = resolveGroupImages(groupOrders);

        int totalQuantity = orderRows.stream()
                .mapToInt(OrderRow::quantity)
                .sum();

        String createdDateText = buildDateRangeText(
                groupOrders.stream()
                        .map(Order::getCreatedAt)
                        .filter(Objects::nonNull)
                        .map(LocalDateTime::toLocalDate)
                        .toList()
        );

        String deliveryDateText = accumulator.key.deliveryDate() != null
                ? accumulator.key.deliveryDate().format(VIEW_DATE_FORMATTER)
                : "-";

        return new GroupRow(
                "management-delivery-group-" + sequence,
                orderIds.isEmpty() ? null : orderIds.get(0),
                orderIds,
                orderIdsText,
                company != null ? valueOrDash(company.getCompanyName()) : "업체 미확인",
                joinOrDash(requesterNames),
                valueOrDash(accumulator.key.address().display()),
                resolveDeliveryMethodName(groupOrders.get(0)),
                createdDateText,
                deliveryDateText,
                joinOrDash(handlerNames),
                orderRows.size(),
                totalQuantity,
                statusSummary.code(),
                statusSummary.label(),
                images.size(),
                orderRows,
                images
        );
    }

    private OrderRow toOrderRow(Order order) {
        OrderItem item = order.getOrderItem();
        Map<String, Object> optionMap = parseOptionMap(item != null ? item.getOptionJson() : null);
        AddressValue address = resolveActualAddress(order);

        String category = firstNonBlank(
                pickFirst(optionMap, "카테고리", "제품카테고리"),
                order.getProductCategory() != null ? order.getProductCategory().getName() : null
        );
        String productName = item != null ? item.getProductName() : "";
        String size = firstNonBlank(pickFirst(optionMap, "사이즈", "규격", "제품사이즈"), "-");
        String color = firstNonBlank(pickFirst(optionMap, "색상", "컬러", "제품색상"), "-");
        String optionText = buildOptionText(optionMap);
        int quantity = item != null && item.getQuantity() != 0
                ? item.getQuantity()
                : order.getQuantity();
        OrderStatus status = order.getStatus();

        return new OrderRow(
                order.getId(),
                status != null ? status.name() : "UNKNOWN",
                status != null ? status.getLabel() : "상태없음",
                order.isStandard(),
                formatDate(order.getCreatedAt()),
                formatDate(order.getPreferredDeliveryDate()),
                resolveRequesterName(order),
                valueOrDash(category),
                valueOrDash(productName),
                valueOrDash(size),
                valueOrDash(color),
                valueOrDash(optionText),
                quantity,
                valueOrDash(order.getOrdererName()),
                valueOrDash(order.getOrdererPhone()),
                valueOrDash(address.display()),
                resolveDeliveryMethodName(order),
                resolveHandlerName(order),
                valueOrDash(cleanMultilineText(order.getAdminMemo())),
                valueOrDash(cleanMultilineText(order.getOrderComment())),
                valueOrDash(cleanMultilineText(order.getDispatchCompleteMessage()))
        );
    }

    private List<ImageRow> resolveGroupImages(List<Order> groupOrders) {
        /*
         * 업체별 배송완료 처리에서는 같은 업로드 이미지가 선택된 모든 주문에 복사됩니다.
         * 전체 주문의 이미지를 합치면 같은 사진이 주문 수만큼 중복되므로,
         * DELIVERY 이미지가 가장 많이 들어 있는 대표 주문 1건의 이미지 목록을 사용합니다.
         */
        List<OrderImage> representativeImages = List.of();
        Long representativeOrderId = null;

        for (Order order : groupOrders) {
            if (order == null || order.getId() == null || order.getOrderImages() == null) {
                continue;
            }

            List<OrderImage> deliveryImages = order.getOrderImages().stream()
                    .filter(Objects::nonNull)
                    .filter(image -> "DELIVERY".equalsIgnoreCase(safeText(image.getType())))
                    .filter(image -> normalizeNullableText(image.getUrl()) != null)
                    .sorted(Comparator
                            .comparing(OrderImage::getUploadedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                            .thenComparing(OrderImage::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                    .toList();

            boolean replace = deliveryImages.size() > representativeImages.size()
                    || (deliveryImages.size() == representativeImages.size()
                    && !deliveryImages.isEmpty()
                    && (representativeOrderId == null || order.getId() < representativeOrderId));

            if (replace) {
                representativeImages = deliveryImages;
                representativeOrderId = order.getId();
            }
        }

        LinkedHashMap<String, ImageRow> unique = new LinkedHashMap<>();

        for (OrderImage image : representativeImages) {
            String url = normalizeNullableText(image.getUrl());
            if (url == null) {
                continue;
            }

            String key = image.getId() != null ? "ID:" + image.getId() : "URL:" + url;
            unique.putIfAbsent(
                    key,
                    new ImageRow(
                            image.getId(),
                            url,
                            firstNonBlank(image.getFilename(), "배송완료 이미지")
                    )
            );
        }

        return List.copyOf(unique.values());
    }

    private StatusSummary resolveStatusSummary(List<Order> orders) {
        LinkedHashMap<OrderStatus, Long> counts = orders.stream()
                .map(Order::getStatus)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(
                        Function.identity(),
                        LinkedHashMap::new,
                        Collectors.counting()
                ));

        if (counts.isEmpty()) {
            return new StatusSummary("UNKNOWN", "상태없음");
        }

        if (counts.size() == 1) {
            OrderStatus only = counts.keySet().iterator().next();
            return new StatusSummary(only.name(), only.getLabel());
        }

        String label = counts.entrySet().stream()
                .sorted(Comparator.comparingInt(entry -> entry.getKey().ordinal()))
                .map(entry -> entry.getKey().getLabel() + " " + entry.getValue() + "건")
                .collect(Collectors.joining(" / "));

        return new StatusSummary("MIXED", label);
    }

    private Comparator<GroupRow> buildGroupComparator(String sortField, String sortDir) {
        boolean ascending = "asc".equals(sortDir);
        Comparator<GroupRow> comparator = switch (sortField) {
            case "orderId" -> Comparator.comparing(
                    GroupRow::representativeOrderId,
                    nullSafeComparator(ascending)
            );
            case "company" -> Comparator.comparing(
                    group -> normalizeGroupingText(group.companyName()),
                    nullSafeComparator(ascending)
            );
            case "createdDate" -> Comparator.comparing(
                    group -> group.orders().stream()
                            .map(OrderRow::createdDateText)
                            .filter(this::isMeaningfulText)
                            .min(String::compareTo)
                            .orElse(null),
                    nullSafeComparator(ascending)
            );
            case "handler" -> Comparator.comparing(
                    group -> normalizeGroupingText(group.handlerNames()),
                    nullSafeComparator(ascending)
            );
            case "orderCount" -> Comparator.comparing(
                    GroupRow::orderCount,
                    nullSafeComparator(ascending)
            );
            case "status" -> Comparator.comparing(
                    GroupRow::statusCode,
                    nullSafeComparator(ascending)
            );
            case "deliveryDate" -> Comparator.comparing(
                    group -> group.orders().stream()
                            .map(OrderRow::deliveryDateText)
                            .filter(this::isMeaningfulText)
                            .findFirst()
                            .orElse(null),
                    nullSafeComparator(ascending)
            );
            default -> Comparator.comparing(
                    group -> group.orders().stream()
                            .map(OrderRow::deliveryDateText)
                            .filter(this::isMeaningfulText)
                            .findFirst()
                            .orElse(null),
                    Comparator.nullsLast(Comparator.reverseOrder())
            );
        };

        return comparator
                .thenComparing(
                        GroupRow::representativeOrderId,
                        Comparator.nullsLast(Comparator.reverseOrder())
                );
    }

    private <T extends Comparable<? super T>> Comparator<T> nullSafeComparator(boolean ascending) {
        Comparator<T> valueComparator = ascending
                ? Comparator.naturalOrder()
                : Comparator.reverseOrder();
        return Comparator.nullsLast(valueComparator);
    }

    private List<FilterItem> buildFilterItems(SearchCondition condition) {
        String category = condition.categoryId() == null
                ? "전체"
                : teamCategoryRepository.findById(condition.categoryId())
                        .map(TeamCategory::getName)
                        .filter(StringUtils::hasText)
                        .orElse("ID " + condition.categoryId());

        String handler = condition.assignedMemberId() == null
                ? "전체"
                : memberRepository.findById(condition.assignedMemberId())
                        .map(this::resolveMemberName)
                        .orElse("ID " + condition.assignedMemberId());

        String deliveryMethod = condition.deliveryMethodId() == null
                ? "전체"
                : deliveryMethodRepository.findById(condition.deliveryMethodId())
                        .map(DeliveryMethod::getMethodName)
                        .filter(StringUtils::hasText)
                        .orElse("ID " + condition.deliveryMethodId());

        String status = condition.status() != null
                ? condition.status().getLabel()
                : "전체";

        String dateLabel = "created".equals(condition.dateType()) ? "신청일" : "배송일";
        String dateRange = buildLocalDateRangeText(condition.startDate(), condition.endDate());

        return List.of(
                new FilterItem("오더 ID", buildOrderIdRangeText(condition.orderIdFrom(), condition.orderIdTo())),
                new FilterItem("대리점명", valueOrAll(condition.companyName())),
                new FilterItem("제품명", valueOrAll(condition.productName())),
                new FilterItem("오더 상태", status),
                new FilterItem("배송수단", deliveryMethod),
                new FilterItem("제품 카테고리", category),
                new FilterItem("배송 담당자", handler),
                new FilterItem("날짜", dateLabel + " " + dateRange)
        );
    }

    private ResolvedStatus resolveStatus(String status) {
        if (status == null) {
            return new ResolvedStatus(OrderStatus.DELIVERY_DONE, OrderStatus.DELIVERY_DONE.name());
        }

        String normalized = status.trim();
        if (normalized.isBlank() || "all".equalsIgnoreCase(normalized)) {
            return new ResolvedStatus(null, "");
        }

        try {
            OrderStatus parsed = OrderStatus.valueOf(normalized);
            return new ResolvedStatus(parsed, parsed.name());
        } catch (IllegalArgumentException e) {
            return new ResolvedStatus(OrderStatus.DELIVERY_DONE, OrderStatus.DELIVERY_DONE.name());
        }
    }

    private String normalizeSortField(String sortField) {
        String normalized = safeText(sortField);
        return ALLOWED_SORT_FIELDS.contains(normalized) ? normalized : "deliveryDate";
    }

    private int normalizePageSize(Integer size) {
        int resolved = size == null ? 100 : size;
        return ALLOWED_PAGE_SIZES.contains(resolved) ? resolved : 100;
    }

    private void validatePositiveId(Long value, String label) {
        if (value != null && value <= 0) {
            throw new IllegalArgumentException(label + "는 1 이상의 정수로 입력해 주세요.");
        }
    }

    private Map<String, Object> parseOptionMap(String optionJson) {
        if (!StringUtils.hasText(optionJson)) {
            return Map.of();
        }

        try {
            return objectMapper.readValue(
                    optionJson,
                    new TypeReference<LinkedHashMap<String, Object>>() {
                    }
            );
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private String buildOptionText(Map<String, Object> optionMap) {
        if (optionMap == null || optionMap.isEmpty()) {
            return "";
        }

        return optionMap.entrySet().stream()
                .filter(entry -> entry.getKey() != null)
                .filter(entry -> entry.getKey().trim().startsWith("옵션"))
                .map(entry -> safeText(entry.getValue()))
                .filter(StringUtils::hasText)
                .collect(Collectors.joining(" / "));
    }

    private String pickFirst(Map<String, Object> optionMap, String... keys) {
        if (optionMap == null || optionMap.isEmpty() || keys == null) {
            return "";
        }

        for (String key : keys) {
            String value = safeText(optionMap.get(key));
            if (StringUtils.hasText(value)) {
                return value;
            }
        }

        return "";
    }

    private AddressValue resolveActualAddress(Order order) {
        if (order == null) {
            return DeliveryAddressNormalizationUtil.build("", "", "", "", "", "");
        }

        boolean useSiteAddress = DeliveryAddressNormalizationUtil.hasAnyMeaningfulAddressText(
                order.getSiteDoName(),
                order.getSiteSiName(),
                order.getSiteGuName(),
                order.getSiteRoadAddress(),
                order.getSiteDetailAddress()
        );

        if (useSiteAddress) {
            return DeliveryAddressNormalizationUtil.build(
                    order.getSiteZipCode(),
                    order.getSiteDoName(),
                    order.getSiteSiName(),
                    order.getSiteGuName(),
                    order.getSiteRoadAddress(),
                    order.getSiteDetailAddress()
            );
        }

        return DeliveryAddressNormalizationUtil.build(
                order.getZipCode(),
                order.getDoName(),
                order.getSiName(),
                order.getGuName(),
                order.getRoadAddress(),
                order.getDetailAddress()
        );
    }

    private Company resolveCompany(Order order) {
        return order != null
                && order.getTask() != null
                && order.getTask().getRequestedBy() != null
                ? order.getTask().getRequestedBy().getCompany()
                : null;
    }

    private String resolveRequesterName(Order order) {
        return order != null
                && order.getTask() != null
                && order.getTask().getRequestedBy() != null
                ? valueOrDash(resolveMemberName(order.getTask().getRequestedBy()))
                : "-";
    }

    private String resolveHandlerName(Order order) {
        return order != null && order.getAssignedDeliveryHandler() != null
                ? valueOrDash(resolveMemberName(order.getAssignedDeliveryHandler()))
                : "미지정";
    }

    private String resolveMemberName(Member member) {
        return member == null
                ? ""
                : firstNonBlank(member.getName(), member.getUsername());
    }

    private String resolveDeliveryMethodName(Order order) {
        return order != null && order.getDeliveryMethod() != null
                ? valueOrDash(order.getDeliveryMethod().getMethodName())
                : "미지정";
    }

    private String formatDate(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.toLocalDate().format(VIEW_DATE_FORMATTER) : "-";
    }

    private String buildDateRangeText(List<LocalDate> dates) {
        if (dates == null || dates.isEmpty()) {
            return "-";
        }

        LocalDate min = dates.stream().min(LocalDate::compareTo).orElse(null);
        LocalDate max = dates.stream().max(LocalDate::compareTo).orElse(null);

        if (min == null) {
            return "-";
        }

        if (Objects.equals(min, max)) {
            return min.format(VIEW_DATE_FORMATTER);
        }

        return min.format(VIEW_DATE_FORMATTER) + " ~ " + max.format(VIEW_DATE_FORMATTER);
    }

    private String buildLocalDateRangeText(LocalDate start, LocalDate end) {
        if (start == null && end == null) {
            return "전체";
        }
        if (start != null && end != null) {
            return start.format(VIEW_DATE_FORMATTER) + " ~ " + end.format(VIEW_DATE_FORMATTER);
        }
        return start != null
                ? start.format(VIEW_DATE_FORMATTER) + " 이후"
                : end.format(VIEW_DATE_FORMATTER) + " 이전";
    }

    private String buildOrderIdRangeText(Long from, Long to) {
        if (from == null && to == null) {
            return "전체";
        }
        if (from != null && to != null) {
            return from.equals(to) ? String.valueOf(from) : from + " ~ " + to;
        }
        return from != null ? from + " 이상" : to + " 이하";
    }

    private String cleanMultilineText(String value) {
        return safeText(value)
                .replace("\r", " ")
                .replace("\n", " ")
                .replace("\t", " ")
                .replaceAll("\\s{2,}", " ")
                .trim();
    }

    private String normalizeGroupingText(String value) {
        return Normalizer.normalize(safeText(value), Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", "")
                .trim();
    }

    private String normalizeNullableText(String value) {
        String normalized = safeText(value);
        return normalized.isBlank() ? null : normalized;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }

        for (String value : values) {
            if (StringUtils.hasText(value) && !"-".equals(value.trim())) {
                return value.trim();
            }
        }

        return "";
    }

    private String joinOrDash(LinkedHashSet<String> values) {
        if (values == null || values.isEmpty()) {
            return "-";
        }
        String joined = values.stream()
                .filter(this::isMeaningfulText)
                .collect(Collectors.joining(", "));
        return joined.isBlank() ? "-" : joined;
    }

    private boolean isMeaningfulText(String value) {
        return StringUtils.hasText(value) && !"-".equals(value.trim());
    }

    private String valueOrDash(Object value) {
        String text = safeText(value);
        return text.isBlank() ? "-" : text;
    }

    private String valueOrAll(String value) {
        return StringUtils.hasText(value) ? value.trim() : "전체";
    }

    private String safeText(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private record ResolvedStatus(OrderStatus status, String viewValue) {
    }

    private record GroupKey(String value, AddressValue address, LocalDate deliveryDate) {
    }

    private record StatusSummary(String code, String label) {
    }

    private static final class GroupAccumulator {
        private final GroupKey key;
        private final List<Order> orders = new ArrayList<>();

        private GroupAccumulator(GroupKey key) {
            this.key = key;
        }

        private void add(Order order) {
            orders.add(order);
        }
    }
}
