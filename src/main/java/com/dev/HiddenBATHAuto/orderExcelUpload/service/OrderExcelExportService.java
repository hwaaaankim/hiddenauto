package com.dev.HiddenBATHAuto.orderExcelUpload.service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dev.HiddenBATHAuto.model.auth.Company;
import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.amount.AmountCustomerMaster;
import com.dev.HiddenBATHAuto.model.task.Order;
import com.dev.HiddenBATHAuto.model.task.OrderImage;
import com.dev.HiddenBATHAuto.model.task.OrderItem;
import com.dev.HiddenBATHAuto.model.task.OrderStatus;
import com.dev.HiddenBATHAuto.model.task.Task;
import com.dev.HiddenBATHAuto.orderExcelUpload.support.OrderExcelAddressParser;
import com.dev.HiddenBATHAuto.orderExcelUpload.support.OrderExcelColumnLayout;
import com.dev.HiddenBATHAuto.orderExcelUpload.support.OrderExcelDeliveryRule;
import com.dev.HiddenBATHAuto.orderExcelUpload.support.OrderExcelProductNameParser;
import com.dev.HiddenBATHAuto.orderExcelUpload.support.ParsedSiteAddress;
import com.dev.HiddenBATHAuto.repository.order.OrderRepository;
import com.dev.HiddenBATHAuto.repository.amount.AmountCustomerMasterRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * 관리자 발주조회 결과를 일반/지역포함 거래처명을 병기한 A:T 20열로 내보냅니다.
 * 업로드는 원본 A:S 19열과 이 20열을 모두 읽으며 D열 지역거래처명은 참고용입니다.
 * 원본 행을 DB에서 다시 찾는 기능이 아니라 현재 저장된 발주를 업로드 가능한 열로 변환합니다.
 * 원래 지역 접두사/별표, P:R 작업 메모, 독립 VAT 및 엑셀 그림은 업로드 시 저장되지 않습니다.
 */
@Service
@RequiredArgsConstructor
public class OrderExcelExportService {

    private static final String TEMPLATE = "excel/order-upload/order_registration_template.xlsx";
    private static final int COLUMN_COUNT = 20;
    private static final int REGIONAL_CUSTOMER_COLUMN = 3;
    private static final String REGIONAL_CUSTOMER_HEADER = OrderExcelColumnLayout.REGIONAL_CUSTOMER_NAME_HEADER;
    private static final String MIRROR_CUTTING_FILTER = "-9000001";
    private static final List<String> WEEKDAYS = List.of("월", "화", "수", "목", "금", "토", "일");
    private static final Pattern FREIGHT = Pattern.compile(".*(?:운임비|배송비|화물비|택배비).*");
    private static final Set<String> UPLOAD_OPTION_KEYS = Set.of("카테고리", "제품시리즈", "제품시리즈ID", "제품명", "사이즈", "색상");

    private final OrderRepository orderRepository;
    private final AmountCustomerMasterRepository customerRepository;
    private final ObjectMapper objectMapper;
    private final OrderExcelProductNameParser productNameParser;
    private final OrderExcelAddressParser addressParser;

    /** 조회 화면과 매출전표 다운로드가 사용하는 동일한 저장소 검색조건입니다. */
    @Transactional(readOnly = true)
    public void download(String keyword, String orderIdFrom, String orderIdTo, String orderId,
                         String productName, String dateCriteria, String startDate, String endDate,
                         String productCategoryId, String orderStatus, String standard,
                         String sortField, String sortDir, HttpServletResponse response) throws IOException {
        String criteria = normalizeDateCriteria(dateCriteria);
        Long from = positiveLong(orderIdFrom);
        Long to = positiveLong(orderIdTo);
        Long single = positiveLong(orderId);
        if (from == null && to == null && single != null) {
            from = single;
            to = single;
        }
        if (from != null && to != null && from > to) {
            throw new IllegalArgumentException("오더 ID FROM은 TO보다 클 수 없습니다.");
        }
        LocalDate fromDate = "all".equals(criteria) ? null : optionalDate(startDate, "시작일");
        LocalDate toDate = "all".equals(criteria) ? null : optionalDate(endDate, "종료일");
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException("시작일은 종료일보다 늦을 수 없습니다.");
        }
        boolean mirrorOnly = MIRROR_CUTTING_FILTER.equals(safe(productCategoryId));
        Long categoryId = mirrorOnly || safe(productCategoryId).equalsIgnoreCase("all")
                ? null : positiveLong(productCategoryId);
        List<Order> orders = orderRepository.findFilteredOrdersForExcelWithOrderIdRangeAndProductName(
                nullable(keyword), from, to, nullable(productName), criteria,
                fromDate == null ? null : fromDate.atStartOfDay(),
                toDate == null ? null : toDate.atTime(LocalTime.MAX),
                categoryId, mirrorOnly, optionalStatus(orderStatus), optionalStandard(standard));

