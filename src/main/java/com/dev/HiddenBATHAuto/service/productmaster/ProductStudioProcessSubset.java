package com.dev.HiddenBATHAuto.service.productmaster;

import com.dev.HiddenBATHAuto.dto.productmaster.ProductStudioDtos.*;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductStudioDtos.Process;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Retain selected questions without broadening a rule by deleting only part of its condition. */
public final class ProductStudioProcessSubset {
  private ProductStudioProcessSubset() {}

  public static Process retain(Process source, Set<Long> groupIds) {
    Set<String> keys = list(source.questions()).stream()
        .filter(q -> groupIds.contains(q.groupId())).map(Question::key).collect(Collectors.toSet());
    List<Question> questions = list(source.questions()).stream()
        .filter(q -> groupIds.contains(q.groupId()))
        .map(q -> new Question(q.key(), q.groupId(), q.labels(), q.control(), q.fixed(), q.visible(),
            q.required(), q.requireRule(), q.question(), q.guide(), q.choices(), q.fields(), q.assetIds(),
            list(q.numberCases()).stream().filter(c -> conditionsPresent(c.conditions(), keys)).toList(), q.preset()))
        .toList();
    // A mixed rule is omitted in full if any condition OR action refers to a removed question.
    List<Rule> rules = list(source.rules()).stream()
        .filter(r -> conditionsPresent(r.conditions(), keys)
            && list(r.actions()).stream().allMatch(a -> a != null && keys.contains(a.targetKey())))
        .toList();
    List<InputLimit> limits = list(source.inputLimits()).stream()
        .filter(l -> keys.contains(l.targetKey())
            && (l.sourceKey() == null || l.sourceKey().isBlank() || keys.contains(l.sourceKey()))
            && list(l.cases()).stream().allMatch(c -> conditionsPresent(c.conditions(), keys)))
        .toList();
    List<InputNotice> notices = list(source.notices()).stream()
        .filter(n -> keys.contains(n.targetKey()) && conditionsPresent(n.conditions(), keys)).toList();
    return new Process(source.schemaVersion(), questions, rules, limits, notices);
  }

  public static boolean hasRemovedElements(Process source, Process retained) {
    return list(source.questions()).size() != list(retained.questions()).size()
        || list(source.rules()).size() != list(retained.rules()).size()
        || list(source.inputLimits()).size() != list(retained.inputLimits()).size()
        || list(source.notices()).size() != list(retained.notices()).size()
        || list(source.questions()).stream().mapToInt(q -> list(q.numberCases()).size()).sum()
            != list(retained.questions()).stream().mapToInt(q -> list(q.numberCases()).size()).sum();
  }

  /** Metadata saves preserve the independently edited process order; an explicit
   * configuration reorder applies the selected order. New questions append otherwise. */
  public static List<Question> mergeQuestions(List<Question> previous, List<Question> defaults,
      boolean compositionReordered) {
    Map<Long, Question> previousByGroup = new LinkedHashMap<>();
    list(previous).forEach(q -> previousByGroup.put(q.groupId(), q));
    Map<Long, Question> expected = new LinkedHashMap<>();
    list(defaults).forEach(q -> expected.put(q.groupId(), q));
    List<Question> order = new ArrayList<>(compositionReordered ? list(defaults) : list(previous));
    Set<Long> ordered = order.stream().map(Question::groupId).collect(Collectors.toSet());
    list(defaults).stream().filter(q -> !ordered.contains(q.groupId())).forEach(order::add);
    return order.stream().filter(q -> expected.containsKey(q.groupId())).map(q -> {
      Question original = expected.get(q.groupId());
      return original.fixed() ? original : previousByGroup.getOrDefault(q.groupId(), original);
    }).toList();
  }

  private static boolean conditionsPresent(List<Condition> conditions, Set<String> keys) {
    return list(conditions).stream().allMatch(c -> c != null && keys.contains(c.groupKey()));
  }

  private static <T> List<T> list(List<T> values) { return values == null ? List.of() : values; }
}
