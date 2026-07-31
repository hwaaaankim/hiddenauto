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
     * 거래처 A - Task 1 오더들 - Task 1 운임비/포장비 각 1회 - Task 2 오더들 - 비용 각 1회 ...
     *
     * deliveryCost/packingCost는 현재 Order에 있지만 실제 업무 의미는 Task 단위 비용이므로,
     * Task 안의 여러 Order에 중복 저장되어 있어도 중복 합산하지 않고 비용별 양수 최대값 1회만 사용합니다.
     * 비용이 0원 이하이면 해당 비용 품목행과 주소 보조행을 출력하지 않습니다.
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

            List<VoucherLine> chargeLines = buildTaskChargeLines(
                    entry.getKey(), transactionDate, companyName, customerMatch, taskOrders, items);
            if (!chargeLines.isEmpty()) {
                lines.addAll(chargeLines);

                VoucherLine addressLine = buildTaskAddressLine(
                        entry.getKey(), transactionDate, companyName, customerMatch, taskOrders);
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
        String sourceItemName = resolveOrderItemName(order);
        AmountParsedOrderProduct parsed = optionParser.parse(order);
        AmountItemMatchResult itemMatch = matchItem(
                sourceItemName,
                parsed,
                items,
                resolveOrderStandard(order),
                order.isMirrorCuttingProduct()
        );
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

    /**
     * 운임비와 포장비는 품목 마스터의 서로 다른 품목으로 각각 출력합니다.
     *
     * 기존 업무 규칙대로 같은 Task 안에 동일 비용이 여러 Order에 중복 저장될 수 있으므로
     * 각 비용별 양수 최대값을 실제 Order 값으로 1회만 사용합니다.
     * 0원 이하인 비용은 품목행 자체를 만들지 않습니다.
     */
    private List<VoucherLine> buildTaskChargeLines(Long taskId,
                                                   LocalDate transactionDate,
                                                   String companyName,
                                                   AmountCustomerMatchResult customerMatch,
                                                   List<Order> taskOrders,
                                                   List<AmountItemMaster> items) {
        List<VoucherLine> result = new ArrayList<>();

        for (ChargeType chargeType : ChargeType.values()) {
            TaskChargeAmount chargeAmount = resolveTaskChargeAmount(taskOrders, chargeType);
            if (chargeAmount.amount() <= 0) {
                continue;
            }

            VoucherLine chargeLine = buildTaskChargeLine(
                    taskId,
                    transactionDate,
                    companyName,
                    customerMatch,
                    chargeType,
                    chargeAmount,
                    items
            );
            if (chargeLine != null) {
                result.add(chargeLine);
            }
        }
        return result;
    }

    private VoucherLine buildTaskChargeLine(Long taskId,
                                            LocalDate transactionDate,
                                            String companyName,
                                            AmountCustomerMatchResult customerMatch,
                                            ChargeType chargeType,
                                            TaskChargeAmount chargeAmount,
                                            List<AmountItemMaster> items) {
        if (chargeAmount == null || chargeAmount.amount() <= 0) {
            return null;
        }

        AmountItemMatchResult chargeMatch = matchChargeItem(chargeType, items);
        AmountItemMaster matchedItem = chargeMatch.item();

        BigDecimal supply = BigDecimal.valueOf(chargeAmount.amount());
        BigDecimal vat = supply.multiply(BigDecimal.valueOf(0.1)).setScale(0, RoundingMode.HALF_UP);
        BigDecimal total = supply.add(vat);

        String sourceOrderText = chargeAmount.sourceOrderId() == null
                ? ""
                : " / 기준 Order=" + chargeAmount.sourceOrderId();
        String memo = "Task " + taskId + " " + chargeType.itemName() + " 1회 처리"
                + sourceOrderText
                + " / 실제 Order 금액=" + chargeAmount.amount()
                + " / Task 내 중복 저장값은 양수 최대값 1회만 반영";

        return new VoucherLine(
                taskId,
                chargeAmount.sourceOrderId(),
                true,
                false,
                transactionDate,
                companyName,
                customerMatch,
                chargeMatch,
                matchedItem != null ? safe(matchedItem.getItemCode()) : "",
                matchedItem != null ? safe(matchedItem.getItemName()) : chargeType.itemName(),
                matchedItem != null ? safe(matchedItem.getSpecification()) : "",
                matchedItem != null && StringUtils.hasText(matchedItem.getUnit())
                        ? matchedItem.getUnit()
                        : "기타",
                1,
                supply,
                supply,
                vat,
                total,
                memo,
                ""
        );
    }

    private TaskChargeAmount resolveTaskChargeAmount(List<Order> taskOrders, ChargeType chargeType) {
        if (taskOrders == null || taskOrders.isEmpty() || chargeType == null) {
            return new TaskChargeAmount(0, null);
        }

        int maxAmount = 0;
        Long sourceOrderId = null;
        for (Order order : taskOrders) {
            if (order == null) {
                continue;
            }
            int amount = Math.max(0, chargeType.amount(order));
            if (amount > maxAmount) {
                maxAmount = amount;
                sourceOrderId = order.getId();
            }
        }
        return new TaskChargeAmount(maxAmount, sourceOrderId);
    }

    private AmountItemMatchResult matchChargeItem(ChargeType chargeType, List<AmountItemMaster> items) {
        if (chargeType == null) {
            return AmountItemMatchResult.empty("비용 품목 유형이 없습니다.");
        }
        if (items == null || items.isEmpty()) {
            return AmountItemMatchResult.empty(
                    "품목 마스터가 비어 있어 " + chargeType.itemName() + " 코드를 찾지 못했습니다.");
        }

        Optional<AmountItemMaster> exact = items.stream()
                .filter(item -> item != null && same(chargeType.itemName(), item.getItemName()))
                .sorted(Comparator.comparing(item -> safe(item.getItemCode())))
                .findFirst();
        if (exact.isPresent()) {
            return new AmountItemMatchResult(
                    exact.get(),
                    100,
                    "EXACT",
                    chargeType.itemName() + " 품목명 100% 일치",
                    false
            );
        }

        List<AmountItemMaster> relatedCandidates = items.stream()
                .filter(item -> item != null && chargeType.isRelatedItemName(item.getItemName()))
                .toList();
        if (relatedCandidates.isEmpty()) {
            return AmountItemMatchResult.empty(
                    "품목 마스터에서 " + chargeType.itemName() + " 관련 항목을 찾지 못했습니다.");
        }

        AmountItemMaster bestItem = null;
        int bestScore = -1;
        for (AmountItemMaster item : relatedCandidates) {
            int score = chargeType.matchScore(item.getItemName());
            if (score > bestScore) {
                bestScore = score;
                bestItem = item;
            } else if (score == bestScore && bestItem != null
                    && safe(item.getItemCode()).compareTo(safe(bestItem.getItemCode())) < 0) {
                bestItem = item;
            }
        }

        if (bestItem == null) {
            return AmountItemMatchResult.empty(
                    "품목 마스터에서 " + chargeType.itemName() + " 관련 항목을 찾지 못했습니다.");
        }

        int finalScore = Math.max(0, Math.min(100, bestScore));
        return new AmountItemMatchResult(
                bestItem,
                finalScore,
                level(finalScore),
                chargeType.itemName() + " 유사 품목명 매칭: " + safe(bestItem.getItemName())
                        + ", score=" + finalScore,
                false
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
        AmountItemMatchResult addressMatch = new AmountItemMatchResult(null, 100, "EXACT", "비용 품목 하단 주소 보조행", false);
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

    /**
     * 품목 매칭 진입점입니다.
     *
     * 1) OrderItem.itemName이 실제로 저장되어 있으면 원문 품목명 모드로 매칭합니다.
     * 2) 값이 없으면 원문 품목명 관련 점수/완전일치 로직을 전혀 사용하지 않고
     *    기존 옵션(제품명, 시리즈, 색상, 사이즈, 도어 수 등) 기반 매칭으로만 처리합니다.
     */
    private AmountItemMatchResult matchItem(String sourceItemName,
                                            AmountParsedOrderProduct parsed,
                                            List<AmountItemMaster> items,
                                            Boolean orderStandard,
                                            boolean orderMirrorCuttingProduct) {
        if (items == null || items.isEmpty()) {
            return AmountItemMatchResult.empty("품목 마스터가 비어 있습니다.");
        }

        ItemCandidateContext candidateContext = resolveItemCandidateContext(items, orderStandard);
        String normalizedSourceItemName = safe(sourceItemName);

        if (StringUtils.hasText(normalizedSourceItemName)) {
            return matchItemByStoredItemName(
                    normalizedSourceItemName,
                    parsed,
                    candidateContext,
                    orderStandard,
                    orderMirrorCuttingProduct
            );
        }

        return matchItemByLegacyOptions(
                parsed,
                candidateContext,
                orderStandard,
                orderMirrorCuttingProduct
        );
    }

    private ItemCandidateContext resolveItemCandidateContext(List<AmountItemMaster> items,
                                                             Boolean orderStandard) {
        List<AmountItemMaster> candidates = items;
        String standardReasonPrefix = "";

        if (orderStandard != null) {
            List<AmountItemMaster> filtered = items.stream()
                    .filter(item -> item != null && item.isStandard() == orderStandard.booleanValue())
                    .toList();
            if (!filtered.isEmpty()) {
                candidates = filtered;
                standardReasonPrefix = "규격구분 선필터[주문=" + standardLabel(orderStandard) + "] 적용 / ";
            } else {
                // 동기화 데이터가 아직 부족한 경우 전표 생성 자체가 완전히 비지 않도록 전체 후보에서 대체 매칭하되,
                // 비고에 반드시 남겨서 사용자가 보정할 수 있게 합니다.
                standardReasonPrefix = "규격구분 선필터 후보 없음[주문=" + standardLabel(orderStandard)
                        + "] → 전체 후보에서 대체 매칭 / ";
            }
        }

        return new ItemCandidateContext(candidates, standardReasonPrefix);
    }

    /**
     * 신규/보정 데이터용 원문 품목명 매칭 모드입니다.
     * OrderItem.itemName이 있을 때만 진입합니다.
     */
    private AmountItemMatchResult matchItemByStoredItemName(String sourceItemName,
                                                            AmountParsedOrderProduct parsed,
                                                            ItemCandidateContext candidateContext,
                                                            Boolean orderStandard,
                                                            boolean orderMirrorCuttingProduct) {
        List<AmountItemMaster> candidates = candidateContext.candidates();
        String standardReasonPrefix = candidateContext.standardReasonPrefix();

        List<AmountItemMaster> exactNameMatches = candidates.stream()
                .filter(item -> item != null && same(sourceItemName, item.getItemName()))
                .toList();
        if (!exactNameMatches.isEmpty()) {
            AmountItemMaster exactItem = chooseExactNameMatch(
                    exactNameMatches, parsed, orderMirrorCuttingProduct);
            int exactScore = 100;
            String reason = standardReasonPrefix
                    + "원문 품목명 매칭 모드 / "
                    + "OrderItem.itemName ↔ AmountItemMaster.itemName 정규화 100% 일치"
                    + " / 원문품목명=" + sourceItemName;

            if (exactNameMatches.size() > 1) {
                reason += " / 동일 품목명 후보 " + exactNameMatches.size()
                        + "건 중 거울재단 여부·기존 옵션점수로 선택";
            }
            if (exactItem != null
                    && exactItem.isMirrorCuttingProduct() != orderMirrorCuttingProduct) {
                reason += " / 거울재단구분 불일치: 주문="
                        + mirrorCuttingLabel(orderMirrorCuttingProduct)
                        + ", 품목=" + mirrorCuttingLabel(exactItem.isMirrorCuttingProduct());
            }
            if (orderStandard != null && exactItem != null
                    && exactItem.isStandard() != orderStandard.booleanValue()) {
                exactScore = 50;
                reason += " / 규격구분 불일치: 주문=" + standardLabel(orderStandard)
                        + ", 품목=" + standardLabel(exactItem.isStandard());
            }

            return new AmountItemMatchResult(
                    exactItem,
                    exactScore,
                    level(exactScore),
                    reason,
                    false
            );
        }

        List<ScoredItem> scored = candidates.stream()
                .filter(item -> item != null)
                .map(item -> scoreStoredItemNameCandidate(sourceItemName, parsed, item))
                .sorted(Comparator.comparingInt(ScoredItem::score).reversed()
                        .thenComparing(Comparator.comparingInt(ScoredItem::nameScore).reversed())
                        .thenComparing(scoredItem -> safe(scoredItem.item().getItemCode())))
                .limit(25)
                .toList();
        ScoredItem best = scored.isEmpty() ? null : scored.get(0);
        if (best == null) {
            return AmountItemMatchResult.empty("품목 후보 없음");
        }

        boolean aiUsed = false;
        String reason = standardReasonPrefix + buildStoredItemNameReason(
                parsed,
                sourceItemName,
                best.item(),
                best.score(),
                best.nameScore(),
                best.optionScore()
        );
        int finalScore = best.score();
        AmountItemMaster finalItem = best.item();

        boolean ambiguous = scored.size() > 1
                && (Math.abs(scored.get(0).score() - scored.get(1).score()) <= 5
                || Math.abs(scored.get(0).nameScore() - scored.get(1).nameScore()) <= 3);
        if (finalScore < 97 || ambiguous) {
            AmountParsedOrderProduct aiParsed = withProductName(parsed, sourceItemName);
            Optional<OpenAiAmountProductMatchClient.AiProductChoice> aiChoice = aiProductMatchClient.chooseBest(
                    aiParsed,
                    scored.stream().map(ScoredItem::item).toList()
            );
            if (aiChoice.isPresent()) {
                OpenAiAmountProductMatchClient.AiProductChoice choice = aiChoice.get();
                Optional<ScoredItem> selected = scored.stream()
                        .filter(scoredItem -> same(choice.itemCode(), scoredItem.item().getItemCode())
                                || same(choice.itemName(), scoredItem.item().getItemName()))
                        .findFirst();
                if (selected.isPresent()) {
                    ScoredItem selectedItem = selected.get();
                    finalItem = selectedItem.item();
                    finalScore = Math.max(selectedItem.score(), choice.confidence());
                    reason = standardReasonPrefix
                            + "원문 품목명 매칭 모드 / AI선택: " + choice.reason()
                            + " / 원문품목명=" + sourceItemName
                            + " / 선택품목명=" + safe(finalItem.getItemName())
                            + " / 이름점수=" + selectedItem.nameScore()
                            + " / 옵션점수=" + selectedItem.optionScore();
                    aiUsed = true;
                }
            }
        }

        return finishItemMatch(
                finalItem,
                finalScore,
                reason,
                aiUsed,
                orderStandard,
                orderMirrorCuttingProduct
        );
    }

    /**
     * 기존 주문 데이터용 옵션 매칭 모드입니다.
     *
     * OrderItem.itemName이 없을 때만 진입하며, 원문 품목명 완전일치 및
     * 품목명 90% 가중치를 사용하지 않습니다. 기존 scoreItem 계산과
     * 기존 AI 호출 조건(97점 미만 또는 상위 후보 점수차 5점 이하)을 그대로 사용합니다.
     */
    private AmountItemMatchResult matchItemByLegacyOptions(AmountParsedOrderProduct parsed,
                                                            ItemCandidateContext candidateContext,
                                                            Boolean orderStandard,
                                                            boolean orderMirrorCuttingProduct) {
        List<ScoredItem> scored = candidateContext.candidates().stream()
                .filter(item -> item != null)
                .map(item -> {
                    int optionScore = scoreItem(parsed, item);
                    return new ScoredItem(item, optionScore, 0, optionScore);
                })
                .sorted(Comparator.comparingInt(ScoredItem::score).reversed()
                        .thenComparing(scoredItem -> safe(scoredItem.item().getItemCode())))
                .limit(25)
                .toList();
        ScoredItem best = scored.isEmpty() ? null : scored.get(0);
        if (best == null) {
            return AmountItemMatchResult.empty("품목 후보 없음");
        }

        String standardReasonPrefix = candidateContext.standardReasonPrefix();
        boolean aiUsed = false;
        String reason = standardReasonPrefix + buildLegacyItemReason(parsed, best.item(), best.score());
        int finalScore = best.score();
        AmountItemMaster finalItem = best.item();

        boolean ambiguous = scored.size() > 1
                && Math.abs(scored.get(0).score() - scored.get(1).score()) <= 5;
        if (finalScore < 97 || ambiguous) {
            Optional<OpenAiAmountProductMatchClient.AiProductChoice> aiChoice = aiProductMatchClient.chooseBest(
                    parsed,
                    scored.stream().map(ScoredItem::item).toList()
            );
            if (aiChoice.isPresent()) {
                OpenAiAmountProductMatchClient.AiProductChoice choice = aiChoice.get();
                Optional<ScoredItem> selected = scored.stream()
                        .filter(scoredItem -> same(choice.itemCode(), scoredItem.item().getItemCode())
                                || same(choice.itemName(), scoredItem.item().getItemName()))
                        .findFirst();
                if (selected.isPresent()) {
                    ScoredItem selectedItem = selected.get();
                    finalItem = selectedItem.item();
                    finalScore = Math.max(selectedItem.score(), choice.confidence());
                    reason = standardReasonPrefix
                            + "기존 옵션 매칭 모드[OrderItem.itemName 없음] / AI선택: " + choice.reason()
                            + " / 주문옵션=" + (parsed == null ? "" : parsed.displayText())
                            + " / 선택품목명=" + safe(finalItem.getItemName())
                            + " / 옵션점수=" + selectedItem.optionScore();
                    aiUsed = true;
                }
            }
        }

        return finishItemMatch(
                finalItem,
                finalScore,
                reason,
                aiUsed,
                orderStandard,
                orderMirrorCuttingProduct
        );
    }

    private AmountItemMatchResult finishItemMatch(AmountItemMaster finalItem,
                                                  int finalScore,
                                                  String reason,
                                                  boolean aiUsed,
                                                  Boolean orderStandard,
                                                  boolean orderMirrorCuttingProduct) {
        String finalReason = safe(reason);
        int boundedScore = Math.max(0, Math.min(100, finalScore));

        if (orderStandard != null && finalItem != null
                && finalItem.isStandard() != orderStandard.booleanValue()) {
            boundedScore = Math.min(boundedScore, 50);
            finalReason = finalReason + " / 규격구분 불일치: 주문=" + standardLabel(orderStandard)
                    + ", 품목=" + standardLabel(finalItem.isStandard());
        }
        if (finalItem != null
                && finalItem.isMirrorCuttingProduct() != orderMirrorCuttingProduct) {
            finalReason = finalReason + " / 거울재단구분 불일치: 주문="
                    + mirrorCuttingLabel(orderMirrorCuttingProduct)
                    + ", 품목=" + mirrorCuttingLabel(finalItem.isMirrorCuttingProduct());
        }

        return new AmountItemMatchResult(
                finalItem,
                boundedScore,
                level(boundedScore),
                finalReason,
                aiUsed
        );
    }

    private AmountParsedOrderProduct withProductName(AmountParsedOrderProduct parsed,
                                                      String productName) {
        if (parsed == null) {
            return null;
        }
        return new AmountParsedOrderProduct(
                parsed.orderId(),
                parsed.category(),
                parsed.series(),
                safe(productName),
                parsed.color(),
                parsed.sizeText(),
                parsed.width(),
                parsed.height(),
                parsed.depth(),
                parsed.doorCount(),
                parsed.unitHint(),
                parsed.optionMap()
        );
    }

    private AmountItemMaster chooseExactNameMatch(List<AmountItemMaster> exactNameMatches,
                                                   AmountParsedOrderProduct parsed,
                                                   boolean orderMirrorCuttingProduct) {
        if (exactNameMatches == null || exactNameMatches.isEmpty()) {
            return null;
        }

        return exactNameMatches.stream()
                .sorted(Comparator
                        .comparingInt((AmountItemMaster item) ->
                                item.isMirrorCuttingProduct() == orderMirrorCuttingProduct ? 0 : 1)
                        .thenComparing(
                                Comparator.comparingInt((AmountItemMaster item) -> scoreItem(parsed, item)).reversed())
                        .thenComparing(item -> safe(item.getItemCode())))
                .findFirst()
                .orElse(exactNameMatches.get(0));
    }

    private ScoredItem scoreStoredItemNameCandidate(String sourceItemName,
                                                    AmountParsedOrderProduct parsed,
                                                    AmountItemMaster item) {
        int optionScore = scoreItem(parsed, item);
        int nameScore = AmountTextNormalizer.similarity100(sourceItemName, item.getItemName());
        int combinedScore = Math.round(nameScore * 0.90f + optionScore * 0.10f);
        return new ScoredItem(
                item,
                Math.max(0, Math.min(100, combinedScore)),
                Math.max(0, Math.min(100, nameScore)),
                optionScore
        );
    }

    private int scoreItem(AmountParsedOrderProduct parsed, AmountItemMaster item) {
        String itemName = AmountTextNormalizer.compact(item.getItemName());
        String itemSpec = AmountTextNormalizer.compact(item.getSpecification());
        String unit = AmountTextNormalizer.compact(item.getUnit());
        String categoryName = AmountTextNormalizer.compact(item.getCategoryName());
        String middleCategoryName = AmountTextNormalizer.compact(item.getMiddleCategoryName());
        String all = itemName + itemSpec + unit + categoryName + middleCategoryName;

        int score = 0;
        String product = AmountTextNormalizer.compact(parsed.productName());
        String series = AmountTextNormalizer.compact(parsed.series());
        String color = AmountTextNormalizer.compact(parsed.color());
        String category = AmountTextNormalizer.compact(parsed.category());

        if (StringUtils.hasText(product)) {
            score += Math.round(AmountTextNormalizer.similarity100(product, item.getItemName()) * 0.30f);
            if (itemName.contains(product) || product.contains(itemName)) {
                score += 10;
            }
        }
        if (StringUtils.hasText(category)) {
            int categoryScore = Math.max(
                    AmountTextNormalizer.similarity100(category, item.getCategoryName()),
                    AmountTextNormalizer.similarity100(category, item.getUnit())
            );
            score += Math.round(categoryScore * 0.12f);
            if (categoryName.contains(category) || all.contains(category)) {
                score += 8;
            }
        }
        if (StringUtils.hasText(series)) {
            int seriesScore = Math.max(
                    AmountTextNormalizer.similarity100(series, item.getMiddleCategoryName()),
                    AmountTextNormalizer.similarity100(series, item.getItemName())
            );
            score += Math.round(seriesScore * 0.14f);
            if (middleCategoryName.contains(series) || all.contains(series)) {
                score += 12;
            }
        }
        if (StringUtils.hasText(color) && all.contains(color)) {
            score += 16;
        }
        if (parsed.width() != null) {
            String width = String.valueOf(parsed.width());
            if (all.contains(width)) {
                score += 18;
            }
        }
        if (parsed.height() != null && itemSpec.contains(String.valueOf(parsed.height()))) {
            score += 8;
        }
        if (parsed.depth() != null && itemSpec.contains(String.valueOf(parsed.depth()))) {
            score += 5;
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

    private String buildStoredItemNameReason(AmountParsedOrderProduct parsed,
                                             String sourceItemName,
                                             AmountItemMaster item,
                                             int score,
                                             int nameScore,
                                             int optionScore) {
        return "원문 품목명 매칭 모드 / 원문품목명[" + safe(sourceItemName) + "] ↔ 품목["
                + value(item, AmountItemMaster::getItemName)
                + " / 대분류=" + value(item, AmountItemMaster::getCategoryName)
                + " / 중분류=" + value(item, AmountItemMaster::getMiddleCategoryName)
                + " / 규격구분=" + (item != null ? standardLabel(item.isStandard()) : "")
                + " / 사이즈=" + value(item, AmountItemMaster::getSpecification)
                + " / 단위=" + value(item, AmountItemMaster::getUnit)
                + "], 최종점수=" + score
                + ", 이름점수=" + nameScore
                + ", 옵션점수=" + optionScore
                + ", 주문옵션=" + (parsed == null ? "" : parsed.displayText());
    }

    private String buildLegacyItemReason(AmountParsedOrderProduct parsed,
                                         AmountItemMaster item,
                                         int score) {
        return "기존 옵션 매칭 모드[OrderItem.itemName 없음] / 주문["
                + (parsed == null ? "" : parsed.displayText())
                + "] ↔ 품목["
                + value(item, AmountItemMaster::getItemName)
                + " / 대분류=" + value(item, AmountItemMaster::getCategoryName)
                + " / 중분류=" + value(item, AmountItemMaster::getMiddleCategoryName)
                + " / 규격구분=" + (item != null ? standardLabel(item.isStandard()) : "")
                + " / 사이즈=" + value(item, AmountItemMaster::getSpecification)
                + " / 단위=" + value(item, AmountItemMaster::getUnit)
                + "], 옵션점수=" + score;
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

        String optionText = line.addressLine() || line.chargeLine() ? "" : safe(line.optionText());
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
        cell(ih, 2, "대분류", styles.get("header"));
        cell(ih, 3, "중분류", styles.get("header"));
        cell(ih, 4, "규격구분", styles.get("header"));
        cell(ih, 5, "거울재단", styles.get("header"));
        cell(ih, 6, "사이즈", styles.get("header"));
        rowIndex = 1;
        for (AmountItemMaster item : items) {
            Row row = itemSheet.createRow(rowIndex++);
            row.setHeightInPoints(18);
            cell(row, 0, item.getItemName(), styles.get("body"));
            cell(row, 1, item.getItemCode(), styles.get("body"));
            cell(row, 2, item.getCategoryName(), styles.get("body"));
            cell(row, 3, item.getMiddleCategoryName(), styles.get("body"));
            cell(row, 4, standardLabel(item.isStandard()), styles.get("body"));
            cell(row, 5, item.isMirrorCuttingProduct() ? "재단필요" : "", styles.get("body"));
            cell(row, 6, item.getSpecification(), styles.get("body"));
        }
        itemSheet.setColumnWidth(0, 12000);
        itemSheet.setColumnWidth(1, 4000);
        itemSheet.setColumnWidth(2, 6000);
        itemSheet.setColumnWidth(3, 6000);
        itemSheet.setColumnWidth(4, 4000);
        itemSheet.setColumnWidth(5, 4000);
        itemSheet.setColumnWidth(6, 6500);

        Row row = infoSheet.createRow(0);
        row.setHeightInPoints(18);
        cell(row, 0, "검정/무채색: 97점 이상 정확 매칭, 주황: 51~96점 부분 매칭, 빨강: 50점 이하 재검토 필요", styles.get("body"));
        Row row2 = infoSheet.createRow(1);
        row2.setHeightInPoints(18);
        cell(row2, 0, "비용 품목: 운임비와 포장비를 품목 마스터에서 각각 매칭해 Task 마지막에 별도 행으로 출력합니다. 각 실제 Order 금액이 0원보다 클 때만 출력하며, 같은 Task에 중복 저장된 값은 비용별 양수 최대값 1회만 사용합니다. 비용행이 있을 때만 바로 다음 주소 보조행을 출력합니다.", styles.get("body"));
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


    private String resolveOrderItemName(Order order) {
        return safe(() -> order.getOrderItem().getItemName());
    }

    private Boolean resolveOrderStandard(Order order) {
        if (order == null) {
            return null;
        }
        return order.isStandard();
    }

    private String standardLabel(Boolean standard) {
        if (standard == null) {
            return "";
        }
        return standard ? "규격" : "비규격";
    }

    private String mirrorCuttingLabel(boolean mirrorCuttingProduct) {
        return mirrorCuttingProduct ? "재단필요" : "재단없음";
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
        String originalItemName = safe(() -> order.getOrderItem().getItemName());
        if (StringUtils.hasText(originalItemName)) {
            return originalItemName;
        }
        if (parsed != null && StringUtils.hasText(parsed.productName())) {
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
        Boolean orderStandard = resolveOrderStandard(order);
        if (orderStandard != null && itemMatch.item() != null && itemMatch.item().isStandard() != orderStandard.booleanValue()) {
            parts.add("규격구분확인: 주문=" + standardLabel(orderStandard) + ", 품목=" + standardLabel(itemMatch.item().isStandard()));
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
                               boolean chargeLine,
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

    private enum ChargeType {
        DELIVERY("운임비", List.of("운임비", "배송비", "화물비", "운송비")) {
            @Override
            int amount(Order order) {
                return order == null ? 0 : order.getDeliveryCost();
            }
        },
        PACKING("포장비", List.of("포장비", "포장료", "포장비용")) {
            @Override
            int amount(Order order) {
                return order == null ? 0 : order.getPackingCost();
            }
        };

        private final String itemName;
        private final List<String> aliases;

        ChargeType(String itemName, List<String> aliases) {
            this.itemName = itemName;
            this.aliases = aliases;
        }

        String itemName() {
            return itemName;
        }

        abstract int amount(Order order);

        boolean isRelatedItemName(String candidateName) {
            String compactCandidate = AmountTextNormalizer.compact(candidateName);
            if (!StringUtils.hasText(compactCandidate)) {
                return false;
            }
            return aliases.stream()
                    .map(AmountTextNormalizer::compact)
                    .anyMatch(alias -> StringUtils.hasText(alias)
                            && (compactCandidate.contains(alias) || alias.contains(compactCandidate)));
        }

        int matchScore(String candidateName) {
            if (sameCompact(itemName, candidateName)) {
                return 100;
            }
            boolean exactAlias = aliases.stream().anyMatch(alias -> sameCompact(alias, candidateName));
            if (exactAlias) {
                return 95;
            }
            int score = aliases.stream()
                    .mapToInt(alias -> AmountTextNormalizer.similarity100(alias, candidateName))
                    .max()
                    .orElse(0);
            return Math.min(score, 94);
        }

        private static boolean sameCompact(String left, String right) {
            return AmountTextNormalizer.compact(left).equals(AmountTextNormalizer.compact(right));
        }
    }

    private record TaskChargeAmount(int amount, Long sourceOrderId) {
    }

    private record Money(BigDecimal unitPrice, BigDecimal supply, BigDecimal vat, BigDecimal total) {
    }

    private record ItemCandidateContext(List<AmountItemMaster> candidates,
                                        String standardReasonPrefix) {
    }

    private record ScoredItem(AmountItemMaster item, int score, int nameScore, int optionScore) {
    }
}
