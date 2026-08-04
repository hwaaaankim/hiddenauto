package com.dev.HiddenBATHAuto.dto.management.delivery;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;

import com.dev.HiddenBATHAuto.model.task.OrderStatus;

/**
 * 관리자 배송관리 목록 전용 DTO 모음입니다.
 *
 * 목록, 넓게보기, 이미지 모달, 엑셀이 같은 묶음 데이터를 사용하도록 화면 전용 모델을 분리합니다.
 */
public final class ManagementDeliveryListDtos {

    private ManagementDeliveryListDtos() {
    }

    public record SearchCondition(
            Long categoryId,
            Long assignedMemberId,
            OrderStatus status,
            String statusForView,
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
            int page,
            int size
    ) {
    }

    public record SearchResult(
            Page<GroupRow> groups,
            long filteredOrderCount,
            List<FilterItem> filters
    ) {
        public SearchResult {
            filters = filters == null ? List.of() : List.copyOf(filters);
        }
    }

    public record GroupRow(
            String groupId,
            Long representativeOrderId,
            List<Long> orderIds,
            String orderIdsText,
            String companyName,
            String requesterNames,
            String address,
            String deliveryMethodName,
            String createdDateText,
            String deliveryDateText,
            String handlerNames,
            int orderCount,
            int totalQuantity,
            String statusCode,
            String statusLabel,
            int imageCount,
            List<OrderRow> orders,
            List<ImageRow> images
    ) {
        public GroupRow {
            orderIds = orderIds == null ? List.of() : List.copyOf(orderIds);
            orders = orders == null ? List.of() : List.copyOf(orders);
            images = images == null ? List.of() : List.copyOf(images);
        }

        public boolean hasImages() {
            return imageCount > 0 && !images.isEmpty();
        }

        public boolean deliveryDone() {
            return "DELIVERY_DONE".equals(statusCode);
        }

        public boolean productionDone() {
            return "PRODUCTION_DONE".equals(statusCode);
        }

        public boolean mixedStatus() {
            return "MIXED".equals(statusCode);
        }
    }

    public record OrderRow(
            Long orderId,
            String statusCode,
            String statusLabel,
            boolean standard,
            String createdDateText,
            String deliveryDateText,
            String requesterName,
            String category,
            String productName,
            String size,
            String color,
            String optionText,
            int quantity,
            String ordererName,
            String ordererPhone,
            String address,
            String deliveryMethodName,
            String handlerName,
            String adminMemo,
            String orderComment,
            String dispatchCompleteMessage
    ) {
    }

    public record ImageRow(
            Long imageId,
            String url,
            String filename
    ) {
    }

    public record FilterItem(
            String label,
            String value
    ) {
    }
}
