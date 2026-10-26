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
  public enum TermRole { PRICE, FACTOR }
  public enum MultiplierAggregation { EXACT_ONE, SUM, PRODUCT }
  public record MultiplierLink(String key, String name, List<String> termKeys,
      MultiplierAggregation aggregation) {
    public MultiplierLink(String key, String name, List<String> termKeys) {
      this(key, name, termKeys, null);
    }
  }
  public enum Threshold { LE, LT, GE, GT }
  public enum OutsidePolicy { ERROR, EDGE }
  public record Axis(String fieldKey, Threshold operator, List<BigDecimal> headers,
      List<Threshold> operators, OutsidePolicy outsidePolicy) {
    public Axis(String fieldKey, Threshold operator, List<BigDecimal> headers, List<Threshold> operators) {
      this(fieldKey, operator, headers, operators, null);
    }
    // Existing definitions and callers retain their axis-wide boundary behavior.
    public Axis(String fieldKey, Threshold operator, List<BigDecimal> headers) {
      this(fieldKey, operator, headers, null);
    }
    public Threshold operatorAt(int index) {
      return operators == null || operators.isEmpty() || operators.get(index) == null
          ? operator : operators.get(index);
    }
    public boolean usesEdgeOutside() { return outsidePolicy == OutsidePolicy.EDGE; }
  }
  public record Bundle(String key, String label, List<Field> fields) {}
  public record PricingGroup(Long groupId, String groupKey, String label, Control control,
      List<Bundle> bundles, List<Choice> choices) {}
  public record Term(String key, String name, String groupKey, String bundleKey,
      List<String> fieldKeys, TermType type, BigDecimal amount, Map<String, BigDecimal> rates,
      Axis xAxis, Axis yAxis, List<List<BigDecimal>> values, String match,
      List<Condition> conditions, List<String> choiceKeys, OptionRangePricing optionPricing,
      OptionTarget optionTarget, TermRole role, List<MultiplierLink> multipliers) {
    public Term(String key, String name, String groupKey, String bundleKey,
        List<String> fieldKeys, TermType type, BigDecimal amount, Map<String, BigDecimal> rates,
        Axis xAxis, Axis yAxis, List<List<BigDecimal>> values, String match,
        List<Condition> conditions, List<String> choiceKeys, OptionRangePricing optionPricing,
        OptionTarget optionTarget) {
      this(key, name, groupKey, bundleKey, fieldKeys, type, amount, rates, xAxis, yAxis,
          values, match, conditions, choiceKeys, optionPricing, optionTarget, null, List.of());
    }
    public Term(String key, String name, String groupKey, String bundleKey,
        List<String> fieldKeys, TermType type, BigDecimal amount, Map<String, BigDecimal> rates,
        Axis xAxis, Axis yAxis, List<List<BigDecimal>> values, String match,
        List<Condition> conditions, List<String> choiceKeys, OptionRangePricing optionPricing) {
      this(key, name, groupKey, bundleKey, fieldKeys, type, amount, rates, xAxis, yAxis,
          values, match, conditions, choiceKeys, optionPricing, null);
    }
    public Term(String key, String name, String groupKey, String bundleKey,
        List<String> fieldKeys, TermType type, BigDecimal amount, Map<String, BigDecimal> rates,
        Axis xAxis, Axis yAxis, List<List<BigDecimal>> values, String match,
        List<Condition> conditions, List<String> choiceKeys) {
      this(key, name, groupKey, bundleKey, fieldKeys, type, amount, rates, xAxis, yAxis,
          values, match, conditions, choiceKeys, null);
    }
  }
  /** The chosen option being priced by a numeric term; independent options remain additive. */
  public record OptionTarget(String groupKey, String choiceKey) {}
  /** One option term chooses exactly one price; the base amount is never added again. */
  public record OptionRangePricing(String groupKey, Axis axis, List<BigDecimal> amounts,
      BigDecimal aboveAmount) {}
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
  public record FactorResult(String key, String name, BigDecimal value) {}
  public record MultiplierResult(String key, String name, MultiplierAggregation aggregation,
      List<FactorResult> terms, BigDecimal value) {}
  public record TermResult(String key, String name, boolean applied, BigDecimal amount,
      String explanation, TermRole role, BigDecimal baseAmount, List<MultiplierResult> multipliers) {
    public TermResult(String key, String name, boolean applied, BigDecimal amount, String explanation) {
      this(key, name, applied, amount, explanation, TermRole.PRICE, amount, List.of());
    }
  }
  public record Calculation(BigDecimal total, boolean complete, List<String> errors,
      List<TermResult> terms, Evaluation evaluation) {}
  public record ActualCalculation(Long actualId, BigDecimal total, boolean complete,
      List<String> errors, List<TermResult> terms, Evaluation evaluation) {}
}
