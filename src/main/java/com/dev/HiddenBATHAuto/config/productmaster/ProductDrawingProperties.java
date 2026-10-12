package com.dev.HiddenBATHAuto.config.productmaster;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Drawing editor settings. API credentials remain in the existing hiddenbath.rag.openai settings. */
@Component
@ConfigurationProperties(prefix = "hiddenbath.product-drawing")
public class ProductDrawingProperties {
  private String editorUrl = "/administration/assets/libs/diagrams/index.html";
  private String visionModel = "gpt-6.1-sol";
  private String visionReasoningEffort = "medium";
  private int maxDraftCompletionTokens = 32768;
  private boolean diagnosticLoggingEnabled = false;
  private int diagnosticLogMaxChars = 65536;
  private long maxImageBytes = 10L * 1024 * 1024;
  private long maxImagePixels = 24_000_000L;
  private int maxImageDimension = 1600;
  private int maxDraftPages = 4;
  private int maxDraftShapesPerPage = 180;
  private int maxConcurrentDrafts = 2;

  public String getEditorUrl() { return editorUrl; }
  public void setEditorUrl(String value) { editorUrl = value; }
  public String getVisionModel() { return visionModel; }
  public void setVisionModel(String value) { visionModel = value; }
  public String getVisionReasoningEffort() { return visionReasoningEffort; }
  public void setVisionReasoningEffort(String value) {
    if (value == null || !java.util.Set.of("low", "medium", "high", "xhigh", "max").contains(value))
      throw new IllegalArgumentException("도면 추론 수준은 low, medium, high, xhigh, max 중 하나여야 합니다.");
    visionReasoningEffort = value;
  }
  public int getMaxDraftCompletionTokens() { return maxDraftCompletionTokens; }
  public void setMaxDraftCompletionTokens(int value) { maxDraftCompletionTokens = Math.max(4000, Math.min(value, 64000)); }
  public boolean isDiagnosticLoggingEnabled() { return diagnosticLoggingEnabled; }
  public void setDiagnosticLoggingEnabled(boolean value) { diagnosticLoggingEnabled = value; }
  public int getDiagnosticLogMaxChars() { return diagnosticLogMaxChars; }
  public void setDiagnosticLogMaxChars(int value) { diagnosticLogMaxChars = Math.max(4096, Math.min(value, 262144)); }
  public long getMaxImageBytes() { return maxImageBytes; }
  public void setMaxImageBytes(long value) { maxImageBytes = Math.max(1024, Math.min(value, 20L * 1024 * 1024)); }
  public long getMaxImagePixels() { return maxImagePixels; }
  public void setMaxImagePixels(long value) { maxImagePixels = Math.max(100_000, Math.min(value, 40_000_000)); }
  public int getMaxImageDimension() { return maxImageDimension; }
  public void setMaxImageDimension(int value) { maxImageDimension = Math.max(640, Math.min(value, 2048)); }
  public int getMaxDraftPages() { return maxDraftPages; }
  public void setMaxDraftPages(int value) { maxDraftPages = Math.max(1, Math.min(value, 4)); }
  public int getMaxDraftShapesPerPage() { return maxDraftShapesPerPage; }
  public void setMaxDraftShapesPerPage(int value) { maxDraftShapesPerPage = Math.max(20, Math.min(value, 300)); }
  public int getMaxConcurrentDrafts() { return maxConcurrentDrafts; }
  public void setMaxConcurrentDrafts(int value) { maxConcurrentDrafts = Math.max(1, Math.min(value, 8)); }
}
