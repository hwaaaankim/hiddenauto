package com.dev.HiddenBATHAuto.service.ordernotification;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 같은 DB 트랜잭션에서 생성된 발주 알림을 하나의 동시작업으로 묶습니다.
 * 웹 알림은 오더별로 모두 저장하되, 카카오 알림은 이 키와 수신자를 기준으로 1회만 발송합니다.
 */
@Component
public class OrderNotificationBatchContext {

    private static final Object RESOURCE_KEY = OrderNotificationBatchContext.class.getName() + ".BATCH_KEY";

    public String currentBatchKey() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            return newBatchKey();
        }

        Object existing = TransactionSynchronizationManager.getResource(RESOURCE_KEY);
        if (existing instanceof String batchKey && !batchKey.isBlank()) {
            return batchKey;
        }

        String batchKey = newBatchKey();
        TransactionSynchronizationManager.bindResource(RESOURCE_KEY, batchKey);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (TransactionSynchronizationManager.hasResource(RESOURCE_KEY)) {
                    TransactionSynchronizationManager.unbindResource(RESOURCE_KEY);
                }
            }
        });
        return batchKey;
    }

    private String newBatchKey() {
        return UUID.randomUUID().toString();
    }
}
