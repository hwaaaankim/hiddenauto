package com.dev.HiddenBATHAuto.service.team.delivery;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

import com.dev.HiddenBATHAuto.dto.delivery.route.DeliveryRouteDtos.Group;
import com.dev.HiddenBATHAuto.dto.delivery.route.DeliveryRouteDtos.OrderRow;
import com.dev.HiddenBATHAuto.dto.delivery.route.DeliveryRouteDtos.Page;
import com.dev.HiddenBATHAuto.dto.delivery.route.DeliveryRouteDtos.PrintRow;
import com.dev.HiddenBATHAuto.model.auth.Company;
import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.task.DeliveryOrderIndex;
import com.dev.HiddenBATHAuto.model.task.Order;
import com.dev.HiddenBATHAuto.model.task.OrderItem;
import com.dev.HiddenBATHAuto.model.task.OrderStatus;
import com.dev.HiddenBATHAuto.repository.order.DeliveryRouteQueryRepository;
import com.dev.HiddenBATHAuto.utils.DeliveryAddressNormalizationUtil;
import com.dev.HiddenBATHAuto.utils.DeliveryAddressNormalizationUtil.AddressValue;
import com.dev.HiddenBATHAuto.utils.DeliveryProductDisplayUtil;
import com.dev.HiddenBATHAuto.utils.OrderItemOptionJsonUtil;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DeliveryRouteService {

    private static final String DELIVERY_TEAM_NAME = "배송팀";
    private static final String DIRECT_METHOD_NAME = "직배송";
    private static final String SITE_METHOD_NAME = "현장배송";
    private static final String FREIGHT_METHOD_NAME = "화물";

    private static final String SECTION_DIRECT = "DIRECT";
    private static final String SECTION_FREIGHT = "FREIGHT";

    private static final int MAX_BULK_COMPLETE_ORDER_COUNT = 200;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final Pattern SIGNED_INTEGER_PATTERN = Pattern.compile("[-+]?\\d+");

    private static final List<OrderStatus> VISIBLE_STATUSES = List.of(
            OrderStatus.CONFIRMED,
            OrderStatus.PRODUCTION_DONE,
            OrderStatus.DISPATCH_DONE,
            OrderStatus.DELIVERY_DONE
    );

    private final DeliveryRouteQueryRepository deliveryRouteQueryRepository;

    /**
     * 당일 배송목록을 업체 + 실제 배송지 + 배송수단 기준으로 묶습니다.
     *
     * 정렬 규칙:
     * - DeliveryOrderIndex.orderIndex가 가장 빠른 주문의 위치가 묶음의 위치가 됩니다.
     * - 같은 업체/같은 주소/같은 배송수단 주문이 뒤쪽 인덱스에 다시 등장해도 최초 묶음으로 합칩니다.
     * - 같은 업체라도 주소 또는 배송수단이 다르면 별도 묶음입니다.
     * - 직배송/현장배송 묶음을 먼저, 화물 묶음을 그 다음에 표시합니다.
     * - 각 섹션 안에서는 미완료 주문이 하나라도 남은 묶음을 먼저 표시하고, 전체 배송완료 묶음을 하단에 표시합니다.
     * - 묶음 내부에서도 미완료 주문을 먼저, 배송완료 주문을 뒤에 표시합니다.
     * - 택배/방문/미배송/미지정은 이 화면에서 제외합니다.
     */
    @Transactional(readOnly = true)
    public Page getRoutePage(Member loginMember, LocalDate deliveryDate) {
        validateDeliveryTeamMember(loginMember);

        LocalDate targetDate = deliveryDate == null ? LocalDate.now() : deliveryDate;
        List<DeliveryOrderIndex> allRows = loadRows(loginMember.getId(), targetDate);

        List<DeliveryOrderIndex> directRows = new ArrayList<>();
        List<DeliveryOrderIndex> freightRows = new ArrayList<>();

        for (DeliveryOrderIndex row : allRows) {
            Order order = row != null ? row.getOrder() : null;
            String methodName = normalizedDeliveryMethodName(order);

            if (DIRECT_METHOD_NAME.equals(methodName) || SITE_METHOD_NAME.equals(methodName)) {
                directRows.add(row);
            } else if (FREIGHT_METHOD_NAME.equals(methodName)) {
                freightRows.add(row);
            }
        }

        List<Group> directGroups = buildGroups(directRows, SECTION_DIRECT, 1);
        List<Group> freightGroups = buildGroups(
                freightRows,
                SECTION_FREIGHT,
                directGroups.size() + 1
        );

        int directOrderCount = directGroups.stream().mapToInt(Group::getOrderCount).sum();
        int freightOrderCount = freightGroups.stream().mapToInt(Group::getOrderCount).sum();
        int deliveryDoneCount = directGroups.stream().mapToInt(Group::getDeliveryDoneCount).sum()
                + freightGroups.stream().mapToInt(Group::getDeliveryDoneCount).sum();

        return Page.builder()
                .deliveryDate(targetDate)
                .handlerId(loginMember.getId())
                .handlerName(resolveMemberName(loginMember))
                .directGroups(directGroups)
                .freightGroups(freightGroups)
                .directGroupCount(directGroups.size())
                .freightGroupCount(freightGroups.size())
                .totalGroupCount(directGroups.size() + freightGroups.size())
                .directOrderCount(directOrderCount)
                .freightOrderCount(freightOrderCount)
                .totalOrderCount(directOrderCount + freightOrderCount)
                .deliveryDoneCount(deliveryDoneCount)
                .build();
    }

    /**
     * 업체별 배송 화면에서 선택된 주문을 배송완료 처리하기 전에 서버 기준으로 다시 검증합니다.
     *
     * 검증 항목:
     * - 현재 로그인 배송 담당자의 해당 날짜 DeliveryOrderIndex인지
     * - 직배송/현장배송 + 생산완료(PRODUCTION_DONE) 주문인지
     * - 선택된 모든 주문이 같은 업체/같은 실제 주소/같은 배송수단 묶음인지
     */
    @Transactional(readOnly = true)
    public List<Long> validateCompletionSelection(
            Member loginMember,
            LocalDate deliveryDate,
            List<Long> selectedOrderIds
    ) {
        validateDeliveryTeamMember(loginMember);

        LocalDate targetDate = deliveryDate == null ? LocalDate.now() : deliveryDate;
        List<Long> requestedIds = normalizeOrderIds(selectedOrderIds);

        if (requestedIds.size() > MAX_BULK_COMPLETE_ORDER_COUNT) {
            throw new IllegalArgumentException(
                    "한 번에 배송완료 처리할 수 있는 주문은 최대 "
                            + MAX_BULK_COMPLETE_ORDER_COUNT
                            + "건입니다."
            );
        }

        Map<Long, DeliveryOrderIndex> rowByOrderId = loadRows(loginMember.getId(), targetDate).stream()
                .filter(row -> row.getOrder() != null && row.getOrder().getId() != null)
                .collect(Collectors.toMap(
                        row -> row.getOrder().getId(),
                        row -> row,
                        (a, b) -> a,
                        LinkedHashMap::new
                ));

        List<Long> invalidIds = requestedIds.stream()
                .filter(orderId -> !rowByOrderId.containsKey(orderId))
                .toList();

        if (!invalidIds.isEmpty()) {
            throw new AccessDeniedException(
                    "현재 담당자의 해당 날짜 배송목록에 없는 주문이 포함되어 있습니다: " + invalidIds
            );
        }

        LinkedHashSet<String> groupKeys = new LinkedHashSet<>();

        for (Long orderId : requestedIds) {
            DeliveryOrderIndex indexRow = rowByOrderId.get(orderId);
            Order order = indexRow.getOrder();

            if (!isCompletableOrder(order)) {
                throw new IllegalStateException(
                        "직배송 또는 현장배송의 생산완료 주문만 배송완료 처리할 수 있습니다. "
                                + "선택 항목 중 생산완료가 아닌 주문이 포함되어 있습니다. orderId="
                                + orderId
                );
            }

            groupKeys.add(buildRouteGroupKey(order));
        }

        if (groupKeys.size() != 1) {
            throw new IllegalStateException(
                    "배송완료 일괄처리는 같은 업체, 같은 배송지, 같은 배송수단 주문끼리만 가능합니다."
            );
        }

        return requestedIds;
    }

    /**
     * 배송완료 처리 직후 프론트 화면을 새로고침하지 않고도 정확히 갱신할 수 있도록
     * 처리 대상이 속한 묶음의 최신 상태를 반환합니다.
     *
     * 완료 API 자체의 성공 여부와 화면 보조정보 조회가 강하게 결합되지 않도록
     * 대상 묶음을 찾지 못하면 예외 대신 Optional.empty()를 반환합니다.
     */
    @Transactional(readOnly = true)
    public Optional<CompletionSnapshot> findCompletionSnapshot(
            Member loginMember,
            LocalDate deliveryDate,
            List<Long> completedOrderIds
    ) {
        validateDeliveryTeamMember(loginMember);

        LinkedHashSet<Long> targetOrderIds = completedOrderIds == null
                ? new LinkedHashSet<>()
                : completedOrderIds.stream()
                        .filter(Objects::nonNull)
                        .filter(orderId -> orderId > 0)
                        .collect(Collectors.toCollection(LinkedHashSet::new));

        if (targetOrderIds.isEmpty()) {
            return Optional.empty();
        }

        Page page = getRoutePage(loginMember, deliveryDate);

        for (Group group : page.getDirectGroups()) {
            boolean targetGroup = group.getOrders().stream()
                    .map(OrderRow::getOrderId)
                    .filter(Objects::nonNull)
                    .anyMatch(targetOrderIds::contains);

            if (!targetGroup) {
                continue;
            }

            List<Long> groupOrderIds = group.getOrders().stream()
                    .map(OrderRow::getOrderId)
                    .filter(Objects::nonNull)
                    .toList();

            List<Long> deliveryDoneOrderIds = group.getOrders().stream()
                    .filter(OrderRow::isDeliveryDone)
                    .map(OrderRow::getOrderId)
                    .filter(Objects::nonNull)
                    .toList();

            boolean fullyCompleted = group.getOrderCount() > 0
                    && group.getDeliveryDoneCount() == group.getOrderCount();

            return Optional.of(new CompletionSnapshot(
                    groupOrderIds,
                    deliveryDoneOrderIds,
                    group.getOrderCount(),
                    group.getCompletableOrderCount(),
                    group.getDeliveryDoneCount(),
                    fullyCompleted,
                    page.getDeliveryDoneCount()
            ));
        }

        return Optional.empty();
    }

    /**
     * 기존 배송리스트 DOM 순서대로 A4 인쇄용 데이터를 만듭니다.
     * 현재 담당자와 날짜에 속하지 않는 주문 ID가 섞이면 전체 요청을 거절합니다.
     */
    @Transactional(readOnly = true)
    public List<PrintRow> getPrintRows(
            Member loginMember,
            LocalDate deliveryDate,
            List<Long> orderedOrderIds
    ) {
        validateDeliveryTeamMember(loginMember);

        LocalDate targetDate = deliveryDate == null ? LocalDate.now() : deliveryDate;
        List<Long> requestedIds = orderedOrderIds == null
                ? List.of()
                : orderedOrderIds.stream()
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList();

        if (requestedIds.isEmpty()) {
            throw new IllegalArgumentException("인쇄할 배송 주문이 없습니다.");
        }

        Map<Long, DeliveryOrderIndex> rowByOrderId = loadRows(loginMember.getId(), targetDate).stream()
                .filter(Objects::nonNull)
                .filter(row -> row.getOrder() != null && row.getOrder().getId() != null)
                .collect(Collectors.toMap(
                        row -> row.getOrder().getId(),
                        row -> row,
                        (a, b) -> a,
                        HashMap::new
                ));

        List<Long> invalidIds = requestedIds.stream()
                .filter(orderId -> !rowByOrderId.containsKey(orderId))
                .toList();

        if (!invalidIds.isEmpty()) {
            throw new AccessDeniedException(
                    "현재 담당자의 해당 날짜 배송목록에 없는 주문이 포함되어 있습니다: " + invalidIds
            );
        }

        List<PrintRow> result = new ArrayList<>(requestedIds.size());

        for (Long orderId : requestedIds) {
            DeliveryOrderIndex row = rowByOrderId.get(orderId);
            enrichOrderForDelivery(row.getOrder());
            result.add(toPrintRow(row));
        }

        return result;
    }

    /**
     * 업체별 배송 화면의 일반 데이터 엑셀을 생성합니다.
     *
     * 명세서 다운로드와는 별개의 목록형 엑셀이며, 현재 화면 DOM 순서로 전달된 주문 ID를
     * 그대로 유지합니다. 특히 수량은 문자열이 아니라 부호 있는 숫자 셀로 기록하므로
     * -1, -2 같은 반품/회수 수량이 0으로 바뀌지 않습니다.
     */
    @Transactional(readOnly = true)
    public byte[] createRouteExcel(
            Member loginMember,
            LocalDate deliveryDate,
            List<Long> orderedOrderIds
    ) {
        LocalDate targetDate = deliveryDate == null ? LocalDate.now() : deliveryDate;
        List<PrintRow> rows = getPrintRows(loginMember, targetDate, orderedOrderIds);

        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("배송리스트");
            configureRouteExcelPrint(sheet);
            writeRouteExcelSheet(workbook, sheet, rows, loginMember, targetDate);

            workbook.write(outputStream);
            return outputStream.toByteArray();

        } catch (IOException e) {
            throw new IllegalStateException("배송리스트 엑셀 파일 생성 중 오류가 발생했습니다.", e);
        }
    }

    private void configureRouteExcelPrint(Sheet sheet) {
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

    private void writeRouteExcelSheet(
            Workbook workbook,
            Sheet sheet,
            List<PrintRow> rows,
            Member loginMember,
            LocalDate deliveryDate
    ) {
        CellStyle titleStyle = createRouteExcelTitleStyle(workbook);
        CellStyle infoStyle = createRouteExcelInfoStyle(workbook);
        CellStyle headerStyle = createRouteExcelHeaderStyle(workbook);
        CellStyle bodyStyle = createRouteExcelBodyStyle(workbook);
        CellStyle centerStyle = createRouteExcelCenterStyle(workbook);
        CellStyle memoStyle = createRouteExcelMemoStyle(workbook);
        CellStyle quantityStyle = createRouteExcelQuantityStyle(workbook);

        int rowIndex = 0;

        Row titleRow = sheet.createRow(rowIndex++);
        titleRow.setHeightInPoints(25);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("업체별 배송리스트");
        titleCell.setCellStyle(titleStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 13));

        Row infoRow = sheet.createRow(rowIndex++);
        infoRow.setHeightInPoints(19);
        Cell infoCell = infoRow.createCell(0);
        infoCell.setCellValue(
                "배송일: " + deliveryDate
                        + " | 담당자: " + resolveMemberName(loginMember)
                        + " | 총 " + rows.size() + "건"
        );
        infoCell.setCellStyle(infoStyle);
        sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, 13));

        String[] headers = {
                "순서",
                "주문ID",
                "거래처",
                "배송수단",
                "상태",
                "배송지",
                "주문자",
                "연락처",
                "카테고리",
                "제품명",
                "사이즈",
                "색상",
                "수량",
                "관리자 메모"
        };

        Row headerRow = sheet.createRow(rowIndex++);
        headerRow.setHeightInPoints(23);

        for (int columnIndex = 0; columnIndex < headers.length; columnIndex++) {
            Cell cell = headerRow.createCell(columnIndex);
            cell.setCellValue(headers[columnIndex]);
            cell.setCellStyle(headerStyle);
        }

        for (PrintRow dto : rows) {
            Row row = sheet.createRow(rowIndex++);
            row.setHeightInPoints(39);

            createRouteExcelTextCell(row, 0, String.valueOf(dto.getOrderIndex()), centerStyle);
            createRouteExcelTextCell(row, 1, "#" + safeLong(dto.getOrderId()), centerStyle);
            createRouteExcelTextCell(row, 2, dto.getCompanyName(), bodyStyle);
            createRouteExcelTextCell(row, 3, dto.getDeliveryMethodName(), centerStyle);
            createRouteExcelTextCell(row, 4, dto.getStatusLabel(), centerStyle);
            createRouteExcelTextCell(row, 5, dto.getAddress(), memoStyle);
            createRouteExcelTextCell(row, 6, dto.getOrdererName(), bodyStyle);
            createRouteExcelTextCell(row, 7, dto.getOrdererPhone(), centerStyle);
            createRouteExcelTextCell(row, 8, dto.getCategory(), bodyStyle);
            createRouteExcelTextCell(row, 9, dto.getProductName(), bodyStyle);
            createRouteExcelTextCell(row, 10, dto.getSize(), bodyStyle);
            createRouteExcelTextCell(row, 11, dto.getColor(), bodyStyle);
            createRouteExcelIntegerCell(
                    row,
                    12,
                    parseSignedQuantity(dto.getQuantityText()),
                    quantityStyle
            );
            createRouteExcelTextCell(row, 13, dto.getAdminMemo(), memoStyle);
        }

        int[] widths = {
                8, 11, 22, 15, 13, 42, 14, 16, 16, 28, 18, 13, 9, 36
        };

        for (int columnIndex = 0; columnIndex < widths.length; columnIndex++) {
            sheet.setColumnWidth(columnIndex, widths[columnIndex] * 256);
        }

        sheet.setAutoFilter(new CellRangeAddress(2, Math.max(2, rowIndex - 1), 0, 13));
    }

    private int parseSignedQuantity(String quantityText) {
        Matcher matcher = SIGNED_INTEGER_PATTERN.matcher(safeText(quantityText));

        if (!matcher.find()) {
            return 0;
        }

        try {
            return Integer.parseInt(matcher.group());
        } catch (NumberFormatException e) {
            throw new IllegalStateException("엑셀 수량값을 숫자로 변환할 수 없습니다: " + quantityText, e);
        }
    }

    private void createRouteExcelTextCell(
            Row row,
            int columnIndex,
            String value,
            CellStyle style
    ) {
        Cell cell = row.createCell(columnIndex);
        cell.setCellValue(valueOrDash(value));
        cell.setCellStyle(style);
    }

    private void createRouteExcelIntegerCell(
            Row row,
            int columnIndex,
            int value,
            CellStyle style
    ) {
        Cell cell = row.createCell(columnIndex);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private CellStyle createRouteExcelTitleStyle(Workbook workbook) {
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 16);

        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    private CellStyle createRouteExcelInfoStyle(Workbook workbook) {
        Font font = workbook.createFont();
        font.setFontHeightInPoints((short) 10);

        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    private CellStyle createRouteExcelHeaderStyle(Workbook workbook) {
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 10);
        font.setColor(IndexedColors.WHITE.getIndex());

        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_50_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        applyRouteExcelBorder(style);
        return style;
    }

    private CellStyle createRouteExcelBodyStyle(Workbook workbook) {
        Font font = workbook.createFont();
        font.setFontHeightInPoints((short) 10);

        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setWrapText(true);
        applyRouteExcelBorder(style);
        return style;
    }

    private CellStyle createRouteExcelCenterStyle(Workbook workbook) {
        CellStyle style = createRouteExcelBodyStyle(workbook);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private CellStyle createRouteExcelMemoStyle(Workbook workbook) {
        CellStyle style = createRouteExcelBodyStyle(workbook);
        style.setVerticalAlignment(VerticalAlignment.TOP);
        return style;
    }

    private CellStyle createRouteExcelQuantityStyle(Workbook workbook) {
        CellStyle style = createRouteExcelCenterStyle(workbook);
        style.setDataFormat(workbook.createDataFormat().getFormat("0;-0;0"));
        return style;
    }

    private void applyRouteExcelBorder(CellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
    }

    private List<Long> normalizeOrderIds(List<Long> orderIds) {
        List<Long> normalized = orderIds == null
                ? List.of()
                : orderIds.stream()
                        .filter(Objects::nonNull)
                        .filter(orderId -> orderId > 0)
                        .collect(Collectors.toCollection(LinkedHashSet::new))
                        .stream()
                        .toList();

        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("배송완료 처리할 주문을 1개 이상 선택해 주세요.");
        }

        return normalized;
    }

    private List<DeliveryOrderIndex> loadRows(Long handlerId, LocalDate deliveryDate) {
        return deliveryRouteQueryRepository.findRouteRows(handlerId, deliveryDate, VISIBLE_STATUSES).stream()
                .filter(Objects::nonNull)
                .filter(row -> row.getOrder() != null)
                .sorted(Comparator
                        .comparingInt(DeliveryOrderIndex::getOrderIndex)
                        .thenComparingLong(this::safeOrderId))
                .toList();
    }

    private List<Group> buildGroups(
            List<DeliveryOrderIndex> rows,
            String section,
            int sequenceStart
    ) {
        LinkedHashMap<String, GroupAccumulator> grouped = new LinkedHashMap<>();

        for (DeliveryOrderIndex indexRow : rows) {
            if (indexRow == null || indexRow.getOrder() == null) {
                continue;
            }

            Order order = indexRow.getOrder();
            enrichOrderForDelivery(order);

            AddressInfo addressInfo = resolveActualDeliveryAddress(order);
            Company company = resolveCompany(order);
            String methodName = normalizedDeliveryMethodName(order);
            String groupKey = buildRouteGroupKey(order);

            GroupAccumulator accumulator = grouped.computeIfAbsent(
                    groupKey,
                    ignored -> new GroupAccumulator(
                            section,
                            indexRow.getOrderIndex(),
                            valueOrDash(resolveDeliveryMethodDisplayName(order)),
                            resolveCompanyName(company),
                            addressInfo.display(),
                            addressInfo.zipCode(),
                            resolvePrimaryContact(order)
                    )
            );

            accumulator.firstOrderIndex = Math.min(accumulator.firstOrderIndex, indexRow.getOrderIndex());

            OrderRow orderRow = toOrderRow(indexRow, addressInfo.display());
            accumulator.orders.add(orderRow);
            /*
             * 반품/회수 주문은 음수 수량을 사용하므로 0으로 보정하지 않습니다.
             * 묶음 수량도 주문별 부호를 유지한 순수 합계로 표시합니다.
             */
            accumulator.totalQuantity += orderRow.getQuantity();

            if (orderRow.isDeliveryDone()) {
                accumulator.deliveryDoneCount++;
            }

            if (orderRow.isCompletable()) {
                accumulator.completableOrderCount++;
            }

            if (!StringUtils.hasText(accumulator.primaryContact)
                    || "-".equals(accumulator.primaryContact)) {
                accumulator.primaryContact = resolvePrimaryContact(order);
            }

            if (!StringUtils.hasText(accumulator.deliveryMethodName)
                    || "-".equals(accumulator.deliveryMethodName)) {
                accumulator.deliveryMethodName = valueOrDash(methodName);
            }
        }

        List<GroupAccumulator> accumulators = new ArrayList<>(grouped.values());
        accumulators.sort(Comparator
                .comparing((GroupAccumulator accumulator) -> isFullyCompleted(accumulator))
                .thenComparingInt(accumulator -> accumulator.firstOrderIndex));

        List<Group> result = new ArrayList<>(accumulators.size());
        int sequence = sequenceStart;
        int domSequence = 1;

        for (GroupAccumulator accumulator : accumulators) {
            accumulator.orders.sort(Comparator
                    .comparing((OrderRow order) -> order.isDeliveryDone())
                    .thenComparingInt(OrderRow::getOrderIndex)
                    .thenComparingLong(order -> safeLong(order.getOrderId())));

            String domId = "delivery-route-"
                    + section.toLowerCase(Locale.ROOT)
                    + "-group-"
                    + domSequence++;

            result.add(Group.builder()
                    .domId(domId)
                    .section(section)
                    .sequence(sequence++)
                    .firstOrderIndex(accumulator.firstOrderIndex)
                    .deliveryMethodName(valueOrDash(accumulator.deliveryMethodName))
                    .companyName(accumulator.companyName)
                    .address(accumulator.address)
                    .zipCode(accumulator.zipCode)
                    .primaryContact(valueOrDash(accumulator.primaryContact))
                    .orders(List.copyOf(accumulator.orders))
                    .orderCount(accumulator.orders.size())
                    .completableOrderCount(accumulator.completableOrderCount)
                    .totalQuantity(accumulator.totalQuantity)
                    .deliveryDoneCount(accumulator.deliveryDoneCount)
                    .build());
        }

        return result;
    }

    private boolean isFullyCompleted(GroupAccumulator accumulator) {
        return accumulator != null
                && !accumulator.orders.isEmpty()
                && accumulator.deliveryDoneCount == accumulator.orders.size();
    }

    private String buildRouteGroupKey(Order order) {
        Long orderId = order != null ? order.getId() : null;
        Company company = resolveCompany(order);
        AddressInfo addressInfo = resolveActualDeliveryAddress(order);
        String methodKey = normalizedDeliveryMethodName(order);

        String companyKey;

        if (company != null && company.getId() != null) {
            companyKey = "COMPANY:" + company.getId();
        } else if (company != null && StringUtils.hasText(company.getCompanyName())) {
            companyKey = "COMPANY-NAME:" + normalizeGeneralKey(company.getCompanyName());
        } else {
            companyKey = "MISSING-COMPANY-ORDER:" + safeLong(orderId);
        }

        String addressKey = StringUtils.hasText(addressInfo.key())
                ? addressInfo.key()
                : "MISSING-ADDRESS-ORDER:" + safeLong(orderId);

        String safeMethodKey = StringUtils.hasText(methodKey)
                ? methodKey
                : "MISSING-METHOD-ORDER:" + safeLong(orderId);

        return safeMethodKey + "|" + companyKey + "|" + addressKey;
    }

    private OrderRow toOrderRow(DeliveryOrderIndex indexRow, String address) {
        Order order = indexRow.getOrder();
        OrderItem item = order.getOrderItem();
        OrderStatus status = order.getStatus();
        int quantity = resolveQuantity(order, item);

        String category = firstNonBlank(
                item != null ? item.getDeliveryCategoryText() : null,
                order.getProductCategory() != null ? order.getProductCategory().getName() : null
        );

        String productName = firstNonBlank(
                item != null ? item.getDeliveryProductName() : null,
                item != null ? item.getProductName() : null
        );

        String quantityText = resolveQuantityText(item, quantity, true);

        return OrderRow.builder()
                .orderId(order.getId())
                .taskId(order.getTask() != null ? order.getTask().getId() : null)
                .orderIndex(indexRow.getOrderIndex())
                .status(status != null ? status.name() : "")
                .statusLabel(status != null ? status.getLabel() : "-")
                .deliveryMethodName(valueOrDash(resolveDeliveryMethodDisplayName(order)))
                .address(valueOrDash(address))
                .ordererName(valueOrDash(order.getOrdererName()))
                .ordererPhone(valueOrDash(order.getOrdererPhone()))
                .category(valueOrDash(category))
                .productName(valueOrDash(productName))
                .size(valueOrDash(item != null ? item.getDeliverySizeText() : null))
                .color(valueOrDash(item != null ? item.getDeliveryColorText() : null))
                .quantity(quantity)
                .quantityText(valueOrDash(quantityText))
                .adminMemo(valueOrDash(cleanMemo(order.getAdminMemo())))
                .orderComment(valueOrDash(cleanMemo(order.getOrderComment())))
                .preferredDeliveryDateText(order.getPreferredDeliveryDate() != null
                        ? order.getPreferredDeliveryDate().toLocalDate().format(DATE_FORMATTER)
                        : "-")
                .deliveryDone(status == OrderStatus.DELIVERY_DONE)
                .completable(isCompletableOrder(order))
                .build();
    }

    private PrintRow toPrintRow(DeliveryOrderIndex indexRow) {
        Order order = indexRow.getOrder();
        OrderItem item = order.getOrderItem();
        AddressInfo addressInfo = resolveActualDeliveryAddress(order);
        OrderStatus status = order.getStatus();
        int quantity = resolveQuantity(order, item);

        String category = firstNonBlank(
                item != null ? item.getDeliveryCategoryText() : null,
                order.getProductCategory() != null ? order.getProductCategory().getName() : null
        );

        String productName = firstNonBlank(
                item != null ? item.getDeliveryProductName() : null,
                item != null ? item.getProductName() : null
        );

        String quantityText = resolveQuantityText(item, quantity, false);

        return PrintRow.builder()
                .orderId(order.getId())
                .orderIndex(indexRow.getOrderIndex())
                .companyName(resolveCompanyName(resolveCompany(order)))
                .deliveryMethodName(valueOrDash(resolveDeliveryMethodDisplayName(order)))
                .statusLabel(status != null ? status.getLabel() : "-")
                .address(valueOrDash(addressInfo.display()))
                .ordererName(valueOrDash(order.getOrdererName()))
                .ordererPhone(valueOrDash(order.getOrdererPhone()))
                .category(valueOrDash(category))
                .productName(valueOrDash(productName))
                .size(valueOrDash(item != null ? item.getDeliverySizeText() : null))
                .color(valueOrDash(item != null ? item.getDeliveryColorText() : null))
                .quantityText(valueOrDash(quantityText))
                .adminMemo(valueOrDash(cleanMemo(order.getAdminMemo())))
                .build();
    }

    private void enrichOrderForDelivery(Order order) {
        if (order == null || order.getOrderItem() == null) {
            return;
        }

        OrderItemOptionJsonUtil.enrich(order.getOrderItem());
        DeliveryProductDisplayUtil.enrich(order);
    }

    private boolean isCompletableOrder(Order order) {
        if (order == null || order.getStatus() == null) {
            return false;
        }

        String methodName = normalizedDeliveryMethodName(order);
        boolean supportedMethod = DIRECT_METHOD_NAME.equals(methodName)
                || SITE_METHOD_NAME.equals(methodName);

        return supportedMethod
                && order.getStatus() == OrderStatus.PRODUCTION_DONE;
    }

    private AddressInfo resolveActualDeliveryAddress(Order order) {
        if (order == null) {
            return new AddressInfo("", "-", "");
        }

        boolean siteDelivery = SITE_METHOD_NAME.equals(normalizedDeliveryMethodName(order));
        boolean hasSiteAddress = DeliveryAddressNormalizationUtil.hasAnyMeaningfulAddressText(
                order.getSiteDoName(),
                order.getSiteSiName(),
                order.getSiteGuName(),
                order.getSiteRoadAddress(),
                order.getSiteDetailAddress()
        );

        if (siteDelivery && hasSiteAddress) {
            return buildAddressInfo(
                    order.getSiteZipCode(),
                    order.getSiteDoName(),
                    order.getSiteSiName(),
                    order.getSiteGuName(),
                    order.getSiteRoadAddress(),
                    order.getSiteDetailAddress()
            );
        }

        return buildAddressInfo(
                order.getZipCode(),
                order.getDoName(),
                order.getSiName(),
                order.getGuName(),
                order.getRoadAddress(),
                order.getDetailAddress()
        );
    }

    private AddressInfo buildAddressInfo(
            String zipCode,
            String doName,
            String siName,
            String guName,
            String roadAddress,
            String detailAddress
    ) {
        AddressValue normalizedAddress = DeliveryAddressNormalizationUtil.build(
                zipCode,
                doName,
                siName,
                guName,
                roadAddress,
                detailAddress
        );

        return new AddressInfo(
                normalizedAddress.key(),
                normalizedAddress.display(),
                normalizedAddress.zipCode()
        );
    }

    private Company resolveCompany(Order order) {
        if (order == null
                || order.getTask() == null
                || order.getTask().getRequestedBy() == null) {
            return null;
        }

        return order.getTask().getRequestedBy().getCompany();
    }

    private String resolveCompanyName(Company company) {
        return company != null && StringUtils.hasText(company.getCompanyName())
                ? company.getCompanyName().trim()
                : "업체 미확인";
    }

    private String resolvePrimaryContact(Order order) {
        return firstNonBlank(
                order != null ? order.getOrdererPhone() : null,
                order != null
                        && order.getTask() != null
                        && order.getTask().getRequestedBy() != null
                        ? order.getTask().getRequestedBy().getPhone()
                        : null,
                "-"
        );
    }

    /**
     * 수량은 반품/회수 처리를 위해 음수를 허용합니다.
     *
     * OrderItem 수량이 0이 아닌 경우 기존 우선순위를 유지하고,
     * 과거 데이터처럼 OrderItem 수량이 0인 경우에는 Order 수량으로 보완합니다.
     * 어느 경우에도 음수를 0으로 보정하지 않습니다.
     */
    private int resolveQuantity(Order order, OrderItem item) {
        if (item != null && item.getQuantity() != 0) {
            return item.getQuantity();
        }

        return order != null ? order.getQuantity() : 0;
    }

    /**
     * DeliveryProductDisplayUtil이 만든 표시 문자열은 양수/0 수량에서 우선 사용하되,
     * 음수 수량은 기존 표시 문자열이 누락되거나 0으로 만들어졌을 가능성이 있으므로
     * 실제 수량값을 기준으로 직접 표시합니다.
     */
    private String resolveQuantityText(OrderItem item, int quantity, boolean includeLabel) {
        String prefix = includeLabel ? "수량 " : "";

        if (quantity < 0) {
            return prefix + quantity + "개";
        }

        return firstNonBlank(
                item != null ? item.getDeliveryQuantityText() : null,
                prefix + quantity + "개"
        );
    }

    private String resolveDeliveryMethodDisplayName(Order order) {
        if (order == null || order.getDeliveryMethod() == null) {
            return "";
        }

        return safeText(order.getDeliveryMethod().getMethodName());
    }

    private String normalizedDeliveryMethodName(Order order) {
        return normalizeMethodName(resolveDeliveryMethodDisplayName(order));
    }

    private String normalizeMethodName(String value) {
        return safeText(value)
                .replaceAll("\\(금액:.*?\\)", "")
                .replaceAll("\\s+", "")
                .trim();
    }


    private String normalizeGeneralKey(String value) {
        return Normalizer.normalize(
                safeText(value),
                Normalizer.Form.NFKC
        )
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", "")
                .trim();
    }

    private String cleanMemo(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }

        return value
                .replace("\r", " ")
                .replace("\n", " ")
                .replace("\t", " ")
                .replaceAll("\\s{2,}", " ")
                .trim();
    }


    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }

        for (String value : values) {
            if (StringUtils.hasText(value) && !"-".equals(value.trim())) {
                return value.trim();
            }
        }

        return "";
    }

    private String valueOrDash(String value) {
        return StringUtils.hasText(value) ? value.trim() : "-";
    }

    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }

    private long safeLong(Long value) {
        return value == null ? Long.MAX_VALUE : value;
    }

    private long safeOrderId(DeliveryOrderIndex index) {
        return index == null || index.getOrder() == null || index.getOrder().getId() == null
                ? Long.MAX_VALUE
                : index.getOrder().getId();
    }

    private String resolveMemberName(Member member) {
        if (member == null) {
            return "-";
        }

        return valueOrDash(firstNonBlank(member.getName(), member.getUsername()));
    }

    private void validateDeliveryTeamMember(Member member) {
        if (member == null
                || member.getTeam() == null
                || !DELIVERY_TEAM_NAME.equals(member.getTeam().getName())) {
            throw new AccessDeniedException("배송팀만 접근할 수 있습니다.");
        }
    }

    public record CompletionSnapshot(
            List<Long> groupOrderIds,
            List<Long> deliveryDoneOrderIds,
            int groupOrderCount,
            int groupCompletableOrderCount,
            int groupDeliveryDoneCount,
            boolean groupFullyCompleted,
            int pageDeliveryDoneCount
    ) {
        public CompletionSnapshot {
            groupOrderIds = groupOrderIds == null ? List.of() : List.copyOf(groupOrderIds);
            deliveryDoneOrderIds = deliveryDoneOrderIds == null
                    ? List.of()
                    : List.copyOf(deliveryDoneOrderIds);
        }
    }

    private record AddressInfo(String key, String display, String zipCode) {
    }

    private static class GroupAccumulator {
        private final String section;
        private int firstOrderIndex;
        private String deliveryMethodName;
        private final String companyName;
        private final String address;
        private final String zipCode;
        private String primaryContact;
        private final List<OrderRow> orders = new ArrayList<>();
        private int totalQuantity;
        private int deliveryDoneCount;
        private int completableOrderCount;

        private GroupAccumulator(
                String section,
                int firstOrderIndex,
                String deliveryMethodName,
                String companyName,
                String address,
                String zipCode,
                String primaryContact
        ) {
            this.section = section;
            this.firstOrderIndex = firstOrderIndex;
            this.deliveryMethodName = deliveryMethodName;
            this.companyName = companyName;
            this.address = address;
            this.zipCode = zipCode;
            this.primaryContact = primaryContact;
        }
    }
}
