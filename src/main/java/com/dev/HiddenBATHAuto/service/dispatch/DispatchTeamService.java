package com.dev.HiddenBATHAuto.service.dispatch;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.PageMargin;
import org.apache.poi.ss.usermodel.PrintSetup;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dev.HiddenBATHAuto.dto.dispatch.DispatchBulkDtos.BulkDeliveryMethodChangeRequest;
import com.dev.HiddenBATHAuto.dto.dispatch.DispatchBulkDtos.BulkDeliveryMethodChangeResponse;
import com.dev.HiddenBATHAuto.dto.dispatch.DispatchBulkDtos.BulkDeliveryMethodPreviewResponse;
import com.dev.HiddenBATHAuto.dto.dispatch.DispatchBulkDtos.BulkHandlerChangeRequest;
import com.dev.HiddenBATHAuto.dto.dispatch.DispatchBulkDtos.BulkHandlerChangeResponse;
import com.dev.HiddenBATHAuto.dto.dispatch.DispatchBulkDtos.BulkHandlerChangePreviewResponse;
import com.dev.HiddenBATHAuto.dto.dispatch.DispatchBulkDtos.BulkOrderInfoDto;
import com.dev.HiddenBATHAuto.dto.dispatch.DispatchBulkDtos.DeliveryMethodOptionDto;
import com.dev.HiddenBATHAuto.dto.dispatch.DispatchBulkDtos.OrderHandlerAssignmentDto;
import com.dev.HiddenBATHAuto.dto.dispatch.DispatchDtos.BulkDispatchCompleteResponse;
import com.dev.HiddenBATHAuto.dto.dispatch.DispatchDtos.BulkDispatchFailDto;
import com.dev.HiddenBATHAuto.dto.dispatch.DispatchDtos.DeliveryMethodDto;
import com.dev.HiddenBATHAuto.dto.dispatch.DispatchDtos.DispatchOrderRowDto;
import com.dev.HiddenBATHAuto.dto.dispatch.DispatchDtos.DispatchOrderSearchRequest;
import com.dev.HiddenBATHAuto.dto.dispatch.DispatchDtos.DispatchOrderSearchResponse;
import com.dev.HiddenBATHAuto.model.auth.Company;
import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.auth.TeamCategory;
import com.dev.HiddenBATHAuto.model.caculate.DeliveryMethod;
import com.dev.HiddenBATHAuto.model.task.DeliveryOrderIndex;
import com.dev.HiddenBATHAuto.model.task.Order;
import com.dev.HiddenBATHAuto.model.task.OrderItem;
import com.dev.HiddenBATHAuto.model.task.OrderStatus;
import com.dev.HiddenBATHAuto.model.task.Task;
import com.dev.HiddenBATHAuto.repository.auth.MemberRepository;
import com.dev.HiddenBATHAuto.repository.caculate.DeliveryMethodRepository;
import com.dev.HiddenBATHAuto.repository.order.DeliveryOrderIndexRepository;
import com.dev.HiddenBATHAuto.repository.order.OrderRepository;
import com.dev.HiddenBATHAuto.service.order.DeliveryMethodAssignmentPolicy;
import com.dev.HiddenBATHAuto.service.order.DeliveryMethodAssignmentPolicy.MethodGroup;
import com.dev.HiddenBATHAuto.service.order.DeliveryOrderIndexService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DispatchTeamService {

    private static final int DEFAULT_SIZE = 50;
    private static final int MAX_SIZE = 100;

    private static final String DISPATCH_TEAM_NAME = "출고팀";
    private static final String DELIVERY_TEAM_NAME = "배송팀";

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final EntityManager entityManager;
    private final ObjectMapper objectMapper;

    private final OrderRepository orderRepository;
    private final DeliveryMethodRepository deliveryMethodRepository;
    private final MemberRepository memberRepository;
    private final DeliveryOrderIndexRepository deliveryOrderIndexRepository;
    private final DeliveryOrderIndexService deliveryOrderIndexService;

    @Transactional(readOnly = true)
    public DispatchOrderSearchResponse searchDispatchOrders(
            DispatchOrderSearchRequest request,
            Member loginMember
    ) {
        validateDispatchTeamMember(loginMember);

        DispatchOrderSearchRequest normalizedRequest = normalizeSearchRequest(request);
        int size = normalizeSize(normalizedRequest.getSize());

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Order> cq = cb.createQuery(Order.class);
        Root<Order> orderRoot = cq.from(Order.class);

        Join<Order, Task> taskJoin = orderRoot.join("task", JoinType.LEFT);
        Join<Task, Member> requestedByJoin = taskJoin.join("requestedBy", JoinType.LEFT);
        Join<Member, Company> companyJoin = requestedByJoin.join("company", JoinType.LEFT);
        Join<Order, TeamCategory> categoryJoin = orderRoot.join("productCategory", JoinType.LEFT);
        Join<Order, DeliveryMethod> deliveryMethodJoin = orderRoot.join("deliveryMethod", JoinType.LEFT);

        List<Predicate> predicates = new ArrayList<>();

        addCommonSearchPredicates(
                cb,
                predicates,
                orderRoot,
                requestedByJoin,
                companyJoin,
                categoryJoin,
                deliveryMethodJoin,
                normalizedRequest
        );

        List<Long> loadedOrderIds = normalizeIds(normalizedRequest.getLoadedOrderIds());
        if (!loadedOrderIds.isEmpty()) {
            predicates.add(cb.not(orderRoot.get("id").in(loadedOrderIds)));
        }

        Predicate cursorPredicate = buildCursorPredicate(
                cb,
                orderRoot,
                normalizedRequest.getLastStatusSort(),
                normalizedRequest.getLastOrderId()
        );

        if (cursorPredicate != null) {
            predicates.add(cursorPredicate);
        }

        Expression<Integer> statusSort = buildStatusSortExpression(cb, orderRoot);

        cq.select(orderRoot)
                .where(predicates.toArray(new Predicate[0]))
                .orderBy(
                        cb.asc(statusSort),
                        cb.desc(orderRoot.get("id"))
                );

        TypedQuery<Order> query = entityManager.createQuery(cq);
        query.setMaxResults(size + 1);

        List<Order> fetchedOrders = query.getResultList();

        boolean hasNext = fetchedOrders.size() > size;
        List<Order> visibleOrders = hasNext
                ? fetchedOrders.subList(0, size)
                : fetchedOrders;

        List<DispatchOrderRowDto> rows = visibleOrders.stream()
                .map(this::toDispatchOrderRowDto)
                .toList();

        Integer nextLastStatusSort = null;
        Long nextLastOrderId = null;

        if (!rows.isEmpty()) {
            DispatchOrderRowDto lastRow = rows.get(rows.size() - 1);
            nextLastStatusSort = lastRow.getStatusSort();
            nextLastOrderId = lastRow.getOrderId();
        }

        return DispatchOrderSearchResponse.builder()
                .orders(rows)
                .hasNext(hasNext)
                .nextLastStatusSort(nextLastStatusSort)
                .nextLastOrderId(nextLastOrderId)
                .requestedSize(size)
                .returnedSize(rows.size())
                .build();
    }

    @Transactional(readOnly = true)
    public byte[] createDispatchOrdersExcel(
            DispatchOrderSearchRequest request,
            Member loginMember
    ) {
        validateDispatchTeamMember(loginMember);

        DispatchOrderSearchRequest normalizedRequest = normalizeSearchRequest(request);
        List<Order> orders = findDispatchOrdersForExcel(normalizedRequest);

        List<DispatchOrderRowDto> rows = orders.stream()
                .map(this::toDispatchOrderRowDto)
                .toList();

        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("출고팀 업무현황");

            configurePrint(sheet);
            createExcelSheet(workbook, sheet, rows, normalizedRequest);

            workbook.write(out);
            return out.toByteArray();

        } catch (IOException e) {
            throw new IllegalStateException("엑셀 파일 생성 중 오류가 발생했습니다.", e);
        }
    }

    @Transactional
    public BulkDispatchCompleteResponse completeDispatchOrders(
            List<Long> orderIds,
            Member loginMember
    ) {
        validateDispatchTeamMember(loginMember);

        List<Long> normalizedIds = normalizeIds(orderIds);

        if (normalizedIds.isEmpty()) {
            return BulkDispatchCompleteResponse.builder()
                    .requestedCount(0)
                    .updatedCount(0)
                    .failedCount(0)
                    .build();
        }

        List<Order> foundOrders = orderRepository.findAllById(normalizedIds);
        Map<Long, Order> orderMap = new LinkedHashMap<>();

        for (Order order : foundOrders) {
            if (order != null && order.getId() != null) {
                orderMap.put(order.getId(), order);
            }
        }

        List<Long> updatedOrderIds = new ArrayList<>();
        List<DispatchOrderRowDto> updatedRows = new ArrayList<>();
        List<BulkDispatchFailDto> failedItems = new ArrayList<>();
        List<Order> saveTargets = new ArrayList<>();

        for (Long orderId : normalizedIds) {
            Order order = orderMap.get(orderId);

            if (order == null) {
                failedItems.add(BulkDispatchFailDto.builder()
                        .orderId(orderId)
                        .message("주문을 찾을 수 없습니다.")
                        .build());
                continue;
            }

            if (!isDispatchCompletableStatus(order.getStatus())) {
                failedItems.add(BulkDispatchFailDto.builder()
                        .orderId(orderId)
                        .message("승인완료 또는 생산완료 상태만 출고완료 처리할 수 있습니다.")
                        .build());
                continue;
            }

            order.setStatus(OrderStatus.DISPATCH_DONE);
            order.setUpdatedAt(LocalDateTime.now());

            saveTargets.add(order);
            updatedOrderIds.add(orderId);
        }

        if (!saveTargets.isEmpty()) {
            orderRepository.saveAll(saveTargets);
            orderRepository.flush();

            for (Order order : saveTargets) {
                updatedRows.add(toDispatchOrderRowDto(order));
            }
        }

        return BulkDispatchCompleteResponse.builder()
                .updatedOrderIds(updatedOrderIds)
                .updatedRows(updatedRows)
                .failedItems(failedItems)
                .requestedCount(normalizedIds.size())
                .updatedCount(updatedOrderIds.size())
                .failedCount(failedItems.size())
                .build();
    }

    @Transactional
    public DeliveryMethodDto updateDeliveryMethod(
            Long orderId,
            Long deliveryMethodId,
            Long deliveryHandlerId,
            Member loginMember
    ) {
        validateDispatchTeamMember(loginMember);

        if (orderId == null) {
            throw new IllegalArgumentException("주문 ID가 없습니다.");
        }

        if (deliveryMethodId == null) {
            throw new IllegalArgumentException("배송수단 ID가 없습니다.");
        }

        Order order = entityManager.find(
                Order.class,
                orderId,
                LockModeType.PESSIMISTIC_WRITE
        );

        if (order == null) {
            throw new IllegalArgumentException("주문을 찾을 수 없습니다.");
        }

        validateBulkTargetOrder(order);

        DeliveryMethod nextDeliveryMethod = deliveryMethodRepository.findById(deliveryMethodId)
                .orElseThrow(() -> new IllegalArgumentException("배송수단을 찾을 수 없습니다."));

        MethodGroup nextGroup = DeliveryMethodAssignmentPolicy.classify(nextDeliveryMethod);
        DeliveryOrderIndex existingIndex = findDeliveryOrderIndex(order);
        Member currentHandler = resolveAssignedDeliveryHandler(order, existingIndex);
        Member nextHandler = null;
        Set<Long> affectedHandlerIds = new HashSet<>();

        if (currentHandler != null && currentHandler.getId() != null) {
            affectedHandlerIds.add(currentHandler.getId());
        }

        if (nextGroup != MethodGroup.NO_HANDLER) {
            Long resolvedHandlerId = deliveryHandlerId != null
                    ? deliveryHandlerId
                    : currentHandler != null ? currentHandler.getId() : null;

            if (resolvedHandlerId == null) {
                throw new IllegalArgumentException(
                        safeTextOrDash(nextDeliveryMethod.getMethodName())
                                + "으로 변경하려면 배송 담당자를 선택해야 합니다."
                );
            }

            nextHandler = getValidatedDeliveryHandler(resolvedHandlerId, new HashMap<>());
            requireDeliveryDate(order);
            affectedHandlerIds.add(nextHandler.getId());
        }

        lockDeliveryHandlerIds(affectedHandlerIds);

        order.setDeliveryMethod(nextDeliveryMethod);
        order.setAssignedDeliveryHandler(nextHandler);
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);
        orderRepository.flush();

        if (nextGroup == MethodGroup.NO_HANDLER) {
            deliveryOrderIndexService.removeIndex(order);
        } else {
            deliveryOrderIndexService.ensureIndex(order);
        }

        deliveryOrderIndexRepository.flush();

        DeliveryOrderIndex savedDeliveryOrderIndex = findDeliveryOrderIndex(order);
        return toDeliveryMethodDto(nextDeliveryMethod, savedDeliveryOrderIndex);
    }

    @Transactional(readOnly = true)
    public BulkHandlerChangePreviewResponse previewBulkHandlerChange(
            List<Long> orderIds,
            Member loginMember
    ) {
        validateDispatchTeamMember(loginMember);

        List<Long> normalizedIds = normalizeIds(orderIds);

        if (normalizedIds.isEmpty()) {
            throw new IllegalArgumentException("담당자를 변경할 주문을 1건 이상 선택해 주세요.");
        }

        Map<Long, Order> orderMap = findOrderMap(normalizedIds);

        List<BulkOrderInfoDto> changeableOrders = new ArrayList<>();
        List<BulkOrderInfoDto> excludedOrders = new ArrayList<>();
        List<BulkOrderInfoDto> blockingOrders = new ArrayList<>();

        for (Long orderId : normalizedIds) {
            Order order = orderMap.get(orderId);

            if (order == null) {
                blockingOrders.add(missingBulkOrderInfo(orderId, "주문을 찾을 수 없습니다."));
                continue;
            }

            if (!isVisibleDispatchStatus(order.getStatus())) {
                blockingOrders.add(toBulkOrderInfo(order, "현재 상태에서는 출고팀 일괄 변경을 할 수 없습니다."));
                continue;
            }

            if (!DeliveryMethodAssignmentPolicy.requiresHandler(order.getDeliveryMethod())) {
                String methodName = deliveryMethodName(order);
                excludedOrders.add(toBulkOrderInfo(
                        order,
                        methodName + " 건은 담당자 지정이 불가능합니다. "
                                + "배송수단을 직배송/현장배송/화물로 변경한 후 담당자를 지정할 수 있습니다."
                ));
                continue;
            }

            if (order.getPreferredDeliveryDate() == null) {
                blockingOrders.add(toBulkOrderInfo(order, "배송희망일이 없어 담당자 배정 순번을 생성할 수 없습니다."));
                continue;
            }

            changeableOrders.add(toBulkOrderInfo(order, null));
        }

        return BulkHandlerChangePreviewResponse.builder()
                .requestedCount(normalizedIds.size())
                .changeableCount(changeableOrders.size())
                .excludedCount(excludedOrders.size())
                .blockingCount(blockingOrders.size())
                .exclusionConfirmationRequired(!excludedOrders.isEmpty())
                .changeableOrders(changeableOrders)
                .excludedOrders(excludedOrders)
                .blockingOrders(blockingOrders)
                .build();
    }

    @Transactional
    public BulkHandlerChangeResponse bulkChangeDeliveryHandler(
            BulkHandlerChangeRequest request,
            Member loginMember
    ) {
        validateDispatchTeamMember(loginMember);

        if (request == null) {
            throw new IllegalArgumentException("일괄 담당자 변경 요청이 없습니다.");
        }

        List<Long> normalizedIds = normalizeIds(request.getOrderIds());

        if (normalizedIds.isEmpty()) {
            throw new IllegalArgumentException("담당자를 변경할 주문을 1건 이상 선택해 주세요.");
        }

        Member targetHandler = getValidatedDeliveryHandler(
                request.getDeliveryHandlerId(),
                new HashMap<>()
        );

        Map<Long, Order> orderMap = findLockedOrderMap(normalizedIds);
        List<Order> changeTargets = new ArrayList<>();
        List<Long> excludedOrderIds = new ArrayList<>();
        Set<Long> affectedHandlerIds = new HashSet<>();
        affectedHandlerIds.add(targetHandler.getId());
        Set<Long> acknowledgedExcludedIds = normalizeAcknowledgedOrderIdSet(
                request.getAcknowledgedExcludedOrderIds(),
                normalizedIds,
                "담당자 지정 제외 확인 주문"
        );

        for (Long orderId : normalizedIds) {
            Order order = orderMap.get(orderId);

            if (order == null) {
                throw new IllegalArgumentException("주문을 찾을 수 없습니다. orderId=" + orderId);
            }

            validateBulkTargetOrder(order);

            if (!DeliveryMethodAssignmentPolicy.requiresHandler(order.getDeliveryMethod())) {
                excludedOrderIds.add(orderId);
                continue;
            }

            requireDeliveryDate(order);

            Member currentHandler = resolveAssignedDeliveryHandler(
                    order,
                    findDeliveryOrderIndex(order)
            );
            if (currentHandler != null && currentHandler.getId() != null) {
                affectedHandlerIds.add(currentHandler.getId());
            }

            changeTargets.add(order);
        }

        Set<Long> actualExcludedIds = new LinkedHashSet<>(excludedOrderIds);

        if (!actualExcludedIds.isEmpty() && !request.isExcludeUnavailable()) {
            throw new IllegalStateException(
                    "담당자 지정이 불가능한 배송수단이 포함되어 있습니다. "
                            + "제외 안내를 확인한 후 다시 진행해 주세요."
            );
        }

        if (!actualExcludedIds.equals(acknowledgedExcludedIds)) {
            throw new IllegalStateException(
                    "미리보기 이후 담당자 지정 가능 여부가 변경되었습니다. "
                            + "모달을 닫고 선택 주문을 다시 확인해 주세요."
            );
        }

        if (changeTargets.isEmpty()) {
            throw new IllegalStateException("담당자를 변경할 수 있는 주문이 없습니다.");
        }

        lockDeliveryHandlerIds(affectedHandlerIds);

        LocalDateTime now = LocalDateTime.now();
        for (Order order : changeTargets) {
            order.setAssignedDeliveryHandler(targetHandler);
            order.setUpdatedAt(now);
        }

        orderRepository.saveAll(changeTargets);
        orderRepository.flush();

        for (Order order : changeTargets) {
            deliveryOrderIndexService.ensureIndex(order);
        }

        deliveryOrderIndexRepository.flush();

        List<DispatchOrderRowDto> updatedRows = changeTargets.stream()
                .map(this::toDispatchOrderRowDto)
                .toList();

        return BulkHandlerChangeResponse.builder()
                .requestedCount(normalizedIds.size())
                .updatedCount(changeTargets.size())
                .excludedCount(excludedOrderIds.size())
                .updatedOrderIds(changeTargets.stream().map(Order::getId).toList())
                .excludedOrderIds(excludedOrderIds)
                .updatedRows(updatedRows)
                .build();
    }

    @Transactional(readOnly = true)
    public BulkDeliveryMethodPreviewResponse previewBulkDeliveryMethodChange(
            List<Long> orderIds,
            Long deliveryMethodId,
            Member loginMember
    ) {
        validateDispatchTeamMember(loginMember);

        List<Long> normalizedIds = normalizeIds(orderIds);

        if (normalizedIds.isEmpty()) {
            throw new IllegalArgumentException("배송수단을 변경할 주문을 1건 이상 선택해 주세요.");
        }

        if (deliveryMethodId == null) {
            throw new IllegalArgumentException("변경할 배송수단을 선택해 주세요.");
        }

        DeliveryMethod targetMethod = deliveryMethodRepository.findById(deliveryMethodId)
                .orElseThrow(() -> new IllegalArgumentException("변경할 배송수단을 찾을 수 없습니다."));

        MethodGroup targetGroup = DeliveryMethodAssignmentPolicy.classify(targetMethod);
        Map<Long, Order> orderMap = findOrderMap(normalizedIds);

        List<BulkOrderInfoDto> assignmentRequiredOrders = new ArrayList<>();
        List<BulkOrderInfoDto> assignmentRemovalOrders = new ArrayList<>();
        List<BulkOrderInfoDto> preservedAssignmentOrders = new ArrayList<>();
        List<BulkOrderInfoDto> blockingOrders = new ArrayList<>();

        for (Long orderId : normalizedIds) {
            Order order = orderMap.get(orderId);

            if (order == null) {
                blockingOrders.add(missingBulkOrderInfo(orderId, "주문을 찾을 수 없습니다."));
                continue;
            }

            if (!isVisibleDispatchStatus(order.getStatus())) {
                blockingOrders.add(toBulkOrderInfo(order, "현재 상태에서는 출고팀 일괄 변경을 할 수 없습니다."));
                continue;
            }

            DeliveryOrderIndex existingIndex = findDeliveryOrderIndex(order);
            Member currentHandler = resolveAssignedDeliveryHandler(order, existingIndex);
            boolean hasCurrentAssignment = currentHandler != null || existingIndex != null;
            MethodGroup currentGroup = DeliveryMethodAssignmentPolicy.classify(order.getDeliveryMethod());

            if (targetGroup == MethodGroup.NO_HANDLER) {
                boolean assignmentRemovalConfirmationRequired =
                        currentGroup != MethodGroup.NO_HANDLER || hasCurrentAssignment;

                if (assignmentRemovalConfirmationRequired) {
                    assignmentRemovalOrders.add(toBulkOrderInfo(
                            order,
                            "변경 후 담당자 배정이 불필요하여 기존 담당자의 배정업무에서 삭제됩니다."
                    ));
                }
                continue;
            }

            if (order.getPreferredDeliveryDate() == null) {
                blockingOrders.add(toBulkOrderInfo(order, "배송희망일이 없어 담당자 배정 순번을 생성할 수 없습니다."));
                continue;
            }

            boolean explicitAssignmentRequired = currentGroup == MethodGroup.NO_HANDLER
                    || !isSelectableDeliveryHandler(currentHandler);

            if (explicitAssignmentRequired) {
                assignmentRequiredOrders.add(toBulkOrderInfo(
                        order,
                        safeTextOrDash(targetMethod.getMethodName())
                                + "으로 변경하려면 이 주문의 배송 담당자를 선택해야 합니다."
                ));
            } else {
                preservedAssignmentOrders.add(toBulkOrderInfo(
                        order,
                        "기존 담당자 배정을 유지합니다."
                ));
            }
        }

        return BulkDeliveryMethodPreviewResponse.builder()
                .requestedCount(normalizedIds.size())
                .assignmentRequiredCount(assignmentRequiredOrders.size())
                .assignmentRemovalCount(assignmentRemovalOrders.size())
                .preservedAssignmentCount(preservedAssignmentOrders.size())
                .blockingCount(blockingOrders.size())
                .assignmentRemovalConfirmationRequired(!assignmentRemovalOrders.isEmpty())
                .targetMethod(toDeliveryMethodOptionDto(targetMethod))
                .assignmentRequiredOrders(assignmentRequiredOrders)
                .assignmentRemovalOrders(assignmentRemovalOrders)
                .preservedAssignmentOrders(preservedAssignmentOrders)
                .blockingOrders(blockingOrders)
                .build();
    }

    @Transactional
    public BulkDeliveryMethodChangeResponse bulkChangeDeliveryMethod(
            BulkDeliveryMethodChangeRequest request,
            Member loginMember
    ) {
        validateDispatchTeamMember(loginMember);

        if (request == null) {
            throw new IllegalArgumentException("일괄 배송수단 변경 요청이 없습니다.");
        }

        List<Long> normalizedIds = normalizeIds(request.getOrderIds());

        if (normalizedIds.isEmpty()) {
            throw new IllegalArgumentException("배송수단을 변경할 주문을 1건 이상 선택해 주세요.");
        }

        if (request.getDeliveryMethodId() == null) {
            throw new IllegalArgumentException("변경할 배송수단을 선택해 주세요.");
        }

        DeliveryMethod targetMethod = deliveryMethodRepository.findById(request.getDeliveryMethodId())
                .orElseThrow(() -> new IllegalArgumentException("변경할 배송수단을 찾을 수 없습니다."));

        MethodGroup targetGroup = DeliveryMethodAssignmentPolicy.classify(targetMethod);
        Map<Long, Long> assignmentMap = normalizeAssignmentMap(request.getAssignments(), normalizedIds);
        Set<Long> acknowledgedRemovalIds = normalizeAcknowledgedOrderIdSet(
                request.getAcknowledgedAssignmentRemovalOrderIds(),
                normalizedIds,
                "배정업무 삭제 확인 주문"
        );
        Map<Long, Member> handlerCache = new HashMap<>();
        Map<Long, Order> orderMap = findLockedOrderMap(normalizedIds);
        List<BulkDeliveryMethodPlan> plans = new ArrayList<>();
        List<Long> actualAssignmentRequiredOrderIds = new ArrayList<>();
        List<Long> actualRemovalOrderIds = new ArrayList<>();
        Set<Long> handlerIdsToLock = new HashSet<>();

        int assignmentCreatedCount = 0;
        int assignmentRemovedCount = 0;
        int assignmentPreservedCount = 0;

        for (Long orderId : normalizedIds) {
            Order order = orderMap.get(orderId);

            if (order == null) {
                throw new IllegalArgumentException("주문을 찾을 수 없습니다. orderId=" + orderId);
            }

            validateBulkTargetOrder(order);

            DeliveryOrderIndex existingIndex = findDeliveryOrderIndex(order);
            Member currentHandler = resolveAssignedDeliveryHandler(order, existingIndex);
            boolean hasCurrentAssignment = currentHandler != null || existingIndex != null;
            MethodGroup currentGroup = DeliveryMethodAssignmentPolicy.classify(order.getDeliveryMethod());

            if (currentHandler != null && currentHandler.getId() != null) {
                handlerIdsToLock.add(currentHandler.getId());
            }

            if (targetGroup == MethodGroup.NO_HANDLER) {
                boolean assignmentRemovalConfirmationRequired =
                        currentGroup != MethodGroup.NO_HANDLER || hasCurrentAssignment;

                if (assignmentRemovalConfirmationRequired) {
                    actualRemovalOrderIds.add(orderId);
                    assignmentRemovedCount++;
                }

                plans.add(new BulkDeliveryMethodPlan(order, null, true));
                continue;
            }

            requireDeliveryDate(order);

            Long requestedHandlerId = assignmentMap.get(orderId);
            boolean explicitAssignmentRequired = currentGroup == MethodGroup.NO_HANDLER
                    || !isSelectableDeliveryHandler(currentHandler);

            Member nextHandler;
            if (explicitAssignmentRequired) {
                actualAssignmentRequiredOrderIds.add(orderId);

                if (requestedHandlerId == null) {
                    throw new IllegalArgumentException(
                            "담당자 선택이 필요한 주문이 있습니다. orderId=" + orderId
                    );
                }

                nextHandler = getValidatedDeliveryHandler(requestedHandlerId, handlerCache);
                assignmentCreatedCount++;
            } else {
                if (requestedHandlerId != null
                        && !requestedHandlerId.equals(currentHandler.getId())) {
                    throw new IllegalStateException(
                            "기존 담당자를 유지하는 주문의 담당자 정보가 변경되었습니다. "
                                    + "모달을 닫고 다시 확인해 주세요. orderId=" + orderId
                    );
                }

                nextHandler = currentHandler;
                assignmentPreservedCount++;
            }

            handlerIdsToLock.add(nextHandler.getId());
            plans.add(new BulkDeliveryMethodPlan(order, nextHandler, false));
        }

        Set<Long> actualAssignmentRequiredIds =
                new LinkedHashSet<>(actualAssignmentRequiredOrderIds);
        Set<Long> submittedAssignmentIds = new LinkedHashSet<>(assignmentMap.keySet());

        if (!actualAssignmentRequiredIds.equals(submittedAssignmentIds)) {
            throw new IllegalStateException(
                    "미리보기 이후 담당자 선택이 필요한 주문이 변경되었습니다. "
                            + "배송수단 변경 내용을 다시 확인해 주세요."
            );
        }

        Set<Long> actualRemovalIds = new LinkedHashSet<>(actualRemovalOrderIds);

        if (!actualRemovalIds.isEmpty() && !request.isConfirmAssignmentRemoval()) {
            throw new IllegalStateException(
                    "담당자 배정업무 삭제 안내를 확인한 후 다시 진행해 주세요."
            );
        }

        if (!actualRemovalIds.equals(acknowledgedRemovalIds)) {
            throw new IllegalStateException(
                    "미리보기 이후 배정업무 삭제 대상이 변경되었습니다. "
                            + "배송수단 변경 내용을 다시 확인해 주세요."
            );
        }

        lockDeliveryHandlerIds(handlerIdsToLock);

        LocalDateTime now = LocalDateTime.now();
        List<Order> saveTargets = new ArrayList<>();

        for (BulkDeliveryMethodPlan plan : plans) {
            Order order = plan.order();
            order.setDeliveryMethod(targetMethod);
            order.setAssignedDeliveryHandler(plan.removeAssignment() ? null : plan.handler());
            order.setUpdatedAt(now);
            saveTargets.add(order);
        }

        orderRepository.saveAll(saveTargets);
        orderRepository.flush();

        for (BulkDeliveryMethodPlan plan : plans) {
            if (plan.removeAssignment()) {
                deliveryOrderIndexService.removeIndex(plan.order());
            } else {
                deliveryOrderIndexService.ensureIndex(plan.order());
            }
        }

        deliveryOrderIndexRepository.flush();

        List<DispatchOrderRowDto> updatedRows = saveTargets.stream()
                .map(this::toDispatchOrderRowDto)
                .toList();

        return BulkDeliveryMethodChangeResponse.builder()
                .requestedCount(normalizedIds.size())
                .updatedCount(saveTargets.size())
                .assignmentCreatedCount(assignmentCreatedCount)
                .assignmentRemovedCount(assignmentRemovedCount)
                .assignmentPreservedCount(assignmentPreservedCount)
                .updatedOrderIds(saveTargets.stream().map(Order::getId).toList())
                .updatedRows(updatedRows)
                .build();
    }

    public void validateDispatchTeamMember(Member loginMember) {
        if (loginMember == null
                || loginMember.getTeam() == null
                || !DISPATCH_TEAM_NAME.equals(loginMember.getTeam().getName())) {
            throw new AccessDeniedException("출고팀만 접근할 수 있습니다.");
        }
    }

    private Map<Long, Order> findOrderMap(List<Long> orderIds) {
        Map<Long, Order> result = new LinkedHashMap<>();

        if (orderIds == null || orderIds.isEmpty()) {
            return result;
        }

        for (Order order : orderRepository.findAllById(orderIds)) {
            if (order != null && order.getId() != null) {
                result.put(order.getId(), order);
            }
        }

        return result;
    }

    private Map<Long, Order> findLockedOrderMap(List<Long> orderIds) {
        Map<Long, Order> result = new LinkedHashMap<>();

        if (orderIds == null || orderIds.isEmpty()) {
            return result;
        }

        List<Long> sortedIds = orderIds.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .sorted()
                .toList();

        for (Long orderId : sortedIds) {
            Order order = entityManager.find(
                    Order.class,
                    orderId,
                    LockModeType.PESSIMISTIC_WRITE
            );

            if (order != null) {
                result.put(orderId, order);
            }
        }

        return result;
    }

    private DeliveryOrderIndex findDeliveryOrderIndex(Order order) {
        if (order == null || order.getId() == null) {
            return null;
        }

        return deliveryOrderIndexRepository.findByOrder_Id(order.getId()).orElse(null);
    }

    private Member resolveAssignedDeliveryHandler(
            Order order,
            DeliveryOrderIndex deliveryOrderIndex
    ) {
        if (order != null && order.getAssignedDeliveryHandler() != null) {
            return order.getAssignedDeliveryHandler();
        }

        return deliveryOrderIndex != null
                ? deliveryOrderIndex.getDeliveryHandler()
                : null;
    }

    private LocalDate requireDeliveryDate(Order order) {
        if (order == null || order.getPreferredDeliveryDate() == null) {
            Long orderId = order != null ? order.getId() : null;
            throw new IllegalStateException("배송희망일이 없어 담당자를 배정할 수 없습니다. orderId=" + orderId);
        }

        return order.getPreferredDeliveryDate().toLocalDate();
    }

    private void validateBulkTargetOrder(Order order) {
        if (order == null) {
            throw new IllegalArgumentException("주문을 찾을 수 없습니다.");
        }

        if (!isVisibleDispatchStatus(order.getStatus())) {
            throw new IllegalStateException(
                    "출고관리 대상 상태가 아닙니다. orderId=" + order.getId()
            );
        }
    }

    private Member getValidatedDeliveryHandler(
            Long handlerId,
            Map<Long, Member> cache
    ) {
        if (handlerId == null) {
            throw new IllegalArgumentException("배송 담당자를 선택해 주세요.");
        }

        Member cached = cache != null ? cache.get(handlerId) : null;
        if (cached != null) {
            return cached;
        }

        Member handler = memberRepository.findById(handlerId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "배송 담당자를 찾을 수 없습니다. handlerId=" + handlerId
                ));

        validateDeliveryHandler(handler);

        if (cache != null) {
            cache.put(handlerId, handler);
        }

        return handler;
    }

    private boolean isSelectableDeliveryHandler(Member member) {
        return member != null
                && member.isEnabled()
                && member.getTeam() != null
                && DELIVERY_TEAM_NAME.equals(member.getTeam().getName());
    }

    private void lockDeliveryHandlerIds(Set<Long> handlerIds) {
        if (handlerIds == null || handlerIds.isEmpty()) {
            return;
        }

        List<Long> sortedIds = handlerIds.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .sorted()
                .toList();

        for (Long handlerId : sortedIds) {
            Member locked = entityManager.find(
                    Member.class,
                    handlerId,
                    LockModeType.PESSIMISTIC_WRITE
            );

            if (locked == null) {
                throw new IllegalArgumentException(
                        "배송 담당자를 찾을 수 없습니다. handlerId=" + handlerId
                );
            }

            validateDeliveryHandler(locked);
        }
    }

    private Set<Long> normalizeAcknowledgedOrderIdSet(
            List<Long> acknowledgedOrderIds,
            List<Long> selectedOrderIds,
            String fieldLabel
    ) {
        Set<Long> selectedSet = new LinkedHashSet<>(selectedOrderIds);
        Set<Long> result = new LinkedHashSet<>();

        if (acknowledgedOrderIds == null) {
            return result;
        }

        for (Long orderId : acknowledgedOrderIds) {
            if (orderId == null || orderId <= 0) {
                continue;
            }

            if (!selectedSet.contains(orderId)) {
                throw new IllegalArgumentException(
                        fieldLabel + "에 선택하지 않은 주문이 포함되어 있습니다. orderId=" + orderId
                );
            }

            result.add(orderId);
        }

        return result;
    }

    private Map<Long, Long> normalizeAssignmentMap(
            List<OrderHandlerAssignmentDto> assignments,
            List<Long> selectedOrderIds
    ) {
        Map<Long, Long> result = new LinkedHashMap<>();
        Set<Long> selectedSet = new HashSet<>(selectedOrderIds);

        if (assignments == null) {
            return result;
        }

        for (OrderHandlerAssignmentDto assignment : assignments) {
            if (assignment == null
                    || assignment.getOrderId() == null
                    || assignment.getDeliveryHandlerId() == null) {
                continue;
            }

            if (!selectedSet.contains(assignment.getOrderId())) {
                throw new IllegalArgumentException(
                        "선택하지 않은 주문의 담당자 정보가 포함되어 있습니다. orderId="
                                + assignment.getOrderId()
                );
            }

            Long previous = result.putIfAbsent(
                    assignment.getOrderId(),
                    assignment.getDeliveryHandlerId()
            );

            if (previous != null && !previous.equals(assignment.getDeliveryHandlerId())) {
                throw new IllegalArgumentException(
                        "한 주문에 서로 다른 담당자가 중복 지정되었습니다. orderId="
                                + assignment.getOrderId()
                );
            }
        }

        return result;
    }

    private DeliveryMethodOptionDto toDeliveryMethodOptionDto(DeliveryMethod deliveryMethod) {
        MethodGroup group = DeliveryMethodAssignmentPolicy.classify(deliveryMethod);

        return DeliveryMethodOptionDto.builder()
                .id(deliveryMethod != null ? deliveryMethod.getId() : null)
                .methodName(deliveryMethod != null
                        ? safeTextOrDash(deliveryMethod.getMethodName())
                        : "미지정")
                .methodGroup(group.name())
                .handlerRequired(group != MethodGroup.NO_HANDLER)
                .build();
    }

    private BulkOrderInfoDto toBulkOrderInfo(Order order, String reason) {
        if (order == null) {
            return missingBulkOrderInfo(null, reason);
        }

        DispatchOrderRowDto row = toDispatchOrderRowDto(order);
        DeliveryOrderIndex deliveryOrderIndex = findDeliveryOrderIndex(order);
        Member handler = resolveAssignedDeliveryHandler(order, deliveryOrderIndex);

        return BulkOrderInfoDto.builder()
                .orderId(order.getId())
                .companyName(row.getCompanyName())
                .productName(row.getProductName())
                .deliveryMethodName(row.getDeliveryMethodName())
                .deliveryHandlerId(handler != null ? handler.getId() : null)
                .deliveryHandlerName(handler != null ? safeTextOrDash(handler.getName()) : null)
                .deliveryDate(order.getPreferredDeliveryDate() != null
                        ? order.getPreferredDeliveryDate().toLocalDate()
                        : null)
                .reason(reason)
                .build();
    }

    private BulkOrderInfoDto missingBulkOrderInfo(Long orderId, String reason) {
        return BulkOrderInfoDto.builder()
                .orderId(orderId)
                .companyName("-")
                .productName("-")
                .deliveryMethodName("미확인")
                .reason(reason)
                .build();
    }

    private String deliveryMethodName(Order order) {
        return order != null && order.getDeliveryMethod() != null
                ? safeTextOrDash(order.getDeliveryMethod().getMethodName())
                : "미지정";
    }

    private record BulkDeliveryMethodPlan(
            Order order,
            Member handler,
            boolean removeAssignment
    ) {
    }

    private List<Order> findDispatchOrdersForExcel(DispatchOrderSearchRequest request) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Order> cq = cb.createQuery(Order.class);
        Root<Order> orderRoot = cq.from(Order.class);

        Join<Order, Task> taskJoin = orderRoot.join("task", JoinType.LEFT);
        Join<Task, Member> requestedByJoin = taskJoin.join("requestedBy", JoinType.LEFT);
        Join<Member, Company> companyJoin = requestedByJoin.join("company", JoinType.LEFT);
        Join<Order, TeamCategory> categoryJoin = orderRoot.join("productCategory", JoinType.LEFT);
        Join<Order, DeliveryMethod> deliveryMethodJoin = orderRoot.join("deliveryMethod", JoinType.LEFT);

        List<Predicate> predicates = new ArrayList<>();

        addCommonSearchPredicates(
                cb,
                predicates,
                orderRoot,
                requestedByJoin,
                companyJoin,
                categoryJoin,
                deliveryMethodJoin,
                request
        );

        Expression<Integer> statusSort = buildStatusSortExpression(cb, orderRoot);

        cq.select(orderRoot)
                .where(predicates.toArray(new Predicate[0]))
                .orderBy(
                        cb.asc(statusSort),
                        cb.desc(orderRoot.get("id"))
                );

        return entityManager.createQuery(cq).getResultList();
    }

    private void addCommonSearchPredicates(
            CriteriaBuilder cb,
            List<Predicate> predicates,
            Root<Order> orderRoot,
            Join<Task, Member> requestedByJoin,
            Join<Member, Company> companyJoin,
            Join<Order, TeamCategory> categoryJoin,
            Join<Order, DeliveryMethod> deliveryMethodJoin,
            DispatchOrderSearchRequest request
    ) {
        predicates.add(orderRoot.get("status").in(
                OrderStatus.CONFIRMED,
                OrderStatus.PRODUCTION_DONE,
                OrderStatus.DISPATCH_DONE
        ));

        if (request.getProductCategoryId() != null) {
            predicates.add(cb.equal(
                    categoryJoin.get("id"),
                    request.getProductCategoryId()
            ));
        }

        String standard = safeText(request.getStandard()).toUpperCase();

        if ("STANDARD".equals(standard)) {
            predicates.add(cb.isTrue(orderRoot.get("standard")));
        } else if ("NON_STANDARD".equals(standard)) {
            predicates.add(cb.isFalse(orderRoot.get("standard")));
        }

        if (request.getOrderDate() != null) {
            LocalDate orderDate = request.getOrderDate();
            LocalDateTime start = orderDate.atStartOfDay();
            LocalDateTime end = orderDate.plusDays(1).atStartOfDay();

            predicates.add(cb.greaterThanOrEqualTo(orderRoot.get("preferredDeliveryDate"), start));
            predicates.add(cb.lessThan(orderRoot.get("preferredDeliveryDate"), end));
        }

        if (request.getDeliveryMethodId() != null) {
            predicates.add(cb.equal(
                    deliveryMethodJoin.get("id"),
                    request.getDeliveryMethodId()
            ));
        }

        addKeywordPredicate(
                cb,
                predicates,
                request,
                requestedByJoin,
                companyJoin
        );

        addAddressPredicate(
                cb,
                predicates,
                orderRoot,
                request
        );
    }

    private DispatchOrderSearchRequest normalizeSearchRequest(DispatchOrderSearchRequest request) {
        DispatchOrderSearchRequest normalized = request == null
                ? new DispatchOrderSearchRequest()
                : request;

        if (normalized.getSize() == null) {
            normalized.setSize(DEFAULT_SIZE);
        }

        if (isBlank(normalized.getStandard())) {
            normalized.setStandard("ALL");
        }

        return normalized;
    }

    private int normalizeSize(Integer size) {
        if (size == null || size <= 0) {
            return DEFAULT_SIZE;
        }

        return Math.min(size, MAX_SIZE);
    }

    private void addKeywordPredicate(
            CriteriaBuilder cb,
            List<Predicate> predicates,
            DispatchOrderSearchRequest request,
            Join<Task, Member> requestedByJoin,
            Join<Member, Company> companyJoin
    ) {
        String keyword = safeText(request.getKeyword());

        if (keyword.isBlank()) {
            return;
        }

        String keywordType = safeText(request.getKeywordType()).toUpperCase();
        String likeKeyword = "%" + keyword.toLowerCase() + "%";

        switch (keywordType) {
            case "MEMBER_NAME" -> predicates.add(cb.like(
                    cb.lower(requestedByJoin.get("name")),
                    likeKeyword
            ));
            case "MEMBER_USERNAME" -> predicates.add(cb.like(
                    cb.lower(requestedByJoin.get("username")),
                    likeKeyword
            ));
            case "MEMBER_PHONE" -> predicates.add(cb.like(
                    cb.lower(requestedByJoin.get("phone")),
                    likeKeyword
            ));
            case "MEMBER_EMAIL" -> predicates.add(cb.like(
                    cb.lower(requestedByJoin.get("email")),
                    likeKeyword
            ));
            case "COMPANY_NAME" -> predicates.add(cb.like(
                    cb.lower(companyJoin.get("companyName")),
                    likeKeyword
            ));
            default -> predicates.add(cb.or(
                    cb.like(cb.lower(companyJoin.get("companyName")), likeKeyword),
                    cb.like(cb.lower(requestedByJoin.get("name")), likeKeyword),
                    cb.like(cb.lower(requestedByJoin.get("username")), likeKeyword),
                    cb.like(cb.lower(requestedByJoin.get("phone")), likeKeyword),
                    cb.like(cb.lower(requestedByJoin.get("email")), likeKeyword)
            ));
        }
    }

    private void addAddressPredicate(
            CriteriaBuilder cb,
            List<Predicate> predicates,
            Root<Order> orderRoot,
            DispatchOrderSearchRequest request
    ) {
        String doName = safeText(request.getDoName());
        String siName = safeText(request.getSiName());
        String guName = safeText(request.getGuName());

        if (!doName.isBlank()) {
            predicates.add(cb.like(
                    cb.lower(orderRoot.get("doName")),
                    "%" + doName.toLowerCase() + "%"
            ));
        }

        if (!siName.isBlank()) {
            predicates.add(cb.like(
                    cb.lower(orderRoot.get("siName")),
                    "%" + siName.toLowerCase() + "%"
            ));
        }

        if (!guName.isBlank()) {
            predicates.add(cb.like(
                    cb.lower(orderRoot.get("guName")),
                    "%" + guName.toLowerCase() + "%"
            ));
        }
    }

    private Predicate buildCursorPredicate(
            CriteriaBuilder cb,
            Root<Order> orderRoot,
            Integer lastStatusSort,
            Long lastOrderId
    ) {
        if (lastStatusSort == null || lastOrderId == null) {
            return null;
        }

        if (lastStatusSort <= 1) {
            return cb.or(
                    orderRoot.get("status").in(
                            OrderStatus.PRODUCTION_DONE,
                            OrderStatus.DISPATCH_DONE
                    ),
                    cb.and(
                            cb.equal(orderRoot.get("status"), OrderStatus.CONFIRMED),
                            cb.lessThan(orderRoot.get("id"), lastOrderId)
                    )
            );
        }

        if (lastStatusSort == 2) {
            return cb.or(
                    cb.equal(orderRoot.get("status"), OrderStatus.DISPATCH_DONE),
                    cb.and(
                            cb.equal(orderRoot.get("status"), OrderStatus.PRODUCTION_DONE),
                            cb.lessThan(orderRoot.get("id"), lastOrderId)
                    )
            );
        }

        return cb.and(
                cb.equal(orderRoot.get("status"), OrderStatus.DISPATCH_DONE),
                cb.lessThan(orderRoot.get("id"), lastOrderId)
        );
    }

    private Expression<Integer> buildStatusSortExpression(
            CriteriaBuilder cb,
            Root<Order> orderRoot
    ) {
        return cb.<Integer>selectCase()
                .when(cb.equal(orderRoot.get("status"), OrderStatus.CONFIRMED), 1)
                .when(cb.equal(orderRoot.get("status"), OrderStatus.PRODUCTION_DONE), 2)
                .when(cb.equal(orderRoot.get("status"), OrderStatus.DISPATCH_DONE), 3)
                .otherwise(99);
    }

    private DispatchOrderRowDto toDispatchOrderRowDto(Order order) {
        OrderItem item = order.getOrderItem();
        Map<String, Object> optionMap = parseOptionJson(item != null ? item.getOptionJson() : null);

        String optionProductName = pickFirstValue(optionMap, List.of(
                "제품",
                "제품명",
                "productName",
                "ProductName",
                "product_name"
        ));

        String itemProductName = item != null ? safeText(item.getProductName()) : "";
        String productName = firstNonBlank(optionProductName, itemProductName, "-");

        String color = firstNonBlank(
                pickFirstValue(optionMap, List.of("색상", "컬러", "color", "Color")),
                "-"
        );

        String sizeText = firstNonBlank(
                pickFirstValue(optionMap, List.of("사이즈", "규격", "size", "Size")),
                "-"
        );

        TeamCategory category = order.getProductCategory();
        DeliveryMethod deliveryMethod = order.getDeliveryMethod();

        DeliveryOrderIndex deliveryOrderIndex = deliveryOrderIndexRepository.findByOrder_Id(order.getId())
                .orElse(null);

        Member deliveryHandler = deliveryOrderIndex != null
                ? deliveryOrderIndex.getDeliveryHandler()
                : order.getAssignedDeliveryHandler();

        Member requestedBy = null;
        Company company = null;

        if (order.getTask() != null) {
            requestedBy = order.getTask().getRequestedBy();

            if (requestedBy != null) {
                company = requestedBy.getCompany();
            }
        }

        OrderStatus status = order.getStatus();

        return DispatchOrderRowDto.builder()
                .orderId(order.getId())
                .status(status != null ? status.name() : "")
                .statusLabel(status != null ? status.getLabel() : "-")
                .statusSort(statusSort(status))
                .dispatchCompletable(isDispatchCompletableStatus(status))
                .standard(order.isStandard())
                .standardLabel(order.isStandard() ? "규격" : "비규격")
                .productCategoryId(category != null ? category.getId() : null)
                .productCategoryName(category != null ? safeTextOrDash(category.getName()) : "-")
                .companyName(company != null ? safeTextOrDash(company.getCompanyName()) : "-")
                .memberName(requestedBy != null ? safeTextOrDash(requestedBy.getName()) : "-")
                .memberUsername(requestedBy != null ? safeTextOrDash(requestedBy.getUsername()) : "-")
                .memberPhone(requestedBy != null ? safeTextOrDash(requestedBy.getPhone()) : "-")
                .memberEmail(requestedBy != null ? safeTextOrDash(requestedBy.getEmail()) : "-")
                .productName(productName)
                .color(color)
                .sizeText(sizeText)
                .quantity(order.getQuantity())
                .adminMemo(safeTextOrDash(order.getAdminMemo()))
                .deliveryMethodId(deliveryMethod != null ? deliveryMethod.getId() : null)
                .deliveryMethodName(deliveryMethod != null ? safeTextOrDash(deliveryMethod.getMethodName()) : "미지정")
                .deliveryHandlerId(deliveryHandler != null ? deliveryHandler.getId() : null)
                .deliveryHandlerName(deliveryHandler != null ? safeTextOrDash(deliveryHandler.getName()) : null)
                .deliveryOrderIndex(deliveryOrderIndex != null ? deliveryOrderIndex.getOrderIndex() : null)
                .doName(safeText(order.getDoName()))
                .siName(safeText(order.getSiName()))
                .guName(safeText(order.getGuName()))
                .roadAddress(safeText(order.getRoadAddress()))
                .detailAddress(safeText(order.getDetailAddress()))
                .fullAddress(buildFullAddress(order))
                .createdAtText(formatDateTime(order.getCreatedAt()))
                .build();
    }

    private DeliveryMethodDto toDeliveryMethodDto(
            DeliveryMethod deliveryMethod,
            DeliveryOrderIndex deliveryOrderIndex
    ) {
        if (deliveryMethod == null) {
            return DeliveryMethodDto.builder()
                    .id(null)
                    .methodName("미지정")
                    .methodPrice(0)
                    .directDelivery(false)
                    .build();
        }

        Member handler = deliveryOrderIndex != null
                ? deliveryOrderIndex.getDeliveryHandler()
                : null;

        return DeliveryMethodDto.builder()
                .id(deliveryMethod.getId())
                .methodName(safeTextOrDash(deliveryMethod.getMethodName()))
                .methodPrice(deliveryMethod.getMethodPrice())
                .directDelivery(isDirectDelivery(deliveryMethod))
                .deliveryHandlerId(handler != null ? handler.getId() : null)
                .deliveryHandlerName(handler != null ? safeTextOrDash(handler.getName()) : null)
                .deliveryOrderIndex(deliveryOrderIndex != null ? deliveryOrderIndex.getOrderIndex() : null)
                .build();
    }

    private Map<String, Object> parseOptionJson(String optionJson) {
        if (optionJson == null || optionJson.isBlank()) {
            return Collections.emptyMap();
        }

        try {
            return objectMapper.readValue(
                    optionJson,
                    new TypeReference<LinkedHashMap<String, Object>>() {}
            );
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    private String pickFirstValue(Map<String, Object> map, List<String> keys) {
        if (map == null || map.isEmpty() || keys == null || keys.isEmpty()) {
            return "";
        }

        for (String key : keys) {
            if (key == null) {
                continue;
            }

            Object value = map.get(key);
            String text = safeText(value);

            if (!text.isBlank()) {
                return text;
            }
        }

        return "";
    }

    private void validateDeliveryHandler(Member deliveryHandler) {
        if (deliveryHandler == null) {
            throw new IllegalArgumentException("배송 담당자를 찾을 수 없습니다.");
        }

        if (!deliveryHandler.isEnabled()) {
            throw new IllegalArgumentException("비활성화된 배송 담당자는 선택할 수 없습니다.");
        }

        if (deliveryHandler.getTeam() == null
                || !DELIVERY_TEAM_NAME.equals(deliveryHandler.getTeam().getName())) {
            throw new IllegalArgumentException("배송팀 소속 멤버만 배송 담당자로 선택할 수 있습니다.");
        }
    }

    private boolean isDirectDelivery(DeliveryMethod deliveryMethod) {
        return deliveryMethod != null
                && DeliveryMethodAssignmentPolicy.containsKeyword(
                        deliveryMethod.getMethodName(),
                        "직배송"
                );
    }

    private boolean isVisibleDispatchStatus(OrderStatus status) {
        return status == OrderStatus.CONFIRMED
                || status == OrderStatus.PRODUCTION_DONE
                || status == OrderStatus.DISPATCH_DONE;
    }

    private boolean isDispatchCompletableStatus(OrderStatus status) {
        return status == OrderStatus.CONFIRMED
                || status == OrderStatus.PRODUCTION_DONE;
    }

    private int statusSort(OrderStatus status) {
        if (status == OrderStatus.CONFIRMED) {
            return 1;
        }

        if (status == OrderStatus.PRODUCTION_DONE) {
            return 2;
        }

        if (status == OrderStatus.DISPATCH_DONE) {
            return 3;
        }

        return 99;
    }

    private List<Long> normalizeIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }

        LinkedHashSet<Long> uniqueIds = new LinkedHashSet<>();

        for (Long id : ids) {
            if (id != null && id > 0) {
                uniqueIds.add(id);
            }
        }

        return new ArrayList<>(uniqueIds);
    }

    private String buildFullAddress(Order order) {
        if (order == null) {
            return "-";
        }

        List<String> parts = new ArrayList<>();

        addIfNotBlank(parts, order.getDoName());
        addIfNotBlank(parts, order.getSiName());
        addIfNotBlank(parts, order.getGuName());
        addIfNotBlank(parts, order.getRoadAddress());
        addIfNotBlank(parts, order.getDetailAddress());

        if (parts.isEmpty()) {
            return "-";
        }

        return String.join(" ", parts);
    }

    private void addIfNotBlank(List<String> parts, String value) {
        String text = safeText(value);

        if (!text.isBlank()) {
            parts.add(text);
        }
    }

    private String formatDateTime(LocalDateTime dateTime) {
        if (dateTime == null) {
            return "-";
        }

        return dateTime.format(DATE_TIME_FORMATTER);
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }

        for (String value : values) {
            String text = safeText(value);

            if (!text.isBlank()) {
                return text;
            }
        }

        return "";
    }

    private String safeTextOrDash(Object value) {
        String text = safeText(value);
        return text.isBlank() ? "-" : text;
    }

    private String safeText(Object value) {
        if (value == null) {
            return "";
        }

        return String.valueOf(value).trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isBlank();
    }

    private void configurePrint(Sheet sheet) {
        sheet.setFitToPage(true);
        sheet.setAutobreaks(true);
        sheet.createFreezePane(0, 3);

        sheet.setRepeatingRows(new CellRangeAddress(0, 2, -1, -1));

        PrintSetup printSetup = sheet.getPrintSetup();
        printSetup.setLandscape(true);
        printSetup.setPaperSize(PrintSetup.A4_PAPERSIZE);
        printSetup.setFitWidth((short) 1);
        printSetup.setFitHeight((short) 0);

        sheet.setMargin(PageMargin.LEFT, 0.25);
        sheet.setMargin(PageMargin.RIGHT, 0.25);
        sheet.setMargin(PageMargin.TOP, 0.45);
        sheet.setMargin(PageMargin.BOTTOM, 0.45);
    }

    private void createExcelSheet(
            Workbook workbook,
            Sheet sheet,
            List<DispatchOrderRowDto> rows,
            DispatchOrderSearchRequest request
    ) {
        CellStyle titleStyle = createTitleStyle(workbook);
        CellStyle infoStyle = createInfoStyle(workbook);
        CellStyle headerStyle = createHeaderStyle(workbook);
        CellStyle bodyStyle = createBodyStyle(workbook);
        CellStyle centerStyle = createCenterStyle(workbook);
        CellStyle memoStyle = createMemoStyle(workbook);

        int rowIndex = 0;

        Row titleRow = sheet.createRow(rowIndex++);
        titleRow.setHeightInPoints(24);

        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("출고팀 업무현황");
        titleCell.setCellStyle(titleStyle);

        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 12));

        Row infoRow = sheet.createRow(rowIndex++);
        infoRow.setHeightInPoints(18);

        Cell infoCell = infoRow.createCell(0);
        infoCell.setCellValue(buildExcelConditionText(request, rows.size()));
        infoCell.setCellStyle(infoStyle);

        sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, 12));

        Row headerRow = sheet.createRow(rowIndex++);
        headerRow.setHeightInPoints(22);

        String[] headers = {
                "오더ID",
                "상태",
                "카테고리",
                "거래처",
                "멤버",
                "품목명",
                "색상",
                "규격",
                "수량",
                "관리자 메모",
                "배송수단",
                "배송 담당자",
                "주소"
        };

        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        for (DispatchOrderRowDto dto : rows) {
            Row row = sheet.createRow(rowIndex++);
            row.setHeightInPoints(38);

            createCell(row, 0, "#" + safeText(dto.getOrderId()), centerStyle);
            createCell(row, 1, dto.getStatusLabel(), centerStyle);
            createCell(row, 2, dto.getProductCategoryName(), bodyStyle);
            createCell(row, 3, dto.getCompanyName(), bodyStyle);
            createCell(row, 4, dto.getMemberName() + " / " + dto.getMemberUsername(), bodyStyle);
            createCell(row, 5, dto.getProductName(), bodyStyle);
            createCell(row, 6, dto.getColor(), bodyStyle);
            createCell(row, 7, dto.getSizeText(), bodyStyle);
            createCell(row, 8, String.valueOf(dto.getQuantity()), centerStyle);
            createCell(row, 9, dto.getAdminMemo(), memoStyle);
            createCell(row, 10, dto.getDeliveryMethodName(), bodyStyle);
            createCell(row, 11, buildDeliveryHandlerText(dto), bodyStyle);
            createCell(row, 12, dto.getFullAddress(), memoStyle);
        }

        int[] widths = {
                10, 12, 14, 20, 20, 24, 12, 20, 8, 34, 14, 18, 38
        };

        for (int i = 0; i < widths.length; i++) {
            sheet.setColumnWidth(i, widths[i] * 256);
        }
    }

    private String buildExcelConditionText(
            DispatchOrderSearchRequest request,
            int count
    ) {
        List<String> conditions = new ArrayList<>();

        conditions.add("출력일: " + LocalDate.now());
        conditions.add("총 " + count + "건");

        if (request != null && request.getOrderDate() != null) {
            conditions.add("출고일: " + request.getOrderDate());
        }

        if (request != null && !safeText(request.getKeyword()).isBlank()) {
            conditions.add("검색어: " + request.getKeyword());
        }

        return String.join(" | ", conditions);
    }

    private String buildDeliveryHandlerText(DispatchOrderRowDto dto) {
        if (dto == null || safeText(dto.getDeliveryHandlerName()).isBlank()) {
            return "-";
        }

        if (dto.getDeliveryOrderIndex() == null) {
            return dto.getDeliveryHandlerName();
        }

        return dto.getDeliveryHandlerName() + " / 순번 " + dto.getDeliveryOrderIndex();
    }

    private void createCell(
            Row row,
            int columnIndex,
            String value,
            CellStyle style
    ) {
        Cell cell = row.createCell(columnIndex);
        cell.setCellValue(safeTextOrDash(value));
        cell.setCellStyle(style);
    }

    private CellStyle createTitleStyle(Workbook workbook) {
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 15);

        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);

        return style;
    }

    private CellStyle createInfoStyle(Workbook workbook) {
        Font font = workbook.createFont();
        font.setFontHeightInPoints((short) 9);

        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);

        return style;
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 9);
        font.setColor(IndexedColors.WHITE.getIndex());

        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_50_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);

        applyThinBorder(style);

        return style;
    }

    private CellStyle createBodyStyle(Workbook workbook) {
        Font font = workbook.createFont();
        font.setFontHeightInPoints((short) 9);

        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setWrapText(true);

        applyThinBorder(style);

        return style;
    }

    private CellStyle createCenterStyle(Workbook workbook) {
        CellStyle style = createBodyStyle(workbook);
        style.setAlignment(HorizontalAlignment.CENTER);

        return style;
    }

    private CellStyle createMemoStyle(Workbook workbook) {
        CellStyle style = createBodyStyle(workbook);
        style.setVerticalAlignment(VerticalAlignment.TOP);
        style.setWrapText(true);

        return style;
    }

    private void applyThinBorder(CellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
    }
}