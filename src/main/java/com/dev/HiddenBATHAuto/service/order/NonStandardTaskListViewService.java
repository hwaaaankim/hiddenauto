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

        Map<String, String> optionMap = parseOptionMap(orderItem != null ? orderItem.getOptionJson() : null);

        String productName = firstNotBlank(
                orderItem != null ? orderItem.getProductName() : null,
                optionMap.get("제품명"),
                optionMap.get("상품명"),
                optionMap.get("제품시리즈"),
                "-"
        );

        String fullAddress = joinNonBlank(" ",
                wrapIfNotBlank(order.getZipCode(), "(", ")"),
                order.getDoName(),
                order.getSiName(),
                order.getGuName(),
                order.getRoadAddress(),
                order.getDetailAddress()
        );

        boolean checked = checkStatus != null && checkStatus.isChecked();

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

                .productCategoryId(productCategory != null ? productCategory.getId() : null)
                .productCategoryName(productCategory != null ? safe(productCategory.getName()) : "미지정")

                .productName(productName)
                .quantity(order.getQuantity())
                .productCost(order.getProductCost())
                .productSummary(buildProductSummary(productName, optionMap, order.getQuantity()))
                .optionMap(optionMap)

                .zipCode(order.getZipCode())
                .doName(order.getDoName())
                .siName(order.getSiName())
                .guName(order.getGuName())
                .roadAddress(order.getRoadAddress())
                .detailAddress(order.getDetailAddress())
                .fullAddress(fullAddress.isBlank() ? "-" : fullAddress)

                .orderComment(order.getOrderComment())
                .adminMemo(order.getAdminMemo())
                .noteSummary(buildNoteSummary(order.getOrderComment(), order.getAdminMemo()))

                .createdAt(order.getCreatedAt())
                .preferredDeliveryDate(order.getPreferredDeliveryDate())

                .deliveryMethodId(deliveryMethod != null ? deliveryMethod.getId() : null)
                .deliveryMethodName(deliveryMethod != null ? safe(deliveryMethod.getMethodName()) : "미지정")

                .assignedDeliveryHandlerId(deliveryHandler != null ? deliveryHandler.getId() : null)
                .assignedDeliveryHandlerName(deliveryHandler != null ? safe(deliveryHandler.getName()) : "배정 필요함")

                .status(orderStatus)
                .statusName(orderStatus != null ? orderStatus.name() : "")
                .statusLabel(orderStatus != null ? orderStatus.getLabel() : "상태없음")

                .checked(checked)
                .checkedByUsername(checkStatus != null ? checkStatus.getCheckedByUsername() : null)
                .checkedAt(checkStatus != null ? checkStatus.getCheckedAt() : null)

                .adminImages(adminImages != null ? adminImages : List.of())

                .build();
    }
    
    public List<NonStandardTaskListOrderRowDto> toBulkRows(List<Order> orders) {
        return orders.stream()
                .filter(Objects::nonNull)
                .map(this::toRow)
                .sorted(Comparator
                        .comparing(NonStandardTaskListOrderRowDto::isChecked)
                        .thenComparing(
                                NonStandardTaskListOrderRowDto::getCreatedAt,
                                Comparator.nullsLast(Comparator.reverseOrder())
                        ))
                .collect(Collectors.toList());
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

        String summary = joinNonBlank(" / ",
                productName,
                productCode,
                series,
                size,
                color,
                quantity > 0 ? "수량 " + quantity : null
        );

        return summary.isBlank() ? "-" : summary;
    }

    private String buildNoteSummary(String orderComment, String adminMemo) {
        String summary = joinNonBlank(" / ",
                isBlank(orderComment) ? null : "요청: " + orderComment,
                isBlank(adminMemo) ? null : "관리자: " + adminMemo
        );

        return summary.isBlank() ? "-" : summary;
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "-" : value;
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