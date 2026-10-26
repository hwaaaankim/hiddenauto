package com.dev.HiddenBATHAuto.service.productmaster;

import static com.dev.HiddenBATHAuto.dto.productmaster.ProductStudioDtos.*;
import static com.dev.HiddenBATHAuto.service.productmaster.ProductStudioEngine.*;

import com.dev.HiddenBATHAuto.dto.productmaster.ProductPricingDtos.*;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductStudioDtos.Process;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Validates price definitions against reachable process paths. Sampling partitions the process
 * domains at both process and price predicates, using the original interpreter for visibility and
 * dynamic bounds. Auxiliary predicates never change or persist the customer's process.
 */
public final class ProductPricingCoverage {
  private ProductPricingCoverage() {}

  /** Draft editing permits unconnected groups; saving/copying requires participation per numeric group. */
  public static Validation validateRegistration(Process process, List<PricingGroup> groups,
      Definition definition, Map<String, Answer> fixedAnswers, boolean draft) {
    Validation coverage = validate(process, groups, definition, fixedAnswers);
    List<Issue> issues = new ArrayList<>(coverage.issues());
    issues.addAll(ProductPricingEngine.participationIssues(groups, definition, draft));
    return new Validation(coverage.valid() && issues.stream().noneMatch(i -> "ERROR".equals(i.severity())),
        coverage.exhaustive(), coverage.scenarios(), List.copyOf(issues));
  }

  public static Validation validate(Process process, List<PricingGroup> groups,
      Definition definition, Map<String, Answer> fixedAnswers) {
    List<Issue> issues = new ArrayList<>();
    List<Issue> structure = ProductStudioEngine.structure(process);
    if (!structure.isEmpty()) return new Validation(false, false, 0, structure);
    List<String> definitionErrors = ProductPricingEngine.validate(process, groups, definition);
    if (!definitionErrors.isEmpty()) {
      for (String message : definitionErrors) add(issues, "PRICE_STRUCTURE", "가격계산식", message);
      return new Validation(false, false, 0, List.copyOf(issues));
    }
    Probe probe = new Probe(process, groups, definition, issues);
    probe.walk(0, new LinkedHashMap<>(map(fixedAnswers)));
    if (!probe.exhaustive) add(issues, "PRICE_VALIDATION_LIMIT", "가격 검증",
        "프로세스와 가격식의 조합 검증 한도 20,000개 상태를 초과했습니다. 조건·묶음·매트릭스를 나눠 주세요. 미검증 가격식은 등록할 수 없습니다.");
    if (probe.exhaustive) for (Term term : definition.terms()) {
      if (!probe.hitTerms.contains(term.key())) add(issues, "PRICE_UNREACHABLE_TERM", term.name(),
          "현재 프로세스의 어떤 허용 경로에서도 이 항이 적용되지 않습니다. 항의 입력묶음·선택 보기와 적용 조건이 같은 분기를 가리키는지 확인해 주세요.");
    }
    if (probe.exhaustive && probe.scenarios == 0) add(issues, "PRICE_EMPTY_DOMAIN", "가격 검증",
        "가격계산을 검증할 수 있는 완성된 프로세스 경로가 없습니다.");
    issues.addAll(probe.advisories.values());
    if (probe.advisories.size() >= 50) issues.add(new Issue("WARNING", "PRICE_ADVISORY_LIMIT", "확인 안내",
        "조건 누락·동시 가산 안내가 많아 대표 50개를 표시했습니다. 안내한 조건을 정리한 뒤 다시 검사하면 나머지 사례를 확인할 수 있습니다."));
    return new Validation(issues.stream().noneMatch(i -> "ERROR".equals(i.severity())) && probe.exhaustive, probe.exhaustive,
        probe.scenarios, List.copyOf(issues));
  }

  private static void add(List<Issue> issues, String code, String location, String message) {
    Issue issue = new Issue("ERROR", code, location, message);
    if (issues.size() < 100 && !issues.contains(issue)) issues.add(issue);
  }

