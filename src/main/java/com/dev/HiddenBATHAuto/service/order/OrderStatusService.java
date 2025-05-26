package com.dev.HiddenBATHAuto.service.order;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.dev.HiddenBATHAuto.model.auth.TeamCategory;
import com.dev.HiddenBATHAuto.model.task.Order;
import com.dev.HiddenBATHAuto.model.task.OrderStatus;
import com.dev.HiddenBATHAuto.repository.order.OrderRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrderStatusService {

	private final OrderRepository orderRepository;

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
}