package com.dev.HiddenBATHAuto.service.order;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

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