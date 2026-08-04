package com.dev.HiddenBATHAuto.service.order;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dev.HiddenBATHAuto.dto.orderchange.OrderChangeNoticeDto;
import com.dev.HiddenBATHAuto.dto.orderchange.OrderChangeSummaryDto;
import com.dev.HiddenBATHAuto.dto.orderchange.OrderCheckAggregateDto;
import com.dev.HiddenBATHAuto.dto.orderchange.OrderFieldChangeCommand;
import com.dev.HiddenBATHAuto.dto.orderchange.OrderMemberCheckStateDto;
import com.dev.HiddenBATHAuto.dto.production.ProductionCheckViewDto;
import com.dev.HiddenBATHAuto.enums.notification.OrderNotificationAudience;
import com.dev.HiddenBATHAuto.enums.order.OrderChangeSourceArea;
import com.dev.HiddenBATHAuto.enums.order.OrderCheckState;
import com.dev.HiddenBATHAuto.enums.order.OrderWorkArea;
import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.task.Order;
import com.dev.HiddenBATHAuto.model.task.audit.OrderChangeEvent;
import com.dev.HiddenBATHAuto.model.task.audit.OrderChangeField;
import com.dev.HiddenBATHAuto.model.task.audit.OrderChangeImpact;
import com.dev.HiddenBATHAuto.model.task.audit.OrderMemberCheckStatus;
import com.dev.HiddenBATHAuto.model.task.audit.OrderWorkRevision;
import com.dev.HiddenBATHAuto.repository.auth.MemberRepository;
import com.dev.HiddenBATHAuto.repository.order.OrderChangeEventRepository;
import com.dev.HiddenBATHAuto.repository.order.OrderChangeImpactRepository;
import com.dev.HiddenBATHAuto.repository.order.OrderMemberCheckStatusRepository;
import com.dev.HiddenBATHAuto.repository.order.OrderRepository;
import com.dev.HiddenBATHAuto.repository.order.OrderWorkRevisionRepository;
import com.dev.HiddenBATHAuto.service.ordernotification.OrderNotificationService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrderChangeAuditService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final OrderRepository orderRepository;
    private final MemberRepository memberRepository;
    private final OrderWorkRevisionRepository orderWorkRevisionRepository;
    private final OrderMemberCheckStatusRepository orderMemberCheckStatusRepository;
    private final OrderChangeEventRepository orderChangeEventRepository;
    private final OrderChangeImpactRepository orderChangeImpactRepository;
    private final OrderNotificationService orderNotificationService;

    /**
     * 어느 팀의 변경이든 동일한 형식으로 기록하는 공통 진입점입니다.
     * 각 field change에 affectedAreas를 지정하므로 생산/출고/배송 재확인 범위를 독립적으로 확장할 수 있습니다.
     */
    @Transactional
    public OrderChangeEvent recordOrderChange(
            Order order,
            OrderChangeSourceArea sourceArea,
            Long actorMemberId,
            String actorUsername,
            String actorDisplayName,
            String operationCode,
            String operationLabel,
            String requestPath,
            List<OrderFieldChangeCommand> changes
    ) {
        return recordOrderChange(
                order,
                sourceArea,
                actorMemberId,
                actorUsername,
                actorDisplayName,
                operationCode,
                operationLabel,
                requestPath,
                changes,
                OrderNotificationAudience.RELATED_USERS,
                null,
                null
        );
    }

    /**
     * 변경 이력, 팀별 재확인 버전, 수신자별 웹 알림을 한 트랜잭션에서 생성합니다.
     * 카카오/SOLAPI와 WebSocket 전달은 커밋 이후 별도 단계에서 실행됩니다.
     */
    @Transactional
    public OrderChangeEvent recordOrderChange(
            Order order,
            OrderChangeSourceArea sourceArea,
            Long actorMemberId,
            String actorUsername,
            String actorDisplayName,
            String operationCode,
            String operationLabel,
            String requestPath,
            List<OrderFieldChangeCommand> changes,
            OrderNotificationAudience notificationAudience,
            String notificationTitle,
            String notificationMessage
    ) {
        return recordOrderChange(
                order,
                sourceArea,
                actorMemberId,
                actorUsername,
                actorDisplayName,
                operationCode,
                operationLabel,
                requestPath,
                changes,
                notificationAudience,
                notificationTitle,
                notificationMessage,
                List.of()
        );
    }

    /**
     * 담당자 변경처럼 변경 이전 관계자에게도 마지막 알림을 보내야 하는 경우
     * additionalRecipientMemberIds에 기존 담당자 ID를 전달합니다.
     */
    @Transactional
    public OrderChangeEvent recordOrderChange(
            Order order,
            OrderChangeSourceArea sourceArea,
            Long actorMemberId,
            String actorUsername,
            String actorDisplayName,
            String operationCode,
            String operationLabel,
            String requestPath,
            List<OrderFieldChangeCommand> changes,
            OrderNotificationAudience notificationAudience,
            String notificationTitle,
            String notificationMessage,
            Collection<Long> additionalRecipientMemberIds
    ) {
        if (order == null || order.getId() == null) {
            throw new IllegalArgumentException("변경이력을 기록할 오더가 없습니다.");
        }

        List<OrderFieldChangeCommand> actualChanges = normalizeChanges(changes);
        if (actualChanges.isEmpty()) {
            return null;
        }

        // 동일 오더의 버전 증가 순서를 보장합니다.
        Order lockedOrder = orderRepository.findByIdForChangeAuditLock(order.getId())
                .orElseThrow(() -> new IllegalArgumentException("변경이력을 기록할 오더를 찾을 수 없습니다. orderId=" + order.getId()));

        String summary = actualChanges.stream()
                .map(OrderFieldChangeCommand::fieldLabel)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.joining(", ")) + " 변경";

        ResolvedActor resolvedActor = resolveActor(actorMemberId, actorUsername, actorDisplayName);

        OrderChangeEvent event = OrderChangeEvent.create(
                lockedOrder,
                sourceArea,
                resolvedActor.memberId(),
                resolvedActor.username(),
                resolvedActor.displayName(),
                operationCode,
                operationLabel,
                requestPath,
                summary
        );

        int sortOrder = 0;
        EnumSet<OrderWorkArea> affectedAreas = EnumSet.noneOf(OrderWorkArea.class);

        for (OrderFieldChangeCommand change : actualChanges) {
            event.addField(OrderChangeField.of(
                    change.fieldKey(),
                    change.fieldLabel(),
                    displayValue(change.beforeValue()),
                    displayValue(change.afterValue()),
                    sortOrder++
            ));
            affectedAreas.addAll(change.affectedAreas());
        }

        for (OrderWorkArea workArea : affectedAreas) {
            OrderWorkRevision revision = getOrCreateRevisionForUpdate(lockedOrder, workArea);
            long nextVersion = revision.incrementAndGet();
            orderWorkRevisionRepository.save(revision);
            event.addImpact(OrderChangeImpact.of(workArea, nextVersion));
        }

        OrderChangeEvent savedEvent = orderChangeEventRepository.save(event);
        orderChangeEventRepository.flush();

        orderNotificationService.createForChangeEvent(
                savedEvent,
                notificationAudience == null ? OrderNotificationAudience.RELATED_USERS : notificationAudience,
                affectedAreas,
                notificationTitle,
                notificationMessage,
                additionalRecipientMemberIds
        );

        return savedEvent;
    }

    @Transactional(readOnly = true)
    public Map<Long, ProductionCheckViewDto> getProductionCheckViewMap(
            Collection<Long> orderIds,
            Member member
    ) {
        List<Long> ids = normalizeOrderIds(orderIds);
        if (ids.isEmpty() || member == null || member.getId() == null) {
            return Map.of();
        }

        Map<Long, Long> revisionMap = orderWorkRevisionRepository
                .findByOrder_IdInAndWorkArea(ids, OrderWorkArea.PRODUCTION)
                .stream()
                .filter(row -> row.getOrder() != null && row.getOrder().getId() != null)
                .collect(Collectors.toMap(
                        row -> row.getOrder().getId(),
                        OrderWorkRevision::getCurrentVersion,
                        Math::max,
                        LinkedHashMap::new
                ));

        Map<Long, OrderMemberCheckStatus> statusMap = orderMemberCheckStatusRepository
                .findByOrder_IdInAndMember_IdAndWorkArea(ids, member.getId(), OrderWorkArea.PRODUCTION)
                .stream()
                .filter(row -> row.getOrder() != null && row.getOrder().getId() != null)
                .collect(Collectors.toMap(
                        row -> row.getOrder().getId(),
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        Map<Long, OrderChangeImpact> latestImpactMap = new LinkedHashMap<>();
        for (OrderChangeImpact impact : orderChangeImpactRepository.findLatestCandidates(ids, OrderWorkArea.PRODUCTION)) {
            Long orderId = resolveImpactOrderId(impact);
            if (orderId != null) {
                latestImpactMap.putIfAbsent(orderId, impact);
            }
        }

        Map<Long, ProductionCheckViewDto> result = new LinkedHashMap<>();
        for (Long orderId : ids) {
            long currentVersion = revisionMap.getOrDefault(orderId, 0L);
            OrderMemberCheckStatus memberStatus = statusMap.get(orderId);
            OrderCheckState state = resolveState(memberStatus, currentVersion);
            OrderChangeEvent latestEvent = latestImpactMap.containsKey(orderId)
                    ? latestImpactMap.get(orderId).getEvent()
                    : null;

            result.put(orderId, ProductionCheckViewDto.builder()
                    .orderId(orderId)
                    .checkState(state.name())
                    .checkStateLabel(state.getLabel())
                    .checked(state == OrderCheckState.CHECKED)
                    .checkedByUsername(memberStatus != null ? resolveMemberDisplay(memberStatus.getMember()) : "")
                    .checkedAtText(memberStatus != null ? formatDateTime(memberStatus.getCheckedAt()) : "")
                    .revisionMarkedByUsername(state == OrderCheckState.REVISED_AFTER_CHECK
                            ? resolveEventActor(latestEvent)
                            : "")
                    .revisionMarkedAtText(state == OrderCheckState.REVISED_AFTER_CHECK && latestEvent != null
                            ? formatDateTime(latestEvent.getCreatedAt())
                            : "")
                    .revisionReason(state == OrderCheckState.REVISED_AFTER_CHECK && latestEvent != null
                            ? safeText(latestEvent.getSummary())
                            : "")
                    .revisionCount(state == OrderCheckState.REVISED_AFTER_CHECK
                            ? Math.toIntExact(Math.min(Integer.MAX_VALUE,
                                    Math.max(0L, currentVersion - memberStatus.getLastCheckedVersion())))
                            : 0)
                    .build());
        }

        return result;
    }

    @Transactional
    public OrderMemberCheckResult markChecked(
            Order order,
            Member member,
            OrderWorkArea workArea
    ) {
        if (order == null || order.getId() == null) {
            throw new IllegalArgumentException("확인 처리할 오더가 없습니다.");
        }
        if (member == null || member.getId() == null) {
            throw new IllegalArgumentException("확인 처리할 사용자가 없습니다.");
        }

        Order lockedOrder = orderRepository.findByIdForChangeAuditLock(order.getId())
                .orElseThrow(() -> new IllegalArgumentException("해당 발주를 찾을 수 없습니다."));

        long currentVersion = orderWorkRevisionRepository
                .findForUpdate(order.getId(), workArea)
                .map(OrderWorkRevision::getCurrentVersion)
                .orElse(0L);

        OrderMemberCheckStatus status = orderMemberCheckStatusRepository
                .findForUpdate(order.getId(), member.getId(), workArea)
                .orElse(null);

        boolean firstCheck = status == null;
        long previousVersion = firstCheck ? currentVersion : status.getLastCheckedVersion();
        boolean revisedBeforeCheck = !firstCheck && previousVersion < currentVersion;

        List<OrderChangeNoticeDto> notices = revisedBeforeCheck
                ? orderChangeImpactRepository.findPendingImpacts(order.getId(), workArea, previousVersion)
                        .stream()
                        .map(OrderChangeImpact::getEvent)
                        .filter(Objects::nonNull)
                        .distinct()
                        .map(this::toNoticeDto)
                        .toList()
                : List.of();

        if (status == null) {
            status = OrderMemberCheckStatus.checked(lockedOrder, member, workArea, currentVersion);
        } else {
            status.markChecked(currentVersion);
        }

        orderMemberCheckStatusRepository.save(status);

        return new OrderMemberCheckResult(
                order.getId(),
                currentVersion,
                revisedBeforeCheck,
                notices,
                resolveMemberDisplay(member),
                status.getCheckedAt()
        );
    }

    @Transactional(readOnly = true)
    public Map<Long, OrderChangeSummaryDto> getLatestChangeMap(Collection<Long> orderIds) {
        List<Long> ids = normalizeOrderIds(orderIds);
        if (ids.isEmpty()) return Map.of();

        Map<Long, OrderChangeSummaryDto> result = new LinkedHashMap<>();
        for (OrderChangeEvent event : orderChangeEventRepository.findLatestCandidates(ids)) {
            if (event.getOrder() == null || event.getOrder().getId() == null) continue;
            result.putIfAbsent(event.getOrder().getId(), toSummaryDto(event));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<OrderChangeNoticeDto> getOrderHistory(Long orderId, int limit) {
        if (orderId == null || orderId <= 0) return List.of();
        int safeLimit = Math.max(1, Math.min(100, limit));
        return orderChangeEventRepository.findHistory(orderId, PageRequest.of(0, safeLimit))
                .stream()
                .map(this::toNoticeDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<Long, OrderCheckAggregateDto> getCheckAggregateMap(
            Collection<Long> orderIds,
            OrderWorkArea workArea
    ) {
        List<Long> ids = normalizeOrderIds(orderIds);
        if (ids.isEmpty()) return Map.of();

        Map<Long, Long> revisionMap = orderWorkRevisionRepository.findByOrder_IdInAndWorkArea(ids, workArea)
                .stream()
                .collect(Collectors.toMap(
                        row -> row.getOrder().getId(),
                        OrderWorkRevision::getCurrentVersion,
                        Math::max
                ));

        Map<Long, List<OrderMemberCheckStatus>> grouped = orderMemberCheckStatusRepository
                .findByOrder_IdInAndWorkArea(ids, workArea)
                .stream()
                .collect(Collectors.groupingBy(row -> row.getOrder().getId()));

        Map<Long, OrderCheckAggregateDto> result = new LinkedHashMap<>();
        for (Long orderId : ids) {
            long currentVersion = revisionMap.getOrDefault(orderId, 0L);
            List<OrderMemberCheckStatus> statuses = grouped.getOrDefault(orderId, List.of());
            int checked = 0;
            int revised = 0;

            for (OrderMemberCheckStatus status : statuses) {
                if (status.getLastCheckedVersion() < currentVersion) revised++;
                else checked++;
            }

            result.put(orderId, OrderCheckAggregateDto.builder()
                    .orderId(orderId)
                    .trackedMemberCount(statuses.size())
                    .checkedCount(checked)
                    .revisedCount(revised)
                    .build());
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<OrderMemberCheckStateDto> getMemberCheckStates(Long orderId, OrderWorkArea workArea) {
        if (orderId == null || orderId <= 0) return List.of();
        long currentVersion = orderWorkRevisionRepository.findByOrder_IdAndWorkArea(orderId, workArea)
                .map(OrderWorkRevision::getCurrentVersion)
                .orElse(0L);

        return orderMemberCheckStatusRepository
                .findMemberStates(orderId, workArea)
                .stream()
                .map(status -> {
                    OrderCheckState state = resolveState(status, currentVersion);
                    Member member = status.getMember();
                    return OrderMemberCheckStateDto.builder()
                            .memberId(member != null ? member.getId() : null)
                            .memberName(member != null ? safeText(member.getName()) : "")
                            .username(member != null ? safeText(member.getUsername()) : "")
                            .checkState(state.name())
                            .checkStateLabel(state.getLabel())
                            .lastCheckedVersion(status.getLastCheckedVersion())
                            .currentVersion(currentVersion)
                            .checkedAtText(formatDateTime(status.getCheckedAt()))
                            .build();
                })
                .toList();
    }

    private OrderWorkRevision getOrCreateRevisionForUpdate(Order order, OrderWorkArea workArea) {
        return orderWorkRevisionRepository.findForUpdate(order.getId(), workArea)
                .orElseGet(() -> orderWorkRevisionRepository.save(OrderWorkRevision.initial(order, workArea)));
    }

    private List<OrderFieldChangeCommand> normalizeChanges(List<OrderFieldChangeCommand> changes) {
        if (changes == null || changes.isEmpty()) return List.of();
        return changes.stream()
                .filter(Objects::nonNull)
                .filter(OrderFieldChangeCommand::isActuallyChanged)
                .toList();
    }

    private List<Long> normalizeOrderIds(Collection<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) return List.of();
        return orderIds.stream()
                .filter(Objects::nonNull)
                .filter(id -> id > 0)
                .collect(Collectors.toCollection(LinkedHashSet::new))
                .stream()
                .toList();
    }

    private OrderCheckState resolveState(OrderMemberCheckStatus status, long currentVersion) {
        if (status == null) return OrderCheckState.UNCHECKED;
        return status.getLastCheckedVersion() < currentVersion
                ? OrderCheckState.REVISED_AFTER_CHECK
                : OrderCheckState.CHECKED;
    }

    private Long resolveImpactOrderId(OrderChangeImpact impact) {
        if (impact == null || impact.getEvent() == null || impact.getEvent().getOrder() == null) return null;
        return impact.getEvent().getOrder().getId();
    }

    private OrderChangeNoticeDto toNoticeDto(OrderChangeEvent event) {
        List<OrderChangeNoticeDto.FieldChange> fields = event.getFields() == null
                ? List.of()
                : event.getFields().stream()
                        .sorted(Comparator.comparingInt(OrderChangeField::getSortOrder))
                        .map(field -> new OrderChangeNoticeDto.FieldChange(
                                field.getFieldKey(),
                                field.getFieldLabel(),
                                displayValue(field.getBeforeValue()),
                                displayValue(field.getAfterValue())
                        ))
                        .toList();

        return OrderChangeNoticeDto.builder()
                .eventId(event.getId())
                .sourceArea(event.getSourceArea() != null ? event.getSourceArea().name() : "SYSTEM")
                .sourceAreaLabel(event.getSourceArea() != null ? event.getSourceArea().getLabel() : "시스템")
                .actorUsername(safeText(event.getActorUsername()))
                .actorDisplayName(resolveEventActor(event))
                .operationLabel(safeText(event.getOperationLabel()))
                .requestPath(safeText(event.getRequestPath()))
                .summary(safeText(event.getSummary()))
                .changedAtText(formatDateTime(event.getCreatedAt()))
                .fields(fields)
                .build();
    }

    private OrderChangeSummaryDto toSummaryDto(OrderChangeEvent event) {
        return OrderChangeSummaryDto.builder()
                .eventId(event.getId())
                .orderId(event.getOrder() != null ? event.getOrder().getId() : null)
                .sourceArea(event.getSourceArea() != null ? event.getSourceArea().name() : "SYSTEM")
                .sourceAreaLabel(event.getSourceArea() != null ? event.getSourceArea().getLabel() : "시스템")
                .actorDisplay(resolveEventActor(event))
                .operationLabel(safeText(event.getOperationLabel()))
                .summary(safeText(event.getSummary()))
                .requestPath(safeText(event.getRequestPath()))
                .changedAtText(formatDateTime(event.getCreatedAt()))
                .build();
    }

    private ResolvedActor resolveActor(
            Long actorMemberId,
            String actorUsername,
            String actorDisplayName
    ) {
        String normalizedUsername = safeText(actorUsername);
        String normalizedDisplayName = safeText(actorDisplayName);

        Member actor = null;
        if (actorMemberId != null) {
            actor = memberRepository.findById(actorMemberId).orElse(null);
        } else if (!normalizedUsername.isBlank()) {
            actor = memberRepository.findByUsername(normalizedUsername).orElse(null);
        }

        if (actor == null) {
            return new ResolvedActor(
                    actorMemberId,
                    normalizedUsername,
                    normalizedDisplayName
            );
        }

        String resolvedUsername = normalizedUsername.isBlank()
                ? safeText(actor.getUsername())
                : normalizedUsername;
        boolean displayLooksLikeUsername = !normalizedUsername.isBlank()
                && normalizedDisplayName.equals(normalizedUsername);
        String resolvedDisplayName = normalizedDisplayName.isBlank() || displayLooksLikeUsername
                ? resolveMemberDisplay(actor)
                : normalizedDisplayName;

        return new ResolvedActor(actor.getId(), resolvedUsername, resolvedDisplayName);
    }

    private record ResolvedActor(Long memberId, String username, String displayName) {
    }

    private String resolveEventActor(OrderChangeEvent event) {
        if (event == null) return "시스템";
        if (event.getActorDisplayName() != null && !event.getActorDisplayName().isBlank()) {
            return event.getActorDisplayName();
        }
        if (event.getActorUsername() != null && !event.getActorUsername().isBlank()) {
            return event.getActorUsername();
        }
        return "시스템";
    }

    private String resolveMemberDisplay(Member member) {
        if (member == null) return "";
        if (member.getName() != null && !member.getName().isBlank()) return member.getName().trim();
        if (member.getUsername() != null && !member.getUsername().isBlank()) return member.getUsername().trim();
        return member.getId() != null ? "MEMBER-" + member.getId() : "";
    }

    private String formatDateTime(LocalDateTime value) {
        return value == null ? "" : value.format(DATE_TIME_FORMATTER);
    }

    private String displayValue(String value) {
        if (value == null || value.isBlank()) return "-";
        String normalized = value.replace("\r", " ").replace("\n", " ").replaceAll("\\s{2,}", " ").trim();
        return normalized.length() <= 1000 ? normalized : normalized.substring(0, 1000) + "…";
    }

    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }

    public record OrderMemberCheckResult(
            Long orderId,
            long checkedVersion,
            boolean revisedBeforeCheck,
            List<OrderChangeNoticeDto> changeNotices,
            String checkedByUsername,
            LocalDateTime checkedAt
    ) {
    }
}
