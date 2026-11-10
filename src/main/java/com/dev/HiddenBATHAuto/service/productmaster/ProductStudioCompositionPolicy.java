package com.dev.HiddenBATHAuto.service.productmaster;

import static com.dev.HiddenBATHAuto.dto.productmaster.ProductStudioDtos.*;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductStudioDtos.Process;
import java.util.*;
import java.util.stream.Collectors;

/** Reviews a composition without mutating a product or inventing answers for new questions. */
public final class ProductStudioCompositionPolicy {
  private ProductStudioCompositionPolicy() {}

  public record Decision(Process process, CompositionReview review) {
    public boolean blocked() { return "BLOCKED".equals(review.outcome()); }
  }
  private record Assessment(boolean safe, Validation validation, List<Issue> issues) {}

  public static Decision review(Process source, Process defaults, boolean reorder,
      Map<String, Answer> beforeFixed, Map<String, Answer> afterFixed) {
    if (defaults.schemaVersion() != 2 || defaults.questions().isEmpty()
        || defaults.questions().size() > ProductStudioEngine.MAX_QUESTIONS)
      return decision(source, "BLOCKED", true, false, 0, List.of(), List.of(new Issue("ERROR", "SCHEMA", "제품 구성",
          "질문 그룹은 1~100개인 버전 2 프로세스로 구성해야 합니다.")));
    Set<Long> selected = defaults.questions().stream().map(Question::groupId).collect(Collectors.toSet());
    Set<Long> existing = source.questions().stream().map(Question::groupId).collect(Collectors.toSet());
    List<Long> pending = defaults.questions().stream().map(Question::groupId)
        .filter(id -> !existing.contains(id)).toList();
    List<Issue> removal = removalIssues(source, selected);
    if (removal.stream().anyMatch(i -> "ERROR".equals(i.severity())))
      return decision(source, "BLOCKED", true, false, 0, pending, removal);

    Process retained = ProductStudioProcessSubset.retain(source, selected);
    Process candidate = merge(retained, defaults, reorder);
    Assessment check = assess(candidate, retained, existing, beforeFixed, afterFixed);
    if (check.safe()) {
      List<Issue> issues = new ArrayList<>(removal);
      issues.addAll(check.issues());
      addPending(candidate, pending, issues);
      boolean draft = !pending.isEmpty() || !check.validation().valid() || !check.validation().exhaustive();
      return decision(candidate, draft ? "APPLIED_DRAFT" : "APPLIED", draft,
          check.validation().valid() && check.validation().exhaustive(), check.validation().scenarios(), pending, issues);
    }

    List<Issue> rejected = new ArrayList<>(removal);
    rejected.addAll(check.issues());
    if (reorder) {
      // A fallback must pass its own review. It is never presumed safe merely because it appends.
      Process fallback = merge(retained, defaults, false);
      Assessment fallbackCheck = assess(fallback, retained, existing, beforeFixed, afterFixed);
      if (fallbackCheck.safe()) {
        rejected.addAll(fallbackCheck.issues());
        addPending(fallback, pending, rejected);
        return decision(fallback, "KEPT_ORDER", true,
            fallbackCheck.validation().valid() && fallbackCheck.validation().exhaustive(),
            fallbackCheck.validation().scenarios(), pending, rejected);
      }
      rejected.addAll(fallbackCheck.issues());
    }
    return decision(source, "BLOCKED", true, false, check.validation().scenarios(), pending, rejected);
  }

  private static Process merge(Process retained, Process defaults, boolean reorder) {
    return new Process(2, ProductStudioProcessSubset.mergeQuestions(retained.questions(), defaults.questions(), reorder),
        retained.rules(), retained.inputLimits(), retained.notices(), retained.confirmationMessage());
  }

