package com.dev.HiddenBATHAuto.service.order;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.dev.HiddenBATHAuto.dto.OrderRequestItemDTO;
import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.caculate.DeliveryMethod;
import com.dev.HiddenBATHAuto.model.task.Order;
import com.dev.HiddenBATHAuto.model.task.OrderItem;
import com.dev.HiddenBATHAuto.model.task.OrderStatus;
import com.dev.HiddenBATHAuto.model.task.Task;
import com.dev.HiddenBATHAuto.model.task.TaskStatus;
import com.dev.HiddenBATHAuto.repository.caculate.DeliveryMethodRepository;
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
    private final DeliveryMethodRepository deliveryMethodRepository;
    
    
    public void createTaskWithOrders(Member member, List<OrderRequestItemDTO> items) {
        Task task = new Task();
        task.setRequestedBy(member);
        task.setStatus(TaskStatus.REQUESTED);
        task.setCustomerNote("임시 고객 메모");
        task.setInternalNote("임시 내부 메모");

        List<Order> orderList = new ArrayList<>();
        int totalPrice = 0;

        for (OrderRequestItemDTO dto : items) {
            Order order = new Order();
            order.setTask(task);

            // 1. 기본 수량/가격
            int quantity = dto.getQuantity();
            int productCost = dto.getPrice();
            int deliveryPrice = dto.getDeliveryPrice();
            int singleOrderTotal = quantity * productCost + deliveryPrice;
            totalPrice += singleOrderTotal;

            // 2. 배송지 및 주소 필드 설정
            order.setQuantity(quantity);
            order.setProductCost(productCost);
            order.setZipCode(dto.getZipCode());
//            order.setDoName(dto.getDoName());
//            order.setSiName(dto.getSiName());
//            order.setGuName(dto.getGuName());
            order.setRoadAddress(dto.getMainAddress());
            order.setDetailAddress(dto.getDetailAddress());
            order.setPreferredDeliveryDate(dto.getPreferredDeliveryDate().atStartOfDay());
            refineAddressFromFullRoad(order);
            // 3. 배송수단 엔티티 설정
            DeliveryMethod method = deliveryMethodRepository.findById(dto.getDeliveryMethodId())
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 배송수단 ID: " + dto.getDeliveryMethodId()));
            order.setDeliveryMethod(method);

            // 4. 카테고리 설정 (optionJson에서 '카테고리' key 값)
            String category = dto.getOptionJson() != null && dto.getOptionJson().get("카테고리") != null
            	    ? dto.getOptionJson().get("카테고리").toString()
            	    : "";
            order.setProductCategory(category);

            // 5. 주문 상태
            order.setStatus(OrderStatus.REQUESTED);

            // 6. OrderItem 설정
            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            orderItem.setProductName("임시 제품명");
            orderItem.setQuantity(quantity);

            try {
                // 옵션 변환 후 저장
                Map<String, String> localizedMap = OptionTranslator.getLocalizedOptionMap(
                    objectMapper.writeValueAsString(dto.getOptionJson()),
                    productSeriesRepository,
                    productRepository,
                    productColorRepository,
                    productOptionPositionRepository
                );
                String convertedJson = objectMapper.writeValueAsString(localizedMap);
                orderItem.setOptionJson(convertedJson);

            } catch (Exception e) {
                throw new RuntimeException("옵션 변환 실패", e);
            }

            order.setOrderItem(orderItem);
            orderList.add(order);
        }

        task.setOrders(orderList);
        task.setTotalPrice(totalPrice); // ✅ 총합 저장
        taskRepository.save(task);
    }
    private void refineAddressFromFullRoad(Order order) {
        String full = order.getRoadAddress();
        if (full == null || full.isBlank()) return;

        String[] tokens = full.trim().split("\\s+");

        String doName = "";
        String siName = "";
        String guName = "";

        if (tokens.length >= 1) {
            doName = tokens[0]; // 무조건 첫 단어는 도 (서울, 경기, 광주 등)
        }

        // 이후 토큰에서 '시'와 '구'를 확인
        for (int i = 1; i < tokens.length; i++) {
            String word = tokens[i];

            if (word.endsWith("시") && siName.isBlank()) {
                siName = word;
            } else if (word.endsWith("구") && guName.isBlank()) {
                guName = word;
            }

            if (!siName.isBlank() && !guName.isBlank()) break;
        }

        // 특수 케이스: 서울/광주/부산 등은 시 자체가 없고 구만 있는 경우
        if (siName.isBlank() && guName.isBlank() && tokens.length >= 2) {
            guName = tokens[1]; // 서울 관악구 → 관악구
        }

        order.setDoName(doName);
        order.setSiName(siName);
        order.setGuName(guName);
    }

}
