package com.dev.HiddenBATHAuto.service.asnotification;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dev.HiddenBATHAuto.config.notification.AsNotificationProperties;
import com.dev.HiddenBATHAuto.dto.asnotification.AsImportantNotificationBatchDto;
import com.dev.HiddenBATHAuto.dto.asnotification.AsNotificationFieldDto;
import com.dev.HiddenBATHAuto.dto.asnotification.AsNotificationItemDto;
import com.dev.HiddenBATHAuto.dto.asnotification.AsNotificationPageDto;
import com.dev.HiddenBATHAuto.dto.asnotification.AsNotificationSummaryDto;
import com.dev.HiddenBATHAuto.enums.notification.AsNotificationAction;
import com.dev.HiddenBATHAuto.enums.notification.AsNotificationRecipientGroup;
import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.notification.AsNotification;
import com.dev.HiddenBATHAuto.model.task.AsStatus;
import com.dev.HiddenBATHAuto.model.task.as.audit.AsChangeEvent;
import com.dev.HiddenBATHAuto.repository.notification.AsNotificationRepository;
import com.dev.HiddenBATHAuto.service.asnotification.AsNotificationPolicyService.ChannelPolicy;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AsNotificationService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_IMPORTANT_SIZE = 100;
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final AsNotificationRepository repository;
    private final AsNotificationRecipientResolver recipientResolver;
    private final AsNotificationPolicyService policyService;
    private final AsNotificationProperties properties;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public List<AsNotification> createForEvent(AsChangeEvent event) {
        if (!properties.isEnabled() || event == null) return List.of();
        List<AsNotification> notifications = new ArrayList<>();
        for (AsNotificationRecipientResolver.RecipientTarget target : recipientResolver.resolve(event)) {
            ChannelPolicy policy = policyService.resolvePolicy(event.getSourceArea(), event.getAction(), target.recipientGroup());
            if (policy.disabled()) continue;
            String title = title(event, target.recipientGroup());
            String message = message(event, target.recipientGroup());
            notifications.add(AsNotification.create(
                    event, target.member(), target.recipientGroup(),
                    policy.webEnabled(), policy.kakaoEnabled(), policy.importantEnabled(),
                    title, message
            ));
        }
        if (notifications.isEmpty()) return List.of();
        List<AsNotification> saved = repository.saveAll(notifications);
        repository.flush();
        eventPublisher.publishEvent(new AsNotificationsCreatedEvent(saved.stream().map(AsNotification::getId).toList()));
        return saved;
    }

    @Transactional(readOnly = true)
    public AsNotificationSummaryDto getSummary(Member member) {
        if (member == null || member.getId() == null) return emptySummary();
        return AsNotificationSummaryDto.builder()
                .totalUnreadCount(repository.countBellUnread(member.getId()))
                .importantUnreadCount(repository.countImportantUnread(member.getId()))
                .pendingImportantConfirmationCount(repository.countPendingImportantConfirmation(member.getId()))
                .build();
    }

    @Transactional(readOnly = true)
    public AsNotificationPageDto getNotifications(Member member, boolean importantOnly, Long cursor, int size) {
        if (member == null || member.getId() == null) return emptyPage();
        int safeSize = Math.max(1, Math.min(MAX_PAGE_SIZE, size));
        List<Long> ids = repository.findUnreadIds(member.getId(), importantOnly, cursor,
                PageRequest.of(0, safeSize + 1));
        boolean hasNext = ids.size() > safeSize;
        List<Long> pageIds = hasNext ? ids.subList(0, safeSize) : ids;
        List<AsNotification> details = loadDetailsInOrder(pageIds);
        List<AsNotificationItemDto> content = details.stream().map(this::toDto).toList();
        Long nextCursor = hasNext && !pageIds.isEmpty() ? pageIds.get(pageIds.size() - 1) : null;
        return AsNotificationPageDto.builder()
                .content(content).nextCursor(nextCursor).hasNext(hasNext).size(content.size()).build();
    }

    @Transactional(readOnly = true)
    public AsImportantNotificationBatchDto getPendingImportantNotifications(Member member, int size) {
        if (member == null || member.getId() == null) {
            return AsImportantNotificationBatchDto.builder().content(List.of()).totalPendingCount(0).hasMore(false).size(0).build();
        }
        int safeSize = Math.max(1, Math.min(MAX_IMPORTANT_SIZE, size));
        long total = repository.countPendingImportantConfirmation(member.getId());
        List<Long> ids = repository.findPendingImportantIds(member.getId(), PageRequest.of(0, safeSize));
        List<AsNotification> rows = loadDetailsInOrder(ids);
        List<AsNotificationItemDto> content = rows.stream().map(this::toDto).toList();
        return AsImportantNotificationBatchDto.builder()
                .content(content).totalPendingCount(total).hasMore(total > content.size()).size(content.size()).build();
    }

    @Transactional
    public AsNotificationItemDto markRead(Member member, Long notificationId) {
        if (member == null || member.getId() == null || notificationId == null) {
            throw new IllegalArgumentException("읽음 처리할 AS 알림 정보가 올바르지 않습니다.");
        }
        AsNotification notification = repository.findReadableByIdAndRecipient(notificationId, member.getId())
                .orElseThrow(() -> new IllegalArgumentException("AS 알림을 찾을 수 없습니다."));
        notification.markRead(LocalDateTime.now());
        return toDto(notification);
    }

    @Transactional
    public int markLoadedRead(Member member, Collection<Long> ids) {
        if (member == null || member.getId() == null || ids == null || ids.isEmpty()) return 0;
        List<Long> normalized = ids.stream().filter(id -> id != null && id > 0).distinct().limit(500).toList();
        return normalized.isEmpty() ? 0 : repository.markReadByIds(member.getId(), normalized);
    }

    @Transactional
    public int confirmImportant(Member member, Collection<Long> ids) {
        if (member == null || member.getId() == null || ids == null || ids.isEmpty()) return 0;
        List<Long> normalized = ids.stream().filter(id -> id != null && id > 0).distinct().limit(500).toList();
        return normalized.isEmpty() ? 0 : repository.markImportantConfirmedByIds(member.getId(), normalized);
    }

    public AsNotificationItemDto toDto(AsNotification n) {
        AsChangeEvent event = n.getEvent();
        AsStatus status = n.resolveAsStatus();
        boolean shortcut = n.getAsTask() != null && n.getAsTask().getId() != null
                && n.getAction() != AsNotificationAction.DELETE
                && n.getAction() != AsNotificationAction.CUSTOMER_CANCEL;
        String url = null;
        if (shortcut) {
            AsNotificationRecipientGroup group = n.getRecipientGroup();
            if (group == AsNotificationRecipientGroup.MANAGER_02
                    || group == AsNotificationRecipientGroup.ADMIN) {
                url = "/management/asDetail/" + n.resolveAsTaskId();
            } else if (group == AsNotificationRecipientGroup.AS_HANDLER_CURRENT
                    && n.getRecipient() != null && n.getRecipient().getId() != null
                    && n.getAsTask().getAssignedHandler() != null
                    && Objects.equals(n.getRecipient().getId(), n.getAsTask().getAssignedHandler().getId())) {
                // 과거 알림의 수신자가 이후 담당자 변경으로 조회 권한을 잃었다면 죽은 링크를 노출하지 않습니다.
                url = "/team/asDetail/" + n.resolveAsTaskId();
            }
        }
        return AsNotificationItemDto.builder()
                .id(n.getId()).eventId(event != null ? event.getId() : null).notificationDomain("AS")
                .asTaskId(n.resolveAsTaskId())
                .asStatus(status != null ? status.name() : null)
                .asStatusLabel(status != null ? status.getLabelKr() : "-")
                .subject(n.resolveSubject())
                .title(n.getTitle()).message(n.getMessage())
                .sourceArea(event != null && event.getSourceArea() != null ? event.getSourceArea().name() : null)
                .sourceAreaLabel(event != null && event.getSourceArea() != null ? event.getSourceArea().getLabel() : null)
                .action(n.getAction() != null ? n.getAction().name() : null)
                .actionLabel(n.getAction() != null ? n.getAction().getLabel() : null)
                .recipientGroup(n.getRecipientGroup() != null ? n.getRecipientGroup().name() : null)
                .recipientGroupLabel(n.getRecipientGroup() != null ? n.getRecipientGroup().getLabel() : null)
                .actorMemberId(event != null ? event.getActorMemberId() : null)
                .actorUsername(event != null ? event.getActorUsername() : null)
                .actorDisplayName(event != null ? event.getActorDisplayName() : null)
                .operationCode(event != null ? event.getOperationCode() : null)
                .operationLabel(event != null ? event.getOperationLabel() : null)
                .summary(event != null ? event.getSummary() : null)
                .webEnabled(n.isWebEnabled()).important(n.isImportantEnabled())
                .importantConfirmed(n.getImportantConfirmedAt() != null).importantConfirmedAt(n.getImportantConfirmedAt())
                .read(n.getReadAt() != null).readAt(n.getReadAt())
                .createdAt(n.getCreatedAt()).createdAtText(format(n.getCreatedAt()))
                .changes(event == null || event.getFields() == null ? List.of() : event.getFields().stream()
                        .map(f -> AsNotificationFieldDto.builder()
                                .fieldKey(f.getFieldKey()).fieldLabel(f.getFieldLabel())
                                .beforeValue(f.getBeforeValue()).afterValue(f.getAfterValue()).build())
                        .toList())
                .shortcutEnabled(url != null).shortcutLabel("AS 상세 바로가기").shortcutUrl(url)
                .build();
    }

    private String title(AsChangeEvent event, AsNotificationRecipientGroup group) {
        Long id = event.resolveAsTaskId();
        return switch (event.getAction()) {
            case REQUEST_CREATED -> "신규 AS 신청 · #" + id;
            case CUSTOMER_UPDATE -> "고객 AS 수정 · #" + id;
            case CUSTOMER_CANCEL -> "고객 AS 취소 · #" + id;
            case DETAIL_UPDATE -> "AS 주요내용 변경 · #" + id;
            case STATUS_IN_PROGRESS -> "AS 진행 시작 · #" + id;
            case STATUS_CANCELED -> "AS 취소 처리 · #" + id;
            case STATUS_CHANGE -> "AS 상태 변경 · #" + id;
            case HANDLER_CHANGE -> "AS 담당자 변경 · #" + id;
            case VISIT_SCHEDULE_UPDATE -> "AS 방문일정 변경 · #" + id;
            case COMPLETE -> "AS 완료 처리 · #" + id;
            case INTERNAL_UPDATE -> "AS 내부내용 변경 · #" + id;
            case DELETE -> "AS 삭제 · #" + id;
        };
    }

    private String message(AsChangeEvent event, AsNotificationRecipientGroup group) {
        String actor = event.getActorDisplayName() != null ? event.getActorDisplayName()
                : event.getActorUsername() != null ? event.getActorUsername() : "시스템";
        String subject = event.getSubjectSnapshot() == null ? "" : " (" + event.getSubjectSnapshot() + ")";
        String base = "AS #" + event.resolveAsTaskId() + subject;
        return switch (event.getAction()) {
            case REQUEST_CREATED -> base + "가 신청되었습니다. 담당자와 요청 내용을 확인해 주세요.";
            case CUSTOMER_UPDATE -> base + "의 신청 내용이 고객에 의해 수정되었습니다. 최신 내용을 확인해 주세요.";
            case CUSTOMER_CANCEL -> base + "가 고객에 의해 취소되었습니다.";
            case DETAIL_UPDATE -> actor + "님이 " + base + "의 주요 내용을 변경했습니다.";
            case STATUS_IN_PROGRESS -> actor + "님이 " + base + "를 진행중으로 변경했습니다.";
            case STATUS_CANCELED -> actor + "님이 " + base + "를 취소 처리했습니다.";
            case STATUS_CHANGE -> actor + "님이 " + base + "의 상태를 변경했습니다.";
            case HANDLER_CHANGE -> actor + "님이 " + base + "의 담당자를 변경했습니다.";
            case VISIT_SCHEDULE_UPDATE -> actor + "님이 " + base + "의 방문 예정일/시간을 변경했습니다.";
            case COMPLETE -> actor + "님이 " + base + "를 완료 처리했습니다.";
            case INTERNAL_UPDATE -> actor + "님이 " + base + "의 담당자 메모 또는 첨부 자료를 변경했습니다.";
            case DELETE -> actor + "님이 " + base + "를 삭제했습니다.";
        };
    }

    private List<AsNotification> loadDetailsInOrder(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        Map<Long, AsNotification> byId = new LinkedHashMap<>();
        repository.findPageDetailsByIdIn(ids).stream()
                .filter(Objects::nonNull)
                .forEach(notification -> byId.put(notification.getId(), notification));
        return ids.stream().map(byId::get).filter(Objects::nonNull).toList();
    }

    private String format(LocalDateTime value) {
        return value == null ? "" : value.format(DATE_TIME_FORMATTER);
    }

    private AsNotificationSummaryDto emptySummary() {
        return AsNotificationSummaryDto.builder().totalUnreadCount(0).importantUnreadCount(0)
                .pendingImportantConfirmationCount(0).build();
    }

    private AsNotificationPageDto emptyPage() {
        return AsNotificationPageDto.builder().content(List.of()).nextCursor(null).hasNext(false).size(0).build();
    }
}
