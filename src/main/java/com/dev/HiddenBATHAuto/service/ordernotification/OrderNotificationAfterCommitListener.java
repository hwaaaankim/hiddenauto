package com.dev.HiddenBATHAuto.service.ordernotification;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderNotificationAfterCommitListener {

    private final OrderNotificationDeliveryService deliveryService;

    @Async("orderNotificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void afterCommit(OrderNotificationsCreatedEvent event) {
        for (Long notificationId : event.notificationIds()) {
            try {
                deliveryService.deliver(notificationId);
            } catch (Exception e) {
                String reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                reason = reason.replaceAll("[\\r\\n\\t]+", " ");
                if (reason.length() > 300) reason = reason.substring(0, 300);
                log.warn("커밋 후 오더 알림 전달 실패 - 알림ID={}, 사유={}", notificationId, reason);
            }
        }
    }
}
