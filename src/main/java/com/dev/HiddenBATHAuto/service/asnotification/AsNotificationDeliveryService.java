package com.dev.HiddenBATHAuto.service.asnotification;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.dev.HiddenBATHAuto.config.notification.AsNotificationProperties;
import com.dev.HiddenBATHAuto.dto.asnotification.AsNotificationItemDto;
import com.dev.HiddenBATHAuto.dto.notification.NotificationSendResult;
import com.dev.HiddenBATHAuto.enums.notification.OrderNotificationKakaoStatus;
import com.dev.HiddenBATHAuto.messaging.notification.OrderNotificationWebSocketHandler;
import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.notification.AsNotification;
import com.dev.HiddenBATHAuto.model.task.as.audit.AsChangeEvent;
import com.dev.HiddenBATHAuto.repository.notification.AsNotificationRepository;
import com.dev.HiddenBATHAuto.service.notification.NotificationUseCaseService;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AsNotificationDeliveryService {

    private final AsNotificationRepository repository;
    private final AsNotificationService notificationService;
    private final OrderNotificationWebSocketHandler webSocketHandler;
    private final NotificationUseCaseService notificationUseCaseService;
    private final AsNotificationProperties properties;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deliver(Long notificationId) {
        AsNotification notification = repository.findDeliveryTargetById(notificationId).orElse(null);
        if (notification == null) return;
        if (notification.isWebEnabled() || notification.isImportantEnabled()) sendWebSocket(notification);
        sendKakao(notification);
    }

    private void sendWebSocket(AsNotification notification) {
        try {
            AsNotificationItemDto dto = notificationService.toDto(notification);
            String payload = objectMapper.writeValueAsString(new SocketEnvelope("AS_NOTIFICATION_CREATED", dto));
            webSocketHandler.sendToUsername(notification.getRecipient().getUsername(), payload);
        } catch (Exception e) {
            log.warn("AS 웹 알림 전송 실패 - 알림ID={}, 수신자={}, 사유={}",
                    notification.getId(), recipientLabel(notification), shortReason(e));
        }
    }

    private void sendKakao(AsNotification notification) {
        if (!notification.isKakaoEnabled()) {
            notification.markKakaoSkipped("AS 로깅알림 관리에서 카카오톡이 비활성화되어 있습니다.");
            return;
        }
        if (!properties.getKakao().isEnabled()) {
            notification.markKakaoSkipped("AS 카카오톡 전체 발송 설정이 비활성화되어 있습니다.");
            return;
        }
        if (notification.getKakaoStatus() != null
                && notification.getKakaoStatus() != OrderNotificationKakaoStatus.NOT_REQUESTED) return;

        Member recipient = notification.getRecipient();
        if (recipient == null || recipient.getPhone() == null || recipient.getPhone().isBlank()) {
            notification.markKakaoSkipped("수신자 연락처가 없습니다.");
            return;
        }

        notification.markKakaoRequested();
        try {
            AsChangeEvent event = notification.getEvent();
            String actor = event != null && event.getActorDisplayName() != null ? event.getActorDisplayName()
                    : event != null && event.getActorUsername() != null ? event.getActorUsername() : "시스템";
            NotificationSendResult result = notificationUseCaseService.sendAsChanged(
                    properties.getKakao().getTemplateCode(),
                    notification.resolveAsTaskId(),
                    recipient.getPhone(),
                    "AS #" + notification.resolveAsTaskId(),
                    notification.getMessage(),
                    actor,
                    event != null ? event.getActorMemberId() : null,
                    event != null ? event.getActorUsername() : null
            );
            if (result != null && result.isSuccess()) {
                notification.markKakaoAccepted(result.getLogId());
            } else {
                String reason = result != null ? result.getFailureReason() : "SOLAPI 응답이 없습니다.";
                notification.markKakaoFailed(result != null ? result.getLogId() : null, reason);
                log.warn("AS 카카오톡 전송 실패 - 알림ID={}, AS ID={}, 수신자={}, 사유={}",
                        notification.getId(), notification.resolveAsTaskId(), recipientLabel(notification), shortReason(reason));
            }
        } catch (Exception e) {
            notification.markKakaoFailed(null, shortReason(e));
            log.warn("AS 카카오톡 전송 실패 - 알림ID={}, AS ID={}, 수신자={}, 사유={}",
                    notification.getId(), notification.resolveAsTaskId(), recipientLabel(notification), shortReason(e));
        }
    }

    private String recipientLabel(AsNotification n) {
        Member m = n != null ? n.getRecipient() : null;
        if (m == null) return "알 수 없음";
        return (m.getName() != null && !m.getName().isBlank() ? m.getName().trim() : m.getUsername()) + "(id=" + m.getId() + ")";
    }

    private String shortReason(Throwable e) {
        return shortReason(e == null ? null : (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
    }

    private String shortReason(String value) {
        if (value == null || value.isBlank()) return "알 수 없는 오류";
        String v = value.trim().replaceAll("[\\r\\n\\t]+", " ");
        return v.length() <= 300 ? v : v.substring(0, 300);
    }

    private record SocketEnvelope(String type, AsNotificationItemDto notification) {}
}
