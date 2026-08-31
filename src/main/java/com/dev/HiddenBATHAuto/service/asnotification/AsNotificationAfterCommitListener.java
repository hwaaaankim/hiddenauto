package com.dev.HiddenBATHAuto.service.asnotification;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class AsNotificationAfterCommitListener {

    private final AsNotificationDeliveryService deliveryService;

    @Async("orderNotificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void afterCommit(AsNotificationsCreatedEvent event) {
        for (Long id : event.notificationIds()) {
            try {
                deliveryService.deliver(id);
            } catch (Exception e) {
                String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                reason = reason.replaceAll("[\\r\\n\\t]+", " ");
                if (reason.length() > 300) reason = reason.substring(0, 300);
                log.warn("커밋 후 AS 알림 전달 실패 - 알림ID={}, 사유={}", id, reason);
            }
        }
    }
}
