package com.dev.HiddenBATHAuto.service.order;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dev.HiddenBATHAuto.enums.order.OrderChangeSourceArea;
import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.task.Order;
import com.dev.HiddenBATHAuto.repository.order.OrderRepository;

import lombok.RequiredArgsConstructor;

/**
 * 배송팀 담당자 변경과 공통 오더 변경이력을 하나의 트랜잭션으로 묶습니다.
 * 기존 DeliveryOrderIndexService의 검증/인덱스 정규화 로직은 그대로 사용합니다.
 */
@Service
@RequiredArgsConstructor
public class DeliveryHandlerChangeAuditService {

    private final DeliveryOrderIndexService deliveryOrderIndexService;
    private final OrderRepository orderRepository;
    private final OrderOperationalChangeRecorder changeRecorder;

    @Transactional
    public List<Long> changeDeliveryHandlers(
            Member actor,
            List<Long> orderIds,
            Long newHandlerId
    ) {
        List<Long> normalizedOrderIds = normalizeOrderIds(orderIds);
        Map<Long, String> beforeLabels = loadHandlerLabels(normalizedOrderIds);

        List<Long> changedOrderIds = deliveryOrderIndexService.changeDeliveryHandlers(
                actor,
                orderIds,
                newHandlerId
        );

        recordChangedHandlers(
                actor,
                changedOrderIds,
                beforeLabels,
                "DELIVERY_HANDLER_BULK_CHANGE",
                "배송팀 담당자 일괄 변경",
                "/team/deliveryHandler/bulk"
        );

        return changedOrderIds;
    }

    @Transactional
    public void changeDeliveryHandler(
            Member actor,
            Long orderId,
            Long newHandlerId
    ) {
        List<Long> targetOrderIds = orderId == null ? List.of() : List.of(orderId);
        Map<Long, String> beforeLabels = loadHandlerLabels(targetOrderIds);

        deliveryOrderIndexService.changeDeliveryHandler(actor, orderId, newHandlerId);

        recordChangedHandlers(
                actor,
                targetOrderIds,
                beforeLabels,
                "DELIVERY_HANDLER_CHANGE",
                "배송팀 담당자 변경",
                "/team/deliveryHandler/" + orderId
        );
    }

    private void recordChangedHandlers(
            Member actor,
            List<Long> changedOrderIds,
            Map<Long, String> beforeLabels,
            String operationCode,
            String operationLabel,
            String requestPath
    ) {
        List<Long> normalizedIds = normalizeOrderIds(changedOrderIds);
        if (normalizedIds.isEmpty()) {
            return;
        }

        Map<Long, String> afterLabels = loadHandlerLabels(normalizedIds);
        Map<Long, Order> orderMap = orderRepository.findAllById(normalizedIds).stream()
                .filter(order -> order != null && order.getId() != null)
                .collect(Collectors.toMap(
                        Order::getId,
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        for (Long orderId : normalizedIds) {
            String beforeLabel = beforeLabels.getOrDefault(orderId, "미배정");
            String afterLabel = afterLabels.getOrDefault(orderId, "미배정");

            if (Objects.equals(beforeLabel, afterLabel)) {
                continue;
            }

            Order order = orderMap.get(orderId);
            if (order == null) {
                throw new IllegalStateException("담당자 변경 이력 대상 오더를 찾을 수 없습니다. orderId=" + orderId);
            }

            changeRecorder.recordDeliveryHandlerChange(
                    order,
                    OrderChangeSourceArea.DELIVERY,
                    actor,
                    beforeLabel,
                    afterLabel,
                    operationCode,
                    operationLabel,
                    requestPath
            );
        }
    }

    private Map<Long, String> loadHandlerLabels(Collection<Long> orderIds) {
        List<Long> normalizedIds = normalizeOrderIds(orderIds);
        if (normalizedIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, String> result = new LinkedHashMap<>();

        for (Object[] row : orderRepository.findDeliveryHandlerAuditRows(normalizedIds)) {
            if (row == null || row.length < 4 || row[0] == null) {
                continue;
            }

            Long orderId = ((Number) row[0]).longValue();
            Long handlerId = row[1] instanceof Number number ? number.longValue() : null;
            String username = normalizeText(row[2]);
            String name = normalizeText(row[3]);

            result.put(orderId, buildMemberLabel(handlerId, username, name));
        }

        for (Long orderId : normalizedIds) {
            result.putIfAbsent(orderId, "미배정");
        }

        return result;
    }

    private List<Long> normalizeOrderIds(Collection<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return List.of();
        }

        return orderIds.stream()
                .filter(Objects::nonNull)
                .filter(id -> id > 0)
                .collect(Collectors.collectingAndThen(
                        Collectors.toCollection(LinkedHashSet::new),
                        List::copyOf
                ));
    }

    private String buildMemberLabel(Long handlerId, String username, String name) {
        if (name != null && username != null) {
            return name + "(" + username + ")";
        }
        if (name != null) {
            return name;
        }
        if (username != null) {
            return username;
        }
        return handlerId != null ? "MEMBER-" + handlerId : "미배정";
    }

    private String normalizeText(Object value) {
        if (value == null) {
            return null;
        }
        String normalized = String.valueOf(value).trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
