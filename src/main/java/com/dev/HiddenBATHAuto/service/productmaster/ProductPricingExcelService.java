package com.dev.HiddenBATHAuto.service.productmaster;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** Imports only amounts. Axis fields, bounds and comparison operators stay in the editor. */
@Service
public class ProductPricingExcelService {
  public static final String DATA_SHEET = "가격데이터";
  public static final int MAX_AXIS = 200;
  public static final int MAX_CELLS = 20_000;
  public static final long MAX_FILE_BYTES = 5L * 1024 * 1024;
  private static final long MAX_UNCOMPRESSED_BYTES = 32L * 1024 * 1024;
  private static final long MAX_ENTRY_BYTES = 8L * 1024 * 1024;
  private static final BigDecimal MAX_PRICE = new BigDecimal("9999999999999.99");

  public record MatrixImport(
      int dimensions,
      int rows,
      int columns,
      List<List<BigDecimal>> amounts,
      String sheetName,
      String sourceRange) {}

  public record MatrixExport(
      int dimensions,
      String title,
      String xLabel,
      String yLabel,
      String xOperator,
      String yOperator,
      List<BigDecimal> xHeaders,
      List<BigDecimal> yHeaders,
      List<List<BigDecimal>> amounts) {}

  public MatrixImport importAmounts(MultipartFile file, int dimensions) {
    dimensions(dimensions);
    if (file == null || file.isEmpty())
      throw new IllegalArgumentException("금액이 입력된 XLSX 파일을 선택해 주세요.");
    String name = file.getOriginalFilename();
    if (name == null || !name.toLowerCase(Locale.ROOT).endsWith(".xlsx"))
      throw new IllegalArgumentException("XLSX 파일만 지원합니다. XLS·CSV 파일은 XLSX로 저장해 주세요.");
    if (file.getSize() > MAX_FILE_BYTES)
      throw new IllegalArgumentException("가격 엑셀은 5MB 이하로 업로드해 주세요.");
    try (var input = file.getInputStream()) {
      byte[] bytes = input.readNBytes((int) MAX_FILE_BYTES + 1);
      if (bytes.length > MAX_FILE_BYTES)
        throw new IllegalArgumentException("가격 엑셀은 5MB 이하로 업로드해 주세요.");
      return importAmounts(bytes, dimensions);
    } catch (IOException e) {
      throw new IllegalArgumentException("엑셀 파일을 읽을 수 없습니다. 파일을 다시 선택해 주세요.", e);
    }
  }

