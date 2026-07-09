package com.dev.HiddenBATHAuto.service.amount;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.PropertyAccessorFactory;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.dev.HiddenBATHAuto.dto.amount.AmountExcelColumnDto;
import com.dev.HiddenBATHAuto.dto.amount.AmountUploadResult;
import com.dev.HiddenBATHAuto.model.amount.AmountCustomerMaster;
import com.dev.HiddenBATHAuto.model.amount.AmountItemMaster;
import com.dev.HiddenBATHAuto.repository.amount.AmountCustomerMasterRepository;
import com.dev.HiddenBATHAuto.repository.amount.AmountItemMasterRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AmountExcelImportService {

    private static final String NO_CATEGORY = "분류없음";
    private static final String DEFAULT_ZERO = "0";
    private static final int HEADER_SCAN_ROW_LIMIT = 10;

    private final AmountItemMasterRepository itemRepository;
    private final AmountCustomerMasterRepository customerRepository;

    /**
     * 품목_얼마에요 전체 교체 업로드입니다.
     * 기존 원본 양식뿐 아니라 현재 사용 중인 동기화 양식처럼 일부 컬럼만 있는 엑셀도 허용합니다.
     * 엑셀에 없는 컬럼은 매출전표 매칭에 문제가 없도록 기본값으로 채웁니다.
     */
    @Transactional
    public AmountUploadResult replaceItems(MultipartFile file) {
        List<AmountItemMaster> items = parseItemMasters(file);
        assertNoDuplicateItemCodes(items);

        itemRepository.deleteAllInBatch();
        itemRepository.flush();
        itemRepository.saveAll(items);

        return AmountUploadResult.ok("품목_얼마에요 데이터가 전체 교체되었습니다.", items.size());
    }

    /**
     * 제품코드 기준 동기화 업로드입니다.
     * - 엑셀과 DB 양쪽에 모두 있는 제품코드: 기존 DB 값을 변경하지 않고 유지합니다.
     * - 엑셀에 있고 DB에 없는 제품코드: 기본값을 보정해서 신규 추가합니다.
     * - DB에 있고 엑셀에 없는 제품코드: 삭제합니다.
     */
    @Transactional
    public AmountUploadResult syncItems(MultipartFile file) {
        List<AmountItemMaster> uploadItems = parseItemMasters(file);
        if (uploadItems.isEmpty()) {
            throw new IllegalArgumentException("동기화할 품목 데이터가 없습니다.");
        }

        Map<String, AmountItemMaster> uploadByCode = new LinkedHashMap<>();
        List<String> duplicateUploadCodes = new ArrayList<>();
        for (AmountItemMaster uploadItem : uploadItems) {
            String code = normalizeCode(uploadItem.getItemCode());
            AmountItemMaster previous = uploadByCode.putIfAbsent(code, uploadItem);
            if (previous != null) {
                duplicateUploadCodes.add(code);
            }
        }
        if (!duplicateUploadCodes.isEmpty()) {
            throw new IllegalArgumentException("동기화 엑셀에 중복 제품코드가 있습니다: " + String.join(", ", duplicateUploadCodes));
        }

        List<AmountItemMaster> allExisting = itemRepository.findAll(Sort.by(Sort.Direction.ASC, "id"));
        Map<String, AmountItemMaster> existingByCode = new LinkedHashMap<>();
        List<AmountItemMaster> deleteTargets = new ArrayList<>();

        for (AmountItemMaster existing : allExisting) {
            String code = normalizeCode(existing.getItemCode());
            if (!StringUtils.hasText(code)) {
                deleteTargets.add(existing);
                continue;
            }
            AmountItemMaster previous = existingByCode.putIfAbsent(code, existing);
            if (previous != null) {
                // 같은 제품코드가 DB에 여러 건 있으면 가장 먼저 생성된 1건만 기준으로 남기고 나머지는 삭제합니다.
                deleteTargets.add(existing);
            }
        }

        Set<String> uploadCodes = uploadByCode.keySet();
        for (Map.Entry<String, AmountItemMaster> entry : existingByCode.entrySet()) {
            if (!uploadCodes.contains(entry.getKey())) {
                deleteTargets.add(entry.getValue());
            }
        }

        List<AmountItemMaster> createTargets = new ArrayList<>();
        int keptCount = 0;
        for (Map.Entry<String, AmountItemMaster> uploadEntry : uploadByCode.entrySet()) {
            if (existingByCode.containsKey(uploadEntry.getKey())) {
                keptCount++;
                continue;
            }
            createTargets.add(uploadEntry.getValue());
        }

        if (!deleteTargets.isEmpty()) {
            itemRepository.deleteAllInBatch(deleteTargets);
            itemRepository.flush();
        }
        if (!createTargets.isEmpty()) {
            itemRepository.saveAll(createTargets);
        }

        String message = "품목 동기화 완료: 기존유지 " + keptCount
                + "건, 신규추가 " + createTargets.size()
                + "건, 삭제 " + deleteTargets.size() + "건";
        return AmountUploadResult.ok(message, keptCount + createTargets.size());
    }

    /**
     * 거래처_얼마에요 전체 교체 업로드입니다.
     * 일부 컬럼이 빠진 엑셀도 거래처코드/거래처명만 있으면 기본값을 채워 저장합니다.
     */
    @Transactional
    public AmountUploadResult replaceCustomers(MultipartFile file) {
        List<AmountCustomerMaster> customers = parseCustomerMasters(file);
        assertNoDuplicateCustomerCodes(customers);

        customerRepository.deleteAllInBatch();
        customerRepository.flush();
        customerRepository.saveAll(customers);

        return AmountUploadResult.ok("거래처_얼마에요 데이터가 전체 교체되었습니다.", customers.size());
    }

    /**
     * 거래처코드 기준 동기화 업로드입니다.
     * - 엑셀과 DB 양쪽에 모두 있는 거래처코드: 기존 DB 값을 변경하지 않고 유지합니다.
     * - 엑셀에 있고 DB에 없는 거래처코드: 기본값을 보정해서 신규 추가합니다.
     * - DB에 있고 엑셀에 없는 거래처코드: 삭제합니다.
     */
    @Transactional
    public AmountUploadResult syncCustomers(MultipartFile file) {
        List<AmountCustomerMaster> uploadCustomers = parseCustomerMasters(file);
        if (uploadCustomers.isEmpty()) {
            throw new IllegalArgumentException("동기화할 거래처 데이터가 없습니다.");
        }

        Map<String, AmountCustomerMaster> uploadByCode = new LinkedHashMap<>();
        List<String> duplicateUploadCodes = new ArrayList<>();
        for (AmountCustomerMaster uploadCustomer : uploadCustomers) {
            String code = normalizeCode(uploadCustomer.getCustomerCode());
            AmountCustomerMaster previous = uploadByCode.putIfAbsent(code, uploadCustomer);
            if (previous != null) {
                duplicateUploadCodes.add(code);
            }
        }
        if (!duplicateUploadCodes.isEmpty()) {
            throw new IllegalArgumentException("동기화 엑셀에 중복 거래처코드가 있습니다: " + String.join(", ", duplicateUploadCodes));
        }

        List<AmountCustomerMaster> allExisting = customerRepository.findAll(Sort.by(Sort.Direction.ASC, "id"));
        Map<String, AmountCustomerMaster> existingByCode = new LinkedHashMap<>();
        List<AmountCustomerMaster> deleteTargets = new ArrayList<>();

        for (AmountCustomerMaster existing : allExisting) {
            String code = normalizeCode(existing.getCustomerCode());
            if (!StringUtils.hasText(code)) {
                deleteTargets.add(existing);
                continue;
            }
            AmountCustomerMaster previous = existingByCode.putIfAbsent(code, existing);
            if (previous != null) {
                deleteTargets.add(existing);
            }
        }

        Set<String> uploadCodes = uploadByCode.keySet();
        for (Map.Entry<String, AmountCustomerMaster> entry : existingByCode.entrySet()) {
            if (!uploadCodes.contains(entry.getKey())) {
                deleteTargets.add(entry.getValue());
            }
        }

        List<AmountCustomerMaster> createTargets = new ArrayList<>();
        int keptCount = 0;
        for (Map.Entry<String, AmountCustomerMaster> uploadEntry : uploadByCode.entrySet()) {
            if (existingByCode.containsKey(uploadEntry.getKey())) {
                keptCount++;
                continue;
            }
            createTargets.add(uploadEntry.getValue());
        }

        if (!deleteTargets.isEmpty()) {
            customerRepository.deleteAllInBatch(deleteTargets);
            customerRepository.flush();
        }
        if (!createTargets.isEmpty()) {
            customerRepository.saveAll(createTargets);
        }

        String message = "거래처 동기화 완료: 기존유지 " + keptCount
                + "건, 신규추가 " + createTargets.size()
                + "건, 삭제 " + deleteTargets.size() + "건";
        return AmountUploadResult.ok(message, keptCount + createTargets.size());
    }

    private List<AmountItemMaster> parseItemMasters(MultipartFile file) {
        validateExcelFile(file);

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                throw new IllegalArgumentException("첫 번째 시트를 찾을 수 없습니다.");
            }

            DataFormatter formatter = new DataFormatter();
            HeaderLocation header = resolveBestHeader(sheet, formatter, MasterType.ITEM);

            List<AmountItemMaster> result = new ArrayList<>();
            int blankCheckColumnSize = Math.max(1, header.lastCellNum());
            for (int rowIndex = header.rowIndex() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null || isBlankRow(row, blankCheckColumnSize, formatter)) {
                    continue;
                }
                result.add(parseItemMaster(row, header, formatter, rowIndex + 1));
            }
            return result;
        } catch (IOException e) {
            throw new IllegalStateException("엑셀 파일을 읽는 중 오류가 발생했습니다.", e);
        } catch (Exception e) {
            if (e instanceof IllegalArgumentException) {
                throw (IllegalArgumentException) e;
            }
            throw new IllegalStateException("품목 엑셀 분석 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }

    private AmountItemMaster parseItemMaster(Row row, HeaderLocation header, DataFormatter formatter, int rowNumber) {
        int itemCodeIndex = findHeaderIndex(header, "제품코드", "품목코드", "상품코드", "코드");
        int itemNameIndex = findHeaderIndex(header, "매칭품목명", "매칭 품목명", "품목명", "제품명", "상품명");

        String itemCode = normalizeCode(readCell(row, itemCodeIndex, formatter));
        String itemName = safe(readCell(row, itemNameIndex, formatter));
        if (!StringUtils.hasText(itemCode)) {
            throw new IllegalArgumentException(rowNumber + "행 제품코드가 비어 있습니다.");
        }
        if (!StringUtils.hasText(itemName)) {
            throw new IllegalArgumentException(rowNumber + "행 품목명이 비어 있습니다. 제품코드=" + itemCode);
        }

        AmountItemMaster item = new AmountItemMaster();
        BeanWrapper wrapper = PropertyAccessorFactory.forBeanPropertyAccess(item);
        List<String> memos = new ArrayList<>();

        item.setItemCode(itemCode);
        item.setItemName(itemName);

        setStringPropertyIfPresent(wrapper, row, header, formatter, "division", "구분");
        setStringPropertyIfPresent(wrapper, row, header, formatter, "purchasePrice", "매입단가", "규격매입단가");
        setStringPropertyIfPresent(wrapper, row, header, formatter, "salesPrice", "매출단가", "규격매출단가");
        setStringPropertyIfPresent(wrapper, row, header, formatter, "openingStockQty", "기초재고량");
        setStringPropertyIfPresent(wrapper, row, header, formatter, "openingStockUnitPrice", "기초재고단가");
        setStringPropertyIfPresent(wrapper, row, header, formatter, "unit", "단위");
        setStringPropertyIfPresent(wrapper, row, header, formatter, "barcode", "바코드");
        setStringPropertyIfPresent(wrapper, row, header, formatter, "brandName", "브랜드명", "브랜드");
        setStringPropertyIfPresent(wrapper, row, header, formatter, "modelName", "모델명", "모델");
        setStringPropertyIfPresent(wrapper, row, header, formatter, "taxType", "과세구분");
        setStringPropertyIfPresent(wrapper, row, header, formatter, "itemRegisteredDate", "품목등록일자", "등록일자");
        setStringPropertyIfPresent(wrapper, row, header, formatter, "liquorItemYn", "주류품목여부");
        setStringPropertyIfPresent(wrapper, row, header, formatter, "usageType", "용도구분");
        setStringPropertyIfPresent(wrapper, row, header, formatter, "liquorType", "주종구분");
        setStringPropertyIfPresent(wrapper, row, header, formatter, "dedicatedWarehouseNo", "전용창고번호");
        setStringPropertyIfPresent(wrapper, row, header, formatter, "purchaseBaseQty", "매입기준수량");
        setStringPropertyIfPresent(wrapper, row, header, formatter, "properStock", "적정재고");
        setStringPropertyIfPresent(wrapper, row, header, formatter, "outsourceProductionPrice", "외주생산단가");
        setStringPropertyIfPresent(wrapper, row, header, formatter, "grade1Price", "1등급가");
        setStringPropertyIfPresent(wrapper, row, header, formatter, "grade1Qty", "1등급수량");
        setStringPropertyIfPresent(wrapper, row, header, formatter, "grade2Price", "2등급가");
        setStringPropertyIfPresent(wrapper, row, header, formatter, "grade2Qty", "2등급수량");
        setStringPropertyIfPresent(wrapper, row, header, formatter, "grade3Price", "3등급가");
        setStringPropertyIfPresent(wrapper, row, header, formatter, "grade3Qty", "3등급수량");
        setStringPropertyIfPresent(wrapper, row, header, formatter, "grade4Price", "4등급가");
        setStringPropertyIfPresent(wrapper, row, header, formatter, "grade4Qty", "4등급수량");
        setStringPropertyIfPresent(wrapper, row, header, formatter, "grade5Price", "5등급가");
        setStringPropertyIfPresent(wrapper, row, header, formatter, "grade5Qty", "5등급수량");
        setStringPropertyIfPresent(wrapper, row, header, formatter, "useStatus", "사용상태");
        setStringPropertyIfPresent(wrapper, row, header, formatter, "stockCalculationYn", "재고계산여부");
        setStringPropertyIfPresent(wrapper, row, header, formatter, "originDisplayType", "원산지구분표시");
        setStringPropertyIfPresent(wrapper, row, header, formatter, "procurementIdentifierCode", "조달청식별코드");
        setStringPropertyIfPresent(wrapper, row, header, formatter, "note", "참고사항", "관리자남김말", "관리자 남김말", "비고");
        setStringPropertyIfPresent(wrapper, row, header, formatter, "udiUseYn", "UDI사용여부", "UDI 사용여부");

        int categoryIndex = findHeaderIndex(header, "대분류", "대분류명", "카테고리", "분류명");
        int fallbackCategoryIndex = findHeaderIndex(header, "비규격분류", "대분류추가용", "대분류없는것들추가용", "대분류 누락 추가용", "추가분류", "추가용분류", "분류");
        int middleCategoryIndex = findHeaderIndex(header, "중분류", "중분류명", "소분류");
        int sizeIndex = findHeaderIndex(header, "사이즈", "크기", "제품사이즈", "제품규격", "규격사이즈", "규격");
        int standardIndex = findHeaderIndex(header, "규격여부", "규격/비규격", "규격비규격", "규격비규격여부", "규격구분", "규격비규격구분", "표준여부", "비규격여부");
        int mirrorIndex = findHeaderIndex(header, "거울재단", "거울재단여부", "재단", "재단여부", "거울재단필요여부");

        String rawCategory = readCell(row, categoryIndex, formatter);
        String rawFallbackCategory = readCell(row, fallbackCategoryIndex, formatter);
        String rawMiddleCategory = readCell(row, middleCategoryIndex, formatter);
        String rawSize = readCell(row, sizeIndex, formatter);
        String rawStandard = readCell(row, standardIndex, formatter);
        String rawMirror = readCell(row, mirrorIndex, formatter);

        item.setCategoryName(normalizeCategory(rawCategory, rawFallbackCategory, memos));
        item.setMiddleCategoryName(normalizeMiddleCategory(rawMiddleCategory, memos));
        item.setSpecification(safe(rawSize));

        boolean fallbackIsNonStandardGroup = headerTextContains(header, fallbackCategoryIndex, "비규격");
        boolean standardHeaderIsNonStandardFlag = headerTextContains(header, standardIndex, "비규격여부");
        boolean inferredNonStandard = AmountTextNormalizer.compact(itemName).contains("비규격")
                || (fallbackIsNonStandardGroup && StringUtils.hasText(rawFallbackCategory));
        item.setStandard(parseStandardValue(rawStandard, inferredNonStandard, standardHeaderIsNonStandardFlag, rowNumber, memos));
        item.setMirrorCuttingProduct(parseMirrorCuttingProduct(rawMirror));

        applyItemDefaults(item, memos);
        refreshItemSearchText(item);
        return item;
    }

    private List<AmountCustomerMaster> parseCustomerMasters(MultipartFile file) {
        validateExcelFile(file);

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                throw new IllegalArgumentException("첫 번째 시트를 찾을 수 없습니다.");
            }

            DataFormatter formatter = new DataFormatter();
            HeaderLocation header = resolveBestHeader(sheet, formatter, MasterType.CUSTOMER);

            List<AmountCustomerMaster> result = new ArrayList<>();
            int blankCheckColumnSize = Math.max(1, header.lastCellNum());
            for (int rowIndex = header.rowIndex() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null || isBlankRow(row, blankCheckColumnSize, formatter)) {
                    continue;
                }
                result.add(parseCustomerMaster(row, header, formatter, rowIndex + 1));
            }
            return result;
        } catch (IOException e) {
            throw new IllegalStateException("엑셀 파일을 읽는 중 오류가 발생했습니다.", e);
        } catch (Exception e) {
            if (e instanceof IllegalArgumentException) {
                throw (IllegalArgumentException) e;
            }
            throw new IllegalStateException("거래처 엑셀 분석 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }

    private AmountCustomerMaster parseCustomerMaster(Row row, HeaderLocation header, DataFormatter formatter, int rowNumber) {
        int customerCodeIndex = findHeaderIndex(header, "거래처코드", "고객코드", "customerCode", "코드");
        int customerNameIndex = findHeaderIndex(header, "거래처명", "고객명", "customerName", "상호명");

        String customerCode = normalizeCode(readCell(row, customerCodeIndex, formatter));
        String customerName = safe(readCell(row, customerNameIndex, formatter));
        if (!StringUtils.hasText(customerCode)) {
            throw new IllegalArgumentException(rowNumber + "행 거래처코드가 비어 있습니다.");
        }
        if (!StringUtils.hasText(customerName)) {
            throw new IllegalArgumentException(rowNumber + "행 거래처명이 비어 있습니다. 거래처코드=" + customerCode);
        }

        AmountCustomerMaster customer = new AmountCustomerMaster();
        BeanWrapper wrapper = PropertyAccessorFactory.forBeanPropertyAccess(customer);
        customer.setCustomerCode(customerCode);
        customer.setCustomerName(customerName);

        for (AmountExcelColumnDto column : AmountExcelColumnDefinition.CUSTOMER_COLUMNS) {
            String field = column.field();
            if ("customerCode".equals(field) || "customerName".equals(field)) {
                continue;
            }
            setStringPropertyIfPresent(wrapper, row, header, formatter, field, customerAliases(field, column.header()));
        }

        applyCustomerDefaults(customer);
        refreshCustomerSearchText(customer);
        return customer;
    }

    private void applyItemDefaults(AmountItemMaster item, List<String> memos) {
        defaultText(item::getDivision, item::setDivision, "1", null);
        defaultText(item::getPurchasePrice, item::setPurchasePrice, DEFAULT_ZERO, null);
        defaultText(item::getSalesPrice, item::setSalesPrice, DEFAULT_ZERO, null);
        defaultText(item::getOpeningStockQty, item::setOpeningStockQty, DEFAULT_ZERO, null);
        defaultText(item::getOpeningStockUnitPrice, item::setOpeningStockUnitPrice, DEFAULT_ZERO, null);
        defaultText(item::getUnit, item::setUnit, "EA", null);
        defaultText(item::getCategoryName, item::setCategoryName, NO_CATEGORY, memos, "대분류 누락: " + NO_CATEGORY + "으로 대체");
        defaultText(item::getMiddleCategoryName, item::setMiddleCategoryName, NO_CATEGORY, memos, "중분류 누락: " + NO_CATEGORY + "으로 대체");
        defaultText(item::getSpecification, item::setSpecification, "", null);
        defaultText(item::getTaxType, item::setTaxType, "과세", null);
        defaultText(item::getLiquorItemYn, item::setLiquorItemYn, "N", null);
        defaultText(item::getPurchaseBaseQty, item::setPurchaseBaseQty, DEFAULT_ZERO, null);
        defaultText(item::getProperStock, item::setProperStock, DEFAULT_ZERO, null);
        defaultText(item::getOutsourceProductionPrice, item::setOutsourceProductionPrice, DEFAULT_ZERO, null);
        defaultText(item::getGrade1Price, item::setGrade1Price, DEFAULT_ZERO, null);
        defaultText(item::getGrade1Qty, item::setGrade1Qty, DEFAULT_ZERO, null);
        defaultText(item::getGrade2Price, item::setGrade2Price, DEFAULT_ZERO, null);
        defaultText(item::getGrade2Qty, item::setGrade2Qty, DEFAULT_ZERO, null);
        defaultText(item::getGrade3Price, item::setGrade3Price, DEFAULT_ZERO, null);
        defaultText(item::getGrade3Qty, item::setGrade3Qty, DEFAULT_ZERO, null);
        defaultText(item::getGrade4Price, item::setGrade4Price, DEFAULT_ZERO, null);
        defaultText(item::getGrade4Qty, item::setGrade4Qty, DEFAULT_ZERO, null);
        defaultText(item::getGrade5Price, item::setGrade5Price, DEFAULT_ZERO, null);
        defaultText(item::getGrade5Qty, item::setGrade5Qty, DEFAULT_ZERO, null);
        defaultText(item::getUseStatus, item::setUseStatus, "사용", null);
        defaultText(item::getStockCalculationYn, item::setStockCalculationYn, "N", null);
        defaultText(item::getOriginDisplayType, item::setOriginDisplayType, "미표시", null);
        defaultText(item::getUdiUseYn, item::setUdiUseYn, "N", null);

        if (!memos.isEmpty()) {
            item.setSyncMemo(String.join(" / ", memos));
        } else if (!StringUtils.hasText(item.getSyncMemo())) {
            item.setSyncMemo("엑셀 기준 정상 반영");
        }
    }

    private void applyCustomerDefaults(AmountCustomerMaster customer) {
        defaultText(customer::getDivision, customer::setDivision, "1", null);
        defaultText(customer::getTradeType, customer::setTradeType, "매출", null);
        defaultText(customer::getBusinessName, customer::setBusinessName, customer.getCustomerName(), null);
        defaultText(customer::getTaxType, customer::setTaxType, "과세", null);
        defaultText(customer::getDedicatedItemUseYn, customer::setDedicatedItemUseYn, "N", null);
        defaultText(customer::getTransactionItemUseYn, customer::setTransactionItemUseYn, "N", null);
        defaultText(customer::getSalesPriceType, customer::setSalesPriceType, "기본", null);
        defaultText(customer::getFixedRateYn, customer::setFixedRateYn, "N", null);
        defaultText(customer::getFixedRatePercent, customer::setFixedRatePercent, DEFAULT_ZERO, null);
        defaultText(customer::getUseStatus, customer::setUseStatus, "사용", null);
        defaultText(customer::getReportPrintYn, customer::setReportPrintYn, "Y", null);
        defaultText(customer::getSmsSendYn, customer::setSmsSendYn, "N", null);
        defaultText(customer::getFaxSendYn, customer::setFaxSendYn, "N", null);
    }

    private void defaultText(Supplier<String> getter, TextSetter setter, String defaultValue, List<String> memos) {
        defaultText(getter, setter, defaultValue, memos, null);
    }

    private void defaultText(Supplier<String> getter, TextSetter setter, String defaultValue, List<String> memos, String memo) {
        if (!StringUtils.hasText(getter.get())) {
            setter.set(defaultValue);
            if (memos != null && StringUtils.hasText(memo)) {
                memos.add(memo);
            }
        }
    }

    private void setStringPropertyIfPresent(BeanWrapper wrapper,
                                            Row row,
                                            HeaderLocation header,
                                            DataFormatter formatter,
                                            String field,
                                            String... aliases) {
        int index = findHeaderIndex(header, aliases);
        if (index < 0) {
            return;
        }
        String value = safe(readCell(row, index, formatter));
        if (StringUtils.hasText(value)) {
            wrapper.setPropertyValue(field, value);
        }
    }

    private String normalizeCategory(String rawCategory, String fallbackCategory, List<String> memos) {
        if (StringUtils.hasText(rawCategory)) {
            return rawCategory.trim();
        }
        if (StringUtils.hasText(fallbackCategory)) {
            String value = fallbackCategory.trim();
            memos.add("대분류 누락: 비규격/추가분류 열 값[" + value + "]으로 대체");
            return value;
        }
        memos.add("대분류 누락: " + NO_CATEGORY + "으로 대체");
        return NO_CATEGORY;
    }

    private String normalizeMiddleCategory(String rawMiddleCategory, List<String> memos) {
        String value = safe(rawMiddleCategory);
        if (!StringUtils.hasText(value) || isNoCategoryToken(value)) {
            memos.add("중분류 누락/X: " + NO_CATEGORY + "으로 대체");
            return NO_CATEGORY;
        }
        return value;
    }

    private boolean parseStandardValue(String value,
                                       boolean inferredNonStandard,
                                       boolean nonStandardFlagHeader,
                                       int rowNumber,
                                       List<String> memos) {
        String compact = AmountTextNormalizer.compact(value);
        if (!StringUtils.hasText(compact)) {
            if (inferredNonStandard) {
                memos.add("비규격 분류/품목명 기준: 비규격으로 대체");
                return false;
            }
            memos.add("규격/비규격 누락: 규격으로 대체");
            return true;
        }

        if (nonStandardFlagHeader) {
            if (compact.contains("비규격") || compact.equals("true") || compact.equals("y") || compact.equals("yes")
                    || compact.equals("1") || compact.equals("o") || compact.equals("ㅇ")) {
                return false;
            }
            if (compact.contains("규격") || compact.equals("false") || compact.equals("n") || compact.equals("no")
                    || compact.equals("0") || compact.equals("x")) {
                return true;
            }
        }

        if (compact.contains("비규격") || compact.equals("false") || compact.equals("n") || compact.equals("no") || compact.equals("0")) {
            return false;
        }
        if (compact.contains("규격") || compact.equals("true") || compact.equals("y") || compact.equals("yes") || compact.equals("1") || compact.equals("o") || compact.equals("ㅇ")) {
            return true;
        }
        throw new IllegalArgumentException(rowNumber + "행 규격/비규격 여부 값을 해석할 수 없습니다. 값=" + value);
    }

    private boolean parseMirrorCuttingProduct(String value) {
        String compact = AmountTextNormalizer.compact(value);
        if (!StringUtils.hasText(compact)) {
            return false;
        }
        if (compact.equals("x") || compact.equals("false") || compact.equals("n") || compact.equals("no") || compact.equals("0")
                || compact.contains("불필요") || compact.contains("필요없") || compact.contains("없음")) {
            return false;
        }
        return compact.equals("ㅇ") || compact.equals("o") || compact.equals("y") || compact.equals("yes") || compact.equals("true")
                || compact.equals("1") || compact.contains("재단") || compact.contains("필요");
    }

    private boolean isNoCategoryToken(String value) {
        String compact = AmountTextNormalizer.compact(value);
        return !StringUtils.hasText(compact)
                || "x".equals(compact)
                || "xx".equals(compact)
                || "없음".equals(compact)
                || "무".equals(compact)
                || "분류없음".equals(compact)
                || "-".equals(value.trim());
    }

    private HeaderLocation resolveBestHeader(Sheet sheet, DataFormatter formatter, MasterType masterType) {
        HeaderLocation best = null;
        int bestScore = -1;
        int maxRow = Math.min(sheet.getLastRowNum(), HEADER_SCAN_ROW_LIMIT - 1);
        for (int rowIndex = 0; rowIndex <= maxRow; rowIndex++) {
            HeaderLocation candidate = buildHeaderLocation(sheet, rowIndex, formatter);
            int score = scoreHeader(candidate, masterType);
            if (score > bestScore) {
                best = candidate;
                bestScore = score;
            }
        }

        if (best == null || !hasRequiredHeader(best, masterType)) {
            throw new IllegalArgumentException(masterType.title() + " 엑셀 헤더를 찾을 수 없습니다. "
                    + "첫 " + HEADER_SCAN_ROW_LIMIT + "행 안에 필수 컬럼이 있어야 합니다. 실제 헤더 후보: "
                    + formatScannedHeaders(sheet, formatter));
        }
        return best;
    }

    private HeaderLocation buildHeaderLocation(Sheet sheet, int rowIndex, DataFormatter formatter) {
        Row headerRow = sheet.getRow(rowIndex);
        Row groupRow = rowIndex > 0 ? sheet.getRow(rowIndex - 1) : null;
        int lastCellNum = headerRow == null ? 0 : Math.max(0, headerRow.getLastCellNum());
        if (groupRow != null) {
            lastCellNum = Math.max(lastCellNum, Math.max(0, groupRow.getLastCellNum()));
        }

        Map<String, List<HeaderCell>> cellsByKey = new LinkedHashMap<>();
        for (int index = 0; index < lastCellNum; index++) {
            String headerName = readCell(headerRow, index, formatter);
            String groupName = readCell(groupRow, index, formatter);
            if (!StringUtils.hasText(headerName)) {
                continue;
            }
            HeaderCell cell = new HeaderCell(index, headerName, groupName);
            addHeaderKey(cellsByKey, AmountTextNormalizer.compact(headerName), cell);
            if (StringUtils.hasText(groupName)) {
                addHeaderKey(cellsByKey, AmountTextNormalizer.compact(groupName + headerName), cell);
            }
        }
        return new HeaderLocation(rowIndex, lastCellNum, cellsByKey);
    }

    private void addHeaderKey(Map<String, List<HeaderCell>> cellsByKey, String key, HeaderCell cell) {
        if (!StringUtils.hasText(key)) {
            return;
        }
        cellsByKey.computeIfAbsent(key, ignored -> new ArrayList<>()).add(cell);
    }

    private int scoreHeader(HeaderLocation header, MasterType masterType) {
        if (masterType == MasterType.ITEM) {
            int score = 0;
            score += findHeaderIndex(header, "제품코드", "품목코드", "상품코드", "코드") >= 0 ? 50 : 0;
            score += findHeaderIndex(header, "매칭품목명", "매칭 품목명", "품목명", "제품명", "상품명") >= 0 ? 50 : 0;
            score += findHeaderIndex(header, "대분류", "대분류명", "카테고리", "분류명") >= 0 ? 10 : 0;
            score += findHeaderIndex(header, "중분류", "중분류명", "소분류") >= 0 ? 10 : 0;
            score += findHeaderIndex(header, "사이즈", "크기", "제품사이즈", "제품규격", "규격사이즈", "규격") >= 0 ? 5 : 0;
            score += findHeaderIndex(header, "매입단가") >= 0 ? 5 : 0;
            score += findHeaderIndex(header, "매출단가") >= 0 ? 5 : 0;
            return score;
        }

        int score = 0;
        score += findHeaderIndex(header, "거래처코드", "고객코드", "customerCode", "코드") >= 0 ? 50 : 0;
        score += findHeaderIndex(header, "거래처명", "고객명", "customerName", "상호명") >= 0 ? 50 : 0;
        score += findHeaderIndex(header, "사업자(주민)번호", "사업자번호") >= 0 ? 10 : 0;
        score += findHeaderIndex(header, "전화", "휴대폰") >= 0 ? 5 : 0;
        return score;
    }

    private boolean hasRequiredHeader(HeaderLocation header, MasterType masterType) {
        if (masterType == MasterType.ITEM) {
            return findHeaderIndex(header, "제품코드", "품목코드", "상품코드", "코드") >= 0
                    && findHeaderIndex(header, "매칭품목명", "매칭 품목명", "품목명", "제품명", "상품명") >= 0;
        }
        return findHeaderIndex(header, "거래처코드", "고객코드", "customerCode", "코드") >= 0
                && findHeaderIndex(header, "거래처명", "고객명", "customerName", "상호명") >= 0;
    }

    private int findHeaderIndex(HeaderLocation header, String... aliases) {
        if (header == null || aliases == null) {
            return -1;
        }
        for (String alias : aliases) {
            String key = AmountTextNormalizer.compact(alias);
            if (!StringUtils.hasText(key)) {
                continue;
            }
            List<HeaderCell> cells = header.cellsByKey().get(key);
            if (cells == null || cells.isEmpty()) {
                continue;
            }
            for (HeaderCell cell : cells) {
                if (!isExcludedHeaderGroup(cell.groupName())) {
                    return cell.index();
                }
            }
        }
        return -1;
    }

    private boolean isExcludedHeaderGroup(String groupName) {
        String compact = AmountTextNormalizer.compact(groupName);
        return compact.contains("미사용") || compact.contains("매칭조건");
    }

    private boolean headerTextContains(HeaderLocation header, int index, String token) {
        HeaderCell cell = findHeaderCell(header, index);
        if (cell == null) {
            return false;
        }
        String compactHeader = AmountTextNormalizer.compact(cell.groupName() + " " + cell.headerName());
        String compactToken = AmountTextNormalizer.compact(token);
        return StringUtils.hasText(compactToken) && compactHeader.contains(compactToken);
    }

    private HeaderCell findHeaderCell(HeaderLocation header, int index) {
        if (header == null || index < 0) {
            return null;
        }
        for (List<HeaderCell> cells : header.cellsByKey().values()) {
            for (HeaderCell cell : cells) {
                if (cell.index() == index) {
                    return cell;
                }
            }
        }
        return null;
    }

    private String formatScannedHeaders(Sheet sheet, DataFormatter formatter) {
        List<String> rows = new ArrayList<>();
        int maxRow = Math.min(sheet.getLastRowNum(), HEADER_SCAN_ROW_LIMIT - 1);
        for (int rowIndex = 0; rowIndex <= maxRow; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }
            List<String> headers = new ArrayList<>();
            int lastCellNum = Math.max(0, row.getLastCellNum());
            for (int index = 0; index < lastCellNum; index++) {
                String headerName = readCell(row, index, formatter);
                if (StringUtils.hasText(headerName)) {
                    headers.add((index + 1) + "열=" + headerName);
                }
            }
            if (!headers.isEmpty()) {
                rows.add((rowIndex + 1) + "행[" + String.join(", ", headers) + "]");
            }
        }
        return String.join(" / ", rows);
    }

    private String[] customerAliases(String field, String defaultHeader) {
        if ("customerCode".equals(field)) {
            return new String[]{defaultHeader, "거래처코드", "고객코드", "코드"};
        }
        if ("customerName".equals(field)) {
            return new String[]{defaultHeader, "거래처명", "고객명"};
        }
        if ("businessNo".equals(field)) {
            return new String[]{defaultHeader, "사업자번호", "사업자 주민 번호", "사업자(주민)번호"};
        }
        return new String[]{defaultHeader};
    }

    private void assertNoDuplicateItemCodes(List<AmountItemMaster> items) {
        assertNoDuplicateCodes(items, AmountItemMaster::getItemCode, "제품코드");
    }

    private void assertNoDuplicateCustomerCodes(List<AmountCustomerMaster> customers) {
        assertNoDuplicateCodes(customers, AmountCustomerMaster::getCustomerCode, "거래처코드");
    }

    private <T> void assertNoDuplicateCodes(List<T> rows, CodeGetter<T> getter, String logicalName) {
        Map<String, Integer> firstRowByCode = new LinkedHashMap<>();
        List<String> duplicates = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            String code = normalizeCode(getter.get(rows.get(i)));
            Integer previousRow = firstRowByCode.putIfAbsent(code, i + 2);
            if (previousRow != null) {
                duplicates.add(code + "(데이터 순번 " + previousRow + ", " + (i + 2) + ")");
            }
        }
        if (!duplicates.isEmpty()) {
            throw new IllegalArgumentException("엑셀에 중복 " + logicalName + "가 있습니다: " + String.join(", ", duplicates));
        }
    }

    private void validateExcelFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("업로드할 엑셀 파일을 선택해 주세요.");
        }
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (!filename.endsWith(".xlsx") && !filename.endsWith(".xls")) {
            throw new IllegalArgumentException("엑셀 파일(.xlsx 또는 .xls)만 업로드할 수 있습니다.");
        }
    }

    private boolean isBlankRow(Row row, int columnSize, DataFormatter formatter) {
        return IntStream.range(0, columnSize)
                .mapToObj(i -> readCell(row, i, formatter))
                .allMatch(String::isBlank);
    }

    private String readCell(Row row, int index, DataFormatter formatter) {
        if (row == null || index < 0) {
            return "";
        }
        Cell cell = row.getCell(index, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) {
            return "";
        }
        return formatter.formatCellValue(cell).trim();
    }

    private String normalizeCode(String value) {
        return safe(value).replace("\u00A0", " ").trim();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private void refreshItemSearchText(AmountItemMaster item) {
        if (item == null) {
            return;
        }
        item.setSearchText(AmountTextNormalizer.joinForSearch(
                item.getDivision(),
                item.getItemCode(),
                item.getItemName(),
                item.getCategoryName(),
                item.getMiddleCategoryName(),
                item.getSpecification(),
                item.isStandard() ? "규격" : "비규격",
                item.isMirrorCuttingProduct() ? "거울재단 재단필요" : "거울재단없음",
                item.getSalesPrice(),
                item.getPurchasePrice(),
                item.getUnit(),
                item.getBarcode(),
                item.getBrandName(),
                item.getModelName(),
                item.getNote(),
                item.getSyncMemo()
        ));
    }

    private void refreshCustomerSearchText(AmountCustomerMaster customer) {
        if (customer == null) {
            return;
        }
        customer.setSearchText(AmountTextNormalizer.joinForSearch(
                customer.getCustomerCode(),
                customer.getCustomerName(),
                customer.getBusinessName(),
                customer.getBusinessNo(),
                customer.getCeoName(),
                customer.getTelephone(),
                customer.getMobile(),
                customer.getWorkplaceAddress1(),
                customer.getWorkplaceAddress2(),
                customer.getActualAddress1(),
                customer.getActualAddress2(),
                customer.getManager1Name(),
                customer.getManager1Mobile(),
                customer.getManager1Email(),
                customer.getUseStatus(),
                customer.getNote()
        ));
    }

    private enum MasterType {
        ITEM("품목"),
        CUSTOMER("거래처");

        private final String title;

        MasterType(String title) {
            this.title = title;
        }

        private String title() {
            return title;
        }
    }

    private record HeaderLocation(int rowIndex, int lastCellNum, Map<String, List<HeaderCell>> cellsByKey) {
    }

    private record HeaderCell(int index, String headerName, String groupName) {
    }

    @FunctionalInterface
    private interface TextSetter {
        void set(String value);
    }

    @FunctionalInterface
    private interface CodeGetter<T> {
        String get(T row);
    }
}
