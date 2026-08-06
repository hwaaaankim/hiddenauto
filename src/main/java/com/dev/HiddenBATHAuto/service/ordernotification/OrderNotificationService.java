package com.dev.HiddenBATHAuto.service.ordernotification;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dev.HiddenBATHAuto.config.notification.OrderNotificationProperties;
import com.dev.HiddenBATHAuto.dto.ordernotification.OrderNotificationFieldDto;
import com.dev.HiddenBATHAuto.dto.ordernotification.OrderNotificationItemDto;
import com.dev.HiddenBATHAuto.dto.ordernotification.OrderNotificationPageDto;
import com.dev.HiddenBATHAuto.dto.ordernotification.OrderNotificationSummaryDto;
import com.dev.HiddenBATHAuto.enums.notification.OrderNotificationAction;
import com.dev.HiddenBATHAuto.enums.notification.OrderNotificationAudience;
import com.dev.HiddenBATHAuto.enums.notification.OrderNotificationCategory;
import com.dev.HiddenBATHAuto.enums.order.OrderChangeSourceArea;
import com.dev.HiddenBATHAuto.enums.order.OrderWorkArea;
import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.auth.MemberRole;
import com.dev.HiddenBATHAuto.model.notification.OrderNotification;
import com.dev.HiddenBATHAuto.model.task.Order;
import com.dev.HiddenBATHAuto.model.task.OrderStatus;
import com.dev.HiddenBATHAuto.model.task.audit.OrderChangeEvent;
import com.dev.HiddenBATHAuto.model.task.audit.OrderChangeField;
import com.dev.HiddenBATHAuto.repository.notification.OrderNotificationRepository;
import com.dev.HiddenBATHAuto.service.order.OrderTeamAccessPolicyService;
import com.dev.HiddenBATHAuto.service.ordernotification.OrderNotificationPolicyService.ChannelPolicy;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrderNotificationService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final int MAX_PAGE_SIZE = 100;
    private static final int READ_BATCH_SIZE = 500;

    private final OrderNotificationRepository notificationRepository;
    private final OrderNotificationRecipientResolver recipientResolver;
    private final OrderNotificationActionResolver actionResolver;
    private final OrderNotificationPolicyService policyService;
    private final OrderTeamAccessPolicyService accessPolicyService;
    private final OrderNotificationProperties properties;
    private final ApplicationEventPublisher eventPublisher;
    private final OrderNotificationBatchContext batchContext;

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

        List<OrderNotificationRecipientResolver.RecipientTarget> targets = recipientResolver.resolve(
                event,
                affectedAreas,
                audience,
                additionalRecipientMemberIds
        );
        if (targets.isEmpty()) {
            return List.of();
        }

        OrderNotificationAction action = actionResolver.resolve(event);
        Map<com.dev.HiddenBATHAuto.enums.notification.OrderNotificationRecipientGroup, ChannelPolicy> policies =
                policyService.resolvePolicies(event.getSourceArea(), action);

        String defaultTitle = normalizeText(explicitTitle) != null
                ? explicitTitle.trim()
                : defaultTitle(event, audience);
        String defaultMessage = normalizeText(explicitMessage) != null
                ? explicitMessage.trim()
                : defaultMessage(event);
        String kakaoBatchKey = batchContext.currentBatchKey();

        List<OrderNotification> notifications = new ArrayList<>(targets.size());
        for (OrderNotificationRecipientResolver.RecipientTarget target : targets) {
            ChannelPolicy channelPolicy = policies.getOrDefault(
                    target.recipientGroup(),
                    policyService.defaultPolicy(event.getSourceArea(), action, target.recipientGroup())
            );
            if (!channelPolicy.webEnabled() && !channelPolicy.kakaoEnabled()) {
                continue;
            }

            notifications.add(OrderNotification.create(
                    event,
                    target.member(),
                    target.category(),
                    action,
                    target.recipientGroup(),
                    channelPolicy.webEnabled(),
                    channelPolicy.kakaoEnabled(),
                    firstNonBlank(target.title(), defaultTitle),
                    firstNonBlank(target.message(), defaultMessage),
                    kakaoBatchKey
            ));
        }
        if (notifications.isEmpty()) return List.of();

        List<OrderNotification> saved = notificationRepository.saveAll(notifications);
        notificationRepository.flush();

        List<Long> ids = saved.stream()
                .map(OrderNotification::getId)
                .filter(Objects::nonNull)
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
                .totalUnreadCount(notificationRepository.countByRecipient_IdAndReadAtIsNullAndWebEnabledTrue(memberId))
                .unreadCountByCategory(counts)
                .build();
    }

    /**
     * 읽지 않은 웹 알림만 ID 커서 방식으로 조회합니다. 새 알림이 동시에 들어와도 다음 페이지가 중복/누락되지 않습니다.
     */
    @Transactional(readOnly = true)
    public OrderNotificationPageDto getNotifications(
            Member member,
            OrderNotificationCategory category,
            Long cursor,
            int size
    ) {
        Long memberId = requireMemberId(member);
        int safeSize = Math.min(MAX_PAGE_SIZE, Math.max(1, size));
        List<Long> fetchedIds = notificationRepository.findUnreadIds(
                memberId,
                category,
                cursor,
                PageRequest.of(0, safeSize + 1)
        );

        boolean hasNext = fetchedIds.size() > safeSize;
        List<Long> pageIds = hasNext
                ? new ArrayList<>(fetchedIds.subList(0, safeSize))
                : new ArrayList<>(fetchedIds);
        Long nextCursor = hasNext && !pageIds.isEmpty()
                ? pageIds.get(pageIds.size() - 1)
                : null;

        Map<Long, OrderNotification> notificationById = pageIds.isEmpty()
                ? Map.of()
                : notificationRepository.findPageDetailsByIdIn(pageIds).stream()
                        .filter(row -> row != null && row.getId() != null)
                        .collect(java.util.stream.Collectors.toMap(
                                OrderNotification::getId,
                                row -> row,
                                (left, right) -> left,
                                LinkedHashMap::new
                        ));
        List<OrderNotification> pageRows = pageIds.stream()
                .map(notificationById::get)
                .filter(Objects::nonNull)
                .toList();

        return OrderNotificationPageDto.builder()
                .content(pageRows.stream().map(this::toDto).toList())
                .nextCursor(nextCursor)
                .hasNext(hasNext)
                .size(safeSize)
                .build();
    }

    /** 개별 확인은 웹 알림으로 생성된 행에만 반영합니다. */
    @Transactional
    public OrderNotificationItemDto markRead(Member member, Long notificationId) {
        Long memberId = requireMemberId(member);
        if (notificationId == null) {
            throw new IllegalArgumentException("알림 ID가 없습니다.");
        }

        OrderNotification notification = notificationRepository
                .findByIdAndRecipient_IdAndWebEnabledTrue(notificationId, memberId)
                .orElseThrow(() -> new IllegalArgumentException("해당 알림을 찾을 수 없습니다."));
        notification.markRead(LocalDateTime.now());
        return toDto(notification);
    }

    /**
     * 일괄확인은 현재 브라우저에 실제로 로드된 ID만 처리합니다. 다른 사용자의 ID는 recipient 조건으로 무시됩니다.
     */
    @Transactional
    public int markLoadedRead(Member member, Collection<Long> notificationIds) {
        Long memberId = requireMemberId(member);
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        if (notificationIds != null) {
            notificationIds.stream()
                    .filter(Objects::nonNull)
                    .filter(id -> id > 0)
                    .forEach(ids::add);
        }
        if (ids.isEmpty()) return 0;

        int updated = 0;
        List<Long> list = new ArrayList<>(ids);
        for (int start = 0; start < list.size(); start += READ_BATCH_SIZE) {
            int end = Math.min(start + READ_BATCH_SIZE, list.size());
            updated += notificationRepository.markReadByIds(memberId, list.subList(start, end));
        }
        return updated;
    }

    public OrderNotificationItemDto toDto(OrderNotification notification) {
        OrderChangeEvent event = notification.getEvent();
        OrderStatus resolvedStatus = notification.resolveOrderStatus();
        boolean registrationEvent = event != null
                && normalizeText(event.getOperationCode()) != null
                && event.getOperationCode().toUpperCase(Locale.ROOT).contains("ORDER_CREATED");
        List<OrderNotificationFieldDto> changes = !registrationEvent && event != null && event.getFields() != null
                ? event.getFields().stream()
                        .sorted(Comparator.comparingInt(OrderChangeField::getSortOrder))
                        .map(field -> OrderNotificationFieldDto.builder()
                                .fieldKey(field.getFieldKey())
                                .fieldLabel(field.getFieldLabel())
                                .beforeValue(field.getBeforeValue())
                                .afterValue(field.getAfterValue())
                                .build())
                        .toList()
                : List.of();

        Shortcut shortcut = resolveShortcut(notification);
        return OrderNotificationItemDto.builder()
                .id(notification.getId())
                .eventId(event != null ? event.getId() : null)
                .orderId(notification.resolveOrderId())
                .orderStatus(resolvedStatus != null ? resolvedStatus.name() : null)
                .orderStatusLabel(resolvedStatus != null ? resolvedStatus.getLabel() : null)
                .taskId(notification.resolveTaskId())
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
                .shortcutEnabled(shortcut.enabled())
                .shortcutLabel(shortcut.label())
                .shortcutUrl(shortcut.url())
                .build();
    }

    private Shortcut resolveShortcut(OrderNotification notification) {
        Order order = notification.getOrder();
        OrderChangeEvent event = notification.getEvent();
        Member recipient = notification.getRecipient();

        /*
         * 오더가 삭제되었거나 FK가 해제된 감사 이력은 ID 스냅샷만 표시하고 링크는 제공하지 않습니다.
         * 담당자 변경 후 이전 담당자처럼 현재 조회 권한을 잃은 수신자도 아래 권한 검사에서 링크가 제거됩니다.
         */
        if (order == null || order.getId() == null || recipient == null) return Shortcut.disabled();

        long orderId = order.getId();
        String status = order.getStatus() != null ? order.getStatus().name() : "all";
        MemberRole role = recipient.getRole();
        if (role == MemberRole.ADMIN || role == MemberRole.MANAGEMENT) {
            // 관리자는 승인 전·취소 상태를 포함해 모든 발주를 조회할 수 있으므로 삭제된 경우에만 링크가 없습니다.
            return new Shortcut(true, "발주 바로가기",
                    "/management/nonStandardTaskList?orderIdFrom=" + orderId
                            + "&orderIdTo=" + orderId
                            + "&orderStatus=" + encode(status)
                            + "&dateCriteria=all&productCategoryId=all&standard=all&size=10");
        }

        OrderStatus transitionAfter = resolveStatusAfter(event);
        if (isHidden(order.getStatus()) || isHidden(transitionAfter)) {
            return Shortcut.disabled();
        }

        String teamName = recipient.getTeam() != null ? normalizeText(recipient.getTeam().getName()) : null;
        if ("생산팀".equals(teamName)) {
            if (!accessPolicyService.canViewProductionOrder(recipient, order)) {
                return Shortcut.disabled();
            }
            return new Shortcut(true, "생산 발주 바로가기",
                    "/team/productionList?orderIdFrom=" + orderId
                            + "&orderIdTo=" + orderId
                            + "&statusFilter=" + encode(status));
        }
        if ("배송팀".equals(teamName)) {
            if (!accessPolicyService.canViewDeliveryOrder(recipient, order)) {
                return Shortcut.disabled();
            }
            StringBuilder url = new StringBuilder("/team/deliveryRoute?orderIdFrom=")
                    .append(orderId).append("&orderIdTo=").append(orderId);
            if (order.getPreferredDeliveryDate() != null) {
                url.append("&deliveryDate=").append(order.getPreferredDeliveryDate().toLocalDate());
            }
            return new Shortcut(true, "배송 발주 바로가기", url.toString());
        }
        if ("출고팀".equals(teamName)) {
            if (!accessPolicyService.canViewDispatchOrder(recipient, order)) {
                return Shortcut.disabled();
            }
            return new Shortcut(true, "출고 발주 바로가기",
                    "/team/dispatchList?orderIdFrom=" + orderId + "&orderIdTo=" + orderId);
        }
        return Shortcut.disabled();
    }

    private OrderStatus resolveStatusAfter(OrderChangeEvent event) {
        if (event == null || event.getFields() == null) return null;
        for (OrderChangeField field : event.getFields()) {
            if (field == null || !"status".equalsIgnoreCase(normalizeText(field.getFieldKey()))) continue;
            String value = normalizeText(field.getAfterValue());
            if (value == null) return null;
            for (OrderStatus status : OrderStatus.values()) {
                if (status.name().equalsIgnoreCase(value) || status.getLabel().equals(value)) return status;
            }
        }
        return null;
    }

    private boolean isHidden(OrderStatus status) {
        return status == OrderStatus.REQUESTED || status == OrderStatus.CANCELED;
    }

    private String defaultTitle(OrderChangeEvent event, OrderNotificationAudience audience) {
        Long orderId = event.resolveOrderId();
        if (audience == OrderNotificationAudience.MANAGED_BY_ONLY) {
            return "긴급 관리자요청 · 발주 #" + orderId;
        }

        String operationCode = normalizeText(event.getOperationCode());
        if (operationCode != null && operationCode.toUpperCase(Locale.ROOT).contains("ORDER_CREATED")) {
            return event.getSourceArea() == OrderChangeSourceArea.CUSTOMER
                    ? "고객 발주등록"
                    : "관리자 발주등록";
        }
        return event.getOperationLabel() + " · 발주 #" + orderId;
    }

    private String defaultMessage(OrderChangeEvent event) {
        String actor = normalizeText(event.getActorDisplayName());
        if (actor == null) actor = normalizeText(event.getActorUsername());
        if (actor == null) actor = "시스템";

        String operationCode = normalizeText(event.getOperationCode());
        if (operationCode != null && operationCode.toUpperCase(Locale.ROOT).contains("ORDER_CREATED")) {
            String registrationType = event.getSourceArea() == OrderChangeSourceArea.CUSTOMER
                    ? "고객 발주등록"
                    : "관리자 발주등록";
            return actor + "님이 " + registrationType + "을 완료했습니다.";
        }
        String summary = normalizeText(event.getSummary());
        return actor + "님이 " + event.getOperationLabel() + " 작업을 처리했습니다."
                + (summary == null ? "" : " " + summary);
    }

    private Long requireMemberId(Member member) {
        if (member == null || member.getId() == null) {
            throw new IllegalArgumentException("로그인 사용자 정보가 없습니다.");
        }
        return member.getId();
    }

    private String firstNonBlank(String preferred, String fallback) {
        return normalizeText(preferred) != null ? preferred.trim() : fallback;
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private String normalizeText(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record Shortcut(boolean enabled, String label, String url) {
        static Shortcut disabled() {
            return new Shortcut(false, null, null);
        }
    }
}
