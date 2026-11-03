package com.dev.HiddenBATHAuto.service.productmaster;

import com.dev.HiddenBATHAuto.dto.productmaster.ProductStudioDtos.*;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductStudioDtos.Process;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductPricingDtos.*;
import java.util.*;
import java.util.stream.Collectors;

/** Product-copy dependencies. Whole conditions/rules are retained or removed, never weakened. */
public final class ProductStudioCopyPolicy {
  private ProductStudioCopyPolicy() {}

  public record Plan(boolean branchesAllowed, List<String> branchReasons,
      List<String> optionWarnings, int retainedQuestions, int removedRules,
      int removedNumberCases, int removedLimits, int removedNotices) {}

  public static Plan plan(Process source, Set<Long> included) {
    Process retained = ProductStudioProcessSubset.retain(source, included);
    Set<String> keys = retained.questions().stream().map(Question::key).collect(Collectors.toSet());
    List<String> reasons = new ArrayList<>(), warnings = new ArrayList<>();
    for (Rule rule : list(source.rules())) {
      Set<String> missing = new LinkedHashSet<>();
      for (Condition c : list(rule.conditions())) if (!keys.contains(c.groupKey())) missing.add(c.groupKey());
      for (Action a : list(rule.actions())) if (!keys.contains(a.targetKey())) missing.add(a.targetKey());
      if (!missing.isEmpty()) reasons.add("분기 ‘" + rule.name() + "’에 필요한 질문 제외: "
          + missing.stream().map(k -> label(source, k)).collect(Collectors.joining(", ")));
    }
    int cases = 0;
    for (Question q : retained.questions()) {
      Question original = source.questions().stream().filter(x -> x.key().equals(q.key())).findFirst().orElseThrow();
      int removed = list(original.numberCases()).size() - list(q.numberCases()).size();
      cases += removed;
      if (removed > 0) for (NumberCase c : list(original.numberCases())) {
        List<String> missing = list(c.conditions()).stream().map(Condition::groupKey).filter(k -> !keys.contains(k)).distinct().toList();
        if (!missing.isEmpty()) warnings.add("숫자 옵션 조건 ‘" + c.name() + "’ 제외 (" + label(source, q.key())
            + "): 필요한 질문 ‘" + missing.stream().map(k -> label(source, k)).collect(Collectors.joining(", ")) + "’ 제외");
      }
    }
    int limits = list(source.inputLimits()).size() - list(retained.inputLimits()).size();
    int notices = list(source.notices()).size() - list(retained.notices()).size();
    Set<String> limitKeys = list(retained.inputLimits()).stream().map(InputLimit::key).collect(Collectors.toSet());
    for (InputLimit l : list(source.inputLimits())) if (!limitKeys.contains(l.key())) {
      Set<String> missing = new LinkedHashSet<>();
      if (!keys.contains(l.targetKey())) missing.add(l.targetKey());
      if (l.sourceKey() != null && !l.sourceKey().isBlank() && !keys.contains(l.sourceKey())) missing.add(l.sourceKey());
      list(l.cases()).forEach(c -> list(c.conditions()).stream().map(Condition::groupKey)
          .filter(k -> !keys.contains(k)).forEach(missing::add));
      warnings.add("입력제한 ‘" + l.name() + "’ 전체 제외: 필요한 질문 ‘"
          + missing.stream().map(k -> label(source, k)).collect(Collectors.joining(", ")) + "’ 제외");
    }
    Set<String> noticeKeys = list(retained.notices()).stream().map(InputNotice::key).collect(Collectors.toSet());
    for (InputNotice n : list(source.notices())) if (!noticeKeys.contains(n.key()))
      warnings.add("조건 안내 ‘" + n.message() + "’ 제외: 대상 ‘" + label(source, n.targetKey()) + "’ 또는 조건 질문이 제외되었습니다.");
    return new Plan(reasons.isEmpty(), List.copyOf(reasons), List.copyOf(warnings),
        retained.questions().size(), list(source.rules()).size() - list(retained.rules()).size(), cases, limits, notices);
  }

  public static Process project(Process source, Set<Long> included, boolean options, boolean branches,
      Process defaults) {
    if (!options) return defaults;
    Process retained = ProductStudioProcessSubset.retain(source, included);
    if (branches) return retained;
    // A question formerly shown/required only through a branch must be usable in the linear draft.
    List<Question> questions = retained.questions().stream().map(q -> new Question(q.key(), q.groupId(),
        q.labels(), q.control(), q.fixed(), q.fixed() ? q.visible() : true, q.required(), false,
        q.question(), q.guide(), q.choices(), q.fields(), q.assetIds(), q.numberCases(), q.preset(), q.helpDisplay())).toList();
    return new Process(retained.schemaVersion(), questions, List.of(), retained.inputLimits(), retained.notices());
  }

