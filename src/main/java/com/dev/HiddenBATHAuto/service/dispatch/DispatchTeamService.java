package com.dev.HiddenBATHAuto.service.dispatch;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
import com.dev.HiddenBATHAuto.model.task.Order;
import com.dev.HiddenBATHAuto.model.task.OrderItem;
import com.dev.HiddenBATHAuto.model.task.OrderStatus;
import com.dev.HiddenBATHAuto.model.task.Task;
import com.dev.HiddenBATHAuto.repository.caculate.DeliveryMethodRepository;
import com.dev.HiddenBATHAuto.repository.order.OrderRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.persistence.EntityManager;
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

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final EntityManager entityManager;
    private final ObjectMapper objectMapper;
    private final OrderRepository orderRepository;
    private final DeliveryMethodRepository deliveryMethodRepository;

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

        predicates.add(orderRoot.get("status").in(
                OrderStatus.CONFIRMED,
                OrderStatus.PRODUCTION_DONE,
                OrderStatus.DISPATCH_DONE
        ));

        if (normalizedRequest.getProductCategoryId() != null) {
            predicates.add(cb.equal(
                    categoryJoin.get("id"),
                    normalizedRequest.getProductCategoryId()
            ));
        }

        String standard = safeText(normalizedRequest.getStandard()).toUpperCase();
        if ("STANDARD".equals(standard)) {
            predicates.add(cb.isTrue(orderRoot.get("standard")));
        } else if ("NON_STANDARD".equals(standard)) {
            predicates.add(cb.isFalse(orderRoot.get("standard")));
        }

        if (normalizedRequest.getOrderDate() != null) {
            LocalDate orderDate = normalizedRequest.getOrderDate();
            LocalDateTime start = orderDate.atStartOfDay();
            LocalDateTime end = orderDate.plusDays(1).atStartOfDay();

            predicates.add(cb.greaterThanOrEqualTo(orderRoot.get("preferredDeliveryDate"), start));
            predicates.add(cb.lessThan(orderRoot.get("preferredDeliveryDate"), end));
        }

        if (normalizedRequest.getDeliveryMethodId() != null) {
            predicates.add(cb.equal(
                    deliveryMethodJoin.get("id"),
                    normalizedRequest.getDeliveryMethodId()
            ));
        }

        addKeywordPredicate(
                cb,
                predicates,
                normalizedRequest,
                requestedByJoin,
                companyJoin
        );

        addAddressPredicate(cb, predicates, orderRoot, normalizedRequest);

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
            Member loginMember
    ) {
        validateDispatchTeamMember(loginMember);

        if (orderId == null) {
            throw new IllegalArgumentException("주문 ID가 없습니다.");
        }

        if (deliveryMethodId == null) {
            throw new IllegalArgumentException("배송수단 ID가 없습니다.");
        }

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다."));

        if (!isVisibleDispatchStatus(order.getStatus())) {
            throw new IllegalStateException("출고관리 대상 상태가 아닙니다.");
        }

        DeliveryMethod deliveryMethod = deliveryMethodRepository.findById(deliveryMethodId)
                .orElseThrow(() -> new IllegalArgumentException("배송수단을 찾을 수 없습니다."));

        order.setDeliveryMethod(deliveryMethod);
        order.setUpdatedAt(LocalDateTime.now());

        orderRepository.save(order);

        return toDeliveryMethodDto(deliveryMethod);
    }

    public void validateDispatchTeamMember(Member loginMember) {
        if (loginMember == null
                || loginMember.getTeam() == null
                || !"출고팀".equals(loginMember.getTeam().getName())) {
            throw new AccessDeniedException("출고팀만 접근할 수 있습니다.");
        }
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
                .doName(safeText(order.getDoName()))
                .siName(safeText(order.getSiName()))
                .guName(safeText(order.getGuName()))
                .roadAddress(safeText(order.getRoadAddress()))
                .detailAddress(safeText(order.getDetailAddress()))
                .fullAddress(buildFullAddress(order))
                .createdAtText(formatDateTime(order.getCreatedAt()))
                .build();
    }

    private DeliveryMethodDto toDeliveryMethodDto(DeliveryMethod deliveryMethod) {
        if (deliveryMethod == null) {
            return DeliveryMethodDto.builder()
                    .id(null)
                    .methodName("미지정")
                    .methodPrice(0)
                    .build();
        }

        return DeliveryMethodDto.builder()
                .id(deliveryMethod.getId())
                .methodName(safeTextOrDash(deliveryMethod.getMethodName()))
                .methodPrice(deliveryMethod.getMethodPrice())
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
}