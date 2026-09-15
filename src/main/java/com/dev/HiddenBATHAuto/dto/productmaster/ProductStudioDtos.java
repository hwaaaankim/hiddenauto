package com.dev.HiddenBATHAuto.dto.productmaster;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Versioned, product-local question schema. Keys are opaque identifiers, never labels. */
public final class ProductStudioDtos {
  private ProductStudioDtos() {}

  public enum Control {
    RADIO,
    CHECKBOX,
    NUMBER,
    TEXT,
    TEXTAREA,
    FILE
  }

  public enum Operator {
    EQ,
    NE,
    GT,
    GE,
    LT,
    LE,
    RANGE,
    PRESENT,
    ABSENT
  }

  public enum Effect {
    SHOW,
    HIDE,
    ALLOW,
    REQUIRE,
    OPTIONAL
  }

  public record Labels(String customer, String production, String management) {}

  public record Field(
      String key,
      Labels labels,
      String namePart,
      boolean required,
      boolean allowNegative,
      BigDecimal min,
      BigDecimal max,
      BigDecimal step,
      Integer minLength,
      Integer maxLength,
      String format,
      String unit,
      List<String> extensions,
      Integer minFiles,
      Integer maxFiles,
      Integer maxFileMB) {}

  public record Choice(String key, Labels labels, String namePart, List<String> assetIds) {}

  public record GroupEdit(
      Long id,
      Long version,
      Labels labels,
      String role,
      Control control,
      boolean nonStandard,
      boolean includeInName,
      boolean active,
      String question,
      String guide,
      List<Field> fields) {}

  public record ValueEdit(Long id, Long version, Labels labels, String namePart, boolean active) {}

  public record ValueView(
      Long id,
      long version,
      String key,
      Labels labels,
      String namePart,
      boolean active,
      List<AssetView> assets) {}

  public record GroupView(
      Long id,
      long version,
      String key,
      Labels labels,
      String role,
      Control control,
      boolean nonStandard,
      boolean includeInName,
      boolean active,
      String question,
      String guide,
      List<Field> fields,
      List<ValueView> values,
      List<AssetView> assets) {}

  public record GroupSelection(Long groupId, List<Long> valueIds, Map<String, Object> inputs) {}

  /**
   * Prefix/suffix are conditional on a non-empty name part. before is emitted only between parts.
   */
  public record NameToken(Long groupId, String before, String prefix, String suffix) {}

  public record Variant(Long groupId, List<Long> valueIds, Map<String, Object> inputs) {}

  public record GenerateRequest(
      List<GroupSelection> groups, List<NameToken> nameTokens, int offset, int limit) {}

  public record PreviewRow(
      String key,
      String productName,
      List<Variant> variants,
      Long duplicateId,
      String duplicateName,
      List<String> errors) {}

  public record Preview(
      long combinations, int offset, int limit, List<PreviewRow> rows, String definitionStamp) {}

  public record RegistrationRow(
      String key, String productName, int initialStock, List<String> assetIds) {}

  public record RegisterRequest(
      GenerateRequest generation, String definitionStamp, List<RegistrationRow> rows) {}

  public record ProductEdit(
      Long version,
      String productName,
      String description,
      String status,
      List<Variant> variants,
      List<NameToken> nameTokens,
      List<String> assetIds) {}

  public record Condition(
      String groupKey,
      String fieldKey,
      Operator operator,
      List<String> choiceKeys,
      String text,
      BigDecimal lower,
      BigDecimal upper,
      boolean lowerInclusive,
      boolean upperInclusive) {}

  public record Action(String targetKey, Effect effect, List<String> choiceKeys) {}

  public record Rule(
      String key, String name, String match, List<Condition> conditions, List<Action> actions) {}

  public record Question(
      String key,
      Long groupId,
      Labels labels,
      Control control,
      boolean fixed,
      boolean visible,
      boolean required,
      boolean requireRule,
      String question,
      String guide,
      List<Choice> choices,
      List<Field> fields,
      List<String> assetIds) {}

  public record Process(int schemaVersion, List<Question> questions, List<Rule> rules) {}

  public record ProcessSave(Long version, Process process, boolean publish) {}

  public record Answer(List<String> choices, Map<String, Object> fields) {}

  public record EvaluateRequest(Process process, Map<String, Answer> answers) {}

  public record QuestionState(
      Question question,
      boolean visible,
      boolean required,
      List<String> allowed,
      Answer answer,
      List<String> errors,
      List<String> matchedRules) {}

  public record Evaluation(
      List<QuestionState> questions,
      Map<String, Answer> answers,
      List<String> errors,
      boolean complete) {}

  public record Issue(String severity, String code, String location, String message) {}

  public record Validation(boolean valid, boolean exhaustive, long scenarios, List<Issue> issues) {}

  public record AssetView(
      String id, String name, String type, long size, String url, boolean image) {}

  public record ProductView(
      Long id,
      long version,
      String productName,
      String productCode,
      String catalogCode,
      String token,
      boolean nonStandard,
      String status,
      String registrationStatus,
      int stock,
      String description,
      List<Variant> variants,
      List<NameToken> nameTokens,
      Process process,
      List<AssetView> assets,
      List<AssetView> processAssets,
      List<GroupView> groups) {}

  public record InputFilter(
      Long groupId, String fieldKey, BigDecimal min, BigDecimal max, String contains) {}

  public record ProductFilter(
      boolean nonStandard,
      String keyword,
      String status,
      Map<Long, List<Long>> options,
      List<Long> customGroups,
      List<InputFilter> inputs,
      int page,
      int size) {}

  public record ProductPage(
      List<ProductView> content, long totalElements, int totalPages, int page, int size) {}

  public record PublicProduct(
      String productName,
      String catalogCode,
      String status,
      List<AssetView> assets,
      List<String> productAssetIds,
      Process process) {}

  public record CatalogOption(String key, String label, List<AssetView> assets) {}

  public record CatalogStep(
      Long groupId, String label, List<CatalogOption> options, List<AssetView> assets) {}

  public record CatalogItem(
      String productName, String catalogCode, String token, boolean nonStandard, String status) {}

  public record CatalogResult(
      CatalogStep next, long total, List<CatalogItem> products, int page, int totalPages) {}
}
