package com.dev.HiddenBATHAuto.service.amount;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.dev.HiddenBATHAuto.dto.amount.AmountCustomerMatchResult;
import com.dev.HiddenBATHAuto.dto.amount.AmountItemMatchResult;
import com.dev.HiddenBATHAuto.dto.amount.AmountParsedOrderProduct;
import com.dev.HiddenBATHAuto.model.amount.AmountCustomerMaster;
import com.dev.HiddenBATHAuto.model.amount.AmountItemMaster;
import com.dev.HiddenBATHAuto.model.auth.Company;
import com.dev.HiddenBATHAuto.model.task.Order;
import com.dev.HiddenBATHAuto.model.task.OrderItem;
import com.dev.HiddenBATHAuto.model.task.OrderStatus;
import com.dev.HiddenBATHAuto.model.task.Task;
import com.dev.HiddenBATHAuto.repository.amount.AmountCustomerMasterRepository;
import com.dev.HiddenBATHAuto.repository.amount.AmountItemMasterRepository;
import com.dev.HiddenBATHAuto.repository.order.OrderRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AmountSalesVoucherExportService {

    private static final DateTimeFormatter YMD = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String SALES_DIVISION_CODE = "1";
    private static final String TAX_TYPE_CODE = "1";

    private final OrderRepository orderRepository;
    private final AmountCustomerMasterRepository customerRepository;
    private final AmountItemMasterRepository itemRepository;
    private final AmountOrderOptionParser optionParser;
    private final OpenAiAmountProductMatchClient aiProductMatchClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional(readOnly = true)
    public void downloadSalesVoucher(String keyword,
                                     String dateCriteria,
                                     String startDate,
                                     String endDate,
                                     String productCategoryId,
                                     String orderStatus,
                                     String standard,
                                     String sortField,
                                     String sortDir,
                                     HttpServletResponse response) throws IOException {
        String finalDateCriteria = normalizeDateCriteria(dateCriteria);
        DateRange range = buildDateRangeForCriteria(finalDateCriteria, startDate, endDate);
        Long categoryId = parseLongOrNullAllowAll(productCategoryId);
        OrderStatus status = parseOrderStatusOrNullWithDefault(orderStatus, null);
        Boolean standardBool = parseStandardOrNull(standard);
        String finalKeyword = StringUtils.hasText(keyword) ? keyword.trim() : null;

        List<Order> orders = orderRepository.findFilteredOrdersForExcel(
                finalKeyword,
                finalDateCriteria,
                range.start(),
                range.end(),
                categoryId,
                status,
                standardBool
        );

        List<AmountCustomerMaster> customers = customerRepository.findAll();
        List<AmountItemMaster> items = itemRepository.findAll(Sort.by(Sort.Direction.ASC, "itemName"));

        List<TaskVoucherBlock> blocks = buildTaskBlocks(orders, customers, items, sortField, sortDir);
        writeWorkbook(blocks, customers, items, response);
    }

    /**
     * 전산입력용 출력 구조:
     * 거래처 A - Task 1 오더들 - Task 1 운임비 1회 - Task 2 오더들 - Task 2 운임비 1회 ...
     *
     * deliveryCost/packingCost는 현재 Order에 있지만 실제 업무 의미는 Task 단위 비용이므로,
     * Task 안의 여러 Order에 중복 저장되어 있어도 중복 합산하지 않고 각각 최대값 1회만 사용합니다.
     */
    private List<TaskVoucherBlock> buildTaskBlocks(List<Order> orders,
                                                   List<AmountCustomerMaster> customers,
                                                   List<AmountItemMaster> items,
                                                   String sortField,
                                                   String sortDir) {
        if (orders == null || orders.isEmpty()) {
            return List.of();
        }

        List<Order> sortedOrders = orders.stream()
                .sorted(buildTaskOrderComparator(sortField, sortDir))
                .toList();

        Map<Long, List<Order>> ordersByTask = new LinkedHashMap<>();
        for (Order order : sortedOrders) {
            Long taskId = resolveTaskId(order);
            ordersByTask.computeIfAbsent(taskId, key -> new ArrayList<>()).add(order);
        }

        List<TaskVoucherBlock> blocks = new ArrayList<>();
        for (Map.Entry<Long, List<Order>> entry : ordersByTask.entrySet()) {
            List<Order> taskOrders = entry.getValue().stream()
                    .sorted(Comparator.comparing(Order::getId, Comparator.nullsLast(Long::compareTo)))
                    .toList();
            if (taskOrders.isEmpty()) {
                continue;
            }

            Task task = taskOrders.get(0).getTask();
            Company company = resolveCompany(taskOrders.get(0));
            String companyName = company != null ? safe(company.getCompanyName()) : safe(() -> task.getRequestedBy().getCompany().getCompanyName());
            String businessNumber = company != null ? safe(company.getBusinessNumber()) : "";
            AmountCustomerMatchResult customerMatch = matchCustomer(companyName, businessNumber, customers);
            LocalDate transactionDate = resolveTaskTransactionDate(task, taskOrders);

            List<VoucherLine> lines = new ArrayList<>();
            for (Order order : taskOrders) {
                VoucherLine line = buildOrderLine(order, entry.getKey(), transactionDate, companyName, customerMatch, items);
                if (line != null) {
                    lines.add(line);
                }
            }

            VoucherLine freightLine = buildTaskFreightLine(entry.getKey(), transactionDate, companyName, customerMatch, taskOrders, items);
            if (freightLine != null) {
                lines.add(freightLine);

                VoucherLine addressLine = buildTaskAddressLine(entry.getKey(), transactionDate, companyName, customerMatch, taskOrders);
                if (addressLine != null) {
                    lines.add(addressLine);
                }
            }

            BigDecimal taskTotal = lines.stream()
                    .map(VoucherLine::total)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            blocks.add(new TaskVoucherBlock(entry.getKey(), transactionDate, companyName, customerMatch, lines, taskTotal));
        }
        return blocks;
    }

    private VoucherLine buildOrderLine(Order order,
                                       Long taskId,
                                       LocalDate transactionDate,
                                       String companyName,
                                       AmountCustomerMatchResult customerMatch,
                                       List<AmountItemMaster> items) {
        if (order == null) {
            return null;
        }
        AmountParsedOrderProduct parsed = optionParser.parse(order);
        AmountItemMatchResult itemMatch = matchItem(parsed, items);
        AmountItemMaster matchedItem = itemMatch.item();

        int qty = resolveQuantity(order);
        Money money = resolveProductMoney(order, matchedItem, qty);
        String spec = StringUtils.hasText(parsed.sizeText()) ? parsed.sizeText() : value(matchedItem, AmountItemMaster::getSpecification);
        String unit = StringUtils.hasText(value(matchedItem, AmountItemMaster::getUnit))
                ? matchedItem.getUnit()
                : safe(() -> order.getProductCategory().getName());
        String memo = buildOrderMemo(order, itemMatch, customerMatch);
        String optionText = buildOrderOptionText(order);

        return new VoucherLine(
                taskId,
                order.getId(),
                false,
                false,
                transactionDate,
                companyName,
                customerMatch,
                itemMatch,
                itemMatch.item() != null ? itemMatch.item().getItemCode() : "",
                itemMatch.item() != null ? itemMatch.item().getItemName() : fallbackProductName(parsed, order),
                spec,
                unit,
                qty,
                money.unitPrice(),
                money.supply(),
                money.vat(),
                money.total(),
                memo,
                optionText
        );
    }

    private VoucherLine buildTaskFreightLine(Long taskId,
                                             LocalDate transactionDate,
                                             String companyName,
                                             AmountCustomerMatchResult customerMatch,
                                             List<Order> taskOrders,
                                             List<AmountItemMaster> items) {
        int taskDeliveryCost = taskOrders.stream()
                .mapToInt(Order::getDeliveryCost)
                .filter(value -> value > 0)
                .max()
                .orElse(0);
        int taskPackingCost = taskOrders.stream()
                .mapToInt(Order::getPackingCost)
                .filter(value -> value > 0)
                .max()
                .orElse(0);

        int taskFreightSupply = taskDeliveryCost + taskPackingCost;

        // 샘플 전산입력용 엑셀처럼 운임비는 Task 마지막에 항상 1행 출력합니다.
        // 금액이 0이어도 운임비 품목코드와 바로 다음 주소 행 구조를 유지합니다.
        AmountItemMaster freightItem = findFreightItem(items).orElse(null);
        AmountItemMatchResult freightMatch = freightItem == null
                ? AmountItemMatchResult.empty("품목 마스터에서 운임비 항목을 찾지 못했습니다.")
                : new AmountItemMatchResult(freightItem, 100, "EXACT", "Task 단위 운임비 고정 매칭", false);

        BigDecimal supply = BigDecimal.valueOf(taskFreightSupply);
        BigDecimal vat = supply.multiply(BigDecimal.valueOf(0.1)).setScale(0, RoundingMode.HALF_UP);
        BigDecimal total = supply.add(vat);
        String memo = "Task " + taskId + " 운임비 1회 처리"
                + " / 배송비=" + taskDeliveryCost
                + " / 포장비=" + taskPackingCost
                + " / Order별 중복 금액은 합산하지 않음";

        return new VoucherLine(
                taskId,
                null,
                true,
                false,
                transactionDate,
                companyName,
                customerMatch,
                freightMatch,
                freightItem != null ? safe(freightItem.getItemCode()) : "",
                freightItem != null ? safe(freightItem.getItemName()) : "운임비",
                freightItem != null ? safe(freightItem.getSpecification()) : "",
                freightItem != null && StringUtils.hasText(freightItem.getUnit()) ? freightItem.getUnit() : "기타",
                1,
                supply,
                supply,
                vat,
                total,
                memo,
                ""
        );
    }

    private VoucherLine buildTaskAddressLine(Long taskId,
                                             LocalDate transactionDate,
                                             String companyName,
                                             AmountCustomerMatchResult customerMatch,
                                             List<Order> taskOrders) {
        String address = buildTaskAddressText(taskOrders);
        if (!StringUtils.hasText(address)) {
            return null;
        }
        String phoneMemo = buildTaskAddressMemo(taskOrders);
        AmountItemMatchResult addressMatch = new AmountItemMatchResult(null, 100, "EXACT", "운임비 하단 주소 보조행", false);
        BigDecimal zero = BigDecimal.ZERO;

        return new VoucherLine(
                taskId,
                null,
                false,
                true,
                transactionDate,
                companyName,
                customerMatch,
                addressMatch,
                "",
                address,
                "",
                "",
                0,
                zero,
                zero,
                zero,
                zero,
                phoneMemo,
                ""
        );
    }

    private String buildTaskAddressText(List<Order> taskOrders) {
        if (taskOrders == null || taskOrders.isEmpty()) {
            return "";
        }
        return taskOrders.stream()
                .map(this::buildOrderAddressText)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.joining(" / "));
    }

    private String buildOrderAddressText(Order order) {
        if (order == null) {
            return "";
        }

        String siteAddress = joinNonBlank(" ",
                order.getSiteDoName(),
                order.getSiteSiName(),
                order.getSiteGuName(),
                order.getSiteRoadAddress(),
                order.getSiteDetailAddress()
        );
        if (StringUtils.hasText(siteAddress)) {
            return siteAddress;
        }

        return joinNonBlank(" ",
                order.getDoName(),
                order.getSiName(),
                order.getGuName(),
                order.getRoadAddress(),
                order.getDetailAddress()
        );
    }

    private String buildTaskAddressMemo(List<Order> taskOrders) {
        if (taskOrders == null || taskOrders.isEmpty()) {
            return "";
        }
        return taskOrders.stream()
                .map(order -> joinNonBlank(" ", order.getOrdererName(), order.getOrdererPhone()))
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.joining(" / "));
    }

    private String joinNonBlank(String delimiter, String... values) {
        if (values == null || values.length == 0) {
            return "";
        }
        return java.util.Arrays.stream(values)
                .map(this::safe)
                .filter(StringUtils::hasText)
                .collect(Collectors.joining(delimiter));
    }

    private Optional<AmountItemMaster> findFreightItem(List<AmountItemMaster> items) {
        if (items == null || items.isEmpty()) {
            return Optional.empty();
        }
        Optional<AmountItemMaster> exact = items.stream()
                .filter(item -> "운임비".equals(AmountTextNormalizer.compact(item.getItemName())))
                .findFirst();
        if (exact.isPresent()) {
            return exact;
        }
        return items.stream()
                .filter(item -> {
                    String name = AmountTextNormalizer.compact(item.getItemName());
                    return name.contains("운임비") || name.contains("배송비") || name.contains("화물비");
                })
                .findFirst();
    }

    private AmountCustomerMatchResult matchCustomer(String companyName, String businessNumber, List<AmountCustomerMaster> customers) {
        if (customers == null || customers.isEmpty()) {
            return AmountCustomerMatchResult.empty("거래처 마스터가 비어 있습니다.");
        }

        String companyBusinessDigits = digitsOnly(businessNumber);
        if (StringUtils.hasText(companyBusinessDigits)) {
            Optional<AmountCustomerMaster> byBusinessNo = customers.stream()
                    .filter(customer -> companyBusinessDigits.equals(digitsOnly(customer.getBusinessNo())))
                    .findFirst();
            if (byBusinessNo.isPresent()) {
                return new AmountCustomerMatchResult(byBusinessNo.get(), 100, "EXACT", "사업자번호 100% 일치");
            }
        }

        if (!StringUtils.hasText(companyName)) {
            return AmountCustomerMatchResult.empty("거래처명 없음");
        }

        AmountCustomerMaster best = null;
        int bestScore = 0;
        String bestReason = "";
        for (AmountCustomerMaster customer : customers) {
            int score1 = AmountTextNormalizer.similarity100(companyName, customer.getCustomerName());
            int score2 = AmountTextNormalizer.similarity100(companyName, customer.getBusinessName());
            int score = Math.max(score1, score2);
            if (score > bestScore) {
                bestScore = score;
                best = customer;
                bestReason = score1 >= score2 ? "거래처명 비교" : "상호명 비교";
            }
        }
        return new AmountCustomerMatchResult(best, bestScore, level(bestScore), bestReason);
    }

    private AmountItemMatchResult matchItem(AmountParsedOrderProduct parsed, List<AmountItemMaster> items) {
        if (items == null || items.isEmpty()) {
            return AmountItemMatchResult.empty("품목 마스터가 비어 있습니다.");
        }
        List<ScoredItem> scored = items.stream()
                .map(item -> new ScoredItem(item, scoreItem(parsed, item)))
                .sorted(Comparator.comparingInt(ScoredItem::score).reversed())
                .limit(25)
                .toList();
        ScoredItem best = scored.isEmpty() ? null : scored.get(0);
        if (best == null) {
            return AmountItemMatchResult.empty("품목 후보 없음");
        }

        boolean aiUsed = false;
        String reason = buildItemReason(parsed, best.item(), best.score());
        int finalScore = best.score();
        AmountItemMaster finalItem = best.item();

        boolean ambiguous = scored.size() > 1 && Math.abs(scored.get(0).score() - scored.get(1).score()) <= 5;
        if (finalScore < 97 || ambiguous) {
            Optional<OpenAiAmountProductMatchClient.AiProductChoice> aiChoice = aiProductMatchClient.chooseBest(parsed,
                    scored.stream().map(ScoredItem::item).toList());
            if (aiChoice.isPresent()) {
                OpenAiAmountProductMatchClient.AiProductChoice choice = aiChoice.get();
                Optional<AmountItemMaster> selected = scored.stream()
                        .map(ScoredItem::item)
                        .filter(item -> same(choice.itemCode(), item.getItemCode()) || same(choice.itemName(), item.getItemName()))
                        .findFirst();
                if (selected.isPresent()) {
                    finalItem = selected.get();
                    finalScore = Math.max(finalScore, choice.confidence());
                    reason = "AI선택: " + choice.reason();
                    aiUsed = true;
                }
            }
        }

        return new AmountItemMatchResult(finalItem, Math.max(0, Math.min(100, finalScore)), level(finalScore), reason, aiUsed);
    }

    private int scoreItem(AmountParsedOrderProduct parsed, AmountItemMaster item) {
        String itemName = AmountTextNormalizer.compact(item.getItemName());
        String itemSpec = AmountTextNormalizer.compact(item.getSpecification());
        String unit = AmountTextNormalizer.compact(item.getUnit());
        String all = itemName + itemSpec + unit;

        int score = 0;
        String product = AmountTextNormalizer.compact(parsed.productName());
        String series = AmountTextNormalizer.compact(parsed.series());
        String color = AmountTextNormalizer.compact(parsed.color());
        String category = AmountTextNormalizer.compact(parsed.category());

        if (StringUtils.hasText(product)) {
            score += Math.round(AmountTextNormalizer.similarity100(product, item.getItemName()) * 0.28f);
            if (itemName.contains(product) || product.contains(itemName)) {
                score += 8;
            }
        }
        if (StringUtils.hasText(series) && all.contains(series)) {
            score += 14;
        }
        if (StringUtils.hasText(color) && all.contains(color)) {
            score += 20;
        }
        if (parsed.width() != null) {
            String width = String.valueOf(parsed.width());
            if (all.contains(width)) {
                score += 22;
            }
        }
        if (parsed.height() != null && itemSpec.contains(String.valueOf(parsed.height()))) {
            score += 8;
        }
        if (StringUtils.hasText(category) && (unit.contains(category) || all.contains(category))) {
            score += 10;
        }
        if (parsed.doorCount() != null) {
            String door = parsed.doorCount() + "도어";
            if (all.contains(AmountTextNormalizer.compact(door)) || (parsed.doorCount() == 1 && all.contains("원도어"))) {
                score += 10;
            }
        }
        if (StringUtils.hasText(parsed.sizeText())) {
            score += Math.round(AmountTextNormalizer.similarity100(parsed.sizeText(), item.getSpecification()) * 0.08f);
        }
        return Math.max(0, Math.min(100, score));
    }

    private String buildItemReason(AmountParsedOrderProduct parsed, AmountItemMaster item, int score) {
        return "주문[" + parsed.displayText() + "] ↔ 품목[" + value(item, AmountItemMaster::getItemName) + " / "
                + value(item, AmountItemMaster::getSpecification) + " / " + value(item, AmountItemMaster::getUnit)
                + "], score=" + score;
    }

    private Money resolveProductMoney(Order order, AmountItemMaster matchedItem, int qty) {
        int safeQty = Math.max(qty, 1);
        BigDecimal supply = money(order.getSupplyPrice());
        BigDecimal total = money(order.getTotalAmount());
        BigDecimal unitPrice = money(order.getProductCost());

        if (supply.signum() <= 0 && unitPrice.signum() > 0) {
            supply = unitPrice.multiply(BigDecimal.valueOf(safeQty));
        }
        if (supply.signum() <= 0 && matchedItem != null) {
            unitPrice = parseMoney(matchedItem.getSalesPrice());
            supply = unitPrice.multiply(BigDecimal.valueOf(safeQty));
        }
        if (unitPrice.signum() <= 0 && safeQty > 0) {
            unitPrice = supply.divide(BigDecimal.valueOf(safeQty), 0, RoundingMode.HALF_UP);
        }

        BigDecimal vat;
        if (total.signum() > 0 && supply.signum() > 0 && total.compareTo(supply) >= 0) {
            vat = total.subtract(supply);
        } else {
            vat = supply.multiply(BigDecimal.valueOf(0.1)).setScale(0, RoundingMode.HALF_UP);
            total = supply.add(vat);
        }
        return new Money(unitPrice, supply, vat, total);
    }

    private void writeWorkbook(List<TaskVoucherBlock> blocks,
                               List<AmountCustomerMaster> customers,
                               List<AmountItemMaster> items,
                               HttpServletResponse response) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Map<String, CellStyle> styles = createStyles(workbook);
            Sheet sheet = workbook.createSheet("매입매출");
            Sheet customerSheet = workbook.createSheet("거래처");
            Sheet itemSheet = workbook.createSheet("제품");
            Sheet infoSheet = workbook.createSheet("설명");

            sheet.setDefaultRowHeightInPoints(18);
            customerSheet.setDefaultRowHeightInPoints(18);
            itemSheet.setDefaultRowHeightInPoints(18);
            infoSheet.setDefaultRowHeightInPoints(18);

            writeReferenceSheets(customerSheet, itemSheet, infoSheet, customers, items, styles);
            writeMainSheet(sheet, blocks, styles);
            workbook.setForceFormulaRecalculation(true);

            String filename = "전산입력용_매출전표_" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + ".xlsx";
            String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encoded);
            try (ServletOutputStream os = response.getOutputStream()) {
                workbook.write(os);
                os.flush();
            }
        }
    }

    private void writeMainSheet(Sheet sheet, List<TaskVoucherBlock> blocks, Map<String, CellStyle> styles) {
        Row title = sheet.createRow(0);
        title.setHeightInPoints(18);
        cell(title, 2, "매입매출전표품목내역입력", styles.get("title"));
        Row note = sheet.createRow(17);
        note.setHeightInPoints(62);
        cell(note, 0, "구분코드참고:\n\n1.매출\n2.매입\n3.매출환입\n4.매입환출\n5.고정자산", styles.get("note"));
        cell(note, 3, "유형코드참고:\n\n1.과세\n2.영세\n3.면세", styles.get("note"));
        cell(note, 5, "결제장부코드참고:\n\n1.현금\n2.예금\n3.외상\n4.카드\n5.어음", styles.get("note"));
        BigDecimal total = blocks.stream().map(TaskVoucherBlock::taskTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        cell(note, 8, "합계금액\n" + total.setScale(0, RoundingMode.HALF_UP).toPlainString(), styles.get("note"));

        String[] headers = {"거래일\n(예:2014-10-23)", "구분", "코드", "거래처명", "유형", "적요", "결제\n장부", "은행/카드\n코드", "거래금액", "품목코드", "품목명", "규격", "단위", "수량", "단가", "공급가", "부가세", "합계금액", "창고번호", "창고명", "원산지코드", "조달청식별코드", "매칭상태", "비고", "매칭점수", "옵션", "바이트"};
        Row header = sheet.createRow(18);
        header.setHeightInPoints(24);
        for (int i = 0; i < headers.length; i++) {
            cell(header, i, headers[i], styles.get("header"));
            sheet.setColumnWidth(i, switch (i) {
                case 3, 10, 23 -> 9000;
                case 25 -> 14000;
                case 11 -> 6500;
                case 0 -> 4800;
                case 26 -> 3200;
                default -> 4200;
            });
        }

        int rowIndex = 19;
        for (TaskVoucherBlock block : blocks) {
            boolean first = true;
            for (VoucherLine line : block.lines()) {
                Row row = sheet.createRow(rowIndex++);
                writeVoucherRow(row, line, first, block.taskTotal(), styles);
                first = false;
            }
        }
    }

    private void writeVoucherRow(Row row, VoucherLine line, boolean firstInTask, BigDecimal taskTotal, Map<String, CellStyle> styles) {
        row.setHeightInPoints(18);
        CellStyle base = styles.get("body");
        CellStyle money = styles.get("money");
        CellStyle itemStyle = line.addressLine()
                ? base
                : switch (line.itemMatch().level()) {
                    case "EXACT" -> styles.get("itemExact");
                    case "PARTIAL" -> styles.get("itemPartial");
                    default -> styles.get("itemReview");
                };
        CellStyle customerStyle = switch (line.customerMatch().level()) {
            case "EXACT" -> styles.get("itemExact");
            case "PARTIAL" -> styles.get("itemPartial");
            default -> styles.get("itemReview");
        };

        cell(row, 0, firstInTask ? line.transactionDate().format(YMD) : "", base);
        cell(row, 1, firstInTask ? SALES_DIVISION_CODE : "", base);
        cell(row, 2, firstInTask ? value(line.customerMatch().customer(), AmountCustomerMaster::getCustomerCode) : "", customerStyle);
        cell(row, 3, firstInTask ? line.companyName() : "", customerStyle);
        cell(row, 4, firstInTask ? TAX_TYPE_CODE : "", base);
        cell(row, 5, "", base);
        cell(row, 6, "", base);
        cell(row, 7, "", base);
        numeric(row, 8, firstInTask ? taskTotal : null, money);
        cell(row, 9, line.addressLine() ? "" : line.itemCode(), itemStyle);
        cell(row, 10, line.itemName(), itemStyle);
        cell(row, 11, line.addressLine() ? "" : line.specification(), itemStyle);
        cell(row, 12, line.addressLine() ? "" : line.unit(), itemStyle);

        if (line.addressLine()) {
            cell(row, 13, "", base);
            cell(row, 14, "", money);
            numeric(row, 15, BigDecimal.ZERO, money);
            numeric(row, 16, BigDecimal.ZERO, money);
            numeric(row, 17, BigDecimal.ZERO, money);
        } else {
            numeric(row, 13, BigDecimal.valueOf(line.quantity()), base);
            numeric(row, 14, line.unitPrice(), money);
            numeric(row, 15, line.supply(), money);
            numeric(row, 16, line.vat(), money);
            numeric(row, 17, line.total(), money);
        }

        cell(row, 18, "", base);
        cell(row, 19, "", base);
        cell(row, 20, "", base);
        cell(row, 21, line.addressLine() ? "" : value(line.itemMatch().item(), AmountItemMaster::getProcurementIdentifierCode), base);
        cell(row, 22, line.addressLine() ? "ADDRESS" : line.itemMatch().level(), itemStyle);
        cell(row, 23, line.memo(), base);
        if (line.addressLine()) {
            cell(row, 24, "", base);
        } else {
            numeric(row, 24, BigDecimal.valueOf(Math.min(line.itemMatch().score(), line.customerMatch().score())), base);
        }

        String optionText = line.addressLine() || line.freightLine() ? "" : safe(line.optionText());
        cell(row, 25, optionText, styles.get("option"));
        formula(row, 26, "LENB(" + excelCellRef(row, 25) + ")", base);
    }

    private void writeReferenceSheets(Sheet customerSheet, Sheet itemSheet, Sheet infoSheet,
                                      List<AmountCustomerMaster> customers, List<AmountItemMaster> items,
                                      Map<String, CellStyle> styles) {
        Row ch = customerSheet.createRow(0);
        ch.setHeightInPoints(20);
        cell(ch, 0, "거래처명", styles.get("header"));
        cell(ch, 1, "코드", styles.get("header"));
        int rowIndex = 1;
        for (AmountCustomerMaster customer : customers) {
            Row row = customerSheet.createRow(rowIndex++);
            row.setHeightInPoints(18);
            cell(row, 0, customer.getCustomerName(), styles.get("body"));
            cell(row, 1, customer.getCustomerCode(), styles.get("body"));
        }
        customerSheet.setColumnWidth(0, 10000);
        customerSheet.setColumnWidth(1, 4000);

        Row ih = itemSheet.createRow(0);
        ih.setHeightInPoints(20);
        cell(ih, 0, "품목명", styles.get("header"));
        cell(ih, 1, "코드", styles.get("header"));
        rowIndex = 1;
        for (AmountItemMaster item : items) {
            Row row = itemSheet.createRow(rowIndex++);
            row.setHeightInPoints(18);
            cell(row, 0, item.getItemName(), styles.get("body"));
            cell(row, 1, item.getItemCode(), styles.get("body"));
        }
        itemSheet.setColumnWidth(0, 12000);
        itemSheet.setColumnWidth(1, 4000);

        Row row = infoSheet.createRow(0);
        row.setHeightInPoints(18);
        cell(row, 0, "검정/무채색: 97점 이상 정확 매칭, 주황: 51~96점 부분 매칭, 빨강: 50점 이하 재검토 필요", styles.get("body"));
        Row row2 = infoSheet.createRow(1);
        row2.setHeightInPoints(18);
        cell(row2, 0, "운임비: 품목 마스터에서 운임비 코드를 찾아 Task 마지막에 1회 출력하고, 바로 다음 행 품목명 칸에 배송/현장 주소를 출력합니다. deliveryCost/packingCost는 Task 단위 비용으로 보고 최대값 1회만 사용합니다.", styles.get("body"));
        infoSheet.setColumnWidth(0, 28000);
    }

    private Map<String, CellStyle> createStyles(Workbook workbook) {
        Map<String, CellStyle> styles = new LinkedHashMap<>();
        Font normal = workbook.createFont();
        normal.setFontHeightInPoints((short) 9);

        Font bold = workbook.createFont();
        bold.setBold(true);
        bold.setFontHeightInPoints((short) 9);

        CellStyle title = workbook.createCellStyle();
        title.setFont(bold);
        title.setAlignment(HorizontalAlignment.CENTER);
        title.setVerticalAlignment(VerticalAlignment.CENTER);
        styles.put("title", title);

        CellStyle header = workbook.createCellStyle();
        header.setFont(bold);
        header.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        header.setAlignment(HorizontalAlignment.CENTER);
        header.setVerticalAlignment(VerticalAlignment.CENTER);
        header.setWrapText(true);
        border(header);
        styles.put("header", header);

        CellStyle body = workbook.createCellStyle();
        body.setFont(normal);
        body.setVerticalAlignment(VerticalAlignment.CENTER);
        body.setWrapText(false);
        border(body);
        styles.put("body", body);

        CellStyle money = workbook.createCellStyle();
        money.cloneStyleFrom(body);
        money.setDataFormat(workbook.createDataFormat().getFormat("#,##0"));
        styles.put("money", money);

        CellStyle note = workbook.createCellStyle();
        note.cloneStyleFrom(body);
        note.setWrapText(true);
        note.setVerticalAlignment(VerticalAlignment.TOP);
        styles.put("note", note);

        CellStyle option = workbook.createCellStyle();
        option.cloneStyleFrom(body);
        option.setWrapText(true);
        option.setVerticalAlignment(VerticalAlignment.TOP);
        styles.put("option", option);

        CellStyle exact = workbook.createCellStyle();
        exact.cloneStyleFrom(body);
        styles.put("itemExact", exact);

        CellStyle partial = workbook.createCellStyle();
        partial.cloneStyleFrom(body);
        partial.setFillForegroundColor(IndexedColors.LIGHT_ORANGE.getIndex());
        partial.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        styles.put("itemPartial", partial);

        CellStyle review = workbook.createCellStyle();
        review.cloneStyleFrom(body);
        review.setFillForegroundColor(IndexedColors.ROSE.getIndex());
        review.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        styles.put("itemReview", review);
        return styles;
    }

    private void border(CellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
    }

    private void cell(Row row, int col, String value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value == null ? "" : value);
        if (style != null) {
            cell.setCellStyle(style);
        }
    }

    private void formula(Row row, int col, String formula, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellFormula(formula);
        if (style != null) {
            cell.setCellStyle(style);
        }
    }

    private String excelCellRef(Row row, int col) {
        return CellReference.convertNumToColString(col) + (row.getRowNum() + 1);
    }

    private void numeric(Row row, int col, BigDecimal value, CellStyle style) {
        Cell cell = row.createCell(col);
        if (value != null) {
            cell.setCellValue(value.setScale(0, RoundingMode.HALF_UP).doubleValue());
        }
        if (style != null) {
            cell.setCellStyle(style);
        }
    }

    private Comparator<Order> buildTaskOrderComparator(String sortField, String sortDir) {
        boolean desc = "desc".equalsIgnoreCase(sortDir);
        Comparator<Order> comparator = switch (sortField == null ? "" : sortField) {
            case "agencyName", "companyName" -> Comparator
                    .comparing((Order o) -> safe(() -> o.getTask().getRequestedBy().getCompany().getCompanyName()), Comparator.nullsLast(String::compareTo))
                    .thenComparing(o -> resolveTaskCreatedAt(o.getTask()), Comparator.nullsLast(LocalDateTime::compareTo))
                    .thenComparing(this::resolveTaskId, Comparator.nullsLast(Long::compareTo))
                    .thenComparing(Order::getId, Comparator.nullsLast(Long::compareTo));
            case "preferredDeliveryDate" -> Comparator
                    .comparing(Order::getPreferredDeliveryDate, Comparator.nullsLast(LocalDateTime::compareTo))
                    .thenComparing(this::resolveTaskId, Comparator.nullsLast(Long::compareTo))
                    .thenComparing(Order::getId, Comparator.nullsLast(Long::compareTo));
            default -> Comparator
                    .comparing((Order o) -> safe(() -> o.getTask().getRequestedBy().getCompany().getCompanyName()), Comparator.nullsLast(String::compareTo))
                    .thenComparing(o -> resolveTaskCreatedAt(o.getTask()), Comparator.nullsLast(LocalDateTime::compareTo))
                    .thenComparing(this::resolveTaskId, Comparator.nullsLast(Long::compareTo))
                    .thenComparing(Order::getId, Comparator.nullsLast(Long::compareTo));
        };
        return desc && ("preferredDeliveryDate".equals(sortField)) ? comparator.reversed() : comparator;
    }

    private Long resolveTaskId(Order order) {
        if (order != null && order.getTask() != null && order.getTask().getId() != null) {
            return order.getTask().getId();
        }
        return order != null && order.getId() != null ? -order.getId() : Long.MIN_VALUE;
    }

    private LocalDateTime resolveTaskCreatedAt(Task task) {
        return task != null ? task.getCreatedAt() : null;
    }

    private LocalDate resolveTaskTransactionDate(Task task, List<Order> taskOrders) {
        if (task != null && task.getCreatedAt() != null) {
            return task.getCreatedAt().toLocalDate();
        }
        return taskOrders.stream()
                .map(Order::getCreatedAt)
                .filter(value -> value != null)
                .min(LocalDateTime::compareTo)
                .map(LocalDateTime::toLocalDate)
                .orElse(LocalDate.now());
    }

    private Company resolveCompany(Order order) {
        try {
            if (order != null && order.getTask() != null && order.getTask().getRequestedBy() != null) {
                return order.getTask().getRequestedBy().getCompany();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private int resolveQuantity(Order order) {
        if (order.getQuantity() > 0) {
            return order.getQuantity();
        }
        OrderItem item = order.getOrderItem();
        if (item != null && item.getQuantity() > 0) {
            return item.getQuantity();
        }
        return 1;
    }

    private String fallbackProductName(AmountParsedOrderProduct parsed, Order order) {
        if (StringUtils.hasText(parsed.productName())) {
            return parsed.productName();
        }
        return safe(() -> order.getOrderItem().getProductName());
    }

    private String buildOrderOptionText(Order order) {
        String optionJson = safe(() -> order.getOrderItem().getOptionJson());
        if (!StringUtils.hasText(optionJson)) {
            return "";
        }

        try {
            Object parsed = objectMapper.readValue(optionJson, Object.class);
            String optionText = formatOnlyOptionLines(flattenOptionValue(parsed, ""));
            return StringUtils.hasText(optionText) ? optionText : "";
        } catch (Exception e) {
            String optionText = formatOnlyOptionLines(splitFormattedOptionText(optionJson));
            return StringUtils.hasText(optionText) ? optionText : "";
        }
    }

    private String formatOnlyOptionLines(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return "";
        }

        return lines.stream()
                .map(this::safe)
                .filter(StringUtils::hasText)
                .filter(this::isOptionKeyLine)
                .collect(Collectors.joining(" / "));
    }

    private List<String> splitFormattedOptionText(String text) {
        if (!StringUtils.hasText(text)) {
            return List.of();
        }

        return java.util.Arrays.stream(text.split("\\s*/\\s*"))
                .map(this::safe)
                .filter(StringUtils::hasText)
                .toList();
    }

    private boolean isOptionKeyLine(String line) {
        if (!StringUtils.hasText(line)) {
            return false;
        }

        int colonIndex = line.indexOf(":");
        if (colonIndex < 0) {
            colonIndex = line.indexOf("：");
        }
        if (colonIndex < 0) {
            return false;
        }

        String key = line.substring(0, colonIndex).trim();

        // 중첩 JSON일 경우 예: product.옵션2
        if (key.contains(".")) {
            key = key.substring(key.lastIndexOf(".") + 1).trim();
        }

        // 배열 JSON일 경우 예: 옵션[1]
        int bracketIndex = key.indexOf("[");
        if (bracketIndex >= 0) {
            key = key.substring(0, bracketIndex).trim();
        }

        return "옵션".equals(key) || key.matches("옵션\\d+");
    }

    private List<String> flattenOptionValue(Object value, String prefix) {
        List<String> lines = new ArrayList<>();
        if (value == null) {
            return lines;
        }

        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = entry.getKey() == null ? "" : String.valueOf(entry.getKey()).trim();
                if (!StringUtils.hasText(key)) {
                    continue;
                }
                String nextPrefix = StringUtils.hasText(prefix) ? prefix + "." + key : key;
                Object child = entry.getValue();
                if (isSimpleOptionValue(child)) {
                    String childValue = simpleOptionValue(child);
                    if (StringUtils.hasText(childValue)) {
                        lines.add(nextPrefix + ": " + childValue);
                    }
                } else {
                    lines.addAll(flattenOptionValue(child, nextPrefix));
                }
            }
            return lines;
        }

        if (value instanceof List<?> list) {
            for (int i = 0; i < list.size(); i++) {
                String nextPrefix = StringUtils.hasText(prefix) ? prefix + "[" + (i + 1) + "]" : "[" + (i + 1) + "]";
                Object child = list.get(i);
                if (isSimpleOptionValue(child)) {
                    String childValue = simpleOptionValue(child);
                    if (StringUtils.hasText(childValue)) {
                        lines.add(nextPrefix + ": " + childValue);
                    }
                } else {
                    lines.addAll(flattenOptionValue(child, nextPrefix));
                }
            }
            return lines;
        }

        String simpleValue = simpleOptionValue(value);
        if (StringUtils.hasText(simpleValue)) {
            lines.add(StringUtils.hasText(prefix) ? prefix + ": " + simpleValue : simpleValue);
        }
        return lines;
    }

    private boolean isSimpleOptionValue(Object value) {
        return value == null
                || value instanceof String
                || value instanceof Number
                || value instanceof Boolean;
    }

    private String simpleOptionValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String buildOrderMemo(Order order, AmountItemMatchResult itemMatch, AmountCustomerMatchResult customerMatch) {
        List<String> parts = new ArrayList<>();
        if (StringUtils.hasText(order.getOrderComment())) {
            parts.add(order.getOrderComment().trim());
        }
        if (StringUtils.hasText(order.getAdminMemo())) {
            parts.add(order.getAdminMemo().trim());
        }
        if (!"EXACT".equals(itemMatch.level())) {
            parts.add("품목확인:" + itemMatch.reason());
        }
        if (!"EXACT".equals(customerMatch.level())) {
            parts.add("거래처확인:" + customerMatch.reason() + ", score=" + customerMatch.score());
        }
        String memo = String.join(" / ", parts);
        return memo.length() > 500 ? memo.substring(0, 500) : memo;
    }

    private String level(int score) {
        if (score >= 97) {
            return "EXACT";
        }
        if (score >= 51) {
            return "PARTIAL";
        }
        return "REVIEW";
    }

    private String normalizeDateCriteria(String dateCriteria) {
        if (!StringUtils.hasText(dateCriteria)) {
            return "all";
        }
        String v = dateCriteria.trim().toLowerCase(Locale.ROOT);
        return Set.of("all", "order", "delivery").contains(v) ? v : "all";
    }

    private DateRange buildDateRangeForCriteria(String dateCriteria, String startDateStr, String endDateStr) {
        if ("all".equals(normalizeDateCriteria(dateCriteria))) {
            return new DateRange(null, null);
        }
        LocalDate startDate = parseYmdOrNull(startDateStr);
        LocalDate endDate = parseYmdOrNull(endDateStr);
        LocalDateTime start = startDate == null ? null : startDate.atStartOfDay();
        LocalDateTime end = endDate == null ? null : endDate.atTime(LocalTime.MAX);
        return new DateRange(start, end);
    }

    private LocalDate parseYmdOrNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim(), YMD);
        } catch (Exception e) {
            return null;
        }
    }

    private Long parseLongOrNullAllowAll(String value) {
        if (!StringUtils.hasText(value) || "all".equalsIgnoreCase(value.trim())) {
            return null;
        }
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private OrderStatus parseOrderStatusOrNullWithDefault(String value, OrderStatus defaultValue) {
        if (!StringUtils.hasText(value) || "all".equalsIgnoreCase(value.trim())) {
            return null;
        }
        try {
            return OrderStatus.valueOf(value.trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private Boolean parseStandardOrNull(String value) {
        if (!StringUtils.hasText(value) || "all".equalsIgnoreCase(value.trim())) {
            return null;
        }
        if ("true".equalsIgnoreCase(value.trim())) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(value.trim())) {
            return Boolean.FALSE;
        }
        return null;
    }

    private BigDecimal parseMoney(String value) {
        if (!StringUtils.hasText(value)) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(value.replace(",", "").trim());
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private BigDecimal money(int value) {
        return BigDecimal.valueOf(Math.max(value, 0));
    }

    private boolean same(String a, String b) {
        return AmountTextNormalizer.compact(a).equals(AmountTextNormalizer.compact(b));
    }

    private String digitsOnly(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.replaceAll("[^0-9]", "");
    }

    private <T> String value(T target, Function<T, String> getter) {
        if (target == null) {
            return "";
        }
        try {
            String value = getter.apply(target);
            return value == null ? "" : value.trim();
        } catch (Exception e) {
            return "";
        }
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String safe(SafeSupplier supplier) {
        try {
            String value = supplier.get();
            return value == null ? "" : value.trim();
        } catch (Exception e) {
            return "";
        }
    }

    @FunctionalInterface
    private interface SafeSupplier {
        String get();
    }

    private record DateRange(LocalDateTime start, LocalDateTime end) {
    }

    private record TaskVoucherBlock(Long taskId,
                                    LocalDate transactionDate,
                                    String companyName,
                                    AmountCustomerMatchResult customerMatch,
                                    List<VoucherLine> lines,
                                    BigDecimal taskTotal) {
    }

    private record VoucherLine(Long taskId,
                               Long orderId,
                               boolean freightLine,
                               boolean addressLine,
                               LocalDate transactionDate,
                               String companyName,
                               AmountCustomerMatchResult customerMatch,
                               AmountItemMatchResult itemMatch,
                               String itemCode,
                               String itemName,
                               String specification,
                               String unit,
                               int quantity,
                               BigDecimal unitPrice,
                               BigDecimal supply,
                               BigDecimal vat,
                               BigDecimal total,
                               String memo,
                               String optionText) {
    }

    private record Money(BigDecimal unitPrice, BigDecimal supply, BigDecimal vat, BigDecimal total) {
    }

    private record ScoredItem(AmountItemMaster item, int score) {
    }
}