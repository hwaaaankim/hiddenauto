package com.dev.HiddenBATHAuto.service.productmaster;

/** Only fixed, safe Korean messages are sent to the UI. Upstream response bodies are never exposed. */
public class ProductDrawingAiException extends RuntimeException {
  private final int status;
  private final String traceId;

  public ProductDrawingAiException(int status, String message) {
    this(status, message, null);
  }

  public ProductDrawingAiException(int status, String message, String traceId) {
    super(message);
    this.status = status;
    this.traceId = traceId != null && traceId.matches("[a-fA-F0-9-]{36}") ? traceId : null;
  }

  public int getStatus() { return status; }
  public String getTraceId() { return traceId; }
}
