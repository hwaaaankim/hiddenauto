package com.dev.HiddenBATHAuto.service.ordernotification;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dev.HiddenBATHAuto.config.notification.OrderNotificationProperties;
import com.dev.HiddenBATHAuto.dto.ordernotification.OrderNotificationFieldDto;
import com.dev.HiddenBATHAuto.dto.ordernotification.OrderNotificationItemDto;
import com.dev.HiddenBATHAuto.dto.ordernotification.OrderNotificationPageDto;
import com.dev.HiddenBATHAuto.dto.ordernotification.OrderNotificationSummaryDto;
import com.dev.HiddenBATHAuto.enums.notification.OrderNotificationAudience;
import com.dev.HiddenBATHAuto.enums.notification.OrderNotificationCategory;
import com.dev.HiddenBATHAuto.enums.order.OrderWorkArea;
import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.notification.OrderNotification;
import com.dev.HiddenBATHAuto.model.task.Order;
import com.dev.HiddenBATHAuto.model.task.audit.OrderChangeEvent;
import com.dev.HiddenBATHAuto.model.task.audit.OrderChangeField;
import com.dev.HiddenBATHAuto.repository.notification.OrderNotificationRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrderNotificationService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final OrderNotificationRepository notificationRepository;
    private final OrderNotificationRecipientResolver recipientResolver;
    private final OrderNotificationProperties properties;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public List<OrderNotification> createForChangeEvent(
            OrderChangeEvent event,
            OrderNotificationAudience audience,
            Set<OrderWorkArea> affectedAreas,
            String explicitTitle,
            String explicitMessage,
            Collection<Long> additionalRecipientMemberIds
    ) {
        if (!properties.isEnabled() || event == null || event.getOrder() == null) {
            return List.of();
        }

        Order order = event.getOrder();
        List<OrderNotificationRecipientResolver.RecipientTarget> targets = recipientResolver.resolve(
                order,
                event.getSourceArea(),
                affectedAreas,
                audience,
                event.getActorMemberId(),
                additionalRecipientMemberIds
        );

        if (targets.isEmpty()) {
            return List.of();
        }

        String title = normalizeText(explicitTitle) != null
                ? explicitTitle.trim()
                : defaultTitle(event, audience);
        String message = normalizeText(explicitMessage) != null
                ? explicitMessage.trim()
                : defaultMessage(event);

        List<OrderNotification> notifications = new ArrayList<>(targets.size());
        for (OrderNotificationRecipientResolver.RecipientTarget target : targets) {
            notifications.add(OrderNotification.create(
                    event,
                    target.member(),
                    target.category(),
                    title,
                    message
            ));
        }

        List<OrderNotification> saved = notificationRepository.saveAll(notifications);
        notificationRepository.flush();

        List<Long> ids = saved.stream()
                .map(OrderNotification::getId)
                .filter(java.util.Objects::nonNull)
                .toList();

        if (!ids.isEmpty()) {
            eventPublisher.publishEvent(new OrderNotificationsCreatedEvent(ids));
        }

        return saved;
    }

    @Transactional(readOnly = true)
    public OrderNotificationSummaryDto getSummary(Member member) {
        Long memberId = requireMemberId(member);
        Map<String, Long> counts = new LinkedHashMap<>();
        for (OrderNotificationCategory category : OrderNotificationCategory.values()) {
            counts.put(category.name(), 0L);
        }

        for (Object[] row : notificationRepository.countUnreadByCategory(memberId)) {
            if (row == null || row.length < 2 || !(row[0] instanceof OrderNotificationCategory category)) {
                continue;
            }
            long count = row[1] instanceof Number number ? number.longValue() : 0L;
            counts.put(category.name(), count);
        }

        return OrderNotificationSummaryDto.builder()
                .totalUnreadCount(notificationRepository.countByRecipient_IdAndReadAtIsNull(memberId))
                .unreadCountByCategory(counts)
                .build();
    }

    @Transactional(readOnly = true)
    public OrderNotificationPageDto getNotifications(
            Member member,
            OrderNotificationCategory category,
            int page,
            int size
    ) {
        Long memberId = requireMemberId(member);
        int safePage = Math.max(0, page);
        int safeSize = Math.min(100, Math.max(1, size));
        PageRequest pageable = PageRequest.of(safePage, safeSize);

        Page<OrderNotification> result = category == null
                ? notificationRepository.findByRecipient_IdOrderByCreatedAtDescIdDesc(memberId, pageable)
                : notificationRepository.findByRecipient_IdAndCategoryOrderByCreatedAtDescIdDesc(
                        memberId,
                        category,
                        pageable
                );

        return OrderNotificationPageDto.builder()
                .content(result.getContent().stream().map(this::toDto).toList())
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .first(result.isFirst())
                .last(result.isLast())
                .build();
    }

    @Transactional
    public OrderNotificationItemDto markRead(Member member, Long notificationId) {
        Long memberId = requireMemberId(member);
        if (notificationId == null) {
            throw new IllegalArgumentException("알림 ID가 없습니다.");
        }

        OrderNotification notification = notificationRepository.findByIdAndRecipient_Id(notificationId, memberId)
                .orElseThrow(() -> new IllegalArgumentException("해당 알림을 찾을 수 없습니다."));
        notification.markRead(LocalDateTime.now());
        return toDto(notification);
    }

    @Transactional
    public int markAllRead(Member member, OrderNotificationCategory category) {
        return notificationRepository.markAllRead(requireMemberId(member), category);
    }

    public OrderNotificationItemDto toDto(OrderNotification notification) {
        OrderChangeEvent event = notification.getEvent();
        Order order = notification.getOrder();
        List<OrderNotificationFieldDto> changes = event != null && event.getFields() != null
                ? event.getFields().stream()
                        .sorted(java.util.Comparator.comparingInt(OrderChangeField::getSortOrder))
                        .map(field -> OrderNotificationFieldDto.builder()
                                .fieldKey(field.getFieldKey())
                                .fieldLabel(field.getFieldLabel())
                                .beforeValue(field.getBeforeValue())
                                .afterValue(field.getAfterValue())
                                .build())
                        .toList()
                : List.of();

        return OrderNotificationItemDto.builder()
                .id(notification.getId())
                .eventId(event != null ? event.getId() : null)
                .orderId(order != null ? order.getId() : null)
                .orderStatus(order != null && order.getStatus() != null ? order.getStatus().name() : null)
                .orderStatusLabel(order != null && order.getStatus() != null ? order.getStatus().getLabel() : null)
                .taskId(notification.getTask() != null ? notification.getTask().getId() : null)
                .category(notification.getCategory().name())
                .categoryLabel(notification.getCategory().getLabel())
                .title(notification.getTitle())
                .message(notification.getMessage())
                .sourceArea(event != null && event.getSourceArea() != null ? event.getSourceArea().name() : null)
                .sourceAreaLabel(event != null && event.getSourceArea() != null ? event.getSourceArea().getLabel() : null)
                .actorMemberId(event != null ? event.getActorMemberId() : null)
                .actorUsername(event != null ? event.getActorUsername() : null)
                .actorDisplayName(event != null ? event.getActorDisplayName() : null)
                .operationCode(event != null ? event.getOperationCode() : null)
                .operationLabel(event != null ? event.getOperationLabel() : null)
                .summary(event != null ? event.getSummary() : null)
                .read(notification.getReadAt() != null)
                .readAt(notification.getReadAt())
                .createdAt(notification.getCreatedAt())
                .createdAtText(notification.getCreatedAt() != null
                        ? notification.getCreatedAt().format(DATE_TIME_FORMATTER)
                        : "")
                .changes(changes)
                .build();
    }

    private String defaultTitle(OrderChangeEvent event, OrderNotificationAudience audience) {
        if (audience == OrderNotificationAudience.MANAGED_BY_ONLY) {
            return "긴급 관리자요청 · 발주 #" + event.getOrder().getId();
        }
        return event.getOperationLabel() + " · 발주 #" + event.getOrder().getId();
    }

    private String defaultMessage(OrderChangeEvent event) {
        String actor = normalizeText(event.getActorDisplayName());
        if (actor == null) actor = normalizeText(event.getActorUsername());
        if (actor == null) actor = "시스템";

        return actor + "님이 " + event.getOperationLabel() + " 작업을 처리했습니다. " + event.getSummary();
    }

    private Long requireMemberId(Member member) {
        if (member == null || member.getId() == null) {
            throw new IllegalArgumentException("로그인 사용자 정보가 없습니다.");
        }
        return member.getId();
    }

    private String normalizeText(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
