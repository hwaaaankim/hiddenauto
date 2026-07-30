package com.dev.HiddenBATHAuto.service.order;

import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dev.HiddenBATHAuto.dto.orderchange.OrderFieldChangeCommand;
import com.dev.HiddenBATHAuto.enums.order.OrderChangeSourceArea;
import com.dev.HiddenBATHAuto.enums.order.OrderWorkArea;
import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.task.Order;
import com.dev.HiddenBATHAuto.model.task.OrderStatus;

import lombok.RequiredArgsConstructor;

/**
 * 배송팀/출고팀/생산팀의 기존 서비스가 공통 변경이력을 즉시 기록할 수 있도록 제공하는 실사용 파사드입니다.
 * 변경 서비스의 같은 @Transactional 범위 안에서 호출하면 원본 변경과 이력이 함께 커밋/롤백됩니다.
 */
@Service
@RequiredArgsConstructor
public class OrderOperationalChangeRecorder {

    private final OrderChangeAuditService orderChangeAuditService;

    @Transactional
    public void recordStatusChange(
            Order order,
            OrderChangeSourceArea sourceArea,
            Member actor,
            OrderStatus beforeStatus,
            OrderStatus afterStatus,
            String operationCode,
            String operationLabel,
            String requestPath,
            OrderWorkArea... affectedAreas
    ) {
        orderChangeAuditService.recordOrderChange(
                order,
                sourceArea,
                actor != null ? actor.getId() : null,
                actor != null ? actor.getUsername() : null,
                resolveActorDisplay(actor),
                operationCode,
                operationLabel,
                requestPath,
                List.of(OrderFieldChangeCommand.of(
                        "status",
                        "오더 상태",
                        beforeStatus != null ? beforeStatus.getLabel() : null,
                        afterStatus != null ? afterStatus.getLabel() : null,
                        affectedAreas
                ))
        );
    }

    @Transactional
    public void recordDeliveryHandlerChange(
            Order order,
            OrderChangeSourceArea sourceArea,
            Member actor,
            Member beforeHandler,
            Member afterHandler,
            String operationCode,
            String operationLabel,
            String requestPath
    ) {
        recordDeliveryHandlerChange(
                order,
                sourceArea,
                actor,
                resolveMemberLabel(beforeHandler),
                resolveMemberLabel(afterHandler),
                operationCode,
                operationLabel,
                requestPath
        );
    }

    @Transactional
    public void recordDeliveryHandlerChange(
            Order order,
            OrderChangeSourceArea sourceArea,
            Member actor,
            String beforeHandlerLabel,
            String afterHandlerLabel,
            String operationCode,
            String operationLabel,
            String requestPath
    ) {
        orderChangeAuditService.recordOrderChange(
                order,
                sourceArea,
                actor != null ? actor.getId() : null,
                actor != null ? actor.getUsername() : null,
                resolveActorDisplay(actor),
                operationCode,
                operationLabel,
                requestPath,
                List.of(OrderFieldChangeCommand.of(
                        "assignedDeliveryHandler",
                        "배송담당자",
                        beforeHandlerLabel,
                        afterHandlerLabel,
                        OrderWorkArea.DISPATCH,
                        OrderWorkArea.DELIVERY
                ))
        );
    }

    @Transactional
    public void recordChanges(
            Order order,
            OrderChangeSourceArea sourceArea,
            Member actor,
            String operationCode,
            String operationLabel,
            String requestPath,
            OrderFieldChangeCommand... changes
    ) {
        orderChangeAuditService.recordOrderChange(
                order,
                sourceArea,
                actor != null ? actor.getId() : null,
                actor != null ? actor.getUsername() : null,
                resolveActorDisplay(actor),
                operationCode,
                operationLabel,
                requestPath,
                changes == null ? List.of() : Arrays.asList(changes)
        );
    }

    private String resolveActorDisplay(Member actor) {
        if (actor == null) return null;
        if (actor.getName() != null && !actor.getName().isBlank()) return actor.getName().trim();
        return actor.getUsername();
    }

    private String resolveMemberLabel(Member member) {
        if (member == null) return "미배정";
        String name = member.getName() != null ? member.getName().trim() : "";
        String username = member.getUsername() != null ? member.getUsername().trim() : "";
        if (!name.isEmpty() && !username.isEmpty()) return name + "(" + username + ")";
        if (!name.isEmpty()) return name;
        if (!username.isEmpty()) return username;
        return member.getId() != null ? "MEMBER-" + member.getId() : "미배정";
    }
}