  private static final class Probe {
    final Process process;
    final List<PricingGroup> groups;
    final Definition definition;
    final List<Issue> issues;
    final List<Condition> pricePredicates = new ArrayList<>();
    final Set<String> multipliedFields = new HashSet<>();
    final Set<String> hitTerms = new HashSet<>();
    final Map<String, Issue> advisories = new LinkedHashMap<>();
    final Set<String> rangeIssues = new HashSet<>();
    final Map<String, Scope> scopes = new LinkedHashMap<>();
    private record Scope(Question question, String key, String label, List<Term> terms) {}
    long visits, scenarios;
    boolean exhaustive = true;

    Probe(Process process, List<PricingGroup> groups, Definition definition, List<Issue> issues) {
      this.process = process;
      this.groups = groups;
      this.definition = definition;
      this.issues = issues;
      // A checkbox multiplier requires a mapping for every selected option. Predicate
      // equivalence alone can otherwise merge [mapped] with [mapped, unmapped] and miss
      // a gap when the consuming PRICE applies only to the mapped option's branch.
      Map<String, Term> byKey = definition.terms().stream()
          .collect(Collectors.toMap(Term::key, term -> term));
      Set<String> checkboxFactors = new LinkedHashSet<>();
      for (Term consumer : definition.terms()) for (MultiplierLink link : list(consumer.multipliers()))
        if (ProductPricingEngine.aggregation(link) != MultiplierAggregation.EXACT_ONE)
          for (String key : list(link.termKeys())) checkboxFactors.add(byKey.get(key).groupKey());
      for (Question question : process.questions()) if (checkboxFactors.contains(question.key()))
        for (Choice option : list(question.choices())) pricePredicates.add(new Condition(question.key(),
            null, Operator.EQ, List.of(option.key()), null, null, null, false, false));
      for (Term term : definition.terms()) {
        pricePredicates.addAll(list(term.conditions()));
        if (term.optionPricing() != null) {
          OptionRangePricing pricing = term.optionPricing();
          axis(pricing.groupKey(), pricing.axis());
          pricePredicates.add(numeric(pricing.groupKey(), pricing.axis().fieldKey(), Operator.PRESENT, null));
          pricePredicates.add(numeric(pricing.groupKey(), pricing.axis().fieldKey(), Operator.GE, BigDecimal.ZERO));
        }
        String scopeGroup = term.optionTarget() == null ? term.groupKey() : term.optionTarget().groupKey();
        Question source = process.questions().stream().filter(q -> q.key().equals(scopeGroup))
            .findFirst().orElseThrow();
        List<String> scopeKeys = term.optionTarget() != null ? List.of(term.optionTarget().choiceKey()) : choice(source.control())
            ? (list(term.choiceKeys()).isEmpty() ? source.choices().stream().map(Choice::key).toList() : term.choiceKeys())
            : list(term.fieldKeys());
        // A factor is not an additive price. Its required/mutually exclusive coverage is
        // checked at each consuming link by calculateValidated, while all factor predicates
        // below still partition the process domain and its consumed paths enter hitTerms.
        for (String scopeKey : ProductPricingEngine.role(term) == TermRole.PRICE ? scopeKeys : List.<String>of()) {
          String name = choice(source.control())
              ? source.choices().stream().filter(c -> c.key().equals(scopeKey)).map(c -> c.labels().customer()).findFirst().orElse(scopeKey)
              : source.fields().stream().filter(f -> f.key().equals(scopeKey)).map(f -> f.labels().customer()).findFirst().orElse(scopeKey);
          scopes.computeIfAbsent(source.key() + ":" + scopeKey,
              k -> new Scope(source, scopeKey, label(source) + " · " + name, new ArrayList<>())).terms().add(term);
        }
        // Optional choices still have two meaningful states: absent and selected. With an empty
        // choice filter every non-empty selection has the same price, but none is equivalent to
        // the unanswered state because a fixed choice term applies only after a selection.
        if (choice(source.control())) pricePredicates.add(new Condition(term.groupKey(), null,
            Operator.PRESENT, List.of(), null, null, null, false, false));
        if (!list(term.choiceKeys()).isEmpty()) pricePredicates.add(new Condition(term.groupKey(),
            null, Operator.EQ, term.choiceKeys(), null, null, null, false, false));
        if (term.type() != TermType.FIXED) for (String field : list(term.fieldKeys())) {
          pricePredicates.add(numeric(term.groupKey(), field, Operator.PRESENT, null));
          pricePredicates.add(numeric(term.groupKey(), field, Operator.GE, BigDecimal.ZERO));
          if (term.type() == TermType.MULTIPLY) multipliedFields.add(term.groupKey() + ":" + field);
        }
        if (term.type() == TermType.MATRIX_1D || term.type() == TermType.MATRIX_2D) {
          axis(term.groupKey(), term.xAxis());
          if (term.type() == TermType.MATRIX_2D) axis(term.groupKey(), term.yAxis());
        }
      }
    }

