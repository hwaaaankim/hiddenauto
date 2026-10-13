package com.dev.HiddenBATHAuto.service.productmaster;

import static com.dev.HiddenBATHAuto.dto.productmaster.ProductStudioDtos.*;
import static com.dev.HiddenBATHAuto.service.productmaster.ProductStudioEngine.*;

import com.dev.HiddenBATHAuto.dto.productmaster.ProductStudioDtos.Process;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/** Independent numeric bounds and conditional help. Never changes question visibility. */
final class ProductStudioConstraints {
  private ProductStudioConstraints() {}
  record Bound(InputLimit limit, BigDecimal min, BigDecimal max) {}
  record Projection(List<Field> fields, List<Bound> sums, List<String> errors) {}

  private static void check(boolean ok, String message) {
    if (!ok) throw new IllegalArgumentException(message);
  }
  private static void error(List<Issue> issues, String code, String at, String message) {
    Issue value = new Issue("ERROR", code, at, message);
    if (issues.size() < 200 && !issues.contains(value)) issues.add(value);
  }
  private static boolean key(String value) {
    return value != null && value.matches("[A-Za-z0-9_-]{1,80}");
  }
  private static void bounds(BigDecimal min, BigDecimal max) {
    check(min != null || max != null, "최소값 또는 최대값을 한 개 이상 입력해 주세요.");
    for (BigDecimal n : Arrays.asList(min, max))
      check(n == null || n.abs().compareTo(new BigDecimal("1000000000")) <= 0 && n.scale() <= 3,
          "입력제한은 ±10억 이하, 소수 셋째 자리까지 설정할 수 있습니다.");
    check(min == null || max == null || min.compareTo(max) <= 0, "최소값이 최대값보다 큽니다.");
  }
  private static Map<String, Field> fieldsByKey(Question q) {
    return list(q.fields()).stream().collect(Collectors.toMap(Field::key, f -> f));
  }
  static void structure(Process p, List<Issue> issues) {
    Map<String, Question> questions = new LinkedHashMap<>();
    Map<String, Integer> order = new HashMap<>();
    for (Question q : list(p.questions())) if (q != null) {
      questions.put(q.key(), q); order.put(q.key(), order.size());
    }
    checkCount(p.inputLimits(), 200, "입력제한", issues);
    checkCount(p.notices(), 200, "조건별 안내메시지", issues);
    Set<String> keys = new HashSet<>();
    for (InputLimit l : list(p.inputLimits())) {
      String at = l == null ? "입력제한" : text(l.name());
      try {
        check(l != null && key(l.key()) && keys.add(l.key()), "입력제한 value가 없거나 중복됩니다.");
        check(!at.isBlank() && at.length() <= 120, "입력제한 이름을 1~120자로 입력해 주세요.");
        Question target = questions.get(l.targetKey());
        check(target != null && target.control() == Control.NUMBER && !target.fixed(),
            "입력제한 대상은 이 제품의 수정 가능한 숫자 질문이어야 합니다.");
        Set<String> wanted = new LinkedHashSet<>(list(l.fieldKeys()));
        check(!wanted.isEmpty() && wanted.size() == list(l.fieldKeys()).size()
                && fieldsByKey(target).keySet().containsAll(wanted),
            "입력제한을 적용할 숫자 필드를 중복 없이 선택해 주세요.");
        check("EACH".equals(l.aggregation()) || "SUM".equals(l.aggregation()),
            "입력제한은 개별값(EACH) 또는 합계(SUM) 방식입니다.");
        if ("SUM".equals(l.aggregation())) {
          check(wanted.size() >= 2, "합계 제한은 입력 필드 두 개 이상이 필요합니다.");
          Set<String> bundles = wanted.stream().map(k -> text(fieldsByKey(target).get(k).bundleKey()))
              .collect(Collectors.toSet());
          check(bundles.size() == 1 && !bundles.contains(""), "합계 대상 필드는 하나의 묶음으로 그룹화해 주세요.");
          String bundle = bundles.iterator().next();
          check(target.fields().stream().filter(f -> bundle.equals(f.bundleKey())).allMatch(f -> wanted.contains(f.key())),
              "합계 제한에는 해당 묶음의 모든 필드를 포함해 주세요.");
        }
        if (text(l.sourceKey()).isBlank()) {
          bounds(l.min(), l.max());
          check(list(l.cases()).isEmpty(), "직접 입력 제한에는 앞 질문의 조건을 넣을 수 없습니다.");
        } else {
          Question source = questions.get(l.sourceKey());
          check(source != null && order.get(l.sourceKey()) < order.get(l.targetKey()),
              "입력제한의 영향을 주는 질문은 대상 질문보다 앞에 있어야 합니다. 질문 순서를 확인해 주세요.");
          check(source.control() == Control.NUMBER || choice(source.control()),
              "앞 질문 입력제한은 숫자입력형 또는 선택형만 사용할 수 있습니다.");
          check(!list(l.cases()).isEmpty() && l.cases().size() <= 100,
              "앞 질문의 모든 구간/선택지를 포함하는 제한 조건을 1~100개 등록해 주세요.");
          Set<String> cases = new HashSet<>(), sourceFields = new HashSet<>();
          for (LimitCase c : l.cases()) {
            check(c != null && key(c.key()) && cases.add(c.key()) && !text(c.name()).isBlank()
                    && c.name().length() <= 120, "제한 조건의 이름/value가 없거나 중복됩니다.");
            bounds(c.min(), c.max());
            check(!list(c.conditions()).isEmpty() && c.conditions().size() <= 20,
                "제한 조건마다 앞 질문의 조건식을 1~20개 입력해 주세요.");
            for (Condition condition : c.conditions()) {
              check(condition != null && source.key().equals(condition.groupKey()),
                  "한 입력제한의 조건들은 선택한 앞 질문만 참조해야 합니다.");
              condition(source, condition);
              if (source.control() == Control.NUMBER) sourceFields.add(condition.fieldKey());
            }
          }
          check(sourceFields.size() <= 1, "한 입력제한은 앞 숫자 질문의 한 필드를 기준으로 구간을 나눠 주세요.");
        }
      } catch (IllegalArgumentException ex) { error(issues, "INPUT_LIMIT", at, ex.getMessage()); }
    }
    keys.clear();
    for (InputNotice n : list(p.notices())) {
      try {
        check(n != null && key(n.key()) && keys.add(n.key()), "안내메시지 value가 없거나 중복됩니다.");
        Question target = questions.get(n.targetKey());
        check(target != null, "안내메시지가 표시될 질문을 선택해 주세요.");
        check(!text(n.message()).isBlank() && n.message().length() <= 2000,
            "안내메시지를 1~2,000자로 입력해 주세요.");
        check(Set.of("ALWAYS", "ABSENT", "CONDITIONAL").contains(text(n.mode())),
            "안내메시지는 항상 출력/입력 없음/조건 충족 중 하나를 선택해 주세요.");
        check(text(n.fieldKey()).isBlank() || fieldsByKey(target).containsKey(n.fieldKey()),
            "안내메시지의 입력 필드가 이 질문에 없습니다.");
        if ("CONDITIONAL".equals(n.mode())) {
          check(!list(n.conditions()).isEmpty() && n.conditions().size() <= 20,
              "조건별 안내메시지는 조건식을 1~20개 등록해 주세요.");
          for (Condition c : n.conditions()) {
            check(c != null && target.key().equals(c.groupKey()), "안내메시지는 해당 질문의 입력/선택 조건을 사용해 주세요.");
            condition(target, c);
          }
        } else check(list(n.conditions()).isEmpty(), "항상 출력/입력 없음 안내에는 별도 조건식을 넣을 수 없습니다.");
      } catch (IllegalArgumentException ex) {
        error(issues, "INPUT_NOTICE", n == null ? "안내메시지" : text(n.targetKey()), ex.getMessage());
      }
    }
  }
  private static void checkCount(List<?> values, int max, String label, List<Issue> issues) {
    if (list(values).size() > max) error(issues, "LIMIT_COUNT", label, label + "는 최대 " + max + "개입니다.");
  }
  private static void condition(Question source, Condition c) {
    check(c.operator() != null, "조건 연산자를 선택해 주세요.");
    if (choice(source.control())) {
      check(text(c.fieldKey()).isBlank(), "선택형 조건에는 숫자 필드를 지정할 수 없습니다.");
      check(Set.of(Operator.EQ, Operator.NE, Operator.PRESENT, Operator.ABSENT).contains(c.operator()),
          "선택형 조건은 선택값 일치/불일치/있음/없음만 가능합니다.");
      if (c.operator() == Operator.EQ || c.operator() == Operator.NE)
        check(!list(c.choiceKeys()).isEmpty() && source.choices().stream().map(Choice::key)
            .collect(Collectors.toSet()).containsAll(c.choiceKeys()), "조건에 존재하지 않는 선택지가 있습니다.");
      return;
    }
    check(fieldsByKey(source).containsKey(c.fieldKey()), "조건의 입력 필드를 선택해 주세요.");
    if (c.operator() == Operator.PRESENT || c.operator() == Operator.ABSENT) return;
    if (source.control() == Control.NUMBER) {
      check(Set.of(Operator.EQ, Operator.NE, Operator.GT, Operator.GE, Operator.LT, Operator.LE, Operator.RANGE)
              .contains(c.operator()) && c.lower() != null, "숫자 조건의 연산자와 기준값을 입력해 주세요.");
      check(c.operator() != Operator.RANGE || c.upper() != null
              && (c.lower().compareTo(c.upper()) < 0 || c.lower().compareTo(c.upper()) == 0
              && c.lowerInclusive() && c.upperInclusive()), "숫자 구간의 시작/끝과 경계 포함 여부를 확인해 주세요.");
      for (BigDecimal v : Arrays.asList(c.lower(), c.upper()))
        check(v == null || v.abs().compareTo(new BigDecimal("1000000000")) <= 0 && v.scale() <= 3,
            "숫자 조건은 ±10억 이하, 소수 셋째 자리까지 가능합니다.");
    } else {
      check(source.control() != Control.FILE && Set.of(Operator.EQ, Operator.NE).contains(c.operator()),
          "파일 조건은 있음/없음만, 문자 조건은 일치/불일치/있음/없음만 가능합니다.");
      check(text(c.text()).length() <= 10000, "문자 조건이 너무 깁니다.");
    }
  }

