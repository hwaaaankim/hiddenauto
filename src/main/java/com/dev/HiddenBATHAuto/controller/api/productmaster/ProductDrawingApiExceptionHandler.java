package com.dev.HiddenBATHAuto.controller.api.productmaster;

import com.dev.HiddenBATHAuto.dto.productmaster.ProductMasterDtos.ApiResponse;
import com.dev.HiddenBATHAuto.service.productmaster.ProductDrawingXmlValidator.InvalidDrawingException;
import java.util.NoSuchElementException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Slf4j
@RestControllerAdvice(assignableTypes = ProductDrawingApiController.class)
public class ProductDrawingApiExceptionHandler {
  @ExceptionHandler(InvalidDrawingException.class)
  public ResponseEntity<ApiResponse<Void>> invalidDrawing(InvalidDrawingException exception) {
    return error(exception.isTooLarge() ? HttpStatus.PAYLOAD_TOO_LARGE : HttpStatus.BAD_REQUEST,
        exception.getMessage());
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ApiResponse<Void>> denied(AccessDeniedException exception) {
    return error(HttpStatus.FORBIDDEN, "도면을 관리할 권한이 없습니다.");
  }

  @ExceptionHandler(NoSuchElementException.class)
  public ResponseEntity<ApiResponse<Void>> notFound(NoSuchElementException exception) {
    return error(HttpStatus.NOT_FOUND, exception.getMessage());
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ApiResponse<Void>> badRequest(IllegalArgumentException exception) {
    return error(HttpStatus.BAD_REQUEST, exception.getMessage());
  }

  @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
  public ResponseEntity<ApiResponse<Void>> unreadable(Exception exception) {
    return error(HttpStatus.BAD_REQUEST, "도면 요청의 형식 또는 버전 정보를 확인해 주세요.");
  }

  @ExceptionHandler(IllegalStateException.class)
  public ResponseEntity<ApiResponse<Void>> conflict(IllegalStateException exception) {
    return error(HttpStatus.CONFLICT, exception.getMessage());
  }

  @ExceptionHandler({DataIntegrityViolationException.class, ObjectOptimisticLockingFailureException.class,
      PessimisticLockingFailureException.class})
  public ResponseEntity<ApiResponse<Void>> concurrentChange(RuntimeException exception) {
    return error(HttpStatus.CONFLICT, "도면 또는 제품 정보가 변경되었습니다. 최신 내용을 불러온 뒤 다시 등록해 주세요.");
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> unexpected(Exception exception) {
    log.error("제품 도면 처리 중 오류가 발생했습니다.", exception);
    return error(HttpStatus.INTERNAL_SERVER_ERROR, "도면을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.");
  }

  private static ResponseEntity<ApiResponse<Void>> error(HttpStatus status, String message) {
    return ResponseEntity.status(status).body(ApiResponse.fail(message));
  }
}
