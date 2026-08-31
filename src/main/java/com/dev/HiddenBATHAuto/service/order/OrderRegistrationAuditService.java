package com.dev.HiddenBATHAuto.service.order;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dev.HiddenBATHAuto.dto.orderchange.OrderFieldChangeCommand;
import com.dev.HiddenBATHAuto.enums.notification.OrderNotificationAudience;
import com.dev.HiddenBATHAuto.enums.order.OrderChangeSourceArea;
import com.dev.HiddenBATHAuto.enums.order.OrderWorkArea;
import com.dev.HiddenBATHAuto.model.task.Order;
import com.dev.HiddenBATHAuto.model.task.OrderItem;
import com.dev.HiddenBATHAuto.model.task.OrderStatus;

import lombok.RequiredArgsConstructor;

/**
 * 수동 폼·엑셀·향후 고객 직접 발주가 같은 등록 이력 형식을 사용하도록 만든 공통 서비스입니다.
 * 관리자 등록(NORMAL_REGISTER/URGENT_REGISTER)은 REQUESTED 상태라도 등록 알림을 생성합니다.
 * 고객 직접 발주 등 기타 source는 기존처럼 실제 업무 노출 상태에서만 관련자 알림을 생성합니다.
 */
@Service
@RequiredArgsConstructor
public class OrderRegistrationAuditService {

    private final OrderChangeAuditService orderChangeAuditService;

    @Transactional
    public void recordManagementRegistration(
            Order order,
            String actorUsername,
            String operationCode,
            String operationLabel,
            String requestPath
    ) {
        boolean urgentRegistration = isSameDayRegistration(order);
        String baseCode = operationCode == null || operationCode.isBlank()
                ? "MANAGEMENT_ORDER_CREATED"
                : operationCode.trim();
        String classifiedCode = urgentRegistration
                ? "URGENT_REGISTER_" + baseCode
                : "NORMAL_REGISTER_" + baseCode;
        String classifiedLabel = urgentRegistration ? "긴급발주등록" : "일반발주등록";

        recordRegistration(
                order,
                OrderChangeSourceArea.MANAGEMENT,
                null,
                actorUsername,
                null,
                classifiedCode,
                classifiedLabel,
                requestPath
        );
    }

    /**
     * 향후 고객 직접 발주에서는 sourceArea=CUSTOMER와 로그인 고객의 memberId/username/name을 전달합니다.
     * 고객 직접 발주의 REQUESTED 알림 정책은 기존 동작을 유지하고, 관리자 일반/긴급 등록만 상태와 무관하게 알림을 생성합니다.
     */
    @Transactional
    public void recordRegistration(
            Order order,
            OrderChangeSourceArea sourceArea,
            Long actorMemberId,
            String actorUsername,
            String actorDisplayName,
            String operationCode,
            String operationLabel,
            String requestPath
    ) {
        if (order == null || order.getId() == null) {
            throw new IllegalArgumentException("등록 이력을 기록할 발주가 없습니다.");
        }

        OrderItem item = order.getOrderItem();
        List<OrderFieldChangeCommand> changes = new ArrayList<>();
        changes.add(change("registration", "발주 등록", null, "발주 #" + order.getId()));
        if (sourceArea == OrderChangeSourceArea.MANAGEMENT) {
            changes.add(change(
                    "registrationType",
                    "발주등록 구분",
                    null,
                    isSameDayRegistration(order) ? "긴급발주등록" : "일반발주등록"
            ));
        }
        changes.add(change(
                "status",
                "발주상태",
                null,
                order.getStatus() != null ? order.getStatus().getLabel() : null
        ));
        changes.add(change(
                "productName",
                "제품명",
                null,
                item != null ? item.getProductName() : null
        ));
        changes.add(change("quantity", "수량", null, order.getQuantity()));
        changes.add(change(
                "preferredDeliveryDate",
                "배송희망일",
                null,
                order.getPreferredDeliveryDate()
        ));
        changes.add(change(
                "deliveryMethod",
                "배송수단",
                null,
                order.getDeliveryMethod() != null ? order.getDeliveryMethod().getMethodName() : null
        ));
        changes.add(change(
                "assignedDeliveryHandler",
                "배송담당자",
                null,
                memberLabel(order)
        ));

        boolean managementRegistration = isManagementRegistration(sourceArea, operationCode);
        OrderNotificationAudience audience = managementRegistration || isVisibleToWorkTeams(order.getStatus())
                ? OrderNotificationAudience.RELATED_USERS
                : OrderNotificationAudience.NONE;

        orderChangeAuditService.recordOrderChange(
                order,
                sourceArea == null ? OrderChangeSourceArea.SYSTEM : sourceArea,
                actorMemberId,
                actorUsername,
                actorDisplayName,
                operationCode,
                operationLabel,
                requestPath,
                changes,
                audience,
                null,
                null
        );
    }

    private OrderFieldChangeCommand change(
            String fieldKey,
            String fieldLabel,
            Object beforeValue,
            Object afterValue
    ) {
        return OrderFieldChangeCommand.of(
                fieldKey,
                fieldLabel,
                beforeValue,
                afterValue,
                OrderWorkArea.values()
        );
    }

    /**
     * 관리자 등록 시 등록일과 배송희망일의 달력 날짜가 같으면 당일 긴급발주로 분류합니다.
     * 배송희망일/등록일이 없으면 오분류를 피하기 위해 일반발주로 둡니다.
     */
    private boolean isSameDayRegistration(Order order) {
        if (order == null || order.getCreatedAt() == null || order.getPreferredDeliveryDate() == null) {
            return false;
        }
        LocalDate registeredDate = order.getCreatedAt().toLocalDate();
        LocalDate preferredDate = order.getPreferredDeliveryDate().toLocalDate();
        return registeredDate.equals(preferredDate);
    }

    private boolean isManagementRegistration(
            OrderChangeSourceArea sourceArea,
            String operationCode
    ) {
        if (sourceArea != OrderChangeSourceArea.MANAGEMENT || operationCode == null) return false;
        String normalized = operationCode.trim().toUpperCase(java.util.Locale.ROOT);
        return normalized.contains("NORMAL_REGISTER") || normalized.contains("URGENT_REGISTER");
    }

    private boolean isVisibleToWorkTeams(OrderStatus status) {
        return status == OrderStatus.CONFIRMED
                || status == OrderStatus.PRODUCTION_DONE
                || status == OrderStatus.DISPATCH_DONE
                || status == OrderStatus.DELIVERY_DONE;
    }

    private String memberLabel(Order order) {
        if (order == null || order.getAssignedDeliveryHandler() == null) return null;
        var member = order.getAssignedDeliveryHandler();
        String name = member.getName() != null ? member.getName().trim() : "";
        String username = member.getUsername() != null ? member.getUsername().trim() : "";
        if (!name.isEmpty() && !username.isEmpty()) return name + "(" + username + ")";
        if (!name.isEmpty()) return name;
        if (!username.isEmpty()) return username;
        return member.getId() != null ? "MEMBER-" + member.getId() : null;
    }
}
