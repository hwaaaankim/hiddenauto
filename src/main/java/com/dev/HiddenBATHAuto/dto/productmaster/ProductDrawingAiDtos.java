package com.dev.HiddenBATHAuto.dto.productmaster;

import java.util.List;

public final class ProductDrawingAiDtos {
  private ProductDrawingAiDtos() {}

  /** An unsaved, editable draw.io document. No uploaded image or API credential is returned. */
  public record DraftResult(String xml, List<String> warnings, String summary, String traceId) {
    public DraftResult(String xml, List<String> warnings, String summary) {
      this(xml, warnings, summary, null);
    }
  }
}
