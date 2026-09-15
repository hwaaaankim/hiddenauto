package com.dev.HiddenBATHAuto.dto.productmaster;

import com.dev.HiddenBATHAuto.enums.productmaster.ProductStockMovementType;
import java.time.LocalDateTime;
import java.util.List;

/** Shared envelopes and stock operations for the unified product workflow. */
public final class ProductMasterDtos {
  private ProductMasterDtos() {}

  public record ApiResponse<T>(boolean success, String message, T data) {
    public static <T> ApiResponse<T> ok(T data) {
      return new ApiResponse<>(true, null, data);
    }

    public static <T> ApiResponse<T> ok(String message, T data) {
      return new ApiResponse<>(true, message, data);
    }

    public static <T> ApiResponse<T> fail(String message) {
      return new ApiResponse<>(false, message, null);
    }
  }

  public record StockAdjustmentRequest(
      ProductStockMovementType movementType, Integer quantityDelta, String reason) {}

  public record VoidStockMovementRequest(String reason) {}

  public record StockMovementView(
      Long id,
      String type,
      int delta,
      int before,
      int after,
      String reason,
      String actor,
      LocalDateTime at,
      boolean voided,
      String voidReason) {}

  public record StockView(Long productId, int stock, List<StockMovementView> movements) {}
}
