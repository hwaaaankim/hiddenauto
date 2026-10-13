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
import org.apache.poi.ss.usermodel.ClientAnchor;
import org.apache.poi.ss.usermodel.Comment;
import org.apache.poi.ss.usermodel.Drawing;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
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
 * 저장값 이상은 품목을 제외하거나 다운로드를 중단하지 않고 강조/메모/출력확인 시트로 알립니다.
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

        // 문제 품목은 경고로 표시하며, 워크북 생성이 끝난 후에만 응답을 시작합니다.
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
        Map<String, Set<String>> customerNames = indexCustomerNames(customers);
        List<ExportBlock> blocks = buildBlocks(orders, sortField, sortDir, customerNames);
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
            Set<Object> chargedTasks = new HashSet<>();
            CellStyle[] warningStyles = warningStyles(workbook, styles);
            List<ExportReview> reviews = new ArrayList<>();
            for (ExportBlock block : blocks) {
                if (rowIndex > 1) {
                    newRow(sheet, rowIndex++, styles, rowHeight).setHeightInPoints(10);
                }
                for (ExportItem item : block.items()) {
                    Row row = newRow(sheet, rowIndex++, styles, rowHeight);
                    writeContext(row, item, block);
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
                    if (!item.warnings().isEmpty()) {
                        markWarning(row, warningStyles, item.warnings(), "Order " + item.order().getId());
                        reviews.add(new ExportReview(item, row.getRowNum() + 1, block.addressRow()));
                    }
                }
                Order first = block.items().get(0).order();
                // 주소는 비용 유무와 관계없이 현장/화물/택배마다 반드시 함께 내보냅니다.
                if (block.addressRow() != null) {
                    Row row = newRow(sheet, rowIndex++, styles, rowHeight);
                    writeContext(row, block.items().get(0), block);
                    text(row, 3, block.addressRow());
                    List<ExportWarning> addressWarnings = block.items().stream()
                            .flatMap(item -> item.warnings().stream())
                            .filter(warning -> warning.field().equals("배송지") || warning.field().equals("우편번호")
                                    || warning.field().equals("현장 수령자"))
                            .distinct().toList();
                    if (!addressWarnings.isEmpty()) {
                        markWarning(row, warningStyles, addressWarnings, "주소행 / Order "
                                + block.items().stream().map(item -> String.valueOf(item.order().getId())).reduce((a, b) -> a + ", " + b).orElse(""));
                    }
                }
                if (chargedTasks.add(taskIdentity(first))) {
                    int delivery = maxCharge(first, orders, true);
                    int packing = maxCharge(first, orders, false);
                    if (delivery > 0) {
                        writeCharge(workbook, sheet, rowIndex++, styles, rowHeight, block.items().get(0), block, "운임비", delivery, warningStyles);
                    }
                    if (packing > 0) {
                        writeCharge(workbook, sheet, rowIndex++, styles, rowHeight, block.items().get(0), block, "포장비", packing, warningStyles);
                    }
                }
            }
            writeReviewSheet(workbook, sheet.getSheetName(), reviews);
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

    private List<ExportBlock> buildBlocks(List<Order> orders, String sortField, String sortDir,
                                          Map<String, Set<String>> customerNames) {
        if (orders == null || orders.isEmpty()) return List.of();
        if (orders.stream().anyMatch(java.util.Objects::isNull)) {
            // A null repository result is an application error, rather than a stored field to export.
            throw new IllegalArgumentException("다운로드 대상에 발주 정보가 없는 행이 있습니다.");
        }
        Map<BlockKey, List<ExportItem>> grouped = new LinkedHashMap<>();
        Map<BlockKey, String> addresses = new LinkedHashMap<>();
        Map<BlockKey, OrderExcelDeliveryRule> rules = new LinkedHashMap<>();
        for (Order order : orders.stream().sorted(comparator(sortField, sortDir)).toList()) {
            List<ExportWarning> warnings = new ArrayList<>();
            Company company = company(order);
            if (company == null || safe(company.getCompanyName()).isBlank()) {
                warn(warnings, "거래처", "거래처가 없습니다. 발주의 주문자/업체 연결을 확인해 주세요.");
            }
            if (order.getPreferredDeliveryDate() == null) {
                warn(warnings, "출고일", "출고일이 없어 날짜/요일을 빈칸으로 출력했습니다.");
            }
            if (order.getId() == null) warn(warnings, "Order ID", "발주 ID가 없습니다.");
            OrderExcelDeliveryRule rule = deliveryRule(order, warnings);
            String token = deliveryToken(order, rule, warnings);
            Map<String, String> options = options(order, warnings);
            String category = first(options.get("카테고리"),
                    order.getProductCategory() == null ? "" : order.getProductCategory().getName());
            if (category.isBlank()) warn(warnings, "제품 분류", "제품 분류가 없습니다. 재업로드 전에 분류를 지정해 주세요.");
            String itemName = originalItemName(order, options, category, warnings);
            if (safe(itemName).isBlank()) {
                warn(warnings, "품목명", "품목명이 없어 [품목 정보 없음]으로 표시했습니다. 재업로드 전에 수정해 주세요.");
                itemName = "[품목 정보 없음]";
            } else if (addressParser.looksLikeAddress(itemName)
                    || safe(itemName).replaceAll("\\s+", "").contains("포장비")
                    || FREIGHT.matcher(safe(itemName).replaceAll("\\s+", "")).matches()) {
                warn(warnings, "품목명", "저장된 품목명이 업로드에서 주소/비용 행으로 해석될 수 있습니다. 재업로드 전에 수정해 주세요.");
            }
            String regionalName = regionalCustomerName(order, customerNames, warnings);
            String address = addressRow(order, rule, warnings);
            OrderItem orderItem = order.getOrderItem();
            String size = first(options.get("사이즈"), orderItem == null ? "" : orderItem.getProductionSize());
            for (String value : new String[] { itemName, size, category, token, regionalName, order.getAdminMemo(), address,
                    company == null ? null : company.getCompanyName() }) {
                if (value != null && value.length() > 32767) {
                    warn(warnings, "셀 길이", "Excel 한 칸의 최대 길이(32,767자)를 넘는 값은 본문에 일부를 표시하고 전체 값은 '긴 원문' 시트에 나누어 보존했습니다.");
                    break;
                }
            }
            BlockKey key = new BlockKey(taskIdentity(order), company == null ? null : company.getId(), rule,
                    token, safe(order.getRoadAddress()), safe(order.getDetailAddress()), address == null ? "" : address,
                    safe(order.getOrdererName()), safe(order.getOrdererPhone()));
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>())
                    .add(new ExportItem(order, itemName, size, category, token, regionalName, warnings));
            addresses.put(key, address);
            rules.put(key, rule);
        }
        List<ExportBlock> blocks = new ArrayList<>();
        for (Map.Entry<BlockKey, List<ExportItem>> entry : grouped.entrySet()) {
            blocks.add(new ExportBlock(entry.getValue(), rules.get(entry.getKey()), addresses.get(entry.getKey())));
        }
        return blocks;
    }

    private Map<String, String> options(Order order, List<ExportWarning> warnings) {
        OrderItem item = order.getOrderItem();
        if (item == null) {
            warn(warnings, "품목 정보", "품목 정보가 없습니다. 발주 품목을 확인해 주세요.");
            return Map.of();
        }
        if (safe(item.getOptionJson()).isBlank()) return Map.of();
        try {
            Map<String, Object> source = objectMapper.readValue(item.getOptionJson(), new TypeReference<Map<String, Object>>() { });
            if (source == null) {
                warn(warnings, "제품 옵션", "제품 옵션 JSON이 객체가 아닌 null입니다. 원문은 출력확인 시트에 보존했습니다.");
                return Map.of();
            }
            List<String> unsupported = source.entrySet().stream()
                    .filter(entry -> !UPLOAD_OPTION_KEYS.contains(entry.getKey()) && hasBusinessValue(entry.getValue()))
                    .map(Map.Entry::getKey).toList();
            if (!unsupported.isEmpty()) warn(warnings, "제품 옵션", "기존 업로드가 읽지 않는 추가 옵션: "
                    + String.join(", ", unsupported) + ". 전체 옵션 JSON은 출력확인 시트에 보존했습니다. 재업로드 전 확인해 주세요.");
            Map<String, String> flattened = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : source.entrySet()) {
                if (entry.getValue() == null) continue;
                if (entry.getValue() instanceof String || entry.getValue() instanceof Number || entry.getValue() instanceof Boolean) {
                    flattened.put(entry.getKey(), entry.getValue().toString());
                } else if (Set.of("제품명", "사이즈", "색상", "카테고리").contains(entry.getKey())) {
                    warn(warnings, "제품 옵션", "'" + entry.getKey() + "' 값이 단일 문자열이 아닙니다. 본문은 저장된 품목값을 사용하며 원문 옵션 JSON은 출력확인 시트에서 확인해 주세요.");
                }
            }
            return flattened;
        } catch (IOException e) {
            warn(warnings, "제품 옵션", "제품 옵션 JSON 형식이 잘못되었습니다. 저장된 품목값으로 출력하고 옵션 원문은 출력확인 시트에 보존했습니다.");
            return Map.of();
        }
    }

    private boolean hasBusinessValue(Object value) {
        if (value == null) return false;
        if (value instanceof String text) return !text.isBlank();
        if (value instanceof Map<?, ?> map) return map.values().stream().anyMatch(this::hasBusinessValue);
        if (value instanceof List<?> list) return list.stream().anyMatch(this::hasBusinessValue);
        return true;
    }

    private String originalItemName(Order order, Map<String, String> options, String category, List<ExportWarning> warnings) {
        OrderItem item = order.getOrderItem();
        if (item == null) return "";
        if (!safe(item.getItemName()).isBlank()) {
            validateSourceColor(item.getItemName(), options, category, warnings);
            return item.getItemName();
        }
        String saved = first(options.get("제품명"), item.getProductName());
        // 기존 업로드가 저장한 '가공명[원품목]'은 마지막 대괄호에서 원품목을 복원합니다.
        int opening = saved.lastIndexOf('[');
        if (opening >= 0 && saved.endsWith("]") && opening < saved.length() - 2) {
            String original = saved.substring(opening + 1, saved.length() - 1);
            validateSourceColor(original, options, category, warnings);
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
        warn(warnings, "색상", "원품목명이 없고 색상 '" + color + "'을 기존 품목명 파서가 인식할 수 없습니다. 저장된 제품명을 출력했습니다. 원문 옵션은 출력확인 시트에서 확인해 주세요.");
        return saved;
    }

    private void validateSourceColor(String original, Map<String, String> options, String category, List<ExportWarning> warnings) {
        String color = safe(options.get("색상"));
        String parsedColor = safe(productNameParser.parse(original, category).getColor());
        if (!color.equals(parsedColor)) {
            warn(warnings, "색상", "원품목명 '" + original + "'에서 읽는 색상 '" + parsedColor
                    + "'이 현재 발주의 색상 '" + color + "'과 다릅니다. 원품목명/제품 옵션을 확인해 주세요.");
        }
    }

    private OrderExcelDeliveryRule deliveryRule(Order order, List<ExportWarning> warnings) {
        String method = order.getDeliveryMethod() == null ? "" : safe(order.getDeliveryMethod().getMethodName()).replaceAll("\\s+", "");
        if (method.contains("직배송")) return OrderExcelDeliveryRule.DIRECT;
        if (method.contains("현장배송") || method.equals("현장")) return OrderExcelDeliveryRule.SITE;
        if (method.contains("화물")) return OrderExcelDeliveryRule.CARGO;
        if (method.contains("방문")) return OrderExcelDeliveryRule.VISIT;
        if (method.contains("택배")) return OrderExcelDeliveryRule.PARCEL;
        if (method.contains("미배송")) return OrderExcelDeliveryRule.UNDELIVERED;
        warn(warnings, "배송수단", "배송수단 '" + method + "'은 기존 배송 부호로 표현할 수 없습니다. 원래 명칭을 출력했습니다. 재업로드 전 지원하는 배송수단을 지정해 주세요.");
        return null;
    }

    private String deliveryToken(Order order, OrderExcelDeliveryRule rule, List<ExportWarning> warnings) {
        if (rule == null) return order.getDeliveryMethod() == null ? "[배송수단 없음]" : safe(order.getDeliveryMethod().getMethodName());
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
            warn(warnings, "배송 담당자", "직배송 담당자명 '" + name + "'이 배송수단 부호와 충돌합니다. 담당자명을 확인해 주세요.");
        }
        return name;
    }

    private String addressRow(Order order, OrderExcelDeliveryRule rule, List<ExportWarning> warnings) {
        boolean site = rule != null && rule.usesSiteAddress();
        if (rule == null) site = !safe(order.getSiteRoadAddress()).isBlank();
        // 택배/현장/화물과 미지원 배송수단은 주소가 비어 있어도 확인 대상으로 남깁니다.
        if (rule != null && !site && rule != OrderExcelDeliveryRule.PARCEL) {
            Company company = company(order);
            Member requested = order.getTask() == null ? null : order.getTask().getRequestedBy();
            boolean customAddress = company == null || !safe(order.getRoadAddress()).equals(safe(company.getRoadAddress()))
                    || !safe(order.getDetailAddress()).equals(safe(company.getDetailAddress()))
                    || !safe(order.getZipCode()).equals(safe(company.getZipCode()));
            boolean customContact = requested == null || !safe(order.getOrdererName()).equals(safe(requested.getName()))
                    || !phoneDigits(order.getOrdererPhone()).equals(phoneDigits(requested.getPhone()));
            if (!customAddress && !customContact) return null;
        }
        // 현장 주소가 있으면 우편번호/상세주소도 같은 주소 묶음에서 읽습니다.
        // 현장 상세주소가 비어 있다고 다른 기본 배송지의 동/호수를 붙이지 않습니다.
        boolean savedSite = site && !safe(order.getSiteRoadAddress()).isBlank();
        String road = savedSite ? safe(order.getSiteRoadAddress()) : safe(order.getRoadAddress());
        String detail = savedSite ? safe(order.getSiteDetailAddress()) : safe(order.getDetailAddress());
        String zip = savedSite ? safe(order.getSiteZipCode()) : safe(order.getZipCode());
        if (site && !savedSite && (!safe(order.getSiteDetailAddress()).isBlank() || !safe(order.getSiteZipCode()).isBlank())) {
            warn(warnings, "배송지", "현장 도로주소 없이 현장 상세주소/우편번호만 있습니다. 기본 배송지 묶음으로 출력하고 현장 원문은 출력확인 시트에 보존했습니다. 재업로드 전에 확인해 주세요.");
        }
        String originalAddress = joinAddress(road, detail);
        // DB를 바꾸지 않고, 실제 도로명 접미부와 일치하는 상세주소 접두부만 정리합니다.
        String cleanedDetail = addressParser.normalizeDetailAddress(road, detail);
        if (!equivalentAddress(detail, cleanedDetail)) {
            warnings.add(new ExportWarning("배송지", "도로명 끝부분과 중복된 상세주소 접두부를 출력에서 정리했습니다. 원래 배송지: "
                    + originalAddress, true));
        }
        String address = joinAddress(road, cleanedDetail);
        // 도로주소 칸 자체에 상세주소/중복 접두부가 함께 저장된 과거 자료도 처리합니다.
        ParsedSiteAddress addressParts = addressParser.parseLocal(address);
        String normalizedAddress = joinAddress(safe(addressParts.getRoadAddress()), safe(addressParts.getDetailAddress()));
        boolean corrected = false;
        // 순수 주소만 파싱했고 우편번호/연락처를 떼어내지 않은 경우에만 중복 정리 결과를 사용합니다.
        if (safe(addressParts.getZipCode()).isBlank() && safe(addressParts.getContactPart()).isBlank()
                && equivalentAddress(address, addressParts.getAddressPart())) {
            corrected = !equivalentAddress(address, normalizedAddress);
            address = normalizedAddress;
        }
        for (String warning : addressParts.getWarnings()) {
            warnings.add(new ExportWarning("배송지", warning, corrected && warning.contains("중복")));
        }
        if (!addressParser.looksLikeAddress(address)) {
            warn(warnings, "배송지", "배송지를 업로드 주소행으로 인식할 수 없습니다. 원문을 출력했습니다. 재업로드 전에 주소검색으로 확인해 주세요.");
        }
        if (zip.isBlank()) {
            warn(warnings, "우편번호", "우편번호가 없습니다. 우편번호를 임의로 생성하지 않고 주소 원문을 출력했습니다.");
        } else if (!zip.matches("\\d{5}")) {
            warn(warnings, "우편번호", "우편번호 '" + zip + "'가 5자리 숫자가 아닙니다. 저장값을 그대로 출력했습니다. 재업로드 전에 확인해 주세요.");
        }
        String contact = (safe(order.getOrdererName()) + " " + safe(order.getOrdererPhone())).trim();
        // 앞 우편번호/연락처 구분자 규약은 유지하며 잘못된 값도 원문으로 출력합니다.
        String serialized = (zip.isBlank() ? "" : zip + " ") + address.trim()
                + (contact.isBlank() ? "" : " / " + contact);
        ParsedSiteAddress parsed = addressParser.parseLocal(serialized);
        String restoredAddress = joinAddress(safe(parsed.getRoadAddress()), safe(parsed.getDetailAddress()));
        if (!equivalentAddress(address, restoredAddress)) {
            warn(warnings, "배송지", "주소행을 재업로드할 때 배송지가 다르게 읽힐 수 있습니다. 출력주소와 원래 배송지를 출력확인 시트에서 확인해 주세요.");
        }
        if (!zip.isBlank() && !zip.equals(safe(parsed.getZipCode()))) {
            warn(warnings, "우편번호", "주소행에서 우편번호를 정확하게 읽을 수 없습니다. 원래 우편번호를 출력확인 시트에 보존했습니다.");
        }
        if (!safe(parsed.getRecipientName()).equals(safe(order.getOrdererName()))
                || !phoneDigits(parsed.getRecipientPhone()).equals(phoneDigits(order.getOrdererPhone()))) {
            warn(warnings, "현장 수령자", "수령자명/연락처가 재업로드 시 다르게 읽힐 수 있습니다. 저장된 이름/전화번호를 그대로 출력했습니다. 출력확인 시트에서 확인해 주세요.");
        }
        for (String warning : parsed.getWarnings()) warn(warnings, "배송지", warning);
        return serialized;
    }

    private static String joinAddress(String road, String detail) {
        return (road + (detail.isBlank() || road.endsWith(detail) ? "" : " " + detail)).trim();
    }

    private static String phoneDigits(String value) { return safe(value).replaceAll("\\D", ""); }

    /** Separators introduced by road/detail splitting are formatting, not lost address content. */
    private static boolean equivalentAddress(String left, String right) {
        return safe(left).replaceAll("[\\s,;|｜、，]+", "").equals(safe(right).replaceAll("[\\s,;|｜、，]+", ""));
    }

    private void writeContext(Row row, ExportItem item, ExportBlock block) {
        Order order = item.order();
        if (order.getPreferredDeliveryDate() != null) {
            LocalDate date = order.getPreferredDeliveryDate().toLocalDate();
            row.getCell(0).setCellValue(date.atStartOfDay());
            text(row, 1, WEEKDAYS.get(date.getDayOfWeek().getValue() - 1));
        }
        Company company = company(order);
        text(row, 2, company == null ? "" : company.getCompanyName());
        setCellText(row.getCell(REGIONAL_CUSTOMER_COLUMN), item.regionalName());
        Task task = order.getTask();
        text(row, 8, task == null || task.getManagedBy() == null ? "" : task.getManagedBy().getName());
        text(row, 9, item.token());
        text(row, 18, company == null ? "" : businessNumber(company.getBusinessNumber()));
    }

    private int maxCharge(Order first, List<Order> selected, boolean delivery) {
        Task task = first.getTask();
        if (task == null) return Math.max(0, delivery ? first.getDeliveryCost() : first.getPackingCost());
        // 기존 업로드는 Task의 첫 Order에만 비용을 저장합니다. 필터에서 그 Order가 빠져도 비용은 유실하지 않습니다.
        List<Order> members = task != null && task.getOrders() != null && !task.getOrders().isEmpty()
                ? task.getOrders() : selected;
        return members == null ? 0 : members.stream().filter(o -> o != null && (task == null || o.getTask() == task
                || (o.getTask() != null && task.getId() != null && task.getId().equals(o.getTask().getId()))))
                .mapToInt(o -> delivery ? o.getDeliveryCost() : o.getPackingCost()).max().orElse(0);
    }

    private void writeCharge(Workbook workbook, Sheet sheet, int index, CellStyle[] styles, short height,
                             ExportItem item, ExportBlock block, String name, int total,
                             CellStyle[] warningStyles) {
        Row row = newRow(sheet, index, styles, height);
        writeContext(row, item, block);
        text(row, 3, name);
        row.getCell(exportColumn(5)).setCellValue(1);
        // 업로드 resolveFreightCost는 O -> M -> L을 그대로 비용으로 사용합니다. VAT 포함 합계를 O열에 둡니다.
        int supply = java.math.BigDecimal.valueOf(total).divide(new java.math.BigDecimal("1.1"), 0, java.math.RoundingMode.HALF_UP).intValue();
        row.getCell(exportColumn(11)).setCellValue(supply);
        row.getCell(exportColumn(12)).setCellValue(supply);
        row.getCell(exportColumn(13)).setCellValue(total - supply);
        row.getCell(exportColumn(14)).setCellValue(total);
        if (!item.warnings().isEmpty()) markWarning(row, warningStyles, item.warnings(), "비용행 / Order " + item.order().getId());
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
                .thenComparing(o -> o.getTask() == null ? null : o.getTask().getId(), Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Order::getId, Comparator.nullsLast(Comparator.naturalOrder()));
    }

    private Company company(Order order) {
        return order == null || order.getTask() == null || order.getTask().getRequestedBy() == null
                ? null : order.getTask().getRequestedBy().getCompany();
    }

    private Object taskIdentity(Order order) {
        if (order.getTask() != null && order.getTask().getId() != null) return "task:" + order.getTask().getId();
        if (order.getId() != null) return "order:" + order.getId();
        // Identity of an unsaved entity keeps its block separate without guessing an ID.
        return order;
    }

    private static void warn(List<ExportWarning> warnings, String field, String message) {
        ExportWarning warning = new ExportWarning(field, message, false);
        if (!warnings.contains(warning)) warnings.add(warning);
    }

    /** References below use the original 19-column indices; D insertion shifts D:S one place. */
    private static int exportColumn(int originalColumn) {
        return originalColumn >= REGIONAL_CUSTOMER_COLUMN ? originalColumn + 1 : originalColumn;
    }

    private static void text(Row row, int column, String value) {
        setCellText(row.getCell(exportColumn(column)), value);
    }

    /** Clone template styles once; date/amount formats and borders remain intact. */
    private CellStyle[] warningStyles(Workbook workbook, CellStyle[] originals) {
        CellStyle[] result = new CellStyle[originals.length];
        Map<Integer, Font> fonts = new LinkedHashMap<>();
        for (int col = 0; col < originals.length; col++) {
            CellStyle style = workbook.createCellStyle();
            style.cloneStyleFrom(originals[col]);
            Font font = fonts.computeIfAbsent((int) originals[col].getFontIndex(), index -> {
                Font base = workbook.getFontAt(index);
                Font highlighted = workbook.createFont();
                highlighted.setFontName(base.getFontName());
                highlighted.setFontHeight(base.getFontHeight());
                highlighted.setItalic(base.getItalic());
                highlighted.setStrikeout(base.getStrikeout());
                highlighted.setTypeOffset(base.getTypeOffset());
                highlighted.setUnderline(base.getUnderline());
                highlighted.setCharSet(base.getCharSet());
                highlighted.setBold(true);
                highlighted.setColor(IndexedColors.DARK_RED.getIndex());
                return highlighted;
            });
            style.setFont(font);
            style.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            result[col] = style;
        }
        return result;
    }

    private void markWarning(Row row, CellStyle[] styles, List<ExportWarning> warnings, String label) {
        for (int col = 0; col < COLUMN_COUNT; col++) row.getCell(col).setCellStyle(styles[col]);
        Sheet sheet = row.getSheet();
        Workbook workbook = sheet.getWorkbook();
        Drawing<?> drawing = sheet.createDrawingPatriarch();
        ClientAnchor anchor = workbook.getCreationHelper().createClientAnchor();
        anchor.setCol1(4);
        anchor.setCol2(11);
        anchor.setRow1(row.getRowNum());
        anchor.setRow2(Math.min(row.getRowNum() + 8, workbook.getSpreadsheetVersion().getLastRowIndex()));
        Comment comment = drawing.createCellComment(anchor);
        String details = label + "\n" + warnings.stream()
                .map(warning -> (warning.corrected() ? "[자동 정리] " : "[확인 필요] ") + warning.field() + ": " + warning.message())
                .distinct().reduce((a, b) -> a + "\n" + b).orElse("")
                + "\n원래 저장값 및 확인 사유는 '출력확인' 시트에서 확인해 주세요. DB의 값은 변경하지 않았습니다.";
        comment.setString(workbook.getCreationHelper().createRichTextString(limitText(details)));
        comment.setAuthor("발주등록용 엑셀");
        row.getCell(exportColumn(3)).setCellComment(comment);
    }

    private void writeReviewSheet(Workbook workbook, String dataSheetName, List<ExportReview> reviews) {
        Sheet sheet = workbook.createSheet("출력확인");
        sheet.createRow(0).createCell(0).setCellValue("발주등록용 엑셀 출력 확인");
        String summary = reviews.isEmpty() ? "자동 검증에서 확인이 필요한 품목이 없습니다."
                : "표시 대상 품목 " + reviews.size() + "개. 본문에서 노란 배경·굵은 진한 빨간 글씨로 표시했습니다. 품목명 셀의 메모도 확인해 주세요.";
        sheet.createRow(1).createCell(0).setCellValue(summary);
        sheet.createRow(2).createCell(0).setCellValue("확인 필요 행은 재업로드 전에 수정해 주세요. 주소 중복 정리는 출력에만 적용했고 DB 저장값은 변경하지 않았습니다. 이 시트는 업로드에서 읽지 않습니다.");
        String[] headers = { "본문 시트", "품목 Excel 행", "Order ID", "Task ID", "거래처", "구분", "항목", "확인 사유",
                "출력 주소행", "기본 우편번호 원문", "기본 도로주소 원문", "기본 상세주소 원문",
                "현장 우편번호 원문", "현장 도로주소 원문", "현장 상세주소 원문", "수령자명 원문", "연락처 원문",
                "품목명 원문", "제품명 원문", "제품 옵션 JSON 원문", "관리자 비고 원문", "배송수단 원문" };
        CellStyle headerStyle = workbook.createCellStyle();
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);
        headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        CellStyle bodyStyle = workbook.createCellStyle();
        bodyStyle.setWrapText(true);
        bodyStyle.setVerticalAlignment(org.apache.poi.ss.usermodel.VerticalAlignment.TOP);
        Row header = sheet.createRow(3);
        for (int col = 0; col < headers.length; col++) {
            header.createCell(col).setCellValue(headers[col]);
            header.getCell(col).setCellStyle(headerStyle);
            sheet.setColumnWidth(col, (col < 7 ? 18 : col == 7 || col == 8 || col == 19 ? 60 : 36) * 256);
        }
        int index = 4;
        for (ExportReview review : reviews) {
            ExportItem item = review.item();
            Order order = item.order();
            OrderItem sourceItem = order.getOrderItem();
            for (ExportWarning warning : item.warnings().stream().distinct().toList()) {
                Row row = sheet.createRow(index++);
                row.setHeightInPoints(60);
                String[] values = { dataSheetName, String.valueOf(review.excelRow()), String.valueOf(order.getId()),
                        order.getTask() == null ? "" : String.valueOf(order.getTask().getId()),
                        company(order) == null ? "" : company(order).getCompanyName(), warning.corrected() ? "자동 정리" : "확인 필요",
                        warning.field(), warning.message(), review.addressRow(), order.getZipCode(), order.getRoadAddress(), order.getDetailAddress(),
                        order.getSiteZipCode(), order.getSiteRoadAddress(), order.getSiteDetailAddress(), order.getOrdererName(), order.getOrdererPhone(),
                        sourceItem == null ? "" : sourceItem.getItemName(), sourceItem == null ? "" : sourceItem.getProductName(),
                        sourceItem == null ? "" : sourceItem.getOptionJson(), order.getAdminMemo(),
                        order.getDeliveryMethod() == null ? "" : order.getDeliveryMethod().getMethodName() };
                for (int col = 0; col < values.length; col++) {
                    Cell cell = row.createCell(col);
                    setCellText(cell, values[col]);
                    cell.setCellStyle(bodyStyle);
                }
            }
        }
        workbook.setSheetOrder("출력확인", 1);
        sheet.createFreezePane(7, 4);
        sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(3, Math.max(3, index - 1), 0, headers.length - 1));
    }

    private static String limitText(String value) {
        if (value.length() <= 32767) return value;
        int end = 32700;
        if (Character.isHighSurrogate(value.charAt(end - 1))) end--;
        return value.substring(0, end) + " … [전체 내용은 출력확인/긴 원문 시트 참조]";
    }

    /** POI rejects >32,767 characters; retain every UTF-16 character in ordered chunks. */
    private static void setCellText(Cell cell, String raw) {
        String value = raw == null ? "" : raw;
        cell.setCellValue(limitText(value));
        if (value.length() <= 32767) return;
        Workbook workbook = cell.getSheet().getWorkbook();
        Sheet archive = workbook.getSheet("긴 원문");
        if (archive == null) {
            archive = workbook.createSheet("긴 원문");
            Row header = archive.createRow(0);
            String[] headers = { "원래 시트", "셀", "부분 순서", "원문 (순서대로 이어붙이세요)" };
            for (int col = 0; col < headers.length; col++) header.createCell(col).setCellValue(headers[col]);
            archive.createFreezePane(0, 1);
            archive.setColumnWidth(0, 24 * 256);
            archive.setColumnWidth(1, 15 * 256);
            archive.setColumnWidth(2, 15 * 256);
            archive.setColumnWidth(3, 80 * 256);
        }
        int offset = 0;
        int part = 1;
        while (offset < value.length()) {
            int end = Math.min(value.length(), offset + 32767);
            if (end < value.length() && Character.isHighSurrogate(value.charAt(end - 1))) end--;
            Row row = archive.createRow(archive.getLastRowNum() + 1);
            row.createCell(0).setCellValue(cell.getSheet().getSheetName());
            row.createCell(1).setCellValue(cell.getAddress().formatAsString());
            row.createCell(2).setCellValue(part++);
            row.createCell(3).setCellValue(value.substring(offset, end));
            offset = end;
        }
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

    private String regionalCustomerName(Order order, Map<String, Set<String>> customerNames, List<ExportWarning> warnings) {
        Company company = company(order);
        String key = normalizedBusinessNumber(company == null ? null : company.getBusinessNumber());
        Set<String> names = customerNames.get(key);
        if (key.isBlank() || names == null || names.isEmpty()) return "";
        if (names.size() > 1) {
            warn(warnings, "지역 포함 거래처명", "사업자번호 " + businessNumber(key)
                    + "에 서로 다른 거래처 마스터명이 여러 개 있습니다. 임의 선택하지 않고 모두 병기했습니다: "
                    + String.join(" / ", names));
        }
        return String.join(" / ", names);
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

    private record ExportWarning(String field, String message, boolean corrected) { }
    private record ExportReview(ExportItem item, int excelRow, String addressRow) { }
    private record ExportItem(Order order, String itemName, String size, String category,
                              String token, String regionalName, List<ExportWarning> warnings) { }
    private record ExportBlock(List<ExportItem> items, OrderExcelDeliveryRule rule, String addressRow) { }
    private record BlockKey(Object task, Long company, OrderExcelDeliveryRule rule,
                            String handler, String road, String detail, String siteAddress, String orderer, String phone) { }
}
