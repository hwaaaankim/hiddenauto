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

    private static final Set<String> REQUIRED_DELIVERY_HANDLER_METHOD_NAMES = Set.of(
            DIRECT_DELIVERY_METHOD_NAME,
            FREIGHT_DELIVERY_METHOD_NAME,
            SITE_DELIVERY_METHOD_NAME
    );

    private final DeliveryOrderIndexRepository deliveryOrderIndexRepository;
    private final MemberRepository memberRepository;
    private final OrderRepository orderRepository;

    /**
     * 관리자 오더 수정 저장 시 배송순서 인덱스를 동기화합니다.
     *
     * 변경 기준:
     * - 담당자 없음 또는 배송희망일 없음 또는 취소 상태 => 기존 DeliveryOrderIndex 삭제
     * - 기존 인덱스 없음 + 담당자/날짜 있음 => 해당 담당자/날짜의 max(orderIndex) + 1 로 생성
     * - 기존 인덱스 있음 + 담당자 또는 날짜 변경 => 기존 인덱스 삭제 후 max(orderIndex) + 1 로 새로 생성
     * - 기존 인덱스 있음 + 담당자/날짜 동일 => 유지
     *
     * 중요:
     * - 이제 직배송만 대상으로 보지 않습니다.
     * - 배송수단과 관계없이 assignedDeliveryHandler가 지정되어 있고 배송희망일이 있으면 배송순서 대상입니다.
     * - 직배송/화물/현장배송 담당자 필수 여부 검증은 OrderUpdateService에서 수행하고,
     *   이 서비스는 실제 DeliveryOrderIndex 생성/삭제/재배치를 담당합니다.
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

        if (existing != null
                && existing.getDeliveryHandler() != null
                && Objects.equals(existing.getDeliveryHandler().getId(), handler.getId())
                && Objects.equals(existing.getDeliveryDate(), deliveryDate)) {
            return;
        }

        replaceIndexAtQueueEnd(order, existing, handler, deliveryDate);
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

        List<DeliveryOrderIndex> current = deliveryOrderIndexRepository
                .findAllByHandlerAndDateForTaskGrouping(handler.getId(), date)
                .stream()
                .filter(this::isVisibleDeliveryIndex)
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

        List<Long> requestedOrderIds = request.getOrderList().stream()
                .map(DeliveryOrderIndexUpdateRequest.OrderIndexDto::getOrderId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        long distinctCount = requestedOrderIds.stream().distinct().count();

        if (distinctCount != requestedOrderIds.size()) {
            throw new IllegalArgumentException("중복된 orderId가 존재합니다.");
        }

        Map<Long, DeliveryOrderIndex> indexByOrderId = current.stream()
                .collect(Collectors.toMap(
                        x -> x.getOrder().getId(),
                        x -> x,
                        (a, b) -> a
                ));

        for (Long orderId : requestedOrderIds) {
            if (!indexByOrderId.containsKey(orderId)) {
                throw new IllegalArgumentException("해당 주문 인덱스 없음: orderId=" + orderId);
            }
        }

        int newIndex = 1;

        for (DeliveryOrderIndexUpdateRequest.OrderIndexDto dto : request.getOrderList()) {
            if (dto == null || dto.getOrderId() == null) {
                continue;
            }

            DeliveryOrderIndex index = indexByOrderId.get(dto.getOrderId());
            index.setOrderIndex(newIndex++);
        }
    }

    @Transactional(readOnly = true)
    public List<Long> reorderPendingOrderIdsByTask(Long handlerId, LocalDate deliveryDate, List<Long> pendingOrderIds) {
        List<DeliveryOrderIndex> all = getDirectDeliveryIndexes(handlerId, deliveryDate, null);

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

            if (deliveryOrderIndex.getOrder().getStatus() == OrderStatus.DELIVERY_DONE) {
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
            throw new IllegalArgumentException("업체별정렬 불가: pending 대상이 아닌 orderId 포함 - " + invalid);
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
     *
     * 기존에는 직배송만 화면에 보여주기 위해 필터링했지만,
     * 현재 요구사항에서는 배송수단과 관계없이 담당자가 지정된 주문은
     * DeliveryOrderIndex 관리 대상입니다.
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
        validateDeliveryIndexManagedOrder(order);
        validateCompletableStatus(order);

        return order;
    }

    /**
     * 동일 업체 + 동일 주소 + 동일 배송일 기준 배송완료 대상 조회
     *
     * 주소 기준:
     * - 현장배송이고 site_road_address가 있으면 site_* 현장주소 기준
     * - 그 외에는 기존 배송주소 기준
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

        if (order.getStatus() == OrderStatus.DELIVERY_DONE) {
            throw new IllegalStateException("배송완료 건은 담당자를 변경할 수 없습니다.");
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

        replaceIndexAtQueueEnd(order, existingIndex, newHandler, deliveryDate);
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
     * DeliveryOrderIndex에 올라와 있는 주문이 배송팀 화면에서 관리 가능한 주문인지 확인합니다.
     *
     * 여기서는 배송수단명을 제한하지 않습니다.
     * 담당자가 지정되어 DeliveryOrderIndex가 생성된 주문은 배송수단과 관계없이 관리 대상으로 봅니다.
     */
    public boolean isDeliveryIndexManagedOrder(Order order) {
        if (order == null) {
            return false;
        }

        return order.getStatus() != OrderStatus.CANCELED;
    }

    public boolean isCompletableByDeliveryTeam(Order order) {
        if (order == null || order.getStatus() == null) {
            return false;
        }

        return order.getStatus() == OrderStatus.PRODUCTION_DONE
                || order.getStatus() == OrderStatus.DISPATCH_DONE;
    }

    private void replaceIndexAtQueueEnd(
            Order order,
            DeliveryOrderIndex existing,
            Member handler,
            LocalDate deliveryDate
    ) {
        if (existing != null) {
            deliveryOrderIndexRepository.delete(existing);

            /*
             * order_id unique 제약과 handler/date/order_index unique 제약 충돌 방지용입니다.
             * 같은 트랜잭션 안에서 delete 후 insert를 확실히 분리합니다.
             */
            deliveryOrderIndexRepository.flush();
        }

        Integer maxIndex = deliveryOrderIndexRepository.findMaxIndexByHandlerAndDate(handler.getId(), deliveryDate);
        int nextIndex = maxIndex == null ? 1 : maxIndex + 1;

        DeliveryOrderIndex created = new DeliveryOrderIndex();
        created.setOrder(order);
        created.setDeliveryHandler(handler);
        created.setDeliveryDate(deliveryDate);
        created.setOrderIndex(nextIndex);

        deliveryOrderIndexRepository.save(created);
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
            throw new IllegalStateException("배송순서 관리 대상 주문만 배송팀에서 처리할 수 있습니다.");
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
}