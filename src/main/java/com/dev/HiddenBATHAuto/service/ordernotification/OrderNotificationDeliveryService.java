package com.dev.HiddenBATHAuto.service.ordernotification;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.dev.HiddenBATHAuto.config.notification.OrderNotificationProperties;
import com.dev.HiddenBATHAuto.dto.notification.NotificationSendResult;
import com.dev.HiddenBATHAuto.dto.ordernotification.OrderNotificationItemDto;
import com.dev.HiddenBATHAuto.enums.notification.OrderNotificationCategory;
import com.dev.HiddenBATHAuto.enums.notification.OrderNotificationKakaoStatus;
import com.dev.HiddenBATHAuto.messaging.notification.OrderNotificationWebSocketHandler;
import com.dev.HiddenBATHAuto.model.auth.Member;
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

        if (notification.isWebEnabled() || notification.isImportantEnabled()) {
            sendWebSocket(notification);
        }
        sendKakaoIfRequired(notification);
    }

    private void sendWebSocket(OrderNotification notification) {
        try {
            OrderNotificationItemDto dto = notificationService.toDto(notification);
            String payload = objectMapper.writeValueAsString(new SocketEnvelope("ORDER_NOTIFICATION_CREATED", dto));
            webSocketHandler.sendToUsername(notification.getRecipient().getUsername(), payload);
        } catch (Exception e) {
            // 실시간 전송 실패가 DB 변경/웹 알림 저장을 되돌리면 안 되며 콘솔에는 짧은 원인만 남깁니다.
            log.warn("웹 알림 전송 실패 - 알림ID={}, 수신자={}, 사유={}",
                    notification.getId(), recipientLabel(notification), shortReason(e));
        }
    }

    private void sendKakaoIfRequired(OrderNotification notification) {
        if (!notification.isKakaoEnabled()) {
            notification.markKakaoSkipped("로깅알림 관리에서 카카오톡 발송이 비활성화되어 있습니다.");
            return;
        }
        if (!properties.getKakao().isEnabled()) {
            notification.markKakaoSkipped("카카오톡 전체 발송 설정이 비활성화되어 있습니다.");
            return;
        }
        if (notification.getKakaoStatus() != null
                && notification.getKakaoStatus() != OrderNotificationKakaoStatus.NOT_REQUESTED) {
            return;
        }

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

        String batchKey = notification.getKakaoBatchKey();
        Long recipientId = notification.getRecipient().getId();
        Long leaderId = notificationRepository.findKakaoBatchLeaderId(batchKey, recipientId);
        if (leaderId != null && !Objects.equals(leaderId, notification.getId())) {
            notification.markKakaoSkipped("동시작업 카카오 알림은 수신자별 대표 알림 한 건으로 통합되었습니다.");
            return;
        }

        List<OrderNotification> batch = notificationRepository.findKakaoBatch(batchKey, recipientId);
        if (batch.isEmpty()) batch = List.of(notification);
        for (OrderNotification row : batch) {
            if (!Objects.equals(row.getId(), notification.getId())) {
                row.markKakaoSkipped("동시작업 카카오 알림은 대표 알림에 포함되었습니다.");
            }
        }

        notification.markKakaoRequested();
        try {
            KakaoMessage message = buildKakaoMessage(batch);
            OrderChangeEvent event = notification.getEvent();

            NotificationSendResult result = notificationUseCaseService.sendTaskChanged(
                    properties.getKakao().getTemplateCode(),
                    notification.resolveTaskId(),
                    notification.getRecipient().getPhone(),
                    message.taskName(),
                    message.changedContent(),
                    message.actorName(),
                    event != null ? event.getActorMemberId() : null,
                    event != null ? event.getActorUsername() : null
            );

            if (result != null && result.isSuccess()) {
                notification.markKakaoAccepted(result.getLogId());
                return;
            }

            String reason = result != null ? result.getFailureReason() : "SOLAPI 응답이 없습니다.";
            notification.markKakaoFailed(result != null ? result.getLogId() : null, reason);
            log.warn("카카오톡 전송 실패 - 알림ID={}, 발주ID={}, 수신자={}, 사유={}",
                    notification.getId(), notification.resolveOrderId(), recipientLabel(notification), shortReason(reason));
        } catch (Exception e) {
            String reason = shortReason(e);
            notification.markKakaoFailed(null, reason);
            log.warn("카카오톡 전송 실패 - 알림ID={}, 발주ID={}, 수신자={}, 사유={}",
                    notification.getId(), notification.resolveOrderId(), recipientLabel(notification), reason);
        }
    }

    private KakaoMessage buildKakaoMessage(List<OrderNotification> batch) {
        OrderNotification first = batch.get(0);
        OrderChangeEvent event = first.getEvent();
        String actorName = event != null && event.getActorDisplayName() != null
                ? event.getActorDisplayName()
                : event != null && event.getActorUsername() != null ? event.getActorUsername() : "시스템";

        if (batch.size() == 1) {
            Long orderId = first.resolveOrderId();
            return new KakaoMessage("발주 #" + orderId, first.getMessage(), actorName);
        }

        List<Long> orderIds = batch.stream()
                .map(OrderNotification::resolveOrderId)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
        String operationLabels = batch.stream()
                .map(OrderNotification::getEvent)
                .filter(Objects::nonNull)
                .map(OrderChangeEvent::getOperationLabel)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .distinct()
                .limit(3)
                .collect(Collectors.joining(", "));
        String range = orderIds.isEmpty()
                ? ""
                : orderIds.size() == 1
                        ? "발주 #" + orderIds.get(0)
                        : "발주 #" + orderIds.get(0) + " ~ #" + orderIds.get(orderIds.size() - 1);
        String content = actorName + "님이 동시작업으로 발주 " + orderIds.size() + "건을 처리했습니다."
                + (operationLabels.isBlank() ? "" : " 작업: " + operationLabels + ".")
                + (range.isBlank() ? "" : " 범위: " + range + ".")
                + " 웹 알림에서 각 발주별 상세 변경사항을 확인해 주세요.";
        return new KakaoMessage("발주 동시작업 " + orderIds.size() + "건", content, actorName);
    }

    private String recipientLabel(OrderNotification notification) {
        Member member = notification != null ? notification.getRecipient() : null;
        if (member == null) return "알 수 없음";
        String name = normalize(member.getName());
        String username = normalize(member.getUsername());
        String display = name != null ? name : username != null ? username : "이름 없음";
        return display + "(id=" + member.getId() + (username != null ? ", username=" + username : "") + ")";
    }

    private String shortReason(Throwable throwable) {
        if (throwable == null) return "알 수 없는 오류";
        return shortReason(throwable.getMessage() != null
                ? throwable.getMessage()
                : throwable.getClass().getSimpleName());
    }

    private String shortReason(String reason) {
        if (reason == null || reason.isBlank()) return "알 수 없는 오류";
        String normalized = reason.trim().replaceAll("[\\r\\n\\t]+", " ");
        return normalized.length() > 300 ? normalized.substring(0, 300) : normalized;
    }

    private String normalize(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private record KakaoMessage(String taskName, String changedContent, String actorName) {
    }

    private record SocketEnvelope(String type, OrderNotificationItemDto notification) {
    }
}
