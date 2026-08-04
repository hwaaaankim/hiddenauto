package com.dev.HiddenBATHAuto.service.order;

import java.io.IOException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.dev.HiddenBATHAuto.enums.order.OrderChangeSourceArea;
import com.dev.HiddenBATHAuto.enums.order.OrderWorkArea;
import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.task.Order;
import com.dev.HiddenBATHAuto.model.task.OrderStatus;
import com.dev.HiddenBATHAuto.repository.order.OrderRepository;
import com.dev.HiddenBATHAuto.service.team.delivery.DeliveryRouteService;

import lombok.RequiredArgsConstructor;

/**
 * 배송상태, 증빙 이미지, DeliveryOrderIndex 재분류, 변경이력을 하나의 트랜잭션 경계로 묶습니다.
 */
@Service
@RequiredArgsConstructor
public class DeliveryCompletionService {

    private final OrderService orderService;
    private final DeliveryOrderIndexService deliveryOrderIndexService;
    private final DeliveryRouteService deliveryRouteService;
    private final OrderRepository orderRepository;
    private final OrderOperationalChangeRecorder changeRecorder;

    @Transactional(rollbackFor = Exception.class)
    public void completeSingle(
            Member actor,
            Long orderId,
            List<MultipartFile> files
    ) throws IOException {
        List<Long> orderIds = List.of(orderId);
        Map<Long, OrderStatus> beforeStatuses = loadBeforeStatuses(orderIds);

        orderService.updateDeliveryStatusAndImages(
                orderId,
                OrderStatus.DELIVERY_DONE.name(),
                files
        );

        deliveryOrderIndexService.reclassifyIndex(orderId);
        recordDeliveryCompleted(actor, orderIds, beforeStatuses, "DELIVERY_COMPLETE", "배송완료 처리",
                "/team/deliveryStatus/" + orderId);
    }

    @Transactional(rollbackFor = Exception.class)
    public List<Long> completeSameAddress(
            Member actor,
            List<Long> orderIds,
            List<MultipartFile> files
    ) throws IOException {
        List<Long> distinctOrderIds = normalizeOrderIds(orderIds, "동일주소 배송완료 처리할 주문이 없습니다.");
        Map<Long, OrderStatus> beforeStatuses = loadBeforeStatuses(distinctOrderIds);

        completeValidatedOrderIds(distinctOrderIds, files);
        recordDeliveryCompleted(actor, distinctOrderIds, beforeStatuses,
                "DELIVERY_SAME_ADDRESS_COMPLETE", "동일주소 배송완료 처리",
                "/team/deliveryStatus/same-address");
        return distinctOrderIds;
    }

    /**
     * 업체별 오늘 배송 화면의 체크박스 선택 완료 처리입니다.
     * 검증과 상태변경을 같은 트랜잭션 안에서 수행합니다.
     */
    @Transactional(rollbackFor = Exception.class)
    public List<Long> completeRouteSelection(
            Member loginMember,
            LocalDate deliveryDate,
            List<Long> orderIds,
            List<MultipartFile> files
    ) throws IOException {
        List<Long> validatedOrderIds = deliveryRouteService.validateCompletionSelection(
                loginMember,
                deliveryDate,
                orderIds
        );
        Map<Long, OrderStatus> beforeStatuses = loadBeforeStatuses(validatedOrderIds);

        completeValidatedOrderIds(validatedOrderIds, files);
        recordDeliveryCompleted(loginMember, validatedOrderIds, beforeStatuses,
                "DELIVERY_ROUTE_COMPLETE", "배송경로 선택 배송완료 처리",
                "/team/deliveryRoute/complete");
        return validatedOrderIds;
    }

    private void completeValidatedOrderIds(
            List<Long> orderIds,
            List<MultipartFile> files
    ) throws IOException {
        orderService.updateDeliveryStatusesAndSharedImages(
                orderIds,
                OrderStatus.DELIVERY_DONE.name(),
                files
        );

        deliveryOrderIndexService.reclassifyIndexes(orderIds);
    }

    private Map<Long, OrderStatus> loadBeforeStatuses(List<Long> orderIds) {
        Map<Long, OrderStatus> result = orderRepository.findAllById(orderIds).stream()
                .filter(order -> order != null && order.getId() != null)
                .collect(Collectors.toMap(
                        Order::getId,
                        Order::getStatus,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        for (Long orderId : orderIds) {
            if (!result.containsKey(orderId)) {
                throw new IllegalArgumentException("해당 주문이 존재하지 않습니다. orderId=" + orderId);
            }
        }
        return result;
    }

    private void recordDeliveryCompleted(
            Member actor,
            List<Long> orderIds,
            Map<Long, OrderStatus> beforeStatuses,
            String operationCode,
            String operationLabel,
            String requestPath
    ) {
        Map<Long, Order> orderMap = orderRepository.findAllById(orderIds).stream()
                .filter(order -> order != null && order.getId() != null)
                .collect(Collectors.toMap(Order::getId, Function.identity()));

        for (Long orderId : orderIds) {
            Order order = orderMap.get(orderId);
            if (order == null) {
                throw new IllegalStateException("배송완료 이력 대상 오더를 찾을 수 없습니다. orderId=" + orderId);
            }
            changeRecorder.recordStatusChange(
                    order,
                    OrderChangeSourceArea.DELIVERY,
                    actor,
                    beforeStatuses.get(orderId),
                    OrderStatus.DELIVERY_DONE,
                    operationCode,
                    operationLabel,
                    requestPath,
                    OrderWorkArea.DISPATCH,
                    OrderWorkArea.DELIVERY
            );
        }
    }

    private List<Long> normalizeOrderIds(List<Long> orderIds, String emptyMessage) {
        List<Long> distinctOrderIds = orderIds == null
                ? List.of()
                : orderIds.stream().filter(Objects::nonNull).distinct().toList();

        if (distinctOrderIds.isEmpty()) {
            throw new IllegalArgumentException(emptyMessage);
        }

        return distinctOrderIds;
    }
}
