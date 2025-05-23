package com.dev.HiddenBATHAuto.service.order;

import java.time.LocalDate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dev.HiddenBATHAuto.dto.DeliveryOrderIndexUpdateRequest;
import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.task.DeliveryOrderIndex;
import com.dev.HiddenBATHAuto.model.task.Order;
import com.dev.HiddenBATHAuto.repository.auth.MemberRepository;
import com.dev.HiddenBATHAuto.repository.order.DeliveryOrderIndexRepository;

import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class DeliveryOrderIndexService {

    private final DeliveryOrderIndexRepository deliveryOrderIndexRepository;
    private final MemberRepository memberRepository;
    
    public void ensureIndex(Order order) {
        if (order.getAssignedDeliveryHandler() == null || order.getPreferredDeliveryDate() == null) return;

        // 기존 인덱스 존재 시, 갱신 처리 (배송기사/날짜 바뀐 경우)
        DeliveryOrderIndex existingIndex = deliveryOrderIndexRepository.findByOrder(order).orElse(null);

        if (existingIndex != null) {
            boolean needsUpdate = !existingIndex.getDeliveryDate().equals(order.getPreferredDeliveryDate().toLocalDate())
                               || !existingIndex.getDeliveryHandler().getId().equals(order.getAssignedDeliveryHandler().getId());
            if (needsUpdate) {
                Integer maxIndex = deliveryOrderIndexRepository.findMaxIndexByHandlerAndDate(
                    order.getAssignedDeliveryHandler().getId(), order.getPreferredDeliveryDate().toLocalDate());
                existingIndex.setDeliveryDate(order.getPreferredDeliveryDate().toLocalDate());
                existingIndex.setDeliveryHandler(order.getAssignedDeliveryHandler());
                existingIndex.setOrderIndex(maxIndex == null ? 1 : maxIndex + 1);
                deliveryOrderIndexRepository.save(existingIndex);
            }
        } else {
            // 인덱스가 없다면 새로 생성
            Integer maxIndex = deliveryOrderIndexRepository.findMaxIndexByHandlerAndDate(
                    order.getAssignedDeliveryHandler().getId(), order.getPreferredDeliveryDate().toLocalDate());
            DeliveryOrderIndex newIndex = new DeliveryOrderIndex();
            newIndex.setOrder(order);
            newIndex.setDeliveryDate(order.getPreferredDeliveryDate().toLocalDate());
            newIndex.setDeliveryHandler(order.getAssignedDeliveryHandler());
            newIndex.setOrderIndex(maxIndex == null ? 1 : maxIndex + 1);
            deliveryOrderIndexRepository.save(newIndex);
        }
    }
    
    public void updateIndexes(DeliveryOrderIndexUpdateRequest request) {
        Member handler = memberRepository.findById(request.getDeliveryHandlerId())
                .orElseThrow(() -> new IllegalArgumentException("배송 담당자 없음"));

        LocalDate date = LocalDate.parse(request.getDeliveryDate());

        for (DeliveryOrderIndexUpdateRequest.OrderIndexDto dto : request.getOrderList()) {
            DeliveryOrderIndex index = deliveryOrderIndexRepository
                    .findByDeliveryHandlerIdAndDeliveryDateAndOrderId(
                            handler.getId(), date, dto.getOrderId())
                    .orElseThrow(() -> new IllegalStateException("해당 주문 인덱스 없음"));

            index.setOrderIndex(dto.getOrderIndex());
        }
    }
}
