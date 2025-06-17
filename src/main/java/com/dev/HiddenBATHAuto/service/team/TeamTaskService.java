package com.dev.HiddenBATHAuto.service.team;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.task.AsStatus;
import com.dev.HiddenBATHAuto.model.task.AsTask;
import com.dev.HiddenBATHAuto.model.task.Order;
import com.dev.HiddenBATHAuto.model.task.OrderStatus;
import com.dev.HiddenBATHAuto.repository.as.AsTaskRepository;
import com.dev.HiddenBATHAuto.repository.order.OrderRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TeamTaskService {

	private final OrderRepository orderRepository;
    private final AsTaskRepository asTaskRepository;

    public Page<Order> getProductionOrdersByDateType(
            List<OrderStatus> statuses,
            Long categoryId,
            String dateType,
            LocalDateTime start,
            LocalDateTime end,
            Pageable pageable) {

        if ("created".equalsIgnoreCase(dateType)) {
            return orderRepository.findByCreatedDateRangeFlexible(statuses, categoryId, start, end, pageable);
        } else {
            // 기본값은 preferred (배송희망일)
            return orderRepository.findByPreferredDateRangeFlexible(statuses, categoryId, start, end, pageable);
        }
    }
    
    public Page<Order> getProductionOrders(List<OrderStatus> statuses, Long categoryId, LocalDate preferredDate, Pageable pageable) {

        LocalDateTime startOfDay = preferredDate.atStartOfDay();
        LocalDateTime endOfDay = preferredDate.plusDays(1).atStartOfDay();

        Page<Order> result = orderRepository.findFilteredOrders(
            statuses,
            categoryId,
            startOfDay,
            endOfDay,
            pageable
        );
        return result;
    }


    public Page<Order> getDeliveryOrders(Member member, LocalDate preferredDate, Pageable pageable) {
        if (!"배송팀".equals(member.getTeam().getName())) throw new AccessDeniedException("접근 불가");

        return orderRepository.findDeliveryOrders(
            List.of(OrderStatus.PRODUCTION_DONE, OrderStatus.DELIVERY_DONE),
            member.getId(),
            preferredDate,
            pageable
        );
    }

    public Page<AsTask> getFilteredAsTasks(
    	    Member handler,
    	    AsStatus status,
    	    String dateType,
    	    LocalDate baseDate,
    	    Pageable pageable
    	) {
    	    LocalDateTime start = (baseDate != null ? baseDate : LocalDate.now()).atStartOfDay();
    	    LocalDateTime end = start.plusDays(1);

    	    return asTaskRepository.findAsTasksByFilter(
    	        handler.getId(),
    	        status,
    	        (dateType != null && dateType.equals("requested")) ? "requested" : "processed", // default "processed"
    	        start,
    	        end,
    	        pageable
    	    );
    	}

    
    public Page<AsTask> getAsTasks(Member handler, LocalDate asDate, Pageable pageable) {
        LocalDateTime start = asDate.atStartOfDay();
        LocalDateTime end = start.plusDays(1);

        return asTaskRepository.findByAssignedHandlerAndDate(
            handler.getId(), start, end, pageable
        );
    }

}
