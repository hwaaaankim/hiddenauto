package com.dev.HiddenBATHAuto.service.order;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.dev.HiddenBATHAuto.dto.OrderRequestItemDTO;
import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.task.Order;
import com.dev.HiddenBATHAuto.model.task.OrderItem;
import com.dev.HiddenBATHAuto.model.task.OrderStatus;
import com.dev.HiddenBATHAuto.model.task.Task;
import com.dev.HiddenBATHAuto.model.task.TaskStatus;
import com.dev.HiddenBATHAuto.repository.nonstandard.ProductColorRepository;
import com.dev.HiddenBATHAuto.repository.nonstandard.ProductOptionPositionRepository;
import com.dev.HiddenBATHAuto.repository.nonstandard.ProductRepository;
import com.dev.HiddenBATHAuto.repository.nonstandard.ProductSeriesRepository;
import com.dev.HiddenBATHAuto.repository.order.TaskRepository;
import com.dev.HiddenBATHAuto.utils.OptionTranslator;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrderProcessingService {

    private final TaskRepository taskRepository;
    private final ProductSeriesRepository productSeriesRepository;
    private final ProductRepository productRepository;
    private final ProductColorRepository productColorRepository;
    private final ProductOptionPositionRepository productOptionPositionRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

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
            order.setProductCategory("하부장"); // 예시, 실제로는 dto에서 받아야 함
            order.setStatus(OrderStatus.REQUESTED);

            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            orderItem.setProductName("임시 제품명");
            orderItem.setQuantity(dto.getQuantity());

            try {
                // ✅ 변환: 영어/숫자 → 한글 값으로 변환
                Map<String, String> localizedMap = OptionTranslator.getLocalizedOptionMap(
                    objectMapper.writeValueAsString(dto.getOptionJson()),
                    productSeriesRepository,
                    productRepository,
                    productColorRepository,
                    productOptionPositionRepository
                );

                // ✅ 다시 JSON 문자열로 변환해서 저장
                String convertedJson = objectMapper.writeValueAsString(localizedMap);
                orderItem.setOptionJson(convertedJson);

            } catch (Exception e) {
                throw new RuntimeException("옵션 변환 실패", e);
            }

            order.setOrderItem(orderItem);
            orderList.add(order);
        }

        task.setOrders(orderList);
        taskRepository.save(task);
    }
}