  private static Assessment assess(Process candidate, Process retained, Set<Long> existing,
      Map<String, Answer> beforeFixed, Map<String, Answer> afterFixed) {
    List<Issue> ordering = ProductStudioProcessSubset.orderIssues(candidate);
    if (!ordering.isEmpty()) return new Assessment(false, new Validation(false, false, 0, ordering), ordering);
    // Only newly added, independent questions are pending. Existing unfinished questions are not
    // silently replaced by dummy choices/fields to make validation pass.
    Process projection = new Process(candidate.schemaVersion(), candidate.questions().stream()
        .filter(q -> existing.contains(q.groupId())).toList(), candidate.rules(), candidate.inputLimits(), candidate.notices(), candidate.confirmationMessage());
    Map<String, Answer> oldFixed = fixedFor(retained, beforeFixed), newFixed = fixedFor(projection, afterFixed);
    Validation validation = validate(projection, newFixed);
    boolean unchanged = projection.equals(retained) && oldFixed.equals(newFixed);
    List<Issue> issues = new ArrayList<>();
    if (!validation.valid() || !validation.exhaustive()) {
      issues.addAll(validation.issues());
      Validation draftCheck = incompleteDraftCheck(projection, newFixed);
      if (draftCheck != null) {
        if (draftCheck.valid() && draftCheck.exhaustive()) {
          // Compare visibility even in unfinished drafts; a new unreachable existing question
          // must not be silently accepted as an ordinary missing-answer warning.
          List<Issue> hidden = draftCheck.issues().stream().filter(i -> "UNREACHABLE_QUESTION".equals(i.code())).toList();
          if (!hidden.isEmpty() && !unchanged) {
            Validation oldDraft = incompleteDraftCheck(retained, oldFixed);
            if (oldDraft == null) oldDraft = validate(retained, oldFixed);
            Set<String> oldHidden = oldDraft.issues().stream().filter(i -> "UNREACHABLE_QUESTION".equals(i.code()))
                .map(ProductStudioCompositionPolicy::questionIdentity).collect(Collectors.toSet());
            if (!oldDraft.valid() || !oldDraft.exhaustive() || hidden.stream().anyMatch(i -> !oldHidden.contains(questionIdentity(i))))
              return new Assessment(false, validation, List.of(new Issue("ERROR", "COMPOSITION_UNREACHABLE", "질문 표시 경로",
                  "기존 질문이 변경 후 표시되지 않거나 표시 경로의 안전성을 확인할 수 없습니다. 비규격 프로세스에서 연결을 먼저 확인해 주세요.")));
          }
          issues.clear();
          for (Issue issue : validation.issues()) {
            String target = projection.questions().stream().filter(q -> ProductStudioEngine.label(q).equals(issue.location()))
                .map(Question::key).findFirst().orElse(null);
            issues.add("ERROR".equals(issue.severity())
                ? new Issue("INFO", "COMPOSITION_PENDING_CONFIGURATION", issue.location(),
                    "미설정 질문 [" + issue.location() + "]: " + issue.message(), target)
                : issue);
          }
          issues.addAll(draftCheck.issues());
          issues.add(new Issue("WARNING", "COMPOSITION_EXISTING_DRAFT", "기존 프로세스",
              "기존 답변·분기·입력조건은 보존하고 설정된 질문들의 경로와 전체 선후 관계를 검사했습니다. 미설정 질문의 답변·입력필드는 비규격 프로세스에서 이어서 설정해 주세요. 최종 전체 검증 전까지 등록중으로 저장합니다."));
          return new Assessment(true, new Validation(false, false, draftCheck.scenarios(), validation.issues()), issues);
        }
        issues.addAll(draftCheck.issues());
      }
      if (issues.isEmpty()) issues.add(new Issue("ERROR", "COMPOSITION_UNVERIFIED", "프로세스",
          "변경한 기존 질문 흐름의 전체 검증을 완료할 수 없습니다. 비규격 프로세스에서 먼저 수정·검증해 주세요."));
      return new Assessment(false, validation, issues);
    }
    List<Issue> unreachable = validation.issues().stream()
        .filter(i -> "UNREACHABLE_QUESTION".equals(i.code())).toList();
    if (!unreachable.isEmpty() && !unchanged) {
      Validation baseline = validate(retained, oldFixed);
      if (!baseline.valid() || !baseline.exhaustive()) {
        issues.add(new Issue("ERROR", "COMPOSITION_UNVERIFIED", "질문 표시 경로",
            "기존 흐름의 검증이 완료되지 않아 질문 표시 경로가 안전하게 유지되는지 비교할 수 없습니다. 비규격 프로세스에서 먼저 검증해 주세요."));
        return new Assessment(false, validation, issues);
      }
      Set<String> previouslyUnreachable = baseline.issues().stream()
          .filter(i -> "UNREACHABLE_QUESTION".equals(i.code())).map(ProductStudioCompositionPolicy::questionIdentity).collect(Collectors.toSet());
      for (Issue issue : unreachable) if (!previouslyUnreachable.contains(questionIdentity(issue))) {
        String key = issue.target();
        issues.add(new Issue("ERROR", "COMPOSITION_UNREACHABLE", issue.location(),
            "변경 후 [" + issue.location() + "] 질문이 표시되는 경로를 찾지 못했습니다. 의도한 생략인지 비규격 프로세스에서 확인해 주세요.", key));
      }
      if (!issues.isEmpty()) return new Assessment(false, validation, issues);
    }
    issues.addAll(validation.issues());
    return new Assessment(true, validation, issues);
  }