    private void axis(String question, Axis axis) {
      if (axis == null) return;
      for (int i = 0; i < list(axis.headers()).size(); i++) {
        Operator operator = Operator.valueOf(axis.operatorAt(i).name());
        pricePredicates.add(numeric(question, axis.fieldKey(), operator, axis.headers().get(i)));
      }
    }

    void walk(int index, Map<String, Answer> submitted) {
      if (++visits > MAX_SCENARIOS) { exhaustive = false; return; }
      if (index == process.questions().size()) { check(submitted); return; }
      Evaluation evaluation = ProductStudioEngine.evaluate(process, submitted);
      QuestionState state = evaluation.questions().get(index);
      Question q = state.question();
      if (!state.visible() || q.fixed()) {
        if (q.fixed() && !state.errors().isEmpty()) add(issues, "PRICE_FIXED_INPUT", label(q),
            "고정 사양을 평가할 수 없습니다: " + String.join(" / ", state.errors()));
        walk(index + 1, submitted);
        return;
      }
      Set<String> hidden = evaluation.questions().stream().filter(s -> !s.visible() && !s.question().fixed())
          .map(s -> s.question().key()).collect(Collectors.toSet());
      Set<String> hiddenFields = new HashSet<>();
      for (QuestionState row : evaluation.questions()) {
        Question original = process.questions().stream()
            .filter(x -> x.key().equals(row.question().key())).findFirst().orElseThrow();
        for (Field f : list(original.fields())) if (!row.allowed().contains(f.key()))
          hiddenFields.add(original.key() + ":" + f.key());
      }
      ProductStudioConstraints.Projection projection = ProductStudioConstraints.project(process, q,
          list(q.fields()), evaluation.answers(), evaluation.answers().keySet(), true, hidden, hiddenFields);
      if (!projection.errors().isEmpty()) {
        add(issues, "PRICE_PROCESS_CONFLICT", label(q),
            String.join(" / ", projection.errors()) + " 예시: " + example(submitted));
        return;
      }
      Process sampling = sampling(q);
      List<Answer> answers = projection.sums().isEmpty()
          ? ProductStudioEngine.samples(q, state.required(), state.allowed(), sampling)
          : ProductStudioConstraints.sumSamples(q, state.required(), sampling, projection.sums());
      if (answers.size() > MAX_SCENARIOS) { exhaustive = false; return; }
      boolean progressed = false;
      for (Answer answer : answers) {
        Map<String, Answer> next = new LinkedHashMap<>(submitted);
        next.put(q.key(), answer);
        QuestionState tested = ProductStudioEngine.evaluate(process, next).questions().get(index);
        if (tested.errors().isEmpty()) { progressed = true; walk(index + 1, next); }
        if (!exhaustive) return;
      }
      if (!progressed) add(issues, "PRICE_EMPTY_DOMAIN", label(q),
          "현재 경로의 입력 범위·표시묶음·합계 제한을 동시에 만족하는 값이 없습니다. 예시: " + example(submitted));
    }

