package com.dev.HiddenBATHAuto.service.amount;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
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

    private final AmountItemMasterRepository itemRepository;
    private final AmountCustomerMasterRepository customerRepository;

    @Transactional
    public AmountUploadResult replaceItems(MultipartFile file) {
        List<AmountItemMaster> items = parse(file, AmountExcelColumnDefinition.ITEM_COLUMNS, AmountItemMaster::new);
        itemRepository.deleteAllInBatch();
        itemRepository.flush();
        itemRepository.saveAll(items);
        return AmountUploadResult.ok("품목_얼마에요 데이터가 전체 교체되었습니다.", items.size());
    }

    @Transactional
    public AmountUploadResult replaceCustomers(MultipartFile file) {
        List<AmountCustomerMaster> customers = parse(file, AmountExcelColumnDefinition.CUSTOMER_COLUMNS, AmountCustomerMaster::new);
        customerRepository.deleteAllInBatch();
        customerRepository.flush();
        customerRepository.saveAll(customers);
        return AmountUploadResult.ok("거래처_얼마에요 데이터가 전체 교체되었습니다.", customers.size());
    }

    private <T> List<T> parse(MultipartFile file, List<AmountExcelColumnDto> columns, RowFactory<T> rowFactory) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("업로드할 엑셀 파일을 선택해 주세요.");
        }
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        if (!filename.endsWith(".xlsx") && !filename.endsWith(".xls")) {
            throw new IllegalArgumentException("엑셀 파일(.xlsx 또는 .xls)만 업로드할 수 있습니다.");
        }

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
                throw e;
            }
            throw new IllegalStateException("엑셀 분석 중 오류가 발생했습니다: " + e.getMessage(), e);
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
        if (row == null) {
            return "";
        }
        Cell cell = row.getCell(index, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) {
            return "";
        }
        return formatter.formatCellValue(cell).trim();
    }

    @FunctionalInterface
    private interface RowFactory<T> {
        T create();
    }
}