  /** Recognize missing configuration only; malformed fields, rules and numeric bounds
   * are not reclassified as harmless unfinished questions. */
  private static Validation incompleteDraftCheck(Process process, Map<String, Answer> fixed) {
    Set<String> incomplete = process.questions().stream().filter(q -> !q.fixed()
        && (ProductStudioEngine.choice(q.control()) ? ProductStudioEngine.list(q.choices()).isEmpty()
            : ProductStudioEngine.list(q.fields()).isEmpty())).map(Question::key).collect(Collectors.toSet());
    if (incomplete.isEmpty()) return null;
    // The draft validator independently checks every property. It merely allows empty answer
    // definitions on these exact questions; no errors from the ordinary validator are hidden.
    try { return ProductStudioEngine.validateCompositionDraft(process, fixed, incomplete); }
    catch (RuntimeException ex) { return null; }
  }

  private static Validation validate(Process process, Map<String, Answer> fixed) {
    try { return ProductStudioEngine.validate(process, fixed); }
    catch (RuntimeException ex) {
      return new Validation(false, false, 0, List.of(new Issue("ERROR", "COMPOSITION_UNVERIFIED", "프로세스",
          "저장된 질문·조건을 검증할 수 없습니다. 비규격 프로세스에서 설정을 확인해 주세요.")));
    }
  }

  private static String questionIdentity(Issue issue) {
    return issue.target() == null ? issue.location() : issue.target();
  }

  private static Map<String, Answer> fixedFor(Process process, Map<String, Answer> fixed) {
    Map<String, Answer> result = new LinkedHashMap<>();
    for (Question q : process.questions()) if (fixed != null && fixed.containsKey(q.key())) result.put(q.key(), fixed.get(q.key()));
    return result;
  }

  private static void addPending(Process process, List<Long> pending, List<Issue> issues) {
    for (Question q : process.questions()) if (pending.contains(q.groupId()))
      issues.add(new Issue("INFO", "COMPOSITION_PENDING_QUESTION", ProductStudioEngine.label(q),
          "새 질문 [" + ProductStudioEngine.label(q) + "]의 답변·입력필드·필요한 분기는 비규격 프로세스에서 설정해 주세요. 아직 최종 등록된 질문이 아닙니다.", q.key()));
  }

  private static Decision decision(Process process, String outcome, boolean required, boolean validated,
      long scenarios, List<Long> pending, List<Issue> issues) {
    return new Decision(process, new CompositionReview(outcome, required, validated, scenarios,
        List.copyOf(pending), issues.stream().distinct().toList()));
  }

  /** Cross-boundary dependencies cannot be silently deleted by a composition edit. */
  private static List<Issue> removalIssues(Process source, Set<Long> selected) {
    Map<String, Question> questions = source.questions().stream().collect(Collectors.toMap(Question::key, q -> q));
    Set<String> retained = source.questions().stream().filter(q -> selected.contains(q.groupId()))
        .map(Question::key).collect(Collectors.toSet());
    Set<String> removed = new HashSet<>(questions.keySet()); removed.removeAll(retained);
    List<Issue> issues = referenceIssues(source, questions);
    for (Rule rule : ProductStudioEngine.list(source.rules())) {
      Set<String> refs = new LinkedHashSet<>();
      ProductStudioEngine.list(rule.conditions()).forEach(c -> refs.add(c.groupKey()));
      ProductStudioEngine.list(rule.actions()).forEach(a -> refs.add(a.targetKey()));
      Set<String> missing = refs.stream().filter(removed::contains).collect(Collectors.toSet());
      if (missing.isEmpty()) continue;
      if (refs.stream().anyMatch(retained::contains))
        removalIssue(issues, questions, "분기 [" + rule.name() + "]", missing, refs.stream().filter(retained::contains).findFirst().orElse(null));
      else issues.add(new Issue("INFO", "COMPOSITION_REMOVED_OWNED_DATA", rule.name(),
          "제거할 질문들 사이에서만 사용하는 분기 [" + rule.name() + "]도 함께 제외합니다."));
    }
    for (Question q : source.questions()) if (retained.contains(q.key()))
      for (NumberCase c : ProductStudioEngine.list(q.numberCases())) {
        Set<String> missing = ProductStudioEngine.list(c.conditions()).stream().map(Condition::groupKey)
            .filter(removed::contains).collect(Collectors.toSet());
        if (!missing.isEmpty()) removalIssue(issues, questions, "숫자조건 [" + c.name() + "]", missing, q.key());
      }
    for (InputLimit limit : ProductStudioEngine.list(source.inputLimits())) if (retained.contains(limit.targetKey())) {
      Set<String> refs = new LinkedHashSet<>(); refs.add(limit.sourceKey());
      for (LimitCase c : ProductStudioEngine.list(limit.cases())) ProductStudioEngine.list(c.conditions()).forEach(condition -> refs.add(condition.groupKey()));
      Set<String> missing = refs.stream().filter(removed::contains).collect(Collectors.toSet());
      if (!missing.isEmpty()) removalIssue(issues, questions, "입력제한 [" + limit.name() + "]", missing, limit.targetKey());
    }
    for (InputNotice notice : ProductStudioEngine.list(source.notices())) if (retained.contains(notice.targetKey())) {
      Set<String> missing = ProductStudioEngine.list(notice.conditions()).stream().map(Condition::groupKey)
          .filter(removed::contains).collect(Collectors.toSet());
      if (!missing.isEmpty()) removalIssue(issues, questions, "조건별 안내", missing, notice.targetKey());
    }
    return issues;
  }