  /** Every direct numeric/choice/condition/correction reference, including nested price branches. */
  public static Set<String> priceReferences(Definition definition) {
    Set<String> keys = new LinkedHashSet<>();
    if (definition != null) collectTerms(definition.terms(), keys);
    return keys;
  }
  private static void collectTerms(List<Term> terms, Set<String> keys) {
    for (Term t : list(terms)) {
      add(keys, t.groupKey());
      list(t.conditions()).forEach(c -> add(keys, c.groupKey()));
      if (t.optionPricing() != null) add(keys, t.optionPricing().groupKey());
      if (t.optionTarget() != null) add(keys, t.optionTarget().groupKey());
      list(t.adjustments()).forEach(a -> add(keys, a.groupKey()));
      collectTerms(t.branches(), keys);
    }
  }

  /** Include upstream visibility, allowed values, numeric bounds and conditional input dependencies. */
  public static Set<String> priceDependencies(Process process, Definition definition) {
    Set<String> keys = new LinkedHashSet<>(priceReferences(definition));
    boolean changed;
    do {
      int count = keys.size();
      for (Rule r : list(process.rules())) if (list(r.actions()).stream().anyMatch(a -> keys.contains(a.targetKey())))
        list(r.conditions()).forEach(c -> add(keys, c.groupKey()));
      for (InputLimit l : list(process.inputLimits())) if (keys.contains(l.targetKey())) {
        add(keys, l.sourceKey());
        list(l.cases()).forEach(c -> list(c.conditions()).forEach(v -> add(keys, v.groupKey())));
      }
      for (Question q : list(process.questions())) if (keys.contains(q.key()))
        list(q.numberCases()).forEach(c -> list(c.conditions()).forEach(v -> add(keys, v.groupKey())));
      for (InputNotice n : list(process.notices())) if (keys.contains(n.targetKey()))
        list(n.conditions()).forEach(c -> add(keys, c.groupKey()));
      changed = keys.size() != count;
    } while (changed);
    return keys;
  }

  public static List<String> priceDifferences(Process source, Process target, Definition definition,
      Map<String, Answer> oldFixed, Map<String, Answer> newFixed) {
    List<String> errors = new ArrayList<>();
    Set<String> required = priceDependencies(source, definition);
    Map<String, Question> current = target.questions().stream().collect(Collectors.toMap(Question::key, q -> q));
    for (Question q : source.questions()) if (required.contains(q.key())) {
      Question next = current.get(q.key());
      if (next == null) errors.add("가격계산·조건에 필요한 질문 제외: " + label(source, q.key()));
      else {
        if (!Objects.equals(ProductPricingCompatibility.canonical(ProductStudioMedia.withoutProcessAssets(
                new Process(2, List.of(q), List.of()))),
            ProductPricingCompatibility.canonical(ProductStudioMedia.withoutProcessAssets(
                new Process(2, List.of(next), List.of())))))
          errors.add("가격계산에 필요한 옵션·입력조건 변경: " + label(source, q.key()));
        if (!Objects.equals(ProductPricingCompatibility.canonical(oldFixed.get(q.key())),
            ProductPricingCompatibility.canonical(newFixed.get(q.key()))))
          errors.add("가격계산 조건의 고정 선택값 변경: " + label(source, q.key()));
      }
    }
    return errors;
  }

  /** Remove answers for omitted questions before positional key remapping. */
  public static Definition retainTestAnswers(Definition definition, Process projected) {
    Set<String> keys = projected.questions().stream().map(Question::key).collect(Collectors.toSet());
    Map<String, Answer> answers = new LinkedHashMap<>();
    if (definition.testAnswers() != null) definition.testAnswers().forEach((key, answer) -> {
      if (keys.contains(key)) answers.put(key, answer);
    });
    return new Definition(definition.schemaVersion(), definition.schemaFingerprint(), definition.terms(), answers);
  }
  /** Order changes can keep keyed pricing only if the rest of the schema is identical. */
  public static boolean sameExceptQuestionOrder(Process before, Process after) {
    if (before == null || after == null) return false;
    java.util.function.Function<Process, Object> canonicalOrder = p -> ProductPricingCompatibility.canonical(
        ProductStudioMedia.withoutProcessAssets(new Process(p.schemaVersion(), p.questions().stream()
            .sorted(Comparator.comparing(Question::key)).toList(), p.rules(), p.inputLimits(), p.notices())));
    return Objects.equals(canonicalOrder.apply(before), canonicalOrder.apply(after));
  }

  private static String label(Process source, String key) {
    return source.questions().stream().filter(q -> q.key().equals(key))
        .map(q -> q.labels() == null ? key : q.labels().management()).findFirst().orElse(key);
  }
  private static void add(Set<String> keys, String key) { if (key != null && !key.isBlank()) keys.add(key); }
  private static <T> List<T> list(List<T> values) { return values == null ? List.of() : values; }
}
