package com.dev.HiddenBATHAuto.service.team.delivery;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.dev.HiddenBATHAuto.dto.delivery.route.DeliveryRouteDtos.Group;
import com.dev.HiddenBATHAuto.dto.delivery.route.DeliveryRouteDtos.OrderRow;
import com.dev.HiddenBATHAuto.dto.delivery.route.DeliveryRouteDtos.Page;
import com.dev.HiddenBATHAuto.dto.delivery.route.DeliveryRouteDtos.PrintRow;
import com.dev.HiddenBATHAuto.model.auth.Company;
import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.task.DeliveryOrderIndex;
import com.dev.HiddenBATHAuto.model.task.Order;
import com.dev.HiddenBATHAuto.model.task.OrderItem;
import com.dev.HiddenBATHAuto.model.task.OrderStatus;
import com.dev.HiddenBATHAuto.repository.order.DeliveryRouteQueryRepository;
import com.dev.HiddenBATHAuto.utils.DeliveryAddressNormalizationUtil;
import com.dev.HiddenBATHAuto.utils.DeliveryAddressNormalizationUtil.AddressValue;
import com.dev.HiddenBATHAuto.utils.DeliveryProductDisplayUtil;
import com.dev.HiddenBATHAuto.utils.OrderItemOptionJsonUtil;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DeliveryRouteService {

    private static final String DELIVERY_TEAM_NAME = "배송팀";
    private static final String DIRECT_METHOD_NAME = "직배송";
    private static final String SITE_METHOD_NAME = "현장배송";
    private static final String FREIGHT_METHOD_NAME = "화물";

    private static final String SECTION_DIRECT = "DIRECT";
    private static final String SECTION_FREIGHT = "FREIGHT";

    private static final int MAX_BULK_COMPLETE_ORDER_COUNT = 200;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final List<OrderStatus> VISIBLE_STATUSES = List.of(
            OrderStatus.CONFIRMED,
            OrderStatus.PRODUCTION_DONE,
            OrderStatus.DISPATCH_DONE,
            OrderStatus.DELIVERY_DONE
    );

    private final DeliveryRouteQueryRepository deliveryRouteQueryRepository;

    /**
     * 당일 배송목록을 업체 + 실제 배송지 + 배송수단 기준으로 묶습니다.
     *
     * 정렬 규칙:
     * - DeliveryOrderIndex.orderIndex가 가장 빠른 주문의 위치가 묶음의 위치가 됩니다.
     * - 같은 업체/같은 주소/같은 배송수단 주문이 뒤쪽 인덱스에 다시 등장해도 최초 묶음으로 합칩니다.
     * - 같은 업체라도 주소 또는 배송수단이 다르면 별도 묶음입니다.
     * - 직배송/현장배송 묶음을 먼저, 화물 묶음을 그 다음에 표시합니다.
     * - 택배/방문/미배송/미지정은 이 화면에서 제외합니다.
     */
    @Transactional(readOnly = true)
    public Page getRoutePage(Member loginMember, LocalDate deliveryDate) {
        validateDeliveryTeamMember(loginMember);

        LocalDate targetDate = deliveryDate == null ? LocalDate.now() : deliveryDate;
        List<DeliveryOrderIndex> allRows = loadRows(loginMember.getId(), targetDate);

        List<DeliveryOrderIndex> directRows = new ArrayList<>();
        List<DeliveryOrderIndex> freightRows = new ArrayList<>();

        for (DeliveryOrderIndex row : allRows) {
            Order order = row != null ? row.getOrder() : null;
            String methodName = normalizedDeliveryMethodName(order);

            if (DIRECT_METHOD_NAME.equals(methodName) || SITE_METHOD_NAME.equals(methodName)) {
                directRows.add(row);
            } else if (FREIGHT_METHOD_NAME.equals(methodName)) {
                freightRows.add(row);
            }
        }

        List<Group> directGroups = buildGroups(directRows, SECTION_DIRECT, 1);
        List<Group> freightGroups = buildGroups(
                freightRows,
                SECTION_FREIGHT,
                directGroups.size() + 1
        );

        int directOrderCount = directGroups.stream().mapToInt(Group::getOrderCount).sum();
        int freightOrderCount = freightGroups.stream().mapToInt(Group::getOrderCount).sum();
        int deliveryDoneCount = directGroups.stream().mapToInt(Group::getDeliveryDoneCount).sum()
                + freightGroups.stream().mapToInt(Group::getDeliveryDoneCount).sum();

        return Page.builder()
                .deliveryDate(targetDate)
                .handlerId(loginMember.getId())
                .handlerName(resolveMemberName(loginMember))
                .directGroups(directGroups)
                .freightGroups(freightGroups)
                .directGroupCount(directGroups.size())
                .freightGroupCount(freightGroups.size())
                .totalGroupCount(directGroups.size() + freightGroups.size())
                .directOrderCount(directOrderCount)
                .freightOrderCount(freightOrderCount)
                .totalOrderCount(directOrderCount + freightOrderCount)
                .deliveryDoneCount(deliveryDoneCount)
                .build();
    }

    /**
     * 업체별 배송 화면에서 선택된 주문을 배송완료 처리하기 전에 서버 기준으로 다시 검증합니다.
     *
     * 검증 항목:
     * - 현재 로그인 배송 담당자의 해당 날짜 DeliveryOrderIndex인지
     * - 직배송/현장배송 + 생산완료(PRODUCTION_DONE) 주문인지
     * - 선택된 모든 주문이 같은 업체/같은 실제 주소/같은 배송수단 묶음인지
     */
    @Transactional(readOnly = true)
    public List<Long> validateCompletionSelection(
            Member loginMember,
            LocalDate deliveryDate,
            List<Long> selectedOrderIds
    ) {
        validateDeliveryTeamMember(loginMember);

        LocalDate targetDate = deliveryDate == null ? LocalDate.now() : deliveryDate;
        List<Long> requestedIds = normalizeOrderIds(selectedOrderIds);

        if (requestedIds.size() > MAX_BULK_COMPLETE_ORDER_COUNT) {
            throw new IllegalArgumentException(
                    "한 번에 배송완료 처리할 수 있는 주문은 최대 "
                            + MAX_BULK_COMPLETE_ORDER_COUNT
                            + "건입니다."
            );
        }

        Map<Long, DeliveryOrderIndex> rowByOrderId = loadRows(loginMember.getId(), targetDate).stream()
                .filter(row -> row.getOrder() != null && row.getOrder().getId() != null)
                .collect(Collectors.toMap(
                        row -> row.getOrder().getId(),
                        row -> row,
                        (a, b) -> a,
                        LinkedHashMap::new
                ));

        List<Long> invalidIds = requestedIds.stream()
                .filter(orderId -> !rowByOrderId.containsKey(orderId))
                .toList();

        if (!invalidIds.isEmpty()) {
            throw new AccessDeniedException(
                    "현재 담당자의 해당 날짜 배송목록에 없는 주문이 포함되어 있습니다: " + invalidIds
            );
        }

        LinkedHashSet<String> groupKeys = new LinkedHashSet<>();

        for (Long orderId : requestedIds) {
            DeliveryOrderIndex indexRow = rowByOrderId.get(orderId);
            Order order = indexRow.getOrder();

            if (!isCompletableOrder(order)) {
                throw new IllegalStateException(
                        "직배송 또는 현장배송의 생산완료 주문만 배송완료 처리할 수 있습니다. "
                                + "선택 항목 중 생산완료가 아닌 주문이 포함되어 있습니다. orderId="
                                + orderId
                );
            }

            groupKeys.add(buildRouteGroupKey(order));
        }

        if (groupKeys.size() != 1) {
            throw new IllegalStateException(
                    "배송완료 일괄처리는 같은 업체, 같은 배송지, 같은 배송수단 주문끼리만 가능합니다."
            );
        }

        return requestedIds;
    }

    /**
     * 기존 배송리스트 DOM 순서대로 A4 인쇄용 데이터를 만듭니다.
     * 현재 담당자와 날짜에 속하지 않는 주문 ID가 섞이면 전체 요청을 거절합니다.
     */
    @Transactional(readOnly = true)
    public List<PrintRow> getPrintRows(
            Member loginMember,
            LocalDate deliveryDate,
            List<Long> orderedOrderIds
    ) {
        validateDeliveryTeamMember(loginMember);

        LocalDate targetDate = deliveryDate == null ? LocalDate.now() : deliveryDate;
        List<Long> requestedIds = orderedOrderIds == null
                ? List.of()
                : orderedOrderIds.stream()
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList();

        if (requestedIds.isEmpty()) {
            throw new IllegalArgumentException("인쇄할 배송 주문이 없습니다.");
        }

        Map<Long, DeliveryOrderIndex> rowByOrderId = loadRows(loginMember.getId(), targetDate).stream()
                .filter(Objects::nonNull)
                .filter(row -> row.getOrder() != null && row.getOrder().getId() != null)
                .collect(Collectors.toMap(
                        row -> row.getOrder().getId(),
                        row -> row,
                        (a, b) -> a,
                        HashMap::new
                ));

        List<Long> invalidIds = requestedIds.stream()
                .filter(orderId -> !rowByOrderId.containsKey(orderId))
                .toList();

        if (!invalidIds.isEmpty()) {
            throw new AccessDeniedException(
                    "현재 담당자의 해당 날짜 배송목록에 없는 주문이 포함되어 있습니다: " + invalidIds
            );
        }

        List<PrintRow> result = new ArrayList<>(requestedIds.size());

        for (Long orderId : requestedIds) {
            DeliveryOrderIndex row = rowByOrderId.get(orderId);
            enrichOrderForDelivery(row.getOrder());
            result.add(toPrintRow(row));
        }

        return result;
    }

    private List<Long> normalizeOrderIds(List<Long> orderIds) {
        List<Long> normalized = orderIds == null
                ? List.of()
                : orderIds.stream()
                        .filter(Objects::nonNull)
                        .filter(orderId -> orderId > 0)
                        .collect(Collectors.toCollection(LinkedHashSet::new))
                        .stream()
                        .toList();

        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("배송완료 처리할 주문을 1개 이상 선택해 주세요.");
        }

        return normalized;
    }

    private List<DeliveryOrderIndex> loadRows(Long handlerId, LocalDate deliveryDate) {
        return deliveryRouteQueryRepository.findRouteRows(handlerId, deliveryDate, VISIBLE_STATUSES).stream()
                .filter(Objects::nonNull)
                .filter(row -> row.getOrder() != null)
                .sorted(Comparator
                        .comparingInt(DeliveryOrderIndex::getOrderIndex)
                        .thenComparingLong(this::safeOrderId))
                .toList();
    }

    private List<Group> buildGroups(
            List<DeliveryOrderIndex> rows,
            String section,
            int sequenceStart
    ) {
        LinkedHashMap<String, GroupAccumulator> grouped = new LinkedHashMap<>();

        for (DeliveryOrderIndex indexRow : rows) {
            if (indexRow == null || indexRow.getOrder() == null) {
                continue;
            }

            Order order = indexRow.getOrder();
            enrichOrderForDelivery(order);

            AddressInfo addressInfo = resolveActualDeliveryAddress(order);
            Company company = resolveCompany(order);
            String methodName = normalizedDeliveryMethodName(order);
            String groupKey = buildRouteGroupKey(order);

            GroupAccumulator accumulator = grouped.computeIfAbsent(
                    groupKey,
                    ignored -> new GroupAccumulator(
                            section,
                            indexRow.getOrderIndex(),
                            valueOrDash(resolveDeliveryMethodDisplayName(order)),
                            resolveCompanyName(company),
                            addressInfo.display(),
                            addressInfo.zipCode(),
                            resolvePrimaryContact(order)
                    )
            );

            accumulator.firstOrderIndex = Math.min(accumulator.firstOrderIndex, indexRow.getOrderIndex());

            OrderRow orderRow = toOrderRow(indexRow, addressInfo.display());
            accumulator.orders.add(orderRow);
            accumulator.totalQuantity += Math.max(0, orderRow.getQuantity());

            if (orderRow.isDeliveryDone()) {
                accumulator.deliveryDoneCount++;
            }

            if (orderRow.isCompletable()) {
                accumulator.completableOrderCount++;
            }

            if (!StringUtils.hasText(accumulator.primaryContact)
                    || "-".equals(accumulator.primaryContact)) {
                accumulator.primaryContact = resolvePrimaryContact(order);
            }

            if (!StringUtils.hasText(accumulator.deliveryMethodName)
                    || "-".equals(accumulator.deliveryMethodName)) {
                accumulator.deliveryMethodName = valueOrDash(methodName);
            }
        }

        List<GroupAccumulator> accumulators = new ArrayList<>(grouped.values());
        accumulators.sort(Comparator.comparingInt(accumulator -> accumulator.firstOrderIndex));

        List<Group> result = new ArrayList<>(accumulators.size());
        int sequence = sequenceStart;
        int domSequence = 1;

        for (GroupAccumulator accumulator : accumulators) {
            accumulator.orders.sort(Comparator
                    .comparingInt(OrderRow::getOrderIndex)
                    .thenComparingLong(order -> safeLong(order.getOrderId())));

            String domId = "delivery-route-"
                    + section.toLowerCase(Locale.ROOT)
                    + "-group-"
                    + domSequence++;

            result.add(Group.builder()
                    .domId(domId)
                    .section(section)
                    .sequence(sequence++)
                    .firstOrderIndex(accumulator.firstOrderIndex)
                    .deliveryMethodName(valueOrDash(accumulator.deliveryMethodName))
                    .companyName(accumulator.companyName)
                    .address(accumulator.address)
                    .zipCode(accumulator.zipCode)
                    .primaryContact(valueOrDash(accumulator.primaryContact))
                    .orders(List.copyOf(accumulator.orders))
                    .orderCount(accumulator.orders.size())
                    .completableOrderCount(accumulator.completableOrderCount)
                    .totalQuantity(accumulator.totalQuantity)
                    .deliveryDoneCount(accumulator.deliveryDoneCount)
                    .build());
        }

        return result;
    }

    private String buildRouteGroupKey(Order order) {
        Long orderId = order != null ? order.getId() : null;
        Company company = resolveCompany(order);
        AddressInfo addressInfo = resolveActualDeliveryAddress(order);
        String methodKey = normalizedDeliveryMethodName(order);

        String companyKey;

        if (company != null && company.getId() != null) {
            companyKey = "COMPANY:" + company.getId();
        } else if (company != null && StringUtils.hasText(company.getCompanyName())) {
            companyKey = "COMPANY-NAME:" + normalizeGeneralKey(company.getCompanyName());
        } else {
            companyKey = "MISSING-COMPANY-ORDER:" + safeLong(orderId);
        }

        String addressKey = StringUtils.hasText(addressInfo.key())
                ? addressInfo.key()
                : "MISSING-ADDRESS-ORDER:" + safeLong(orderId);

        String safeMethodKey = StringUtils.hasText(methodKey)
                ? methodKey
                : "MISSING-METHOD-ORDER:" + safeLong(orderId);

        return safeMethodKey + "|" + companyKey + "|" + addressKey;
    }

    private OrderRow toOrderRow(DeliveryOrderIndex indexRow, String address) {
        Order order = indexRow.getOrder();
        OrderItem item = order.getOrderItem();
        OrderStatus status = order.getStatus();
        int quantity = resolveQuantity(order, item);

        String category = firstNonBlank(
                item != null ? item.getDeliveryCategoryText() : null,
                order.getProductCategory() != null ? order.getProductCategory().getName() : null
        );

        String productName = firstNonBlank(
                item != null ? item.getDeliveryProductName() : null,
                item != null ? item.getProductName() : null
        );

        String quantityText = firstNonBlank(
                item != null ? item.getDeliveryQuantityText() : null,
                quantity > 0 ? "수량 " + quantity + "개" : null
        );

        return OrderRow.builder()
                .orderId(order.getId())
                .taskId(order.getTask() != null ? order.getTask().getId() : null)
                .orderIndex(indexRow.getOrderIndex())
                .status(status != null ? status.name() : "")
                .statusLabel(status != null ? status.getLabel() : "-")
                .deliveryMethodName(valueOrDash(resolveDeliveryMethodDisplayName(order)))
                .address(valueOrDash(address))
                .ordererName(valueOrDash(order.getOrdererName()))
                .ordererPhone(valueOrDash(order.getOrdererPhone()))
                .category(valueOrDash(category))
                .productName(valueOrDash(productName))
                .size(valueOrDash(item != null ? item.getDeliverySizeText() : null))
                .color(valueOrDash(item != null ? item.getDeliveryColorText() : null))
                .quantity(quantity)
                .quantityText(valueOrDash(quantityText))
                .adminMemo(valueOrDash(cleanMemo(order.getAdminMemo())))
                .orderComment(valueOrDash(cleanMemo(order.getOrderComment())))
                .preferredDeliveryDateText(order.getPreferredDeliveryDate() != null
                        ? order.getPreferredDeliveryDate().toLocalDate().format(DATE_FORMATTER)
                        : "-")
                .deliveryDone(status == OrderStatus.DELIVERY_DONE)
                .completable(isCompletableOrder(order))
                .build();
    }

    private PrintRow toPrintRow(DeliveryOrderIndex indexRow) {
        Order order = indexRow.getOrder();
        OrderItem item = order.getOrderItem();
        AddressInfo addressInfo = resolveActualDeliveryAddress(order);
        OrderStatus status = order.getStatus();
        int quantity = resolveQuantity(order, item);

        String category = firstNonBlank(
                item != null ? item.getDeliveryCategoryText() : null,
                order.getProductCategory() != null ? order.getProductCategory().getName() : null
        );

        String productName = firstNonBlank(
                item != null ? item.getDeliveryProductName() : null,
                item != null ? item.getProductName() : null
        );

        String quantityText = firstNonBlank(
                item != null ? item.getDeliveryQuantityText() : null,
                quantity > 0 ? quantity + "개" : null
        );

        return PrintRow.builder()
                .orderId(order.getId())
                .orderIndex(indexRow.getOrderIndex())
                .companyName(resolveCompanyName(resolveCompany(order)))
                .deliveryMethodName(valueOrDash(resolveDeliveryMethodDisplayName(order)))
                .statusLabel(status != null ? status.getLabel() : "-")
                .address(valueOrDash(addressInfo.display()))
                .ordererName(valueOrDash(order.getOrdererName()))
                .ordererPhone(valueOrDash(order.getOrdererPhone()))
                .category(valueOrDash(category))
                .productName(valueOrDash(productName))
                .size(valueOrDash(item != null ? item.getDeliverySizeText() : null))
                .color(valueOrDash(item != null ? item.getDeliveryColorText() : null))
                .quantityText(valueOrDash(quantityText))
                .adminMemo(valueOrDash(cleanMemo(order.getAdminMemo())))
                .build();
    }

    private void enrichOrderForDelivery(Order order) {
        if (order == null || order.getOrderItem() == null) {
            return;
        }

        OrderItemOptionJsonUtil.enrich(order.getOrderItem());
        DeliveryProductDisplayUtil.enrich(order);
    }

    private boolean isCompletableOrder(Order order) {
        if (order == null || order.getStatus() == null) {
            return false;
        }

        String methodName = normalizedDeliveryMethodName(order);
        boolean supportedMethod = DIRECT_METHOD_NAME.equals(methodName)
                || SITE_METHOD_NAME.equals(methodName);

        return supportedMethod
                && order.getStatus() == OrderStatus.PRODUCTION_DONE;
    }

    private AddressInfo resolveActualDeliveryAddress(Order order) {
        if (order == null) {
            return new AddressInfo("", "-", "");
        }

        boolean siteDelivery = SITE_METHOD_NAME.equals(normalizedDeliveryMethodName(order));
        boolean hasSiteAddress = DeliveryAddressNormalizationUtil.hasAnyMeaningfulAddressText(
                order.getSiteDoName(),
                order.getSiteSiName(),
                order.getSiteGuName(),
                order.getSiteRoadAddress(),
                order.getSiteDetailAddress()
        );

        if (siteDelivery && hasSiteAddress) {
            return buildAddressInfo(
                    order.getSiteZipCode(),
                    order.getSiteDoName(),
                    order.getSiteSiName(),
                    order.getSiteGuName(),
                    order.getSiteRoadAddress(),
                    order.getSiteDetailAddress()
            );
        }

        return buildAddressInfo(
                order.getZipCode(),
                order.getDoName(),
                order.getSiName(),
                order.getGuName(),
                order.getRoadAddress(),
                order.getDetailAddress()
        );
    }

    private AddressInfo buildAddressInfo(
            String zipCode,
            String doName,
            String siName,
            String guName,
            String roadAddress,
            String detailAddress
    ) {
        AddressValue normalizedAddress = DeliveryAddressNormalizationUtil.build(
                zipCode,
                doName,
                siName,
                guName,
                roadAddress,
                detailAddress
        );

        return new AddressInfo(
                normalizedAddress.key(),
                normalizedAddress.display(),
                normalizedAddress.zipCode()
        );
    }

    private Company resolveCompany(Order order) {
        if (order == null
                || order.getTask() == null
                || order.getTask().getRequestedBy() == null) {
            return null;
        }

        return order.getTask().getRequestedBy().getCompany();
    }

    private String resolveCompanyName(Company company) {
        return company != null && StringUtils.hasText(company.getCompanyName())
                ? company.getCompanyName().trim()
                : "업체 미확인";
    }

    private String resolvePrimaryContact(Order order) {
        return firstNonBlank(
                order != null ? order.getOrdererPhone() : null,
                order != null
                        && order.getTask() != null
                        && order.getTask().getRequestedBy() != null
                        ? order.getTask().getRequestedBy().getPhone()
                        : null,
                "-"
        );
    }

    private int resolveQuantity(Order order, OrderItem item) {
        if (item != null && item.getQuantity() > 0) {
            return item.getQuantity();
        }

        return order != null ? Math.max(0, order.getQuantity()) : 0;
    }

    private String resolveDeliveryMethodDisplayName(Order order) {
        if (order == null || order.getDeliveryMethod() == null) {
            return "";
        }

        return safeText(order.getDeliveryMethod().getMethodName());
    }

    private String normalizedDeliveryMethodName(Order order) {
        return normalizeMethodName(resolveDeliveryMethodDisplayName(order));
    }

    private String normalizeMethodName(String value) {
        return safeText(value)
                .replaceAll("\\(금액:.*?\\)", "")
                .replaceAll("\\s+", "")
                .trim();
    }


    private String normalizeGeneralKey(String value) {
        return Normalizer.normalize(
                safeText(value),
                Normalizer.Form.NFKC
        )
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", "")
                .trim();
    }

    private String cleanMemo(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }

        return value
                .replace("\r", " ")
                .replace("\n", " ")
                .replace("\t", " ")
                .replaceAll("\\s{2,}", " ")
                .trim();
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

    private String valueOrDash(String value) {
        return StringUtils.hasText(value) ? value.trim() : "-";
    }

    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }

    private long safeLong(Long value) {
        return value == null ? Long.MAX_VALUE : value;
    }

    private long safeOrderId(DeliveryOrderIndex index) {
        return index == null || index.getOrder() == null || index.getOrder().getId() == null
                ? Long.MAX_VALUE
                : index.getOrder().getId();
    }

    private String resolveMemberName(Member member) {
        if (member == null) {
            return "-";
        }

        return valueOrDash(firstNonBlank(member.getName(), member.getUsername()));
    }

    private void validateDeliveryTeamMember(Member member) {
        if (member == null
                || member.getTeam() == null
                || !DELIVERY_TEAM_NAME.equals(member.getTeam().getName())) {
            throw new AccessDeniedException("배송팀만 접근할 수 있습니다.");
        }
    }

    private record AddressInfo(String key, String display, String zipCode) {
    }

    private static class GroupAccumulator {
        private final String section;
        private int firstOrderIndex;
        private String deliveryMethodName;
        private final String companyName;
        private final String address;
        private final String zipCode;
        private String primaryContact;
        private final List<OrderRow> orders = new ArrayList<>();
        private int totalQuantity;
        private int deliveryDoneCount;
        private int completableOrderCount;

        private GroupAccumulator(
                String section,
                int firstOrderIndex,
                String deliveryMethodName,
                String companyName,
                String address,
                String zipCode,
                String primaryContact
        ) {
            this.section = section;
            this.firstOrderIndex = firstOrderIndex;
            this.deliveryMethodName = deliveryMethodName;
            this.companyName = companyName;
            this.address = address;
            this.zipCode = zipCode;
            this.primaryContact = primaryContact;
        }
    }
}