    private Process sampling(Question q) {
      List<Condition> predicates = new ArrayList<>(pricePredicates);
      // A multiplied quantity's effective endpoint may be set by a preceding question. Include
      // that projected maximum, not the broader base maximum, when checking monetary overflow.
      for (Field f : list(q.fields())) if (multipliedFields.contains(q.key() + ":" + f.key())) {
        BigDecimal minimum = f.min() == null ? f.allowNegative() ? new BigDecimal("-1000000000") : BigDecimal.ZERO : f.min();
        BigDecimal maximum = f.max() == null ? new BigDecimal("1000000000") : f.max();
        predicates.add(numeric(q.key(), f.key(), Operator.EQ, minimum));
        predicates.add(numeric(q.key(), f.key(), Operator.EQ, maximum));
      }
      List<InputNotice> notices = new ArrayList<>(list(process.notices()));
      int index = 0;
      for (Condition predicate : new LinkedHashSet<>(predicates)) notices.add(new InputNotice(
          "PRICE_PROBE_" + index++, predicate.groupKey(), predicate.fieldKey(), "price validation",
          "CONDITIONAL", List.of(predicate)));
      return new Process(process.schemaVersion(), process.questions(), process.rules(),
          process.inputLimits(), notices);
    }

    private void check(Map<String, Answer> answers) {
      scenarios++;
      Calculation calculation = ProductPricingEngine.calculateValidated(process, definition, answers);
      calculation.terms().stream().filter(TermResult::applied).map(TermResult::key).forEach(hitTerms::add);
      Set<String> explained = new HashSet<>();
      for (TermResult result : calculation.terms()) {
        if (!result.applied() || result.amount() != null || !result.explanation().startsWith("가격표 범위 밖:")) continue;
        Term term = definition.terms().stream().filter(t -> t.key().equals(result.key())).findFirst().orElseThrow();
        explained.add(term.name() + ": " + result.explanation());
        rangeIssue(term, term.xAxis(), "x", calculation.evaluation());
        if (term.yAxis() != null) rangeIssue(term, term.yAxis(), "y", calculation.evaluation());
      }
      if (!calculation.complete()) { for (String error : calculation.errors()) if (!explained.contains(error)) add(issues,
          error.startsWith("가격표 중복:") ? "PRICE_TABLE_OVERLAP" : error.startsWith("가격표 범위 누락:") ? "PRICE_TABLE_GAP" : "PRICE_PATH",
          "가격계산", error + " 예시: " + example(answers));
      } else checkScopes(calculation);
    }

    private void rangeIssue(Term term, Axis axis, String key, Evaluation evaluation) {
      if (axis == null || axis.usesEdgeOutside()) return;
      Answer answer = evaluation.answers().get(term.groupKey());
      Object raw = answer == null ? null : map(answer.fields()).get(axis.fieldKey());
      BigDecimal value = raw == null ? null : ProductStudioEngine.number(raw);
      if (value == null) return;
      try { ProductPricingEngine.index(axis, value); return; } catch (IllegalArgumentException outside) { /* Explain domain mismatch once per axis. */ }
      String signature = term.key() + ":" + key;
      if (!rangeIssues.add(signature)) return;
      QuestionState state = evaluation.questions().stream().filter(s -> s.question().key().equals(term.groupKey())).findFirst().orElseThrow();
      Field field = state.question().fields().stream().filter(f -> f.key().equals(axis.fieldKey())).findFirst().orElseThrow();
      boolean lower = ProductPricingEngine.lowerAxis(axis);
      int at = lower ? 0 : axis.headers().size() - 1;
      String boundary = axis.headers().get(at).toPlainString();
      String comparison = switch (axis.operatorAt(at)) { case LE -> "이하"; case LT -> "미만"; case GE -> "이상"; case GT -> "초과"; };
      BigDecimal limit = lower ? field.min() : field.max();
      String limitName = lower ? "최소" : "최대";
      String message = "현재 가격표는 " + boundary + " " + comparison + "까지 계산하지만, 프로세스에서 표 밖 입력을 허용하는 경로가 있습니다. "
          + "해당 경로의 " + limitName + " 입력값: " + (limit == null ? "미설정" : limit.toPlainString())
          + ". 엑셀에 큰 값이 추가된 것이 아닙니다. ‘표 밖 입력 처리’에서 끝 기준 가격을 적용할지 선택하거나, 가격표 기준을 추가하세요. "
          + "실제 주문 크기를 제한하려면 프로세스의 해당 숫자 입력 범위를 수정하세요. 조건별 제한도 최종 검사에 반영됩니다.";
      issues.add(new Issue("ERROR", "PRICE_AXIS_RANGE", term.name() + " · " + key.toUpperCase(Locale.ROOT) + "축 · " + field.labels().customer(),
          message, "terms." + term.key() + "." + key + "Axis.outsidePolicy"));
    }