  static List<Issue> coverage(Process p) {
    List<Issue> issues = new ArrayList<>();
    Map<String, Question> questions = p.questions().stream().collect(Collectors.toMap(Question::key, q -> q));
    for (InputLimit l : list(p.inputLimits())) if (!text(l.sourceKey()).isBlank()) {
      Question source = questions.get(l.sourceKey());
      List<String> allowed = choice(source.control()) ? source.choices().stream().map(Choice::key).toList() : List.of();
      // Conditions of a limit reference one numeric field. Unrelated field dimensions are irrelevant.
      if (source.control() == Control.NUMBER) {
        String field = l.cases().get(0).conditions().get(0).fieldKey();
        source = copy(source, source.fields().stream().filter(f -> f.key().equals(field)).toList());
      }
      List<Answer> samples = ProductStudioEngine.samples(source, source.required(), allowed, p);
      if (samples.size() > MAX_SCENARIOS) {
        error(issues, "LIMIT_COVERAGE", l.name(), "조건 범위 검증 한도를 초과했습니다. 범위를 단순화해 주세요."); continue;
      }
      Set<String> hit = new HashSet<>();
      for (Answer a : samples) {
        List<LimitCase> rows = l.cases().stream().filter(c -> c.conditions().stream().allMatch(x -> matches(x, a))).toList();
        rows.forEach(c -> hit.add(c.key()));
        if (rows.size() != 1) error(issues, "LIMIT_COVERAGE", l.name(),
            (rows.isEmpty() ? "입력제한의 빈 구간" : "입력제한 조건 중복") + ": " + label(source)
                + " 값 " + (choice(source.control()) ? a.choices() : a.fields())
                + (rows.isEmpty() ? "에 적용할 최소/최대값이 없습니다. 모든 구간/선택지를 빠짐없이 연결해 주세요."
                : "에서 " + rows.stream().map(LimitCase::name).collect(Collectors.joining(" / "))
                + " 조건이 동시에 적용됩니다. 경계 포함 또는 선택지 묶음을 분리해 주세요."));
      }
      for (LimitCase c : l.cases()) if (!hit.contains(c.key()))
        error(issues, "LIMIT_COVERAGE", l.name(), "[" + c.name() + "] 조건은 앞 질문의 입력 범위에서 성립하지 않습니다.");
    }
    return issues;
  }

