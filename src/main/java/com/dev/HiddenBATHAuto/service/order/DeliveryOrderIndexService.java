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

    private static final String DIRECT_DELIVERY_METHOD_NAME = "직배송";
    private static final String FREIGHT_DELIVERY_METHOD_NAME = "화물";
    private static final String SITE_DELIVERY_METHOD_NAME = "현장배송";

    /*
     * 관리자 저장 시 담당자 필수 검증은 OrderUpdateService에서 처리합니다.
     * 여기서는 배송 담당자가 지정된 주문을 DeliveryOrderIndex에 올리고,
     * 배송팀 화면에서 조작 가능한 영역과 조작 불가 영역을 index range로 분리합니다.
     */
    private static final Set<String> REQUIRED_DELIVERY_HANDLER_METHOD_NAMES = Set.of(
            DIRECT_DELIVERY_METHOD_NAME,
            FREIGHT_DELIVERY_METHOD_NAME,
            SITE_DELIVERY_METHOD_NAME
    );

    /*
     * order_index range 분리 기준입니다.
     * - 1 ~ 99,999         : 직배송/현장배송 + 생산완료/출고완료. 순서변경/업체별정렬/배송완료/담당자변경 가능
     * - 100,000 ~ 999,999  : 직배송/현장배송 + 배송완료. 상세만 가능
     * - 1,000,000 이상     : 그 외 배송수단 또는 배송팀 조작 불가 상태. 상세만 가능
     *
     * tb_delivery_order_index의 unique(handler, date, order_index) 충돌을 피하기 위해
     * 저장/정규화 시 임시 음수 index로 한 번 밀어낸 뒤 range별로 재부여합니다.
     */
    private static final int ACTIONABLE_PENDING_INDEX_START = 1;
    private static final int ACTIONABLE_DONE_INDEX_START = 100_000;
    private static final int OTHER_INDEX_START = 1_000_000;
    private static final int TEMP_REINDEX_START = -1_000_000_000;

    private final DeliveryOrderIndexRepository deliveryOrderIndexRepository;
    private final MemberRepository memberRepository;
    private final OrderRepository orderRepository;

    /**
     * 관리자 오더 수정 저장 시 배송순서 인덱스를 동기화합니다.
     *
     * 처리 기준:
     * - 취소/담당자 없음/배송희망일 없음 => 기존 DeliveryOrderIndex 삭제
     * - 담당자 있음 + 배송희망일 있음 => 배송수단과 관계없이 DeliveryOrderIndex 생성
     * - 담당자/날짜 변경 => 기존 row 삭제 후 새 담당자/날짜의 적절한 section 끝에 추가
     * - 담당자/날짜 동일 + 배송수단/상태만 변경 => row는 유지하되 section range가 달라지면 index만 재배치
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

        if (order.getStatus() == OrderStatus.CANCELED || handler == null || deliveryDate == null) {
            deleteExistingIndex(existing);
            return;
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

            existing.setOrderIndex(nextIndexForSection(handler.getId(), deliveryDate, targetSection, order.getId()));
            return;
        }

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

        List<DeliveryOrderIndex> doneRows = current.stream()
                .filter(x -> isActionableDoneDeliveryOrder(x.getOrder()))
                .sorted(indexComparator())
                .collect(Collectors.toList());

        List<DeliveryOrderIndex> otherRows = current.stream()
                .filter(x -> isOtherDeliveryListOrder(x.getOrder()))
                .sorted(indexComparator())
                .collect(Collectors.toList());

        rewriteIndexesBySection(current, activeRows, doneRows, otherRows);
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
                .ifPresent(deliveryOrderIndexRepository::delete);
    }

    /**
     * 기존 호출부 호환을 위해 메서드명은 유지합니다.
     * 현재는 직배송만 반환하지 않고, 담당자가 지정되어 DeliveryOrderIndex에 올라간 row를
     * 배송팀 화면 표시 순서(직/현 작업 가능 -> 배송완료 -> 기타)로 반환합니다.
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

        List<DeliveryOrderIndex> current = getVisibleIndexesForHandlerAndDate(handlerId, deliveryDate);

        if (current.isEmpty()) {
            return;
        }

        List<DeliveryOrderIndex> activeRows = current.stream()
                .filter(x -> isActionablePendingDeliveryOrder(x.getOrder()))
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

        boolean alreadyNormalized = activeRows.stream().allMatch(x -> isIndexInSectionRange(x.getOrderIndex(), DeliveryListSection.ACTIONABLE_PENDING))
                && doneRows.stream().allMatch(x -> isIndexInSectionRange(x.getOrderIndex(), DeliveryListSection.ACTIONABLE_DONE))
                && otherRows.stream().allMatch(x -> isIndexInSectionRange(x.getOrderIndex(), DeliveryListSection.OTHER));

        if (alreadyNormalized) {
            return;
        }

        rewriteIndexesBySection(current, activeRows, doneRows, otherRows);
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
     * 동일 업체 + 동일 주소 + 동일 배송일 기준 배송완료 대상 조회.
     * 배송완료 대상은 직배송/현장배송 + 생산완료/출고완료만 허용합니다.
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

        return deliveryOrderIndexRepository.findAllByHandlerAndDateForTaskGrouping(
                        loginMember.getId(),
                        sourceDeliveryDate
                )
                .stream()
                .filter(this::isVisibleDeliveryIndex)
                .filter(x -> isCompletableByDeliveryTeam(x.getOrder()))
                .filter(x -> sourceCompanyId.equals(resolveCompanyId(x.getOrder())))
                .filter(x -> sourceAddressKey.equals(buildAddressKey(x.getOrder())))
                .sorted(Comparator.comparingInt(DeliveryOrderIndex::getOrderIndex))
                .map(DeliveryOrderIndex::getOrder)
                .collect(Collectors.toList());
    }

    /**
     * 배송팀 담당자 변경은 직배송/현장배송 + 생산완료/출고완료 건만 가능합니다.
     * 택배/화물/방문/배송완료/승인완료 등은 상세 확인만 가능합니다.
     */
    @Transactional
    public void changeDeliveryHandler(Member loginMember, Long orderId, Long newHandlerId) {
        validateDeliveryTeamMember(loginMember);

        if (newHandlerId == null) {
            throw new IllegalArgumentException("변경할 담당자를 선택해주세요.");
        }

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("해당 주문이 존재하지 않습니다."));

        DeliveryOrderIndex existingIndex = deliveryOrderIndexRepository.findByOrder(order)
                .orElseThrow(() -> new IllegalStateException("배송순서 정보가 없습니다."));

        validateCurrentHandler(loginMember, existingIndex);
        validateDeliveryIndexManagedOrder(order);

        if (!isActionablePendingDeliveryOrder(order)) {
            throw new IllegalStateException("담당자 변경은 직배송/현장배송의 생산완료 또는 출고완료 건만 가능합니다.");
        }

        Member newHandler = memberRepository.findById(newHandlerId)
                .orElseThrow(() -> new IllegalArgumentException("변경할 배송 담당자가 존재하지 않습니다."));

        validateDeliveryTeamHandler(newHandler);

        if (newHandler.getId() != null && newHandler.getId().equals(loginMember.getId())) {
            return;
        }

        LocalDate deliveryDate = existingIndex.getDeliveryDate();

        if (deliveryDate == null) {
            throw new IllegalStateException("배송일 정보가 없습니다.");
        }

        order.setAssignedDeliveryHandler(newHandler);

        if (order.getPreferredDeliveryDate() == null) {
            order.setPreferredDeliveryDate(deliveryDate.atStartOfDay());
        }

        order.setUpdatedAt(java.time.LocalDateTime.now());

        replaceIndexAtQueueEnd(order, existingIndex, newHandler, deliveryDate, resolveDeliveryListSection(order));
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

    public boolean isOtherDeliveryListOrder(Order order) {
        if (order == null) {
            return false;
        }

        return isDeliveryIndexManagedOrder(order)
                && !isActionablePendingDeliveryOrder(order)
                && !isActionableDoneDeliveryOrder(order);
    }

    /**
     * 담당자 필수 배송수단 여부입니다.
     * - 직배송
     * - 화물
     * - 현장배송
     */
    public boolean isDeliveryHandlerRequiredMethod(Order order) {
        if (order == null || order.getDeliveryMethod() == null) {
            return false;
        }

        String methodName = normalizeMethodName(order.getDeliveryMethod().getMethodName());

        if (methodName.isBlank()) {
            return false;
        }

        return REQUIRED_DELIVERY_HANDLER_METHOD_NAMES.contains(methodName);
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
     * 배송팀에서 실제 배송완료 처리 가능한 주문은 직배송/현장배송 + 생산완료/출고완료뿐입니다.
     */
    public boolean isCompletableByDeliveryTeam(Order order) {
        return isActionablePendingDeliveryOrder(order);
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

        deliveryOrderIndexRepository.save(created);
    }

    private int nextIndexForSection(Long handlerId, LocalDate deliveryDate, DeliveryListSection section, Long excludeOrderId) {
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

        return Math.max(base, max + 1);
    }

    private void rewriteIndexesBySection(
            List<DeliveryOrderIndex> allRows,
            List<DeliveryOrderIndex> activeRows,
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
            row.setOrderIndex(next++);
        }

        next = ACTIONABLE_DONE_INDEX_START;
        for (DeliveryOrderIndex row : doneRows) {
            row.setOrderIndex(next++);
        }

        next = OTHER_INDEX_START;
        for (DeliveryOrderIndex row : otherRows) {
            row.setOrderIndex(next++);
        }
    }

    private void deleteExistingIndex(DeliveryOrderIndex existing) {
        if (existing == null) {
            return;
        }

        deliveryOrderIndexRepository.delete(existing);
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
        if (isActionablePendingDeliveryOrder(order)) {
            return DeliveryListSection.ACTIONABLE_PENDING;
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
            case ACTIONABLE_DONE -> 1;
            case OTHER -> 2;
        };
    }

    private int sectionStartIndex(DeliveryListSection section) {
        return switch (section) {
            case ACTIONABLE_PENDING -> ACTIONABLE_PENDING_INDEX_START;
            case ACTIONABLE_DONE -> ACTIONABLE_DONE_INDEX_START;
            case OTHER -> OTHER_INDEX_START;
        };
    }

    private boolean isIndexInSectionRange(int index, DeliveryListSection section) {
        return switch (section) {
            case ACTIONABLE_PENDING -> index >= ACTIONABLE_PENDING_INDEX_START && index < ACTIONABLE_DONE_INDEX_START;
            case ACTIONABLE_DONE -> index >= ACTIONABLE_DONE_INDEX_START && index < OTHER_INDEX_START;
            case OTHER -> index >= OTHER_INDEX_START;
        };
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
            throw new IllegalStateException("직배송 또는 현장배송의 생산완료/출고완료 주문만 배송완료 처리할 수 있습니다.");
        }
    }

    private String buildAddressKey(Order order) {
        if (order == null) {
            return "";
        }

        /*
         * 현장배송일 때는 실제 배송지가 site_* 주소입니다.
         * 단, 과거 데이터나 미입력 데이터 보호를 위해 siteRoadAddress가 없으면 기존 배송주소로 fallback합니다.
         */
        if (isSiteDeliveryOrder(order) && !safeText(order.getSiteRoadAddress()).isBlank()) {
            return String.join("|",
                    normalizeKey(order.getSiteZipCode()),
                    normalizeKey(order.getSiteDoName()),
                    normalizeKey(order.getSiteSiName()),
                    normalizeKey(order.getSiteGuName()),
                    normalizeKey(order.getSiteRoadAddress()),
                    normalizeKey(order.getSiteDetailAddress())
            );
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

    private boolean hasDeliveryMethodName(Order order, String expectedMethodName) {
        if (order == null || order.getDeliveryMethod() == null) {
            return false;
        }

        String methodName = normalizeMethodName(order.getDeliveryMethod().getMethodName());
        String expected = normalizeMethodName(expectedMethodName);

        return !methodName.isBlank() && methodName.equals(expected);
    }

    private String normalizeMethodName(String value) {
        return safeText(value)
                .replaceAll("\\s+", "")
                .replaceAll("\\(금액:.*?\\)", "")
                .trim();
    }

    private String normalizeKey(String value) {
        return safeText(value)
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", "");
    }

    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }

    private enum DeliveryListSection {
        ACTIONABLE_PENDING,
        ACTIONABLE_DONE,
        OTHER
    }
}
