package com.dev.HiddenBATHAuto.service.team.delivery;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackagePartName;
import org.apache.poi.openxml4j.opc.PackageRelationship;
import org.apache.poi.openxml4j.opc.PackagingURIHelper;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.dev.HiddenBATHAuto.dto.delivery.DeliveryStatementExcelRequest;
import com.dev.HiddenBATHAuto.model.auth.Company;
import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.task.Order;
import com.dev.HiddenBATHAuto.model.task.OrderItem;
import com.dev.HiddenBATHAuto.model.task.Task;
import com.dev.HiddenBATHAuto.repository.order.OrderRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DeliveryStatementExcelService {

    private static final String TYPE_SITE = "SITE";
    private static final String TYPE_PARCEL = "PARCEL";

    private static final String DELIVERY_METHOD_DIRECT = "직배송";
    private static final String DELIVERY_METHOD_SITE = "현장배송";
    private static final String DELIVERY_METHOD_PARCEL = "택배";

    private static final String SITE_TEMPLATE_PATH = "excel/delivery-statement/site_statement_template.xlsx";
    private static final String PARCEL_TEMPLATE_PATH = "excel/delivery-statement/parcel_statement_template.xlsx";

    private static final String SITE_SHEET_NAME = "현장명세서";
    private static final String PARCEL_SHEET_NAME = "택배명세서";

    private static final DateTimeFormatter YMD = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final OrderRepository orderRepository;
    private final ObjectMapper objectMapper;

    /**
     * 선택된 오더를 명세서 구분별 배송수단으로 필터링한 뒤 Task 단위로 묶어 ZIP으로 내려줍니다.
     *
     * - 현장명세서: deliveryMethod.methodName 이 직배송 또는 현장배송인 오더만 출력
     * - 택배명세서: deliveryMethod.methodName 이 택배인 오더만 출력
     * - 같은 Task 안에서 선택된 오더들은 하나의 엑셀 파일에 품목 여러 줄로 출력
     * - 선택하지 않은 같은 Task의 다른 오더는 출력하지 않음
     */
    @Transactional(readOnly = true)
    public byte[] buildZip(DeliveryStatementExcelRequest request, Member loginMember) throws IOException {
        if (request == null) {
            throw new IllegalArgumentException("잘못된 요청입니다.(요청 데이터 누락)");
        }

        String statementType = normalizeStatementType(request.getStatementType());
        List<Long> orderedOrderIds = normalizeOrderIds(request.getOrderIds());

        if (orderedOrderIds.isEmpty()) {
            throw new IllegalArgumentException("명세서로 출력할 주문을 하나 이상 선택해 주세요.");
        }

        List<Order> orders = loadOrdersInRequestOrder(orderedOrderIds);

        if (orders.isEmpty()) {
            throw new IllegalArgumentException("출력 가능한 주문을 찾지 못했습니다.");
        }

        List<Order> filteredOrders = filterOrdersByStatementType(orders, statementType);

        if (filteredOrders.isEmpty()) {
            if (TYPE_SITE.equals(statementType)) {
                throw new IllegalArgumentException("현장명세서는 배송수단이 직배송 또는 현장배송인 주문만 출력할 수 있습니다.");
            }

            throw new IllegalArgumentException("택배명세서는 배송수단이 택배인 주문만 출력할 수 있습니다.");
        }

        List<StatementTaskGroup> taskGroups = groupSelectedOrdersByTask(filteredOrders);

        if (taskGroups.isEmpty()) {
            throw new IllegalArgumentException("출력 가능한 주문 묶음을 찾지 못했습니다.");
        }

        return buildZipWorkbookFiles(statementType, taskGroups, loginMember);
    }

    public String normalizeStatementType(String statementType) {
        String normalized = statementType == null ? "" : statementType.trim().toUpperCase();

        if (TYPE_SITE.equals(normalized) || TYPE_PARCEL.equals(normalized)) {
            return normalized;
        }

        throw new IllegalArgumentException("명세서 구분이 올바르지 않습니다.(SITE/PARCEL)");
    }

    private List<Long> normalizeOrderIds(List<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return List.of();
        }

        return orderIds.stream()
                .filter(Objects::nonNull)
                .filter(id -> id > 0)
                .collect(Collectors.toCollection(LinkedHashSet::new))
                .stream()
                .toList();
    }

    private List<Order> loadOrdersInRequestOrder(List<Long> orderedOrderIds) {
        List<Order> loadedOrders = orderRepository.findAllForDeliveryStatementByIds(orderedOrderIds);

        Map<Long, Order> orderMap = loadedOrders.stream()
                .filter(order -> order.getId() != null)
                .collect(Collectors.toMap(Order::getId, order -> order, (a, b) -> a, LinkedHashMap::new));

        List<Order> result = new ArrayList<>();

        for (Long orderId : orderedOrderIds) {
            Order order = orderMap.get(orderId);

            if (order != null) {
                result.add(order);
            }
        }

        return result;
    }

    private List<Order> filterOrdersByStatementType(List<Order> orders, String statementType) {
        if (orders == null || orders.isEmpty()) {
            return List.of();
        }

        return orders.stream()
                .filter(Objects::nonNull)
                .filter(order -> isAllowedDeliveryMethod(order, statementType))
                .toList();
    }

    private boolean isAllowedDeliveryMethod(Order order, String statementType) {
        String methodName = normalizeDeliveryMethodName(resolveDeliveryMethodName(order));

        if (TYPE_SITE.equals(statementType)) {
            return DELIVERY_METHOD_DIRECT.equals(methodName) || DELIVERY_METHOD_SITE.equals(methodName);
        }

        return DELIVERY_METHOD_PARCEL.equals(methodName);
    }

    private String normalizeDeliveryMethodName(String methodName) {
        return text(methodName).replaceAll("\\s+", "");
    }

    /**
     * 선택된 오더만 기준으로 Task 단위 명세서를 만듭니다.
     * 같은 Task에 속한 오더가 여러 개 선택되면 한 개의 엑셀 파일 품목란에 선택된 오더 품목만 줄 단위로 출력합니다.
     */
    private List<StatementTaskGroup> groupSelectedOrdersByTask(List<Order> orders) {
        LinkedHashMap<String, StatementTaskGroup> grouped = new LinkedHashMap<>();

        for (Order order : orders) {
            if (order == null || order.getId() == null) {
                continue;
            }

            Task task = order.getTask();
            String key = task != null && task.getId() != null
                    ? "TASK:" + task.getId()
                    : "ORDER:" + order.getId();

            StatementTaskGroup group = grouped.computeIfAbsent(key, ignored -> new StatementTaskGroup(task));
            group.addOrder(order);
        }

        return grouped.values().stream()
                .filter(group -> !group.orders().isEmpty())
                .toList();
    }

    private byte[] buildZipWorkbookFiles(String statementType, List<StatementTaskGroup> taskGroups, Member loginMember)
            throws IOException {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream, StandardCharsets.UTF_8)) {

            String prefix = TYPE_SITE.equals(statementType) ? "현장명세서" : "택배명세서";
            int index = 1;

            for (StatementTaskGroup taskGroup : taskGroups) {
                byte[] workbookBytes = TYPE_SITE.equals(statementType)
                        ? buildSingleSiteStatementWorkbook(taskGroup, loginMember)
                        : buildSingleParcelStatementWorkbook(taskGroup, loginMember);

                String entryName = buildEntryFilename(prefix, index, taskGroup.representative());
                zipOutputStream.putNextEntry(new ZipEntry(entryName));
                zipOutputStream.write(workbookBytes);
                zipOutputStream.closeEntry();

                index++;
            }

            zipOutputStream.finish();
            return outputStream.toByteArray();
        }
    }

    private String buildEntryFilename(String prefix, int index, Order representative) {
        String companyName = sanitizeFilenameToken(resolveCompanyName(representative));
        String receiverName = sanitizeFilenameToken(resolveReceiverName(representative));

        List<String> tokens = new ArrayList<>();
        tokens.add(prefix);
        tokens.add(String.format("%03d", index));

        if (StringUtils.hasText(companyName)) {
            tokens.add(companyName);
        }

        if (StringUtils.hasText(receiverName)) {
            tokens.add(receiverName);
        }

        return String.join("_", tokens) + ".xlsx";
    }

    private String sanitizeFilenameToken(String value) {
        String text = text(value);

        if (!StringUtils.hasText(text)) {
            return "";
        }

        return text.replaceAll("[\\\\/:*?\"<>|]", "")
                .replaceAll("\\s+", "")
                .trim();
    }

    private byte[] buildSingleSiteStatementWorkbook(StatementTaskGroup taskGroup, Member loginMember) throws IOException {
        try (InputStream inputStream = new ClassPathResource(SITE_TEMPLATE_PATH).getInputStream();
             XSSFWorkbook workbook = new XSSFWorkbook(inputStream);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            XSSFSheet sheet = keepOnlySheet(workbook, SITE_SHEET_NAME);
            clearAllFormulaCells(sheet);
            clearSiteTemplateDynamicCells(sheet);
            fillSiteSheet(sheet, taskGroup, loginMember);

            workbook.setPrintArea(workbook.getSheetIndex(sheet), 0, 11, 0, 12);
            removeFormulaAndCalculationArtifacts(workbook);
            sheet.setForceFormulaRecalculation(false);
            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    private byte[] buildSingleParcelStatementWorkbook(StatementTaskGroup taskGroup, Member loginMember) throws IOException {
        try (InputStream inputStream = new ClassPathResource(PARCEL_TEMPLATE_PATH).getInputStream();
             XSSFWorkbook workbook = new XSSFWorkbook(inputStream);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            XSSFSheet sheet = keepOnlySheet(workbook, PARCEL_SHEET_NAME);
            clearAllFormulaCells(sheet);
            clearParcelTemplateDynamicCells(sheet);
            fillParcelSheet(sheet, taskGroup, loginMember);

            workbook.setPrintArea(workbook.getSheetIndex(sheet), 1, 6, 0, 16);
            removeFormulaAndCalculationArtifacts(workbook);
            sheet.setForceFormulaRecalculation(false);
            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    private XSSFSheet keepOnlySheet(XSSFWorkbook workbook, String sheetName) {
        XSSFSheet sheet = workbook.getSheet(sheetName);

        if (sheet == null) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new IllegalArgumentException("엑셀 양식에 시트가 없습니다. sheetName=" + sheetName);
            }

            sheet = workbook.getSheetAt(0);
            workbook.setSheetName(workbook.getSheetIndex(sheet), sheetName);
        }

        for (int i = workbook.getNumberOfSheets() - 1; i >= 0; i--) {
            if (!sheetName.equals(workbook.getSheetName(i))) {
                workbook.removeSheetAt(i);
            }
        }

        return workbook.getSheet(sheetName);
    }

    private void clearAllFormulaCells(Sheet sheet) {
        if (sheet == null) {
            return;
        }

        for (Row row : sheet) {
            if (row == null) {
                continue;
            }

            for (Cell cell : row) {
                if (cell != null && cell.getCellType() == CellType.FORMULA) {
                    setCellText(sheet, cell.getRowIndex(), cell.getColumnIndex(), "");
                }
            }
        }
    }

    /**
     * 템플릿에 남아 있는 calcChain.xml, 외부참조, 계산 속성을 제거합니다.
     *
     * 셀 수식을 값으로 비운 뒤에도 /xl/calcChain.xml 이 남아 있으면 Excel에서
     * "제거된 레코드: /xl/calcChain.xml 부분의 수식" 복구 경고가 발생할 수 있습니다.
     * 명세서 양식은 수식이 필요 없으므로 출력 직전에 계산 관련 패키지 조각을 제거합니다.
     */
    private void removeFormulaAndCalculationArtifacts(XSSFWorkbook workbook) {
        if (workbook == null) {
            return;
        }

        try {
            if (workbook.getCTWorkbook() != null && workbook.getCTWorkbook().isSetCalcPr()) {
                workbook.getCTWorkbook().unsetCalcPr();
            }
        } catch (Exception ignore) {
            // POI/OOXML 버전에 따라 calcPr 접근이 실패하더라도 파일 생성은 계속 진행합니다.
        }

        try {
            if (workbook.getCTWorkbook() != null && workbook.getCTWorkbook().isSetExternalReferences()) {
                workbook.getCTWorkbook().unsetExternalReferences();
            }
        } catch (Exception ignore) {
            // 외부참조 제거 실패 시에도 아래 패키지 관계 제거를 계속 시도합니다.
        }

        try {
            List<String> relationshipIdsToRemove = new ArrayList<>();

            for (PackageRelationship relationship : workbook.getPackagePart().getRelationships()) {
                String type = relationship.getRelationshipType();

                if ("http://schemas.openxmlformats.org/officeDocument/2006/relationships/calcChain".equals(type)
                        || "http://schemas.openxmlformats.org/officeDocument/2006/relationships/externalLink".equals(type)) {
                    relationshipIdsToRemove.add(relationship.getId());
                }
            }

            for (String relationshipId : relationshipIdsToRemove) {
                workbook.getPackagePart().removeRelationship(relationshipId);
            }
        } catch (Exception ignore) {
            // 관계 제거 실패 시에도 part 제거를 계속 시도합니다.
        }

        try {
            OPCPackage opcPackage = workbook.getPackage();
            removePackagePartIfExists(opcPackage, "/xl/calcChain.xml");
            removePackagePartIfExists(opcPackage, "/xl/externalLinks/externalLink1.xml");
            removePackagePartIfExists(opcPackage, "/xl/externalLinks/_rels/externalLink1.xml.rels");
        } catch (Exception ignore) {
            // 템플릿에 해당 part가 없거나 POI 버전상 제거가 불가능하면 무시합니다.
        }
    }

    private void removePackagePartIfExists(OPCPackage opcPackage, String partName) {
        if (opcPackage == null || !StringUtils.hasText(partName)) {
            return;
        }

        try {
            PackagePartName packagePartName = PackagingURIHelper.createPartName(partName);

            if (opcPackage.containPart(packagePartName)) {
                opcPackage.removePart(packagePartName);
            }
        } catch (Exception ignore) {
            // 없는 part 또는 제거 실패는 출력 차단 사유가 아닙니다.
        }
    }

    private void clearSiteTemplateDynamicCells(Sheet sheet) {
        // 새 양식의 라벨/선/서식은 유지하고, 괄호로 표시된 입력 대상과 이전 출력값만 비웁니다.
        blankCells(sheet,
                "A1", "E1", "L1",
                "A3", "D3", "H3", "K3",
                "A4", "H4",
                "C7", "J7",
                "C8", "J8",
                "C9", "J9",
                "B12", "I12",
                "C13", "J13"
        );
    }

    private void clearParcelTemplateDynamicCells(Sheet sheet) {
        // 새 양식의 라벨/선/서식은 유지하고, 괄호로 표시된 입력 대상과 이전 출력값만 비웁니다.
        blankCells(sheet,
                "C1", "D1", "D3",
                "C5", "F5", "C6", "F6", "C7", "F7", "C8", "C9", "D10", "F10",
                "C13", "F13", "C14", "F14", "C15", "F15", "C16", "C17"
        );
    }

    private void blankCells(Sheet sheet, String... cellRefs) {
        if (cellRefs == null) {
            return;
        }

        for (String cellRef : cellRefs) {
            CellAddressParts address = parseCellAddress(cellRef);
            setCellText(sheet, address.rowIndex(), address.columnIndex(), "");
        }
    }

    /**
     * 현장명세서: 좌측 공급자용, 우측 공급받는자용에 동일한 하차지 정보를 채웁니다.
     */
    private void fillSiteSheet(Sheet sheet, StatementTaskGroup group, Member loginMember) {
        Order representative = group.representativeForHeader();

        String dateText = resolveStatementDate().format(YMD);
        String companyName = resolveCompanyName(representative);
        String deliveryMethodName = resolveDeliveryMethodName(representative);
        String address = resolveDeliveryAddress(representative);
        String receiverName = resolveReceiverName(representative);
        String receiverPhone = resolveReceiverPhone(representative);
        String receiverContact = joinNonBlank(" / ", receiverName, receiverPhone);
        String itemText = buildSelectedItemText(group.orders());
        String note = resolveNoteText(representative);

        // 상단 운송수단 표시. NO/오더ID 성격의 칸은 계속 빈칸으로 둡니다.
        setCellText(sheet, 0, 0, "");
        setCellText(sheet, 0, 4, deliveryMethodName);
        setCellText(sheet, 0, 11, deliveryMethodName);

        // 공급자용(좌측)
        setCellText(sheet, 2, 0, dateText);              // A3 오늘날짜
        setCellText(sheet, 2, 3, "");                   // D3 NO/오더ID 제거
        setCellText(sheet, 3, 0, companyName);           // A4 거래처
        setCellText(sheet, 6, 2, address);               // C7 하차지 주소
        setCellText(sheet, 7, 2, receiverPhone);         // C8 하차지 연락처: 전화번호만
        setCellText(sheet, 8, 2, receiverContact);       // C9 배송자 이름 / 연락처: 주문자명 / 연락처
        setCellText(sheet, 11, 1, itemText);             // B12 출고목록
        setCellText(sheet, 12, 2, note);                 // C13 비고

        // 공급받는자용(우측)
        setCellText(sheet, 2, 7, dateText);              // H3 오늘날짜
        setCellText(sheet, 2, 10, "");                  // K3 NO/오더ID 제거
        setCellText(sheet, 3, 7, companyName);           // H4 거래처
        setCellText(sheet, 6, 9, address);               // J7 하차지 주소
        setCellText(sheet, 7, 9, receiverPhone);         // J8 하차지 연락처: 전화번호만
        setCellText(sheet, 8, 9, receiverContact);       // J9 배송자 이름 / 연락처: 주문자명 / 연락처
        setCellText(sheet, 11, 8, itemText);             // I12 출고목록
        setCellText(sheet, 12, 9, note);                 // J13 비고

        applySiteOutputStyles(sheet, itemText, address, receiverContact, note);
    }

    /**
     * 택배명세서: 한 파일 안에 보관용/출고용을 같은 값으로 채웁니다. 운송장번호와 오더ID/NO 영역은 빈칸입니다.
     */
    private void fillParcelSheet(Sheet sheet, StatementTaskGroup group, Member loginMember) {
        Order representative = group.representativeForHeader();

        String dateText = resolveStatementDate().format(YMD);
        String deliveryMethodName = resolveDeliveryMethodName(representative);
        String packingText = resolvePackingText(group.orders());
        String receiverName = resolveReceiverName(representative);
        String receiverPhone = resolveReceiverPhone(representative);
        String address = resolveDeliveryAddress(representative);
        String itemText = buildSelectedItemText(group.orders());
        String companyName = resolveCompanyName(representative);

        // 가장 상단 날짜
        setCellText(sheet, 0, 2, dateText);             // C1 날짜

        // 보관용
        setCellText(sheet, 4, 2, dateText);             // C5 발송일
        setCellText(sheet, 4, 5, "");                  // F5 운송장번호 빈칸
        setCellText(sheet, 5, 2, deliveryMethodName);   // C6 운임구분
        setCellText(sheet, 5, 5, packingText);          // F6 포장
        setCellText(sheet, 6, 2, receiverName);         // C7 받는분
        setCellText(sheet, 6, 5, receiverPhone);        // F7 전화번호
        setCellText(sheet, 7, 2, address);              // C8 주소
        setCellText(sheet, 8, 2, itemText);             // C9 품명
        setCellText(sheet, 9, 3, companyName);          // D10 거래처
        setCellText(sheet, 9, 5, "");                  // F10 담당자: 출력하지 않음

        // 출고용
        setCellText(sheet, 12, 2, dateText);            // C13 발송일
        setCellText(sheet, 12, 5, "");                 // F13 운송장번호 빈칸
        setCellText(sheet, 13, 2, deliveryMethodName);  // C14 운임구분
        setCellText(sheet, 13, 5, packingText);         // F14 포장
        setCellText(sheet, 14, 2, receiverName);        // C15 받는분
        setCellText(sheet, 14, 5, receiverPhone);       // F15 전화번호
        setCellText(sheet, 15, 2, address);             // C16 주소
        setCellText(sheet, 16, 2, itemText);            // C17 품명

        applyParcelOutputStyles(sheet, itemText, address);
    }

    private void applySiteOutputStyles(Sheet sheet, String itemText, String address, String receiverContact, String note) {
        prepareSiteSheetLayout(sheet);
        applyBorderToRange(sheet, 0, 0, 12, 5);          // 좌측 양식 A1:F13
        applyBorderToRange(sheet, 0, 7, 12, 11);         // 우측 양식 H1:L13

        applyWrappedTextStyle(sheet, 6, 2, address, 10, false, 44, 30.0f);          // C7 주소
        applyWrappedTextStyle(sheet, 6, 9, address, 10, false, 44, 30.0f);          // J7 주소
        applyWrappedTextStyle(sheet, 7, 2, receiverContact, 10, false, 28, 24.0f);  // C8 연락처
        applyWrappedTextStyle(sheet, 7, 9, receiverContact, 10, false, 28, 24.0f);  // J8 연락처
        applyWrappedTextStyle(sheet, 8, 2, receiverContact, 10, false, 28, 24.0f);  // C9 배송자 이름/연락처
        applyWrappedTextStyle(sheet, 8, 9, receiverContact, 10, false, 28, 24.0f);  // J9 배송자 이름/연락처
        applyWrappedTextStyle(sheet, 11, 1, itemText, 14, true, 44, 84.0f);         // B12 출고목록 좌측
        applyWrappedTextStyle(sheet, 11, 8, itemText, 14, true, 44, 84.0f);         // I12 출고목록 우측
        applyWrappedTextStyle(sheet, 12, 2, note, 10, false, 34, 24.0f);            // C13 비고
        applyWrappedTextStyle(sheet, 12, 9, note, 10, false, 34, 24.0f);            // J13 비고
    }

    private void applyParcelOutputStyles(Sheet sheet, String itemText, String address) {
        prepareParcelSheetLayout(sheet);
        applyBorderToRange(sheet, 0, 1, 0, 2);           // 상단 날짜 영역
        applyBorderToRange(sheet, 3, 1, 9, 6);           // 보관용 B4:G10
        applyBorderToRange(sheet, 11, 1, 16, 6);         // 출고용 B12:G17

        applyWrappedTextStyle(sheet, 7, 2, address, 10, false, 44, 32.0f);          // C8 주소
        applyWrappedTextStyle(sheet, 15, 2, address, 10, false, 44, 32.0f);         // C16 주소
        applyWrappedTextStyle(sheet, 8, 2, itemText, 14, true, 42, 76.0f);          // C9 품명 보관용
        applyWrappedTextStyle(sheet, 16, 2, itemText, 14, true, 42, 76.0f);         // C17 품명 출고용
    }

    private void prepareSiteSheetLayout(Sheet sheet) {
        setColumnWidth(sheet, 0, 18);   // A: 라벨/거래처
        setColumnWidth(sheet, 1, 4);    // B
        setColumnWidth(sheet, 2, 42);   // C: 주소/연락처
        setColumnWidth(sheet, 3, 10);   // D
        setColumnWidth(sheet, 4, 20);   // E
        setColumnWidth(sheet, 5, 8);    // F
        setColumnWidth(sheet, 6, 3);    // G: 간격
        setColumnWidth(sheet, 7, 18);   // H: 라벨/거래처
        setColumnWidth(sheet, 8, 4);    // I
        setColumnWidth(sheet, 9, 42);   // J: 주소/연락처
        setColumnWidth(sheet, 10, 10);  // K
        setColumnWidth(sheet, 11, 20);  // L
    }

    private void prepareParcelSheetLayout(Sheet sheet) {
        setColumnWidth(sheet, 1, 14);   // B: 라벨
        setColumnWidth(sheet, 2, 42);   // C: 받는분/주소/품명
        setColumnWidth(sheet, 3, 22);   // D: 거래처
        setColumnWidth(sheet, 4, 14);   // E: 라벨
        setColumnWidth(sheet, 5, 22);   // F: 전화번호/포장
        setColumnWidth(sheet, 6, 13);   // G: 보관용/출고용
    }

    private void setColumnWidth(Sheet sheet, int columnIndex, int widthCharacters) {
        sheet.setColumnWidth(columnIndex, Math.max(1, widthCharacters) * 256);
    }

    private void applyWrappedTextStyle(Sheet sheet, int rowIndex, int columnIndex, String value,
            int fontSize, boolean bold, int charsPerLine, float minimumHeight) {
        Cell cell = getOrCreateCell(sheet, rowIndex, columnIndex);
        Workbook workbook = sheet.getWorkbook();
        CellStyle style = cloneCellStyle(workbook, cell);
        Font font = workbook.createFont();
        font.setFontName("맑은 고딕");
        font.setFontHeightInPoints((short) fontSize);
        font.setBold(bold);
        style.setFont(font);
        style.setWrapText(true);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setAlignment(HorizontalAlignment.LEFT);
        cell.setCellStyle(style);

        Row row = sheet.getRow(rowIndex);
        if (row != null) {
            int estimatedLines = estimateWrappedLineCount(value, charsPerLine);
            float calculatedHeight = Math.max(minimumHeight, 20.0f + estimatedLines * Math.max(16.0f, fontSize + 7.0f));
            row.setHeightInPoints(Math.max(row.getHeightInPoints(), calculatedHeight));
        }
    }

    private int estimateWrappedLineCount(String value, int charsPerLine) {
        String text = text(value);
        if (!StringUtils.hasText(text)) {
            return 1;
        }

        int safeCharsPerLine = Math.max(10, charsPerLine);
        int lineCount = 0;

        for (String line : text.split("\\n", -1)) {
            int length = Math.max(1, line.length());
            lineCount += (int) Math.ceil((double) length / (double) safeCharsPerLine);
        }

        return Math.max(1, lineCount);
    }

    private void applyBorderToRange(Sheet sheet, int firstRow, int firstColumn, int lastRow, int lastColumn) {
        for (int rowIndex = firstRow; rowIndex <= lastRow; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                row = sheet.createRow(rowIndex);
            }

            for (int columnIndex = firstColumn; columnIndex <= lastColumn; columnIndex++) {
                Cell cell = getOrCreateCell(sheet, rowIndex, columnIndex);
                CellStyle style = cloneCellStyle(sheet.getWorkbook(), cell);
                style.setBorderTop(BorderStyle.THIN);
                style.setBorderBottom(BorderStyle.THIN);
                style.setBorderLeft(BorderStyle.THIN);
                style.setBorderRight(BorderStyle.THIN);
                cell.setCellStyle(style);
            }
        }
    }

    private CellStyle cloneCellStyle(Workbook workbook, Cell cell) {
        CellStyle style = workbook.createCellStyle();
        if (cell != null && cell.getCellStyle() != null) {
            style.cloneStyleFrom(cell.getCellStyle());
        }
        return style;
    }

    private Cell getOrCreateCell(Sheet sheet, int rowIndex, int columnIndex) {
        Row row = sheet.getRow(rowIndex);
        if (row == null) {
            row = sheet.createRow(rowIndex);
        }

        Cell cell = row.getCell(columnIndex, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) {
            cell = row.createCell(columnIndex, CellType.STRING);
        }

        return cell;
    }

    private int countTextLines(String value) {
        if (!StringUtils.hasText(value)) {
            return 1;
        }

        return (int) java.util.Arrays.stream(value.split("\n", -1))
                .filter(StringUtils::hasText)
                .count();
    }

    private void setCellText(Sheet sheet, int rowIndex, int columnIndex, String value) {
        Cell cell = getOrCreateCell(sheet, rowIndex, columnIndex);
        CellStyle originalStyle = cell.getCellStyle();

        if (cell.getCellType() == CellType.FORMULA) {
            cell.setCellFormula(null);
        }

        cell.setCellValue(value == null ? "" : value);

        if (originalStyle != null) {
            cell.setCellStyle(originalStyle);
        }
    }

    private CellAddressParts parseCellAddress(String cellRef) {
        if (!StringUtils.hasText(cellRef)) {
            throw new IllegalArgumentException("셀 주소가 비어 있습니다.");
        }

        String normalized = cellRef.trim().toUpperCase();
        int splitIndex = 0;

        while (splitIndex < normalized.length() && Character.isLetter(normalized.charAt(splitIndex))) {
            splitIndex++;
        }

        if (splitIndex == 0 || splitIndex >= normalized.length()) {
            throw new IllegalArgumentException("셀 주소가 올바르지 않습니다. cellRef=" + cellRef);
        }

        String columnLetters = normalized.substring(0, splitIndex);
        String rowText = normalized.substring(splitIndex);

        int columnIndex = 0;
        for (int i = 0; i < columnLetters.length(); i++) {
            columnIndex = columnIndex * 26 + (columnLetters.charAt(i) - 'A' + 1);
        }
        columnIndex--;

        int rowIndex = Integer.parseInt(rowText) - 1;

        return new CellAddressParts(rowIndex, columnIndex);
    }

    private LocalDate resolveStatementDate() {
        return LocalDate.now();
    }

    private LocalDate resolveDeliveryDate(Order order) {
        if (order != null && order.getPreferredDeliveryDate() != null) {
            return order.getPreferredDeliveryDate().toLocalDate();
        }

        return LocalDate.now();
    }

    private String resolveDeliveryAddress(Order order) {
        if (order == null) {
            return "";
        }

        String siteAddress = buildAddress(
                order.getSiteZipCode(),
                order.getSiteDoName(),
                order.getSiteSiName(),
                order.getSiteGuName(),
                order.getSiteRoadAddress(),
                order.getSiteDetailAddress()
        );

        if (StringUtils.hasText(siteAddress)) {
            return siteAddress;
        }

        return buildAddress(
                order.getZipCode(),
                order.getDoName(),
                order.getSiName(),
                order.getGuName(),
                order.getRoadAddress(),
                order.getDetailAddress()
        );
    }

    private boolean hasDeliveryAddress(Order order) {
        return StringUtils.hasText(resolveDeliveryAddress(order));
    }

    private boolean hasReceiver(Order order) {
        return StringUtils.hasText(resolveReceiverName(order)) || StringUtils.hasText(resolveReceiverPhone(order));
    }

    private String resolveNoteText(Order order) {
        if (order == null) {
            return "";
        }

        String orderNote = firstNonBlank(order.getOrderComment(), order.getAdminMemo());
        if (StringUtils.hasText(orderNote)) {
            return orderNote;
        }

        Task task = order.getTask();
        if (task == null) {
            return "";
        }

        return firstNonBlank(task.getCustomerNote(), task.getInternalNote());
    }

    private String buildAddress(String zipCode, String doName, String siName, String guName, String roadAddress,
            String detailAddress) {
        List<String> tokens = new ArrayList<>();

        if (StringUtils.hasText(zipCode)) {
            tokens.add("(" + zipCode.trim() + ")");
        }

        addIfText(tokens, doName);
        addIfText(tokens, siName);
        addIfText(tokens, guName);
        addIfText(tokens, roadAddress);
        addIfText(tokens, detailAddress);

        return String.join(" ", tokens).trim();
    }

    private void addIfText(Collection<String> tokens, String value) {
        if (StringUtils.hasText(value)) {
            tokens.add(value.trim());
        }
    }

    private String resolveReceiverName(Order order) {
        if (order == null) {
            return "";
        }

        String ordererName = text(order.getOrdererName());
        if (StringUtils.hasText(ordererName)) {
            return ordererName;
        }

        Member requestedBy = safeRequestedBy(order);
        return requestedBy != null ? text(requestedBy.getName()) : "";
    }

    private String resolveReceiverPhone(Order order) {
        if (order == null) {
            return "";
        }

        String ordererPhone = text(order.getOrdererPhone());
        if (StringUtils.hasText(ordererPhone)) {
            return ordererPhone;
        }

        Member requestedBy = safeRequestedBy(order);
        if (requestedBy == null) {
            return "";
        }

        String phone = text(requestedBy.getPhone());
        if (StringUtils.hasText(phone)) {
            return phone;
        }

        return text(requestedBy.getTelephone());
    }

    private String resolveCompanyName(Order order) {
        Company company = safeCompany(order);
        return company != null ? text(company.getCompanyName()) : "";
    }

    private String resolveDeliveryMethodName(Order order) {
        if (order == null || order.getDeliveryMethod() == null) {
            return "";
        }

        return text(order.getDeliveryMethod().getMethodName());
    }

    private String resolveLoginMemberName(Member loginMember) {
        return loginMember != null ? text(loginMember.getName()) : "";
    }

    private String resolvePackingText(List<Order> orders) {
        int totalPackingCost = 0;

        if (orders != null) {
            for (Order order : orders) {
                if (order != null) {
                    totalPackingCost += Math.max(0, order.getPackingCost());
                }
            }
        }

        return String.valueOf(totalPackingCost);
    }

    private Member safeRequestedBy(Order order) {
        if (order == null || order.getTask() == null) {
            return null;
        }

        return order.getTask().getRequestedBy();
    }

    private Company safeCompany(Order order) {
        Member requestedBy = safeRequestedBy(order);

        if (requestedBy == null) {
            return null;
        }

        return requestedBy.getCompany();
    }

    private String buildSelectedItemText(List<Order> orders) {
        if (orders == null || orders.isEmpty()) {
            return "";
        }

        List<String> lines = new ArrayList<>();

        for (Order order : orders) {
            String line = buildSelectedItemLine(order);

            if (StringUtils.hasText(line)) {
                lines.add(line);
            }
        }

        return String.join("\n", lines);
    }

    private String buildSelectedItemLine(Order order) {
        if (order == null) {
            return "";
        }

        OrderItem item = order.getOrderItem();
        Map<String, Object> optionMap = parseOptionMap(item != null ? item.getOptionJson() : null);

        String productName = firstNonBlank(
                pick(optionMap, "제품명", "productName", "ProductName", "제품"),
                item != null ? item.getProductName() : null
        );

        String size = pick(optionMap, "사이즈", "size", "Size", "제품사이즈");
        String color = pick(optionMap, "색상", "color", "Color", "컬러", "제품색상");
        int quantity = Math.max(0, order.getQuantity());

        List<String> tokens = new ArrayList<>();
        addIfText(tokens, productName);
        addIfText(tokens, size);
        addIfText(tokens, color);
        tokens.add(quantity + "개");

        return String.join(" / ", tokens);
    }

    private Map<String, Object> parseOptionMap(String optionJson) {
        if (!StringUtils.hasText(optionJson)) {
            return Map.of();
        }

        try {
            return objectMapper.readValue(optionJson, new TypeReference<LinkedHashMap<String, Object>>() {
            });
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String pick(Map<String, Object> map, String... keys) {
        if (map == null || map.isEmpty() || keys == null) {
            return "";
        }

        for (String key : keys) {
            if (key == null) {
                continue;
            }

            String value = text(map.get(key));

            if (StringUtils.hasText(value)) {
                return value;
            }
        }

        return "";
    }

    private String firstNonBlank(String... values) {
        if (values == null || values.length == 0) {
            return "";
        }

        for (String value : values) {
            String text = text(value);

            if (StringUtils.hasText(text)) {
                return text;
            }
        }

        return "";
    }

    private String joinNonBlank(String delimiter, String... values) {
        if (values == null || values.length == 0) {
            return "";
        }

        return java.util.Arrays.stream(values)
                .map(this::text)
                .filter(StringUtils::hasText)
                .collect(Collectors.joining(delimiter));
    }

    private String text(Object value) {
        if (value == null) {
            return "";
        }

        return String.valueOf(value)
                .replace("\r", " ")
                .replace("\t", " ")
                .replaceAll(" {2,}", " ")
                .trim();
    }

    private record CellAddressParts(int rowIndex, int columnIndex) {
    }

    private final class StatementTaskGroup {
        private final Task task;
        private final List<Order> orders = new ArrayList<>();

        private StatementTaskGroup(Task task) {
            this.task = task;
        }

        private void addOrder(Order order) {
            if (order != null) {
                this.orders.add(order);
            }
        }

        private List<Order> orders() {
            return orders;
        }

        private Order representative() {
            if (orders.isEmpty()) {
                throw new IllegalStateException("명세서 대표 주문이 없습니다.");
            }

            return orders.get(0);
        }

        private Order representativeForHeader() {
            if (orders.isEmpty()) {
                throw new IllegalStateException("명세서 대표 주문이 없습니다.");
            }

            return orders.stream()
                    .filter(order -> hasDeliveryAddress(order) || hasReceiver(order))
                    .findFirst()
                    .orElseGet(this::representative);
        }
    }
}
