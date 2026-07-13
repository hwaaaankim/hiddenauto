package com.dev.HiddenBATHAuto.service.order;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.task.OrderStatus;
import com.dev.HiddenBATHAuto.service.team.delivery.DeliveryRouteService;

import lombok.RequiredArgsConstructor;

/**
 * 배송상태, 증빙 이미지, DeliveryOrderIndex 재분류를 하나의 트랜잭션 경계로 묶습니다.
 */
@Service
@RequiredArgsConstructor
public class DeliveryCompletionService {

    private final OrderService orderService;
    private final DeliveryOrderIndexService deliveryOrderIndexService;
    private final DeliveryRouteService deliveryRouteService;

    @Transactional(rollbackFor = Exception.class)
    public void completeSingle(Long orderId, List<MultipartFile> files) throws IOException {
        orderService.updateDeliveryStatusAndImages(
                orderId,
                OrderStatus.DELIVERY_DONE.name(),
                files
        );

        deliveryOrderIndexService.reclassifyIndex(orderId);
    }

    @Transactional(rollbackFor = Exception.class)
    public List<Long> completeSameAddress(
            List<Long> orderIds,
            List<MultipartFile> files
    ) throws IOException {
        List<Long> distinctOrderIds = normalizeOrderIds(orderIds, "동일주소 배송완료 처리할 주문이 없습니다.");

        completeValidatedOrderIds(distinctOrderIds, files);
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

        completeValidatedOrderIds(validatedOrderIds, files);
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
