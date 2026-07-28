package com.dev.HiddenBATHAuto.service.team.delivery;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

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
import org.springframework.util.StringUtils;

import com.dev.HiddenBATHAuto.dto.delivery.DeliveryStatementLayoutDtos.LayoutRequest;
import com.dev.HiddenBATHAuto.dto.delivery.DeliveryStatementLayoutDtos.LayoutResponse;
import com.dev.HiddenBATHAuto.dto.delivery.DeliveryStatementLayoutDtos.StatementItemDto;
import com.dev.HiddenBATHAuto.dto.delivery.DeliveryStatementLayoutDtos.StatementPageDto;
import com.dev.HiddenBATHAuto.model.auth.Company;
import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.caculate.DeliveryMethod;
import com.dev.HiddenBATHAuto.model.task.DeliveryOrderIndex;
import com.dev.HiddenBATHAuto.model.task.Order;
import com.dev.HiddenBATHAuto.model.task.OrderItem;
import com.dev.HiddenBATHAuto.model.task.OrderStatus;
import com.dev.HiddenBATHAuto.model.task.Task;
import com.dev.HiddenBATHAuto.repository.order.DeliveryRouteQueryRepository;
import com.dev.HiddenBATHAuto.repository.order.OrderRepository;
import com.dev.HiddenBATHAuto.service.order.DeliveryMethodAssignmentPolicy;
import com.dev.HiddenBATHAuto.utils.DeliveryAddressNormalizationUtil;
import com.dev.HiddenBATHAuto.utils.DeliveryAddressNormalizationUtil.AddressValue;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DeliveryStatementLayoutService {

    public static final String LAYOUT_HORIZONTAL = "HORIZONTAL";
    public static final String LAYOUT_VERTICAL = "VERTICAL";

    public static final String STATEMENT_SITE = "SITE";
    public static final String STATEMENT_PARCEL = "PARCEL";

    private static final String SITE_LABEL = "현장명세서";
    private static final String PARCEL_LABEL = "택배명세서";

    private static final int HORIZONTAL_ITEMS_PER_PAGE = 8;
    private static final int VERTICAL_ITEMS_PER_PAGE = 5;

    private static final int COPY_COLUMN_COUNT = 8;
    private static final int HORIZONTAL_SEPARATOR_COLUMN = 8;
    private static final int HORIZONTAL_SECOND_COPY_START_COLUMN = 9;
    private static final int VERTICAL_SEPARATOR_ROW_HEIGHT = 8;

    private static final String DELIVERY_TEAM_NAME = "배송팀";

    private static final List<OrderStatus> DELIVERY_ROUTE_VISIBLE_STATUSES = List.of(
            OrderStatus.CONFIRMED,
            OrderStatus.PRODUCTION_DONE,
            OrderStatus.DISPATCH_DONE,
            OrderStatus.DELIVERY_DONE
    );

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    private static final DateTimeFormatter DATE_WITH_DAY_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd (E)", Locale.KOREAN);

    private final OrderRepository orderRepository;
    private final DeliveryRouteQueryRepository deliveryRouteQueryRepository;
    private final ObjectMapper objectMapper;

    public String normalizeLayoutType(String layoutType) {
        String normalized = normalizeCode(layoutType);

        if (LAYOUT_HORIZONTAL.equals(normalized) || LAYOUT_VERTICAL.equals(normalized)) {
            return normalized;
        }

        throw new IllegalArgumentException("명세서 레이아웃 구분이 올바르지 않습니다.(HORIZONTAL/VERTICAL)");
    }

    public String normalizeStatementType(String statementType) {
        String normalized = normalizeCode(statementType);

        if (STATEMENT_SITE.equals(normalized) || STATEMENT_PARCEL.equals(normalized)) {
            return normalized;
        }

        throw new IllegalArgumentException("명세서 종류가 올바르지 않습니다.(SITE/PARCEL)");
    }

    public String statementTypeLabel(String statementType) {
        return STATEMENT_PARCEL.equals(normalizeStatementType(statementType))
                ? PARCEL_LABEL
                : SITE_LABEL;
    }

    /**
     * 출고팀 선택형 명세서입니다.
     *
     * 묶음 기준:
     * - 동일 업체
     * - 동일 실제 배송지(site 주소 우선)
     * - 동일 배송수단
     * - 동일 배송일
     *
     * 현장명세서는 방문/현장배송/화물, 택배명세서는 택배만 포함합니다.
     */
    @Transactional(readOnly = true)
    public LayoutResponse buildLayoutResponse(
            LayoutRequest request,
            Member loginMember
    ) {
        validateRequest(request, loginMember);

        String layoutType = normalizeLayoutType(request.getLayoutType());
        String statementType = normalizeStatementType(request.getStatementType());
        List<Long> requestedOrderIds = normalizeOrderIds(request.getOrderIds());
        List<Order> requestedOrders = loadOrdersInRequestedOrder(requestedOrderIds);

        LocalDate statementDate = requestedOrders.stream()
                .map(this::resolveDeliveryDate)
                .filter(Objects::nonNull)
                .findFirst()
                .orElseGet(this::today);

        return buildLayoutResponseFromOrders(
                layoutType,
                statementType,
                requestedOrders,
                statementDate,
                StatementSource.DISPATCH_SELECTION,
                Map.of()
        );
    }

    /**
     * 배송팀 개인 화면 전용 명세서입니다.
     * 로그인한 멤버의 DeliveryOrderIndex에서 선택 날짜의 주문만 읽습니다.
     * 배송팀 현장명세서는 현장배송/화물만 허용하며 택배명세서는 차단합니다.
     */
    @Transactional(readOnly = true)
    public LayoutResponse buildLayoutResponseForDeliveryDate(
            LocalDate deliveryDate,
            String layoutType,
            String statementType,
            Member loginMember
    ) {
        validateDeliveryRouteMember(loginMember);

        if (deliveryDate == null) {
            throw new IllegalArgumentException("명세서를 출력할 배송일이 없습니다.");
        }

        String normalizedLayoutType = normalizeLayoutType(layoutType);
        String normalizedStatementType = normalizeStatementType(statementType);

        if (STATEMENT_PARCEL.equals(normalizedStatementType)) {
            throw new IllegalArgumentException("배송팀 화면에서는 택배명세서를 출력하지 않습니다.");
        }

        List<Order> requestedOrders = loadOrdersForDeliveryMemberDate(
                loginMember,
                deliveryDate
        );

        if (requestedOrders.isEmpty()) {
            throw new IllegalArgumentException(
                    deliveryDate + "에 현재 담당자의 배송순서 주문이 없습니다."
            );
        }

        return buildLayoutResponseFromOrders(
                normalizedLayoutType,
                normalizedStatementType,
                requestedOrders,
                deliveryDate,
                StatementSource.DELIVERY_MEMBER,
                Map.of()
        );
    }

    /**
     * 배송팀 팀장 전용 전체 현장명세서입니다.
     * TeamStatementOrderRef는 서버가 DeliveryOrderIndex를 조회해서 만든 값만 전달해야 합니다.
     * 담당자 ID를 첫 번째 묶음 키로 사용하므로 서로 다른 배송직원의 주문은 절대 합쳐지지 않습니다.
     */
    @Transactional(readOnly = true)
    public LayoutResponse buildLayoutResponseForTeamSite(
            LocalDate deliveryDate,
            String layoutType,
            List<TeamStatementOrderRef> orderRefs
    ) {
        if (deliveryDate == null) {
            throw new IllegalArgumentException("배송팀 현장명세서로 출력할 배송일이 없습니다.");
        }

        String normalizedLayoutType = normalizeLayoutType(layoutType);
        List<TeamStatementOrderRef> normalizedRefs = normalizeTeamStatementOrderRefs(orderRefs);

        if (normalizedRefs.isEmpty()) {
            throw new IllegalArgumentException(
                    deliveryDate + " 배송팀 전체 현장명세서 대상 주문이 없습니다."
            );
        }

        List<Long> orderIds = normalizedRefs.stream()
                .map(TeamStatementOrderRef::orderId)
                .toList();
        List<Order> requestedOrders = loadOrdersInRequestedOrder(orderIds);
        Map<Long, TeamStatementOrderRef> refByOrderId = normalizedRefs.stream()
                .collect(Collectors.toMap(
                        TeamStatementOrderRef::orderId,
                        ref -> ref,
                        (a, b) -> a,
                        LinkedHashMap::new
                ));

        return buildLayoutResponseFromOrders(
                normalizedLayoutType,
                STATEMENT_SITE,
                requestedOrders,
                deliveryDate,
                StatementSource.DELIVERY_TEAM,
                refByOrderId
        );
    }

    @Transactional(readOnly = true)
    public int countTeamSiteGroups(
            LocalDate deliveryDate,
            List<TeamStatementOrderRef> orderRefs
    ) {
        if (deliveryDate == null) {
            throw new IllegalArgumentException("배송일이 없습니다.");
        }

        List<TeamStatementOrderRef> normalizedRefs = normalizeTeamStatementOrderRefs(orderRefs);

        if (normalizedRefs.isEmpty()) {
            return 0;
        }

        List<Long> orderIds = normalizedRefs.stream()
                .map(TeamStatementOrderRef::orderId)
                .toList();
        List<Order> orders = loadOrdersInRequestedOrder(orderIds);
        Map<Long, TeamStatementOrderRef> refByOrderId = normalizedRefs.stream()
                .collect(Collectors.toMap(
                        TeamStatementOrderRef::orderId,
                        ref -> ref,
                        (a, b) -> a,
                        LinkedHashMap::new
                ));

        List<Order> includedOrders = filterOrdersByStatementType(
                orders,
                STATEMENT_SITE,
                StatementSource.DELIVERY_TEAM
        );

        return groupOrdersByStatementCriteria(
                includedOrders,
                STATEMENT_SITE,
                deliveryDate,
                StatementSource.DELIVERY_TEAM,
                refByOrderId
        ).size();
    }

    private LayoutResponse buildLayoutResponseFromOrders(
            String layoutType,
            String statementType,
            List<Order> requestedOrders,
            LocalDate statementDate,
            StatementSource source,
            Map<Long, TeamStatementOrderRef> teamRefByOrderId
    ) {
        List<Order> safeRequestedOrders = requestedOrders == null
                ? List.of()
                : requestedOrders.stream()
                        .filter(Objects::nonNull)
                        .toList();

        if (safeRequestedOrders.isEmpty()) {
            throw new IllegalArgumentException("명세서로 출력할 주문이 없습니다.");
        }

        List<Order> includedOrders = filterOrdersByStatementType(
                safeRequestedOrders,
                statementType,
                source
        );

        if (includedOrders.isEmpty()) {
            throw new IllegalArgumentException(buildNoMatchingOrderMessage(statementType, source));
        }

        List<StatementGroup> groups = groupOrdersByStatementCriteria(
                includedOrders,
                statementType,
                statementDate,
                source,
                teamRefByOrderId
        );
        List<StatementPageDto> pages = splitGroupsIntoPages(
                groups,
                layoutType,
                statementDate
        );

        return LayoutResponse.builder()
                .layoutType(layoutType)
                .statementType(statementType)
                .statementTypeLabel(statementTypeLabel(statementType))
                .generatedDateText(formatDateWithDay(statementDate))
                .requestedOrderCount(safeRequestedOrders.size())
                .includedOrderCount(includedOrders.size())
                .excludedOrderCount(Math.max(
                        0,
                        safeRequestedOrders.size() - includedOrders.size()
                ))
                .pages(pages)
                .build();
    }

    @Transactional(readOnly = true)
    public byte[] buildLayoutExcel(
            LayoutRequest request,
            Member loginMember
    ) {
        LayoutResponse response = buildLayoutResponse(request, loginMember);
        return buildLayoutExcelFromResponse(response);
    }

    @Transactional(readOnly = true)
    public byte[] buildLayoutExcelForDeliveryDate(
            LocalDate deliveryDate,
            String layoutType,
            String statementType,
            Member loginMember
    ) {
        LayoutResponse response = buildLayoutResponseForDeliveryDate(
                deliveryDate,
                layoutType,
                statementType,
                loginMember
        );

        return buildLayoutExcelFromResponse(response);
    }

    @Transactional(readOnly = true)
    public byte[] buildLayoutExcelForTeamSite(
            LocalDate deliveryDate,
            String layoutType,
            List<TeamStatementOrderRef> orderRefs
    ) {
        LayoutResponse response = buildLayoutResponseForTeamSite(
                deliveryDate,
                layoutType,
                orderRefs
        );
        return buildLayoutExcelFromResponse(response);
    }

    private byte[] buildLayoutExcelFromResponse(LayoutResponse response) {
        if (response == null
                || response.getPages() == null
                || response.getPages().isEmpty()) {
            throw new IllegalArgumentException("엑셀로 생성할 명세서 데이터가 없습니다.");
        }

        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Map<String, CellStyle> styles = createExcelStyles(workbook);
            String layoutType = normalizeLayoutType(response.getLayoutType());

            for (int i = 0; i < response.getPages().size(); i++) {
                StatementPageDto page = response.getPages().get(i);
                Sheet sheet = workbook.createSheet(buildSheetName(i + 1, page));

                configureStatementSheet(sheet, layoutType);

                int lastRow;
                int lastColumn;

                if (LAYOUT_HORIZONTAL.equals(layoutType)) {
                    lastRow = writeHorizontalSheet(sheet, page, styles);
                    lastColumn = HORIZONTAL_SECOND_COPY_START_COLUMN + COPY_COLUMN_COUNT - 1;
                } else {
                    lastRow = writeVerticalSheet(sheet, page, styles);
                    lastColumn = COPY_COLUMN_COUNT - 1;
                }

                workbook.setPrintArea(
                        workbook.getSheetIndex(sheet),
                        0,
                        lastColumn,
                        0,
                        lastRow
                );
            }

            workbook.setActiveSheet(0);
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("명세서 엑셀 파일 생성 중 오류가 발생했습니다.", e);
        }
    }

    private void validateRequest(LayoutRequest request, Member loginMember) {
        if (loginMember == null) {
            throw new AccessDeniedException("로그인 사용자 정보를 확인할 수 없습니다.");
        }

        if (request == null) {
            throw new IllegalArgumentException("명세서 생성 요청이 없습니다.");
        }

        normalizeLayoutType(request.getLayoutType());
        normalizeStatementType(request.getStatementType());

        if (normalizeOrderIds(request.getOrderIds()).isEmpty()) {
            throw new IllegalArgumentException("명세서로 출력할 주문을 하나 이상 선택해 주세요.");
        }
    }

    private List<Long> normalizeOrderIds(List<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return List.of();
        }

        LinkedHashSet<Long> normalized = new LinkedHashSet<>();

        for (Long orderId : orderIds) {
            if (orderId != null && orderId > 0) {
                normalized.add(orderId);
            }
        }

        return new ArrayList<>(normalized);
    }

    private List<TeamStatementOrderRef> normalizeTeamStatementOrderRefs(
            List<TeamStatementOrderRef> orderRefs
    ) {
        if (orderRefs == null || orderRefs.isEmpty()) {
            return List.of();
        }

        LinkedHashMap<Long, TeamStatementOrderRef> uniqueByOrderId = new LinkedHashMap<>();

        for (TeamStatementOrderRef ref : orderRefs) {
            if (ref == null
                    || ref.deliveryHandlerId() == null
                    || ref.deliveryHandlerId() <= 0
                    || ref.orderId() == null
                    || ref.orderId() <= 0) {
                continue;
            }

            uniqueByOrderId.putIfAbsent(ref.orderId(), ref);
        }

        return uniqueByOrderId.values().stream()
                .sorted(Comparator
                        .comparingLong((TeamStatementOrderRef ref) -> ref.deliveryHandlerId())
                        .thenComparingInt(TeamStatementOrderRef::orderIndex)
                        .thenComparingLong(TeamStatementOrderRef::orderId))
                .toList();
    }

    private List<Order> loadOrdersForDeliveryMemberDate(
            Member loginMember,
            LocalDate deliveryDate
    ) {
        if (loginMember == null || loginMember.getId() == null) {
            throw new AccessDeniedException("로그인 배송 담당자 정보를 확인할 수 없습니다.");
        }

        List<Long> orderIds = deliveryRouteQueryRepository.findRouteRows(
                        loginMember.getId(),
                        deliveryDate,
                        DELIVERY_ROUTE_VISIBLE_STATUSES
                )
                .stream()
                .filter(Objects::nonNull)
                .filter(row -> row.getOrder() != null && row.getOrder().getId() != null)
                .sorted(Comparator
                        .comparingInt(DeliveryOrderIndex::getOrderIndex)
                        .thenComparingLong(this::safeOrderId))
                .map(row -> row.getOrder().getId())
                .distinct()
                .toList();

        return orderIds.isEmpty()
                ? List.of()
                : loadOrdersInRequestedOrder(orderIds);
    }

    private void validateDeliveryRouteMember(Member member) {
        if (member == null) {
            throw new AccessDeniedException("로그인 사용자 정보를 확인할 수 없습니다.");
        }

        if (member.getTeam() == null
                || !DELIVERY_TEAM_NAME.equals(member.getTeam().getName())) {
            throw new AccessDeniedException("배송팀만 날짜 전체 명세서를 출력할 수 있습니다.");
        }
    }

    private List<Order> loadOrdersInRequestedOrder(List<Long> orderIds) {
        Map<Long, Order> foundMap = new LinkedHashMap<>();

        for (Order order : orderRepository.findAllForDeliveryStatementByIds(orderIds)) {
            if (order != null && order.getId() != null) {
                foundMap.put(order.getId(), order);
            }
        }

        List<Long> missingOrderIds = orderIds.stream()
                .filter(orderId -> !foundMap.containsKey(orderId))
                .toList();

        if (!missingOrderIds.isEmpty()) {
            throw new IllegalArgumentException(
                    "선택한 주문 중 존재하지 않는 주문이 있습니다. orderIds=" + missingOrderIds
            );
        }

        List<Order> ordered = new ArrayList<>();
        for (Long orderId : orderIds) {
            ordered.add(foundMap.get(orderId));
        }

        return ordered;
    }

    private List<Order> filterOrdersByStatementType(
            List<Order> orders,
            String statementType,
            StatementSource source
    ) {
        return orders.stream()
                .filter(order -> isAllowedDeliveryMethod(order, statementType, source))
                .toList();
    }

    private boolean isAllowedDeliveryMethod(
            Order order,
            String statementType,
            StatementSource source
    ) {
        if (order == null || order.getDeliveryMethod() == null) {
            return false;
        }

        DeliveryMethod deliveryMethod = order.getDeliveryMethod();
        String methodName = deliveryMethod.getMethodName();

        if (STATEMENT_PARCEL.equals(statementType)) {
            return source == StatementSource.DISPATCH_SELECTION
                    && DeliveryMethodAssignmentPolicy.containsKeyword(methodName, "택배");
        }

        boolean siteOrFreight = DeliveryMethodAssignmentPolicy.containsKeyword(
                methodName,
                "현장배송"
        ) || DeliveryMethodAssignmentPolicy.containsKeyword(
                methodName,
                "화물"
        );

        if (source == StatementSource.DISPATCH_SELECTION) {
            return siteOrFreight || DeliveryMethodAssignmentPolicy.containsKeyword(
                    methodName,
                    "방문"
            );
        }

        return siteOrFreight;
    }

    private String buildNoMatchingOrderMessage(
            String statementType,
            StatementSource source
    ) {
        if (STATEMENT_PARCEL.equals(statementType)) {
            return source == StatementSource.DISPATCH_SELECTION
                    ? "선택한 주문 중 배송수단이 택배인 주문이 없습니다."
                    : "배송팀 화면에서는 택배명세서를 출력하지 않습니다.";
        }

        return source == StatementSource.DISPATCH_SELECTION
                ? "선택한 주문 중 배송수단이 방문, 현장배송 또는 화물인 주문이 없습니다."
                : "해당 날짜의 배송순서 중 배송수단이 현장배송 또는 화물인 주문이 없습니다.";
    }

    /**
     * 명세서 묶음 기준을 화면별로 통일합니다.
     *
     * 출고팀/배송팀 개인:
     * - 동일 업체 + 동일 실제 배송지 + 동일 배송수단 + 동일 배송일
     *
     * 배송팀 팀장:
     * - 동일 배송직원 + 동일 업체 + 동일 실제 배송지 + 동일 배송수단 + 동일 배송일
     */
    private List<StatementGroup> groupOrdersByStatementCriteria(
            List<Order> orders,
            String statementType,
            LocalDate statementDate,
            StatementSource source,
            Map<Long, TeamStatementOrderRef> teamRefByOrderId
    ) {
        LinkedHashMap<StatementGroupKey, List<Order>> grouped = new LinkedHashMap<>();

        for (Order order : orders) {
            if (order == null || order.getId() == null) {
                continue;
            }

            StatementGroupKey key = buildStatementGroupKey(
                    order,
                    statementDate,
                    source,
                    teamRefByOrderId
            );
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(order);
        }

        List<StatementGroup> result = new ArrayList<>();

        for (Map.Entry<StatementGroupKey, List<Order>> entry : grouped.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                result.add(createStatementGroup(
                        entry.getValue(),
                        statementType,
                        entry.getKey(),
                        source,
                        teamRefByOrderId
                ));
            }
        }

        return result;
    }

    private StatementGroupKey buildStatementGroupKey(
            Order order,
            LocalDate statementDate,
            StatementSource source,
            Map<Long, TeamStatementOrderRef> teamRefByOrderId
    ) {
        String handlerKey = "";

        if (source == StatementSource.DELIVERY_TEAM) {
            TeamStatementOrderRef ref = teamRefByOrderId.get(order.getId());

            if (ref == null || ref.deliveryHandlerId() == null) {
                throw new IllegalStateException(
                        "배송팀 전체 명세서 담당자 정보가 없습니다. orderId=" + order.getId()
                );
            }

            handlerKey = "HANDLER:" + ref.deliveryHandlerId();
        }

        String companyKey = resolveCompanyGroupingKey(order);
        String addressKey = resolveAddressGroupingKey(order);
        String methodKey = resolveMethodGroupingKey(order);
        LocalDate deliveryDate = source == StatementSource.DISPATCH_SELECTION
                ? resolveDeliveryDate(order)
                : statementDate;
        String dateKey = deliveryDate != null
                ? deliveryDate.toString()
                : "MISSING-DATE-ORDER:" + order.getId();

        return new StatementGroupKey(
                handlerKey,
                companyKey,
                addressKey,
                methodKey,
                dateKey
        );
    }

    private String resolveCompanyGroupingKey(Order order) {
        Company company = resolveCompany(order);

        if (company != null && company.getId() != null) {
            return "COMPANY:" + company.getId();
        }

        if (company != null && StringUtils.hasText(company.getCompanyName())) {
            return "COMPANY-NAME:" + normalizeGroupingText(company.getCompanyName());
        }

        return "MISSING-COMPANY-ORDER:" + safeLong(order != null ? order.getId() : null);
    }

    private String resolveAddressGroupingKey(Order order) {
        AddressValue address = resolveStatementAddressValue(order);

        if (StringUtils.hasText(address.key())) {
            return address.key();
        }

        return "MISSING-ADDRESS-ORDER:" + safeLong(order != null ? order.getId() : null);
    }

    private String resolveMethodGroupingKey(Order order) {
        String methodName = order != null && order.getDeliveryMethod() != null
                ? order.getDeliveryMethod().getMethodName()
                : "";
        String normalized = DeliveryMethodAssignmentPolicy.normalize(methodName);

        return normalized.isBlank()
                ? "MISSING-METHOD-ORDER:" + safeLong(order != null ? order.getId() : null)
                : normalized;
    }

    private String normalizeGroupingText(String value) {
        return Normalizer.normalize(
                safeText(value),
                Normalizer.Form.NFKC
        )
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", "")
                .trim();
    }

    private StatementGroup createStatementGroup(
            List<Order> orders,
            String statementType,
            StatementGroupKey groupKey,
            StatementSource source,
            Map<Long, TeamStatementOrderRef> teamRefByOrderId
    ) {
        Order representative = representativeForHeader(orders);
        Company company = resolveCompany(representative);
        RecipientData recipient = resolveRecipient(representative);
        AddressData address = resolveStatementAddress(representative);

        StatementGroup group = new StatementGroup();
        group.taskId = resolveSingleTaskId(orders);
        group.documentType = statementType;
        group.documentTypeLabel = statementTypeLabel(statementType);
        group.companyName = company != null
                ? safeTextOrDash(company.getCompanyName())
                : "-";
        group.recipientName = safeTextOrDash(recipient.name());
        group.recipientPhone = safeTextOrDash(recipient.phone());
        group.postalCode = safeText(address.postalCode());
        group.addressText = safeTextOrDash(address.addressText());
        group.deliveryDateTexts.add(formatStatementGroupDate(groupKey.deliveryDateKey()));

        if (source == StatementSource.DELIVERY_TEAM) {
            TeamStatementOrderRef ref = teamRefByOrderId.get(representative.getId());
            group.deliveryHandlerId = ref != null ? ref.deliveryHandlerId() : null;
            group.deliveryHandlerName = ref != null ? safeText(ref.deliveryHandlerName()) : "";
        }

        for (Order order : orders) {
            addOrderToStatementGroup(group, order, statementType);
        }

        return group;
    }

    private Long resolveSingleTaskId(List<Order> orders) {
        LinkedHashSet<Long> taskIds = orders.stream()
                .map(Order::getTask)
                .filter(Objects::nonNull)
                .map(Task::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        return taskIds.size() == 1 ? taskIds.iterator().next() : null;
    }

    private Company resolveCompany(Order order) {
        if (order == null
                || order.getTask() == null
                || order.getTask().getRequestedBy() == null) {
            return null;
        }

        return order.getTask().getRequestedBy().getCompany();
    }

    private Order representativeForHeader(List<Order> orders) {
        return orders.stream()
                .filter(order -> hasDeliveryAddress(order) || hasExplicitRecipient(order))
                .findFirst()
                .orElse(orders.get(0));
    }

    private void addOrderToStatementGroup(
            StatementGroup group,
            Order order,
            String statementType
    ) {
        group.orderIds.add(order.getId());
        group.deliveryMethodNames.add(resolveDeliveryMethodName(order));
        group.items.add(toStatementItem(order, group.items.size() + 1));
    }

    private StatementItemDto toStatementItem(Order order, int no) {
        OrderItem orderItem = order.getOrderItem();
        Map<String, Object> optionMap = parseOptionJson(
                orderItem != null ? orderItem.getOptionJson() : null
        );

        String productName = firstNonBlank(
                orderItem != null ? orderItem.getProductName() : null,
                "-"
        );

        String sizeText = firstNonBlank(
                pickFirstValue(optionMap, List.of(
                        "사이즈",
                        "규격",
                        "size",
                        "Size",
                        "제품사이즈"
                )),
                "-"
        );

        String color = firstNonBlank(
                pickFirstValue(optionMap, List.of(
                        "색상",
                        "컬러",
                        "color",
                        "Color",
                        "제품색상"
                )),
                "-"
        );

        return StatementItemDto.builder()
                .no(no)
                .orderId(order.getId())
                .productName(productName)
                .sizeText(sizeText)
                .color(color)
                .quantity(order.getQuantity())
                .memo(safeText(order.getAdminMemo()))
                .build();
    }

    private List<StatementPageDto> splitGroupsIntoPages(
            List<StatementGroup> groups,
            String layoutType,
            LocalDate statementDate
    ) {
        int itemsPerPage = LAYOUT_HORIZONTAL.equals(layoutType)
                ? HORIZONTAL_ITEMS_PER_PAGE
                : VERTICAL_ITEMS_PER_PAGE;
        List<StatementPageDto> pages = new ArrayList<>();
        int sequence = 1;

        for (StatementGroup group : groups) {
            int pageCount = Math.max(
                    1,
                    (int) Math.ceil(group.items.size() / (double) itemsPerPage)
            );

            for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
                int fromIndex = Math.min(pageIndex * itemsPerPage, group.items.size());
                int toIndex = Math.min(fromIndex + itemsPerPage, group.items.size());
                List<StatementItemDto> pageItems = group.items.isEmpty()
                        ? List.of()
                        : new ArrayList<>(group.items.subList(fromIndex, toIndex));

                pages.add(toStatementPageDto(
                        group,
                        pageItems,
                        sequence++,
                        pageIndex + 1,
                        pageCount,
                        statementDate
                ));
            }
        }

        return pages;
    }

    private StatementPageDto toStatementPageDto(
            StatementGroup group,
            List<StatementItemDto> pageItems,
            int sequence,
            int pageNumber,
            int pageCount,
            LocalDate statementDate
    ) {
        boolean parcel = STATEMENT_PARCEL.equals(group.documentType);
        LocalDate effectiveStatementDate = statementDate != null
                ? statementDate
                : today();
        String dateText = joinOrDash(group.deliveryDateTexts, ", ");

        if ("-".equals(dateText)) {
            dateText = formatDateWithDay(effectiveStatementDate);
        }

        return StatementPageDto.builder()
                .sequence(sequence)
                .pageNumber(pageNumber)
                .pageCount(pageCount)
                .lastPage(pageNumber == pageCount)
                .taskId(group.taskId)
                .documentType(group.documentType)
                .documentTypeLabel(group.documentTypeLabel)
                .companyName(group.companyName)
                .orderIdsText(group.orderIds.stream()
                        .map(orderId -> "#" + orderId)
                        .collect(Collectors.joining(", ")))
                .dateLabel(parcel ? "발송일" : "출고일")
                .dateText(dateText)
                .recipientName(group.recipientName)
                .recipientPhone(group.recipientPhone)
                .postalCode(group.postalCode)
                .addressText(group.addressText)
                .deliveryMethodName(joinOrDash(group.deliveryMethodNames, ", "))
                .trackingNumber("")
                .freightType("")
                .packingMethod("")
                .managerName(parcel ? "히든바스" : "")
                .acceptanceText(parcel ? "" : "위 품목을 이상없이 출고 인수 하였습니다.")
                .signatureText(parcel ? "" : "확인 : ____________________ (서명 또는 인)")
                .items(pageItems)
                .build();
    }

    private RecipientData resolveRecipient(Order order) {
        Task task = order.getTask();
        Member requestedBy = task != null ? task.getRequestedBy() : null;

        String recipientName = firstNonBlank(
                order.getOrdererName(),
                requestedBy != null ? requestedBy.getName() : null,
                "-"
        );

        String recipientPhone = firstNonBlank(
                order.getOrdererPhone(),
                requestedBy != null ? requestedBy.getPhone() : null,
                "-"
        );

        return new RecipientData(recipientName, recipientPhone);
    }

    /**
     * 화면 목록과 동일하게 site 주소의 의미 있는 본문 값이 하나라도 있으면 site 주소를 우선합니다.
     * 우편번호만 존재하는 경우에는 일반 주소를 가리는 값으로 사용하지 않습니다.
     */
    private AddressData resolveStatementAddress(Order order) {
        AddressValue value = resolveStatementAddressValue(order);
        return new AddressData(
                safeText(value.zipCode()),
                safeTextOrDash(value.display())
        );
    }

    private AddressValue resolveStatementAddressValue(Order order) {
        if (order == null) {
            return DeliveryAddressNormalizationUtil.build("", "", "", "", "", "");
        }

        boolean useSiteAddress = DeliveryAddressNormalizationUtil.hasAnyMeaningfulAddressText(
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
            );
        }

        return DeliveryAddressNormalizationUtil.build(
                order.getZipCode(),
                order.getDoName(),
                order.getSiName(),
                order.getGuName(),
                order.getRoadAddress(),
                order.getDetailAddress()
        );
    }

    private boolean hasDeliveryAddress(Order order) {
        return order != null && !"-".equals(resolveStatementAddress(order).addressText());
    }

    private boolean hasExplicitRecipient(Order order) {
        return order != null && hasAnyText(order.getOrdererName(), order.getOrdererPhone());
    }

    private String formatStatementGroupDate(String deliveryDateKey) {
        if (!StringUtils.hasText(deliveryDateKey)
                || deliveryDateKey.startsWith("MISSING-DATE-ORDER:")) {
            return "-";
        }

        try {
            return formatDateWithDay(LocalDate.parse(deliveryDateKey));
        } catch (Exception ignored) {
            return "-";
        }
    }

    private String resolveDeliveryDateText(Order order) {
        if (order == null || order.getPreferredDeliveryDate() == null) {
            return "-";
        }

        return formatDateWithDay(order.getPreferredDeliveryDate().toLocalDate());
    }

    private String resolveDeliveryMethodName(Order order) {
        return order != null && order.getDeliveryMethod() != null
                ? safeTextOrDash(order.getDeliveryMethod().getMethodName())
                : "미지정";
    }


    private LocalDate resolveDeliveryDate(Order order) {
        return order != null && order.getPreferredDeliveryDate() != null
                ? order.getPreferredDeliveryDate().toLocalDate()
                : null;
    }

    private long safeOrderId(DeliveryOrderIndex index) {
        return index == null
                || index.getOrder() == null
                || index.getOrder().getId() == null
                ? Long.MAX_VALUE
                : index.getOrder().getId();
    }

    private long safeLong(Long value) {
        return value == null ? Long.MAX_VALUE : value;
    }

    private LocalDate today() {
        return LocalDate.now(KOREA_ZONE);
    }

    private String formatDateWithDay(LocalDate date) {
        return date != null ? date.format(DATE_WITH_DAY_FORMATTER) : "-";
    }

    private Map<String, Object> parseOptionJson(String optionJson) {
        if (optionJson == null || optionJson.isBlank()) {
            return Collections.emptyMap();
        }

        try {
            return objectMapper.readValue(
                    optionJson,
                    new TypeReference<LinkedHashMap<String, Object>>() {
                    }
            );
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    private String pickFirstValue(Map<String, Object> optionMap, List<String> keys) {
        if (optionMap == null || optionMap.isEmpty() || keys == null) {
            return "";
        }

        for (String key : keys) {
            String value = safeText(optionMap.get(key));
            if (!value.isBlank()) {
                return value;
            }
        }

        return "";
    }

    private int writeHorizontalSheet(
            Sheet sheet,
            StatementPageDto page,
            Map<String, CellStyle> styles
    ) {
        configureHorizontalColumnWidths(sheet);

        int firstLastRow = writeStatementCopy(
                sheet,
                0,
                0,
                page,
                "보관용",
                HORIZONTAL_ITEMS_PER_PAGE,
                styles,
                LAYOUT_HORIZONTAL
        );

        int secondLastRow = writeStatementCopy(
                sheet,
                0,
                HORIZONTAL_SECOND_COPY_START_COLUMN,
                page,
                "고객용",
                HORIZONTAL_ITEMS_PER_PAGE,
                styles,
                LAYOUT_HORIZONTAL
        );

        int lastRow = Math.max(firstLastRow, secondLastRow);
        applyVerticalCutLine(
                sheet,
                HORIZONTAL_SEPARATOR_COLUMN,
                0,
                lastRow,
                styles.get("cutVertical")
        );
        return lastRow;
    }

    private int writeVerticalSheet(
            Sheet sheet,
            StatementPageDto page,
            Map<String, CellStyle> styles
    ) {
        configureVerticalColumnWidths(sheet);

        int firstLastRow = writeStatementCopy(
                sheet,
                0,
                0,
                page,
                "보관용",
                VERTICAL_ITEMS_PER_PAGE,
                styles,
                LAYOUT_VERTICAL
        );

        int separatorRowIndex = firstLastRow + 1;
        applyHorizontalCutLine(
                sheet,
                separatorRowIndex,
                0,
                COPY_COLUMN_COUNT - 1,
                styles.get("cutHorizontal")
        );

        return writeStatementCopy(
                sheet,
                separatorRowIndex + 1,
                0,
                page,
                "고객용",
                VERTICAL_ITEMS_PER_PAGE,
                styles,
                LAYOUT_VERTICAL
        );
    }

    private int writeStatementCopy(
            Sheet sheet,
            int startRow,
            int startColumn,
            StatementPageDto page,
            String copyLabel,
            int fixedItemRows,
            Map<String, CellStyle> styles,
            String layoutType
    ) {
        if (STATEMENT_PARCEL.equals(page.getDocumentType())) {
            return writeParcelStatementCopy(
                    sheet,
                    startRow,
                    startColumn,
                    page,
                    copyLabel,
                    fixedItemRows,
                    styles,
                    layoutType
            );
        }

        return writeSiteStatementCopy(
                sheet,
                startRow,
                startColumn,
                page,
                copyLabel,
                fixedItemRows,
                styles,
                layoutType
        );
    }

    private int writeSiteStatementCopy(
            Sheet sheet,
            int startRow,
            int startColumn,
            StatementPageDto page,
            String copyLabel,
            int fixedItemRows,
            Map<String, CellStyle> styles,
            String layoutType
    ) {
        int rowIndex = writeTitleRow(
                sheet,
                startRow,
                startColumn,
                page,
                copyLabel,
                styles,
                layoutType
        );

        rowIndex = writeMetaPair(
                sheet,
                rowIndex,
                startColumn,
                "거래처명",
                safeTextOrDash(page.getCompanyName()),
                "주문번호",
                safeTextOrDash(page.getOrderIdsText()),
                styles
        );

        rowIndex = writeMetaPair(
                sheet,
                rowIndex,
                startColumn,
                "하차지 담당자",
                safeTextOrDash(page.getRecipientName()),
                "연락처",
                safeTextOrDash(page.getRecipientPhone()),
                styles
        );

        rowIndex = writeAddressRow(
                sheet,
                rowIndex,
                startColumn,
                "하차지 주소",
                buildAddressWithPostalCode(page),
                styles,
                layoutType
        );

        rowIndex = writeMetaPair(
                sheet,
                rowIndex,
                startColumn,
                "출고일",
                safeTextOrDash(page.getDateText()),
                "배송수단",
                safeTextOrDash(page.getDeliveryMethodName()),
                styles,
                styles.get("emphasis")
        );

        rowIndex = writeSiteItemTable(
                sheet,
                rowIndex,
                startColumn,
                page,
                fixedItemRows,
                styles,
                layoutType
        );

        Row acceptanceRow = getOrCreateRow(sheet, rowIndex++);
        acceptanceRow.setHeightInPoints(LAYOUT_HORIZONTAL.equals(layoutType) ? 22 : 18);
        setMergedValue(
                sheet,
                acceptanceRow.getRowNum(),
                startColumn,
                startColumn + 7,
                page.isLastPage()
                        ? safeTextOrDash(page.getAcceptanceText())
                        : "품목 계속 - 확인란은 마지막 페이지에 표시됩니다.",
                styles.get("acceptance")
        );

        Row signatureRow = getOrCreateRow(sheet, rowIndex++);
        signatureRow.setHeightInPoints(LAYOUT_HORIZONTAL.equals(layoutType) ? 23 : 19);
        setMergedValue(
                sheet,
                signatureRow.getRowNum(),
                startColumn,
                startColumn + 7,
                page.isLastPage() ? safeText(page.getSignatureText()) : "",
                styles.get("signature")
        );

        return rowIndex - 1;
    }

    private int writeParcelStatementCopy(
            Sheet sheet,
            int startRow,
            int startColumn,
            StatementPageDto page,
            String copyLabel,
            int fixedItemRows,
            Map<String, CellStyle> styles,
            String layoutType
    ) {
        int rowIndex = writeTitleRow(
                sheet,
                startRow,
                startColumn,
                page,
                copyLabel,
                styles,
                layoutType
        );

        rowIndex = writeMetaPair(
                sheet,
                rowIndex,
                startColumn,
                "발송일",
                safeTextOrDash(page.getDateText()),
                "운송장번호",
                safeText(page.getTrackingNumber()),
                styles
        );

        rowIndex = writeMetaPair(
                sheet,
                rowIndex,
                startColumn,
                "운임 구분",
                safeText(page.getFreightType()),
                "포장 수단",
                safeText(page.getPackingMethod()),
                styles
        );

        rowIndex = writeMetaPair(
                sheet,
                rowIndex,
                startColumn,
                "받는분",
                safeTextOrDash(page.getRecipientName()),
                "연락처",
                safeTextOrDash(page.getRecipientPhone()),
                styles
        );

        rowIndex = writeAddressRow(
                sheet,
                rowIndex,
                startColumn,
                "주소",
                buildAddressWithPostalCode(page),
                styles,
                layoutType
        );

        rowIndex = writeMetaPair(
                sheet,
                rowIndex,
                startColumn,
                "거래처명",
                safeTextOrDash(page.getCompanyName()),
                "담당자",
                safeTextOrDash(page.getManagerName()),
                styles
        );

        rowIndex = writeParcelItemTable(
                sheet,
                rowIndex,
                startColumn,
                page,
                fixedItemRows,
                styles,
                layoutType
        );

        Row footerRow = getOrCreateRow(sheet, rowIndex++);
        footerRow.setHeightInPoints(LAYOUT_HORIZONTAL.equals(layoutType) ? 20 : 17);
        setMergedValue(
                sheet,
                footerRow.getRowNum(),
                startColumn,
                startColumn + 7,
                page.getPageCount() > 1
                        ? "품목 " + page.getPageNumber() + " / " + page.getPageCount()
                        : "",
                styles.get("parcelFooter")
        );

        return rowIndex - 1;
    }

    private int writeTitleRow(
            Sheet sheet,
            int startRow,
            int startColumn,
            StatementPageDto page,
            String copyLabel,
            Map<String, CellStyle> styles,
            String layoutType
    ) {
        Row titleRow = getOrCreateRow(sheet, startRow);
        titleRow.setHeightInPoints(LAYOUT_HORIZONTAL.equals(layoutType) ? 30 : 24);

        String partText = page.getPageCount() > 1
                ? page.getPageNumber() + "/" + page.getPageCount()
                : "";

        setMergedValue(
                sheet,
                titleRow.getRowNum(),
                startColumn,
                startColumn + 1,
                partText,
                styles.get("documentKind")
        );
        setMergedValue(
                sheet,
                titleRow.getRowNum(),
                startColumn + 2,
                startColumn + 5,
                safeTextOrDash(page.getDocumentTypeLabel()),
                styles.get("title")
        );
        setMergedValue(
                sheet,
                titleRow.getRowNum(),
                startColumn + 6,
                startColumn + 7,
                copyLabel,
                styles.get("copyLabel")
        );

        return startRow + 1;
    }

    private int writeMetaPair(
            Sheet sheet,
            int rowIndex,
            int startColumn,
            String leftLabel,
            String leftValue,
            String rightLabel,
            String rightValue,
            Map<String, CellStyle> styles
    ) {
        return writeMetaPair(
                sheet,
                rowIndex,
                startColumn,
                leftLabel,
                leftValue,
                rightLabel,
                rightValue,
                styles,
                styles.get("body")
        );
    }

    private int writeMetaPair(
            Sheet sheet,
            int rowIndex,
            int startColumn,
            String leftLabel,
            String leftValue,
            String rightLabel,
            String rightValue,
            Map<String, CellStyle> styles,
            CellStyle rightValueStyle
    ) {
        Row row = getOrCreateRow(sheet, rowIndex);
        row.setHeightInPoints(22);

        setMergedValue(sheet, rowIndex, startColumn, startColumn, leftLabel, styles.get("label"));
        setMergedValue(sheet, rowIndex, startColumn + 1, startColumn + 3, leftValue, styles.get("body"));
        setMergedValue(sheet, rowIndex, startColumn + 4, startColumn + 4, rightLabel, styles.get("label"));
        setMergedValue(sheet, rowIndex, startColumn + 5, startColumn + 7, rightValue, rightValueStyle);

        return rowIndex + 1;
    }

    private int writeAddressRow(
            Sheet sheet,
            int rowIndex,
            int startColumn,
            String label,
            String value,
            Map<String, CellStyle> styles,
            String layoutType
    ) {
        Row row = getOrCreateRow(sheet, rowIndex);
        row.setHeightInPoints(LAYOUT_HORIZONTAL.equals(layoutType) ? 34 : 27);

        setMergedValue(sheet, rowIndex, startColumn, startColumn, label, styles.get("label"));
        setMergedValue(sheet, rowIndex, startColumn + 1, startColumn + 7, value, styles.get("body"));

        return rowIndex + 1;
    }

    private int writeSiteItemTable(
            Sheet sheet,
            int rowIndex,
            int startColumn,
            StatementPageDto page,
            int fixedItemRows,
            Map<String, CellStyle> styles,
            String layoutType
    ) {
        Row headerRow = getOrCreateRow(sheet, rowIndex++);
        headerRow.setHeightInPoints(22);

        setMergedValue(sheet, headerRow.getRowNum(), startColumn, startColumn, "NO", styles.get("tableHeader"));
        setMergedValue(sheet, headerRow.getRowNum(), startColumn + 1, startColumn + 2, "품명", styles.get("tableHeader"));
        setMergedValue(sheet, headerRow.getRowNum(), startColumn + 3, startColumn + 3, "규격", styles.get("tableHeader"));
        setMergedValue(sheet, headerRow.getRowNum(), startColumn + 4, startColumn + 4, "색상", styles.get("tableHeader"));
        setMergedValue(sheet, headerRow.getRowNum(), startColumn + 5, startColumn + 5, "수량", styles.get("tableHeader"));
        setMergedValue(sheet, headerRow.getRowNum(), startColumn + 6, startColumn + 7, "비고", styles.get("tableHeader"));

        List<StatementItemDto> items = page.getItems() != null ? page.getItems() : List.of();

        for (int i = 0; i < fixedItemRows; i++) {
            Row itemRow = getOrCreateRow(sheet, rowIndex++);
            itemRow.setHeightInPoints(LAYOUT_HORIZONTAL.equals(layoutType) ? 36 : 22);
            StatementItemDto item = i < items.size() ? items.get(i) : null;

            setMergedValue(sheet, itemRow.getRowNum(), startColumn, startColumn,
                    item != null ? String.valueOf(item.getNo()) : "", styles.get("bodyCenter"));
            setMergedValue(sheet, itemRow.getRowNum(), startColumn + 1, startColumn + 2,
                    item != null ? safeText(item.getProductName()) : "", styles.get("body"));
            setMergedValue(sheet, itemRow.getRowNum(), startColumn + 3, startColumn + 3,
                    item != null ? safeText(item.getSizeText()) : "", styles.get("body"));
            setMergedValue(sheet, itemRow.getRowNum(), startColumn + 4, startColumn + 4,
                    item != null ? safeText(item.getColor()) : "", styles.get("body"));
            setMergedValue(sheet, itemRow.getRowNum(), startColumn + 5, startColumn + 5,
                    item != null ? String.valueOf(item.getQuantity()) : "", styles.get("bodyCenter"));
            setMergedValue(sheet, itemRow.getRowNum(), startColumn + 6, startColumn + 7,
                    item != null ? safeText(item.getMemo()) : "", styles.get("body"));
        }

        return rowIndex;
    }

    private int writeParcelItemTable(
            Sheet sheet,
            int rowIndex,
            int startColumn,
            StatementPageDto page,
            int fixedItemRows,
            Map<String, CellStyle> styles,
            String layoutType
    ) {
        Row headerRow = getOrCreateRow(sheet, rowIndex++);
        headerRow.setHeightInPoints(22);

        /*
         * 택배명세서도 현장명세서와 동일하게 주문별 adminMemo를 비고로 표시합니다.
         * 한쪽 명세서가 8개 열로 고정되어 있으므로 품명은 2칸, 비고는 2칸을 사용합니다.
         */
        setMergedValue(sheet, headerRow.getRowNum(), startColumn, startColumn, "NO", styles.get("tableHeader"));
        setMergedValue(sheet, headerRow.getRowNum(), startColumn + 1, startColumn + 2, "품명", styles.get("tableHeader"));
        setMergedValue(sheet, headerRow.getRowNum(), startColumn + 3, startColumn + 3, "규격", styles.get("tableHeader"));
        setMergedValue(sheet, headerRow.getRowNum(), startColumn + 4, startColumn + 4, "색상", styles.get("tableHeader"));
        setMergedValue(sheet, headerRow.getRowNum(), startColumn + 5, startColumn + 5, "수량", styles.get("tableHeader"));
        setMergedValue(sheet, headerRow.getRowNum(), startColumn + 6, startColumn + 7, "비고", styles.get("tableHeader"));

        List<StatementItemDto> items = page.getItems() != null ? page.getItems() : List.of();

        for (int i = 0; i < fixedItemRows; i++) {
            Row itemRow = getOrCreateRow(sheet, rowIndex++);
            itemRow.setHeightInPoints(LAYOUT_HORIZONTAL.equals(layoutType) ? 36 : 22);
            StatementItemDto item = i < items.size() ? items.get(i) : null;

            setMergedValue(sheet, itemRow.getRowNum(), startColumn, startColumn,
                    item != null ? String.valueOf(item.getNo()) : "", styles.get("bodyCenter"));
            setMergedValue(sheet, itemRow.getRowNum(), startColumn + 1, startColumn + 2,
                    item != null ? safeText(item.getProductName()) : "", styles.get("body"));
            setMergedValue(sheet, itemRow.getRowNum(), startColumn + 3, startColumn + 3,
                    item != null ? safeText(item.getSizeText()) : "", styles.get("body"));
            setMergedValue(sheet, itemRow.getRowNum(), startColumn + 4, startColumn + 4,
                    item != null ? safeText(item.getColor()) : "", styles.get("body"));
            setMergedValue(sheet, itemRow.getRowNum(), startColumn + 5, startColumn + 5,
                    item != null ? String.valueOf(item.getQuantity()) : "", styles.get("bodyCenter"));
            setMergedValue(sheet, itemRow.getRowNum(), startColumn + 6, startColumn + 7,
                    item != null ? safeText(item.getMemo()) : "", styles.get("body"));
        }

        return rowIndex;
    }

    private void configureStatementSheet(Sheet sheet, String layoutType) {
        sheet.setFitToPage(true);
        sheet.setAutobreaks(true);
        sheet.setHorizontallyCenter(true);
        sheet.setVerticallyCenter(true);
        sheet.setDisplayGridlines(false);
        sheet.setPrintGridlines(false);

        PrintSetup printSetup = sheet.getPrintSetup();
        printSetup.setPaperSize(PrintSetup.A4_PAPERSIZE);
        printSetup.setLandscape(LAYOUT_HORIZONTAL.equals(layoutType));
        printSetup.setFitWidth((short) 1);
        printSetup.setFitHeight((short) 1);

        sheet.setMargin(PageMargin.LEFT, 0.12);
        sheet.setMargin(PageMargin.RIGHT, 0.12);
        sheet.setMargin(PageMargin.TOP, 0.15);
        sheet.setMargin(PageMargin.BOTTOM, 0.15);
        sheet.setMargin(PageMargin.HEADER, 0.0);
        sheet.setMargin(PageMargin.FOOTER, 0.0);
    }

    private void configureHorizontalColumnWidths(Sheet sheet) {
        /*
         * A/J 라벨 열은 거래처명, 하차지 담당자, 하차지 주소 등이 한 줄로 보이도록 넓힙니다.
         * 한쪽 명세서 전체 폭 합계는 기존 87을 유지하여 A4 한 페이지 맞춤 축소를 최소화합니다.
         */
        int[] widths = {12, 11, 11, 9, 10, 9, 10, 15};

        for (int i = 0; i < widths.length; i++) {
            sheet.setColumnWidth(i, widths[i] * 256);
            sheet.setColumnWidth(HORIZONTAL_SECOND_COPY_START_COLUMN + i, widths[i] * 256);
        }

        sheet.setColumnWidth(HORIZONTAL_SEPARATOR_COLUMN, 2 * 256);
    }

    private void configureVerticalColumnWidths(Sheet sheet) {
        /* 세로형은 현재 버튼을 숨겨 두었지만 동일한 라벨 가독성을 유지합니다. */
        int[] widths = {13, 14, 14, 12, 12, 11, 13, 19};

        for (int i = 0; i < widths.length; i++) {
            sheet.setColumnWidth(i, widths[i] * 256);
        }
    }

    private void applyVerticalCutLine(
            Sheet sheet,
            int columnIndex,
            int firstRow,
            int lastRow,
            CellStyle style
    ) {
        for (int rowIndex = firstRow; rowIndex <= lastRow; rowIndex++) {
            Row row = getOrCreateRow(sheet, rowIndex);
            Cell cell = getOrCreateCell(row, columnIndex);
            cell.setCellStyle(style);
        }
    }

    private void applyHorizontalCutLine(
            Sheet sheet,
            int rowIndex,
            int firstColumn,
            int lastColumn,
            CellStyle style
    ) {
        Row row = getOrCreateRow(sheet, rowIndex);
        row.setHeightInPoints(VERTICAL_SEPARATOR_ROW_HEIGHT);

        for (int columnIndex = firstColumn; columnIndex <= lastColumn; columnIndex++) {
            Cell cell = getOrCreateCell(row, columnIndex);
            cell.setCellStyle(style);
        }
    }

    private Map<String, CellStyle> createExcelStyles(Workbook workbook) {
        Map<String, CellStyle> styles = new LinkedHashMap<>();

        /*
         * A4 한 페이지 맞춤은 유지하면서 엑셀에서 읽기 쉽도록 전체 폰트를 한 단계 확대합니다.
         */
        Font normalFont = createFont(workbook, (short) 10, false, IndexedColors.BLACK.getIndex());
        Font boldFont = createFont(workbook, (short) 10, true, IndexedColors.BLACK.getIndex());
        Font titleFont = createFont(workbook, (short) 17, true, IndexedColors.BLACK.getIndex());
        Font whiteBoldFont = createFont(workbook, (short) 10, true, IndexedColors.WHITE.getIndex());
        Font emphasisFont = createFont(workbook, (short) 11, true, IndexedColors.BLACK.getIndex());

        CellStyle body = workbook.createCellStyle();
        body.setFont(normalFont);
        body.setAlignment(HorizontalAlignment.LEFT);
        body.setVerticalAlignment(VerticalAlignment.CENTER);
        body.setWrapText(true);
        applyThinBorder(body);
        styles.put("body", body);

        CellStyle bodyCenter = workbook.createCellStyle();
        bodyCenter.cloneStyleFrom(body);
        bodyCenter.setAlignment(HorizontalAlignment.CENTER);
        styles.put("bodyCenter", bodyCenter);

        CellStyle label = workbook.createCellStyle();
        label.setFont(boldFont);
        label.setAlignment(HorizontalAlignment.CENTER);
        label.setVerticalAlignment(VerticalAlignment.CENTER);
        label.setWrapText(false);
        label.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        label.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        applyThinBorder(label);
        styles.put("label", label);

        CellStyle title = workbook.createCellStyle();
        title.setFont(titleFont);
        title.setAlignment(HorizontalAlignment.CENTER);
        title.setVerticalAlignment(VerticalAlignment.CENTER);
        title.setBorderBottom(BorderStyle.MEDIUM);
        styles.put("title", title);

        CellStyle documentKind = workbook.createCellStyle();
        documentKind.setFont(boldFont);
        documentKind.setAlignment(HorizontalAlignment.LEFT);
        documentKind.setVerticalAlignment(VerticalAlignment.BOTTOM);
        documentKind.setBorderBottom(BorderStyle.MEDIUM);
        styles.put("documentKind", documentKind);

        CellStyle copyLabel = workbook.createCellStyle();
        copyLabel.setFont(boldFont);
        copyLabel.setAlignment(HorizontalAlignment.CENTER);
        copyLabel.setVerticalAlignment(VerticalAlignment.CENTER);
        copyLabel.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
        copyLabel.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        applyThinBorder(copyLabel);
        styles.put("copyLabel", copyLabel);

        CellStyle tableHeader = workbook.createCellStyle();
        tableHeader.setFont(whiteBoldFont);
        tableHeader.setAlignment(HorizontalAlignment.CENTER);
        tableHeader.setVerticalAlignment(VerticalAlignment.CENTER);
        tableHeader.setWrapText(true);
        tableHeader.setFillForegroundColor(IndexedColors.GREY_50_PERCENT.getIndex());
        tableHeader.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        applyThinBorder(tableHeader);
        styles.put("tableHeader", tableHeader);

        CellStyle emphasis = workbook.createCellStyle();
        emphasis.cloneStyleFrom(body);
        emphasis.setFont(emphasisFont);
        styles.put("emphasis", emphasis);

        CellStyle acceptance = workbook.createCellStyle();
        acceptance.setFont(normalFont);
        acceptance.setAlignment(HorizontalAlignment.CENTER);
        acceptance.setVerticalAlignment(VerticalAlignment.CENTER);
        acceptance.setWrapText(true);
        acceptance.setBorderTop(BorderStyle.THIN);
        acceptance.setBorderLeft(BorderStyle.THIN);
        acceptance.setBorderRight(BorderStyle.THIN);
        styles.put("acceptance", acceptance);

        CellStyle signature = workbook.createCellStyle();
        signature.setFont(boldFont);
        signature.setAlignment(HorizontalAlignment.RIGHT);
        signature.setVerticalAlignment(VerticalAlignment.CENTER);
        signature.setWrapText(false);
        signature.setBorderTop(BorderStyle.THIN);
        signature.setBorderBottom(BorderStyle.THIN);
        signature.setBorderLeft(BorderStyle.THIN);
        signature.setBorderRight(BorderStyle.THIN);
        styles.put("signature", signature);

        CellStyle parcelFooter = workbook.createCellStyle();
        parcelFooter.setFont(normalFont);
        parcelFooter.setAlignment(HorizontalAlignment.RIGHT);
        parcelFooter.setVerticalAlignment(VerticalAlignment.CENTER);
        parcelFooter.setBorderTop(BorderStyle.THIN);
        styles.put("parcelFooter", parcelFooter);

        CellStyle cutVertical = workbook.createCellStyle();
        cutVertical.setBorderLeft(BorderStyle.DASHED);
        cutVertical.setBorderRight(BorderStyle.DASHED);
        styles.put("cutVertical", cutVertical);

        CellStyle cutHorizontal = workbook.createCellStyle();
        cutHorizontal.setBorderTop(BorderStyle.DASHED);
        cutHorizontal.setBorderBottom(BorderStyle.DASHED);
        styles.put("cutHorizontal", cutHorizontal);

        return styles;
    }

    private Font createFont(
            Workbook workbook,
            short size,
            boolean bold,
            short color
    ) {
        Font font = workbook.createFont();
        font.setFontName("맑은 고딕");
        font.setFontHeightInPoints(size);
        font.setBold(bold);
        font.setColor(color);
        return font;
    }

    private void applyThinBorder(CellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
    }

    private void setMergedValue(
            Sheet sheet,
            int rowIndex,
            int firstColumn,
            int lastColumn,
            String value,
            CellStyle style
    ) {
        Row row = getOrCreateRow(sheet, rowIndex);

        for (int columnIndex = firstColumn; columnIndex <= lastColumn; columnIndex++) {
            Cell cell = getOrCreateCell(row, columnIndex);
            cell.setCellStyle(style);
        }

        Cell firstCell = getOrCreateCell(row, firstColumn);
        firstCell.setCellValue(value != null ? value : "");

        if (lastColumn > firstColumn) {
            sheet.addMergedRegion(new CellRangeAddress(
                    rowIndex,
                    rowIndex,
                    firstColumn,
                    lastColumn
            ));
        }
    }

    private Row getOrCreateRow(Sheet sheet, int rowIndex) {
        Row row = sheet.getRow(rowIndex);
        return row != null ? row : sheet.createRow(rowIndex);
    }

    private Cell getOrCreateCell(Row row, int columnIndex) {
        Cell cell = row.getCell(columnIndex);
        return cell != null ? cell : row.createCell(columnIndex);
    }

    private String buildAddressWithPostalCode(StatementPageDto page) {
        String postalCode = safeText(page.getPostalCode());
        String addressText = safeTextOrDash(page.getAddressText());

        return postalCode.isBlank()
                ? addressText
                : "[" + postalCode + "] " + addressText;
    }

    private String buildSheetName(int index, StatementPageDto page) {
        String type = STATEMENT_PARCEL.equals(page.getDocumentType()) ? "택배" : "현장";
        String name = String.format("%03d_%s명세서", index, type);
        return name.length() <= 31 ? name : name.substring(0, 31);
    }

    private boolean hasAnyText(String... values) {
        if (values == null) {
            return false;
        }

        for (String value : values) {
            if (!safeText(value).isBlank()) {
                return true;
            }
        }

        return false;
    }

    private String joinAddressParts(String... values) {
        List<String> parts = new ArrayList<>();

        if (values != null) {
            for (String value : values) {
                String text = safeText(value);
                if (!text.isBlank()) {
                    parts.add(text);
                }
            }
        }

        return parts.isEmpty() ? "-" : String.join(" ", parts);
    }

    private String joinOrDash(Set<String> values, String delimiter) {
        if (values == null || values.isEmpty()) {
            return "-";
        }

        String joined = values.stream()
                .map(this::safeText)
                .filter(value -> !value.isBlank() && !"-".equals(value))
                .collect(Collectors.joining(delimiter));

        return joined.isBlank() ? "-" : joined;
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

    private String normalizeCode(String value) {
        return safeText(value).replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
    }

    private String safeTextOrDash(Object value) {
        String text = safeText(value);
        return text.isBlank() ? "-" : text;
    }

    private String safeText(Object value) {
        if (value == null) {
            return "";
        }

        return String.valueOf(value)
                .replace("\r", " ")
                .replace("\t", " ")
                .replaceAll(" {2,}", " ")
                .trim();
    }

    public record TeamStatementOrderRef(
            Long deliveryHandlerId,
            String deliveryHandlerName,
            Long orderId,
            int orderIndex
    ) {
    }

    private enum StatementSource {
        DISPATCH_SELECTION,
        DELIVERY_MEMBER,
        DELIVERY_TEAM
    }

    private record StatementGroupKey(
            String deliveryHandlerKey,
            String companyKey,
            String addressKey,
            String deliveryMethodKey,
            String deliveryDateKey
    ) {
    }

    private record RecipientData(String name, String phone) {
    }

    private record AddressData(String postalCode, String addressText) {
    }

    private static final class StatementGroup {
        private Long taskId;
        private String documentType;
        private String documentTypeLabel;
        private String companyName;
        private String recipientName;
        private String recipientPhone;
        private String postalCode;
        private String addressText;
        private Long deliveryHandlerId;
        private String deliveryHandlerName;

        private final LinkedHashSet<Long> orderIds = new LinkedHashSet<>();
        private final LinkedHashSet<String> deliveryDateTexts = new LinkedHashSet<>();
        private final LinkedHashSet<String> deliveryMethodNames = new LinkedHashSet<>();
        private final List<StatementItemDto> items = new ArrayList<>();
    }
}
