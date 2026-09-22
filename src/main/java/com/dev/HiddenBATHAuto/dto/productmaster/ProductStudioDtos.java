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
    OPTIONAL,
    RENAME
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
      Integer maxFileMB,
      String guide) {
    public Field(
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
        Integer maxFileMB) {
      this(
          key,
          labels,
          namePart,
          required,
          allowNegative,
          min,
          max,
          step,
          minLength,
          maxLength,
          format,
          unit,
          extensions,
          minFiles,
          maxFiles,
          maxFileMB,
          null);
    }
  }

  public record Choice(
      String key, Labels labels, String namePart, List<String> assetIds, String guide) {
    public Choice(String key, Labels labels, String namePart, List<String> assetIds) {
      this(key, labels, namePart, assetIds, null);
    }
  }

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
      List<Field> fields,
      Boolean askQuestion,
      boolean priceImpact) {
    public GroupEdit(
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
        List<Field> fields) {
      this(
          id,
          version,
          labels,
          role,
          control,
          nonStandard,
          includeInName,
          active,
          question,
          guide,
          fields,
          null,
          false);
    }
  }

  public record ValueEdit(
      Long id, Long version, Labels labels, String namePart, boolean active, String guide) {
    public ValueEdit(Long id, Long version, Labels labels, String namePart, boolean active) {
      this(id, version, labels, namePart, active, null);
    }
  }

  public record ValueView(
      Long id,
      long version,
      String key,
      Labels labels,
      String namePart,
      boolean active,
      List<AssetView> assets,
      String guide) {
    public ValueView(
        Long id,
        long version,
        String key,
        Labels labels,
        String namePart,
        boolean active,
        List<AssetView> assets) {
      this(id, version, key, labels, namePart, active, assets, null);
    }
  }

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
      List<AssetView> assets,
      boolean askQuestion,
      boolean priceImpact) {
    public GroupView(
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
        List<AssetView> assets) {
      this(
          id,
          version,
          key,
          labels,
          role,
          control,
          nonStandard,
          includeInName,
          active,
          question,
          guide,
          fields,
          values,
          assets,
          true,
          false);
    }
  }

  public record GroupSelection(Long groupId, List<Long> valueIds, Map<String, Object> inputs) {}

  /**
   * Prefix/suffix are conditional on a non-empty name part. before is emitted only between parts.
   */
  public record NameToken(Long groupId, String before, String prefix, String suffix) {}

  public record Variant(Long groupId, List<Long> valueIds, Map<String, Object> inputs) {}

  public record GenerateRequest(
      List<GroupSelection> groups,
      List<NameToken> nameTokens,
      int offset,
      int limit,
      boolean nonStandard) {
    public GenerateRequest(
        List<GroupSelection> groups, List<NameToken> nameTokens, int offset, int limit) {
      this(groups, nameTokens, offset, limit, false);
    }
  }

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
      String key,
      String productName,
      int initialStock,
      List<String> assetIds,
      BigDecimal productionHours,
      BigDecimal unitPrice,
      Long faqTopicId) {
    public RegistrationRow(
        String key, String productName, int initialStock, List<String> assetIds) {
      this(key, productName, initialStock, assetIds, null, null, null);
    }
  }

  public record RegisterRequest(
      GenerateRequest generation, String definitionStamp, List<RegistrationRow> rows) {}

  public record ProductEdit(
      Long version,
      String productName,
      String description,
      String status,
      List<Variant> variants,
      List<NameToken> nameTokens,
      List<String> assetIds,
      BigDecimal productionHours,
      BigDecimal unitPrice,
      Long faqTopicId) {
    public ProductEdit(
        Long version,
        String productName,
        String description,
        String status,
        List<Variant> variants,
        List<NameToken> nameTokens,
        List<String> assetIds) {
      this(
          version,
          productName,
          description,
          status,
          variants,
          nameTokens,
          assetIds,
          null,
          null,
          null);
    }
  }

  public record NumberCase(String key, String name, List<Condition> conditions, String guide) {}

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

  public record Action(
      String targetKey, Effect effect, List<String> choiceKeys, String questionText) {
    public Action(String targetKey, Effect effect, List<String> choiceKeys) {
      this(targetKey, effect, choiceKeys, null);
    }
  }

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
      List<String> assetIds,
      List<NumberCase> numberCases,
      Answer preset) {
    public Question(
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
        List<String> assetIds,
        List<NumberCase> numberCases) {
      this(
          key,
          groupId,
          labels,
          control,
          fixed,
          visible,
          required,
          requireRule,
          question,
          guide,
          choices,
          fields,
          assetIds,
          numberCases,
          null);
    }

    public Question(
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
        List<String> assetIds) {
      this(
          key,
          groupId,
          labels,
          control,
          fixed,
          visible,
          required,
          requireRule,
          question,
          guide,
          choices,
          fields,
          assetIds,
          List.of());
    }
  }

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
      List<GroupView> groups,
      BigDecimal productionHours,
      BigDecimal unitPrice,
      Long faqTopicId,
      long actualCount,
      int unallocatedStock) {
    public ProductView(
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
        List<GroupView> groups) {
      this(
          id,
          version,
          productName,
          productCode,
          catalogCode,
          token,
          nonStandard,
          status,
          registrationStatus,
          stock,
          description,
          variants,
          nameTokens,
          process,
          assets,
          processAssets,
          groups,
          null,
          null,
          null,
          0,
          0);
    }
  }

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
      int size,
      Integer minActualCount,
      Integer maxActualCount) {
    public ProductFilter(
        boolean nonStandard,
        String keyword,
        String status,
        Map<Long, List<Long>> options,
        List<Long> customGroups,
        List<InputFilter> inputs,
        int page,
        int size) {
      this(nonStandard, keyword, status, options, customGroups, inputs, page, size, null, null);
    }
  }

  public record ProductPage(
      List<ProductView> content, long totalElements, int totalPages, int page, int size) {}

  public record PublicProduct(
      String productName,
      String catalogCode,
      String status,
      List<AssetView> assets,
      List<String> productAssetIds,
      Process process,
      BigDecimal productionHours,
      BigDecimal unitPrice,
      Long faqTopicId) {
    public PublicProduct(
        String productName,
        String catalogCode,
        String status,
        List<AssetView> assets,
        List<String> productAssetIds,
        Process process) {
      this(productName, catalogCode, status, assets, productAssetIds, process, null, null, null);
    }
  }

  public record CatalogOption(String key, String label, List<AssetView> assets, String guide) {
    public CatalogOption(String key, String label, List<AssetView> assets) {
      this(key, label, assets, null);
    }
  }

  public record CatalogStep(
      Long groupId, String label, List<CatalogOption> options, List<AssetView> assets) {}

  public record CatalogItem(
      String productName, String catalogCode, String token, boolean nonStandard, String status) {}

  public record CatalogResult(
      CatalogStep next, long total, List<CatalogItem> products, int page, int totalPages) {}
}
