package com.dev.HiddenBATHAuto.service.productmaster;

import com.dev.HiddenBATHAuto.dto.productmaster.ProductStudioDtos.AssetView;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductStudioDtos.Choice;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductStudioDtos.Field;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductStudioDtos.GroupView;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductStudioDtos.Process;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductStudioDtos.Question;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** GROUP ownership stores both question media and field media; the schema defines their scope. */
public final class ProductStudioMedia {
  private ProductStudioMedia() {}

  /** Used standard fields may replace guidance files without changing any input definition. */
  public static boolean sameFieldStructure(List<Field> before, List<Field> after) {
    return withoutFieldAssets(before).equals(withoutFieldAssets(after));
  }

  private static List<Field> withoutFieldAssets(List<Field> fields) {
    return ProductStudioEngine.list(fields).stream()
        .map(field -> new Field(field.key(), field.labels(), field.namePart(), field.required(),
            field.allowNegative(), field.min(), field.max(), field.step(), field.minLength(),
            field.maxLength(), field.format(), field.unit(), field.extensions(), field.minFiles(),
            field.maxFiles(), field.maxFileMB(), field.guide(), field.bundleKey(), List.of()))
        .toList();
  }

  /** Exclude explanatory media only. Preset file answers and every pricing/branch property remain. */
  public static Process withoutProcessAssets(Process process) {
    List<Question> questions = process.questions() == null ? null : process.questions().stream()
        .map(question -> new Question(question.key(), question.groupId(), question.labels(),
            question.control(), question.fixed(), question.visible(), question.required(),
            question.requireRule(), question.question(), question.guide(),
            question.choices() == null ? null : question.choices().stream()
                .map(choice -> new Choice(choice.key(), choice.labels(), choice.namePart(),
                    List.of(), choice.guide())).toList(),
            question.fields() == null ? null : withoutFieldAssets(question.fields()),
            List.of(), question.numberCases(), question.preset()))
        .toList();
    return new Process(process.schemaVersion(), questions, process.rules(),
        process.inputLimits(), process.notices());
  }

  /** Files belonging to a field must not also appear as the whole question's illustration. */
  public static List<AssetView> questionAssets(GroupView group) {
    Set<String> fieldIds = new LinkedHashSet<>();
    for (Field field : ProductStudioEngine.list(group.fields()))
      fieldIds.addAll(ProductStudioEngine.list(field.assetIds()));
    return ProductStudioEngine.list(group.assets()).stream()
        .filter(asset -> !fieldIds.contains(asset.id())).toList();
  }

  /** Standard input combinations receive only the guidance files for fields they actually use. */
  public static List<AssetView> inputAssets(GroupView group, Map<String, Object> inputs) {
    Set<String> fieldIds = new LinkedHashSet<>();
    for (Field field : ProductStudioEngine.list(group.fields()))
      if (ProductStudioEngine.map(inputs).containsKey(field.key()))
        fieldIds.addAll(ProductStudioEngine.list(field.assetIds()));
    return ProductStudioEngine.list(group.assets()).stream()
        .filter(asset -> fieldIds.contains(asset.id())).toList();
  }
}
