package com.dev.HiddenBATHAuto.service.order;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
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
import com.dev.HiddenBATHAuto.utils.DeliveryAddressNormalizationUtil;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class DeliveryOrderIndexService {

    private static final String DELIVERY_TEAM_NAME = "배송팀";

    private static final String DIRECT_DELIVERY_METHOD_NAME = "직배송";
    private static final String FREIGHT_DELIVERY_METHOD_NAME = "화물";
    private static final String SITE_DELIVERY_METHOD_NAME = "현장배송";

    /*
     * order_index range 분리 기준입니다.
     * - 1 ~ 99,998             : 직배송/현장배송 + 승인완료/생산완료/출고완료
     * - 99,999 ~ 999,999       : 화물. 첫 건은 99,999이며 같은 담당자/날짜의 다음 화물은 +1
     * - 1,000,000 ~ 1,999,999  : 직배송/현장배송 + 배송완료
     * - 2,000,000 이상         : 과거 데이터 등 기타 표시 전용 영역
     *
     * tb_delivery_order_index에는 (담당자, 날짜, 순번) 유니크 제약이 있으므로
     * 여러 화물을 모두 99,999로 저장할 수 없습니다. 따라서 99,999를 화물 구간의
     * 시작값으로 사용하고 같은 구간 안에서 순차 증가시킵니다.
     *
     * 저장/정규화 시에는 유니크 충돌을 피하기 위해 임시 음수 index로 한 번 밀어낸 뒤
     * 구간별로 재부여합니다.
     */
    private static final int ACTIONABLE_PENDING_INDEX_START = 1;
    private static final int FREIGHT_INDEX_START = 99_999;
    private static final int ACTIONABLE_DONE_INDEX_START = 1_000_000;
    private static final int OTHER_INDEX_START = 2_000_000;
    private static final int TEMP_REINDEX_START = -1_000_000_000;

    private final EntityManager entityManager;
    private final DeliveryOrderIndexRepository deliveryOrderIndexRepository;
    private final MemberRepository memberRepository;
    private final OrderRepository orderRepository;

    /**
     * 관리자 오더 수정 저장 시 배송순서 인덱스를 동기화합니다.
     *
     * 처리 기준:
     * - 취소 또는 담당자 없음 => 기존 DeliveryOrderIndex 삭제
     * - 담당자가 있는데 배송희망일이 없음 => 잘못된 상태이므로 저장 차단
     * - 직배송/화물/현장배송 => 담당자 필수
     * - 담당자/날짜 변경 => 기존 row 삭제 후 새 담당자/날짜의 배송수단별 section 끝에 추가
     * - 담당자/날짜 동일 + 배송수단/상태만 변경 => section range가 달라지면 재배치
     * - 담당자 선택이 가능한 기타 배송수단 => 담당자가 지정된 경우 OTHER section으로 유지
     *
     * section 규칙:
     * - 직배송/현장배송 진행 건: 1부터 시작
     * - 화물: 99,999부터 시작
     * - 직배송/현장배송 배송완료: 1,000,000부터 시작
     * - 기타: 2,000,000부터 시작
     */
    public void ensureIndex(Order order) {
        if (order == null || order.getId() == null) {
            return;
        }

        DeliveryOrderIndex existing = deliveryOrderIndexRepository.findByOrder(order).orElse(null);

        Member handler = order.getAssignedDeliveryHandler();
        LocalDate deliveryDate = order.getPreferredDeliveryDate() == null
                ? null
                : order.getPreferredDeliveryDate().toLocalDate();

        if (order.getStatus() == OrderStatus.CANCELED) {
            deleteExistingIndex(existing);
            return;
        }

        if (isDeliveryHandlerRequiredMethod(order) && handler == null) {
            String methodName = order.getDeliveryMethod() != null
                    ? safeText(order.getDeliveryMethod().getMethodName())
                    : "배송수단";
            throw new IllegalArgumentException(methodName + " 선택 시 배송팀 담당자는 필수입니다.");
        }

        if (handler == null) {
            deleteExistingIndex(existing);
            return;
        }

        if (deliveryDate == null) {
            throw new IllegalArgumentException("배송팀 담당자를 지정하는 경우 배송희망일은 필수입니다.");
        }

        validateDeliveryTeamHandler(handler);

        DeliveryListSection targetSection = resolveDeliveryListSection(order);

        if (existing != null
                && existing.getDeliveryHandler() != null
                && Objects.equals(existing.getDeliveryHandler().getId(), handler.getId())
                && Objects.equals(existing.getDeliveryDate(), deliveryDate)) {

            existing.setOrder(order);
            existing.setDeliveryHandler(handler);
            existing.setDeliveryDate(deliveryDate);

            if (isIndexInSectionRange(existing.getOrderIndex(), targetSection)) {
                return;
            }

            /*
             * 같은 담당자/같은 날짜라도 배송수단 또는 상태 변경으로 section이 달라진 경우
             * 현재 row를 삭제한 뒤 해당 section의 마지막 순번으로 다시 등록합니다.
             */
            replaceIndexAtQueueEnd(order, existing, handler, deliveryDate, targetSection);
            return;
        }

        /*
         * 담당자 또는 배송일이 달라진 경우 기존 row를 먼저 삭제한 뒤 새 큐에 등록합니다.
         * replaceIndexAtQueueEnd 내부에서 기존 큐의 빈 순번도 정규화합니다.
         */
        replaceIndexAtQueueEnd(order, existing, handler, deliveryDate, targetSection);
    }

    /**
     * 배송완료 처리처럼 OrderService에서 상태가 바뀐 뒤 index section만 다시 맞춰야 하는 경우 사용합니다.
     */
    public void reclassifyIndex(Long orderId) {
        if (orderId == null) {
            return;
        }

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("해당 주문이 존재하지 않습니다. orderId=" + orderId));

        ensureIndex(order);
    }

    /**
     * 동일주소 일괄완료 후 여러 주문의 index section을 한 트랜잭션에서 재분류합니다.
     */
    public void reclassifyIndexes(List<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return;
        }

        orderIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .forEach(this::reclassifyIndex);
    }

    /**
     * 기존 호출부 호환용입니다.
     * 가능하면 updateIndexesWithDoneGuard를 사용해 주세요.
     */
    public void updateIndexes(DeliveryOrderIndexUpdateRequest request) {
        updateIndexesWithDoneGuard(request);
    }

    /**
     * 배송팀 순서 저장.
     * 프론트에서 보내는 orderList는 "직배송/현장배송 + 생산완료/출고완료" 영역만 허용합니다.
     * 배송완료/기타 영역은 request에 섞여 들어오면 예외 처리합니다.
     */
    @Transactional
    public void updateIndexesWithDoneGuard(DeliveryOrderIndexUpdateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("요청이 비어있습니다.");
        }
        if (request.getDeliveryHandlerId() == null) {
            throw new IllegalArgumentException("담당자 ID가 없습니다.");
        }
        if (request.getDeliveryDate() == null || request.getDeliveryDate().isBlank()) {
            throw new IllegalArgumentException("날짜가 없습니다.");
        }
        if (request.getOrderList() == null) {
            throw new IllegalArgumentException("orderList가 없습니다.");
        }

        Member handler = memberRepository.findById(request.getDeliveryHandlerId())
                .orElseThrow(() -> new IllegalArgumentException("배송 담당자 없음"));

        validateDeliveryTeamHandler(handler);
        lockDeliveryHandlerQueues(Set.of(handler.getId()));

        LocalDate date;
        try {
            date = LocalDate.parse(request.getDeliveryDate());
        } catch (Exception e) {
            throw new IllegalArgumentException("날짜 형식이 올바르지 않습니다. yyyy-MM-dd");
        }

        List<DeliveryOrderIndex> current = getVisibleIndexesForHandlerAndDate(handler.getId(), date);

        Map<Long, DeliveryOrderIndex> activeIndexByOrderId = current.stream()
                .filter(x -> isActionablePendingDeliveryOrder(x.getOrder()))
                .collect(Collectors.toMap(
                        x -> x.getOrder().getId(),
                        x -> x,
                        (a, b) -> a,
                        LinkedHashMap::new
                ));

        List<Long> requestedOrderIds = request.getOrderList().stream()
                .map(DeliveryOrderIndexUpdateRequest.OrderIndexDto::getOrderId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        long distinctCount = requestedOrderIds.stream().distinct().count();
        if (distinctCount != requestedOrderIds.size()) {
            throw new IllegalArgumentException("중복된 orderId가 존재합니다.");
        }

        for (Long orderId : requestedOrderIds) {
            if (!activeIndexByOrderId.containsKey(orderId)) {
                throw new IllegalArgumentException("순서 변경은 직배송/현장배송의 생산완료 또는 출고완료 건만 가능합니다. orderId=" + orderId);
            }
        }

        Set<Long> requestedSet = new HashSet<>(requestedOrderIds);

        List<DeliveryOrderIndex> activeRows = new ArrayList<>();

        for (Long orderId : requestedOrderIds) {
            DeliveryOrderIndex row = activeIndexByOrderId.get(orderId);
            if (row != null) {
                activeRows.add(row);
            }
        }

        /*
         * 상태 필터 등으로 화면에 보이지 않은 active row가 있을 수 있으므로
         * 누락된 row는 기존 순서를 유지한 채 요청 row 뒤에 붙입니다.
         */
        current.stream()
                .filter(x -> isActionablePendingDeliveryOrder(x.getOrder()))
                .filter(x -> x.getOrder() != null && x.getOrder().getId() != null)
                .filter(x -> !requestedSet.contains(x.getOrder().getId()))
                .sorted(indexComparator())
                .forEach(activeRows::add);

        /*
         * 승인완료 직배송/현장배송은 배송 큐에는 포함하지만 배송팀 순서조작 대상은 아닙니다.
         * 따라서 요청된 작업 가능 주문 뒤에 기존 순서를 유지하여 붙입니다.
         */
        current.stream()
                .filter(x -> isQueuedDirectOrSiteOrder(x.getOrder()))
                .filter(x -> !isActionablePendingDeliveryOrder(x.getOrder()))
                .sorted(indexComparator())
                .forEach(activeRows::add);

        List<DeliveryOrderIndex> freightRows = current.stream()
                .filter(x -> isFreightDeliveryListOrder(x.getOrder()))
                .sorted(indexComparator())
                .collect(Collectors.toList());

        List<DeliveryOrderIndex> doneRows = current.stream()
                .filter(x -> isActionableDoneDeliveryOrder(x.getOrder()))
                .sorted(indexComparator())
                .collect(Collectors.toList());

        List<DeliveryOrderIndex> otherRows = current.stream()
                .filter(x -> isOtherDeliveryListOrder(x.getOrder()))
                .sorted(indexComparator())
                .collect(Collectors.toList());

        rewriteIndexesBySection(current, activeRows, freightRows, doneRows, otherRows);
    }

    /**
     * 업체별정렬은 직배송/현장배송 + 생산완료/출고완료 영역만 가능합니다.
     * DB 저장은 순서 저장 API에서 수행합니다.
     */
    @Transactional(readOnly = true)
    public List<Long> reorderPendingOrderIdsByTask(Long handlerId, LocalDate deliveryDate, List<Long> pendingOrderIds) {
        List<DeliveryOrderIndex> all = getDirectDeliveryIndexes(handlerId, deliveryDate, null).stream()
                .filter(x -> isActionablePendingDeliveryOrder(x.getOrder()))
                .collect(Collectors.toList());

        Map<Long, Long> orderIdToTaskId = new HashMap<>();
        Set<Long> validPendingOrderIds = new HashSet<>();

        for (DeliveryOrderIndex deliveryOrderIndex : all) {
            if (deliveryOrderIndex == null || deliveryOrderIndex.getOrder() == null) {
                continue;
            }

            Long orderId = deliveryOrderIndex.getOrder().getId();

            if (orderId == null) {
                continue;
            }

            Long taskId = deliveryOrderIndex.getOrder().getTask() != null
                    ? deliveryOrderIndex.getOrder().getTask().getId()
                    : 0L;

            orderIdToTaskId.put(orderId, taskId);
            validPendingOrderIds.add(orderId);
        }

        List<Long> invalid = pendingOrderIds.stream()
                .filter(id -> id == null || !validPendingOrderIds.contains(id))
                .collect(Collectors.toList());

        if (!invalid.isEmpty()) {
            throw new IllegalArgumentException("업체별정렬은 직배송/현장배송의 생산완료 또는 출고완료 건만 가능합니다. invalid=" + invalid);
        }

        Map<Long, List<Long>> groups = new LinkedHashMap<>();

        for (Long orderId : pendingOrderIds) {
            Long taskId = orderIdToTaskId.getOrDefault(orderId, 0L);
            groups.computeIfAbsent(taskId, k -> new ArrayList<>()).add(orderId);
        }

        List<Long> reordered = new ArrayList<>(pendingOrderIds.size());

        for (List<Long> group : groups.values()) {
            reordered.addAll(group);
        }

        return reordered;
    }

    public void removeIndex(Order order) {
        if (order == null || order.getId() == null) {
            return;
        }

        deliveryOrderIndexRepository.findByOrder(order)
                .ifPresent(this::deleteExistingIndex);
    }

    /**
     * 기존 호출부 호환을 위해 메서드명은 유지합니다.
     * 현재는 직배송만 반환하지 않고, 담당자가 지정되어 DeliveryOrderIndex에 올라간 row를
     * 배송팀 화면 표시 순서(직/현 작업 가능 -> 화물 -> 배송완료 -> 기타)로 반환합니다.
     */
    @Transactional(readOnly = true)
    public List<DeliveryOrderIndex> getDirectDeliveryIndexes(
            Long handlerId,
            LocalDate deliveryDate,
            List<OrderStatus> statuses
    ) {
        List<OrderStatus> safeStatuses = statuses == null || statuses.isEmpty()
                ? List.of(
                        OrderStatus.CONFIRMED,
                        OrderStatus.PRODUCTION_DONE,
                        OrderStatus.DISPATCH_DONE,
                        OrderStatus.DELIVERY_DONE
                )
                : statuses;

        return deliveryOrderIndexRepository.findListByHandlerAndDateAndStatusIn(
                        handlerId,
                        deliveryDate,
                        safeStatuses
                )
                .stream()
                .filter(this::isVisibleDeliveryIndex)
                .sorted(deliveryListComparator())
                .collect(Collectors.toList());
    }

    /**
     * 과거 데이터 또는 배송수단만 변경된 같은 담당자/같은 날짜 row의 order_index range를 정규화합니다.
     */
    @Transactional
    public void normalizeIndexesForHandlerDate(Long handlerId, LocalDate deliveryDate) {
        if (handlerId == null || deliveryDate == null) {
            return;
        }

        /*
         * 과거 데이터의 담당자가 비활성화되었더라도 인덱스 정리 자체는 가능해야 합니다.
         * 따라서 큐 정규화에서는 담당자 활성 상태를 검증하지 않고 row lock만 획득합니다.
         */
        lockQueueOwners(Set.of(handlerId));

        List<DeliveryOrderIndex> current = getVisibleIndexesForHandlerAndDate(handlerId, deliveryDate);

        if (current.isEmpty()) {
            return;
        }

        List<DeliveryOrderIndex> activeRows = current.stream()
                .filter(x -> isQueuedDirectOrSiteOrder(x.getOrder()))
                .sorted(indexComparator())
                .collect(Collectors.toList());

        List<DeliveryOrderIndex> freightRows = current.stream()
                .filter(x -> isFreightDeliveryListOrder(x.getOrder()))
                .sorted(indexComparator())
                .collect(Collectors.toList());

        List<DeliveryOrderIndex> doneRows = current.stream()
                .filter(x -> isActionableDoneDeliveryOrder(x.getOrder()))
                .sorted(indexComparator())
                .collect(Collectors.toList());

        List<DeliveryOrderIndex> otherRows = current.stream()
                .filter(x -> isOtherDeliveryListOrder(x.getOrder()))
                .sorted(indexComparator())
                .collect(Collectors.toList());

        /*
         * 단순히 각 row가 section 범위 안에 있는지만 확인하면, 삭제 후 1, 3처럼
         * 중간 순번이 비어 있어도 정규화된 것으로 오판합니다.
         * 각 section의 시작값부터 정확히 연속되는지까지 검사합니다.
         */
        boolean alreadyNormalized = hasSequentialIndexes(activeRows, ACTIONABLE_PENDING_INDEX_START)
                && hasSequentialIndexes(freightRows, FREIGHT_INDEX_START)
                && hasSequentialIndexes(doneRows, ACTIONABLE_DONE_INDEX_START)
                && hasSequentialIndexes(otherRows, OTHER_INDEX_START);

        if (alreadyNormalized) {
            return;
        }

        rewriteIndexesBySection(current, activeRows, freightRows, doneRows, otherRows);
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
        validateDeliveryIndexManagedOrder(order);
        validateCompletableStatus(order);

        return order;
    }

    /**
     * 동일 업체 + 동일 주소 + 동일 배송수단 + 동일 배송일 기준 배송완료 대상 조회.
     * 배송완료 대상은 직배송/현장배송 + 생산완료(PRODUCTION_DONE)만 허용합니다.
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
        validateDeliveryIndexManagedOrder(sourceOrder);
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

        String sourceDeliveryMethodKey = resolveDeliveryMethodKey(sourceOrder);

        if (sourceDeliveryMethodKey.isBlank()) {
            throw new IllegalStateException("선택 주문의 배송수단 정보를 확인할 수 없습니다.");
        }

        return deliveryOrderIndexRepository.findAllByHandlerAndDateForTaskGrouping(
                        loginMember.getId(),
                        sourceDeliveryDate
                )
                .stream()
                .filter(this::isVisibleDeliveryIndex)
                .filter(x -> isCompletableByDeliveryTeam(x.getOrder()))
                .filter(x -> sourceCompanyId.equals(resolveCompanyId(x.getOrder())))
                .filter(x -> sourceAddressKey.equals(buildAddressKey(x.getOrder())))
                .filter(x -> sourceDeliveryMethodKey.equals(resolveDeliveryMethodKey(x.getOrder())))
                .sorted(Comparator.comparingInt(DeliveryOrderIndex::getOrderIndex))
                .map(DeliveryOrderIndex::getOrder)
                .collect(Collectors.toList());
    }

    /**
     * 배송팀 담당자 변경은 직배송/현장배송 + 생산완료/출고완료 건만 가능합니다.
     * 택배/화물/방문/배송완료/승인완료 등은 상세 확인만 가능합니다.
     *
     * 단건 호출부 보호용입니다. 실제 처리는 일괄 메서드와 동일한 로직을 사용합니다.
     */
    @Transactional
    public void changeDeliveryHandler(Member loginMember, Long orderId, Long newHandlerId) {
        changeDeliveryHandlers(loginMember, List.of(orderId), newHandlerId);
    }

    /**
     * 체크박스로 선택된 여러 주문의 배송 담당자를 일괄 변경합니다.
     *
     * 처리 방식:
     * 1. 현재 로그인 배송 담당자의 DeliveryOrderIndex row인지 검증합니다.
     * 2. 직배송/현장배송 + 생산완료/출고완료 상태인지 검증합니다.
     * 3. 기존 DeliveryOrderIndex row를 삭제합니다.
     * 4. 선택한 새 담당자의 같은 배송일 queue 끝에 order_index 최대값 + 1로 순차 추가합니다.
     * 5. source/target 담당자의 해당 날짜 index를 정규화합니다.
     */
    @Transactional
    public List<Long> changeDeliveryHandlers(Member loginMember, List<Long> orderIds, Long newHandlerId) {
        validateDeliveryTeamMember(loginMember);

        if (newHandlerId == null) {
            throw new IllegalArgumentException("변경할 담당자를 선택해주세요.");
        }

        if (orderIds == null || orderIds.isEmpty()) {
            throw new IllegalArgumentException("담당자를 변경할 주문을 1개 이상 선택해주세요.");
        }

        List<Long> distinctOrderIds = orderIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        if (distinctOrderIds.isEmpty()) {
            throw new IllegalArgumentException("담당자를 변경할 주문을 1개 이상 선택해주세요.");
        }

        Member newHandler = memberRepository.findById(newHandlerId)
                .orElseThrow(() -> new IllegalArgumentException("변경할 배송 담당자가 존재하지 않습니다."));

        validateDeliveryTeamHandler(newHandler);

        Set<Long> handlerIdsToLock = new HashSet<>();
        handlerIdsToLock.add(loginMember.getId());
        handlerIdsToLock.add(newHandler.getId());
        lockDeliveryHandlerQueues(handlerIdsToLock);

        List<Long> changedOrderIds = new ArrayList<>();
        Set<LocalDate> affectedDates = new HashSet<>();

        for (Long orderId : distinctOrderIds) {
            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new IllegalArgumentException("해당 주문이 존재하지 않습니다. orderId=" + orderId));

            DeliveryOrderIndex existingIndex = deliveryOrderIndexRepository.findByOrder(order)
                    .orElseThrow(() -> new IllegalStateException("배송순서 정보가 없습니다. orderId=" + orderId));

            validateCurrentHandler(loginMember, existingIndex);
            validateDeliveryIndexManagedOrder(order);

            if (!isActionablePendingDeliveryOrder(order)) {
                throw new IllegalStateException("담당자 변경은 직배송/현장배송의 생산완료 또는 출고완료 건만 가능합니다. orderId=" + orderId);
            }

            if (existingIndex.getDeliveryHandler() != null
                    && existingIndex.getDeliveryHandler().getId() != null
                    && existingIndex.getDeliveryHandler().getId().equals(newHandler.getId())) {
                continue;
            }

            LocalDate deliveryDate = existingIndex.getDeliveryDate();

            if (deliveryDate == null) {
                throw new IllegalStateException("배송일 정보가 없습니다. orderId=" + orderId);
            }

            affectedDates.add(deliveryDate);

            order.setAssignedDeliveryHandler(newHandler);

            if (order.getPreferredDeliveryDate() == null) {
                order.setPreferredDeliveryDate(deliveryDate.atStartOfDay());
            }

            order.setUpdatedAt(java.time.LocalDateTime.now());

            replaceIndexAtQueueEnd(
                    order,
                    existingIndex,
                    newHandler,
                    deliveryDate,
                    resolveDeliveryListSection(order)
            );

            changedOrderIds.add(order.getId());
        }

        for (LocalDate affectedDate : affectedDates) {
            normalizeIndexesForHandlerDate(loginMember.getId(), affectedDate);
            normalizeIndexesForHandlerDate(newHandler.getId(), affectedDate);
        }

        return changedOrderIds;
    }

    /**
     * 기존 코드 호환용입니다.
     * 실제 직배송 여부만 확인합니다.
     */
    public boolean isDirectDeliveryOrder(Order order) {
        return hasDeliveryMethodName(order, DIRECT_DELIVERY_METHOD_NAME);
    }

    /**
     * 화물 여부 확인용입니다.
     */
    public boolean isFreightDeliveryOrder(Order order) {
        return hasDeliveryMethodName(order, FREIGHT_DELIVERY_METHOD_NAME);
    }

    /**
     * 현장배송 여부 확인용입니다.
     */
    public boolean isSiteDeliveryOrder(Order order) {
        return hasDeliveryMethodName(order, SITE_DELIVERY_METHOD_NAME);
    }

    public boolean isActionableDeliveryMethod(Order order) {
        return isDirectDeliveryOrder(order) || isSiteDeliveryOrder(order);
    }

    /**
     * 직배송/현장배송 배송 큐에 배치할 상태입니다.
     * 승인완료도 출고팀에서 담당자를 미리 배정할 수 있으므로 일반 배송순번 구간에 포함합니다.
     * 단, 배송팀의 순서변경/담당자변경 가능 여부는 isActionablePendingDeliveryOrder로 별도 제한합니다.
     */
    public boolean isQueuedDirectOrSiteOrder(Order order) {
        if (order == null || order.getStatus() == null) {
            return false;
        }

        return isActionableDeliveryMethod(order)
                && (order.getStatus() == OrderStatus.CONFIRMED
                || order.getStatus() == OrderStatus.PRODUCTION_DONE
                || order.getStatus() == OrderStatus.DISPATCH_DONE);
    }

    public boolean isActionablePendingDeliveryOrder(Order order) {
        if (order == null || order.getStatus() == null) {
            return false;
        }

        return isActionableDeliveryMethod(order)
                && (order.getStatus() == OrderStatus.PRODUCTION_DONE
                || order.getStatus() == OrderStatus.DISPATCH_DONE);
    }

    public boolean isActionableDoneDeliveryOrder(Order order) {
        if (order == null || order.getStatus() == null) {
            return false;
        }

        return isActionableDeliveryMethod(order)
                && order.getStatus() == OrderStatus.DELIVERY_DONE;
    }

    public boolean isFreightDeliveryListOrder(Order order) {
        return order != null
                && isDeliveryIndexManagedOrder(order)
                && isFreightDeliveryOrder(order);
    }

    public boolean isOtherDeliveryListOrder(Order order) {
        if (order == null) {
            return false;
        }

        return isDeliveryIndexManagedOrder(order)
                && !isQueuedDirectOrSiteOrder(order)
                && !isFreightDeliveryListOrder(order)
                && !isActionableDoneDeliveryOrder(order);
    }

    /**
     * 담당자 필수 배송수단 여부입니다.
     * 배송수단명에 직배송/현장배송/화물 단어가 포함되면 담당자 필수로 판정합니다.
     */
    public boolean isDeliveryHandlerRequiredMethod(Order order) {
        return order != null
                && DeliveryMethodAssignmentPolicy.requiresHandler(order.getDeliveryMethod());
    }

    /**
     * DeliveryOrderIndex에 올라와 있는 주문이 배송팀 화면에서 표시 가능한 주문인지 확인합니다.
     * 담당자가 지정되어 DeliveryOrderIndex가 생성된 주문은 배송수단과 관계없이 표시 대상입니다.
     */
    public boolean isDeliveryIndexManagedOrder(Order order) {
        if (order == null) {
            return false;
        }

        return order.getStatus() != OrderStatus.CANCELED;
    }

    /**
     * 배송팀에서 실제 배송완료 처리 가능한 주문은 직배송/현장배송 + 생산완료(PRODUCTION_DONE)뿐입니다.
     * DISPATCH_DONE을 포함한 다른 상태는 목록에 보이더라도 배송완료 처리할 수 없습니다.
     */
    public boolean isCompletableByDeliveryTeam(Order order) {
        return order != null
                && isActionableDeliveryMethod(order)
                && order.getStatus() == OrderStatus.PRODUCTION_DONE;
    }

    private List<DeliveryOrderIndex> getVisibleIndexesForHandlerAndDate(Long handlerId, LocalDate date) {
        return deliveryOrderIndexRepository.findAllByHandlerAndDateForTaskGrouping(handlerId, date)
                .stream()
                .filter(this::isVisibleDeliveryIndex)
                .collect(Collectors.toList());
    }

    private void replaceIndexAtQueueEnd(
            Order order,
            DeliveryOrderIndex existing,
            Member handler,
            LocalDate deliveryDate,
            DeliveryListSection section
    ) {
        DeliveryQueueLocation previousQueue = DeliveryQueueLocation.from(existing);

        Set<Long> handlerIdsToLock = new HashSet<>();
        if (previousQueue.handlerId() != null) {
            handlerIdsToLock.add(previousQueue.handlerId());
        }
        if (handler != null && handler.getId() != null) {
            handlerIdsToLock.add(handler.getId());
        }
        lockQueueOwners(handlerIdsToLock);

        if (existing != null) {
            deliveryOrderIndexRepository.delete(existing);

            /*
             * order_id unique 제약과 handler/date/order_index unique 제약 충돌 방지용입니다.
             * 같은 트랜잭션 안에서 delete 후 insert를 확실히 분리합니다.
             */
            deliveryOrderIndexRepository.flush();
        }

        DeliveryOrderIndex created = new DeliveryOrderIndex();
        created.setOrder(order);
        created.setDeliveryHandler(handler);
        created.setDeliveryDate(deliveryDate);
        created.setOrderIndex(nextIndexForSection(handler.getId(), deliveryDate, section, order.getId()));

        /*
         * 저장 시점에 유니크 제약 위반을 바로 확인하고, Order 날짜 flush 이후에
         * DeliveryOrderIndex 날짜가 확실히 반영되도록 saveAndFlush를 사용합니다.
         */
        deliveryOrderIndexRepository.saveAndFlush(created);

        normalizePreviousQueueAfterMove(previousQueue, handler.getId(), deliveryDate);
    }

    private int nextIndexForSection(Long handlerId, LocalDate deliveryDate, DeliveryListSection section, Long excludeOrderId) {
        lockDeliveryHandlerQueues(Set.of(handlerId));

        /*
         * 기존 배포 버전의 100,000/1,000,000 기준 데이터가 남아 있어도
         * 새 화물 구간과 충돌하지 않도록 해당 담당자/날짜를 먼저 정규화합니다.
         */
        normalizeIndexesForHandlerDate(handlerId, deliveryDate);

        int base = sectionStartIndex(section);

        int max = getVisibleIndexesForHandlerAndDate(handlerId, deliveryDate)
                .stream()
                .filter(x -> x.getOrder() != null)
                .filter(x -> excludeOrderId == null
                        || x.getOrder().getId() == null
                        || !excludeOrderId.equals(x.getOrder().getId()))
                .filter(x -> resolveDeliveryListSection(x.getOrder()) == section)
                .mapToInt(DeliveryOrderIndex::getOrderIndex)
                .filter(index -> isIndexInSectionRange(index, section))
                .max()
                .orElse(base - 1);

        int next = Math.max(base, max + 1);
        int endExclusive = sectionEndExclusive(section);

        if (endExclusive != Integer.MAX_VALUE && next >= endExclusive) {
            throw new IllegalStateException("배송순번 구간이 가득 찼습니다. 담당자=" + handlerId
                    + ", 배송일=" + deliveryDate + ", 구간=" + section);
        }

        return next;
    }

    private void rewriteIndexesBySection(
            List<DeliveryOrderIndex> allRows,
            List<DeliveryOrderIndex> activeRows,
            List<DeliveryOrderIndex> freightRows,
            List<DeliveryOrderIndex> doneRows,
            List<DeliveryOrderIndex> otherRows
    ) {
        int temp = TEMP_REINDEX_START;

        for (DeliveryOrderIndex row : allRows) {
            row.setOrderIndex(temp++);
        }

        deliveryOrderIndexRepository.flush();

        int next = ACTIONABLE_PENDING_INDEX_START;
        for (DeliveryOrderIndex row : activeRows) {
            if (next >= FREIGHT_INDEX_START) {
                throw new IllegalStateException("직배송/현장배송 배송순번 구간이 가득 찼습니다.");
            }
            row.setOrderIndex(next++);
        }

        next = FREIGHT_INDEX_START;
        for (DeliveryOrderIndex row : freightRows) {
            if (next >= ACTIONABLE_DONE_INDEX_START) {
                throw new IllegalStateException("화물 배송순번 구간이 가득 찼습니다.");
            }
            row.setOrderIndex(next++);
        }

        next = ACTIONABLE_DONE_INDEX_START;
        for (DeliveryOrderIndex row : doneRows) {
            if (next >= OTHER_INDEX_START) {
                throw new IllegalStateException("배송완료 배송순번 구간이 가득 찼습니다.");
            }
            row.setOrderIndex(next++);
        }

        next = OTHER_INDEX_START;
        for (DeliveryOrderIndex row : otherRows) {
            row.setOrderIndex(next++);
        }

        /*
         * 임시 음수 순번에서 최종 순번으로 되돌린 결과를 즉시 반영합니다.
         * 이후 같은 트랜잭션에서 최대 순번을 다시 조회해도 최종값만 사용됩니다.
         */
        deliveryOrderIndexRepository.flush();
    }

    private boolean hasSequentialIndexes(List<DeliveryOrderIndex> rows, int startIndex) {
        int expectedIndex = startIndex;

        for (DeliveryOrderIndex row : rows) {
            if (row == null || row.getOrderIndex() != expectedIndex) {
                return false;
            }
            expectedIndex++;
        }

        return true;
    }

    private void deleteExistingIndex(DeliveryOrderIndex existing) {
        if (existing == null) {
            return;
        }

        DeliveryQueueLocation previousQueue = DeliveryQueueLocation.from(existing);

        if (previousQueue.handlerId() != null) {
            lockQueueOwners(Set.of(previousQueue.handlerId()));
        }

        deliveryOrderIndexRepository.delete(existing);
        deliveryOrderIndexRepository.flush();

        normalizeQueue(previousQueue);
    }

    private void normalizePreviousQueueAfterMove(
            DeliveryQueueLocation previousQueue,
            Long currentHandlerId,
            LocalDate currentDeliveryDate
    ) {
        if (!previousQueue.isComplete()) {
            return;
        }

        if (Objects.equals(previousQueue.handlerId(), currentHandlerId)
                && Objects.equals(previousQueue.deliveryDate(), currentDeliveryDate)) {
            return;
        }

        normalizeQueue(previousQueue);
    }

    private void normalizeQueue(DeliveryQueueLocation queueLocation) {
        if (queueLocation == null || !queueLocation.isComplete()) {
            return;
        }

        normalizeIndexesForHandlerDate(queueLocation.handlerId(), queueLocation.deliveryDate());
    }

    private boolean isVisibleDeliveryIndex(DeliveryOrderIndex deliveryOrderIndex) {
        if (deliveryOrderIndex == null) {
            return false;
        }

        Order order = deliveryOrderIndex.getOrder();

        if (!isDeliveryIndexManagedOrder(order)) {
            return false;
        }

        if (deliveryOrderIndex.getDeliveryHandler() == null
                || deliveryOrderIndex.getDeliveryHandler().getId() == null) {
            return false;
        }

        if (deliveryOrderIndex.getDeliveryDate() == null) {
            return false;
        }

        return true;
    }

    private DeliveryListSection resolveDeliveryListSection(Order order) {
        if (isQueuedDirectOrSiteOrder(order)) {
            return DeliveryListSection.ACTIONABLE_PENDING;
        }

        if (isFreightDeliveryListOrder(order)) {
            return DeliveryListSection.FREIGHT;
        }

        if (isActionableDoneDeliveryOrder(order)) {
            return DeliveryListSection.ACTIONABLE_DONE;
        }

        return DeliveryListSection.OTHER;
    }

    private Comparator<DeliveryOrderIndex> deliveryListComparator() {
        return Comparator
                .comparingInt((DeliveryOrderIndex x) -> sectionSortOrder(resolveDeliveryListSection(x.getOrder())))
                .thenComparingInt(this::safeOrderIndex)
                .thenComparingLong(this::safeOrderId);
    }

    private Comparator<DeliveryOrderIndex> indexComparator() {
        return Comparator
                .comparingInt(this::safeOrderIndex)
                .thenComparingLong(this::safeOrderId);
    }

    private int safeOrderIndex(DeliveryOrderIndex index) {
        return index == null ? Integer.MAX_VALUE : index.getOrderIndex();
    }

    private long safeOrderId(DeliveryOrderIndex index) {
        if (index == null || index.getOrder() == null || index.getOrder().getId() == null) {
            return Long.MAX_VALUE;
        }

        return index.getOrder().getId();
    }

    private int sectionSortOrder(DeliveryListSection section) {
        return switch (section) {
            case ACTIONABLE_PENDING -> 0;
            case FREIGHT -> 1;
            case ACTIONABLE_DONE -> 2;
            case OTHER -> 3;
        };
    }

    private int sectionStartIndex(DeliveryListSection section) {
        return switch (section) {
            case ACTIONABLE_PENDING -> ACTIONABLE_PENDING_INDEX_START;
            case FREIGHT -> FREIGHT_INDEX_START;
            case ACTIONABLE_DONE -> ACTIONABLE_DONE_INDEX_START;
            case OTHER -> OTHER_INDEX_START;
        };
    }

    private int sectionEndExclusive(DeliveryListSection section) {
        return switch (section) {
            case ACTIONABLE_PENDING -> FREIGHT_INDEX_START;
            case FREIGHT -> ACTIONABLE_DONE_INDEX_START;
            case ACTIONABLE_DONE -> OTHER_INDEX_START;
            case OTHER -> Integer.MAX_VALUE;
        };
    }

    private boolean isIndexInSectionRange(int index, DeliveryListSection section) {
        return index >= sectionStartIndex(section) && index < sectionEndExclusive(section);
    }

    private void lockQueueOwners(Set<Long> handlerIds) {
        if (handlerIds == null || handlerIds.isEmpty()) {
            return;
        }

        List<Long> sortedIds = handlerIds.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .sorted()
                .toList();

        for (Long handlerId : sortedIds) {
            Member lockedHandler = entityManager.find(
                    Member.class,
                    handlerId,
                    LockModeType.PESSIMISTIC_WRITE
            );

            if (lockedHandler == null) {
                throw new IllegalArgumentException(
                        "배송 담당자가 존재하지 않습니다. handlerId=" + handlerId
                );
            }
        }
    }

    private void lockDeliveryHandlerQueues(Set<Long> handlerIds) {
        if (handlerIds == null || handlerIds.isEmpty()) {
            return;
        }

        List<Long> sortedIds = handlerIds.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .sorted()
                .toList();

        for (Long handlerId : sortedIds) {
            Member lockedHandler = entityManager.find(
                    Member.class,
                    handlerId,
                    LockModeType.PESSIMISTIC_WRITE
            );

            if (lockedHandler == null) {
                throw new IllegalArgumentException(
                        "배송 담당자가 존재하지 않습니다. handlerId=" + handlerId
                );
            }

            validateDeliveryTeamHandler(lockedHandler);
        }
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

    private void validateDeliveryTeamMember(Member member) {
        if (member == null || member.getTeam() == null || !DELIVERY_TEAM_NAME.equals(member.getTeam().getName())) {
            throw new AccessDeniedException("배송팀만 접근할 수 있습니다.");
        }
    }

    private void validateDeliveryTeamHandler(Member member) {
        if (member == null) {
            throw new IllegalArgumentException("배송 담당자가 없습니다.");
        }

        if (member.getTeam() == null || !DELIVERY_TEAM_NAME.equals(member.getTeam().getName())) {
            throw new IllegalArgumentException("배송팀 직원만 담당자로 지정할 수 있습니다.");
        }

        if (!member.isEnabled()) {
            throw new IllegalArgumentException("비활성화된 직원은 담당자로 지정할 수 없습니다.");
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

    private void validateDeliveryIndexManagedOrder(Order order) {
        if (!isDeliveryIndexManagedOrder(order)) {
            throw new IllegalStateException("배송순서 관리 대상 주문만 배송팀에서 확인할 수 있습니다.");
        }
    }

    private void validateCompletableStatus(Order order) {
        if (!isCompletableByDeliveryTeam(order)) {
            throw new IllegalStateException(
                    "직배송 또는 현장배송의 생산완료 주문만 배송완료 처리할 수 있습니다. "
                            + "현재 상태="
                            + (order != null && order.getStatus() != null ? order.getStatus().getLabel() : "미확인")
            );
        }
    }

    private String buildAddressKey(Order order) {
        if (order == null) {
            return "";
        }

        boolean useSiteAddress = isSiteDeliveryOrder(order)
                && DeliveryAddressNormalizationUtil.hasAnyMeaningfulAddressText(
                        order.getSiteDoName(),
                        order.getSiteSiName(),
                        order.getSiteGuName(),
                        order.getSiteRoadAddress(),
                        order.getSiteDetailAddress()
                );

        if (useSiteAddress) {
            return DeliveryAddressNormalizationUtil.build(
                    order.getSiteZipCode(),
                    order.getSiteDoName(),
                    order.getSiteSiName(),
                    order.getSiteGuName(),
                    order.getSiteRoadAddress(),
                    order.getSiteDetailAddress()
            ).key();
        }

        return DeliveryAddressNormalizationUtil.build(
                order.getZipCode(),
                order.getDoName(),
                order.getSiName(),
                order.getGuName(),
                order.getRoadAddress(),
                order.getDetailAddress()
        ).key();
    }

    private String resolveDeliveryMethodKey(Order order) {
        if (order == null || order.getDeliveryMethod() == null) {
            return "";
        }

        return normalizeMethodName(order.getDeliveryMethod().getMethodName());
    }

    private boolean hasDeliveryMethodName(Order order, String expectedMethodName) {
        if (order == null || order.getDeliveryMethod() == null) {
            return false;
        }

        String methodName = normalizeMethodName(order.getDeliveryMethod().getMethodName());
        String expected = normalizeMethodName(expectedMethodName);

        return !methodName.isBlank() && methodName.contains(expected);
    }

    private String normalizeMethodName(String value) {
        return DeliveryMethodAssignmentPolicy.normalize(value);
    }


    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }

    private record DeliveryQueueLocation(Long handlerId, LocalDate deliveryDate) {

        private static DeliveryQueueLocation from(DeliveryOrderIndex index) {
            if (index == null) {
                return new DeliveryQueueLocation(null, null);
            }

            Long handlerId = index.getDeliveryHandler() != null
                    ? index.getDeliveryHandler().getId()
                    : null;

            return new DeliveryQueueLocation(handlerId, index.getDeliveryDate());
        }

        private boolean isComplete() {
            return handlerId != null && deliveryDate != null;
        }
    }

    private enum DeliveryListSection {
        ACTIONABLE_PENDING,
        FREIGHT,
        ACTIONABLE_DONE,
        OTHER
    }
}
