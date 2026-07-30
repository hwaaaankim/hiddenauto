package com.dev.HiddenBATHAuto.service.order;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dev.HiddenBATHAuto.dto.orderchange.OrderFieldChangeCommand;
import com.dev.HiddenBATHAuto.enums.order.OrderChangeSourceArea;
import com.dev.HiddenBATHAuto.enums.order.OrderWorkArea;
import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.auth.TeamCategory;
import com.dev.HiddenBATHAuto.model.task.Order;
import com.dev.HiddenBATHAuto.model.task.OrderStatus;
import com.dev.HiddenBATHAuto.repository.auth.MemberRepository;
import com.dev.HiddenBATHAuto.repository.order.OrderRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrderStatusService {

    private static final String DELIVERY_TEAM_NAME = "배송팀";

    private final OrderRepository orderRepository;
    private final MemberRepository memberRepository;
    private final DeliveryOrderIndexService deliveryOrderIndexService;
    private final OrderChangeAuditService orderChangeAuditService;

    /**
     * 기존 API 호출부 보호용 메서드입니다.
     *
     * 배송담당자 선택값을 함께 보내지 않는 기존 일괄 컨펌 요청은
     * 직배송/화물/현장배송처럼 담당자 필수 배송수단이 포함되어 있으면 저장하지 않습니다.
     * 담당자 필수 건은 관리자 발주관리 화면의 신규 일괄 컨펌 모달을 사용해야 합니다.
     */
    @Transactional
    public int bulkConfirmRequestedOrders(List<Long> orderIds) {
        return bulkConfirmRequestedOrders(orderIds, Map.of(), null, null, null);
    }

    /**
     * 고객 발주 상태의 오더를 승인 완료로 일괄 변경하고 배송담당자/배송순서를 동기화합니다.
     *
     * 처리 규칙:
     * - 현재 상태가 REQUESTED인 오더만 처리합니다.
     * - 직배송/화물/현장배송은 오더별 배송팀 담당자가 필수입니다.
     * - 담당자가 지정되면 배송희망일이 반드시 있어야 합니다.
     * - 담당자와 assignedDeliveryTeam을 함께 저장합니다.
     * - 상태 변경 후 DeliveryOrderIndexService.ensureIndex를 호출하여 인덱스를 생성합니다.
     * - 하나라도 검증에 실패하면 전체 트랜잭션을 롤백합니다.
     */
    @Transactional
    public int bulkConfirmRequestedOrders(
            List<Long> orderIds,
            Map<Long, Long> deliveryHandlerIdByOrderId
    ) {
        return bulkConfirmRequestedOrders(orderIds, deliveryHandlerIdByOrderId, null, null, null);
    }

    @Transactional
    public int bulkConfirmRequestedOrders(
            List<Long> orderIds,
            Map<Long, Long> deliveryHandlerIdByOrderId,
            String actorUsername,
            String actorDisplayName,
            Long actorMemberId
    ) {
        List<Long> normalizedOrderIds = normalizeOrderIds(orderIds);
        Map<Long, Long> normalizedHandlerIds = normalizeHandlerAssignments(
                normalizedOrderIds,
                deliveryHandlerIdByOrderId
        );

        List<Order> orders = orderRepository.findAllByIdInForBulkConfirm(normalizedOrderIds);
        Map<Long, Order> orderMap = orders.stream()
                .collect(Collectors.toMap(Order::getId, Function.identity()));

        Set<Long> foundOrderIds = orderMap.keySet();
        Long missingOrderId = normalizedOrderIds.stream()
                .filter(id -> !foundOrderIds.contains(id))
                .findFirst()
                .orElse(null);

        if (missingOrderId != null) {
            throw new IllegalArgumentException(
                    missingOrderId + "번 오더를 찾을 수 없습니다. 목록을 새로고침 후 다시 시도해 주세요."
            );
        }

        Map<Long, Member> resolvedHandlerByOrderId = new LinkedHashMap<>();
        Map<Long, Long> beforeHandlerIdByOrderId = new LinkedHashMap<>();

        for (Long orderId : normalizedOrderIds) {
            Order beforeOrder = orderMap.get(orderId);
            beforeHandlerIdByOrderId.put(
                    orderId,
                    beforeOrder != null && beforeOrder.getAssignedDeliveryHandler() != null
                            ? beforeOrder.getAssignedDeliveryHandler().getId()
                            : null
            );
            Order order = orderMap.get(orderId);
            validateRequestedStatus(orderId, order);

            Long deliveryHandlerId = normalizedHandlerIds.get(orderId);
            boolean handlerRequired = deliveryOrderIndexService.isDeliveryHandlerRequiredMethod(order);

            if (handlerRequired && deliveryHandlerId == null) {
                String methodName = resolveDeliveryMethodName(order);
                throw new IllegalStateException(
                        orderId + "번 오더의 배송수단 '" + methodName
                                + "'은(는) 승인 완료 변경 시 배송팀 담당자 지정이 필수입니다."
                );
            }

            if (deliveryHandlerId == null) {
                resolvedHandlerByOrderId.put(orderId, null);
                continue;
            }

            if (order.getPreferredDeliveryDate() == null) {
                throw new IllegalStateException(
                        orderId + "번 오더는 배송희망일이 없어 배송담당자를 지정할 수 없습니다. "
                                + "넓게보기에서 배송희망일을 먼저 입력해 주세요."
                );
            }

            Member deliveryHandler = resolveDeliveryTeamHandler(deliveryHandlerId);
            resolvedHandlerByOrderId.put(orderId, deliveryHandler);
        }

        LocalDateTime now = LocalDateTime.now();

        for (Long orderId : normalizedOrderIds) {
            Order order = orderMap.get(orderId);
            Member deliveryHandler = resolvedHandlerByOrderId.get(orderId);

            order.setStatus(OrderStatus.CONFIRMED);
            order.setUpdatedAt(now);

            if (deliveryHandler == null) {
                order.setAssignedDeliveryHandler(null);
                order.setAssignedDeliveryTeam(null);
            } else {
                order.setAssignedDeliveryHandler(deliveryHandler);
                order.setAssignedDeliveryTeam(deliveryHandler.getTeamCategory());
            }
        }

        /*
         * Order 상태/담당자/배송희망일을 먼저 DB에 반영한 뒤 DeliveryOrderIndex를 생성합니다.
         * 인덱스 생성 중 예외가 발생하면 이 메서드 전체가 롤백됩니다.
         */
        orderRepository.saveAll(orders);
        orderRepository.flush();

        for (Order order : orders) {
            List<OrderFieldChangeCommand> changes = new java.util.ArrayList<>();
            changes.add(OrderFieldChangeCommand.of(
                    "status",
                    "오더 상태",
                    OrderStatus.REQUESTED.getLabel(),
                    OrderStatus.CONFIRMED.getLabel(),
                    OrderWorkArea.PRODUCTION,
                    OrderWorkArea.DISPATCH,
                    OrderWorkArea.DELIVERY
            ));
            changes.add(OrderFieldChangeCommand.of(
                    "assignedDeliveryHandler",
                    "배송담당자",
                    beforeHandlerIdByOrderId.get(order.getId()),
                    order.getAssignedDeliveryHandler() != null ? order.getAssignedDeliveryHandler().getId() : null,
                    OrderWorkArea.DISPATCH,
                    OrderWorkArea.DELIVERY
            ));

            orderChangeAuditService.recordOrderChange(
                    order,
                    OrderChangeSourceArea.MANAGEMENT,
                    actorMemberId,
                    actorUsername,
                    actorDisplayName,
                    "MANAGEMENT_BULK_CONFIRM",
                    "관리자 일괄 컨펌",
                    "/management/nonStandardTaskList/bulk-confirm",
                    changes
            );
        }

        /*
         * 여러 담당자 큐를 한 트랜잭션에서 갱신할 때 잠금 획득 순서가 요청 배열 순서에
         * 따라 달라지지 않도록 담당자 ID, 오더 ID 순으로 정렬합니다.
         */
        orders.stream()
                .sorted(Comparator
                        .comparing((Order order) -> order.getAssignedDeliveryHandler() != null
                                ? order.getAssignedDeliveryHandler().getId()
                                : Long.MAX_VALUE)
                        .thenComparing(Order::getId))
                .forEach(deliveryOrderIndexService::ensureIndex);

        return orders.size();
    }

    private List<Long> normalizeOrderIds(List<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            throw new IllegalArgumentException("컨펌 처리할 오더를 하나 이상 선택해 주세요.");
        }

        List<Long> normalizedOrderIds = orderIds.stream()
                .filter(Objects::nonNull)
                .filter(id -> id > 0)
                .map(Long::valueOf)
                .collect(Collectors.collectingAndThen(
                        Collectors.toCollection(LinkedHashSet::new),
                        List::copyOf
                ));

        if (normalizedOrderIds.isEmpty()) {
            throw new IllegalArgumentException("컨펌 처리할 오더를 하나 이상 선택해 주세요.");
        }

        return normalizedOrderIds;
    }

    private Map<Long, Long> normalizeHandlerAssignments(
            List<Long> normalizedOrderIds,
            Map<Long, Long> deliveryHandlerIdByOrderId
    ) {
        Map<Long, Long> source = deliveryHandlerIdByOrderId == null
                ? Map.of()
                : deliveryHandlerIdByOrderId;

        Set<Long> requestedOrderIdSet = new LinkedHashSet<>(normalizedOrderIds);
        Long unknownOrderId = source.keySet().stream()
                .filter(Objects::nonNull)
                .filter(id -> !requestedOrderIdSet.contains(id))
                .findFirst()
                .orElse(null);

        if (unknownOrderId != null) {
            throw new IllegalArgumentException(
                    unknownOrderId + "번 오더의 담당자 정보가 컨펌 대상과 일치하지 않습니다."
            );
        }

        Map<Long, Long> normalized = new LinkedHashMap<>();

        for (Long orderId : normalizedOrderIds) {
            Long handlerId = source.get(orderId);
            normalized.put(orderId, handlerId != null && handlerId > 0 ? handlerId : null);
        }

        return normalized;
    }

    private void validateRequestedStatus(Long orderId, Order order) {
        OrderStatus currentStatus = order != null ? order.getStatus() : null;

        if (currentStatus == OrderStatus.REQUESTED) {
            return;
        }

        String statusLabel = currentStatus != null ? currentStatus.getLabel() : "상태없음";
        throw new IllegalStateException(
                orderId + "번 오더는 현재 '" + statusLabel
                        + "' 상태입니다. 고객 발주 상태만 일괄 컨펌할 수 있으니 "
                        + "해당 오더 체크를 해제 후 다시 시도해 주세요."
        );
    }

    private Member resolveDeliveryTeamHandler(Long handlerId) {
        Member member = memberRepository.findById(handlerId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "선택한 배송 담당자를 찾을 수 없습니다. handlerId=" + handlerId
                ));

        if (member.getTeam() == null || !DELIVERY_TEAM_NAME.equals(member.getTeam().getName())) {
            throw new IllegalArgumentException("배송팀 직원만 담당자로 지정할 수 있습니다.");
        }

        if (!member.isEnabled()) {
            throw new IllegalArgumentException("비활성화된 직원은 담당자로 지정할 수 없습니다.");
        }

        return member;
    }

    private String resolveDeliveryMethodName(Order order) {
        if (order == null || order.getDeliveryMethod() == null
                || order.getDeliveryMethod().getMethodName() == null
                || order.getDeliveryMethod().getMethodName().isBlank()) {
            return "배송수단";
        }

        return order.getDeliveryMethod().getMethodName().trim();
    }

    public Page<Order> getOrders(
            LocalDateTime start,
            LocalDateTime end,
            Long categoryId,
            Long assignedMemberId,
            OrderStatus status,
            String dateType,
            Pageable pageable
    ) {
        if ("created".equals(dateType)) {
            return orderRepository.findByCreatedDateRange(
                    categoryId,
                    assignedMemberId,
                    status,
                    start,
                    end,
                    pageable
            );
        }
        return orderRepository.findByPreferredDateRange(
                categoryId,
                assignedMemberId,
                status,
                start,
                end,
                pageable
        );
    }

    public List<Order> getAllOrders(
            LocalDateTime start,
            LocalDateTime end,
            Long categoryId,
            Long assignedMemberId,
            OrderStatus status,
            String dateType
    ) {
        if ("created".equals(dateType)) {
            return orderRepository.findAllByCreatedDateRange(
                    categoryId,
                    assignedMemberId,
                    status,
                    start,
                    end
            );
        }
        return orderRepository.findAllByPreferredDateRange(
                categoryId,
                assignedMemberId,
                status,
                start,
                end
        );
    }

    public Page<Order> getOrders(LocalDateTime start, LocalDateTime end, TeamCategory category,
            Member assignedDeliveryHandler, OrderStatus status, String dateType, Pageable pageable) {
        if ("created".equals(dateType)) {
            return orderRepository.findByCreatedDateRange(category, assignedDeliveryHandler, status, start, end,
                    pageable);
        } else {
            return orderRepository.findByPreferredDateRange(category, assignedDeliveryHandler, status, start, end,
                    pageable);
        }
    }

    public Page<Order> getOrders(
            LocalDateTime start,
            LocalDateTime end,
            TeamCategory category,
            OrderStatus status,
            String dateType,
            Pageable pageable
    ) {
        String dt = (dateType == null) ? "created" : dateType.trim().toLowerCase();

        if ("preferred".equals(dt)) {
            return orderRepository.findProductionListByPreferredDate(category, status, start, end, pageable);
        }
        return orderRepository.findProductionListByCreatedDate(category, status, start, end, pageable);
    }

    public List<Order> getAllOrders(
            LocalDateTime start,
            LocalDateTime end,
            TeamCategory category,
            OrderStatus status,
            String dateType,
            Sort sort
    ) {
        String dt = (dateType == null) ? "created" : dateType.trim().toLowerCase();

        if ("preferred".equals(dt)) {
            return orderRepository.findAllProductionListByPreferredDate(category, status, start, end, sort);
        }
        return orderRepository.findAllProductionListByCreatedDate(category, status, start, end, sort);
    }

    public Page<Order> getOrders(LocalDate date, TeamCategory category, OrderStatus status, Pageable pageable) {
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.atTime(LocalTime.MAX);
        return orderRepository.findOrdersByConditions(category, status, start, end, pageable);
    }

    public List<Order> getAllOrders(LocalDate date, TeamCategory category, OrderStatus status) {
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.plusDays(1).atStartOfDay();

        return orderRepository.findAllByConditions(category, status, start, end);
    }

    public List<Order> getAllOrders(LocalDateTime start, LocalDateTime end, TeamCategory category,
            OrderStatus status, String dateType) {
        if ("created".equals(dateType)) {
            return orderRepository.findAllByCreatedDateRange(category, status, start, end);
        } else {
            return orderRepository.findAllByPreferredDateRange(category, status, start, end);
        }
    }

    public List<Order> getAllOrders(LocalDateTime start, LocalDateTime end, TeamCategory category,
            OrderStatus status, Long assignedMemberId, String dateType) {
        if ("created".equals(dateType)) {
            return orderRepository.findAllByCreatedDateRange(category, status, assignedMemberId, start, end);
        } else {
            return orderRepository.findAllByPreferredDateRange(category, status, assignedMemberId, start, end);
        }
    }
}
