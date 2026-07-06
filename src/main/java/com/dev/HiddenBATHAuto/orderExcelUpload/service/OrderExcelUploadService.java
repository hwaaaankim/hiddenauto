package com.dev.HiddenBATHAuto.orderExcelUpload.service;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.dev.HiddenBATHAuto.model.amount.AmountItemMaster;
import com.dev.HiddenBATHAuto.model.auth.Company;
import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.auth.MemberRole;
import com.dev.HiddenBATHAuto.model.auth.TeamCategory;
import com.dev.HiddenBATHAuto.model.caculate.DeliveryMethod;
import com.dev.HiddenBATHAuto.model.task.DeliveryOrderIndex;
import com.dev.HiddenBATHAuto.model.task.Order;
import com.dev.HiddenBATHAuto.model.task.OrderItem;
import com.dev.HiddenBATHAuto.model.task.OrderStatus;
import com.dev.HiddenBATHAuto.model.task.Task;
import com.dev.HiddenBATHAuto.model.task.TaskStatus;
import com.dev.HiddenBATHAuto.orderExcelUpload.dto.OrderExcelIssueDto;
import com.dev.HiddenBATHAuto.orderExcelUpload.dto.OrderExcelPreviewGroupDto;
import com.dev.HiddenBATHAuto.orderExcelUpload.dto.OrderExcelPreviewResponse;
import com.dev.HiddenBATHAuto.orderExcelUpload.dto.OrderExcelPreviewRowDto;
import com.dev.HiddenBATHAuto.orderExcelUpload.dto.OrderExcelSaveGroupRequest;
import com.dev.HiddenBATHAuto.orderExcelUpload.dto.OrderExcelSaveRequest;
import com.dev.HiddenBATHAuto.orderExcelUpload.dto.OrderExcelSaveResponse;
import com.dev.HiddenBATHAuto.orderExcelUpload.dto.OrderExcelSaveRowRequest;
import com.dev.HiddenBATHAuto.orderExcelUpload.repository.OrderExcelAmountItemMasterRepository;
import com.dev.HiddenBATHAuto.orderExcelUpload.repository.OrderExcelCompanyRepository;
import com.dev.HiddenBATHAuto.orderExcelUpload.repository.OrderExcelDeliveryMethodRepository;
import com.dev.HiddenBATHAuto.orderExcelUpload.repository.OrderExcelDeliveryOrderIndexRepository;
import com.dev.HiddenBATHAuto.orderExcelUpload.repository.OrderExcelMemberRepository;
import com.dev.HiddenBATHAuto.orderExcelUpload.repository.OrderExcelOrderRepository;
import com.dev.HiddenBATHAuto.orderExcelUpload.repository.OrderExcelTaskRepository;
import com.dev.HiddenBATHAuto.orderExcelUpload.repository.OrderExcelTeamCategoryRepository;
import com.dev.HiddenBATHAuto.orderExcelUpload.support.OrderExcelAddressParser;
import com.dev.HiddenBATHAuto.orderExcelUpload.support.OrderExcelCellReader;
import com.dev.HiddenBATHAuto.orderExcelUpload.support.OrderExcelProductNameParser;
import com.dev.HiddenBATHAuto.orderExcelUpload.support.OrderExcelRowKind;
import com.dev.HiddenBATHAuto.orderExcelUpload.support.OrderExcelUploadValidationException;
import com.dev.HiddenBATHAuto.orderExcelUpload.support.ParsedProductName;
import com.dev.HiddenBATHAuto.orderExcelUpload.support.ParsedSiteAddress;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrderExcelUploadService {

    private static final Long PRODUCTION_TEAM_ID = 2L;
    private static final String DELIVERY_TEAM_NAME = "배송팀";
    private static final String DEFAULT_MIDDLE_CATEGORY_NAME = "분류없음";

    /**
     * 실제 주문 엑셀(히든바스.xlsx) 컬럼은 0부터 시작합니다.
     * 0=날짜, 1=요일, 2=거래처, 3=품목, 4=규격/사이즈, 5=수량, 6=비고,
     * 7=단위/대분류, 8=담당자, 9=배송 담당자, 10=사진, 11=단가,
     * 12=공급가액, 13=부가세, 14=합계, 15=생산, 16=LED, 17=유마/해동.
     *
     * 이 양식에는 별도의 규격여부/거울재단여부 컬럼이 없으므로,
     * 해당 값은 AmountItemMaster 매칭 결과를 우선 사용하고,
     * 매칭 실패 시 품목명에 "비규격" 포함 여부로 규격값만 임시 추정합니다.
     */
    private static final int COL_DATE = 0;
    private static final int COL_COMPANY = 2;
    private static final int COL_ITEM_NAME = 3;
    private static final int COL_SIZE = 4;
    private static final int COL_QUANTITY = 5;
    private static final int COL_ADMIN_MEMO = 6;
    private static final int COL_CATEGORY = 7;
    private static final int COL_MANAGER = 8;
    private static final int COL_DELIVERY_HANDLER = 9;
    private static final int COL_PRODUCT_COST = 11;
    private static final int COL_SUPPLY_PRICE = 12;
    private static final int COL_VAT_AMOUNT = 13;
    private static final int COL_TOTAL_AMOUNT = 14;
    private static final int COL_STANDARD = -1;
    private static final int COL_MIRROR_CUTTING = -1;

    private final OrderExcelAmountItemMasterRepository amountItemMasterRepository;
    private final OrderExcelCompanyRepository companyRepository;
    private final OrderExcelMemberRepository memberRepository;
    private final OrderExcelTeamCategoryRepository teamCategoryRepository;
    private final OrderExcelDeliveryMethodRepository deliveryMethodRepository;
    private final OrderExcelTaskRepository taskRepository;
    private final OrderExcelOrderRepository orderRepository;
    private final OrderExcelDeliveryOrderIndexRepository deliveryOrderIndexRepository;
    private final OrderExcelCellReader cellReader;
    private final OrderExcelAddressParser addressParser;
    private final OrderExcelProductNameParser productNameParser;
    private final OrderExcelUploadLookupService lookupService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public OrderExcelPreviewResponse preview(MultipartFile file, Long directDeliveryMethodId, Long siteDeliveryMethodId) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("업로드할 엑셀 파일이 없습니다.");
        }

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);

            List<ExcelRawRow> rawRows = readRows(sheet);
            List<OrderExcelPreviewGroupDto> groups = buildPreviewGroups(rawRows, directDeliveryMethodId, siteDeliveryMethodId);

            OrderExcelPreviewResponse response = new OrderExcelPreviewResponse();
            response.setSuccess(true);
            response.setGroups(groups);
            response.setOptions(lookupService.getOptions());

            List<OrderExcelIssueDto> issues = collectIssues(groups);
            response.setIssues(issues);
            response.setMessage(response.hasErrors()
                    ? "저장 전 확인이 필요한 오류가 있습니다. 빨간 셀을 수정한 뒤 저장해 주세요."
                    : "엑셀 미리보기가 생성되었습니다. 필요한 값을 수정한 뒤 저장해 주세요.");
            return response;
        } catch (IOException e) {
            throw new IllegalArgumentException("엑셀 파일을 읽는 중 오류가 발생했습니다: " + e.getMessage());
        } catch (Exception e) {
            if (e instanceof IllegalArgumentException) {
                throw e;
            }
            throw new IllegalArgumentException("엑셀 미리보기 생성 중 오류가 발생했습니다: " + e.getMessage());
        }
    }

    @Transactional
    public OrderExcelSaveResponse save(OrderExcelSaveRequest request) {
        List<OrderExcelIssueDto> issues = validateSaveRequest(request);

        if (!issues.isEmpty()) {
            throw new OrderExcelUploadValidationException("저장할 수 없는 오류가 있습니다.", issues);
        }

        List<Long> taskIds = new ArrayList<>();
        int orderCount = 0;
        Map<String, Integer> deliveryIndexCache = new HashMap<>();

        for (OrderExcelSaveGroupRequest groupRequest : request.getGroups()) {
            List<OrderExcelSaveRowRequest> rows = activeRows(groupRequest);
            if (rows.isEmpty()) {
                continue;
            }

            Company company = resolveCompanyForSave(groupRequest);
            Member requestedBy = resolveRequestedByForSave(groupRequest, company);
            Member managedBy = resolveManagerForSave(groupRequest);
            DeliveryMethod deliveryMethod = resolveDeliveryMethodForSave(groupRequest.getDeliveryMethodId(), groupRequest.getGroupNo());

            LocalDateTime now = LocalDateTime.now();

            Task task = new Task();
            task.setRequestedBy(requestedBy);
            task.setManagedBy(managedBy);
            task.setStatus(TaskStatus.REQUESTED);
            task.setTotalPrice(0);
            task.setCreatedAt(now);
            task.setUpdatedAt(now);
            task = taskRepository.save(task);

            int ordersTotalAmount = 0;

            for (OrderExcelSaveRowRequest rowRequest : rows) {
                LocalDate deliveryDate = parseRequiredDate(rowRequest.getPreferredDeliveryDate(), rowRequest.getExcelRowNumber(), groupRequest.getGroupNo());
                TeamCategory productionCategory = resolveProductionCategoryForSave(rowRequest, groupRequest.getGroupNo());
                Member deliveryHandler = resolveDeliveryHandlerForSave(rowRequest, groupRequest.getGroupNo());

                Order order = new Order();
                order.setTask(task);
                order.setStandard(rowRequest.isStandard());
                order.setMirrorCuttingProduct(rowRequest.isMirrorCuttingProduct());
                order.setProductCategory(productionCategory);
                order.setAssignedProductionTeam(productionCategory);
                order.setDeliveryMethod(deliveryMethod);
                order.setPackingCost(Math.max(0, groupRequest.getPackingCost()));
                order.setDeliveryCost(Math.max(0, groupRequest.getDeliveryCost()));

                if (deliveryHandler != null) {
                    order.setAssignedDeliveryHandler(deliveryHandler);
                    order.setAssignedDeliveryTeam(deliveryHandler.getTeamCategory());
                }

                applyCompanyAddress(order, groupRequest, company);
                applySiteAddress(order, groupRequest);
                applyOrderer(order, groupRequest, company, requestedBy);

                order.setPreferredDeliveryDate(deliveryDate.atStartOfDay());
                order.setQuantity(rowRequest.getQuantity());
                order.setProductCost(rowRequest.getProductCost());
                order.setSupplyPrice(rowRequest.getSupplyPrice());
                order.setTotalAmount(rowRequest.getTotalAmount());
                order.setAdminMemo(trimToNull(rowRequest.getAdminMemo()));
                order.setStatus(OrderStatus.CONFIRMED);
                order.setCreatedAt(now);
                order.setUpdatedAt(now);

                LinkedHashMap<String, String> optionMap = buildOptionMap(rowRequest, productionCategory);
                OrderItem orderItem = new OrderItem();
                orderItem.setProductName(optionMap.getOrDefault("제품명", safe(rowRequest.getCalculatedProductName())));
                orderItem.setQuantity(rowRequest.getQuantity());
                orderItem.setOptionJson(toJson(optionMap));
                order.setOrderItem(orderItem);

                order = orderRepository.save(order);

                if (deliveryHandler != null) {
                    registerDeliveryOrderIndex(order, deliveryHandler, deliveryDate, deliveryIndexCache);
                }

                ordersTotalAmount += order.getTotalAmount();
                orderCount++;
            }

            task.setTotalPrice(ordersTotalAmount + Math.max(0, groupRequest.getDeliveryCost()) + Math.max(0, groupRequest.getPackingCost()));
            task.setUpdatedAt(LocalDateTime.now());
            taskRepository.save(task);
            taskIds.add(task.getId());
        }

        return new OrderExcelSaveResponse(
                true,
                "엑셀 발주가 저장되었습니다.",
                taskIds.size(),
                orderCount,
                taskIds,
                List.of()
        );
    }

    private List<ExcelRawRow> readRows(Sheet sheet) {
        List<ExcelRawRow> rows = new ArrayList<>();
        if (sheet == null) {
            return rows;
        }

        for (int rowIndex = sheet.getFirstRowNum(); rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }

            String dateText = cellReader.text(row, COL_DATE);
            String companyText = cellReader.text(row, COL_COMPANY);
            String itemName = cellReader.text(row, COL_ITEM_NAME);

            if (isHeaderRow(dateText, companyText, itemName)) {
                continue;
            }

            ExcelRawRow raw = new ExcelRawRow();
            raw.excelRowNumber = rowIndex + 1;
            raw.preferredDeliveryDate = cellReader.date(row, COL_DATE);
            raw.companyRaw = companyText;
            raw.itemName = itemName;
            raw.size = cellReader.text(row, COL_SIZE);
            raw.quantity = cellReader.money(row, COL_QUANTITY);
            raw.adminMemo = cellReader.text(row, COL_ADMIN_MEMO);
            raw.categoryName = cellReader.text(row, COL_CATEGORY);
            raw.managerName = cellReader.text(row, COL_MANAGER);
            raw.deliveryHandlerName = normalizeOptionalMemberName(cellReader.text(row, COL_DELIVERY_HANDLER));
            raw.productCost = cellReader.money(row, COL_PRODUCT_COST);
            raw.supplyPrice = cellReader.money(row, COL_SUPPLY_PRICE);
            raw.vatAmount = cellReader.money(row, COL_VAT_AMOUNT);
            raw.totalAmount = cellReader.money(row, COL_TOTAL_AMOUNT);
            raw.standard = inferStandardFromItemName(itemName);
            raw.mirrorCuttingProduct = false;
            raw.kind = classify(raw);

            rows.add(raw);
        }

        return rows;
    }

    private List<OrderExcelPreviewGroupDto> buildPreviewGroups(List<ExcelRawRow> rawRows, Long directDeliveryMethodId, Long siteDeliveryMethodId) {
        List<OrderExcelPreviewGroupDto> groups = new ArrayList<>();
        List<ExcelRawRow> currentRows = new ArrayList<>();
        String currentCompanyName = null;
        String currentRawCompanyText = null;
        int groupNo = 1;

        for (ExcelRawRow raw : rawRows) {
            if (raw.kind == OrderExcelRowKind.EMPTY) {
                if (!currentRows.isEmpty()) {
                    groups.add(buildOneGroup(groupNo++, currentRawCompanyText, currentCompanyName, currentRows, directDeliveryMethodId, siteDeliveryMethodId));
                    currentRows.clear();
                    currentCompanyName = null;
                    currentRawCompanyText = null;
                }
                continue;
            }

            String rowCompanyName = extractCompanyName(raw.companyRaw);
            if (rowCompanyName.isBlank()) {
                raw.kind = OrderExcelRowKind.SKIP;
                continue;
            }

            if (isAsOnlyCompany(rowCompanyName) || isAsOnlyCompany(raw.companyRaw)) {
                if (!currentRows.isEmpty()) {
                    groups.add(buildOneGroup(groupNo++, currentRawCompanyText, currentCompanyName, currentRows, directDeliveryMethodId, siteDeliveryMethodId));
                    currentRows.clear();
                    currentCompanyName = null;
                    currentRawCompanyText = null;
                }
                raw.kind = OrderExcelRowKind.SKIP;
                continue;
            }

            if (currentCompanyName == null) {
                currentCompanyName = rowCompanyName;
                currentRawCompanyText = raw.companyRaw;
            }

            if (!Objects.equals(currentCompanyName, rowCompanyName)) {
                if (!currentRows.isEmpty()) {
                    groups.add(buildOneGroup(groupNo++, currentRawCompanyText, currentCompanyName, currentRows, directDeliveryMethodId, siteDeliveryMethodId));
                }
                currentRows = new ArrayList<>();
                currentCompanyName = rowCompanyName;
                currentRawCompanyText = raw.companyRaw;
            }

            currentRows.add(raw);
        }

        if (!currentRows.isEmpty()) {
            groups.add(buildOneGroup(groupNo, currentRawCompanyText, currentCompanyName, currentRows, directDeliveryMethodId, siteDeliveryMethodId));
        }

        return groups;
    }

    private OrderExcelPreviewGroupDto buildOneGroup(
            int groupNo,
            String rawCompanyText,
            String companyName,
            List<ExcelRawRow> rawRows,
            Long directDeliveryMethodId,
            Long siteDeliveryMethodId
    ) {
        OrderExcelPreviewGroupDto group = new OrderExcelPreviewGroupDto();
        group.setGroupNo(groupNo);
        group.setRawCompanyText(safe(rawCompanyText));
        group.setCompanyName(safe(companyName));

        Optional<Company> companyOpt = findUniqueCompany(companyName, group.getIssues(), groupNo, firstRowNo(rawRows));
        companyOpt.ifPresent(company -> applyCompanyToPreviewGroup(group, company));

        ExcelRawRow siteAddressRow = rawRows.stream()
                .filter(row -> row.kind == OrderExcelRowKind.SITE_ADDRESS)
                .reduce((first, second) -> second)
                .orElse(null);

        group.setSiteDelivery(siteAddressRow != null);

        DeliveryMethod method = resolvePreviewDeliveryMethod(
                group.isSiteDelivery() ? siteDeliveryMethodId : directDeliveryMethodId,
                group.isSiteDelivery(),
                group.getIssues(),
                groupNo,
                firstRowNo(rawRows)
        );

        if (method != null) {
            group.setDeliveryMethodId(method.getId());
            group.setDeliveryMethodName(method.getMethodName());
        }

        int deliveryCost = rawRows.stream()
                .filter(row -> row.kind == OrderExcelRowKind.FREIGHT)
                .mapToInt(this::resolveFreightCost)
                .sum();
        group.setDeliveryCost(deliveryCost);
        group.setPackingCost(0);

        if (siteAddressRow != null) {
            ParsedSiteAddress parsed = addressParser.parse(siteAddressRow.itemName);
            applyParsedSiteAddress(group, parsed);
            for (String warning : parsed.getWarnings()) {
                group.getIssues().add(OrderExcelIssueDto.warn(siteAddressRow.excelRowNumber, groupNo, "siteAddress", warning));
            }
        }

        rawRows.stream()
                .filter(row -> row.kind == OrderExcelRowKind.PRODUCT)
                .forEach(row -> group.getRows().add(toPreviewRow(row, groupNo)));

        if (group.getRows().isEmpty()) {
            group.getIssues().add(OrderExcelIssueDto.error(firstRowNo(rawRows), groupNo, "rows", "저장할 제품 행이 없습니다."));
        }

        ExcelRawRow firstProduct = rawRows.stream()
                .filter(row -> row.kind == OrderExcelRowKind.PRODUCT)
                .findFirst()
                .orElse(null);

        if (firstProduct != null) {
            resolveMemberByNameForPreview(firstProduct.managerName, group.getIssues(), firstProduct.excelRowNumber, groupNo, "managedBy")
                    .ifPresent(member -> {
                        group.setManagedByMemberId(member.getId());
                        group.setManagedByName(member.getName());
                    });
        }

        return group;
    }

    private void applyCompanyToPreviewGroup(OrderExcelPreviewGroupDto group, Company company) {
        group.setCompanyId(company.getId());

        ResolvedCompanyAddress address = resolveCompanyDefaultAddress(company);
        group.setZipCode(address.zipCode());
        group.setDoName(address.doName());
        group.setSiName(address.siName());
        group.setGuName(address.guName());
        group.setRoadAddress(address.roadAddress());
        group.setDetailAddress(address.detailAddress());

        if (address.fallbackUsed()) {
            group.getIssues().add(OrderExcelIssueDto.warn(
                    null,
                    group.getGroupNo(),
                    "roadAddress",
                    "업체 기본 도로명/상세주소가 비어 있어 originAddress 또는 jibunAddress를 기본배송지로 사용했습니다."
            ));
        }

        List<Member> reps = memberRepository.findByCompany_IdAndRoleOrderByIdAsc(company.getId(), MemberRole.CUSTOMER_REPRESENTATIVE);
        if (reps.isEmpty()) {
            group.getIssues().add(OrderExcelIssueDto.error(null, group.getGroupNo(), "requestedBy", "해당 업체의 CUSTOMER_REPRESENTATIVE 멤버가 없습니다."));
        } else {
            Member rep = reps.get(0);
            group.setRequestedByMemberId(rep.getId());
            group.setRequestedByName(rep.getName());
            group.setOrdererName(rep.getName());
            group.setOrdererPhone(rep.getPhone());
        }
    }

    private void applyParsedSiteAddress(OrderExcelPreviewGroupDto group, ParsedSiteAddress parsed) {
        group.setSiteAddressRaw(safe(parsed.getRaw()));
        group.setSiteZipCode(safe(parsed.getZipCode()));
        group.setSiteDoName(safe(parsed.getDoName()));
        group.setSiteSiName(safe(parsed.getSiName()));
        group.setSiteGuName(safe(parsed.getGuName()));
        group.setSiteRoadAddress(safe(parsed.getRoadAddress()));
        group.setSiteDetailAddress(safe(parsed.getDetailAddress()));
        group.setSiteRecipientName(safe(parsed.getRecipientName()));
        group.setSiteRecipientPhone(safe(parsed.getRecipientPhone()));

        if (!safe(parsed.getRecipientName()).isBlank()) {
            group.setOrdererName(parsed.getRecipientName());
        }
        if (!safe(parsed.getRecipientPhone()).isBlank()) {
            group.setOrdererPhone(parsed.getRecipientPhone());
        }
    }

    private OrderExcelPreviewRowDto toPreviewRow(ExcelRawRow raw, int groupNo) {
        OrderExcelPreviewRowDto row = new OrderExcelPreviewRowDto();
        row.setExcelRowNumber(raw.excelRowNumber);
        row.setOriginalCompanyText(raw.companyRaw);
        row.setOriginalItemName(raw.itemName);
        row.setPreferredDeliveryDate(raw.preferredDeliveryDate == null ? "" : raw.preferredDeliveryDate.toString());
        row.setSize(safe(raw.size));
        row.setQuantity(raw.quantity);
        row.setAdminMemo(safe(raw.adminMemo));
        row.setCategoryName(normalizeCategoryName(firstNonBlank(raw.categoryName, "")));
        row.setProductCost(raw.productCost);
        row.setSupplyPrice(raw.supplyPrice);
        row.setVatAmount(raw.vatAmount);
        row.setTotalAmount(raw.totalAmount);
        row.setStandard(raw.standard);
        row.setMirrorCuttingProduct(raw.mirrorCuttingProduct);
        row.setDeliveryHandlerName(safe(raw.deliveryHandlerName));

        AmountItemMaster itemMaster = amountItemMasterRepository.findFirstByItemNameOrderByIdDesc(raw.itemName).orElse(null);
        if (itemMaster != null) {
            row.setAmountItemMasterId(itemMaster.getId());
            row.setStandard(itemMaster.isStandard());
            row.setMirrorCuttingProduct(itemMaster.isMirrorCuttingProduct());
            if (row.getCategoryName().isBlank()) {
                row.setCategoryName(normalizeCategoryName(itemMaster.getCategoryName()));
            }
            row.setMiddleCategoryName(resolveMiddleCategoryName(itemMaster.getMiddleCategoryName()));
            if (!safe(itemMaster.getCategoryName()).isBlank() && !safe(raw.categoryName).isBlank()
                    && !normalizeCategoryName(itemMaster.getCategoryName()).equals(normalizeCategoryName(raw.categoryName))) {
                row.getIssues().add(OrderExcelIssueDto.warn(raw.excelRowNumber, groupNo, "categoryName", "엑셀 대분류와 AmountItemMaster 대분류가 다릅니다. 엑셀 값을 우선 표시했습니다."));
            }
        } else {
            row.setMiddleCategoryName(DEFAULT_MIDDLE_CATEGORY_NAME);
            row.getIssues().add(OrderExcelIssueDto.warn(raw.excelRowNumber, groupNo, "amountItemMaster", "AmountItemMaster에서 동일 품목명을 찾지 못했습니다. 제품명/색상/중분류를 확인해 주세요."));
        }

        ParsedProductName parsedProductName = productNameParser.parse(raw.itemName, row.getCategoryName());
        row.setCalculatedProductName(parsedProductName.getProductName());
        row.setItemNameForSave(parsedProductName.getProductNameForSave());
        row.setColor(parsedProductName.getColor());

        resolveProductionCategoryForPreview(row.getCategoryName(), row.getIssues(), raw.excelRowNumber, groupNo)
                .ifPresent(category -> {
                    row.setProductionCategoryId(category.getId());
                    row.setCategoryName(category.getName());
                });

        resolveDeliveryHandlerByNameForPreview(raw.deliveryHandlerName, row.getIssues(), raw.excelRowNumber, groupNo)
                .ifPresent(member -> {
                    row.setDeliveryHandlerMemberId(member.getId());
                    row.setDeliveryHandlerName(member.getName());
                });

        if (raw.preferredDeliveryDate == null) {
            row.getIssues().add(OrderExcelIssueDto.error(raw.excelRowNumber, groupNo, "preferredDeliveryDate", "출고일을 읽지 못했습니다."));
        }
        if (safe(raw.itemName).isBlank()) {
            row.getIssues().add(OrderExcelIssueDto.error(raw.excelRowNumber, groupNo, "itemName", "품목명이 비어 있습니다."));
        }
        if (row.getCategoryName().isBlank()) {
            row.getIssues().add(OrderExcelIssueDto.error(raw.excelRowNumber, groupNo, "categoryName", "대분류가 비어 있습니다."));
        }
        if (row.getSize().isBlank()) {
            row.getIssues().add(OrderExcelIssueDto.warn(raw.excelRowNumber, groupNo, "size", "사이즈가 비어 있습니다."));
        }
        if (row.getTotalAmount() == 0 && row.getSupplyPrice() > 0) {
            row.setTotalAmount(row.getSupplyPrice() + Math.round(row.getSupplyPrice() * 0.1f));
            row.setVatAmount(row.getTotalAmount() - row.getSupplyPrice());
        }
        if (row.getSupplyPrice() == 0 && row.getProductCost() != 0 && row.getQuantity() != 0) {
            row.setSupplyPrice(row.getProductCost() * row.getQuantity());
            row.setVatAmount(Math.round(row.getSupplyPrice() * 0.1f));
            row.setTotalAmount(row.getSupplyPrice() + row.getVatAmount());
        }

        return row;
    }

    private List<OrderExcelIssueDto> validateSaveRequest(OrderExcelSaveRequest request) {
        List<OrderExcelIssueDto> issues = new ArrayList<>();

        if (request == null || request.getGroups() == null || request.getGroups().isEmpty()) {
            issues.add(OrderExcelIssueDto.error(null, null, "groups", "저장할 미리보기 데이터가 없습니다."));
            return issues;
        }

        for (OrderExcelSaveGroupRequest group : request.getGroups()) {
            if (activeRows(group).isEmpty()) {
                continue;
            }

            validateCompanyForSave(group, issues);
            validateRequestedByForSave(group, issues);
            validateManagerForSave(group, issues);
            if (group.getDeliveryMethodId() == null) {
                issues.add(OrderExcelIssueDto.error(null, group.getGroupNo(), "deliveryMethodId", "배송수단을 선택해 주세요."));
            } else if (deliveryMethodRepository.findById(group.getDeliveryMethodId()).isEmpty()) {
                issues.add(OrderExcelIssueDto.error(null, group.getGroupNo(), "deliveryMethodId", "선택한 배송수단을 찾을 수 없습니다."));
            }

            if (group.isSiteDelivery() && safe(group.getSiteRoadAddress()).isBlank()) {
                issues.add(OrderExcelIssueDto.error(null, group.getGroupNo(), "siteRoadAddress", "현장배송 묶음은 현장 주소가 필요합니다."));
            }

            for (OrderExcelSaveRowRequest row : activeRows(group)) {
                validateRowForSave(row, group, issues);
            }
        }

        return issues;
    }

    private void validateCompanyForSave(OrderExcelSaveGroupRequest group, List<OrderExcelIssueDto> issues) {
        if (group.getCompanyId() != null) {
            if (companyRepository.findById(group.getCompanyId()).isEmpty()) {
                issues.add(OrderExcelIssueDto.error(null, group.getGroupNo(), "companyId", "선택한 업체를 찾을 수 없습니다."));
            }
            return;
        }

        if (safe(group.getCompanyName()).isBlank()) {
            issues.add(OrderExcelIssueDto.error(null, group.getGroupNo(), "companyName", "거래처명이 비어 있습니다."));
            return;
        }

        List<Company> companies = findCompanies(group.getCompanyName());
        if (companies.isEmpty()) {
            issues.add(OrderExcelIssueDto.error(null, group.getGroupNo(), "companyName", "거래처명으로 업체를 찾을 수 없습니다: " + group.getCompanyName()));
        } else if (companies.size() > 1) {
            issues.add(OrderExcelIssueDto.error(null, group.getGroupNo(), "companyName", "거래처명이 중복됩니다. 업체명을 더 정확히 수정해 주세요: " + group.getCompanyName()));
        }
    }

    private void validateRequestedByForSave(OrderExcelSaveGroupRequest group, List<OrderExcelIssueDto> issues) {
        Company company = null;
        try {
            company = resolveCompanyForSave(group);
        } catch (Exception ignored) {
            return;
        }

        if (group.getRequestedByMemberId() != null && memberRepository.findById(group.getRequestedByMemberId()).isPresent()) {
            return;
        }

        List<Member> reps = memberRepository.findByCompany_IdAndRoleOrderByIdAsc(company.getId(), MemberRole.CUSTOMER_REPRESENTATIVE);
        if (reps.isEmpty()) {
            issues.add(OrderExcelIssueDto.error(null, group.getGroupNo(), "requestedBy", "해당 업체의 CUSTOMER_REPRESENTATIVE 멤버가 없습니다."));
        }
    }

    private void validateManagerForSave(OrderExcelSaveGroupRequest group, List<OrderExcelIssueDto> issues) {
        if (group.getManagedByMemberId() != null) {
            if (memberRepository.findById(group.getManagedByMemberId()).isEmpty()) {
                issues.add(OrderExcelIssueDto.error(null, group.getGroupNo(), "managedBy", "선택한 담당자를 찾을 수 없습니다."));
            }
            return;
        }

        if (safe(group.getManagedByName()).isBlank()) {
            return;
        }

        List<Member> members = memberRepository.findByName(group.getManagedByName().trim());
        if (members.isEmpty()) {
            issues.add(OrderExcelIssueDto.error(null, group.getGroupNo(), "managedBy", "담당자 이름으로 멤버를 찾을 수 없습니다: " + group.getManagedByName()));
        } else if (members.size() > 1) {
            issues.add(OrderExcelIssueDto.error(null, group.getGroupNo(), "managedBy", "담당자 이름이 중복됩니다. 저장 전 정확히 선택해 주세요: " + group.getManagedByName()));
        }
    }

    private void validateRowForSave(OrderExcelSaveRowRequest row, OrderExcelSaveGroupRequest group, List<OrderExcelIssueDto> issues) {
        if (parseDate(row.getPreferredDeliveryDate()) == null) {
            issues.add(OrderExcelIssueDto.error(row.getExcelRowNumber(), group.getGroupNo(), "preferredDeliveryDate", "출고일이 올바르지 않습니다."));
        }
        if (safe(row.getItemNameForSave()).isBlank()) {
            issues.add(OrderExcelIssueDto.error(row.getExcelRowNumber(), group.getGroupNo(), "itemNameForSave", "저장 제품명이 비어 있습니다."));
        }
        if (row.getProductionCategoryId() == null && safe(row.getCategoryName()).isBlank()) {
            issues.add(OrderExcelIssueDto.error(row.getExcelRowNumber(), group.getGroupNo(), "productionCategoryId", "생산팀 분류를 선택해 주세요."));
        } else {
            try {
                resolveProductionCategoryForSave(row, group.getGroupNo());
            } catch (Exception e) {
                issues.add(OrderExcelIssueDto.error(row.getExcelRowNumber(), group.getGroupNo(), "productionCategoryId", e.getMessage()));
            }
        }
        if (row.getDeliveryHandlerMemberId() != null) {
            Optional<Member> member = memberRepository.findById(row.getDeliveryHandlerMemberId());
            if (member.isEmpty()) {
                issues.add(OrderExcelIssueDto.error(row.getExcelRowNumber(), group.getGroupNo(), "deliveryHandler", "선택한 배송 담당자를 찾을 수 없습니다."));
            } else if (!isDeliveryTeamMember(member.get())) {
                issues.add(OrderExcelIssueDto.error(row.getExcelRowNumber(), group.getGroupNo(), "deliveryHandler", "배송 담당자는 배송팀 소속이어야 합니다."));
            }
        } else if (!safe(row.getDeliveryHandlerName()).isBlank()) {
            List<Member> members = memberRepository.findByTeamNameAndName(DELIVERY_TEAM_NAME, row.getDeliveryHandlerName().trim());
            if (members.isEmpty()) {
                issues.add(OrderExcelIssueDto.error(row.getExcelRowNumber(), group.getGroupNo(), "deliveryHandler", "배송팀에서 배송 담당자 이름을 찾을 수 없습니다: " + row.getDeliveryHandlerName()));
            } else if (members.size() > 1) {
                issues.add(OrderExcelIssueDto.error(row.getExcelRowNumber(), group.getGroupNo(), "deliveryHandler", "배송팀 내 배송 담당자 이름이 중복됩니다. 저장 전 셀렉트에서 정확히 선택해 주세요: " + row.getDeliveryHandlerName()));
            }
        }
    }

    private List<OrderExcelSaveRowRequest> activeRows(OrderExcelSaveGroupRequest group) {
        if (group == null || group.getRows() == null) {
            return List.of();
        }
        return group.getRows().stream()
                .filter(OrderExcelSaveRowRequest::isSaveTarget)
                .collect(Collectors.toList());
    }

    private Company resolveCompanyForSave(OrderExcelSaveGroupRequest group) {
        if (group.getCompanyId() != null) {
            return companyRepository.findById(group.getCompanyId())
                    .orElseThrow(() -> new IllegalArgumentException("선택한 업체를 찾을 수 없습니다."));
        }

        List<Company> companies = findCompanies(group.getCompanyName());
        if (companies.size() != 1) {
            throw new IllegalArgumentException("거래처를 정확히 찾을 수 없습니다: " + group.getCompanyName());
        }
        return companies.get(0);
    }

    private Member resolveRequestedByForSave(OrderExcelSaveGroupRequest group, Company company) {
        if (group.getRequestedByMemberId() != null) {
            return memberRepository.findById(group.getRequestedByMemberId())
                    .orElseThrow(() -> new IllegalArgumentException("선택한 요청자 멤버를 찾을 수 없습니다."));
        }

        return memberRepository.findByCompany_IdAndRoleOrderByIdAsc(company.getId(), MemberRole.CUSTOMER_REPRESENTATIVE)
                .stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("해당 업체의 CUSTOMER_REPRESENTATIVE 멤버가 없습니다."));
    }

    private Member resolveManagerForSave(OrderExcelSaveGroupRequest group) {
        if (group.getManagedByMemberId() != null) {
            return memberRepository.findById(group.getManagedByMemberId())
                    .orElseThrow(() -> new IllegalArgumentException("선택한 담당자를 찾을 수 없습니다."));
        }
        if (safe(group.getManagedByName()).isBlank()) {
            return null;
        }
        List<Member> members = memberRepository.findByName(group.getManagedByName().trim());
        if (members.size() != 1) {
            throw new IllegalArgumentException("담당자 이름을 정확히 찾을 수 없습니다: " + group.getManagedByName());
        }
        return members.get(0);
    }

    private Member resolveDeliveryHandlerForSave(OrderExcelSaveRowRequest row, int groupNo) {
        if (row.getDeliveryHandlerMemberId() != null) {
            Member member = memberRepository.findById(row.getDeliveryHandlerMemberId())
                    .orElseThrow(() -> new IllegalArgumentException("배송 담당자를 찾을 수 없습니다."));
            if (!isDeliveryTeamMember(member)) {
                throw new IllegalArgumentException("그룹 " + groupNo + ": 배송 담당자는 배송팀 소속이어야 합니다.");
            }
            return member;
        }
        if (safe(row.getDeliveryHandlerName()).isBlank()) {
            return null;
        }
        List<Member> members = memberRepository.findByTeamNameAndName(DELIVERY_TEAM_NAME, row.getDeliveryHandlerName().trim());
        if (members.size() != 1) {
            throw new IllegalArgumentException("그룹 " + groupNo + ": 배송팀 내 배송 담당자 이름을 정확히 찾을 수 없습니다: " + row.getDeliveryHandlerName());
        }
        return members.get(0);
    }

    private DeliveryMethod resolveDeliveryMethodForSave(Long deliveryMethodId, int groupNo) {
        return deliveryMethodRepository.findById(deliveryMethodId)
                .orElseThrow(() -> new IllegalArgumentException("그룹 " + groupNo + ": 배송수단을 찾을 수 없습니다."));
    }

    private TeamCategory resolveProductionCategoryForSave(OrderExcelSaveRowRequest row, int groupNo) {
        if (row.getProductionCategoryId() != null) {
            return teamCategoryRepository.findByIdAndTeam_Id(row.getProductionCategoryId(), PRODUCTION_TEAM_ID)
                    .orElseThrow(() -> new IllegalArgumentException("생산팀 분류를 찾을 수 없습니다."));
        }

        List<TeamCategory> categories = findProductionCategories(row.getCategoryName());
        if (categories.size() != 1) {
            throw new IllegalArgumentException("생산팀 분류명을 정확히 찾을 수 없습니다: " + row.getCategoryName());
        }
        return categories.get(0);
    }

    private void applyCompanyAddress(Order order, OrderExcelSaveGroupRequest group, Company company) {
        ResolvedCompanyAddress companyAddress = resolveCompanyDefaultAddress(company);

        order.setZipCode(firstMeaningful(group.getZipCode(), companyAddress.zipCode()));
        order.setDoName(firstMeaningful(group.getDoName(), companyAddress.doName()));
        order.setSiName(firstMeaningful(group.getSiName(), companyAddress.siName()));
        order.setGuName(firstMeaningful(group.getGuName(), companyAddress.guName()));

        String groupRoadAddress = normalizeMeaningful(group.getRoadAddress());
        String groupDetailAddress = normalizeMeaningful(group.getDetailAddress());

        if (hasText(groupRoadAddress) || hasText(groupDetailAddress)) {
            order.setRoadAddress(groupRoadAddress);
            order.setDetailAddress(groupDetailAddress);
            return;
        }

        order.setRoadAddress(companyAddress.roadAddress());
        order.setDetailAddress(companyAddress.detailAddress());
    }

    private void applySiteAddress(Order order, OrderExcelSaveGroupRequest group) {
        if (!group.isSiteDelivery()) {
            order.setSiteZipCode(null);
            order.setSiteDoName(null);
            order.setSiteSiName(null);
            order.setSiteGuName(null);
            order.setSiteRoadAddress(null);
            order.setSiteDetailAddress(null);
            return;
        }

        order.setSiteZipCode(trimToNull(group.getSiteZipCode()));
        order.setSiteDoName(trimToNull(group.getSiteDoName()));
        order.setSiteSiName(trimToNull(group.getSiteSiName()));
        order.setSiteGuName(trimToNull(group.getSiteGuName()));
        order.setSiteRoadAddress(trimToNull(group.getSiteRoadAddress()));
        order.setSiteDetailAddress(trimToNull(group.getSiteDetailAddress()));
    }

    private void applyOrderer(Order order, OrderExcelSaveGroupRequest group, Company company, Member requestedBy) {
        String ordererName = firstNonBlank(group.getOrdererName(), group.getSiteRecipientName(), requestedBy == null ? null : requestedBy.getName());
        String ordererPhone = firstNonBlank(group.getOrdererPhone(), group.getSiteRecipientPhone(), requestedBy == null ? null : requestedBy.getPhone());
        order.setOrdererName(trimToNull(ordererName));
        order.setOrdererPhone(trimToNull(ordererPhone));
    }

    private LinkedHashMap<String, String> buildOptionMap(OrderExcelSaveRowRequest row, TeamCategory productionCategory) {
        LinkedHashMap<String, String> optionMap = new LinkedHashMap<>();
        optionMap.put("카테고리", firstNonBlank(row.getCategoryName(), productionCategory == null ? null : productionCategory.getName()));
        if (row.isStandard()) {
            optionMap.put("제품시리즈", resolveMiddleCategoryName(row.getMiddleCategoryName()));
            optionMap.put("제품시리즈ID", "");
        }
        optionMap.put("제품명", safe(row.getItemNameForSave()));
        optionMap.put("사이즈", safe(row.getSize()));
        optionMap.put("색상", safe(row.getColor()));
        return optionMap;
    }

    private void registerDeliveryOrderIndex(Order order, Member deliveryHandler, LocalDate deliveryDate, Map<String, Integer> deliveryIndexCache) {
        String key = deliveryHandler.getId() + "|" + deliveryDate;
        int nextIndex;
        if (deliveryIndexCache.containsKey(key)) {
            nextIndex = deliveryIndexCache.get(key) + 1;
        } else {
            nextIndex = deliveryOrderIndexRepository
                    .findMaxOrderIndexByDeliveryHandlerAndDeliveryDate(deliveryHandler.getId(), deliveryDate)
                    .orElse(0) + 1;
        }

        DeliveryOrderIndex deliveryOrderIndex = new DeliveryOrderIndex();
        deliveryOrderIndex.setDeliveryHandler(deliveryHandler);
        deliveryOrderIndex.setOrder(order);
        deliveryOrderIndex.setDeliveryDate(deliveryDate);
        deliveryOrderIndex.setOrderIndex(nextIndex);
        deliveryOrderIndexRepository.save(deliveryOrderIndex);
        deliveryIndexCache.put(key, nextIndex);
    }

    private String toJson(LinkedHashMap<String, String> optionMap) {
        try {
            return objectMapper.writeValueAsString(optionMap);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("옵션 JSON 생성에 실패했습니다.");
        }
    }

    private List<OrderExcelIssueDto> collectIssues(List<OrderExcelPreviewGroupDto> groups) {
        List<OrderExcelIssueDto> result = new ArrayList<>();
        if (groups == null) {
            return result;
        }
        for (OrderExcelPreviewGroupDto group : groups) {
            result.addAll(group.getIssues());
            for (OrderExcelPreviewRowDto row : group.getRows()) {
                result.addAll(row.getIssues());
            }
        }
        return result;
    }

    private OrderExcelRowKind classify(ExcelRawRow row) {
        if (safe(row.companyRaw).isBlank() && safe(row.itemName).isBlank()) {
            return OrderExcelRowKind.EMPTY;
        }
        if (isFreight(row.itemName)) {
            return OrderExcelRowKind.FREIGHT;
        }
        if (addressParser.looksLikeAddress(row.itemName)) {
            return OrderExcelRowKind.SITE_ADDRESS;
        }
        if (!safe(row.itemName).isBlank()) {
            return OrderExcelRowKind.PRODUCT;
        }
        return OrderExcelRowKind.SKIP;
    }

    private boolean isFreight(String itemName) {
        String normalized = safe(itemName).replaceAll("\\s+", "");
        return "운임비".equals(normalized)
                || "배송비".equals(normalized)
                || "화물비".equals(normalized)
                || "택배비".equals(normalized);
    }

    private int resolveFreightCost(ExcelRawRow row) {
        if (row.totalAmount > 0) {
            return row.totalAmount;
        }
        if (row.supplyPrice > 0) {
            return row.supplyPrice;
        }
        return Math.max(0, row.productCost);
    }


    private String normalizeOptionalMemberName(String value) {
        String normalized = safe(value).trim();
        if (normalized.isBlank()) {
            return "";
        }

        String compact = normalized.replaceAll("\\s+", "");
        if ("ㅇ".equals(compact)
                || "O".equalsIgnoreCase(compact)
                || "0".equals(compact)
                || "-".equals(compact)
                || "없음".equals(compact)
                || "미정".equals(compact)) {
            return "";
        }

        return normalized;
    }

    private boolean isAsOnlyCompany(String value) {
        String normalized = safe(value).replace("*", "").replaceAll("\\s+", "").toUpperCase();
        return normalized.contains("A/S용".toUpperCase())
                || normalized.contains("AS용".toUpperCase())
                || normalized.contains("A/S")
                || normalized.contains("AS용".toUpperCase());
    }

    private String extractCompanyName(String rawCompany) {
        String value = safe(rawCompany).replace("*", "").trim();
        int slashIndex = value.indexOf('/');
        if (slashIndex >= 0 && slashIndex < value.length() - 1) {
            value = value.substring(slashIndex + 1);
        }
        return value.trim();
    }

    private Optional<Company> findUniqueCompany(String companyName, List<OrderExcelIssueDto> issues, int groupNo, Integer rowNo) {
        if (safe(companyName).isBlank()) {
            issues.add(OrderExcelIssueDto.error(rowNo, groupNo, "companyName", "거래처명이 비어 있습니다."));
            return Optional.empty();
        }

        List<Company> companies = findCompanies(companyName);
        if (companies.isEmpty()) {
            issues.add(OrderExcelIssueDto.error(rowNo, groupNo, "companyName", "거래처명으로 업체를 찾을 수 없습니다: " + companyName));
            return Optional.empty();
        }
        if (companies.size() > 1) {
            issues.add(OrderExcelIssueDto.error(rowNo, groupNo, "companyName", "거래처명이 중복됩니다. 업체명을 더 정확히 수정해 주세요: " + companyName));
            return Optional.empty();
        }
        return Optional.of(companies.get(0));
    }

    private List<Company> findCompanies(String companyName) {
        String trimmed = safe(companyName);
        if (trimmed.isBlank()) {
            return List.of();
        }

        List<Company> exact = companyRepository.findByCompanyName(trimmed);
        if (!exact.isEmpty()) {
            return exact;
        }

        return companyRepository.findByCompanyNameWithoutSpaces(trimmed.replaceAll("\\s+", ""));
    }

    private Optional<Member> resolveMemberByNameForPreview(String name, List<OrderExcelIssueDto> issues, Integer rowNo, int groupNo, String field) {
        String normalizedName = normalizeOptionalMemberName(name);
        if (normalizedName.isBlank()) {
            return Optional.empty();
        }

        List<Member> members = memberRepository.findByName(normalizedName);
        if (members.isEmpty()) {
            issues.add(OrderExcelIssueDto.error(rowNo, groupNo, field, "멤버 이름으로 찾을 수 없습니다: " + normalizedName));
            return Optional.empty();
        }
        if (members.size() > 1) {
            issues.add(OrderExcelIssueDto.error(rowNo, groupNo, field, "동명이인 멤버가 있습니다. 저장 전 셀렉트에서 정확히 선택해 주세요: " + normalizedName));
            return Optional.empty();
        }
        return Optional.of(members.get(0));
    }
    private Optional<Member> resolveDeliveryHandlerByNameForPreview(String name, List<OrderExcelIssueDto> issues, Integer rowNo, int groupNo) {
        String normalizedName = normalizeOptionalMemberName(name);
        if (normalizedName.isBlank()) {
            return Optional.empty();
        }

        List<Member> members = memberRepository.findByTeamNameAndName(DELIVERY_TEAM_NAME, normalizedName);
        if (members.isEmpty()) {
            issues.add(OrderExcelIssueDto.warn(rowNo, groupNo, "deliveryHandler", "엑셀 배송담당자 이름을 배송팀에서 찾지 못했습니다. 저장 전 셀렉트에서 선택해 주세요: " + normalizedName));
            return Optional.empty();
        }
        if (members.size() > 1) {
            issues.add(OrderExcelIssueDto.error(rowNo, groupNo, "deliveryHandler", "배송팀 내 동명이인 배송담당자가 있습니다. 저장 전 셀렉트에서 정확히 선택해 주세요: " + normalizedName));
            return Optional.empty();
        }
        return Optional.of(members.get(0));
    }

    private boolean isDeliveryTeamMember(Member member) {
        return member != null
                && member.getTeam() != null
                && DELIVERY_TEAM_NAME.equals(member.getTeam().getName());
    }


    private Optional<TeamCategory> resolveProductionCategoryForPreview(String categoryName, List<OrderExcelIssueDto> issues, Integer rowNo, int groupNo) {
        if (safe(categoryName).isBlank()) {
            return Optional.empty();
        }
        List<TeamCategory> categories = findProductionCategories(categoryName);
        if (categories.isEmpty()) {
            issues.add(OrderExcelIssueDto.error(rowNo, groupNo, "productionCategory", "생산팀 분류를 찾을 수 없습니다: " + categoryName));
            return Optional.empty();
        }
        if (categories.size() > 1) {
            issues.add(OrderExcelIssueDto.error(rowNo, groupNo, "productionCategory", "생산팀 분류명이 중복됩니다: " + categoryName));
            return Optional.empty();
        }
        return Optional.of(categories.get(0));
    }

    private List<TeamCategory> findProductionCategories(String categoryName) {
        String normalized = normalizeCategoryName(categoryName);
        if (normalized.isBlank()) {
            return List.of();
        }

        List<TeamCategory> exact = teamCategoryRepository.findByTeam_IdAndNameIgnoreCase(PRODUCTION_TEAM_ID, normalized);
        if (!exact.isEmpty()) {
            return exact;
        }

        return teamCategoryRepository.findByTeamIdAndNameWithoutSpaces(PRODUCTION_TEAM_ID, normalized.replaceAll("\\s+", ""));
    }

    private DeliveryMethod resolvePreviewDeliveryMethod(Long requestedId, boolean siteDelivery, List<OrderExcelIssueDto> issues, int groupNo, Integer rowNo) {
        if (requestedId != null) {
            Optional<DeliveryMethod> requested = deliveryMethodRepository.findById(requestedId);
            if (requested.isPresent()) {
                return requested.get();
            }
            issues.add(OrderExcelIssueDto.error(rowNo, groupNo, "deliveryMethod", "선택한 기본 배송수단을 찾을 수 없습니다."));
            return null;
        }

        String keyword = siteDelivery ? "현장배송" : "직배송";
        List<DeliveryMethod> methods = deliveryMethodRepository.findAllByOrderByMethodNameAsc()
                .stream()
                .filter(method -> safe(method.getMethodName()).replace(" ", "").contains(keyword))
                .collect(Collectors.toList());

        if (methods.isEmpty()) {
            issues.add(OrderExcelIssueDto.error(rowNo, groupNo, "deliveryMethod", keyword + " 배송수단을 DB에서 찾지 못했습니다. 상단 SELECT에서 직접 선택해 주세요."));
            return null;
        }
        if (methods.size() > 1) {
            issues.add(OrderExcelIssueDto.warn(rowNo, groupNo, "deliveryMethod", keyword + " 배송수단이 여러 개라 첫 번째 값을 미리 선택했습니다. 저장 전 확인해 주세요."));
        }
        return methods.get(0);
    }

    private LocalDate parseRequiredDate(String value, Integer rowNo, int groupNo) {
        LocalDate date = parseDate(value);
        if (date == null) {
            throw new IllegalArgumentException("그룹 " + groupNo + " / 엑셀 " + rowNo + "행: 출고일이 올바르지 않습니다.");
        }
        return date;
    }

    private LocalDate parseDate(String value) {
        if (safe(value).isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private boolean inferStandardFromItemName(String itemName) {
        String normalized = safe(itemName).replaceAll("\\s+", "");
        if (normalized.isBlank()) {
            return false;
        }
        return !normalized.contains("비규격");
    }

    private boolean isHeaderRow(String dateText, String companyText, String itemName) {
        String merged = (safe(dateText) + " " + safe(companyText) + " " + safe(itemName)).replaceAll("\\s+", "");
        return merged.contains("날짜") && merged.contains("거래처")
                || merged.contains("출고일") && merged.contains("품목명");
    }

    private String normalizeCategoryName(String value) {
        return productNameParser.normalizeCategory(value);
    }

    private String resolveMiddleCategoryName(String value) {
        String normalized = safe(value);
        if (normalized.isBlank() || "X".equalsIgnoreCase(normalized)) {
            return DEFAULT_MIDDLE_CATEGORY_NAME;
        }
        return normalized;
    }

    private Integer firstRowNo(List<ExcelRawRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        return rows.get(0).excelRowNumber;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String trimToNull(String value) {
        String safe = safe(value);
        return safe.isBlank() ? null : safe;
    }

    private ResolvedCompanyAddress resolveCompanyDefaultAddress(Company company) {
        if (company == null) {
            return new ResolvedCompanyAddress("", "", "", "", "", "", false);
        }

        String roadAddress = normalizeMeaningful(company.getRoadAddress());
        String detailAddress = normalizeMeaningful(company.getDetailAddress());

        if (hasText(roadAddress) || hasText(detailAddress)) {
            return new ResolvedCompanyAddress(
                    normalizeMeaningful(company.getZipCode()),
                    normalizeMeaningful(company.getDoName()),
                    normalizeMeaningful(company.getSiName()),
                    normalizeMeaningful(company.getGuName()),
                    roadAddress,
                    detailAddress,
                    false
            );
        }

        String fallbackAddress = firstMeaningful(
                company.getOriginAddress(),
                company.getJibunAddress()
        );

        return new ResolvedCompanyAddress(
                normalizeMeaningful(company.getZipCode()),
                normalizeMeaningful(company.getDoName()),
                normalizeMeaningful(company.getSiName()),
                normalizeMeaningful(company.getGuName()),
                fallbackAddress,
                "",
                hasText(fallbackAddress)
        );
    }

    private String firstMeaningful(String... values) {
        if (values == null) {
            return "";
        }

        for (String value : values) {
            String normalized = normalizeMeaningful(value);
            if (hasText(normalized)) {
                return normalized;
            }
        }

        return "";
    }

    private String normalizeMeaningful(String value) {
        String normalized = safe(value);

        if (normalized.isBlank()) {
            return "";
        }

        if ("-".equals(normalized) || "–".equals(normalized) || "—".equals(normalized)) {
            return "";
        }

        return normalized;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isBlank();
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.trim().isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private record ResolvedCompanyAddress(
            String zipCode,
            String doName,
            String siName,
            String guName,
            String roadAddress,
            String detailAddress,
            boolean fallbackUsed
    ) {
    }

    private static class ExcelRawRow {
        Integer excelRowNumber;
        LocalDate preferredDeliveryDate;
        String companyRaw;
        String itemName;
        String size;
        int quantity;
        String adminMemo;
        String categoryName;
        String managerName;
        String deliveryHandlerName;
        int productCost;
        int supplyPrice;
        int vatAmount;
        int totalAmount;
        boolean standard;
        boolean mirrorCuttingProduct;
        OrderExcelRowKind kind;
    }
}
