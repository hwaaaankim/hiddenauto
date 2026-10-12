package com.dev.HiddenBATHAuto.dto.productmaster;

import java.time.LocalDateTime;
import java.util.List;

public final class ProductDrawingDtos {
  private ProductDrawingDtos() {}

  public record SaveRequest(String xml, Long expectedVersion) {}

  public record DrawingView(
      String kind, Long targetId, Long productId, String label, String xml, Long version,
      LocalDateTime updatedAt, String updatedBy, int pageCount) {}

  public record TemplateView(
      String kind, Long targetId, Long productId, String label, Long version,
      LocalDateTime updatedAt, int pageCount) {}

  public record TemplatePage(List<TemplateView> items, boolean hasNext) {}
}
