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

/** Supports legacy amount-only sheets and explicit tables with thresholds and boundary settings. */
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
      String sourceRange,
      List<String> xHeaders, List<String> yHeaders,
      String xOperator, String yOperator, List<String> xOperators, List<String> yOperators,
      String xOutsidePolicy, String yOutsidePolicy) {
    public MatrixImport(int dimensions, int rows, int columns, List<List<BigDecimal>> amounts,
        String sheetName, String sourceRange, List<String> xHeaders, List<String> yHeaders,
        String xOperator, String yOperator, List<String> xOperators, List<String> yOperators) {
      this(dimensions, rows, columns, amounts, sheetName, sourceRange, xHeaders, yHeaders, xOperator, yOperator, xOperators, yOperators, null, null);
    }
    public MatrixImport(int dimensions, int rows, int columns, List<List<BigDecimal>> amounts,
        String sheetName, String sourceRange) {
      this(dimensions, rows, columns, amounts, sheetName, sourceRange, null, null, null, null, null, null);
    }
  }

  public record MatrixExport(
      int dimensions,
      String title,
      String xLabel,
      String yLabel,
      String xOperator,
      String yOperator,
      List<BigDecimal> xHeaders,
      List<BigDecimal> yHeaders,
      List<List<BigDecimal>> amounts, List<String> xOperators, List<String> yOperators,
      String xOutsidePolicy, String yOutsidePolicy) {
    public MatrixExport(int dimensions, String title, String xLabel, String yLabel,
        String xOperator, String yOperator, List<BigDecimal> xHeaders, List<BigDecimal> yHeaders,
        List<List<BigDecimal>> amounts, List<String> xOperators, List<String> yOperators) {
      this(dimensions, title, xLabel, yLabel, xOperator, yOperator, xHeaders, yHeaders, amounts, xOperators, yOperators, null, null);
    }
    public MatrixExport(int dimensions, String title, String xLabel, String yLabel,
        String xOperator, String yOperator, List<BigDecimal> xHeaders, List<BigDecimal> yHeaders,
        List<List<BigDecimal>> amounts) {
      this(dimensions, title, xLabel, yLabel, xOperator, yOperator, xHeaders, yHeaders, amounts, null, null);
    }
  }

  public MatrixImport importAmounts(MultipartFile file, int dimensions) {
    return importFile(file, dimensions, false);
  }

  public MatrixImport importFile(MultipartFile file, int dimensions, boolean includeHeaders) {
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
      return includeHeaders ? importTable(bytes, dimensions) : importAmounts(bytes, dimensions);
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
        throw new IllegalArgumentException("가격데이터 시트에 금액이 없습니다. 회색 예시를 지운 뒤 실제 금액을 입력하고 저장해 주세요.");
      diagnose(sheet, bounds, dimensions, false);
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
    return template(dimensions, rows, columns, "X 입력", "Y 입력");
  }
  public byte[] template(int dimensions, int rows, int columns, String xLabel, String yLabel) {
    label(xLabel, "X 항목", 120, false); label(yLabel, "Y 항목", 120, false);
    dimensions(dimensions);
    if (dimensions == 1) rows = 1;
    shape(dimensions, rows, columns, false);
    List<List<BigDecimal>> amounts = new ArrayList<>();
    for (int row = 0; row < rows; row++) {
      List<BigDecimal> values = new ArrayList<>();
      for (int column = 0; column < columns; column++) values.add(BigDecimal.valueOf(50000L + row * 10000L + column * 5000L));
      amounts.add(values);
    }
    try (XSSFWorkbook workbook = new XSSFWorkbook()) {
      writeData(workbook, amounts);
      greyExamples(workbook, workbook.getSheet(DATA_SHEET));
      Sheet instructions = workbook.createSheet("안내");
      text(instructions, 0, 0, "금액 전용 양식 · 회색 예시를 지우고 실제 금액으로 입력하세요");
      text(instructions, 1, 0, "선택한 입력: X(열)=" + xLabel + (dimensions == 2 ? " / Y(행)=" + yLabel : " / 1차원"));
      text(instructions, 2, 0, "가격데이터 시트의 회색 숫자는 작성 예시입니다. 예시 전체를 지우고 실제 금액을 입력하세요. 실제 무료 가격만 0원입니다.");
      text(instructions, 3, 0, "그룹명·항목명·X/Y 기준값은 넣지 않습니다. 업로드 후 화면에서 입력합니다.");
      text(instructions, 4, 0, dimensions == 1
          ? "1차원은 한 행 또는 한 열로 작성합니다. 업로드 후 한 행으로 정리됩니다."
          : "2차원은 X축 기준마다 열, Y축 기준마다 행을 작성합니다.");
      text(instructions, 5, 0, "행·열 수를 바꿀 수 있습니다. 중간 빈칸은 미설정 오류입니다. 무료일 때만 0을 입력하세요.");
      text(instructions, 6, 0, "숫자 금액만 지원합니다. 수식은 값으로 붙여넣고, 문자·날짜·음수는 제거해 주세요.");
      text(instructions, 7, 0, "금액: 0~9,999,999,999,999.99원. 소수점 둘째 자리까지. 축마다 최대 200개, 총 20,000개 금액.");
      text(instructions, 9, 0, "300·400 등 사이즈 기준을 이 시트의 금액에 섞지 마세요. 기준은 업로드 후 화면에서 입력·자동 생성합니다.");
      text(instructions, 10, 0, "엑셀 수식이 있다면 금액 영역을 복사 → 새 시트 A1 선택 → 선택하여 붙여넣기 → 값으로 붙여넣습니다.");
      text(instructions, 11, 0, "기준을 엑셀에 함께 넣으려면 기준 포함 양식을 다운로드하고 업로드 방식도 기준 포함을 선택하세요.");
      instructions.setColumnWidth(0, 110 * 256);
      formatHelp(workbook, instructions);
      workbook.setActiveSheet(workbook.getSheetIndex("안내"));
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
      writeTable(workbook, request, amounts);
      workbook.getSheet(DATA_SHEET).setSelected(false);
      workbook.getSheet("가격표").setSelected(true);
      workbook.setActiveSheet(workbook.getSheetIndex("가격표"));
      Sheet review = workbook.createSheet("기준과가격검토");
      text(review, 0, 0, blank(request.title()) ? "비규격 가격계산 항" : request.title().trim());
      text(review, 1, 0, "기준 포함 업로드는 가격표·가격표설정 시트를, 금액만 업로드는 가격데이터 시트를 읽습니다. 개별 포함 여부는 가격표설정의 X/Y 개별 기준을 확인하세요.");
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


  /** A complete table uses row 1 for X bounds and column A for Y bounds. */
  public MatrixImport importTable(byte[] bytes, int dimensions) {
    dimensions(dimensions);
    if (bytes == null || bytes.length == 0 || bytes.length > MAX_FILE_BYTES)
      throw new IllegalArgumentException("가격 엑셀은 비어 있지 않은 5MB 이하 파일이어야 합니다.");
    validateArchive(bytes);
    try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
      Sheet sheet = workbook.getSheet("가격표");
      if (sheet == null) sheet = dataSheet(workbook);
      if (sheet.getNumMergedRegions() > 0) throw new IllegalArgumentException("가격표 시트의 병합된 셀을 해제해 주세요.");
      int[] bounds = bounds(sheet);
      if (bounds == null) throw new IllegalArgumentException("읽을 가격 데이터가 없습니다. 회색 예시를 지우고 실제 가격을 입력해 주세요.");
      diagnose(sheet, bounds, dimensions, true);
      int firstRow = bounds[0], firstColumn = bounds[1];
      int rows = bounds[2] - firstRow, columns = bounds[3] - firstColumn;
      shape(dimensions, rows, columns, false);
      List<BigDecimal> x = new ArrayList<>(), y = new ArrayList<>();
      for (int c = 1; c <= columns; c++) x.add(boundCell(cell(sheet, firstRow, firstColumn + c), "X축 " + c + "번째 기준"));
      if (dimensions == 2) for (int r = 1; r <= rows; r++)
        y.add(boundCell(cell(sheet, firstRow + r, firstColumn), "Y축 " + r + "번째 기준"));
      headers(x, columns, "X축");
      if (dimensions == 2) headers(y, rows, "Y축");
      List<List<BigDecimal>> amounts = new ArrayList<>();
      for (int r = 1; r <= rows; r++) {
        List<BigDecimal> values = new ArrayList<>();
        for (int c = 1; c <= columns; c++)
          values.add(amount(cell(sheet, firstRow + r, firstColumn + c), sheet.getSheetName() + "!" + new CellReference(firstRow + r, firstColumn + c).formatAsString()));
        amounts.add(List.copyOf(values));
      }
      Sheet settings = workbook.getSheet("가격표설정");
      String xo = null, yo = null, xp = null, yp = null;
      List<String> xops = null, yops = null;
      if (settings != null) {
        String dimension = setting(settings, 0);
        if (!Integer.toString(dimensions).equals(dimension))
          throw new IllegalArgumentException("엑셀 가격표 차원과 선택한 계산 방식이 다릅니다.");
        xo = setting(settings, 1); operatorLabel(xo);
        if (dimensions == 2) { yo = setting(settings, 2); operatorLabel(yo); }
        xops = readOperators(setting(settings, 3), columns, "X축");
        xp = outsidePolicy(setting(settings, 5));
        if (dimensions == 2) yp = outsidePolicy(setting(settings, 6));
        if (dimensions == 2) yops = readOperators(setting(settings, 4), rows, "Y축");
      }
      direction(xo == null ? "LE" : xo, xops, "X축");
      if (dimensions == 2) direction(yo == null ? "LE" : yo, yops, "Y축");
      return new MatrixImport(dimensions, rows, columns, List.copyOf(amounts), sheet.getSheetName(),
          new CellReference(firstRow + 1, firstColumn + 1).formatAsString() + ":" + new CellReference(bounds[2], bounds[3]).formatAsString(),
          x.stream().map(BigDecimal::toPlainString).toList(), y.stream().map(BigDecimal::toPlainString).toList(),
          xo, yo, xops, yops, xp, yp);
    } catch (IllegalArgumentException e) { throw e; }
    catch (Exception e) { throw new IllegalArgumentException("가격표 XLSX를 읽을 수 없습니다. 기준 포함 양식으로 다시 저장해 주세요.", e); }
  }

  public byte[] tableTemplate(int dimensions, int rows, int columns) {
    return tableTemplate(dimensions, rows, columns, "X 입력", "Y 입력");
  }
  public byte[] tableTemplate(int dimensions, int rows, int columns, String xLabel, String yLabel) {
    label(xLabel, "X 항목", 120, false); label(yLabel, "Y 항목", 120, false);
    dimensions(dimensions);
    if (dimensions == 1) rows = 1;
    shape(dimensions, rows, columns, false);
    try (XSSFWorkbook workbook = new XSSFWorkbook()) {
      Sheet sheet = workbook.createSheet("가격표");
      text(sheet, 0, 0, dimensions == 2 ? yLabel + " / " + xLabel : xLabel + " 기준");
      CellStyle heading = headingStyle(workbook), money = moneyStyle(workbook);
      for (int c = 1; c <= columns; c++) { numeric(sheet, 0, c, BigDecimal.valueOf(200L + c * 100L), heading); sheet.setColumnWidth(c, 18 * 256); }
      for (int r = 1; r <= rows; r++) {
        if (dimensions == 1) text(sheet, r, 0, "금액(원)");
        else numeric(sheet, r, 0, BigDecimal.valueOf(200L + r * 100L), heading);
        cell(sheet, r, 0).setCellStyle(heading);
        for (int c = 1; c <= columns; c++) { numeric(sheet, r, c, BigDecimal.valueOf(50000L + (r - 1) * 10000L + (c - 1) * 5000L), money); }
      }
      sheet.setColumnWidth(0, 22 * 256); sheet.createFreezePane(1, 1);
      greyExamples(workbook, sheet);
      writeSettings(workbook, dimensions, "LE", "LE", null, null, "ERROR", "ERROR");
      Sheet help = workbook.createSheet("안내");
      String[] lines = {"기준 포함 양식 · 회색 예시 숫자를 지우고 실제 기준과 금액을 입력하세요", "선택한 입력: X(열)=" + xLabel + (dimensions == 2 ? " / Y(행)=" + yLabel : " / 1차원"), "가격표 첫 행 B1부터 X 기준값을 작은 순서로 입력하세요.",
          dimensions == 2 ? "첫 열 A2부터 Y 기준값을 작은 순서로 입력하세요. B2부터 교차 금액입니다." : "금액은 B2부터 한 행에 입력하세요. Y축은 사용하지 않습니다.",
          "금액은 비워 두지 말고 무료인 칸도 0을 입력하세요. 수식은 값 붙여넣기로 바꿔 주세요.",
          "LE=이하, LT=미만: 401→500. GE=이상, GT=초과: 401→400. 기본값은 LE이며 같은 축에는 같은 방향만 사용합니다.",
          "개별 기준은 LE|LT|LE처럼 X는 왼쪽부터, Y는 위쪽부터 입력합니다. 비우면 축 기본값을 따릅니다.",
          "업로드 후 화면에서 입력 항목 연결·포함 여부·교차 금액을 확인하고 최종 저장하세요.",
          "기준값은 소수 6자리, 금액은 소수 2자리까지. 축당 200개, 교차 금액 20,000개까지입니다."};
      for (int i = 0; i < lines.length; i++) text(help, i, 0, lines[i]);
      help.setColumnWidth(0, 110 * 256);
      formatHelp(workbook, help);
      workbook.setActiveSheet(workbook.getSheetIndex("안내"));
      return bytes(workbook);
    } catch (IOException e) { throw new IllegalStateException("가격표 양식을 만들 수 없습니다.", e); }
  }

  /** Report all actionable format errors together, without executing formulas or changing the editor. */
  private static void diagnose(Sheet sheet, int[] b, int dimensions, boolean includeHeaders) {
    List<String> issues = new ArrayList<>();
    int height = b[2] - b[0] + 1, width = b[3] - b[1] + 1;
    if ((long) height * width > 41000) throw new IllegalArgumentException("읽을 범위가 " + height + "행 × " + width + "열입니다. 금액 영역만 남겨 주세요. 축당 200개, 금액 20,000개까지 가능합니다.");
    boolean table = headerShape(sheet, b, dimensions) || dimensions == 1 && headerShape(sheet, b, 2);
    String region = range(b[0], b[1], b[2], b[3]);
    String prices = table ? range(b[0] + 1, b[1] + 1, b[2], b[3]) : region;
    if (!includeHeaders && table) {
      issues.add("[기준값이 함께 들어 있습니다] " + sheet.getSheetName() + "!" + region
          + "의 첫 행·첫 열에 있는 300·400 등의 사이즈 기준값을 지워 주세요. 금액만 업로드에는 사이즈 기준을 넣지 않습니다.");
      issues.add("실제 금액 영역: " + prices + ". 이 영역만 복사 → 새 파일의 A1에 ‘값’ 붙여넣기 → ‘금액만’ 방식으로 올려 주세요.");
      issues.add("기준값도 함께 읽으려면 ‘기준 포함’ 방식으로 다시 업로드할 수 있습니다. 시트명은 가격표가 아니어도 한 개의 명확한 표이면 읽습니다.");
    }
    if (includeHeaders && table) {
      checkHeaderCells(sheet, b[0], b[1] + 1, b[3] - b[1], true, issues);
      if (dimensions == 2) checkHeaderCells(sheet, b[0] + 1, b[1], b[2] - b[0], false, issues);
      else if (height != 2) issues.add("[차원 불일치] 숫자 입력을 1개 선택했지만 기준 포함 표의 금액이 " + (height - 1) + "행입니다. 1차원은 기준 한 행과 금액 한 행만 넣으세요. W·H 교차표라면 숫자를 2개 선택해 주세요.");
    }
    if (includeHeaders && !table) {
      issues.add("[기준 포함 구조를 확인하세요] 첫 행에는 X 기준, 첫 열에는 Y 기준(1차원은 금액 제목), 그 안에는 금액이 필요합니다. 기준은 중복 없이 작은 순서로 입력하세요.");
      issues.add("금액만 있는 파일이라면 ‘금액만’ 방식으로 업로드한 뒤 화면에서 300·400 등의 기준값을 입력하세요. 현재 읽은 영역: " + sheet.getSheetName() + "!" + region);
    }
    int formulas = 0, blanks = 0, invalid = 0;
    List<String> formulaCells = new ArrayList<>(), blankCells = new ArrayList<>(), invalidCells = new ArrayList<>();
    int firstRow = table ? b[0] + 1 : b[0], firstColumn = table ? b[1] + 1 : b[1];
    for (int r = firstRow; r <= b[2]; r++) for (int c = firstColumn; c <= b[3]; c++) {
      Cell value = readCell(sheet, r, c); String at = new CellReference(r, c).formatAsString();
      if (value != null && value.getCellType() == CellType.FORMULA) { formulas++; if (formulaCells.size() < 8) formulaCells.add(at); }
      else if (empty(value)) { blanks++; if (blankCells.size() < 8) blankCells.add(at); }
      else try { amount(value, at); } catch (IllegalArgumentException ex) { invalid++; if (invalidCells.size() < 5) invalidCells.add(ex.getMessage()); }
    }
    if (formulas > 0) issues.add("[수식 " + formulas + "개] " + String.join(", ", formulaCells)
        + (formulas > formulaCells.size() ? " 외" : "") + ". 금액 영역 " + prices
        + "을 복사하여 ‘선택하여 붙여넣기 → 값’으로 바꾼 뒤 저장하세요. 수식이 아닌 계산 완료된 실제 숫자값이 필요합니다.");
    if (blanks > 0) issues.add("[빈 금액 " + blanks + "개] " + String.join(", ", blankCells)
        + (blanks > blankCells.size() ? " 외" : "") + ". 실제 가격을 채우세요. 무료일 때만 0을 입력하고, 사용하지 않는 끝 행·열은 기준값과 함께 삭제하세요.");
    if (invalid > 0) issues.add("[숫자 형식·금액 오류 " + invalid + "개] " + String.join(" / ", invalidCells));
    if (!issues.isEmpty()) throw new IllegalArgumentException("선택한 방식: " + (includeHeaders ? "기준 포함" : "금액만") + " / " + dimensions + "차원\n"
        + String.join("\n", issues) + "\n화면의 ‘회색 예시가 있는 양식 다운로드’를 참고해 수정한 뒤 다시 올려 주세요. 현재 작성한 가격표는 유지됩니다.");
  }

  private static boolean headerShape(Sheet sheet, int[] b, int dimensions) {
    if (b[2] <= b[0] || b[3] <= b[1] || dimensions == 1 && b[2] - b[0] != 1) return false;
    Cell corner = readCell(sheet, b[0], b[1]);
    if (!empty(corner) && corner.getCellType() != CellType.STRING) return false;
    try {
      BigDecimal prev = null;
      for (int c = b[1] + 1; c <= b[3]; c++) {
        BigDecimal n = boundCell(readCell(sheet, b[0], c), "X");
        prev = n;
      }
      if (dimensions == 2) {
        prev = null;
        for (int r = b[0] + 1; r <= b[2]; r++) {
          BigDecimal n = boundCell(readCell(sheet, r, b[1]), "Y");
          prev = n;
        }
      }
      return true;
    } catch (IllegalArgumentException ex) { return false; }
  }
  private static void checkHeaderCells(Sheet sheet, int row, int column, int count, boolean x, List<String> issues) {
    BigDecimal previous = null; String previousAt = null;
    for (int i = 0; i < count; i++) {
      int r = row + (x ? 0 : i), c = column + (x ? i : 0);
      String at = new CellReference(r, c).formatAsString();
      try {
        BigDecimal value = boundCell(readCell(sheet, r, c), (x ? "X" : "Y") + "축 " + at);
        if (previous != null && value.compareTo(previous) <= 0)
          issues.add("[기준 " + (value.compareTo(previous) == 0 ? "중복" : "순서 오류") + "] " + previousAt + "=" + previous.toPlainString() + ", " + at + "=" + value.toPlainString() + ". 기준값은 중복 없이 작은 순서로 입력하세요. 연결된 금액 행·열도 함께 확인하세요.");
        previous = value; previousAt = at;
      } catch (IllegalArgumentException ex) { issues.add(ex.getMessage()); }
      if (issues.size() >= 20) return;
    }
  }
  private static Cell readCell(Sheet sheet, int row, int column) {
    Row r = sheet.getRow(row); return r == null ? null : r.getCell(column);
  }
  private static String range(int r1, int c1, int r2, int c2) {
    return new CellReference(r1, c1).formatAsString() + ":" + new CellReference(r2, c2).formatAsString();
  }
  private static void greyExamples(XSSFWorkbook workbook, Sheet sheet) {
    Font font = workbook.createFont(); font.setColor(IndexedColors.GREY_40_PERCENT.getIndex());
    java.util.Map<Short, CellStyle> styles = new java.util.HashMap<>();
    for (Row row : sheet) for (Cell cell : row) {
      if (cell.getCellType() != CellType.NUMERIC && cell.getCellType() != CellType.STRING) continue;
      CellStyle style = styles.computeIfAbsent(cell.getCellStyle().getIndex(), key -> {
        CellStyle copy = workbook.createCellStyle(); copy.cloneStyleFrom(cell.getCellStyle()); copy.setFont(font); return copy;
      });
      cell.setCellStyle(style);
    }
  }
  private static void formatHelp(XSSFWorkbook workbook, Sheet sheet) {
    CellStyle style = workbook.createCellStyle(); style.setWrapText(true);
    for (Row row : sheet) { row.setHeightInPoints(34); for (Cell cell : row) cell.setCellStyle(style); }
    cell(sheet, 0, 0).setCellStyle(headingStyle(workbook)); sheet.getRow(0).setHeightInPoints(30);
  }

  private static BigDecimal boundCell(Cell cell, String name) {
    if (empty(cell) || cell.getCellType() == CellType.FORMULA
        || cell.getCellType() != CellType.NUMERIC && cell.getCellType() != CellType.STRING
        || cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell))
      throw new IllegalArgumentException(name + ": 기준값을 숫자로 입력해 주세요.");
    try {
      return new BigDecimal(cell.getCellType() == CellType.NUMERIC
          ? ((XSSFCell) cell).getRawValue() : cell.getStringCellValue().trim());
    } catch (NumberFormatException e) { throw new IllegalArgumentException(name + ": 숫자가 아닙니다."); }
  }
  private static String outsidePolicy(String value) {
    if (blank(value)) return "ERROR";
    if ("ERROR".equals(value) || "EDGE".equals(value)) return value;
    throw new IllegalArgumentException("가격표설정의 표 밖 입력은 ERROR(계산 중단) 또는 EDGE(끝 기준 가격 적용)로 입력해 주세요.");
  }
  private static String setting(Sheet sheet, int row) {
    Cell cell = cell(sheet, row, 1);
    if (empty(cell)) return "";
    if (cell.getCellType() == CellType.STRING) return cell.getStringCellValue().trim();
    if (row == 0 && cell.getCellType() == CellType.NUMERIC) return new BigDecimal(((XSSFCell) cell).getRawValue()).stripTrailingZeros().toPlainString();
    throw new IllegalArgumentException("가격표설정 B" + (row + 1) + " 설정값은 수식이 아닌 문자로 입력해 주세요. 기본 비교: LE/LT/GE/GT, 표 밖 입력: ERROR/EDGE.");
  }
  private static List<String> readOperators(String value, int count, String name) {
    if (blank(value)) return null;
    List<String> result = java.util.Arrays.stream(value.split("\\|", -1))
        .map(String::trim).map(s -> s.isEmpty() ? null : s).toList();
    boundaryOperators(result, count, name); return result;
  }
  private static void boundaryOperators(List<String> values, int count, String axis) {
    if (values == null || values.isEmpty()) return;
    if (values.size() != count) throw new IllegalArgumentException(axis + " 개별 포함 여부의 개수가 기준값 수와 다릅니다.");
    for (String value : values) if (value != null) operatorLabel(value);
  }
  private static void direction(String operator, List<String> overrides, String axis) {
    boolean lower = "GE".equals(operator) || "GT".equals(operator);
    if (overrides != null) for (String value : overrides) if (value != null
        && lower != ("GE".equals(value) || "GT".equals(value)))
      throw new IllegalArgumentException(axis + "의 큰 기준(LE/LT)과 작은 기준(GE/GT)을 섞을 수 없습니다.");
  }
  private static void writeSettings(XSSFWorkbook workbook, int dimensions, String x, String y,
      List<String> xops, List<String> yops, String xp, String yp) {
    Sheet settings = workbook.createSheet("가격표설정");
    String[] labels = {"차원", "X축 기본 비교", "Y축 기본 비교", "X축 개별 기준", "Y축 개별 기준", "X축 표 밖 입력", "Y축 표 밖 입력"};
    String[] values = {Integer.toString(dimensions), x, dimensions == 2 ? y : "",
        joinOperators(xops), dimensions == 2 ? joinOperators(yops) : "", outsidePolicy(xp), dimensions == 2 ? outsidePolicy(yp) : ""};
    for (int i = 0; i < labels.length; i++) { text(settings, i, 0, labels[i]); text(settings, i, 1, values[i]); }
    text(settings, 8, 0, "LE/LT=큰 기준 적용(401→500), GE/GT=작은 기준 적용(401→400). 같은 방향끼리만 개별 지정. 빈 항목은 기본 비교 사용.");
    text(settings, 9, 0, "표 밖 입력: ERROR=계산 중단, EDGE=끝 기준 가격 적용. 주문 최소·최대값은 변경하지 않습니다.");
    settings.setColumnWidth(0, 26 * 256); settings.setColumnWidth(1, 80 * 256);
  }
  private static String joinOperators(List<String> values) {
    return values == null ? "" : values.stream().map(v -> v == null ? "" : v).collect(java.util.stream.Collectors.joining("|"));
  }
  private static void writeTable(XSSFWorkbook workbook, MatrixExport request, List<List<BigDecimal>> amounts) {
    Sheet sheet = workbook.createSheet("가격표");
    CellStyle heading = headingStyle(workbook), money = moneyStyle(workbook);
    text(sheet, 0, 0, request.dimensions() == 2 ? "Y 기준 / X 기준" : "X 기준");
    for (int c = 0; c < request.xHeaders().size(); c++) {
      axisValue(sheet, 0, c + 1, request.xHeaders().get(c), heading); sheet.setColumnWidth(c + 1, 18 * 256);
    }
    for (int r = 0; r < amounts.size(); r++) {
      if (request.dimensions() == 2) axisValue(sheet, r + 1, 0, request.yHeaders().get(r), heading);
      else text(sheet, r + 1, 0, "금액(원)");
      for (int c = 0; c < amounts.get(r).size(); c++) numeric(sheet, r + 1, c + 1, amounts.get(r).get(c), money);
    }
    sheet.setColumnWidth(0, 22 * 256); sheet.createFreezePane(1, 1);
    writeSettings(workbook, request.dimensions(), request.xOperator(), request.yOperator(), request.xOperators(), request.yOperators(), request.xOutsidePolicy(), request.yOutsidePolicy());
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
    boundaryOperators(request.xOperators(), columns, "X축");
    direction(request.xOperator(), request.xOperators(), "X축");
    label(request.title(), "항 이름", 200, false);
    label(request.xLabel(), "X축 항목 이름", 120, true);
    if (request.dimensions() == 2) {
      headers(request.yHeaders(), rows, "Y축");
      operatorLabel(request.yOperator());
      boundaryOperators(request.yOperators(), rows, "Y축");
      direction(request.yOperator(), request.yOperators(), "Y축");
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
          || value.precision() > 24 || (long) value.precision() - value.scale() > 24)
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
    if ("GE".equals(operator)) return "작은 기준 적용 · 기준값 이상 (≥)";
    if ("GT".equals(operator)) return "작은 기준 적용 · 기준값 초과 (>)";
    throw new IllegalArgumentException("축의 기준은 LE·LT(큰 기준) 또는 GE·GT(작은 기준)로 선택해 주세요.");
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