  /** Never let retain() silently discard a pre-existing dangling link while planning an edit. */
  private static List<Issue> referenceIssues(Process source, Map<String, Question> questions) {
    List<Issue> issues = new ArrayList<>();
    for (Rule r : ProductStudioEngine.list(source.rules())) {
      Set<String> refs = new LinkedHashSet<>();
      ProductStudioEngine.list(r.conditions()).forEach(c -> refs.add(c.groupKey()));
      ProductStudioEngine.list(r.actions()).forEach(a -> refs.add(a.targetKey()));
      knownReferences(issues, questions, "분기 [" + r.name() + "]", refs);
    }
    for (Question q : source.questions()) for (NumberCase c : ProductStudioEngine.list(q.numberCases()))
      knownReferences(issues, questions, "숫자조건 [" + c.name() + "]", ProductStudioEngine.list(c.conditions()).stream()
          .map(Condition::groupKey).collect(Collectors.toCollection(LinkedHashSet::new)));
    for (InputLimit l : ProductStudioEngine.list(source.inputLimits())) {
      Set<String> refs = new LinkedHashSet<>(); refs.add(l.targetKey());
      if (l.sourceKey() != null && !l.sourceKey().isBlank()) refs.add(l.sourceKey());
      for (LimitCase c : ProductStudioEngine.list(l.cases())) ProductStudioEngine.list(c.conditions()).forEach(v -> refs.add(v.groupKey()));
      knownReferences(issues, questions, "입력제한 [" + l.name() + "]", refs);
    }
    for (InputNotice n : ProductStudioEngine.list(source.notices())) {
      Set<String> refs = new LinkedHashSet<>(); refs.add(n.targetKey());
      ProductStudioEngine.list(n.conditions()).forEach(c -> refs.add(c.groupKey()));
      knownReferences(issues, questions, "조건별 안내", refs);
    }
    return issues;
  }

  private static void knownReferences(List<Issue> issues, Map<String, Question> questions, String context, Set<String> refs) {
    List<String> missing = refs.stream().filter(key -> !questions.containsKey(key)).map(String::valueOf).sorted().toList();
    if (!missing.isEmpty()) issues.add(new Issue("ERROR", "COMPOSITION_INVALID_REFERENCE", context,
        context + "에 현재 제품에 없는 질문 연결 [" + String.join(", ", missing)
            + "]이 남아 있습니다. 연결을 임의로 삭제하지 않고 구성 변경을 차단합니다. 비규격 프로세스에서 먼저 확인해 주세요.",
        refs.stream().filter(questions::containsKey).findFirst().orElse(null)));
  }

  private static void removalIssue(List<Issue> issues, Map<String, Question> questions,
      String context, Set<String> missing, String target) {
    String names = missing.stream().sorted().map(key -> ProductStudioEngine.label(questions.get(key))).collect(Collectors.joining(", "));
    issues.add(new Issue("ERROR", "COMPOSITION_DEPENDENCY_REMOVAL", context,
        context + "에서 사용하는 질문 [" + names + "]을 제거하면 남은 질문의 연결이 달라집니다. 구성 변경은 저장하지 않습니다. 비규격 프로세스에서 관련 연결을 먼저 수정해 주세요.", target));
  }
}