  static Question copy(Question q, List<Field> fields) {
    return new Question(q.key(), q.groupId(), q.labels(), q.control(), q.fixed(), q.visible(),
        q.required(), q.requireRule(), q.question(), q.guide(), q.choices(), fields,
        q.assetIds(), q.numberCases(), q.preset());
  }
  static Field bound(Field f, BigDecimal min, BigDecimal max) {
    return new Field(f.key(), f.labels(), f.namePart(), f.required(), f.allowNegative(), min, max,
        f.step(), f.minLength(), f.maxLength(), f.format(), f.unit(), f.extensions(), f.minFiles(),
        f.maxFiles(), f.maxFileMB(), f.guide(), f.bundleKey(), f.assetIds());
  }
  private static BigDecimal greater(BigDecimal a, BigDecimal b) { return a == null ? b : b == null ? a : a.max(b); }
  private static BigDecimal lesser(BigDecimal a, BigDecimal b) { return a == null ? b : b == null ? a : a.min(b); }

  static Projection project(Process p, Question q, List<Field> shown,
      Map<String, Answer> answers, Set<String> visited, boolean visible, Set<String> hidden,
      Set<String> hiddenFields) {
    Map<String, Field> fields = new LinkedHashMap<>(); shown.forEach(f -> fields.put(f.key(), f));
    List<Bound> sums = new ArrayList<>(); List<String> errors = new ArrayList<>();
    if (!visible) return new Projection(shown, sums, errors);
    for (InputLimit l : list(p.inputLimits())) if (q.key().equals(l.targetKey())) {
      long shownCount = l.fieldKeys().stream().filter(fields::containsKey).count();
      if (shownCount == 0) continue;
      if ("SUM".equals(l.aggregation()) && shownCount != l.fieldKeys().size()) {
        errors.add("의존성 충돌: 합계 제한 [" + l.name() + "]의 일부 필드만 표시됩니다. 묶음 전체를 분기에 연결해 주세요."); continue;
      }
      BigDecimal min = l.min(), max = l.max();
      if (!text(l.sourceKey()).isBlank()) {
        Question source = p.questions().stream().filter(x -> x.key().equals(l.sourceKey())).findFirst().orElseThrow();
        if (!visited.contains(l.sourceKey())) {
          errors.add((hidden.contains(l.sourceKey()) ? "의존성 충돌: " : "앞 질문 입력 필요: ")
              + "입력제한 [" + l.name() + "]이 참조하는 [" + label(source)
              + "] 질문이 " + (hidden.contains(l.sourceKey()) ? "건너뛰어졌습니다. 선행 질문을 유지하거나 대상 질문도 건너뛰도록 수정해 주세요." : "완료되지 않았습니다."));
          continue;
        }
        if (l.cases().stream().flatMap(c -> c.conditions().stream())
            .anyMatch(c -> hiddenFields.contains(c.groupKey() + ":" + c.fieldKey()))) {
          errors.add("의존성 충돌: 입력제한 [" + l.name() + "]이 참조하는 앞 숫자필드가 분기로 숨겨졌습니다. 영향을 주는 필드를 함께 표시하거나 이 입력묶음도 숨기도록 수정해 주세요.");
          continue;
        }
        List<LimitCase> cases = l.cases().stream().filter(c -> c.conditions().stream()
            .allMatch(x -> matches(x, answers.get(l.sourceKey())))).toList();
        if (cases.size() != 1) {
          errors.add("의존성 충돌: 입력제한 [" + l.name() + "]에 적용 가능한 조건이 "
              + cases.size() + "개입니다. 앞 필드의 생략 여부 및 모든 범위/선택지의 연결을 확인해 주세요."); continue;
        }
        min = cases.get(0).min(); max = cases.get(0).max();
      }
      if ("SUM".equals(l.aggregation())) sums.add(new Bound(l, min, max));
      else for (String k : l.fieldKeys()) if (fields.containsKey(k)) {
        Field f = fields.get(k);
        BigDecimal baseMin = f.min() == null ? (f.allowNegative() ? new BigDecimal("-1000000000") : BigDecimal.ZERO) : f.min();
        BigDecimal effectiveMin = greater(baseMin, min), effectiveMax = lesser(f.max(), max);
        BigDecimal step = f.step() == null ? BigDecimal.ONE : f.step();
        BigDecimal anchor = f.min() == null ? BigDecimal.ZERO : f.min();
        if (effectiveMin != null) effectiveMin = effectiveMin.subtract(anchor)
            .divide(step, 0, RoundingMode.CEILING).multiply(step).add(anchor);
        if (effectiveMax != null) effectiveMax = effectiveMax.subtract(anchor)
            .divide(step, 0, RoundingMode.FLOOR).multiply(step).add(anchor);
        fields.put(k, bound(f, effectiveMin, effectiveMax));
      }
    }
    for (Field f : fields.values()) {
      if (f.min() != null && f.max() != null && f.min().compareTo(f.max()) > 0)
        errors.add("의존성 충돌: [" + f.labels().customer() + "]의 기본 범위와 입력제한이 겹치지 않습니다. 최소/최대값을 수정해 주세요.");
      else if (q.control() == Control.NUMBER) {
        BigDecimal lo = f.min() == null ? f.allowNegative() ? new BigDecimal("-1000000000") : BigDecimal.ZERO : f.min();
        BigDecimal hi = f.max() == null ? new BigDecimal("1000000000") : f.max();
        BigDecimal step = f.step() == null ? BigDecimal.ONE : f.step();
        BigDecimal anchor = f.min() == null ? BigDecimal.ZERO : f.min();
        BigDecimal first = lo.subtract(anchor).divide(step, 0, RoundingMode.CEILING).multiply(step).add(anchor);
        if (first.compareTo(hi) > 0)
          errors.add("의존성 충돌: [" + f.labels().customer() + "]의 입력 간격으로 허용 범위에 입력할 수 없습니다.");
      }
    }
    for (Bound b : sums) {
      BigDecimal min = BigDecimal.ZERO, max = BigDecimal.ZERO;
      for (String k : b.limit().fieldKeys()) {
        Field f = fields.get(k);
        min = min.add(f.min() == null ? f.allowNegative() ? new BigDecimal("-1000000000") : BigDecimal.ZERO : f.min());
        max = max.add(f.max() == null ? new BigDecimal("1000000000") : f.max());
      }
      if (b.min() != null && max.compareTo(b.min()) < 0 || b.max() != null && min.compareTo(b.max()) > 0)
        errors.add("의존성 충돌: 합계 제한 [" + b.limit().name() + "]을 만족하는 값이 없습니다. 개별 필드 범위와 합계 범위를 확인해 주세요.");
    }
    return new Projection(List.copyOf(fields.values()), sums, errors);
  }


