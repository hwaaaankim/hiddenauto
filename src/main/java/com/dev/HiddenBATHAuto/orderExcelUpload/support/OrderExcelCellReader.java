package com.dev.HiddenBATHAuto.orderExcelUpload.support;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.springframework.stereotype.Component;

@Component
public class OrderExcelCellReader {

    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("yyyy.MM.dd"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("yyyyMMdd"),
            DateTimeFormatter.ofPattern("M/d/yyyy", Locale.KOREA),
            DateTimeFormatter.ofPattern("M.d.yyyy", Locale.KOREA),
            DateTimeFormatter.ofPattern("M. d", Locale.KOREA),
            DateTimeFormatter.ofPattern("M.d", Locale.KOREA)
    );

    private final DataFormatter dataFormatter = new DataFormatter(Locale.KOREA);

    public String text(Row row, int cellIndex) {
        Cell cell = cell(row, cellIndex);
        if (cell == null) {
            return "";
        }

        String value = formatCellUsingCachedValue(cell);
        return value == null ? "" : value.trim();
    }

    public LocalDate date(Row row, int cellIndex) {
        Cell cell = cell(row, cellIndex);
        if (cell == null) {
            return null;
        }

        if (isNumericOrCachedNumeric(cell) && DateUtil.isCellDateFormatted(cell)) {
            return DateUtil.getJavaDate(cell.getNumericCellValue())
                    .toInstant()
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate();
        }

        String raw = text(row, cellIndex)
                .replace("년", "-")
                .replace("월", "-")
                .replace("일", "")
                .replaceAll("\\s+", "")
                .trim();

        if (raw.isBlank()) {
            return null;
        }

        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                LocalDate parsed = LocalDate.parse(raw, formatter);
                if (parsed.getYear() < 100) {
                    parsed = parsed.withYear(LocalDate.now().getYear());
                }
                return parsed;
            } catch (DateTimeParseException ignored) {
            }
        }

        String normalized = raw.replace('.', '-').replace('/', '-');
        String[] parts = normalized.split("-");
        if (parts.length == 2 && isInteger(parts[0]) && isInteger(parts[1])) {
            int year = LocalDate.now().getYear();
            int month = Integer.parseInt(parts[0]);
            int day = Integer.parseInt(parts[1]);
            return LocalDate.of(year, month, day);
        }

        return null;
    }

    /**
     * 수량처럼 음수가 허용되는 정수 값을 읽습니다.
     *
     * - 일반 음수: -1, -2
     * - 유니코드 마이너스: −1, –1, —1
     * - 회계식 음수: (1)
     *
     * 단독 하이픈("-")은 수량을 특정할 수 없으므로 0으로 처리합니다.
     */
    public int signedInteger(Row row, int cellIndex) {
        return signedInteger(text(row, cellIndex));
    }

    public int signedInteger(String raw) {
        if (raw == null || raw.trim().isBlank()) {
            return 0;
        }

        String trimmed = raw.trim();
        boolean accountingNegative = trimmed.startsWith("(") && trimmed.endsWith(")");

        String normalized = trimmed
                .replace('−', '-')
                .replace('–', '-')
                .replace('—', '-')
                .replace(",", "")
                .replace("개", "")
                .replaceAll("\\s+", "")
                .replaceAll("[^0-9\\-.]", "");

        if (normalized.isBlank() || "-".equals(normalized) || ".".equals(normalized)) {
            return 0;
        }

        try {
            int parsed = new BigDecimal(normalized).stripTrailingZeros().intValueExact();
            return accountingNegative && parsed > 0 ? -parsed : parsed;
        } catch (ArithmeticException | NumberFormatException e) {
            return 0;
        }
    }

    public int money(Row row, int cellIndex) {
        return money(text(row, cellIndex));
    }

    public int money(String raw) {
        if (raw == null || raw.trim().isBlank()) {
            return 0;
        }

        String normalized = raw.trim()
                .replace(",", "")
                .replace("원", "")
                .replace("₩", "")
                .replaceAll("[^0-9\\-]", "");

        if (normalized.isBlank() || "-".equals(normalized)) {
            return 0;
        }

        try {
            return Integer.parseInt(normalized);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public boolean bool01(String raw) {
        if (raw == null) {
            return false;
        }

        String normalized = raw.trim().replaceAll("\\.0$", "").replaceAll("\\s+", "");
        if (normalized.isBlank()) {
            return false;
        }

        if ("0".equals(normalized)
                || "N".equalsIgnoreCase(normalized)
                || "NO".equalsIgnoreCase(normalized)
                || "FALSE".equalsIgnoreCase(normalized)
                || "X".equalsIgnoreCase(normalized)
                || "비규격".equals(normalized)
                || normalized.contains("아님")
                || normalized.contains("아닌")) {
            return false;
        }

        return "1".equals(normalized)
                || "Y".equalsIgnoreCase(normalized)
                || "YES".equalsIgnoreCase(normalized)
                || "TRUE".equalsIgnoreCase(normalized)
                || "O".equalsIgnoreCase(normalized)
                || "ㅇ".equals(normalized)
                || "규격".equals(normalized)
                || normalized.contains("거울재단");
    }

    public boolean hasText(Row row, int cellIndex) {
        return !text(row, cellIndex).isBlank();
    }

    private Cell cell(Row row, int cellIndex) {
        if (row == null || cellIndex < 0) {
            return null;
        }
        return row.getCell(cellIndex, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
    }

    private String formatCellUsingCachedValue(Cell cell) {
        if (cell == null) {
            return "";
        }

        if (cell.getCellType() != CellType.FORMULA) {
            return dataFormatter.formatCellValue(cell);
        }

        CellType cachedType = cell.getCachedFormulaResultType();
        if (cachedType == CellType.NUMERIC) {
            return dataFormatter.formatRawCellContents(
                    cell.getNumericCellValue(),
                    cell.getCellStyle().getDataFormat(),
                    cell.getCellStyle().getDataFormatString()
            );
        }
        if (cachedType == CellType.STRING) {
            return cell.getStringCellValue();
        }
        if (cachedType == CellType.BOOLEAN) {
            return String.valueOf(cell.getBooleanCellValue());
        }
        if (cachedType == CellType.ERROR) {
            return "";
        }
        return "";
    }

    private boolean isNumericOrCachedNumeric(Cell cell) {
        if (cell == null) {
            return false;
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            return true;
        }
        return cell.getCellType() == CellType.FORMULA
                && cell.getCachedFormulaResultType() == CellType.NUMERIC;
    }

    private boolean isInteger(String value) {
        try {
            Integer.parseInt(value);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
