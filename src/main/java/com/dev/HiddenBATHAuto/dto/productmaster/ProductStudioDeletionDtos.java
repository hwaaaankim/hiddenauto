package com.dev.HiddenBATHAuto.dto.productmaster;

import java.util.List;

/** Versioned requests for permanent deletion of products and actual specifications. */
public final class ProductStudioDeletionDtos {
  private ProductStudioDeletionDtos() {}

  public record ProductDeleteTarget(Long id, Long version, Long actualCount) {}

  public record ActualDeleteTarget(Long id, Long version) {}

  public record ProductDeleteRequest(Boolean nonStandard, List<ProductDeleteTarget> items) {}

  public record ActualDeleteRequest(Long productVersion, List<ActualDeleteTarget> items) {}

  public record DeleteResult(int deletedProducts, int deletedActuals, long deletedStock) {}
}
