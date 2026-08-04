package com.dev.HiddenBATHAuto.service.notification;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.dev.HiddenBATHAuto.dto.notification.NotificationSendCommand;
import com.dev.HiddenBATHAuto.dto.notification.NotificationSendResult;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class NotificationUseCaseService {

    private final NotificationMessageService notificationMessageService;

    public NotificationSendResult sendOrderAccepted(
            Long orderId,
            String customerName,
            String customerPhone,
            String orderNumber,
            String orderSummary,
            Long requestedByMemberId,
            String requestedByUsername
    ) {
        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("#{고객명}", customerName);
        variables.put("#{주문번호}", orderNumber);
        variables.put("#{접수내용}", orderSummary);

        String textForLog = """
                안녕하세요 %s님.
                히든바스 주문이 접수되었습니다.

                주문번호: %s
                접수내용: %s

                변경사항이 발생하면 다시 안내드리겠습니다.
                """.formatted(
                blankToDash(customerName),
                blankToDash(orderNumber),
                blankToDash(orderSummary)
        );

        return notificationMessageService.sendAlimtalk(NotificationSendCommand.builder()
                .templateCode("ORDER_ACCEPTED")
                .to(customerPhone)
                .messageTextForLog(textForLog)
                .variables(variables)
                .businessType("ORDER")
                .businessId(orderId)
                .requestedByMemberId(requestedByMemberId)
                .requestedByUsername(requestedByUsername)
                .build());
    }

    public NotificationSendResult sendTaskChanged(
            Long taskId,
            String targetPhone,
            String taskName,
            String changedContent,
            String actorName,
            Long requestedByMemberId,
            String requestedByUsername
    ) {
        return sendTaskChanged(
                "TASK_CHANGED",
                taskId,
                targetPhone,
                taskName,
                changedContent,
                actorName,
                requestedByMemberId,
                requestedByUsername
        );
    }

    /**
     * 발주 알림처럼 운영 중인 테스트 템플릿을 재사용하는 경우 템플릿 코드만 설정에서 교체할 수 있는 오버로드입니다.
     */
    public NotificationSendResult sendTaskChanged(
            String templateCode,
            Long taskId,
            String targetPhone,
            String taskName,
            String changedContent,
            String actorName,
            Long requestedByMemberId,
            String requestedByUsername
    ) {
        String occurredAt = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));

        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("#{업무명}", taskName);
        variables.put("#{변경내용}", changedContent);
        variables.put("#{처리자}", actorName);
        variables.put("#{발생시각}", occurredAt);

        String textForLog = """
                [업무 변경 알림]
                업무: %s
                변경내용: %s
                처리자: %s
                발생시각: %s
                """.formatted(
                blankToDash(taskName),
                blankToDash(changedContent),
                blankToDash(actorName),
                occurredAt
        );

        return notificationMessageService.sendAlimtalk(NotificationSendCommand.builder()
                .templateCode(templateCode == null || templateCode.isBlank() ? "TASK_CHANGED" : templateCode.trim())
                .to(targetPhone)
                .messageTextForLog(textForLog)
                .variables(variables)
                .businessType("TASK")
                .businessId(taskId)
                .requestedByMemberId(requestedByMemberId)
                .requestedByUsername(requestedByUsername)
                .build());
    }

    private String blankToDash(String value) {
        return value == null || value.isBlank() ? "-" : value.trim();
    }
}
