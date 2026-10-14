package com.dev.HiddenBATHAuto.controller.api.productmaster;

import com.dev.HiddenBATHAuto.dto.productmaster.ProductMasterDtos.ApiResponse;
import com.dev.HiddenBATHAuto.service.productmaster.ProductPricingExcelService;
import com.dev.HiddenBATHAuto.service.productmaster.ProductPricingExcelService.MatrixExport;
import com.dev.HiddenBATHAuto.service.productmaster.ProductPricingExcelService.MatrixImport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

/** Excel actions only transform the editor's unsaved matrix and do not change any product. */
@RestController
@RequestMapping("/admin/api/product-master/studio/pricing/excel")
@PreAuthorize("hasRole('ADMIN')")
public class ProductPricingExcelController {
  private static final Logger log = LoggerFactory.getLogger(ProductPricingExcelController.class);
  private static final MediaType XLSX =
      MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
  private final ProductPricingExcelService excel;

  public ProductPricingExcelController(ProductPricingExcelService excel) { this.excel = excel; }

  @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ApiResponse<MatrixImport> importAmounts(
      @RequestPart("file") MultipartFile file, @RequestParam int dimensions) {
    return ApiResponse.ok("금액을 불러왔습니다. X·Y축 기준값을 확인하고 입력해 주세요.",
        excel.importAmounts(file, dimensions));
  }

  @GetMapping("/template")
  public ResponseEntity<byte[]> template(
      @RequestParam int dimensions,
      @RequestParam(defaultValue = "3") int rows,
      @RequestParam(defaultValue = "3") int columns) {
    return download(excel.template(dimensions, rows, columns), "price-matrix-template.xlsx");
  }

  @PostMapping("/export")
  public ResponseEntity<byte[]> export(@RequestBody MatrixExport request) {
    return download(excel.export(request), "price-matrix.xlsx");
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ApiResponse<Void>> invalid(IllegalArgumentException exception) {
    return ResponseEntity.badRequest().body(ApiResponse.fail(exception.getMessage()));
  }

  @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
      MissingServletRequestParameterException.class, MissingServletRequestPartException.class})
  public ResponseEntity<ApiResponse<Void>> unreadable(Exception exception) {
    return ResponseEntity.badRequest().body(ApiResponse.fail(
        "가격 엑셀 요청값을 확인해 주세요. 파일, 차원, 기준값과 금액이 필요합니다."));
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ResponseEntity<ApiResponse<Void>> tooLarge(MaxUploadSizeExceededException exception) {
    return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
        .body(ApiResponse.fail("업로드 용량이 서버 한도를 초과했습니다. 가격 엑셀은 5MB 이하로 사용해 주세요."));
  }

  @ExceptionHandler(MultipartException.class)
  public ResponseEntity<ApiResponse<Void>> multipart(MultipartException exception) {
    return ResponseEntity.badRequest().body(ApiResponse.fail("엑셀 업로드 요청을 읽을 수 없습니다."));
  }

  @ExceptionHandler(IllegalStateException.class)
  public ResponseEntity<ApiResponse<Void>> generation(IllegalStateException exception) {
    log.error("비규격 가격계산 엑셀 생성 실패", exception);
    return ResponseEntity.internalServerError().body(ApiResponse.fail("가격계산 엑셀을 만들 수 없습니다."));
  }

  private static ResponseEntity<byte[]> download(byte[] bytes, String filename) {
    return ResponseEntity.ok().contentType(XLSX)
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .contentLength(bytes.length).body(bytes);
  }
}
