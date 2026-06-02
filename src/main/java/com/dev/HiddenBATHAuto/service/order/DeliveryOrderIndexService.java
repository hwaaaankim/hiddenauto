package com.dev.HiddenBATHAuto.service.order;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dev.HiddenBATHAuto.dto.DeliveryOrderIndexUpdateRequest;
import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.task.DeliveryOrderIndex;
import com.dev.HiddenBATHAuto.model.task.Order;
import com.dev.HiddenBATHAuto.model.task.OrderStatus;
import com.dev.HiddenBATHAuto.repository.auth.MemberRepository;
import com.dev.HiddenBATHAuto.repository.order.DeliveryOrderIndexRepository;
import com.dev.HiddenBATHAuto.repository.order.OrderRepository;

import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class DeliveryOrderIndexService {

    private static final String DELIVERY_TEAM_NAME = "배송팀";

    private final DeliveryOrderIndexRepository deliveryOrderIndexRepository;
    private final MemberRepository memberRepository;
    private final OrderRepository orderRepository;

    /**
     * 규칙:
     * - 레코드 존재 + (담당자/날짜 동일) => 아무 것도 안 함.
     * - 레코드 미존재 + (담당자/날짜 지정) => 새로 생성 (해당 큐 max+1).
     * - 레코드 존재 + (담당자/날짜 변경) => 해당 큐 끝(max+1)으로 이동(갱신).
     * - 담당자 또는 날짜가 null => 기존 레코드 있으면 삭제.
     */
    public void ensureIndex(Order order) {
        DeliveryOrderIndex existing = deliveryOrderIndexRepository.findByOrder(order).orElse(null);

        Member handler = order.getAssignedDeliveryHandler();
        LocalDate date = order.getPreferredDeliveryDate() == null ? null : order.getPreferredDeliveryDate().toLocalDate();

        if (handler == null || date == null) {
            if (existing != null) {
                deliveryOrderIndexRepository.delete(existing);
            }
            return;
        }

        if (existing != null
                && existing.getDeliveryHandler() != null
                && handler.getId().equals(existing.getDeliveryHandler().getId())
                && date.equals(existing.getDeliveryDate())) {
            return;
        }

        Integer maxIndex = deliveryOrderIndexRepository.findMaxIndexByHandlerAndDate(handler.getId(), date);
        int next = (maxIndex == null ? 1 : (maxIndex + 1));

        if (existing == null) {
            DeliveryOrderIndex created = new DeliveryOrderIndex();
            created.setOrder(order);
            created.setDeliveryHandler(handler);
            created.setDeliveryDate(date);
            created.setOrderIndex(next);
            deliveryOrderIndexRepository.save(created);
        } else {
            existing.setDeliveryHandler(handler);
            existing.setDeliveryDate(date);
            existing.setOrderIndex(next);
            deliveryOrderIndexRepository.save(existing);
        }
    }

    public void updateIndexes(DeliveryOrderIndexUpdateRequest request) {
        Member handler = memberRepository.findById(request.getDeliveryHandlerId())
                .orElseThrow(() -> new IllegalArgumentException("배송 담당자 없음"));

        LocalDate date = LocalDate.parse(request.getDeliveryDate());

        for (DeliveryOrderIndexUpdateRequest.OrderIndexDto dto : request.getOrderList()) {
            DeliveryOrderIndex index = deliveryOrderIndexRepository
                    .findByDeliveryHandlerIdAndDeliveryDateAndOrderId(handler.getId(), date, dto.getOrderId())
                    .orElseThrow(() -> new IllegalStateException("해당 주문 인덱스 없음"));
            index.setOrderIndex(dto.getOrderIndex());
        }
    }

    @Transactional
    public void updateIndexesWithDoneGuard(DeliveryOrderIndexUpdateRequest request) {
        if (request == null) throw new IllegalArgumentException("요청이 비어있습니다.");
        if (request.getDeliveryHandlerId() == null) throw new IllegalArgumentException("담당자 ID가 없습니다.");
        if (request.getDeliveryDate() == null || request.getDeliveryDate().isBlank()) {
            throw new IllegalArgumentException("날짜가 없습니다.");
        }
        if (request.getOrderList() == null) throw new IllegalArgumentException("orderList가 없습니다.");

        Member handler = memberRepository.findById(request.getDeliveryHandlerId())
                .orElseThrow(() -> new IllegalArgumentException("배송 담당자 없음"));

        LocalDate date;
        try {
            date = LocalDate.parse(request.getDeliveryDate());
        } catch (Exception e) {
            throw new IllegalArgumentException("날짜 형식이 올바르지 않습니다. yyyy-MM-dd");
        }

        List<DeliveryOrderIndex> current = deliveryOrderIndexRepository
                .findAllByHandlerAndDateForTaskGrouping(handler.getId(), date)
                .stream()
                .filter(x -> x.getOrder() != null)
                .filter(x -> isDirectDeliveryOrder(x.getOrder()))
                .collect(Collectors.toList());

        List<Long> currentDoneOrderIds = current.stream()
                .filter(x -> x.getOrder().getStatus() == OrderStatus.DELIVERY_DONE)
                .map(x -> x.getOrder().getId())
                .collect(Collectors.toList());

        Set<Long> doneSet = new HashSet<>(currentDoneOrderIds);

        List<Long> requestedDoneOrderIds = request.getOrderList().stream()
                .map(DeliveryOrderIndexUpdateRequest.OrderIndexDto::getOrderId)
                .filter(Objects::nonNull)
                .filter(doneSet::contains)
                .collect(Collectors.toList());

        if (!currentDoneOrderIds.equals(requestedDoneOrderIds)) {
            throw new IllegalStateException("배송완료 항목의 순서는 변경할 수 없습니다.");
        }

        List<Long> reqIds = request.getOrderList().stream()
                .map(DeliveryOrderIndexUpdateRequest.OrderIndexDto::getOrderId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        long distinctCount = reqIds.stream().distinct().count();
        if (distinctCount != reqIds.size()) {
            throw new IllegalArgumentException("중복된 orderId가 존재합니다.");
        }

        Map<Long, DeliveryOrderIndex> indexByOrderId = current.stream()
                .collect(Collectors.toMap(
                        x -> x.getOrder().getId(),
                        x -> x,
                        (a, b) -> a
                ));

        for (Long oid : reqIds) {
            if (!indexByOrderId.containsKey(oid)) {
                throw new IllegalArgumentException("해당 주문 인덱스 없음: orderId=" + oid);
            }
        }

        int newIndex = 1;
        for (DeliveryOrderIndexUpdateRequest.OrderIndexDto dto : request.getOrderList()) {
            if (dto == null || dto.getOrderId() == null) continue;

            DeliveryOrderIndex idx = indexByOrderId.get(dto.getOrderId());
            idx.setOrderIndex(newIndex++);
        }
    }

    @Transactional(readOnly = true)
    public List<Long> reorderPendingOrderIdsByTask(Long handlerId, LocalDate deliveryDate, List<Long> pendingOrderIds) {
        List<DeliveryOrderIndex> all = getDirectDeliveryIndexes(handlerId, deliveryDate, null);

        Map<Long, Long> orderIdToTaskId = new HashMap<>();
        Set<Long> validPendingOrderIds = new HashSet<>();

        for (DeliveryOrderIndex doi : all) {
            if (doi == null || doi.getOrder() == null) continue;

            Long orderId = doi.getOrder().getId();
            if (orderId == null) continue;

            if (doi.getOrder().getStatus() == OrderStatus.DELIVERY_DONE) {
                continue;
            }

            Long taskId = (doi.getOrder().getTask() != null) ? doi.getOrder().getTask().getId() : 0L;
            orderIdToTaskId.put(orderId, taskId);
            validPendingOrderIds.add(orderId);
        }

        List<Long> invalid = pendingOrderIds.stream()
                .filter(id -> id == null || !validPendingOrderIds.contains(id))
                .collect(Collectors.toList());

        if (!invalid.isEmpty()) {
            throw new IllegalArgumentException("업체별정렬 불가: pending 대상이 아닌 orderId 포함 - " + invalid);
        }

        Map<Long, List<Long>> groups = new LinkedHashMap<>();
        for (Long orderId : pendingOrderIds) {
            Long taskId = orderIdToTaskId.getOrDefault(orderId, 0L);
            groups.computeIfAbsent(taskId, k -> new ArrayList<>()).add(orderId);
        }

        List<Long> reordered = new ArrayList<>(pendingOrderIds.size());
        for (List<Long> g : groups.values()) {
            reordered.addAll(g);
        }

        return reordered;
    }

    public void removeIndex(Order order) {
        if (order == null || order.getId() == null) {
            return;
        }

        deliveryOrderIndexRepository.findByOrder(order)
                .ifPresent(deliveryOrderIndexRepository::delete);
    }

    /**
     * 배송리스트 조회용입니다.
     * 기존 deliveryOrderIndex에 잘못 남은 비직배송 건이 있더라도 화면에서 한 번 더 제외합니다.
     */
    @Transactional(readOnly = true)
    public List<DeliveryOrderIndex> getDirectDeliveryIndexes(
            Long handlerId,
            LocalDate deliveryDate,
            List<OrderStatus> statuses
    ) {
        List<OrderStatus> safeStatuses = statuses == null || statuses.isEmpty()
                ? List.of(OrderStatus.CONFIRMED, OrderStatus.PRODUCTION_DONE, OrderStatus.DISPATCH_DONE, OrderStatus.DELIVERY_DONE)
                : statuses;

        return deliveryOrderIndexRepository.findListByHandlerAndDateAndStatusIn(
                        handlerId,
                        deliveryDate,
                        safeStatuses
                )
                .stream()
                .filter(x -> x != null && x.getOrder() != null)
                .filter(x -> isDirectDeliveryOrder(x.getOrder()))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<Member> getActiveDeliveryTeamMembers() {
        return memberRepository.findByTeam_NameAndEnabledTrueOrderByNameAsc(DELIVERY_TEAM_NAME);
    }

    @Transactional(readOnly = true)
    public Order getSingleCompletableOrder(Member loginMember, Long orderId) {
        validateDeliveryTeamMember(loginMember);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("해당 주문이 존재하지 않습니다."));

        DeliveryOrderIndex index = deliveryOrderIndexRepository.findByOrder(order)
                .orElseThrow(() -> new IllegalStateException("배송순서 정보가 없습니다."));

        validateCurrentHandler(loginMember, index);
        validateDirectDelivery(order);
        validateCompletableStatus(order);

        return order;
    }

    /**
     * 동일 업체 + 동일 주소 + 동일 배송일 기준 배송완료 대상 조회
     *
     * 기준 배송일은 서버의 오늘 날짜가 아니라,
     * 사용자가 배송완료를 누른 기준 주문의 deliveryOrderIndex.deliveryDate 입니다.
     */
    @Transactional(readOnly = true)
    public List<Order> findSameCompanySameAddressSameDeliveryDateCompletableOrders(
            Member loginMember,
            Long sourceOrderId
    ) {
        validateDeliveryTeamMember(loginMember);

        if (sourceOrderId == null) {
            throw new IllegalArgumentException("주문 ID가 없습니다.");
        }

        Order sourceOrder = orderRepository.findById(sourceOrderId)
                .orElseThrow(() -> new IllegalArgumentException("해당 주문이 존재하지 않습니다."));

        DeliveryOrderIndex sourceIndex = deliveryOrderIndexRepository.findByOrder(sourceOrder)
                .orElseThrow(() -> new IllegalStateException("배송순서 정보가 없습니다."));

        validateCurrentHandler(loginMember, sourceIndex);
        validateDirectDelivery(sourceOrder);
        validateCompletableStatus(sourceOrder);

        LocalDate sourceDeliveryDate = sourceIndex.getDeliveryDate();

        if (sourceDeliveryDate == null) {
            throw new IllegalStateException("선택 주문의 배송일 정보가 없습니다.");
        }

        Long sourceCompanyId = resolveCompanyId(sourceOrder);

        if (sourceCompanyId == null) {
            throw new IllegalStateException("선택 주문의 업체 정보를 확인할 수 없습니다.");
        }

        String sourceAddressKey = buildAddressKey(sourceOrder);

        if (sourceAddressKey.isBlank()) {
            throw new IllegalStateException("선택 주문의 주소 정보를 확인할 수 없습니다.");
        }

        return deliveryOrderIndexRepository.findAllByHandlerAndDateForTaskGrouping(
                        loginMember.getId(),
                        sourceDeliveryDate
                )
                .stream()
                .filter(x -> x != null && x.getOrder() != null)
                .filter(x -> isDirectDeliveryOrder(x.getOrder()))
                .filter(x -> isCompletableByDeliveryTeam(x.getOrder()))
                .filter(x -> sourceCompanyId.equals(resolveCompanyId(x.getOrder())))
                .filter(x -> sourceAddressKey.equals(buildAddressKey(x.getOrder())))
                .sorted(Comparator.comparingInt(DeliveryOrderIndex::getOrderIndex))
                .map(DeliveryOrderIndex::getOrder)
                .collect(Collectors.toList());
    }

    private Long resolveCompanyId(Order order) {
        if (order == null
                || order.getTask() == null
                || order.getTask().getRequestedBy() == null
                || order.getTask().getRequestedBy().getCompany() == null) {
            return null;
        }

        return order.getTask().getRequestedBy().getCompany().getId();
    }

    @Transactional
    public void changeDeliveryHandler(Member loginMember, Long orderId, Long newHandlerId) {
        validateDeliveryTeamMember(loginMember);

        if (newHandlerId == null) {
            throw new IllegalArgumentException("변경할 담당자를 선택해주세요.");
        }

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("해당 주문이 존재하지 않습니다."));

        DeliveryOrderIndex index = deliveryOrderIndexRepository.findByOrder(order)
                .orElseThrow(() -> new IllegalStateException("배송순서 정보가 없습니다."));

        validateCurrentHandler(loginMember, index);
        validateDirectDelivery(order);

        if (order.getStatus() == OrderStatus.DELIVERY_DONE) {
            throw new IllegalStateException("배송완료 건은 담당자를 변경할 수 없습니다.");
        }

        Member newHandler = memberRepository.findById(newHandlerId)
                .orElseThrow(() -> new IllegalArgumentException("변경할 배송 담당자가 존재하지 않습니다."));

        if (newHandler.getTeam() == null || !DELIVERY_TEAM_NAME.equals(newHandler.getTeam().getName())) {
            throw new IllegalArgumentException("배송팀 직원만 담당자로 지정할 수 있습니다.");
        }

        if (!newHandler.isEnabled()) {
            throw new IllegalArgumentException("비활성화된 직원은 담당자로 지정할 수 없습니다.");
        }

        if (newHandler.getId() != null && newHandler.getId().equals(loginMember.getId())) {
            return;
        }

        LocalDate deliveryDate = index.getDeliveryDate();
        if (deliveryDate == null) {
            throw new IllegalStateException("배송일 정보가 없습니다.");
        }

        Integer maxIndex = deliveryOrderIndexRepository.findMaxIndexByHandlerAndDate(newHandler.getId(), deliveryDate);
        int nextIndex = (maxIndex == null ? 1 : maxIndex + 1);

        order.setAssignedDeliveryHandler(newHandler);
        order.setUpdatedAt(java.time.LocalDateTime.now());

        index.setDeliveryHandler(newHandler);
        index.setDeliveryDate(deliveryDate);
        index.setOrderIndex(nextIndex);
    }

    public boolean isDirectDeliveryOrder(Order order) {
        if (order == null) {
            return false;
        }

        /*
         * deliveryOrderIndex 자체가 직배송 건을 기준으로 관리되고 있으므로,
         * deliveryMethod가 비어 있는 과거 데이터 때문에 화면 전체가 비는 것을 막습니다.
         */
        if (order.getDeliveryMethod() == null) {
            return true;
        }

        String methodName = safeText(order.getDeliveryMethod().getMethodName());

        /*
         * 배송수단명이 비어 있는 데이터도 deliveryOrderIndex 기준을 우선 신뢰합니다.
         */
        if (methodName.isBlank()) {
            return true;
        }

        String normalized = methodName.replaceAll("\\s+", "");

        return normalized.contains("직배송");
    }

    public boolean isCompletableByDeliveryTeam(Order order) {
        if (order == null || order.getStatus() == null) {
            return false;
        }

        return order.getStatus() == OrderStatus.PRODUCTION_DONE
                || order.getStatus() == OrderStatus.DISPATCH_DONE;
    }

    private void validateDeliveryTeamMember(Member member) {
        if (member == null || member.getTeam() == null || !DELIVERY_TEAM_NAME.equals(member.getTeam().getName())) {
            throw new AccessDeniedException("배송팀만 접근할 수 있습니다.");
        }
    }

    private void validateCurrentHandler(Member loginMember, DeliveryOrderIndex index) {
        if (index == null || index.getDeliveryHandler() == null || index.getDeliveryHandler().getId() == null) {
            throw new AccessDeniedException("배송 담당자 정보를 확인할 수 없습니다.");
        }

        if (!index.getDeliveryHandler().getId().equals(loginMember.getId())) {
            throw new AccessDeniedException("현재 로그인한 배송 담당자의 주문만 처리할 수 있습니다.");
        }
    }

    private void validateDirectDelivery(Order order) {
        if (!isDirectDeliveryOrder(order)) {
            throw new IllegalStateException("직배송 주문만 배송팀에서 처리할 수 있습니다.");
        }
    }

    private void validateCompletableStatus(Order order) {
        if (!isCompletableByDeliveryTeam(order)) {
            throw new IllegalStateException("생산완료 또는 출고완료 상태의 주문만 배송완료 처리할 수 있습니다.");
        }
    }

    private String buildAddressKey(Order order) {
        if (order == null) {
            return "";
        }

        return String.join("|",
                normalizeKey(order.getZipCode()),
                normalizeKey(order.getDoName()),
                normalizeKey(order.getSiName()),
                normalizeKey(order.getGuName()),
                normalizeKey(order.getRoadAddress()),
                normalizeKey(order.getDetailAddress())
        );
    }

    private String normalizeKey(String value) {
        return safeText(value).toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }
}