  static List<String> sumHints(List<Bound> sums, List<Field> fields) {
    List<String> hints = new ArrayList<>();
    Map<String, Field> byKey = fields.stream().collect(Collectors.toMap(Field::key, f -> f));
    for (Bound b : sums) {
      String names = b.limit().fieldKeys().stream().map(k -> byKey.get(k).labels().customer())
          .collect(Collectors.joining(" + "));
      String bound = (b.min() == null ? "" : "최소 " + b.min().stripTrailingZeros().toPlainString())
          + (b.min() != null && b.max() != null ? ", " : "")
          + (b.max() == null ? "" : "최대 " + b.max().stripTrailingZeros().toPlainString());
      hints.add("합계 입력제한 [" + b.limit().name() + "]: " + names + " 합계 " + bound + ". 묶음의 모든 값을 입력해 주세요.");
    }
    return hints;
  }

  private record Cell(Field field, BigDecimal min, BigDecimal max) {}
  private static final class SearchBudget { long used; boolean exceeded; }

  /** One feasible witness per scalar predicate cell proves all branch truth vectors under SUM. */
  static List<Answer> sumSamples(Question q, boolean required, Process p, List<Bound> sums) {
    List<List<Cell>> dimensions = new ArrayList<>();
    for (Field f : q.fields()) {
      List<Condition> predicates = ProductStudioEngine.predicates(p).stream()
          .filter(c -> q.key().equals(c.groupKey()) && f.key().equals(c.fieldKey())).toList();
      List<BigDecimal> points = ProductStudioEngine.domain(q, f, p).stream()
          .map(ProductStudioEngine::number).sorted().toList();
      List<Cell> cells = new ArrayList<>();
      String previous = null; BigDecimal start = null, end = null;
      for (BigDecimal point : points) {
        String signature = ProductStudioEngine.signature(predicates, new Answer(List.of(), Map.of(f.key(), point)));
        if (!signature.equals(previous) && previous != null) { cells.add(new Cell(f, start, end)); start = null; }
        if (start == null) start = point;
        end = point; previous = signature;
      }
      if (start != null) cells.add(new Cell(f, start, end));
      if (!required || !f.required()) cells.add(new Cell(f, null, null));
      dimensions.add(cells);
    }
    List<Answer> result = new ArrayList<>(); SearchBudget budget = new SearchBudget();
    combine(q, dimensions, sums, 0, new LinkedHashMap<>(), result, budget);
    return budget.exceeded ? Collections.nCopies(MAX_SCENARIOS + 1, empty()) : result;
  }
  private static void combine(Question q, List<List<Cell>> dimensions, List<Bound> sums, int index,
      Map<String, Cell> row, List<Answer> result, SearchBudget budget) {
    if (budget.exceeded) return;
    if (++budget.used > MAX_SCENARIOS) { budget.exceeded = true; return; }
    if (index < dimensions.size()) {
      for (Cell c : dimensions.get(index)) {
        row.put(c.field().key(), c); combine(q, dimensions, sums, index + 1, row, result, budget);
        if (budget.exceeded) return;
      }
      return;
    }
    Map<String, Object> values = new LinkedHashMap<>();
    row.forEach((k, c) -> { if (c.min() != null) values.put(k, c.min()); });
    Map<String, List<Bound>> bundles = new LinkedHashMap<>();
    for (Bound b : sums) bundles.computeIfAbsent(String.join("|", new TreeSet<>(b.limit().fieldKeys())), k -> new ArrayList<>()).add(b);
    for (List<Bound> group : bundles.values()) {
      List<Cell> cells = group.get(0).limit().fieldKeys().stream().map(row::get).toList();
      long present = cells.stream().filter(c -> c.min() != null).count();
      if (present == 0) continue;
      if (present != cells.size()) return;
      BigDecimal min = null, max = null;
      for (Bound b : group) { min = greater(min, b.min()); max = lesser(max, b.max()); }
      if (min != null && max != null && min.compareTo(max) > 0) return;
      if (!solve(cells, 0, BigDecimal.ZERO, min, max, values, budget)) return;
    }
    result.add(new Answer(List.of(), values));
  }
  private static boolean solve(List<Cell> cells, int index, BigDecimal total, BigDecimal min,
      BigDecimal max, Map<String, Object> values, SearchBudget budget) {
    if (++budget.used > MAX_SCENARIOS) { budget.exceeded = true; return false; }
    if (index == cells.size()) return (min == null || total.compareTo(min) >= 0) && (max == null || total.compareTo(max) <= 0);
    Cell cell = cells.get(index);
    BigDecimal restMin = BigDecimal.ZERO, restMax = BigDecimal.ZERO;
    for (int i = index + 1; i < cells.size(); i++) { restMin = restMin.add(cells.get(i).min()); restMax = restMax.add(cells.get(i).max()); }
    BigDecimal lo = cell.min(), hi = cell.max();
    if (min != null) lo = lo.max(min.subtract(total).subtract(restMax));
    if (max != null) hi = hi.min(max.subtract(total).subtract(restMin));
    BigDecimal step = cell.field().step() == null ? BigDecimal.ONE : cell.field().step();
    lo = lo.subtract(cell.min()).divide(step, 0, RoundingMode.CEILING).multiply(step).add(cell.min());
    for (BigDecimal value = lo; value.compareTo(hi) <= 0; value = value.add(step)) {
      values.put(cell.field().key(), value);
      if (solve(cells, index + 1, total.add(value), min, max, values, budget)) return true;
      if (budget.exceeded) return false;
    }
    return false;
  }
  static List<String> sumErrors(List<Bound> sums, Answer answer, boolean required) {
    List<String> errors = new ArrayList<>();
    for (Bound b : sums) {
      BigDecimal sum = BigDecimal.ZERO; boolean complete = true; boolean any = false;
      for (String key : b.limit().fieldKeys()) {
        Object value = map(answer.fields()).get(key);
        if (value == null) { complete = false; continue; }
        any = true; sum = sum.add(number(value));
      }
      if (!complete) {
        if (any) errors.add("합계 제한 [" + b.limit().name() + "]: 묶음의 모든 값을 입력해 주세요.");
        continue;
      }
      if (b.min() != null && sum.compareTo(b.min()) < 0)
        errors.add("합계 제한 [" + b.limit().name() + "]: 합계는 최소 " + b.min() + " 이상이어야 합니다. 현재 합계 " + sum);
      if (b.max() != null && sum.compareTo(b.max()) > 0)
        errors.add("합계 제한 [" + b.limit().name() + "]: 합계는 최대 " + b.max() + " 이하여야 합니다. 현재 합계 " + sum);
    }
    return errors;
  }

