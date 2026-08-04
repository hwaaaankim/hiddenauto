package com.dev.HiddenBATHAuto.service.ordernotification;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.dev.HiddenBATHAuto.config.notification.OrderNotificationProperties;
import com.dev.HiddenBATHAuto.dto.notification.NotificationSendResult;
import com.dev.HiddenBATHAuto.dto.ordernotification.OrderNotificationItemDto;
import com.dev.HiddenBATHAuto.enums.notification.OrderNotificationCategory;
import com.dev.HiddenBATHAuto.messaging.notification.OrderNotificationWebSocketHandler;
import com.dev.HiddenBATHAuto.model.notification.OrderNotification;
import com.dev.HiddenBATHAuto.model.task.audit.OrderChangeEvent;
import com.dev.HiddenBATHAuto.repository.notification.OrderNotificationRepository;
import com.dev.HiddenBATHAuto.service.notification.NotificationUseCaseService;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderNotificationDeliveryService {

    private final OrderNotificationRepository notificationRepository;
    private final OrderNotificationService notificationService;
    private final OrderNotificationWebSocketHandler webSocketHandler;
    private final NotificationUseCaseService notificationUseCaseService;
    private final OrderNotificationProperties properties;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deliver(Long notificationId) {
        OrderNotification notification = notificationRepository.findDeliveryTargetById(notificationId)
                .orElse(null);
        if (notification == null) return;

        sendWebSocket(notification);
        sendKakaoIfRequired(notification);
    }

    private void sendWebSocket(OrderNotification notification) {
        try {
            OrderNotificationItemDto dto = notificationService.toDto(notification);
            String payload = objectMapper.writeValueAsString(new SocketEnvelope("ORDER_NOTIFICATION_CREATED", dto));
            webSocketHandler.sendToUsername(notification.getRecipient().getUsername(), payload);
        } catch (Exception e) {
            // 실시간 전송 실패가 DB 변경/웹 알림 저장을 되돌리면 안 됩니다.
            log.warn("오더 실시간 알림 전송 실패: notificationId={}", notification.getId(), e);
        }
    }

    private void sendKakaoIfRequired(OrderNotification notification) {
        if (!properties.getKakao().isEnabled()) return;

        boolean emergency = notification.getCategory() == OrderNotificationCategory.EMERGENCY;
        boolean allowed = emergency
                ? properties.getKakao().isEmergencyEnabled()
                : properties.getKakao().isNormalEnabled();

        if (!allowed) {
            notification.markKakaoSkipped(emergency
                    ? "긴급 카카오 발송 설정이 비활성화되어 있습니다."
                    : "일반 카카오 발송 설정이 비활성화되어 있습니다.");
            return;
        }

        String phone = notification.getRecipient().getPhone();
        if (phone == null || phone.isBlank()) {
            notification.markKakaoSkipped("수신자의 휴대전화 번호가 없습니다.");
            return;
        }

        notification.markKakaoRequested();

        try {
            OrderChangeEvent event = notification.getEvent();
            Long taskId = notification.getTask() != null ? notification.getTask().getId() : null;
            Long orderId = notification.getOrder() != null ? notification.getOrder().getId() : null;
            String actorName = event != null && event.getActorDisplayName() != null
                    ? event.getActorDisplayName()
                    : event != null ? event.getActorUsername() : "시스템";

            NotificationSendResult result = notificationUseCaseService.sendTaskChanged(
                    taskId,
                    phone,
                    "발주 #" + orderId,
                    notification.getMessage(),
                    actorName,
                    event != null ? event.getActorMemberId() : null,
                    event != null ? event.getActorUsername() : null
            );

            if (result != null && result.isSuccess()) {
                notification.markKakaoAccepted(result.getLogId());
            } else {
                notification.markKakaoFailed(
                        result != null ? result.getLogId() : null,
                        result != null ? result.getFailureReason() : "SOLAPI 응답이 없습니다."
                );
            }
        } catch (Exception e) {
            notification.markKakaoFailed(null, e.getMessage());
            log.warn("오더 카카오 알림 발송 실패: notificationId={}", notification.getId(), e);
        }
    }

    private record SocketEnvelope(String type, OrderNotificationItemDto notification) {
    }
}
