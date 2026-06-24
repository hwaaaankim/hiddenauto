package com.dev.HiddenBATHAuto.service.order;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.auth.TeamCategory;
import com.dev.HiddenBATHAuto.model.task.Order;
import com.dev.HiddenBATHAuto.model.task.OrderStatus;
import com.dev.HiddenBATHAuto.repository.order.OrderRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrderStatusService {

	private final OrderRepository orderRepository;

	@Transactional
	public int bulkConfirmRequestedOrders(List<Long> orderIds) {
		if (orderIds == null || orderIds.isEmpty()) {
			throw new IllegalArgumentException("컨펌 처리할 오더를 하나 이상 선택해 주세요.");
		}

		List<Long> normalizedOrderIds = orderIds.stream()
				.filter(Objects::nonNull)
				.map(Long::valueOf)
				.collect(Collectors.collectingAndThen(
						Collectors.toCollection(LinkedHashSet::new),
						List::copyOf
				));

		if (normalizedOrderIds.isEmpty()) {
			throw new IllegalArgumentException("컨펌 처리할 오더를 하나 이상 선택해 주세요.");
		}

		List<Order> orders = orderRepository.findAllByIdInForBulkConfirm(normalizedOrderIds);
		Map<Long, Order> orderMap = orders.stream()
				.collect(Collectors.toMap(Order::getId, Function.identity()));

		Set<Long> foundOrderIds = orderMap.keySet();
		Long missingOrderId = normalizedOrderIds.stream()
				.filter(id -> !foundOrderIds.contains(id))
				.findFirst()
				.orElse(null);

		if (missingOrderId != null) {
			throw new IllegalArgumentException(missingOrderId + "번 오더를 찾을 수 없습니다. 목록을 새로고침 후 다시 시도해 주세요.");
		}

		for (Long orderId : normalizedOrderIds) {
			Order order = orderMap.get(orderId);
			OrderStatus currentStatus = order.getStatus();

			if (currentStatus != OrderStatus.REQUESTED) {
				String statusLabel = currentStatus != null ? currentStatus.getLabel() : "상태없음";
				throw new IllegalStateException(orderId + "번 오더는 현재 '" + statusLabel
						+ "' 상태입니다. 고객 발주 상태만 일괄 컨펌할 수 있으니 해당 오더 체크를 해제 후 다시 시도해 주세요.");
			}
		}

		LocalDateTime now = LocalDateTime.now();
		orders.forEach(order -> {
			order.setStatus(OrderStatus.CONFIRMED);
			order.setUpdatedAt(now);
		});

		return orders.size();
	}

	public Page<Order> getOrders(
            LocalDateTime start,
            LocalDateTime end,
            Long categoryId,
            Long assignedMemberId,
            OrderStatus status,
            String dateType,
            Pageable pageable
    ) {
        if ("created".equals(dateType)) {
            return orderRepository.findByCreatedDateRange(
                    categoryId,
                    assignedMemberId,
                    status,
                    start,
                    end,
                    pageable
            );
        }
        return orderRepository.findByPreferredDateRange(
                categoryId,
                assignedMemberId,
                status,
                start,
                end,
                pageable
        );
    }

    public List<Order> getAllOrders(
            LocalDateTime start,
            LocalDateTime end,
            Long categoryId,
            Long assignedMemberId,
            OrderStatus status,
            String dateType
    ) {
        if ("created".equals(dateType)) {
            return orderRepository.findAllByCreatedDateRange(
                    categoryId,
                    assignedMemberId,
                    status,
                    start,
                    end
            );
        }
        return orderRepository.findAllByPreferredDateRange(
                categoryId,
                assignedMemberId,
                status,
                start,
                end
        );
    }
	
	public Page<Order> getOrders(LocalDateTime start, LocalDateTime end, TeamCategory category,
			Member assignedDeliveryHandler, OrderStatus status, String dateType, Pageable pageable) {
		if ("created".equals(dateType)) {
			return orderRepository.findByCreatedDateRange(category, assignedDeliveryHandler, status, start, end,
					pageable);
		} else {
			return orderRepository.findByPreferredDateRange(category, assignedDeliveryHandler, status, start, end,
					pageable);
		}
	}

	public Page<Order> getOrders(
            LocalDateTime start,
            LocalDateTime end,
            TeamCategory category,
            OrderStatus status,
            String dateType,
            Pageable pageable
    ) {
        String dt = (dateType == null) ? "created" : dateType.trim().toLowerCase();

        if ("preferred".equals(dt)) {
            return orderRepository.findProductionListByPreferredDate(category, status, start, end, pageable);
        }
        // 기본 created
        return orderRepository.findProductionListByCreatedDate(category, status, start, end, pageable);
    }

    public List<Order> getAllOrders(
            LocalDateTime start,
            LocalDateTime end,
            TeamCategory category,
            OrderStatus status,
            String dateType,
            Sort sort
    ) {
        String dt = (dateType == null) ? "created" : dateType.trim().toLowerCase();

        if ("preferred".equals(dt)) {
            return orderRepository.findAllProductionListByPreferredDate(category, status, start, end, sort);
        }
        return orderRepository.findAllProductionListByCreatedDate(category, status, start, end, sort);
    }
	public Page<Order> getOrders(LocalDate date, TeamCategory category, OrderStatus status, Pageable pageable) {
		LocalDateTime start = date.atStartOfDay();
		LocalDateTime end = date.atTime(LocalTime.MAX);
		return orderRepository.findOrdersByConditions(category, status, start, end, pageable);
	}

	public List<Order> getAllOrders(LocalDate date, TeamCategory category, OrderStatus status) {
		LocalDateTime start = date.atStartOfDay();
		LocalDateTime end = date.plusDays(1).atStartOfDay(); // inclusive

		return orderRepository.findAllByConditions(category, status, start, end);
	}

	public List<Order> getAllOrders(LocalDateTime start, LocalDateTime end, TeamCategory category, OrderStatus status,
			String dateType) {
		if ("created".equals(dateType)) {
			return orderRepository.findAllByCreatedDateRange(category, status, start, end);
		} else {
			return orderRepository.findAllByPreferredDateRange(category, status, start, end);
		}
	}

	public List<Order> getAllOrders(LocalDateTime start, LocalDateTime end, TeamCategory category, OrderStatus status,
			Long assignedMemberId, String dateType) {
		if ("created".equals(dateType)) {
			return orderRepository.findAllByCreatedDateRange(category, status, assignedMemberId, start, end);
		} else {
			return orderRepository.findAllByPreferredDateRange(category, status, assignedMemberId, start, end);
		}
	}

}