  static List<String> notices(Process p, Question q, Answer answer, boolean visible) {
    if (!visible) return List.of();
    LinkedHashSet<String> messages = new LinkedHashSet<>();
    Set<String> shownFields = q.fields().stream().map(Field::key).collect(Collectors.toSet());
    for (InputNotice n : list(p.notices())) if (q.key().equals(n.targetKey())) {
      if (!text(n.fieldKey()).isBlank() && !shownFields.contains(n.fieldKey())) continue;
      if (list(n.conditions()).stream().anyMatch(c -> !text(c.fieldKey()).isBlank() && !shownFields.contains(c.fieldKey()))) continue;
      boolean matches = switch (n.mode()) {
        case "ALWAYS" -> true;
        case "ABSENT" -> text(n.fieldKey()).isBlank() ? blank(answer) : absent(map(answer.fields()).get(n.fieldKey()));
        default -> n.conditions().stream().allMatch(c -> matches(c, answer));
      };
      if (matches) messages.add(n.message());
    }
    if (choice(q.control())) for (Choice c : list(q.choices()))
      if (list(answer.choices()).contains(c.key()) && !text(c.guide()).isBlank()) messages.add(c.guide());
    for (Field f : list(q.fields())) if (!absent(map(answer.fields()).get(f.key())) && !text(f.guide()).isBlank()) messages.add(f.guide());
    for (NumberCase c : list(q.numberCases())) if (!text(c.guide()).isBlank()
        && c.conditions().stream().allMatch(x -> shownFields.contains(x.fieldKey()) && matches(x, answer))) messages.add(c.guide());
    return List.copyOf(messages);
  }
  private static boolean absent(Object value) {
    return value == null || value instanceof String s && s.isBlank() || value instanceof Collection<?> c && c.isEmpty();
  }
}
