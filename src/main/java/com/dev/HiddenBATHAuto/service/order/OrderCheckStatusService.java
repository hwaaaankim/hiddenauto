package com.dev.HiddenBATHAuto.service.order;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Transactional
    public int markChecked(Collection<Long> orderIds, String checkedByUsername) {
        if (orderIds == null || orderIds.isEmpty()) {
            return 0;
        }

        Set<Long> normalizedIds = orderIds.stream()
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (normalizedIds.isEmpty()) {
            return 0;
        }

        List<Order> orders = orderRepository.findAllById(normalizedIds);

        if (orders.isEmpty()) {
            return 0;
        }

        Map<Long, OrderCheckStatus> statusMap = orderCheckStatusRepository.findByOrder_IdIn(normalizedIds)
                .stream()
                .filter(status -> status.getOrder() != null && status.getOrder().getId() != null)
                .collect(Collectors.toMap(status -> status.getOrder().getId(), Function.identity()));

        int changedCount = 0;

        for (Order order : orders) {
            OrderCheckStatus status = statusMap.get(order.getId());

            if (status == null) {
                status = OrderCheckStatus.unchecked(order);
            }

            if (!status.isChecked()) {
                changedCount++;
            }

            status.markChecked(checkedByUsername);
            orderCheckStatusRepository.save(status);
        }

        return changedCount;
    }
}