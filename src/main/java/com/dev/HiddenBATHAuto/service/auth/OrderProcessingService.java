package com.dev.HiddenBATHAuto.service.auth;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.dev.HiddenBATHAuto.dto.OrderRequestItemDTO;
import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.task.Order;
import com.dev.HiddenBATHAuto.model.task.OrderItem;
import com.dev.HiddenBATHAuto.model.task.OrderStatus;
import com.dev.HiddenBATHAuto.model.task.Task;
import com.dev.HiddenBATHAuto.model.task.TaskStatus;
import com.dev.HiddenBATHAuto.repository.order.TaskRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrderProcessingService {

    private final TaskRepository taskRepository;

    public void createTaskWithOrders(Member member, List<OrderRequestItemDTO> items) {
        Task task = new Task();
        task.setRequestedBy(member);
        task.setStatus(TaskStatus.REQUESTED);
        task.setCustomerNote("임시 고객 메모");
        task.setInternalNote("임시 내부 메모");

        List<Order> orderList = new ArrayList<>();

        for (OrderRequestItemDTO dto : items) {
            Order order = new Order();
            order.setTask(task);
            order.setQuantity(dto.getQuantity());
            order.setProductCategory("하부장"); // 예시로 고정
            order.setDeliveryAddress("서울시 강남구 임시주소");
            order.setStatus(OrderStatus.REQUESTED);

            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            orderItem.setProductName("임시 제품명");
            orderItem.setQuantity(dto.getQuantity());
            orderItem.setOptionJson(dto.getOptionJson());

            order.setOrderItems(List.of(orderItem));
            orderList.add(order);
        }

        task.setOrders(orderList);
        taskRepository.save(task);
    }
}
