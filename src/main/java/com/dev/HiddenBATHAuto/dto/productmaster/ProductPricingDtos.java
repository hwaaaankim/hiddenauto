package com.dev.HiddenBATHAuto.dto.productmaster;

import com.dev.HiddenBATHAuto.dto.productmaster.ProductStudioDtos.*;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductStudioDtos.Process;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Product-local, versioned price formula; monetary arithmetic always uses decimal values. */
public final class ProductPricingDtos {
  private ProductPricingDtos() {}
  public enum TermType { FIXED, MULTIPLY, MATRIX_1D, MATRIX_2D }
  public enum Threshold { LE, LT }
  public record Axis(String fieldKey, Threshold operator, List<BigDecimal> headers,
      List<Threshold> operators) {
    // Existing definitions and callers retain their axis-wide boundary behavior.
    public Axis(String fieldKey, Threshold operator, List<BigDecimal> headers) {
      this(fieldKey, operator, headers, null);
    }
    public Threshold operatorAt(int index) {
      return operators == null || operators.isEmpty() || operators.get(index) == null
          ? operator : operators.get(index);
    }
  }
  public record Bundle(String key, String label, List<Field> fields) {}
  public record PricingGroup(Long groupId, String groupKey, String label, Control control,
      List<Bundle> bundles, List<Choice> choices) {}
  public record Term(String key, String name, String groupKey, String bundleKey,
      List<String> fieldKeys, TermType type, BigDecimal amount, Map<String, BigDecimal> rates,
      Axis xAxis, Axis yAxis, List<List<BigDecimal>> values, String match,
      List<Condition> conditions, List<String> choiceKeys) {}
  public record Definition(int schemaVersion, String schemaFingerprint, List<Term> terms,
      Map<String, Answer> testAnswers) {}
  public record Context(Long productId, long version, boolean ready, List<String> issues,
      Process process, Map<String, Answer> fixedAnswers, List<PricingGroup> groups,
      String schemaFingerprint, boolean registered, boolean stale, Definition definition) {}
  public record SaveRequest(Long version, Definition definition) {}
  public record EvaluateRequest(Definition definition, Map<String, Answer> answers) {}
  public record CopyRequest(Long sourceProductId, Long version, Long sourceVersion) {}
  public record CopySource(Long productId, String productName, String catalogCode, long version) {}
  public record StatusRequest(List<Long> productIds) {}
  public record Status(Long productId, boolean registered, boolean ready, boolean stale,
      String message) {}
  public record TermResult(String key, String name, boolean applied, BigDecimal amount,
      String explanation) {}
  public record Calculation(BigDecimal total, boolean complete, List<String> errors,
      List<TermResult> terms, Evaluation evaluation) {}
  public record ActualCalculation(Long actualId, BigDecimal total, boolean complete,
      List<String> errors, List<TermResult> terms, Evaluation evaluation) {}
}
