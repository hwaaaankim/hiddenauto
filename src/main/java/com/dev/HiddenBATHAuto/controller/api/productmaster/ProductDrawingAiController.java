package com.dev.HiddenBATHAuto.controller.api.productmaster;

import com.dev.HiddenBATHAuto.dto.productmaster.ProductDrawingAiDtos.DraftResult;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductMasterDtos.ApiResponse;
import com.dev.HiddenBATHAuto.service.productmaster.ProductDrawingAiException;
import com.dev.HiddenBATHAuto.service.productmaster.ProductDrawingAiService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@RestController
@RequestMapping("/admin/api/product-master/drawings")
@PreAuthorize("hasRole('ADMIN')")
public class ProductDrawingAiController {
  private final ProductDrawingAiService service;

  public ProductDrawingAiController(ProductDrawingAiService service) { this.service = service; }

  @PostMapping(value = "/draft", consumes = "multipart/form-data")
  public ApiResponse<DraftResult> draft(@RequestPart("file") MultipartFile file,
      @RequestParam(value = "instruction", required = false) String instruction,
      @RequestParam(value = "traceId", required = false) String traceId) {
    return ApiResponse.ok(service.draft(file, instruction, traceId));
  }

  @ExceptionHandler(ProductDrawingAiException.class)
  public ResponseEntity<ApiResponse<Void>> failure(ProductDrawingAiException exception) {
    var response = ResponseEntity.status(exception.getStatus());
    if (exception.getTraceId() != null) response.header("X-Drawing-Trace-Id", exception.getTraceId());
    return response.body(ApiResponse.fail(exception.getMessage()));
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ResponseEntity<ApiResponse<Void>> tooLarge(MaxUploadSizeExceededException exception) {
    return ResponseEntity.status(413).body(ApiResponse.fail("업로드 이미지 용량이 서버 허용 한도를 초과했습니다."));
  }

  @ExceptionHandler({MissingServletRequestPartException.class, MultipartException.class})
  public ResponseEntity<ApiResponse<Void>> invalidUpload(Exception exception) {
    return ResponseEntity.badRequest().body(ApiResponse.fail("이미지 업로드 요청을 읽을 수 없습니다. 파일을 다시 선택해 주세요."));
  }
}
