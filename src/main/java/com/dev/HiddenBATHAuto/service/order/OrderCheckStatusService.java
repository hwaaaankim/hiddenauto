package com.dev.HiddenBATHAuto.service.order;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dev.HiddenBATHAuto.enums.order.OrderCheckState;
import com.dev.HiddenBATHAuto.model.task.Order;
import com.dev.HiddenBATHAuto.model.task.OrderCheckStatus;
import com.dev.HiddenBATHAuto.repository.order.OrderCheckStatusRepository;
import com.dev.HiddenBATHAuto.repository.order.OrderRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrderCheckStatusService {

    private final OrderRepository orderRepository;
    private final OrderCheckStatusRepository orderCheckStatusRepository;

    @Transactional(readOnly = true)
    public Optional<OrderCheckStatus> findByOrderId(Long orderId) {
        if (orderId == null || orderId <= 0) {
            return Optional.empty();
        }

        return orderCheckStatusRepository.findByOrder_Id(orderId);
    }

    @Transactional
    public void ensureUnchecked(Order order) {
        if (order == null || order.getId() == null) {
            return;
        }

        boolean exists = orderCheckStatusRepository.existsByOrder_Id(order.getId());
        if (exists) {
            return;
        }

        orderCheckStatusRepository.save(OrderCheckStatus.unchecked(order));
    }

    @Transactional
    public int markChecked(Collection<Long> orderIds, String checkedByUsername) {
        List<Long> normalizedOrderIds = normalizeOrderIds(orderIds);

        if (normalizedOrderIds.isEmpty()) {
            throw new IllegalArgumentException("체크완료 처리할 오더를 하나 이상 선택해 주세요.");
        }

        Map<Long, OrderCheckStatus> statusMap = orderCheckStatusRepository
                .findByOrderIdInForUpdate(normalizedOrderIds)
                .stream()
                .filter(status -> status.getOrder() != null && status.getOrder().getId() != null)
                .collect(Collectors.toMap(
                        status -> status.getOrder().getId(),
                        status -> status,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        List<Long> missingOrderIds = normalizedOrderIds.stream()
                .filter(orderId -> !statusMap.containsKey(orderId))
                .toList();

        if (!missingOrderIds.isEmpty()) {
            Map<Long, Order> missingOrderMap = orderRepository.findAllById(missingOrderIds)
                    .stream()
                    .filter(order -> order != null && order.getId() != null)
                    .collect(Collectors.toMap(
                            Order::getId,
                            order -> order,
                            (left, right) -> left,
                            LinkedHashMap::new
                    ));

            for (Long orderId : missingOrderIds) {
                Order order = missingOrderMap.get(orderId);
                if (order == null) {
                    throw new IllegalArgumentException("존재하지 않는 오더입니다. orderId=" + orderId);
                }

                OrderCheckStatus newStatus = OrderCheckStatus.unchecked(order);
                statusMap.put(orderId, newStatus);
            }
        }

        int changedCount = 0;
        List<OrderCheckStatus> saveTargets = new ArrayList<>();

        for (Long orderId : normalizedOrderIds) {
            OrderCheckStatus status = statusMap.get(orderId);
            if (status == null) {
                continue;
            }

            status.markChecked(checkedByUsername);
            saveTargets.add(status);
            changedCount++;
        }

        orderCheckStatusRepository.saveAll(saveTargets);
        return changedCount;
    }

    /**
     * 관리자가 생산팀 확인 이후 생산팀이 봐야 하는 항목을 수정한 경우에만 REVISED_AFTER_CHECK로 전환합니다.
     *
     * 기준:
     * 1. 실제 생산 노출 항목이 바뀌지 않았으면 아무 것도 하지 않습니다.
     * 2. 기존 상태가 CHECKED일 때만 REVISED_AFTER_CHECK로 변경합니다.
     * 3. 기존 상태가 UNCHECKED이면 이미 생산팀 확인 전이므로 재수정으로 표시하지 않습니다.
     * 4. 기존 상태가 REVISED_AFTER_CHECK이면 중복 저장 시 revisionCount를 계속 올리지 않도록 유지합니다.
     */
    @Transactional
    public void markRevisedAfterProductionCheckIfNeeded(
            Order order,
            String revisedByUsername,
            boolean productionVisibleChanged,
            String reason
    ) {
        if (order == null || order.getId() == null || !productionVisibleChanged) {
            return;
        }

        OrderCheckStatus status = orderCheckStatusRepository
                .findByOrderIdForUpdate(order.getId())
                .orElse(null);

        if (status == null) {
            orderCheckStatusRepository.save(OrderCheckStatus.unchecked(order));
            return;
        }

        OrderCheckState currentState = status.getResolvedCheckState();

        if (currentState != OrderCheckState.CHECKED) {
            return;
        }

        status.markRevisedAfterCheck(revisedByUsername, reason);
        orderCheckStatusRepository.save(status);
    }

    private List<Long> normalizeOrderIds(Collection<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return List.of();
        }

        Set<Long> normalized = orderIds.stream()
                .filter(Objects::nonNull)
                .filter(id -> id > 0)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        return new ArrayList<>(normalized);
    }
}
