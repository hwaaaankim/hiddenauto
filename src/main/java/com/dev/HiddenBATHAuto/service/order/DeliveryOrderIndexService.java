package com.dev.HiddenBATHAuto.service.order;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dev.HiddenBATHAuto.model.task.DeliveryOrderIndex;
import com.dev.HiddenBATHAuto.model.task.Order;
import com.dev.HiddenBATHAuto.repository.order.DeliveryOrderIndexRepository;

@Service
@Transactional
public class DeliveryOrderIndexService {

    @Autowired
    private DeliveryOrderIndexRepository deliveryOrderIndexRepository;

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
}
