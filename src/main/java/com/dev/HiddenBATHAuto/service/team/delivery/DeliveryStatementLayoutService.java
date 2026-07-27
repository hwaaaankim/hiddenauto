package com.dev.HiddenBATHAuto.service.team.delivery;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
import com.dev.HiddenBATHAuto.model.task.Task;
import com.dev.HiddenBATHAuto.repository.order.DeliveryOrderIndexRepository;
import com.dev.HiddenBATHAuto.repository.order.OrderRepository;
import com.dev.HiddenBATHAuto.service.order.DeliveryMethodAssignmentPolicy;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DeliveryStatementLayoutService {

    public static final String LAYOUT_HORIZONTAL = "HORIZONTAL";
    public static final String LAYOUT_VERTICAL = "VERTICAL";

    private static final String DOCUMENT_PARCEL = "PARCEL";
    private static final String DOCUMENT_SITE = "SITE";

    private static final int HORIZONTAL_ITEMS_PER_PAGE = 8;
    private static final int VERTICAL_ITEMS_PER_PAGE = 5;

    private static final int COPY_COLUMN_COUNT = 8;
    private static final int HORIZONTAL_SECOND_COPY_START_COLUMN = 9;
    private static final int HORIZONTAL_SEPARATOR_COLUMN = 8;
    private static final int VERTICAL_SEPARATOR_ROW_HEIGHT = 8;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final OrderRepository orderRepository;
    private final DeliveryOrderIndexRepository deliveryOrderIndexRepository;
    private final ObjectMapper objectMapper;

    public String normalizeLayoutType(String layoutType) {
        String normalized = safeText(layoutType).replaceAll("\\s+", "").toUpperCase();
        return LAYOUT_HORIZONTAL.equals(normalized) ? LAYOUT_HORIZONTAL : LAYOUT_VERTICAL;
    }

    @Transactional(readOnly = true)
    public LayoutResponse buildLayoutResponse(
            LayoutRequest request,
            Member loginMember
    ) {
        validateRequest(request, loginMember);

        String layoutType = normalizeLayoutType(request.getLayoutType());
        List<Long> orderIds = normalizeOrderIds(request.getOrderIds());
        List<Order> orders = loadOrdersInRequestedOrder(orderIds);
        List<StatementGroup> groups = buildStatementGroups(orders);
        List<StatementPageDto> pages = splitGroupsIntoPages(groups, layoutType);

        return LayoutResponse.builder()
                .layoutType(layoutType)
                .generatedDateText(LocalDate.now().format(DATE_FORMATTER))
                .pages(pages)
                .build();
    }

    @Transactional(readOnly = true)
    public byte[] buildLayoutExcel(
            LayoutRequest request,
            Member loginMember
    ) {
        LayoutResponse response = buildLayoutResponse(request, loginMember);

        if (response.getPages() == null || response.getPages().isEmpty()) {
            throw new IllegalArgumentException("엑셀로 생성할 명세서 데이터가 없습니다.");
        }

        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Map<String, CellStyle> styles = createExcelStyles(workbook);
            String layoutType = normalizeLayoutType(response.getLayoutType());

            for (int i = 0; i < response.getPages().size(); i++) {
                StatementPageDto page = response.getPages().get(i);
                Sheet sheet = workbook.createSheet(buildSheetName(i + 1, page));

                configureStatementSheet(workbook, sheet, layoutType);

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

    private List<StatementGroup> buildStatementGroups(List<Order> orders) {
        LinkedHashMap<String, StatementGroup> groupMap = new LinkedHashMap<>();

        for (Order order : orders) {
            if (order == null || order.getId() == null) {
                continue;
            }

            String deliveryMethodName = resolveDeliveryMethodName(order);
            String documentType = isParcelDeliveryMethod(order.getDeliveryMethod())
                    ? DOCUMENT_PARCEL
                    : DOCUMENT_SITE;
            AddressData address = resolveStatementAddress(order, documentType);
            RecipientData recipient = resolveRecipient(order);
            Long taskId = order.getTask() != null ? order.getTask().getId() : null;

            String groupKey = buildGroupKey(
                    taskId,
                    order.getId(),
                    documentType,
                    deliveryMethodName,
                    recipient,
                    address
            );

            StatementGroup group = groupMap.computeIfAbsent(
                    groupKey,
                    ignored -> createStatementGroup(
                            order,
                            taskId,
                            documentType,
                            deliveryMethodName,
                            recipient,
                            address
                    )
            );

            addOrderToStatementGroup(group, order);
        }

        return new ArrayList<>(groupMap.values());
    }

    private String buildGroupKey(
            Long taskId,
            Long orderId,
            String documentType,
            String deliveryMethodName,
            RecipientData recipient,
            AddressData address
    ) {
        String parentKey = taskId != null ? "TASK:" + taskId : "ORDER:" + orderId;

        return String.join(
                "|",
                parentKey,
                safeText(documentType),
                normalizeKeyText(deliveryMethodName),
                normalizeKeyText(recipient.name()),
                normalizeKeyText(recipient.phone()),
                normalizeKeyText(address.postalCode()),
                normalizeKeyText(address.addressText())
        );
    }

    private StatementGroup createStatementGroup(
            Order order,
            Long taskId,
            String documentType,
            String deliveryMethodName,
            RecipientData recipient,
            AddressData address
    ) {
        Task task = order.getTask();
        Member requestedBy = task != null ? task.getRequestedBy() : null;
        Company company = requestedBy != null ? requestedBy.getCompany() : null;
        Member managedBy = task != null ? task.getManagedBy() : null;

        StatementGroup group = new StatementGroup();
        group.taskId = taskId;
        group.documentType = documentType;
        group.documentTypeLabel = DOCUMENT_PARCEL.equals(documentType)
                ? "택배명세서"
                : "현장명세서";
        group.companyName = company != null
                ? safeTextOrDash(company.getCompanyName())
                : "-";
        group.requesterName = requestedBy != null
                ? safeTextOrDash(requestedBy.getName())
                : "-";
        group.managedByName = managedBy != null
                ? safeTextOrDash(managedBy.getName())
                : "-";
        group.orderDateText = resolveOrderDateText(order);
        group.recipientName = safeTextOrDash(recipient.name());
        group.recipientPhone = safeTextOrDash(recipient.phone());
        group.postalCode = safeText(address.postalCode());
        group.addressText = safeTextOrDash(address.addressText());
        group.deliveryMethodName = safeTextOrDash(deliveryMethodName);

        if (DOCUMENT_PARCEL.equals(documentType)) {
            group.recipientLabel = "받는분";
            group.contactLabel = "연락처";
            group.addressLabel = "받는 주소";
            group.auxiliaryLabel = "운송장번호";
        } else {
            group.recipientLabel = "하차지 담당";
            group.contactLabel = "하차지 연락처";
            group.addressLabel = "하차지 주소";
            group.auxiliaryLabel = "배송순번";
        }

        return group;
    }

    private void addOrderToStatementGroup(StatementGroup group, Order order) {
        group.orderIds.add(order.getId());
        group.deliveryDateTexts.add(resolveDeliveryDateText(order));

        DeliveryOrderIndex deliveryOrderIndex = deliveryOrderIndexRepository
                .findByOrder_Id(order.getId())
                .orElse(null);

        Member deliveryHandler = deliveryOrderIndex != null
                ? deliveryOrderIndex.getDeliveryHandler()
                : order.getAssignedDeliveryHandler();

        if (deliveryHandler != null && !safeText(deliveryHandler.getName()).isBlank()) {
            group.deliveryHandlerNames.add(deliveryHandler.getName().trim());
        }

        if (deliveryOrderIndex != null) {
            group.deliveryOrderIndexes.add(String.valueOf(deliveryOrderIndex.getOrderIndex()));
        }

        int quantity = order.getQuantity();
        group.totalQuantity += quantity;
        group.packingCost += order.getPackingCost();
        group.deliveryCost += order.getDeliveryCost();
        group.totalAmount += order.getTotalAmount();

        String memo = safeText(order.getAdminMemo());
        if (!memo.isBlank()) {
            group.notes.add(memo);
        }

        group.items.add(toStatementItem(order, group.items.size() + 1));
    }

    private StatementItemDto toStatementItem(Order order, int no) {
        OrderItem orderItem = order.getOrderItem();
        Map<String, Object> optionMap = parseOptionJson(
                orderItem != null ? orderItem.getOptionJson() : null
        );

        String productName = firstNonBlank(
                pickFirstValue(optionMap, List.of(
                        "제품명",
                        "제품",
                        "productName",
                        "ProductName",
                        "product_name"
                )),
                orderItem != null ? orderItem.getProductName() : null,
                "-"
        );

        String sizeText = firstNonBlank(
                pickFirstValue(optionMap, List.of(
                        "사이즈",
                        "규격",
                        "size",
                        "Size"
                )),
                "-"
        );

        String color = firstNonBlank(
                pickFirstValue(optionMap, List.of(
                        "색상",
                        "컬러",
                        "color",
                        "Color"
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
                .memo(safeTextOrDash(order.getAdminMemo()))
                .build();
    }

    private List<StatementPageDto> splitGroupsIntoPages(
            List<StatementGroup> groups,
            String layoutType
    ) {
        int itemsPerPage = LAYOUT_HORIZONTAL.equals(layoutType)
                ? HORIZONTAL_ITEMS_PER_PAGE
                : VERTICAL_ITEMS_PER_PAGE;
        List<StatementPageDto> pages = new ArrayList<>();
        int sequence = 1;

        for (StatementGroup group : groups) {
            List<StatementItemDto> items = group.items.isEmpty()
                    ? List.of()
                    : group.items;
            int pageCount = Math.max(1, (int) Math.ceil(items.size() / (double) itemsPerPage));

            for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
                int fromIndex = Math.min(pageIndex * itemsPerPage, items.size());
                int toIndex = Math.min(fromIndex + itemsPerPage, items.size());
                List<StatementItemDto> pageItems = items.isEmpty()
                        ? List.of()
                        : new ArrayList<>(items.subList(fromIndex, toIndex));

                pages.add(toStatementPageDto(
                        group,
                        pageItems,
                        sequence++,
                        pageIndex + 1,
                        pageCount
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
            int pageCount
    ) {
        String auxiliaryValue = DOCUMENT_PARCEL.equals(group.documentType)
                ? ""
                : joinOrDash(group.deliveryOrderIndexes, ", ");

        return StatementPageDto.builder()
                .sequence(sequence)
                .pageNumber(pageNumber)
                .pageCount(pageCount)
                .taskId(group.taskId)
                .documentType(group.documentType)
                .documentTypeLabel(group.documentTypeLabel)
                .companyName(group.companyName)
                .requesterName(group.requesterName)
                .managedByName(group.managedByName)
                .orderIdsText(group.orderIds.stream()
                        .map(orderId -> "#" + orderId)
                        .collect(Collectors.joining(", ")))
                .orderDateText(group.orderDateText)
                .deliveryDateText(joinOrDash(group.deliveryDateTexts, ", "))
                .recipientLabel(group.recipientLabel)
                .recipientName(group.recipientName)
                .contactLabel(group.contactLabel)
                .recipientPhone(group.recipientPhone)
                .addressLabel(group.addressLabel)
                .postalCode(group.postalCode)
                .addressText(group.addressText)
                .deliveryMethodName(group.deliveryMethodName)
                .deliveryHandlerName(joinOrDash(group.deliveryHandlerNames, ", "))
                .auxiliaryLabel(group.auxiliaryLabel)
                .auxiliaryValue(auxiliaryValue)
                .totalQuantity(group.totalQuantity)
                .packingCost(group.packingCost)
                .deliveryCost(group.deliveryCost)
                .totalAmount(group.totalAmount)
                .noteText(joinOrDash(group.notes, " / "))
                .items(pageItems)
                .build();
    }

    private AddressData resolveStatementAddress(Order order, String documentType) {
        boolean useSiteAddress = DOCUMENT_SITE.equals(documentType)
                && hasAnyText(
                        order.getSiteZipCode(),
                        order.getSiteDoName(),
                        order.getSiteSiName(),
                        order.getSiteGuName(),
                        order.getSiteRoadAddress(),
                        order.getSiteDetailAddress()
                );

        if (useSiteAddress) {
            return new AddressData(
                    safeText(order.getSiteZipCode()),
                    joinAddressParts(
                            order.getSiteDoName(),
                            order.getSiteSiName(),
                            order.getSiteGuName(),
                            order.getSiteRoadAddress(),
                            order.getSiteDetailAddress()
                    )
            );
        }

        return new AddressData(
                safeText(order.getZipCode()),
                joinAddressParts(
                        order.getDoName(),
                        order.getSiName(),
                        order.getGuName(),
                        order.getRoadAddress(),
                        order.getDetailAddress()
                )
        );
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

    private String resolveOrderDateText(Order order) {
        Task task = order.getTask();
        LocalDateTime orderDate = task != null && task.getCreatedAt() != null
                ? task.getCreatedAt()
                : order.getCreatedAt();

        return orderDate != null
                ? orderDate.toLocalDate().format(DATE_FORMATTER)
                : "-";
    }

    private String resolveDeliveryDateText(Order order) {
        return order.getPreferredDeliveryDate() != null
                ? order.getPreferredDeliveryDate().toLocalDate().format(DATE_FORMATTER)
                : "-";
    }

    private String resolveDeliveryMethodName(Order order) {
        return order.getDeliveryMethod() != null
                ? safeTextOrDash(order.getDeliveryMethod().getMethodName())
                : "미지정";
    }

    private boolean isParcelDeliveryMethod(DeliveryMethod deliveryMethod) {
        return deliveryMethod != null
                && DeliveryMethodAssignmentPolicy.containsKeyword(
                        deliveryMethod.getMethodName(),
                        "택배"
                );
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
        if (optionMap == null || optionMap.isEmpty()) {
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
        applyVerticalCutLine(sheet, HORIZONTAL_SEPARATOR_COLUMN, 0, lastRow, styles.get("cutVertical"));
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

        int secondStartRow = separatorRowIndex + 1;
        return writeStatementCopy(
                sheet,
                secondStartRow,
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
        int rowIndex = startRow;

        Row titleRow = getOrCreateRow(sheet, rowIndex++);
        titleRow.setHeightInPoints(LAYOUT_HORIZONTAL.equals(layoutType) ? 26 : 22);

        String partText = page.getPageCount() > 1
                ? "  " + page.getPageNumber() + "/" + page.getPageCount()
                : "";

        setMergedValue(
                sheet,
                titleRow.getRowNum(),
                startColumn,
                startColumn + 1,
                safeTextOrDash(page.getDocumentTypeLabel()) + partText,
                styles.get("documentKind")
        );
        setMergedValue(
                sheet,
                titleRow.getRowNum(),
                startColumn + 2,
                startColumn + 5,
                "출 고 명 세 서",
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

        rowIndex = writeMetaPair(
                sheet,
                rowIndex,
                startColumn,
                "거래처",
                page.getCompanyName(),
                "주문번호",
                page.getOrderIdsText(),
                styles
        );

        rowIndex = writeMetaPair(
                sheet,
                rowIndex,
                startColumn,
                page.getRecipientLabel(),
                page.getRecipientName(),
                page.getContactLabel(),
                page.getRecipientPhone(),
                styles
        );

        Row addressRow = getOrCreateRow(sheet, rowIndex++);
        addressRow.setHeightInPoints(LAYOUT_HORIZONTAL.equals(layoutType) ? 28 : 22);
        setMergedValue(
                sheet,
                addressRow.getRowNum(),
                startColumn,
                startColumn,
                safeTextOrDash(page.getAddressLabel()),
                styles.get("label")
        );
        setMergedValue(
                sheet,
                addressRow.getRowNum(),
                startColumn + 1,
                startColumn + 7,
                buildAddressWithPostalCode(page),
                styles.get("body")
        );

        rowIndex = writeMetaPair(
                sheet,
                rowIndex,
                startColumn,
                "출고일",
                page.getDeliveryDateText(),
                "배송수단",
                page.getDeliveryMethodName(),
                styles,
                styles.get("deliveryMethod")
        );

        rowIndex = writeMetaPair(
                sheet,
                rowIndex,
                startColumn,
                "배송담당자",
                page.getDeliveryHandlerName(),
                page.getAuxiliaryLabel(),
                page.getAuxiliaryValue(),
                styles
        );

        Row tableHeaderRow = getOrCreateRow(sheet, rowIndex++);
        tableHeaderRow.setHeightInPoints(19);
        writeItemTableHeader(sheet, tableHeaderRow.getRowNum(), startColumn, styles.get("tableHeader"));

        List<StatementItemDto> items = page.getItems() != null
                ? page.getItems()
                : List.of();

        for (int i = 0; i < fixedItemRows; i++) {
            Row itemRow = getOrCreateRow(sheet, rowIndex++);
            itemRow.setHeightInPoints(LAYOUT_HORIZONTAL.equals(layoutType) ? 23 : 19);

            StatementItemDto item = i < items.size() ? items.get(i) : null;
            writeItemRow(sheet, itemRow.getRowNum(), startColumn, item, styles.get("body"), styles.get("bodyCenter"));
        }

        Row summaryRow = getOrCreateRow(sheet, rowIndex++);
        summaryRow.setHeightInPoints(20);
        writeSummaryRow(sheet, summaryRow.getRowNum(), startColumn, page, styles);

        Row noteRow = getOrCreateRow(sheet, rowIndex++);
        noteRow.setHeightInPoints(LAYOUT_HORIZONTAL.equals(layoutType) ? 28 : 21);
        setMergedValue(
                sheet,
                noteRow.getRowNum(),
                startColumn,
                startColumn,
                "전달사항",
                styles.get("label")
        );
        setMergedValue(
                sheet,
                noteRow.getRowNum(),
                startColumn + 1,
                startColumn + 7,
                safeTextOrDash(page.getNoteText()),
                styles.get("body")
        );

        Row footerRow = getOrCreateRow(sheet, rowIndex++);
        footerRow.setHeightInPoints(22);
        setMergedValue(
                sheet,
                footerRow.getRowNum(),
                startColumn,
                startColumn + 5,
                "위 품목을 이상 없이 출고·인수하였습니다.",
                styles.get("footer")
        );
        setMergedValue(
                sheet,
                footerRow.getRowNum(),
                startColumn + 6,
                startColumn + 7,
                "확인:                    ",
                styles.get("signature")
        );

        return rowIndex - 1;
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
        row.setHeightInPoints(19);

        setMergedValue(sheet, rowIndex, startColumn, startColumn, safeTextOrDash(leftLabel), styles.get("label"));
        setMergedValue(sheet, rowIndex, startColumn + 1, startColumn + 3, safeTextOrDash(leftValue), styles.get("body"));
        setMergedValue(sheet, rowIndex, startColumn + 4, startColumn + 4, safeTextOrDash(rightLabel), styles.get("label"));
        setMergedValue(sheet, rowIndex, startColumn + 5, startColumn + 7, safeTextOrDash(rightValue), rightValueStyle);

        return rowIndex + 1;
    }

    private void writeItemTableHeader(
            Sheet sheet,
            int rowIndex,
            int startColumn,
            CellStyle headerStyle
    ) {
        setMergedValue(sheet, rowIndex, startColumn, startColumn, "NO", headerStyle);
        setMergedValue(sheet, rowIndex, startColumn + 1, startColumn + 3, "품목명", headerStyle);
        setMergedValue(sheet, rowIndex, startColumn + 4, startColumn + 4, "규격", headerStyle);
        setMergedValue(sheet, rowIndex, startColumn + 5, startColumn + 5, "색상", headerStyle);
        setMergedValue(sheet, rowIndex, startColumn + 6, startColumn + 6, "수량", headerStyle);
        setMergedValue(sheet, rowIndex, startColumn + 7, startColumn + 7, "비고", headerStyle);
    }

    private void writeItemRow(
            Sheet sheet,
            int rowIndex,
            int startColumn,
            StatementItemDto item,
            CellStyle bodyStyle,
            CellStyle centerStyle
    ) {
        String no = item != null ? String.valueOf(item.getNo()) : "";
        String productName = item != null ? safeText(item.getProductName()) : "";
        String sizeText = item != null ? safeText(item.getSizeText()) : "";
        String color = item != null ? safeText(item.getColor()) : "";
        String quantity = item != null ? String.valueOf(item.getQuantity()) : "";
        String memo = item != null ? safeText(item.getMemo()) : "";

        setMergedValue(sheet, rowIndex, startColumn, startColumn, no, centerStyle);
        setMergedValue(sheet, rowIndex, startColumn + 1, startColumn + 3, productName, bodyStyle);
        setMergedValue(sheet, rowIndex, startColumn + 4, startColumn + 4, sizeText, bodyStyle);
        setMergedValue(sheet, rowIndex, startColumn + 5, startColumn + 5, color, bodyStyle);
        setMergedValue(sheet, rowIndex, startColumn + 6, startColumn + 6, quantity, centerStyle);
        setMergedValue(sheet, rowIndex, startColumn + 7, startColumn + 7, memo, bodyStyle);
    }

    private void writeSummaryRow(
            Sheet sheet,
            int rowIndex,
            int startColumn,
            StatementPageDto page,
            Map<String, CellStyle> styles
    ) {
        writeSummaryPair(sheet, rowIndex, startColumn, "총수량", formatNumber(page.getTotalQuantity()), styles);
        writeSummaryPair(sheet, rowIndex, startColumn + 2, "포장비", formatMoney(page.getPackingCost()), styles);
        writeSummaryPair(sheet, rowIndex, startColumn + 4, "운임비", formatMoney(page.getDeliveryCost()), styles);
        writeSummaryPair(sheet, rowIndex, startColumn + 6, "합계금액", formatMoney(page.getTotalAmount()), styles);
    }

    private void writeSummaryPair(
            Sheet sheet,
            int rowIndex,
            int startColumn,
            String label,
            String value,
            Map<String, CellStyle> styles
    ) {
        setMergedValue(sheet, rowIndex, startColumn, startColumn, label, styles.get("summaryLabel"));
        setMergedValue(sheet, rowIndex, startColumn + 1, startColumn + 1, value, styles.get("summaryValue"));
    }

    private void configureStatementSheet(
            Workbook workbook,
            Sheet sheet,
            String layoutType
    ) {
        boolean horizontal = LAYOUT_HORIZONTAL.equals(layoutType);

        sheet.setDisplayGridlines(false);
        sheet.setPrintGridlines(false);
        sheet.setFitToPage(true);
        sheet.setAutobreaks(true);
        sheet.setHorizontallyCenter(true);
        sheet.setVerticallyCenter(true);

        PrintSetup printSetup = sheet.getPrintSetup();
        printSetup.setPaperSize(PrintSetup.A4_PAPERSIZE);
        printSetup.setLandscape(horizontal);
        printSetup.setFitWidth((short) 1);
        printSetup.setFitHeight((short) 1);

        sheet.setMargin(PageMargin.LEFT, 0.12);
        sheet.setMargin(PageMargin.RIGHT, 0.12);
        sheet.setMargin(PageMargin.TOP, 0.15);
        sheet.setMargin(PageMargin.BOTTOM, 0.15);

    }

    private void configureHorizontalColumnWidths(Sheet sheet) {
        int[] copyWidths = {
                6, 10, 10, 10, 12, 11, 8, 17
        };

        for (int i = 0; i < copyWidths.length; i++) {
            sheet.setColumnWidth(i, copyWidths[i] * 256);
            sheet.setColumnWidth(HORIZONTAL_SECOND_COPY_START_COLUMN + i, copyWidths[i] * 256);
        }

        sheet.setColumnWidth(HORIZONTAL_SEPARATOR_COLUMN, 2 * 256);
    }

    private void configureVerticalColumnWidths(Sheet sheet) {
        int[] widths = {
                7, 14, 14, 14, 13, 12, 9, 20
        };

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

        Font normalFont = createFont(workbook, (short) 8, false, IndexedColors.BLACK.getIndex());
        Font boldFont = createFont(workbook, (short) 8, true, IndexedColors.BLACK.getIndex());
        Font titleFont = createFont(workbook, (short) 14, true, IndexedColors.BLACK.getIndex());
        Font whiteBoldFont = createFont(workbook, (short) 8, true, IndexedColors.WHITE.getIndex());
        Font methodFont = createFont(workbook, (short) 8, true, IndexedColors.DARK_BLUE.getIndex());

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
        label.setWrapText(true);
        label.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        label.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        applyThinBorder(label);
        styles.put("label", label);

        CellStyle title = workbook.createCellStyle();
        title.setFont(titleFont);
        title.setAlignment(HorizontalAlignment.CENTER);
        title.setVerticalAlignment(VerticalAlignment.CENTER);
        title.setWrapText(false);
        title.setBorderBottom(BorderStyle.MEDIUM);
        styles.put("title", title);

        CellStyle documentKind = workbook.createCellStyle();
        documentKind.setFont(boldFont);
        documentKind.setAlignment(HorizontalAlignment.LEFT);
        documentKind.setVerticalAlignment(VerticalAlignment.CENTER);
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

        CellStyle deliveryMethod = workbook.createCellStyle();
        deliveryMethod.cloneStyleFrom(body);
        deliveryMethod.setFont(methodFont);
        styles.put("deliveryMethod", deliveryMethod);

        CellStyle summaryLabel = workbook.createCellStyle();
        summaryLabel.cloneStyleFrom(label);
        summaryLabel.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
        summaryLabel.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        styles.put("summaryLabel", summaryLabel);

        CellStyle summaryValue = workbook.createCellStyle();
        summaryValue.cloneStyleFrom(bodyCenter);
        summaryValue.setFont(boldFont);
        styles.put("summaryValue", summaryValue);

        CellStyle footer = workbook.createCellStyle();
        footer.setFont(normalFont);
        footer.setAlignment(HorizontalAlignment.LEFT);
        footer.setVerticalAlignment(VerticalAlignment.CENTER);
        footer.setBorderTop(BorderStyle.THIN);
        styles.put("footer", footer);

        CellStyle signature = workbook.createCellStyle();
        signature.setFont(boldFont);
        signature.setAlignment(HorizontalAlignment.RIGHT);
        signature.setVerticalAlignment(VerticalAlignment.CENTER);
        signature.setBorderTop(BorderStyle.THIN);
        signature.setBorderBottom(BorderStyle.THIN);
        styles.put("signature", signature);

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
        String type = DOCUMENT_PARCEL.equals(page.getDocumentType()) ? "택배" : "현장";
        String name = String.format("%03d_%s명세서", index, type);
        return name.length() <= 31 ? name : name.substring(0, 31);
    }

    private String formatNumber(long value) {
        return String.format("%,d", value);
    }

    private String formatMoney(long value) {
        return formatNumber(value) + "원";
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

    private String normalizeKeyText(String value) {
        return safeText(value).replaceAll("\\s+", "").toLowerCase();
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

    private record RecipientData(String name, String phone) {
    }

    private record AddressData(String postalCode, String addressText) {
    }

    private static final class StatementGroup {
        private Long taskId;
        private String documentType;
        private String documentTypeLabel;
        private String companyName;
        private String requesterName;
        private String managedByName;
        private String orderDateText;
        private String recipientLabel;
        private String recipientName;
        private String contactLabel;
        private String recipientPhone;
        private String addressLabel;
        private String postalCode;
        private String addressText;
        private String deliveryMethodName;
        private String auxiliaryLabel;

        private long totalQuantity;
        private long packingCost;
        private long deliveryCost;
        private long totalAmount;

        private final LinkedHashSet<Long> orderIds = new LinkedHashSet<>();
        private final LinkedHashSet<String> deliveryDateTexts = new LinkedHashSet<>();
        private final LinkedHashSet<String> deliveryHandlerNames = new LinkedHashSet<>();
        private final LinkedHashSet<String> deliveryOrderIndexes = new LinkedHashSet<>();
        private final LinkedHashSet<String> notes = new LinkedHashSet<>();
        private final List<StatementItemDto> items = new ArrayList<>();
    }
}