        // 전체 변환/검증 완료 후에만 다운로드 응답을 시작하여 손상된 부분 파일을 만들지 않습니다.
        try (Workbook workbook = buildWorkbook(orders, sortField, sortDir)) {
            String name = "발주등록용엑셀_" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + ".xlsx";
            String encoded = URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20");
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename=order_registration.xlsx; filename*=UTF-8''" + encoded);
            response.setHeader("Cache-Control", "no-store");
            workbook.write(response.getOutputStream());
            response.flushBuffer();
        }
    }

    /**
     * 엔티티 목록만으로 통합 검증할 수 있는 생성 진입점입니다. 호출한 쪽에서 workbook.close() 합니다.
     * Task/배송수단/배송지/현장수령자/직배송담당자별 블록 경계를 빈 행으로 표시합니다.
     * 같은 거래처의 연속 발주가 업로드 미리보기에서 하나의 Task로 합쳐지는 것을 방지합니다.
     */
    public Workbook buildWorkbook(List<Order> orders, String sortField, String sortDir) throws IOException {
        return buildWorkbook(orders, sortField, sortDir, customerRepository.findAll());
    }

    /** An explicit master snapshot makes matching deterministic and permits DB-free verification. */
    public Workbook buildWorkbook(List<Order> orders, String sortField, String sortDir,
                                  List<AmountCustomerMaster> customers) throws IOException {
        List<ExportBlock> blocks = buildBlocks(orders, sortField, sortDir);
        Map<String, Set<String>> customerNames = indexCustomerNames(customers);
        for (ExportBlock block : blocks) {
            for (ExportItem item : block.items()) regionalCustomerName(item.order(), customerNames);
        }
        Workbook workbook;
        try (InputStream input = new ClassPathResource(TEMPLATE).getInputStream()) {
            workbook = WorkbookFactory.create(input);
        }
        try {
            Sheet sheet = workbook.getSheetAt(0);
            Row prototype = sheet.getRow(1);
            if (prototype == null || sheet.getRow(0) == null) {
                throw new IllegalStateException("발주등록용 엑셀 양식의 제목/본문 행이 없습니다.");
            }
            CellStyle[] styles = new CellStyle[COLUMN_COUNT];
            for (int col = 0; col < COLUMN_COUNT; col++) {
                int originalColumn = col == REGIONAL_CUSTOMER_COLUMN ? 2 : col > REGIONAL_CUSTOMER_COLUMN ? col - 1 : col;
                Cell cell = prototype.getCell(originalColumn);
                styles[col] = cell == null ? workbook.getCellStyleAt(0) : cell.getCellStyle();
            }
            insertRegionalCustomerHeader(sheet);
            short rowHeight = prototype.getHeight();
            for (int row = sheet.getLastRowNum(); row >= 1; row--) {
                Row old = sheet.getRow(row);
                if (old != null) {
                    for (Cell cell : old) {
                        if (cell.getCellComment() != null) {
                            cell.removeCellComment();
                        }
                    }
                    sheet.removeRow(old);
                }
            }
            int rowIndex = 1;
            Set<Long> chargedTasks = new HashSet<>();
            for (ExportBlock block : blocks) {
                if (rowIndex > 1) {
                    newRow(sheet, rowIndex++, styles, rowHeight).setHeightInPoints(10);
                }
                for (ExportItem item : block.items()) {
                    Row row = newRow(sheet, rowIndex++, styles, rowHeight);
                    writeContext(workbook, row, item.order(), block, customerNames);
                    text(row, 3, item.itemName());
                    text(row, 4, item.size());
                    row.getCell(exportColumn(5)).setCellValue(item.order().getQuantity());
                    text(row, 6, item.order().getAdminMemo());
                    text(row, 7, item.category());
                    row.getCell(exportColumn(11)).setCellValue(item.order().getProductCost());
                    row.getCell(exportColumn(12)).setCellValue(item.order().getSupplyPrice());
                    row.getCell(exportColumn(13)).setCellValue((long) item.order().getTotalAmount() - item.order().getSupplyPrice());
                    row.getCell(exportColumn(14)).setCellValue(item.order().getTotalAmount());
                    writeImageReferences(workbook, row, item.order());
                }
                Order first = block.items().get(0).order();
                // 주소는 비용 유무와 관계없이 현장/화물/택배마다 반드시 함께 내보냅니다.
                if (block.addressRow() != null) {
                    Row row = newRow(sheet, rowIndex++, styles, rowHeight);
                    writeContext(workbook, row, first, block, customerNames);
                    text(row, 3, block.addressRow());
                }
                if (chargedTasks.add(taskIdentity(first))) {
                    int delivery = maxCharge(first.getTask(), orders, true);
                    int packing = maxCharge(first.getTask(), orders, false);
                    if (delivery > 0) {
                        writeCharge(workbook, sheet, rowIndex++, styles, rowHeight, first, block, "운임비", delivery, customerNames);
                    }
                    if (packing > 0) {
                        writeCharge(workbook, sheet, rowIndex++, styles, rowHeight, first, block, "포장비", packing, customerNames);
                    }
                }
            }
            workbook.setPrintArea(0, 0, COLUMN_COUNT - 1, 0, Math.max(0, rowIndex - 1));
            sheet.setRepeatingRows(new org.apache.poi.ss.util.CellRangeAddress(0, 0, -1, -1));
            return workbook;
        } catch (RuntimeException e) {
            try {
                workbook.close();
            } catch (IOException closeFailure) {
                e.addSuppressed(closeFailure);
            }
            throw e;
        }
    }

    private List<ExportBlock> buildBlocks(List<Order> orders, String sortField, String sortDir) {
        if (orders == null || orders.isEmpty()) {
            return List.of();
        }
        if (orders.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("다운로드 대상에 발주 정보가 없는 행이 있습니다.");
        }
        Map<BlockKey, List<ExportItem>> grouped = new LinkedHashMap<>();
        Map<BlockKey, String> addresses = new LinkedHashMap<>();
        Map<BlockKey, OrderExcelDeliveryRule> rules = new LinkedHashMap<>();
        for (Order order : orders.stream().sorted(comparator(sortField, sortDir)).toList()) {
            Company company = company(order);
            if (company == null || safe(company.getCompanyName()).isBlank()) {
                throw invalid(order, "거래처가 없습니다. 발주의 주문자/업체 연결을 확인해 주세요.");
            }
            if (order.getPreferredDeliveryDate() == null) {
                throw invalid(order, "출고일이 없습니다. 출고일을 지정한 뒤 다운로드해 주세요.");
            }
            OrderExcelDeliveryRule rule = deliveryRule(order);
            String token = deliveryToken(order, rule);
            Map<String, String> options = options(order);
            String category = first(options.get("카테고리"),
                    order.getProductCategory() == null ? "" : order.getProductCategory().getName());
            if (category.isBlank()) {
                throw invalid(order, "제품 분류가 없습니다. 제품 분류를 지정한 뒤 다운로드해 주세요.");
            }
            String itemName = originalItemName(order, options, category);
            if ((itemName != null && itemName.length() > 32767)
                    || (order.getAdminMemo() != null && order.getAdminMemo().length() > 32767)) {
                throw invalid(order, "품목명 또는 비고가 Excel 한 칸의 최대 길이(32,767자)를 넘습니다. "
                        + "내용을 줄이거나 해당 발주를 조회 범위에서 제외한 뒤 다운로드해 주세요.");
            }
            if (safe(itemName).isBlank() || addressParser.looksLikeAddress(itemName)
                    || safe(itemName).replaceAll("\\s+", "").contains("포장비")
                    || FREIGHT.matcher(safe(itemName).replaceAll("\\s+", "")).matches()) {
                throw invalid(order, "품목명 '" + itemName + "'은 업로드에서 주소/비용 행으로 해석됩니다. 품목명을 확인해 주세요.");
            }
            String address = addressRow(order, rule);
            BlockKey key = new BlockKey(taskIdentity(order), company.getId(), rule,
                    token, safe(order.getRoadAddress()),
                    safe(order.getDetailAddress()), address == null ? "" : address,
                    safe(order.getOrdererName()), safe(order.getOrdererPhone()));
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>())
                    .add(new ExportItem(order, itemName, first(options.get("사이즈"), order.getOrderItem().getProductionSize()), category));
            addresses.put(key, address);
            rules.put(key, rule);
        }
        List<ExportBlock> blocks = new ArrayList<>();
        for (Map.Entry<BlockKey, List<ExportItem>> entry : grouped.entrySet()) {
            blocks.add(new ExportBlock(entry.getValue(), rules.get(entry.getKey()), addresses.get(entry.getKey())));
        }
        return blocks;
    }

    private Map<String, String> options(Order order) {
        OrderItem item = order.getOrderItem();
        if (item == null) {
            throw invalid(order, "품목 정보가 없습니다. 발주 품목을 확인해 주세요.");
        }
        if (safe(item.getOptionJson()).isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> source = objectMapper.readValue(item.getOptionJson(), new TypeReference<Map<String, Object>>() { });
            if (source == null) {
                throw invalid(order, "제품 옵션 JSON이 객체가 아닌 null입니다. 발주 옵션을 확인해 주세요.");
            }
            List<String> unsupported = source.entrySet().stream()
                    .filter(entry -> !UPLOAD_OPTION_KEYS.contains(entry.getKey()) && hasBusinessValue(entry.getValue()))
                    .map(Map.Entry::getKey).toList();
            if (!unsupported.isEmpty()) {
                throw invalid(order, "발주등록용 엑셀 업로드가 읽지 않는 추가 옵션이 있습니다: " + String.join(", ", unsupported)
                        + ". 해당 발주를 조회 조건에서 제외하거나 추가 옵션 보존을 지원하는 양식이 필요합니다.");
            }
            Map<String, String> flattened = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : source.entrySet()) {
                if (entry.getValue() == null) {
                    continue;
                }
                if (entry.getValue() instanceof String || entry.getValue() instanceof Number || entry.getValue() instanceof Boolean) {
                    flattened.put(entry.getKey(), entry.getValue().toString());
                } else if (Set.of("제품명", "사이즈", "색상", "카테고리").contains(entry.getKey())) {
                    throw invalid(order, "'" + entry.getKey() + "' 값이 단일 문자열이 아닙니다. 기존 엑셀의 한 칸에 손실 없이 표현할 수 없습니다.");
                }
            }
            return flattened;
        } catch (IOException e) {
            throw invalid(order, "제품 옵션 JSON 형식이 잘못되었습니다. 발주 옵션을 확인해 주세요.");
        }
    }

    private boolean hasBusinessValue(Object value) {
        if (value == null) return false;
        if (value instanceof String text) return !text.isBlank();
        if (value instanceof Map<?, ?> map) return map.values().stream().anyMatch(this::hasBusinessValue);
        if (value instanceof List<?> list) return list.stream().anyMatch(this::hasBusinessValue);
        return true;
    }

    private String originalItemName(Order order, Map<String, String> options, String category) {
        OrderItem item = order.getOrderItem();
        if (!safe(item.getItemName()).isBlank()) {
            validateSourceColor(order, item.getItemName(), options, category);
            return item.getItemName();
        }
        String saved = first(options.get("제품명"), item.getProductName());
        // 기존 업로드가 저장한 '가공명[원품목]'은 마지막 대괄호에서 원품목을 복원합니다.
        int opening = saved.lastIndexOf('[');
        if (opening >= 0 && saved.endsWith("]") && opening < saved.length() - 2) {
            String original = saved.substring(opening + 1, saved.length() - 1);
            validateSourceColor(order, original, options, category);
            return original;
        }
        String color = safe(options.get("색상"));
        if (color.isBlank() || color.equals(productNameParser.parse(saved, category).getColor())) {
            return saved;
        }
        String candidate = saved + " " + color;
        if (color.equals(productNameParser.parse(candidate, category).getColor())) {
            return candidate;
        }
        throw invalid(order, "원품목명이 없고 색상 '" + color + "'은 기존 품목명 파서가 인식할 수 없습니다. "
                + "색상 정보가 사라지지 않도록 원품목명을 확인해 주세요.");
    }

    private void validateSourceColor(Order order, String original, Map<String, String> options, String category) {
        String color = safe(options.get("색상"));
        String parsedColor = safe(productNameParser.parse(original, category).getColor());
        if (!color.equals(parsedColor)) {
            throw invalid(order, "원품목명 '" + original + "'에서 읽는 색상 '" + parsedColor
                    + "'이 현재 발주의 색상 '" + color + "'과 다릅니다. 원품목명/제품 옵션을 확인해 주세요.");
        }
    }

    private OrderExcelDeliveryRule deliveryRule(Order order) {
        String method = order.getDeliveryMethod() == null ? "" : safe(order.getDeliveryMethod().getMethodName()).replaceAll("\\s+", "");
        if (method.contains("직배송")) return OrderExcelDeliveryRule.DIRECT;
        if (method.contains("현장배송") || method.equals("현장")) return OrderExcelDeliveryRule.SITE;
        if (method.contains("화물")) return OrderExcelDeliveryRule.CARGO;
        if (method.contains("방문")) return OrderExcelDeliveryRule.VISIT;
        if (method.contains("택배")) return OrderExcelDeliveryRule.PARCEL;
        if (method.contains("미배송")) return OrderExcelDeliveryRule.UNDELIVERED;
        throw invalid(order, "배송수단 '" + method + "'은 J열 부호로 표현할 수 없습니다. "
                + "직배송/현장배송/화물/방문/택배/미배송 중 업로드가 지원하는 배송수단을 확인해 주세요.");
    }

    private String deliveryToken(Order order, OrderExcelDeliveryRule rule) {
        if (rule != OrderExcelDeliveryRule.DIRECT) {
            return switch (rule) {
                case SITE -> "ㅇ";
                case CARGO -> "ㅎ";
                case VISIT -> "ㅂ";
                case PARCEL -> "ㄷ";
                case UNDELIVERED -> "ㅅ";
                default -> throw new IllegalStateException("배송수단 부호가 없습니다.");
            };
        }
        Member handler = order.getAssignedDeliveryHandler();
        String name = handler == null ? "" : safe(handler.getName());
        if (name.isBlank()) {
            // 고객 신청/취소는 기존 저장 정책상 담당자가 없어도 정상입니다.
            return "-";
        }
        if (Set.of("ㅇ", "O", "o", "0", "ㅎ", "ㅂ", "ㄷ", "ㅅ", "-", "없음", "미정").contains(name.replaceAll("\\s+", ""))) {
            throw invalid(order, "직배송 담당자명 '" + name + "'이 배송수단 부호와 충돌합니다. 담당자명을 확인해 주세요.");
        }
        return name;
    }

    private String addressRow(Order order, OrderExcelDeliveryRule rule) {
        // 택배는 별도 site 필드를 지우지만 주소행을 읽어 기본 배송지를 덮어쓰므로 함께 출력합니다.
        if (!rule.usesSiteAddress() && rule != OrderExcelDeliveryRule.PARCEL) {
            Company company = company(order);
            Member requested = order.getTask() == null ? null : order.getTask().getRequestedBy();
            boolean customAddress = company != null && (!safe(order.getRoadAddress()).equals(safe(company.getRoadAddress()))
                    || !safe(order.getDetailAddress()).equals(safe(company.getDetailAddress()))
                    || !safe(order.getZipCode()).equals(safe(company.getZipCode())));
            boolean customContact = requested != null && (!safe(order.getOrdererName()).equals(safe(requested.getName()))
                    || !safe(order.getOrdererPhone()).replaceAll("\\D", "").equals(safe(requested.getPhone()).replaceAll("\\D", "")));
            if (!customAddress && !customContact) return null;
        }
        String road = rule.usesSiteAddress() ? first(order.getSiteRoadAddress(), order.getRoadAddress()) : safe(order.getRoadAddress());
        String detail = rule.usesSiteAddress() ? first(order.getSiteDetailAddress(), order.getDetailAddress()) : safe(order.getDetailAddress());
        String zip = rule.usesSiteAddress() ? first(order.getSiteZipCode(), order.getZipCode()) : safe(order.getZipCode());
        String address = road + (detail.isBlank() || road.endsWith(detail) ? "" : " " + detail);
        if (!addressParser.looksLikeAddress(address)) {
            throw invalid(order, "배송지 '" + address + "'를 업로드 주소행으로 인식할 수 없습니다. "
                    + "시/도·시/군/구와 도로명/건물번호가 포함된 배송지를 확인해 주세요.");
        }
        String contact = (safe(order.getOrdererName()) + " " + safe(order.getOrdererPhone())).trim();
        if (!safe(order.getOrdererName()).isBlank() && safe(order.getOrdererPhone()).isBlank()) {
            throw invalid(order, "현장 수령자명은 있지만 연락처가 없습니다. 기존 주소 파서는 연락처 앞에서 이름을 읽으므로 "
                    + "현장 연락처를 입력한 뒤 다운로드해 주세요.");
        }
        if (road.contains("/") || detail.contains("/")) {
            throw invalid(order, "배송지에 '/'가 들어 있어 현장 연락처 구분자와 충돌합니다. 주소 표기를 확인해 주세요.");
        }
        String serialized = (zip.matches("\\d{5}") ? zip + " " : "") + address.trim() + (contact.isBlank() ? "" : " / " + contact);
        ParsedSiteAddress parsed = addressParser.parseLocal(serialized);
        String restoredAddress = (safe(parsed.getRoadAddress()) + " " + safe(parsed.getDetailAddress())).replaceAll("\\s+", "");
        if (!address.replaceAll("\\s+", "").equals(restoredAddress)
                || (!zip.isBlank() && !zip.equals(safe(parsed.getZipCode())))) {
            throw invalid(order, "배송지/우편번호를 주소행에서 손실 없이 읽을 수 없습니다. "
                    + "현재 배송지 '" + address + "' / 우편번호 '" + zip + "'를 확인해 주세요. "
                    + "기존 주소 파서는 5자리 숫자를 우편번호로 읽고 도로명과 상세주소를 분리합니다.");
        }
        if (!safe(parsed.getRecipientName()).equals(safe(order.getOrdererName()))
                || !safe(parsed.getRecipientPhone()).replaceAll("\\D", "").equals(safe(order.getOrdererPhone()).replaceAll("\\D", ""))) {
            throw invalid(order, "현장 수령자명/연락처 '" + contact + "'를 주소행에서 정확히 읽을 수 없습니다. "
                    + "기존 양식은 연락처 앞의 공백 없는 이름(12자 이내)과 국내 전화번호를 읽습니다. 현장 연락처 표기를 확인해 주세요.");
        }
        return serialized;
    }

    private void writeContext(Workbook workbook, Row row, Order order, ExportBlock block,
                              Map<String, Set<String>> customerNames) {
        LocalDate date = order.getPreferredDeliveryDate().toLocalDate();
        row.getCell(0).setCellValue(date.atStartOfDay());
        text(row, 1, WEEKDAYS.get(date.getDayOfWeek().getValue() - 1));
        Company company = company(order);
        text(row, 2, company.getCompanyName());
        row.getCell(REGIONAL_CUSTOMER_COLUMN).setCellValue(regionalCustomerName(order, customerNames));
        Task task = order.getTask();
        text(row, 8, task == null || task.getManagedBy() == null ? "" : task.getManagedBy().getName());
        text(row, 9, deliveryToken(order, block.rule()));
        text(row, 18, businessNumber(company.getBusinessNumber()));
    }

    private int maxCharge(Task task, List<Order> selected, boolean delivery) {
        // 기존 업로드는 Task의 첫 Order에만 비용을 저장합니다. 필터에서 그 Order가 빠져도 비용은 유실하지 않습니다.
        List<Order> members = task != null && task.getOrders() != null && !task.getOrders().isEmpty()
                ? task.getOrders() : selected;
        return members == null ? 0 : members.stream().filter(o -> o != null && (task == null || o.getTask() == task
                || (o.getTask() != null && task.getId() != null && task.getId().equals(o.getTask().getId()))))
                .mapToInt(o -> delivery ? o.getDeliveryCost() : o.getPackingCost()).max().orElse(0);
    }

    private void writeCharge(Workbook workbook, Sheet sheet, int index, CellStyle[] styles, short height,
                             Order order, ExportBlock block, String name, int total,
                             Map<String, Set<String>> customerNames) {
        Row row = newRow(sheet, index, styles, height);
        writeContext(workbook, row, order, block, customerNames);
        text(row, 3, name);
        row.getCell(exportColumn(5)).setCellValue(1);
        // 업로드 resolveFreightCost는 O -> M -> L을 그대로 비용으로 사용합니다. VAT 포함 합계를 O열에 둡니다.
        int supply = java.math.BigDecimal.valueOf(total).divide(new java.math.BigDecimal("1.1"), 0, java.math.RoundingMode.HALF_UP).intValue();
        row.getCell(exportColumn(11)).setCellValue(supply);
        row.getCell(exportColumn(12)).setCellValue(supply);
        row.getCell(exportColumn(13)).setCellValue(total - supply);
        row.getCell(exportColumn(14)).setCellValue(total);
    }

    private void writeImageReferences(Workbook workbook, Row row, Order order) {
        List<OrderImage> images = order.getOrderImages() == null ? List.of() : order.getOrderImages().stream()
                .filter(i -> i != null && ("MANAGEMENT".equalsIgnoreCase(i.getType()) || "CUSTOMER".equalsIgnoreCase(i.getType())))
                .toList();
        if (!images.isEmpty()) {
            text(row, 10, images.size() + "개 첨부");
        }
    }

    private Row newRow(Sheet sheet, int index, CellStyle[] styles, short height) {
        Row row = sheet.createRow(index);
        row.setHeight(height);
        for (int col = 0; col < COLUMN_COUNT; col++) {
            row.createCell(col).setCellStyle(styles[col]);
        }
        return row;
    }

    private Comparator<Order> comparator(String sortField, String sortDir) {
        Comparator<LocalDateTime> dates = "desc".equalsIgnoreCase(sortDir) && "preferredDeliveryDate".equals(sortField)
                ? Comparator.nullsLast(Comparator.reverseOrder()) : Comparator.nullsLast(Comparator.naturalOrder());
        return Comparator.comparing(Order::getPreferredDeliveryDate, dates)
                .thenComparing(o -> company(o) == null ? "" : safe(company(o).getCompanyName()), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(this::taskIdentity)
                .thenComparing(Order::getId, Comparator.nullsLast(Comparator.naturalOrder()));
    }

    private Company company(Order order) {
        return order == null || order.getTask() == null || order.getTask().getRequestedBy() == null
                ? null : order.getTask().getRequestedBy().getCompany();
    }

    private Long taskIdentity(Order order) {
        if (order.getTask() != null && order.getTask().getId() != null) return order.getTask().getId();
        if (order.getId() != null) return -order.getId();
        throw invalid(order, "발주/품목 ID가 없습니다.");
    }

    private IllegalArgumentException invalid(Order order, String reason) {
        return new IllegalArgumentException("발주등록용 엑셀 다운로드 / Order " + (order == null ? "없음" : order.getId()) + ": " + reason);
    }

    /** References below use the original 19-column indices; D insertion shifts D:S one place. */
    private static int exportColumn(int originalColumn) {
        return originalColumn >= REGIONAL_CUSTOMER_COLUMN ? originalColumn + 1 : originalColumn;
    }

    private static void text(Row row, int column, String value) {
        row.getCell(exportColumn(column)).setCellValue(value == null ? "" : value);
    }

    private void insertRegionalCustomerHeader(Sheet sheet) {
        Row header = sheet.getRow(0);
        for (int original = 18; original >= REGIONAL_CUSTOMER_COLUMN; original--) {
            Cell source = header.getCell(original);
            Cell target = header.getCell(original + 1);
            if (target == null) target = header.createCell(original + 1);
            target.setCellValue(source == null ? "" : source.getStringCellValue());
            if (source != null) target.setCellStyle(source.getCellStyle());
            sheet.setColumnWidth(original + 1, sheet.getColumnWidth(original));
        }
        Cell added = header.getCell(REGIONAL_CUSTOMER_COLUMN);
        if (added == null) added = header.createCell(REGIONAL_CUSTOMER_COLUMN);
        added.setCellValue(REGIONAL_CUSTOMER_HEADER);
        added.setCellStyle(header.getCell(2).getCellStyle());
        sheet.setColumnWidth(REGIONAL_CUSTOMER_COLUMN, sheet.getColumnWidth(2));
    }

    private Map<String, Set<String>> indexCustomerNames(List<AmountCustomerMaster> customers) {
        Map<String, Set<String>> index = new LinkedHashMap<>();
        if (customers == null) return index;
        for (AmountCustomerMaster customer : customers) {
            if (customer == null || safe(customer.getCustomerName()).isBlank()) continue;
            String key = normalizedBusinessNumber(customer.getBusinessNo());
            if (key.isBlank()) continue;
            index.computeIfAbsent(key, ignored -> new LinkedHashSet<>()).add(customer.getCustomerName());
        }
        return index;
    }

    private String regionalCustomerName(Order order, Map<String, Set<String>> customerNames) {
        Company company = company(order);
        String key = normalizedBusinessNumber(company == null ? null : company.getBusinessNumber());
        Set<String> names = customerNames.get(key);
        if (key.isBlank() || names == null || names.isEmpty()) return "";
        if (names.size() > 1) throw invalid(order,
                "사업자번호 " + businessNumber(key) + "에 서로 다른 거래처 마스터명이 여러 개 있습니다: "
                + String.join(" / ", names)
                + ". tb_amount_customer_master의 business_no/customer_name 중복을 확인해 주세요.");
        String name = names.iterator().next();
        if (name.length() > 32767) throw invalid(order, "지역 포함 거래처명은 Excel 한 칸의 최대 32,767자를 초과할 수 없습니다.");
        return name;
    }

    private static String normalizedBusinessNumber(String value) {
        String digits = value == null ? "" : value.replaceAll("[^0-9]", "");
        return digits.length() == 10 ? digits : "";
    }
    private static String safe(String value) { return value == null ? "" : value.trim(); }
    private static String nullable(String value) { return safe(value).isBlank() ? null : value.trim(); }
    private static String first(String... values) { for (String value : values) if (!safe(value).isBlank()) return value.trim(); return ""; }
    private static String businessNumber(String value) {
        String digits = safe(value).replaceAll("[^0-9]", "");
        return digits.length() == 10 ? digits.substring(0, 3) + "-" + digits.substring(3, 5) + "-" + digits.substring(5) : digits;
    }
    private static Long positiveLong(String value) {
        if (safe(value).isBlank()) return null;
        try { long parsed = Long.parseLong(value.trim()); return parsed > 0 ? parsed : null; }
        catch (NumberFormatException e) { return null; }
    }
    private static String normalizeDateCriteria(String value) {
        String normalized = safe(value).toLowerCase(Locale.ROOT);
        return Set.of("all", "order", "delivery").contains(normalized) ? normalized : "all";
    }
    private static LocalDate optionalDate(String value, String label) {
        if (safe(value).isBlank()) return null;
        try { return LocalDate.parse(value.trim()); }
        catch (RuntimeException e) { throw new IllegalArgumentException(label + " 형식은 yyyy-MM-dd여야 합니다."); }
    }
    private static OrderStatus optionalStatus(String value) {
        if (safe(value).isBlank() || safe(value).equalsIgnoreCase("all")) return null;
        try { return OrderStatus.valueOf(value.trim()); }
        catch (IllegalArgumentException e) { return null; }
    }
    private static Boolean optionalStandard(String value) {
        return "true".equalsIgnoreCase(safe(value)) ? Boolean.TRUE : "false".equalsIgnoreCase(safe(value)) ? Boolean.FALSE : null;
    }

    private record ExportItem(Order order, String itemName, String size, String category) { }
    private record ExportBlock(List<ExportItem> items, OrderExcelDeliveryRule rule, String addressRow) { }
    private record BlockKey(Long task, Long company, OrderExcelDeliveryRule rule,
                            String handler, String road, String detail, String siteAddress, String orderer, String phone) { }
}