  /** Also used by the isolated regression tests; does not execute any Excel formula. */
  public MatrixImport importAmounts(byte[] bytes, int dimensions) {
    dimensions(dimensions);
    if (bytes == null || bytes.length == 0 || bytes.length > MAX_FILE_BYTES)
      throw new IllegalArgumentException("가격 엑셀은 비어 있지 않은 5MB 이하 파일이어야 합니다.");
    validateArchive(bytes);
    try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
      Sheet sheet = dataSheet(workbook);
      if (sheet.getNumMergedRegions() > 0)
        throw new IllegalArgumentException("가격데이터 시트의 병합된 셀을 해제해 주세요.");
      int[] bounds = bounds(sheet);
      if (bounds == null)
        throw new IllegalArgumentException("가격데이터 시트에 금액이 없습니다.");
      int sourceRows = bounds[2] - bounds[0] + 1;
      int sourceColumns = bounds[3] - bounds[1] + 1;
      shape(dimensions, sourceRows, sourceColumns, true);
      List<List<BigDecimal>> amounts = new ArrayList<>();
      for (int rowIndex = bounds[0]; rowIndex <= bounds[2]; rowIndex++) {
        List<BigDecimal> rowAmounts = new ArrayList<>();
        Row row = sheet.getRow(rowIndex);
        for (int column = bounds[1]; column <= bounds[3]; column++) {
          Cell cell = row == null ? null : row.getCell(column);
          String location = sheet.getSheetName() + "!" + new CellReference(rowIndex, column).formatAsString();
          rowAmounts.add(amount(cell, location));
        }
        amounts.add(List.copyOf(rowAmounts));
      }
      if (dimensions == 1 && sourceColumns == 1 && sourceRows > 1)
        amounts = List.of(amounts.stream().map(row -> row.get(0)).toList());
      String range = new CellReference(bounds[0], bounds[1]).formatAsString() + ":"
          + new CellReference(bounds[2], bounds[3]).formatAsString();
      return new MatrixImport(dimensions, amounts.size(), amounts.get(0).size(),
          List.copyOf(amounts), sheet.getSheetName(), range);
    } catch (IllegalArgumentException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalArgumentException(
          "XLSX 파일을 읽을 수 없습니다. 암호·손상 여부를 확인하고 일반 XLSX로 다시 저장해 주세요.", e);
    }
  }

  public byte[] template(int dimensions, int rows, int columns) {
    dimensions(dimensions);
    if (dimensions == 1) rows = 1;
    shape(dimensions, rows, columns, false);
    List<List<BigDecimal>> amounts = new ArrayList<>();
    for (int row = 0; row < rows; row++) {
      List<BigDecimal> values = new ArrayList<>();
      for (int column = 0; column < columns; column++) values.add(BigDecimal.ZERO);
      amounts.add(values);
    }
    try (XSSFWorkbook workbook = new XSSFWorkbook()) {
      writeData(workbook, amounts);
      Sheet instructions = workbook.createSheet("안내");
      text(instructions, 0, 0, "가격계산 금액 전용 엑셀 양식");
      text(instructions, 2, 0, "가격데이터 시트의 0을 실제 금액으로 바꿔 주세요. 0원도 유효한 금액입니다.");
      text(instructions, 3, 0, "그룹명·항목명·X/Y 기준값은 넣지 않습니다. 업로드 후 화면에서 입력합니다.");
      text(instructions, 4, 0, dimensions == 1
          ? "1차원은 한 행 또는 한 열로 작성합니다. 업로드 후 한 행으로 정리됩니다."
          : "2차원은 X축 기준마다 열, Y축 기준마다 행을 작성합니다.");
      text(instructions, 5, 0, "행·열 수를 바꿀 수 있습니다. 중간 빈칸은 금액 0을 입력해 주세요.");
      text(instructions, 6, 0, "숫자 금액만 지원합니다. 수식은 값으로 붙여넣고, 문자·날짜·음수는 제거해 주세요.");
      text(instructions, 7, 0, "금액: 0~9,999,999,999,999.99원. 소수점 둘째 자리까지. 축마다 최대 200개, 총 20,000개 금액.");
      instructions.setColumnWidth(0, 100 * 256);
      return bytes(workbook);
    } catch (IOException e) {
      throw new IllegalStateException("엑셀 양식을 만들 수 없습니다.", e);
    }
  }

  public byte[] export(MatrixExport request) {
    if (request == null) throw new IllegalArgumentException("엑셀로 내보낼 가격표가 없습니다.");
    List<List<BigDecimal>> amounts = validateExport(request);
    try (XSSFWorkbook workbook = new XSSFWorkbook()) {
      writeData(workbook, amounts);
      Sheet review = workbook.createSheet("기준과가격검토");
      text(review, 0, 0, blank(request.title()) ? "비규격 가격계산 항" : request.title().trim());
      text(review, 1, 0, "다시 업로드할 때는 가격데이터 시트의 금액만 읽습니다. 아래 기준은 검토용입니다.");
      text(review, 2, 0, "차원");
      text(review, 2, 1, request.dimensions() + "차원");
      text(review, 3, 0, "X축 항목");
      text(review, 3, 1, request.xLabel());
      text(review, 4, 0, "X축 기준");
      text(review, 4, 1, operatorLabel(request.xOperator()));
      if (request.dimensions() == 2) {
        text(review, 5, 0, "Y축 항목");
        text(review, 5, 1, request.yLabel());
        text(review, 6, 0, "Y축 기준");
        text(review, 6, 1, operatorLabel(request.yOperator()));
      }
      CellStyle money = moneyStyle(workbook);
      CellStyle heading = headingStyle(workbook);
      CellStyle bound = workbook.createCellStyle();
      bound.cloneStyleFrom(heading);
      text(review, 8, 0, request.dimensions() == 2 ? "Y축 / X축" : "X축 기준");
      review.getRow(8).getCell(0).setCellStyle(heading);
      for (int column = 0; column < request.xHeaders().size(); column++)
        axisValue(review, 8, column + 1, request.xHeaders().get(column), bound);
      for (int row = 0; row < amounts.size(); row++) {
        if (request.dimensions() == 2)
          axisValue(review, row + 9, 0, request.yHeaders().get(row), bound);
        else {
          text(review, row + 9, 0, "금액(원)");
          review.getRow(row + 9).getCell(0).setCellStyle(heading);
        }
        for (int column = 0; column < amounts.get(row).size(); column++)
          numeric(review, row + 9, column + 1, amounts.get(row).get(column), money);
      }
      review.setColumnWidth(0, 24 * 256);
      for (int column = 1; column <= amounts.get(0).size(); column++)
        review.setColumnWidth(column, 18 * 256);
      review.createFreezePane(1, 9);
      return bytes(workbook);
    } catch (IOException e) {
      throw new IllegalStateException("가격계산 엑셀을 만들 수 없습니다.", e);
    }
  }

  private static List<List<BigDecimal>> validateExport(MatrixExport request) {
    dimensions(request.dimensions());
    if (request.amounts() == null || request.amounts().isEmpty()
        || request.amounts().get(0) == null || request.amounts().get(0).isEmpty())
      throw new IllegalArgumentException("내보낼 금액을 입력해 주세요.");
    int rows = request.amounts().size(), columns = request.amounts().get(0).size();
    shape(request.dimensions(), rows, columns, false);
    List<List<BigDecimal>> result = new ArrayList<>();
    for (int row = 0; row < rows; row++) {
      List<BigDecimal> values = request.amounts().get(row);
      if (values == null || values.size() != columns)
        throw new IllegalArgumentException("가격표의 모든 행은 열 수가 같아야 합니다.");
      List<BigDecimal> normalized = new ArrayList<>();
      for (int column = 0; column < columns; column++)
        normalized.add(price(values.get(column), "금액 " + (row + 1) + "행 " + (column + 1) + "열"));
      result.add(List.copyOf(normalized));
    }
    headers(request.xHeaders(), columns, "X축");
    operatorLabel(request.xOperator());
    label(request.title(), "항 이름", 200, false);
    label(request.xLabel(), "X축 항목 이름", 120, true);
    if (request.dimensions() == 2) {
      headers(request.yHeaders(), rows, "Y축");
      operatorLabel(request.yOperator());
      label(request.yLabel(), "Y축 항목 이름", 120, true);
    }
    return List.copyOf(result);
  }

  private static void headers(List<BigDecimal> values, int size, String axis) {
    if (values == null || values.size() != size)
      throw new IllegalArgumentException(axis + " 기준값 개수가 금액의 행·열 수와 다릅니다.");
    BigDecimal previous = null;
    for (BigDecimal value : values) {
      if (value == null || value.signum() < 0 || value.stripTrailingZeros().scale() > 6
          || value.precision() > 24)
        throw new IllegalArgumentException(axis + " 기준값은 0 이상, 유효 숫자 24자리 및 소수점 여섯째 자리까지 입력해 주세요.");
      if (previous != null && previous.compareTo(value) >= 0)
        throw new IllegalArgumentException(axis + " 기준값은 중복 없이 작은 값부터 입력해 주세요.");
      previous = value;
    }
  }

  private static void label(String value, String name, int maximum, boolean required) {
    if (required && blank(value)) throw new IllegalArgumentException(name + "을 입력해 주세요.");
    if (value != null && value.length() > maximum)
      throw new IllegalArgumentException(name + "은 " + maximum + "자 이내로 입력해 주세요.");
  }

  private static boolean blank(String value) { return value == null || value.isBlank(); }

  private static String operatorLabel(String operator) {
    if ("LE".equals(operator)) return "기준값 이하 (≤)";
    if ("LT".equals(operator)) return "기준값 미만 (<)";
    throw new IllegalArgumentException("축의 기준은 이하 또는 미만으로 선택해 주세요.");
  }

  private static BigDecimal amount(Cell cell, String location) {
    if (empty(cell))
      throw new IllegalArgumentException(location + ": 빈 금액은 허용하지 않습니다. 무료이면 숫자 0을 입력해 주세요.");
    if (cell.getCellType() == CellType.FORMULA)
      throw new IllegalArgumentException(location + ": 수식을 실행하지 않습니다. 수식 결과를 '값 붙여넣기'로 바꿔 주세요.");
    if (cell.getCellType() == CellType.ERROR)
      throw new IllegalArgumentException(location + ": 엑셀 오류 값이 있습니다. 숫자 금액으로 바꿔 주세요.");
    if (cell.getCellType() != CellType.NUMERIC)
      throw new IllegalArgumentException(location + ": 문자·논리값 대신 숫자 금액을 입력해 주세요. 기준 헤더는 업로드 후 입력합니다.");
    if (DateUtil.isCellDateFormatted(cell))
      throw new IllegalArgumentException(location + ": 날짜 형식 대신 숫자 금액을 입력해 주세요.");
    try {
      // XLSX's decimal text avoids additional binary-double rounding during import.
      String raw = ((XSSFCell) cell).getRawValue();
      return price(new BigDecimal(raw), location);
    } catch (NumberFormatException | NullPointerException e) {
      throw new IllegalArgumentException(location + ": 올바른 숫자 금액이 아닙니다.", e);
    }
  }

  private static BigDecimal price(BigDecimal value, String location) {
    if (value == null || value.signum() < 0 || value.compareTo(MAX_PRICE) > 0
        || value.stripTrailingZeros().scale() > 2)
      throw new IllegalArgumentException(location + ": 금액은 0~9,999,999,999,999.99원, 소수점 둘째 자리까지 입력해 주세요.");
    return value.stripTrailingZeros();
  }

  private static int[] bounds(Sheet sheet) {
    int minRow = Integer.MAX_VALUE, minColumn = Integer.MAX_VALUE, maxRow = -1, maxColumn = -1;
    int scanned = 0;
    for (Row row : sheet) {
      for (Cell cell : row) {
        if (++scanned > 100_000)
          throw new IllegalArgumentException("시트의 셀이 너무 많습니다. 가격 금액 영역만 새 XLSX에 복사해 주세요.");
        if (empty(cell)) continue;
        minRow = Math.min(minRow, cell.getRowIndex());
        maxRow = Math.max(maxRow, cell.getRowIndex());
        minColumn = Math.min(minColumn, cell.getColumnIndex());
        maxColumn = Math.max(maxColumn, cell.getColumnIndex());
      }
    }
    return maxRow < 0 ? null : new int[] {minRow, minColumn, maxRow, maxColumn};
  }

  private static boolean empty(Cell cell) {
    return cell == null || cell.getCellType() == CellType.BLANK || cell.getCellType() == CellType._NONE
        || cell.getCellType() == CellType.STRING && cell.getStringCellValue().isBlank();
  }

  private static Sheet dataSheet(XSSFWorkbook workbook) {
    Sheet preferred = workbook.getSheet(DATA_SHEET);
    if (preferred != null) return preferred;
    Sheet found = null;
    for (int index = 0; index < workbook.getNumberOfSheets(); index++) {
      if (workbook.isSheetHidden(index) || workbook.isSheetVeryHidden(index)) continue;
      Sheet candidate = workbook.getSheetAt(index);
      if (bounds(candidate) == null) continue;
      if (found != null)
        throw new IllegalArgumentException("금액이 있는 시트가 여러 개입니다. 금액 시트 이름을 '가격데이터'로 지정해 주세요.");
      found = candidate;
    }
    if (found == null) throw new IllegalArgumentException("엑셀에 업로드할 금액이 없습니다.");
    return found;
  }

  private static void dimensions(int dimensions) {
    if (dimensions != 1 && dimensions != 2)
      throw new IllegalArgumentException("가격표의 차원은 1 또는 2로 선택해 주세요.");
  }

  private static void shape(int dimensions, int rows, int columns, boolean importFile) {
    if (rows < 1 || columns < 1 || rows > MAX_AXIS || columns > MAX_AXIS
        || (long) rows * columns > MAX_CELLS)
      throw new IllegalArgumentException("가격표는 각 축 1~200개, 전체 20,000개 금액 이내로 입력해 주세요.");
    if (dimensions == 1 && (importFile ? rows > 1 && columns > 1 : rows != 1))
      throw new IllegalArgumentException("1차원 가격표는 금액을 한 행 또는 한 열로 입력해 주세요.");
  }

  private static void validateArchive(byte[] bytes) {
    Set<String> names = new HashSet<>();
    long total = 0;
    boolean workbook = false, contentTypes = false;
    try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
      ZipEntry entry;
      byte[] buffer = new byte[8192];
      while ((entry = zip.getNextEntry()) != null) {
        String name = entry.getName();
        String lower = name.toLowerCase(Locale.ROOT);
        if (names.size() >= 128 || !names.add(name) || name.startsWith("/")
            || name.contains("\\") || List.of(name.split("/")).contains(".."))
          throw new IllegalArgumentException("엑셀 압축 구조가 올바르지 않거나 항목이 너무 많습니다.");
        if (lower.contains("vbaproject") || lower.startsWith("xl/externallinks/")
            || lower.startsWith("xl/embeddings/"))
          throw new IllegalArgumentException("매크로·외부 연결·삽입 객체가 없는 일반 XLSX 파일을 사용해 주세요.");
        boolean xml = lower.endsWith(".xml") || lower.endsWith(".rels");
        ByteArrayOutputStream xmlText = xml ? new ByteArrayOutputStream() : null;
        long expanded = 0;
        int count;
        while ((count = zip.read(buffer)) != -1) {
          expanded += count;
          total += count;
          if (expanded > MAX_ENTRY_BYTES || total > MAX_UNCOMPRESSED_BYTES)
            throw new IllegalArgumentException("엑셀의 압축 해제 용량이 너무 큽니다. 가격 영역만 새 파일에 복사해 주세요.");
          if (xmlText != null) xmlText.write(buffer, 0, count);
        }
        long compressed = entry.getCompressedSize();
        if (expanded > 1024 * 1024 && compressed > 0 && expanded > compressed * 100L)
          throw new IllegalArgumentException("엑셀 압축 비율이 너무 높습니다. 가격 영역만 새 XLSX 파일로 저장해 주세요.");
        if (xmlText != null) {
          String content = xmlText.toString(StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
          if (content.contains("<!doctype") || content.contains("<!entity")
              || content.matches("(?s).*targetmode\\s*=\\s*['\"]external['\"].*"))
            throw new IllegalArgumentException("외부 XML 참조·외부 링크가 없는 XLSX 파일을 사용해 주세요.");
          if (lower.equals("[content_types].xml") && content.contains("macroenabled"))
            throw new IllegalArgumentException("매크로가 없는 일반 XLSX 파일을 사용해 주세요.");
        }
        workbook |= name.equals("xl/workbook.xml");
        contentTypes |= name.equals("[Content_Types].xml");
        zip.closeEntry();
      }
      if (!workbook || !contentTypes)
        throw new IllegalArgumentException("일반 XLSX 파일이 아닙니다. 엑셀에서 XLSX로 다시 저장해 주세요.");
    } catch (IOException e) {
      throw new IllegalArgumentException("엑셀 압축 데이터를 읽을 수 없습니다. 파일 손상 여부를 확인해 주세요.", e);
    }
  }

  private static void writeData(XSSFWorkbook workbook, List<List<BigDecimal>> amounts) {
    Sheet sheet = workbook.createSheet(DATA_SHEET);
    CellStyle style = moneyStyle(workbook);
    style.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
    style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
    for (int row = 0; row < amounts.size(); row++)
      for (int column = 0; column < amounts.get(row).size(); column++)
        numeric(sheet, row, column, amounts.get(row).get(column), style);
    for (int column = 0; column < amounts.get(0).size(); column++)
      sheet.setColumnWidth(column, 18 * 256);
  }

  private static CellStyle moneyStyle(XSSFWorkbook workbook) {
    CellStyle style = workbook.createCellStyle();
    style.setDataFormat(workbook.createDataFormat().getFormat("#,##0.##"));
    return style;
  }

  private static CellStyle headingStyle(XSSFWorkbook workbook) {
    CellStyle style = workbook.createCellStyle();
    style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
    style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
    Font font = workbook.createFont();
    font.setBold(true);
    style.setFont(font);
    return style;
  }

  private static void numeric(Sheet sheet, int row, int column, BigDecimal value, CellStyle style) {
    Cell cell = cell(sheet, row, column);
    cell.setCellValue(value.doubleValue());
    cell.setCellStyle(style);
  }

  private static void axisValue(Sheet sheet, int row, int column, BigDecimal value, CellStyle style) {
    // Review-only headers are text so Excel's 15-digit numeric precision cannot alter a bound.
    Cell cell = cell(sheet, row, column);
    cell.setCellValue(value.toString());
    cell.setCellStyle(style);
  }

  private static void text(Sheet sheet, int row, int column, String value) {
    cell(sheet, row, column).setCellValue(value == null ? "" : value);
  }

  private static Cell cell(Sheet sheet, int row, int column) {
    Row excelRow = sheet.getRow(row);
    if (excelRow == null) excelRow = sheet.createRow(row);
    Cell cell = excelRow.getCell(column);
    return cell == null ? excelRow.createCell(column) : cell;
  }

  private static byte[] bytes(XSSFWorkbook workbook) throws IOException {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    workbook.write(output);
    return output.toByteArray();
  }
}