    // Independent PRICE terms are additive by design. FACTOR terms are excluded here;
    // a missing or ambiguous required factor is already a hard error from calculateValidated.
    // Preserve advisory-only behavior for intentional price surcharges and free branches.
    private void checkScopes(Calculation calculation) {
      Evaluation evaluation = calculation.evaluation();
      Map<String, QuestionState> states = evaluation.questions().stream().collect(Collectors.toMap(s -> s.question().key(), s -> s));
      Set<String> applied = calculation.terms().stream().filter(TermResult::applied).map(TermResult::key).collect(Collectors.toSet());
      for (Map.Entry<String, Scope> entry : scopes.entrySet()) {
        Scope scope = entry.getValue(); Question q = scope.question();
        QuestionState state = states.get(q.key()); Answer answer = evaluation.answers().get(q.key());
        if (state == null || !state.visible() && !q.fixed() || answer == null || !state.allowed().contains(scope.key())) continue;
        if (choice(q.control()) ? !list(answer.choices()).contains(scope.key()) : !map(answer.fields()).containsKey(scope.key())) continue;
        List<Term> active = scope.terms().stream().filter(t -> applied.contains(t.key())).toList();
        String code = active.isEmpty() ? "PRICE_CONDITION_GAP" : active.size() > 1 ? "PRICE_ADDITIVE_OVERLAP" : null;
        if (code == null) continue;
        String signature = code + ":" + entry.getKey() + ":" + active.stream().map(Term::key).collect(Collectors.joining(","));
        if (advisories.containsKey(signature) || advisories.size() >= 50) continue;
        String names = (active.isEmpty() ? scope.terms() : active).stream().map(Term::name).collect(Collectors.joining(" / "));
        String message = active.isEmpty()
            ? "이 입력에서는 연결한 가격 항이 모두 제외됩니다: " + names + ". 빠진 조건·구간이 아닌지 확인하세요. 예: 600 이하 다음을 602 이상으로 만들면 601이 빠집니다. 의도한 무료 분기는 0원 항으로 명시할 수 있습니다."
            : "같은 입력에 " + active.size() + "개 항이 동시에 적용되어 모두 더해집니다: " + names
                + ". 구간별 대체 가격이면 경계·선택 조건을 겹치지 않게 수정하세요. 예: 600 이하 / 600 이상은 600에서 중복됩니다. 기본금+추가금이면 정상입니다.";
        advisories.put(signature, new Issue("WARNING", code, scope.label(), message + " 실제 재현 입력: " + example(evaluation.answers())));
      }
    }

    private String example(Map<String, Answer> answers) {
      List<String> pieces = new ArrayList<>();
      for (Question q : process.questions()) {
        Answer answer = answers.get(q.key());
        if (answer == null || blank(answer)) continue;
        String value = choice(q.control()) ? list(answer.choices()).stream().map(key ->
            list(q.choices()).stream().filter(c -> c.key().equals(key))
                .map(c -> c.labels().customer()).findFirst().orElse(key)).collect(Collectors.joining(" + "))
            : list(q.fields()).stream().filter(f -> map(answer.fields()).containsKey(f.key()))
                .map(f -> f.labels().customer() + "=" + map(answer.fields()).get(f.key()))
                .collect(Collectors.joining(" / "));
        pieces.add(label(q) + ": " + value);
      }
      String example = String.join("; ", pieces);
      return example.length() > 800 ? example.substring(0, 800) + "…" : example;
    }
  }

  private static Condition numeric(String question, String field, Operator operator,
      BigDecimal boundary) {
    return new Condition(question, field, operator, List.of(), null, boundary, null, false, false);
  }
}
