package com.dev.HiddenBATHAuto.service.order;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.dev.HiddenBATHAuto.dto.task.NonStandardTaskListOrderImageDto;
import com.dev.HiddenBATHAuto.dto.task.NonStandardTaskListOrderRowDto;
import com.dev.HiddenBATHAuto.enums.order.OrderCheckState;
import com.dev.HiddenBATHAuto.model.auth.Company;
import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.auth.TeamCategory;
import com.dev.HiddenBATHAuto.model.caculate.DeliveryMethod;
import com.dev.HiddenBATHAuto.model.task.Order;
import com.dev.HiddenBATHAuto.model.task.OrderCheckStatus;
import com.dev.HiddenBATHAuto.model.task.OrderItem;
import com.dev.HiddenBATHAuto.model.task.OrderStatus;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class NonStandardTaskListViewService {

    private static final String SITE_DELIVERY_METHOD_NAME = "현장배송";

    private final ObjectMapper objectMapper;

    public NonStandardTaskListOrderRowDto toRow(Order order) {
        return toRow(order, List.of());
    }

    public NonStandardTaskListOrderRowDto toRow(
            Order order,
            List<NonStandardTaskListOrderImageDto> adminImages
    ) {
        if (order == null) {
            return null;
        }

        Member requester = order.getTask() != null ? order.getTask().getRequestedBy() : null;
        Company company = requester != null ? requester.getCompany() : null;
        TeamCategory productCategory = order.getProductCategory();
        DeliveryMethod deliveryMethod = order.getDeliveryMethod();
        Member deliveryHandler = order.getAssignedDeliveryHandler();
        OrderStatus orderStatus = order.getStatus();
        OrderItem orderItem = order.getOrderItem();
        OrderCheckStatus checkStatus = order.getCheckStatus();

        OrderCheckState checkState = resolveCheckState(checkStatus);
        boolean latestChecked = checkState.isLatestChecked();
        boolean revisedAfterCheck = checkState == OrderCheckState.REVISED_AFTER_CHECK;
        boolean needProductionCheck = checkState.isNeedProductionCheck();
        boolean checked = latestChecked;

        Map<String, String> optionMap = parseOptionMap(orderItem != null ? orderItem.getOptionJson() : null);

        String productName = firstNotBlank(
                orderItem != null ? orderItem.getProductName() : null,
                optionMap.get("제품명"),
                optionMap.get("상품명"),
                optionMap.get("제품시리즈"),
                "-"
        );

        String fullAddress = buildFullAddress(
                order.getZipCode(),
                order.getDoName(),
                order.getSiName(),
                order.getGuName(),
                order.getRoadAddress(),
                order.getDetailAddress()
        );

        String siteFullAddress = buildFullAddress(
                order.getSiteZipCode(),
                order.getSiteDoName(),
                order.getSiteSiName(),
                order.getSiteGuName(),
                order.getSiteRoadAddress(),
                order.getSiteDetailAddress()
        );

        int supplyPrice = order.getSupplyPrice();
        int totalAmount = order.getTotalAmount();
        int vatPrice = Math.max(0, totalAmount - supplyPrice);

        String ordererSummary = joinNonBlank(" / ", order.getOrdererName(), order.getOrdererPhone());
        String dispatchCompleteMessage = normalizeNullableText(order.getDispatchCompleteMessage());
        String deliveryMethodName = deliveryMethod != null ? safe(deliveryMethod.getMethodName()) : "미지정";
        boolean siteDelivery = SITE_DELIVERY_METHOD_NAME.equals(normalizeNullableText(deliveryMethodName));

        return NonStandardTaskListOrderRowDto.builder()
                .orderId(order.getId())
                .taskId(order.getTask() != null ? order.getTask().getId() : null)

                .companyId(company != null ? company.getId() : null)
                .companyName(company != null ? safe(company.getCompanyName()) : "-")
                .representativeName("")

                .requesterMemberId(requester != null ? requester.getId() : null)
                .requesterName(requester != null ? safe(requester.getName()) : "-")

                .standard(order.isStandard())
                .standardLabel(order.isStandard() ? "규격" : "비규격")
                .mirrorCuttingProduct(order.isMirrorCuttingProduct())

                .productCategoryId(productCategory != null ? productCategory.getId() : null)
                .productCategoryName(productCategory != null ? safe(productCategory.getName()) : "미지정")

                .productName(productName)
                .quantity(order.getQuantity())
                .productCost(order.getProductCost())
                .supplyPrice(supplyPrice)
                .vatPrice(vatPrice)
                .totalAmount(totalAmount)
                .packingCost(order.getPackingCost())
                .deliveryCost(order.getDeliveryCost())
                .productSummary(buildProductSummary(productName, optionMap, order.getQuantity()))
                .optionMap(optionMap)

                .zipCode(order.getZipCode())
                .doName(order.getDoName())
                .siName(order.getSiName())
                .guName(order.getGuName())
                .roadAddress(order.getRoadAddress())
                .detailAddress(order.getDetailAddress())
                .fullAddress(fullAddress.isBlank() ? "-" : fullAddress)

                .siteZipCode(order.getSiteZipCode())
                .siteDoName(order.getSiteDoName())
                .siteSiName(order.getSiteSiName())
                .siteGuName(order.getSiteGuName())
                .siteRoadAddress(order.getSiteRoadAddress())
                .siteDetailAddress(order.getSiteDetailAddress())
                .siteFullAddress(siteFullAddress.isBlank() ? "-" : siteFullAddress)
                .siteDelivery(siteDelivery)

                .ordererName(order.getOrdererName())
                .ordererPhone(order.getOrdererPhone())
                .ordererSummary(ordererSummary.isBlank() ? "-" : ordererSummary)

                .orderComment(order.getOrderComment())
                .adminMemo(order.getAdminMemo())
                .dispatchCompleteMessage(dispatchCompleteMessage)
                .noteSummary(buildNoteSummary(order.getOrderComment(), order.getAdminMemo(), dispatchCompleteMessage))

                .createdAt(order.getCreatedAt())
                .preferredDeliveryDate(order.getPreferredDeliveryDate())

                .deliveryMethodId(deliveryMethod != null ? deliveryMethod.getId() : null)
                .deliveryMethodName(deliveryMethodName)

                .assignedDeliveryHandlerId(deliveryHandler != null ? deliveryHandler.getId() : null)
                .assignedDeliveryHandlerName(deliveryHandler != null ? formatMemberNameWithUsername(deliveryHandler) : "배정 필요함")

                .status(orderStatus)
                .statusName(orderStatus != null ? orderStatus.name() : "")
                .statusLabel(orderStatus != null ? orderStatus.getLabel() : "상태없음")

                .checked(checked)
                .latestChecked(latestChecked)
                .revisedAfterCheck(revisedAfterCheck)
                .needProductionCheck(needProductionCheck)
                .checkState(checkState)
                .checkStateName(checkState.name())
                .checkStateLabel(checkState.getLabel())

                .checkedByUsername(checkStatus != null ? checkStatus.getCheckedByUsername() : null)
                .checkedAt(checkStatus != null ? checkStatus.getCheckedAt() : null)

                .revisionMarkedByUsername(checkStatus != null ? checkStatus.getRevisionMarkedByUsername() : null)
                .revisionMarkedAt(checkStatus != null ? checkStatus.getRevisionMarkedAt() : null)
                .revisionReason(checkStatus != null ? checkStatus.getRevisionReason() : null)
                .revisionCount(checkStatus != null ? checkStatus.getRevisionCount() : 0)

                .adminImages(adminImages != null ? adminImages : List.of())
                .build();
    }

    public List<NonStandardTaskListOrderRowDto> toBulkRows(List<Order> orders) {
        if (orders == null || orders.isEmpty()) {
            return List.of();
        }

        return orders.stream()
                .filter(Objects::nonNull)
                .map(this::toRow)
                .filter(Objects::nonNull)
                .sorted(Comparator
                        .comparingInt((NonStandardTaskListOrderRowDto row) -> row.isNeedProductionCheck() ? 0 : 1)
                        .thenComparingInt(row -> row.isRevisedAfterCheck() ? 0 : 1)
                        .thenComparing(
                                NonStandardTaskListOrderRowDto::getCreatedAt,
                                Comparator.nullsLast(Comparator.reverseOrder())
                        )
                )
                .collect(Collectors.toList());
    }

    private OrderCheckState resolveCheckState(OrderCheckStatus checkStatus) {
        if (checkStatus == null) {
            return OrderCheckState.UNCHECKED;
        }
        OrderCheckState resolved = checkStatus.getResolvedCheckState();
        return resolved != null ? resolved : OrderCheckState.UNCHECKED;
    }

    private Map<String, String> parseOptionMap(String optionJson) {
        if (optionJson == null || optionJson.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, String> parsed = objectMapper.readValue(
                    optionJson,
                    new TypeReference<LinkedHashMap<String, String>>() {}
            );
            return parsed != null ? parsed : new LinkedHashMap<>();
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private String buildProductSummary(String productName, Map<String, String> optionMap, int quantity) {
        String productCode = firstNotBlank(optionMap.get("제품코드"), optionMap.get("상품코드"), null);
        String size = firstNotBlank(optionMap.get("사이즈"), optionMap.get("규격"), null);
        String color = firstNotBlank(optionMap.get("색상"), null);
        String series = firstNotBlank(optionMap.get("제품시리즈"), optionMap.get("시리즈"), null);

        String summary = joinNonBlank(" / ", productName, productCode, series, size, color, quantity > 0 ? "수량 " + quantity : null);
        return summary.isBlank() ? "-" : summary;
    }

    private String buildNoteSummary(String orderComment, String adminMemo, String dispatchCompleteMessage) {
        String summary = joinNonBlank(" / ",
                isBlank(orderComment) ? null : "요청: " + orderComment,
                isBlank(adminMemo) ? null : "관리자: " + adminMemo,
                isBlank(dispatchCompleteMessage) ? null : "출고완료: " + dispatchCompleteMessage
        );
        return summary.isBlank() ? "-" : summary;
    }

    private String buildFullAddress(String zipCode, String doName, String siName, String guName, String roadAddress, String detailAddress) {
        return joinNonBlank(" ", wrapIfNotBlank(zipCode, "(", ")"), doName, siName, guName, roadAddress, detailAddress);
    }

    private String formatMemberNameWithUsername(Member member) {
        if (member == null) {
            return "-";
        }
        String name = normalizeNullableText(member.getName());
        String username = normalizeNullableText(member.getUsername());
        if (name != null && username != null) {
            return name + "(" + username + ")";
        }
        if (name != null) {
            return name;
        }
        if (username != null) {
            return username;
        }
        return "-";
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private String normalizeNullableText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String firstNotBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String wrapIfNotBlank(String value, String prefix, String suffix) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return prefix + value + suffix;
    }

    private String joinNonBlank(String delimiter, String... values) {
        if (values == null) {
            return "";
        }
        return java.util.Arrays.stream(values)
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.joining(delimiter));
    }
}
