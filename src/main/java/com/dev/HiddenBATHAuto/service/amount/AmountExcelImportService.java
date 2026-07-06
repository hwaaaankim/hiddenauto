package com.dev.HiddenBATHAuto.service.amount;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.PropertyAccessorFactory;
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

    private final AmountItemMasterRepository itemRepository;
    private final AmountCustomerMasterRepository customerRepository;

    /**
     * 기존 품목_얼마에요 원본 양식 전체 교체 업로드입니다.
     * 새 동기화 컬럼은 기본값으로 저장해서 기존 원본 양식 업로드가 깨지지 않도록 유지합니다.
     */
    @Transactional
    public AmountUploadResult replaceItems(MultipartFile file) {
        List<AmountItemMaster> items = parse(file, AmountExcelColumnDefinition.ITEM_ORIGINAL_IMPORT_COLUMNS, AmountItemMaster::new);
        for (AmountItemMaster item : items) {
            normalizeLegacyItemDefaults(item);
            refreshItemSearchText(item);
        }
        itemRepository.deleteAllInBatch();
        itemRepository.flush();
        itemRepository.saveAll(items);
        return AmountUploadResult.ok("품목_얼마에요 데이터가 전체 교체되었습니다.", items.size());
    }

    /**
     * 동기화용 엑셀 업로드입니다.
     * 제품코드를 기준으로 기존 데이터를 갱신하고, 업로드 엑셀에 없는 제품코드는 DB에서 삭제합니다.
     */
    @Transactional
    public AmountUploadResult syncItems(MultipartFile file) {
        List<ItemSyncRow> rows = parseItemSyncRows(file);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("동기화할 품목 데이터가 없습니다.");
        }

        Map<String, ItemSyncRow> uploadByCode = new LinkedHashMap<>();
        List<String> duplicateCodes = new ArrayList<>();
        for (ItemSyncRow row : rows) {
            ItemSyncRow previous = uploadByCode.putIfAbsent(row.itemCode(), row);
            if (previous != null) {
                duplicateCodes.add(row.itemCode() + "(행 " + previous.rowNumber() + ", " + row.rowNumber() + ")");
            }
        }
        if (!duplicateCodes.isEmpty()) {
            throw new IllegalArgumentException("동기화 엑셀에 중복 제품코드가 있습니다: " + String.join(", ", duplicateCodes));
        }

        List<AmountItemMaster> allExisting = itemRepository.findAll(SortById.asc());
        Map<String, AmountItemMaster> existingByCode = new LinkedHashMap<>();
        List<AmountItemMaster> deleteTargets = new ArrayList<>();

        for (AmountItemMaster existing : allExisting) {
            String code = normalizeCode(existing.getItemCode());
            if (!StringUtils.hasText(code)) {
                deleteTargets.add(existing);
                continue;
            }
            AmountItemMaster duplicated = existingByCode.putIfAbsent(code, existing);
            if (duplicated != null) {
                deleteTargets.add(existing);
            }
        }

        Set<String> uploadCodes = uploadByCode.keySet();
        for (Map.Entry<String, AmountItemMaster> entry : existingByCode.entrySet()) {
            if (!uploadCodes.contains(entry.getKey())) {
                deleteTargets.add(entry.getValue());
            }
        }

        List<AmountItemMaster> saveTargets = new ArrayList<>();
        int createdCount = 0;
        int updatedCount = 0;
        for (ItemSyncRow row : uploadByCode.values()) {
            AmountItemMaster item = existingByCode.get(row.itemCode());
            if (item == null) {
                item = new AmountItemMaster();
                item.setItemCode(row.itemCode());
                createdCount++;
            } else {
                updatedCount++;
            }

            applySyncRow(item, row);
            refreshItemSearchText(item);
            saveTargets.add(item);
        }

        if (!deleteTargets.isEmpty()) {
            itemRepository.deleteAllInBatch(deleteTargets);
            itemRepository.flush();
        }
        itemRepository.saveAll(saveTargets);

        String message = "품목 동기화 완료: 저장/수정 " + saveTargets.size()
                + "건(신규 " + createdCount + "건, 기존수정 " + updatedCount + "건), 삭제 " + deleteTargets.size() + "건";
        return AmountUploadResult.ok(message, saveTargets.size());
    }

    @Transactional
    public AmountUploadResult replaceCustomers(MultipartFile file) {
        List<AmountCustomerMaster> customers = parse(file, AmountExcelColumnDefinition.CUSTOMER_COLUMNS, AmountCustomerMaster::new);
        customerRepository.deleteAllInBatch();
        customerRepository.flush();
        customerRepository.saveAll(customers);
        return AmountUploadResult.ok("거래처_얼마에요 데이터가 전체 교체되었습니다.", customers.size());
    }

    private void normalizeLegacyItemDefaults(AmountItemMaster item) {
        if (!StringUtils.hasText(item.getMiddleCategoryName())) {
            item.setMiddleCategoryName(NO_CATEGORY);
        }
        // 기존 원본 업로드에는 규격/비규격 구분이 없으므로 기본값은 규격으로 둡니다.
        item.setStandard(true);
        item.setMirrorCuttingProduct(false);
        item.setSyncMemo("기존 품목_얼마에요 원본 업로드: 중분류/규격구분/거울재단 정보는 동기화 엑셀 업로드로 보강 필요");
    }

    private void applySyncRow(AmountItemMaster item, ItemSyncRow row) {
        item.setItemCode(row.itemCode());
        item.setItemName(row.itemName());
        item.setCategoryName(row.categoryName());
        item.setMiddleCategoryName(row.middleCategoryName());
        item.setSpecification(row.size());
        item.setStandard(row.standard());
        item.setMirrorCuttingProduct(row.mirrorCuttingProduct());
        item.setSyncMemo(row.syncMemo());
    }

    private List<ItemSyncRow> parseItemSyncRows(MultipartFile file) {
        validateExcelFile(file);
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                throw new IllegalArgumentException("첫 번째 시트를 찾을 수 없습니다.");
            }

            DataFormatter formatter = new DataFormatter();
            ItemSyncHeader header = resolveItemSyncHeader(sheet.getRow(0), formatter);
            int blankCheckColumnSize = Math.max(1, maxItemSyncHeaderIndex(header) + 1);

            List<ItemSyncRow> rows = new ArrayList<>();
            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null || isBlankRow(row, blankCheckColumnSize, formatter)) {
                    continue;
                }

                String itemCode = normalizeCode(readCell(row, header.itemCodeIndex(), formatter));
                String itemName = safe(readCell(row, header.itemNameIndex(), formatter));
                if (!StringUtils.hasText(itemCode)) {
                    throw new IllegalArgumentException((rowIndex + 1) + "행 제품코드가 비어 있습니다.");
                }
                if (!StringUtils.hasText(itemName)) {
                    throw new IllegalArgumentException((rowIndex + 1) + "행 품목명이 비어 있습니다. 제품코드=" + itemCode);
                }

                String rawCategory = safe(readCell(row, header.categoryIndex(), formatter));
                String rawMiddleCategory = safe(readCell(row, header.middleCategoryIndex(), formatter));
                String size = safe(readCell(row, header.sizeIndex(), formatter));
                String rawStandard = safe(readCell(row, header.standardIndex(), formatter));
                String rawMirror = safe(readCell(row, header.mirrorCuttingIndex(), formatter));
                String fallbackCategory = safe(readCell(row, header.fallbackCategoryIndex(), formatter));

                List<String> memos = new ArrayList<>();
                String categoryName = normalizeCategory(rawCategory, fallbackCategory, memos);
                String middleCategoryName = normalizeMiddleCategory(rawMiddleCategory, memos);
                boolean standard = parseStandard(rawStandard, rowIndex + 1, memos);
                boolean mirrorCuttingProduct = parseMirrorCuttingProduct(rawMirror);

                rows.add(new ItemSyncRow(
                        rowIndex + 1,
                        itemCode,
                        itemName,
                        categoryName,
                        middleCategoryName,
                        size,
                        standard,
                        mirrorCuttingProduct,
                        String.join(" / ", memos)
                ));
            }
            return rows;
        } catch (IOException e) {
            throw new IllegalStateException("엑셀 파일을 읽는 중 오류가 발생했습니다.", e);
        } catch (Exception e) {
            if (e instanceof IllegalArgumentException) {
                throw (IllegalArgumentException) e;
            }
            throw new IllegalStateException("동기화 엑셀 분석 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }

    private String normalizeCategory(String rawCategory, String fallbackCategory, List<String> memos) {
        if (StringUtils.hasText(rawCategory)) {
            return rawCategory.trim();
        }
        if (StringUtils.hasText(fallbackCategory)) {
            String value = fallbackCategory.trim();
            memos.add("대분류 누락: 추가용 열 값[" + value + "]으로 대체");
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

    private boolean parseStandard(String value, int rowNumber, List<String> memos) {
        String compact = AmountTextNormalizer.compact(value);
        if (!StringUtils.hasText(compact)) {
            memos.add("규격/비규격 누락: 규격으로 대체");
            return true;
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

    private ItemSyncHeader resolveItemSyncHeader(Row headerRow, DataFormatter formatter) {
        if (headerRow == null) {
            throw new IllegalArgumentException("동기화 엑셀 1행 헤더가 없습니다.");
        }

        Map<String, Integer> headerIndexByCompactName = new LinkedHashMap<>();
        int lastCellNum = Math.max(0, headerRow.getLastCellNum());
        for (int index = 0; index < lastCellNum; index++) {
            String headerName = readCell(headerRow, index, formatter);
            String compactHeaderName = AmountTextNormalizer.compact(headerName);
            if (StringUtils.hasText(compactHeaderName)) {
                headerIndexByCompactName.putIfAbsent(compactHeaderName, index);
            }
        }

        List<String> errors = new ArrayList<>();
        int itemCodeIndex = findItemSyncHeaderIndex(headerIndexByCompactName, errors, "제품코드", true,
                "제품코드", "품목코드", "상품코드", "코드");
        int itemNameIndex = findItemSyncHeaderIndex(headerIndexByCompactName, errors, "품목명", true,
                "품목명", "제품명", "상품명");
        int categoryIndex = findItemSyncHeaderIndex(headerIndexByCompactName, errors, "대분류", true,
                "대분류", "카테고리", "대분류명");
        int middleCategoryIndex = findItemSyncHeaderIndex(headerIndexByCompactName, errors, "중분류", true,
                "중분류", "중분류명", "소분류");
        int sizeIndex = findItemSyncHeaderIndex(headerIndexByCompactName, errors, "사이즈", true,
                "사이즈", "크기", "제품사이즈", "제품규격", "규격사이즈");
        int standardIndex = findItemSyncHeaderIndex(headerIndexByCompactName, errors, "규격/비규격 구분", true,
                "구분", "규격비규격여부", "규격비규격", "규격구분", "규격여부", "비규격여부", "규격비규격구분");
        int mirrorCuttingIndex = findItemSyncHeaderIndex(headerIndexByCompactName, errors, "거울재단", true,
                "거울재단", "거울재단여부", "재단여부", "거울재단필요여부");
        int fallbackCategoryIndex = findItemSyncHeaderIndex(headerIndexByCompactName, errors, "대분류 추가용", false,
                "분류", "대분류추가용", "대분류없는것들추가용", "대분류누락추가용", "추가분류", "추가용분류");

        if (!errors.isEmpty()) {
            throw new IllegalArgumentException("동기화 엑셀 헤더가 등록 양식과 다릅니다. "
                    + String.join(" / ", errors)
                    + " / 실제 헤더: " + formatActualHeaders(headerRow, formatter));
        }

        return new ItemSyncHeader(
                itemCodeIndex,
                itemNameIndex,
                categoryIndex,
                middleCategoryIndex,
                sizeIndex,
                standardIndex,
                mirrorCuttingIndex,
                fallbackCategoryIndex
        );
    }

    private int findItemSyncHeaderIndex(Map<String, Integer> headerIndexByCompactName,
                                        List<String> errors,
                                        String logicalName,
                                        boolean required,
                                        String... aliases) {
        for (String alias : aliases) {
            Integer index = headerIndexByCompactName.get(AmountTextNormalizer.compact(alias));
            if (index != null) {
                return index;
            }
        }
        if (required) {
            errors.add(logicalName + " 컬럼을 찾을 수 없습니다. 허용 헤더=[" + String.join(", ", aliases) + "]");
        }
        return -1;
    }

    private String formatActualHeaders(Row headerRow, DataFormatter formatter) {
        if (headerRow == null) {
            return "";
        }
        List<String> headers = new ArrayList<>();
        int lastCellNum = Math.max(0, headerRow.getLastCellNum());
        for (int index = 0; index < lastCellNum; index++) {
            String headerName = readCell(headerRow, index, formatter);
            if (StringUtils.hasText(headerName)) {
                headers.add((index + 1) + "열=" + headerName);
            }
        }
        return String.join(", ", headers);
    }

    private int maxItemSyncHeaderIndex(ItemSyncHeader header) {
        return IntStream.of(
                        header.itemCodeIndex(),
                        header.itemNameIndex(),
                        header.categoryIndex(),
                        header.middleCategoryIndex(),
                        header.sizeIndex(),
                        header.standardIndex(),
                        header.mirrorCuttingIndex(),
                        header.fallbackCategoryIndex()
                )
                .max()
                .orElse(0);
    }

    private <T> List<T> parse(MultipartFile file, List<AmountExcelColumnDto> columns, RowFactory<T> rowFactory) {
        validateExcelFile(file);

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                throw new IllegalArgumentException("첫 번째 시트를 찾을 수 없습니다.");
            }

            DataFormatter formatter = new DataFormatter();
            validateHeader(sheet.getRow(0), columns, formatter);

            List<T> result = new ArrayList<>();
            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null || isBlankRow(row, columns.size(), formatter)) {
                    continue;
                }

                T entity = rowFactory.create();
                BeanWrapper wrapper = PropertyAccessorFactory.forBeanPropertyAccess(entity);
                List<String> searchParts = new ArrayList<>();

                for (int colIndex = 0; colIndex < columns.size(); colIndex++) {
                    AmountExcelColumnDto col = columns.get(colIndex);
                    String value = readCell(row, colIndex, formatter);
                    wrapper.setPropertyValue(col.field(), value);
                    if (!value.isBlank()) {
                        searchParts.add(value);
                    }
                }
                wrapper.setPropertyValue("searchText", String.join(" ", searchParts));
                result.add(entity);
            }
            return result;
        } catch (IOException e) {
            throw new IllegalStateException("엑셀 파일을 읽는 중 오류가 발생했습니다.", e);
        } catch (Exception e) {
            if (e instanceof IllegalArgumentException) {
                throw (IllegalArgumentException) e;
            }
            throw new IllegalStateException("엑셀 분석 중 오류가 발생했습니다: " + e.getMessage(), e);
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

    private void validateHeader(Row headerRow, List<AmountExcelColumnDto> columns, DataFormatter formatter) {
        if (headerRow == null) {
            throw new IllegalArgumentException("엑셀 1행 헤더가 없습니다.");
        }
        List<String> errors = new ArrayList<>();
        for (int i = 0; i < columns.size(); i++) {
            String expected = columns.get(i).header();
            String actual = readCell(headerRow, i, formatter);
            if (!expected.equals(actual)) {
                errors.add((i + 1) + "번째 컬럼: 기대값 [" + expected + "], 실제값 [" + actual + "]");
            }
        }
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException("엑셀 헤더가 등록 양식과 다릅니다. " + String.join(" / ", errors));
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

    @FunctionalInterface
    private interface RowFactory<T> {
        T create();
    }

    private record ItemSyncHeader(int itemCodeIndex,
                                  int itemNameIndex,
                                  int categoryIndex,
                                  int middleCategoryIndex,
                                  int sizeIndex,
                                  int standardIndex,
                                  int mirrorCuttingIndex,
                                  int fallbackCategoryIndex) {
    }

    private record ItemSyncRow(int rowNumber,
                               String itemCode,
                               String itemName,
                               String categoryName,
                               String middleCategoryName,
                               String size,
                               boolean standard,
                               boolean mirrorCuttingProduct,
                               String syncMemo) {
    }

    /**
     * Repository import를 하나 더 늘리지 않기 위한 작은 정렬 헬퍼입니다.
     */
    private static final class SortById {
        private SortById() {
        }

        private static org.springframework.data.domain.Sort asc() {
            return org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.ASC, "id");
        }
    }
}
