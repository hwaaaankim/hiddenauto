package com.dev.HiddenBATHAuto.service.productmaster;

import static com.dev.HiddenBATHAuto.service.productmaster.ProductStudioEngine.*;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductPricingDtos.*;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductStudioDtos.*;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductStudioDtos.Process;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/** Pure engine. Prices use the same evaluated, branch-filtered answers as the customer process. */
public final class ProductPricingEngine {
  private ProductPricingEngine() {}
  public static final BigDecimal MAX_MONEY = new BigDecimal("9999999999999.99");
  public static final int MAX_TERMS = 100, MAX_AXIS = 200, MAX_CELLS = 20000;

  public static TermRole role(Term term) { return ProductPricingGraph.role(term); }
  public static MultiplierAggregation aggregation(MultiplierLink link) {
    return ProductPricingGraph.aggregation(link);
  }

  public static String bundleKey(Field field) {
    return text(field.bundleKey()).isBlank() ? field.key() : field.bundleKey();
  }

  public static List<PricingGroup> groups(Process process, Set<Long> impactGroups) {
    List<PricingGroup> result = new ArrayList<>();
    for (Question q : list(process.questions())) {
      if (!impactGroups.contains(q.groupId())) continue;
      Map<String, List<Field>> grouped = new LinkedHashMap<>();
      if (q.control() == Control.NUMBER) for (Field f : list(q.fields()))
        grouped.computeIfAbsent(bundleKey(f), k -> new ArrayList<>()).add(f);
      List<Bundle> bundles = grouped.entrySet().stream().map(e -> new Bundle(e.getKey(),
          e.getValue().stream().map(f -> f.labels().customer()).collect(Collectors.joining(" / ")),
          List.copyOf(e.getValue()))).toList();
      result.add(new PricingGroup(q.groupId(), q.key(), label(q), q.control(), bundles,
          List.copyOf(list(q.choices()))));
    }
    return List.copyOf(result);
  }

  /** Numeric inputs may price their own term, an option range, or a conditional term. */
  public static Set<String> numericFieldReferences(Term term, String groupKey) {
    Set<String> fields = new LinkedHashSet<>();
    if (term == null) return fields;
    for (Term branch : list(term.branches())) fields.addAll(numericFieldReferences(branch, groupKey));
    if (Objects.equals(groupKey, term.groupKey())) fields.addAll(list(term.fieldKeys()));
    OptionRangePricing range = term.optionPricing();
    if (range != null && Objects.equals(groupKey, range.groupKey()) && range.axis() != null)
      fields.add(range.axis().fieldKey());
    for (PriceAdjustment adjustment : list(term.adjustments()))
      if (adjustment != null && Objects.equals(groupKey, adjustment.groupKey()) && adjustment.axis() != null)
        fields.add(adjustment.axis().fieldKey());
    for (Condition condition : list(term.conditions()))
      if (condition != null && Objects.equals(groupKey, condition.groupKey())) fields.add(condition.fieldKey());
    fields.removeIf(key -> text(key).isBlank());
    return fields;
  }

  /** Publication requirement, separate from evaluation so existing registered prices stay readable. */
  public static List<Issue> participationIssues(List<PricingGroup> groups, Definition definition, boolean draft) {
    List<Issue> issues = new ArrayList<>();
    for (PricingGroup group : groups) {
      if (group.control() != Control.NUMBER) continue;
      List<Field> fields = group.bundles().stream().flatMap(bundle -> bundle.fields().stream()).toList();
      Set<String> referenced = new HashSet<>();
      for (Term term : list(definition == null ? null : definition.terms()))
        referenced.addAll(numericFieldReferences(term, group.groupKey()));
      if (fields.stream().anyMatch(field -> referenced.contains(field.key()))) continue;
      String names = fields.stream().map(field -> field.labels().customer()).collect(Collectors.joining(" / "));
      issues.add(new Issue(draft ? "WARNING" : "ERROR", "PRICE_NUMERIC_GROUP_UNUSED", group.label(),
          group.label() + ": 가격영향이 켜진 숫자 그룹의 입력 " + fields.size() + "개"
              + (names.isBlank() ? "" : " (" + names + ")") + " 중 최소 1개를 가격계산식에 연결해야 저장할 수 있습니다. "
              + "가격 항의 입력, 숫자 범위별 옵션 금액의 기준, 또는 항의 숫자 적용 조건으로 연결해 주세요. "
              + "모든 입력이나 모든 묶음을 사용할 필요는 없습니다. 예: W·H 묶음 + D에서 W·H 2차원 항 하나 또는 D 1차원 항 하나도 가능합니다."
              + (draft ? " 현재 항은 반영할 수 있으며, 남은 그룹은 최종 저장 전에 연결하세요." : "")));
    }
    return List.copyOf(issues);
  }

  public static List<String> validate(Process process, List<PricingGroup> groups, Definition definition) {
    List<String> errors = new ArrayList<>();
    try { definition = ProductPricingBranches.expand(definition); }
    catch (IllegalArgumentException ex) { return List.of(ex.getMessage()); }
    if (definition == null || definition.schemaVersion() != 1) return List.of("버전 1 가격계산식이 필요합니다.");
    if (list(definition.terms()).isEmpty() || list(definition.terms()).size() > MAX_TERMS)
      return List.of("가격 항을 1~100개 등록해 주세요.");
    Map<String, PricingGroup> byGroup = groups.stream().collect(Collectors.toMap(PricingGroup::groupKey, x -> x));
    Map<String, Question> questions = process.questions().stream().collect(Collectors.toMap(Question::key, x -> x));
    Set<String> seen = new HashSet<>();
    for (Term term : definition.terms()) {
      String at = term == null ? "항" : text(term.name());
      try {
        require(term != null, "빈 항이 있습니다.");
        require(term.key() != null && term.key().matches("[A-Za-z0-9_-]{1,80}") && seen.add(term.key()),
            "항 식별자가 없거나 중복되었습니다.");
        require(!at.isBlank() && at.length() <= 120, "항 이름을 1~120자로 입력해 주세요.");
        require(term.type() != null, "계산 유형을 선택해 주세요.");
        PricingGroup group = byGroup.get(term.groupKey());
        require(group != null, "가격영향 그룹에 포함되지 않은 질문입니다.");
        require("ALL".equals(term.match()) || "ANY".equals(term.match()), "조건 결합은 ALL 또는 ANY입니다.");
        require(list(term.conditions()).size() <= 20, "항 조건은 최대 20개입니다.");
        for (Condition condition : list(term.conditions())) validateCondition(condition, questions);
        if (term.optionTarget() != null) {
          OptionTarget target = term.optionTarget();
          PricingGroup option = byGroup.get(target.groupKey());
          require(group.control() == Control.NUMBER && option != null && choice(option.control())
              && option.choices().stream().anyMatch(c -> c.key().equals(target.choiceKey())),
              "숫자 계산에 연결한 가격 대상 옵션이 없습니다. 옵션별 일괄 설정에서 다시 연결해 주세요.");
          require("ALL".equals(term.match()) && list(term.conditions()).stream().anyMatch(c ->
              Objects.equals(c.groupKey(), target.groupKey()) && c.operator() == Operator.EQ
                  && list(c.choiceKeys()).equals(List.of(target.choiceKey()))),
              "연결한 옵션이 선택됨 조건은 필수입니다. 해당 조건을 유지하고 조건 결합을 ‘모두 일치’로 설정하세요.");
        }
        List<String> fields = list(term.fieldKeys());
        require(new HashSet<>(fields).size() == fields.size(), "입력 필드가 중복되었습니다.");
        if (group.control() == Control.NUMBER) {
          Bundle bundle = group.bundles().stream().filter(b -> Objects.equals(b.key(), term.bundleKey()))
              .findFirst().orElseThrow(() -> new IllegalArgumentException("숫자 입력묶음을 선택해 주세요."));
          Set<String> expected = bundle.fields().stream().map(Field::key).collect(Collectors.toSet());
          require(!fields.isEmpty() && expected.containsAll(fields),
              "선택한 입력묶음 안에서 계산에 사용할 항목을 하나 이상 선택해 주세요.");
          require(list(term.choiceKeys()).isEmpty(), "숫자 항에 선택 보기를 지정할 수 없습니다.");
        } else {
          require(choice(group.control()), "가격 항은 숫자 입력형 또는 선택형 그룹으로 구성합니다.");
          require(term.type() == TermType.FIXED, "선택형 그룹은 고정형 가격을 사용합니다.");
          require(fields.isEmpty() && text(term.bundleKey()).isBlank(), "선택형 항에는 숫자 입력묶음을 지정할 수 없습니다.");
          Set<String> choices = group.choices().stream().map(Choice::key).collect(Collectors.toSet());
          require(choices.containsAll(list(term.choiceKeys()))
              && new HashSet<>(list(term.choiceKeys())).size() == list(term.choiceKeys()).size(),
              "항에 연결한 선택 보기가 없거나 중복되었습니다.");
        }
        validateOptionPricing(term, process, questions);
        validateAdjustments(term, questions, definition.terms());
        switch (term.type()) {
          case FIXED -> money(term.amount(), role(term) == TermRole.FACTOR ? "고정배율" : "고정금액", role(term) == TermRole.FACTOR ? 6 : 2);
          case MULTIPLY -> {
            require(group.control() == Control.NUMBER, "곱셈형은 숫자 입력묶음을 사용합니다.");
            require(map(term.rates()).keySet().equals(new HashSet<>(fields)), "모든 입력 필드에 단가를 하나씩 입력해 주세요.");
            for (String key : fields) money(term.rates().get(key), "곱셈 단가", 6);
          }
          case MATRIX_1D, MATRIX_2D -> {
            int dimensions = term.type() == TermType.MATRIX_1D ? 1 : 2;
            require(group.control() == Control.NUMBER && fields.size() == dimensions,
                dimensions + "차원 가격표는 같은 입력묶음에서 " + dimensions + "개 항목을 선택해 주세요.");
            validateAxis(term.xAxis(), fields, "X축");
            if (dimensions == 2) {
              validateAxis(term.yAxis(), fields, "Y축");
              require(!term.xAxis().fieldKey().equals(term.yAxis().fieldKey()), "X축과 Y축은 서로 다른 필드를 선택해 주세요.");
            } else require(term.yAxis() == null, "1차원 매트릭스에는 Y축이 없습니다.");
            int rows = dimensions == 2 ? term.yAxis().headers().size() : 1;
            int columns = term.xAxis().headers().size();
            require(rows * columns <= MAX_CELLS, "매트릭스는 최대 20,000개 금액입니다.");
            require(list(term.values()).size() == rows, "Y축 기준 수와 금액 행 수가 다릅니다.");
            for (List<BigDecimal> row : term.values()) {
              require(row != null && row.size() == columns, "X축 기준 수와 금액 열 수가 다르거나 빈 행이 있습니다.");
              for (BigDecimal amount : row) money(amount, role(term) == TermRole.FACTOR ? "매트릭스 배율" : "매트릭스 금액", role(term) == TermRole.FACTOR ? 6 : 2);
            }
          }
        }
      } catch (IllegalArgumentException ex) { errors.add(at + ": " + ex.getMessage()); }
    }
    errors.addAll(ProductPricingGraph.validate(definition.terms(), questions));
    return List.copyOf(errors);
  }

  private static void validateAxis(Axis axis, List<String> fields, String name) {
    require(axis != null && fields.contains(axis.fieldKey()) && axis.operator() != null, name + " 입력 필드와 이하/미만을 선택해 주세요.");
    require(!list(axis.headers()).isEmpty() && list(axis.headers()).size() <= MAX_AXIS, name + " 기준값을 1~200개 입력해 주세요.");
    require(axis.operators() == null || axis.operators().isEmpty()
        || axis.operators().size() == axis.headers().size(), name + " 기준값과 개별 이하/미만 설정 수가 다릅니다.");
    for (int i = 0; i < axis.headers().size(); i++)
      require(lowerAxis(axis) == (axis.operatorAt(i) == Threshold.GE || axis.operatorAt(i) == Threshold.GT),
          name + "의 작은 기준/큰 기준 방식을 한 축에서 섞을 수 없습니다.");
    BigDecimal previous = null;
    for (BigDecimal header : axis.headers()) {
      require(header != null && header.signum() >= 0 && header.precision() <= 24 && (long) header.precision() - header.scale() <= 24 && Math.max(0, header.scale()) <= 6,
          name + " 기준값은 0 이상의 소수 6자리 이내 숫자여야 합니다.");
      require(previous == null || header.compareTo(previous) > 0, name + " 기준값은 중복 없이 작은 값부터 입력해 주세요.");
      previous = header;
    }
  }

  private static void validateOptionPricing(Term term, Process process, Map<String, Question> questions) {
    OptionRangePricing pricing = term.optionPricing();
    if (pricing == null) return;
    Question owner = questions.get(term.groupKey()), source = questions.get(pricing.groupKey());
    require(term.type() == TermType.FIXED && owner != null && choice(owner.control())
        && !list(term.choiceKeys()).isEmpty(), "범위별 옵션 가격은 적용할 선택 옵션을 하나 이상 지정해 주세요.");
    require(source != null && source.control() == Control.NUMBER
        && process.questions().indexOf(source) < process.questions().indexOf(owner),
        "옵션보다 앞에 있는 숫자 입력 그룹을 선택해 주세요.");
    validateAxis(pricing.axis(), list(source.fields()).stream().map(Field::key).toList(), "옵션 가격 범위");
    require(!lowerAxis(pricing.axis()), "옵션 가격 범위는 각 구간의 끝값(이하/미만)을 사용합니다.");
    require(!pricing.axis().usesEdgeOutside(), "숫자 범위별 옵션 금액은 마지막 구간 금액을 사용합니다. 가격표의 끝 가격 적용 방식은 사용할 수 없습니다.");
    require(list(pricing.amounts()).size() == pricing.axis().headers().size(), "범위 수와 옵션 금액 수가 다릅니다.");
    for (BigDecimal amount : pricing.amounts()) money(amount, role(term) == TermRole.FACTOR ? "범위별 옵션 배율" : "범위별 옵션 금액", role(term) == TermRole.FACTOR ? 6 : 2);
    money(pricing.aboveAmount(), role(term) == TermRole.FACTOR ? "마지막 기준을 넘는 옵션 배율" : "마지막 기준을 넘는 옵션 금액", role(term) == TermRole.FACTOR ? 6 : 2);
  }

  private static void money(BigDecimal amount, String name, int scale) {
    require(amount != null && amount.signum() >= 0 && amount.compareTo(MAX_MONEY) <= 0
        && Math.max(0, amount.stripTrailingZeros().scale()) <= scale,
        name + "은 0~" + MAX_MONEY.toPlainString() + " 범위, 소수 " + scale + "자리 이내로 입력해 주세요.");
  }

  private static void validateAdjustments(Term term, Map<String, Question> questions, List<Term> allTerms) {
    List<PriceAdjustment> adjustments = list(term.adjustments());
    require(adjustments.isEmpty() || role(term) == TermRole.PRICE,
        "구간별 더하기·곱하기 보정은 금액 항에만 연결할 수 있습니다. 배율 항에는 원화 보정을 연결할 수 없습니다.");
    require(adjustments.size() <= 20, "구간별 보정은 항당 최대 20개입니다.");
    Set<String> keys = new HashSet<>();
    for (PriceAdjustment adjustment : adjustments) {
      require(adjustment != null, "빈 구간별 보정이 있습니다.");
      require(adjustment.key() != null && adjustment.key().matches("[A-Za-z0-9_-]{1,80}") && keys.add(adjustment.key()),
          "구간별 보정 식별자가 없거나 중복되었습니다.");
      require(!text(adjustment.name()).isBlank() && adjustment.name().length() <= 120,
          "구간별 보정 이름은 1~120자입니다.");
      if (adjustment.sameGroupOnly()) require(Objects.equals(term.groupKey(),adjustment.groupKey()) && adjustment.axis()!=null && !list(term.fieldKeys()).contains(adjustment.axis().fieldKey()) && adjustments.stream().noneMatch(other -> other != null && other != adjustment && Objects.equals(other.groupKey(),adjustment.groupKey()) && other.axis()!=null && Objects.equals(other.axis().fieldKey(),adjustment.axis().fieldKey())), "연관식은 같은 그룹에서 기본 항에 사용하지 않은 숫자만 선택하세요.");
      if (adjustment.sameGroupOnly()) {
        Set<String> visited=new HashSet<>();Deque<String> pending=new ArrayDeque<>();
        list(term.multipliers()).forEach(link->pending.addAll(list(link.termKeys())));
        while(!pending.isEmpty()) {
          String key=pending.removeFirst();if(!visited.add(key))continue;
          Term linked=allTerms.stream().filter(Objects::nonNull).filter(t->Objects.equals(t.key(),key)).findFirst().orElse(null);
          if(linked==null)continue;
          require(!Objects.equals(linked.groupKey(),adjustment.groupKey()) || !list(linked.fieldKeys()).contains(adjustment.axis().fieldKey()),"연관식에 고른 입력은 기존 배율 연결에서 이미 사용하고 있습니다.");
          list(linked.multipliers()).forEach(link->pending.addAll(list(link.termKeys())));
        }
      }
      Question source = questions.get(adjustment.groupKey());
      require(source != null && source.control() == Control.NUMBER, "구간별 보정의 숫자 입력 질문을 찾을 수 없습니다.");
      validateAxis(adjustment.axis(), list(source.fields()).stream().map(Field::key).toList(), "보정 구간");
      require(!lowerAxis(adjustment.axis()), "보정 구간은 작은 값부터 각 구간의 끝값(이하/미만)을 입력해 주세요.");
      require(!adjustment.axis().usesEdgeOutside(), "보정은 마지막 기준을 넘는 구간의 연산을 직접 설정합니다. 끝 가격 적용 방식은 사용할 수 없습니다.");
      require(list(adjustment.values()).size() == adjustment.axis().headers().size(), "보정 구간 수와 연산 수가 다릅니다.");
      for (AdjustmentValue value : adjustment.values()) validateAdjustmentValue(value);
      validateAdjustmentValue(adjustment.above());
    }
  }

  private static void validateAdjustmentValue(AdjustmentValue value) {
    require(value != null && value.operation() != null, "모든 보정 구간과 마지막 구간에 더하기 또는 곱하기를 선택해 주세요.");
    money(value.value(), value.operation() == AdjustmentOperation.ADD ? "더할 금액" : "곱할 배율",
        value.operation() == AdjustmentOperation.ADD ? 2 : 6);
  }

  private static boolean safeNumber(BigDecimal number) {
    return number != null && number.precision() <= 24
        && (long) number.precision() - number.scale() <= 24 && Math.max(0, number.scale()) <= 6;
  }

  private static void validateCondition(Condition condition, Map<String, Question> questions) {
    require(condition != null && condition.operator() != null && questions.containsKey(condition.groupKey()), "조건 질문을 찾을 수 없습니다.");
    Question q = questions.get(condition.groupKey());
    if (choice(q.control())) {
      require(text(condition.fieldKey()).isBlank(), "선택 조건에 숫자 필드를 지정할 수 없습니다.");
      require(Set.of(Operator.EQ, Operator.NE, Operator.PRESENT, Operator.ABSENT).contains(condition.operator()), "선택 조건 연산자가 잘못되었습니다.");
      if (condition.operator() == Operator.EQ || condition.operator() == Operator.NE)
        require(!list(condition.choiceKeys()).isEmpty()
            && q.choices().stream().map(Choice::key).collect(Collectors.toSet()).containsAll(condition.choiceKeys()), "조건 보기를 찾을 수 없습니다.");
    } else {
      require(list(q.fields()).stream().anyMatch(f -> f.key().equals(condition.fieldKey())), "조건 입력 필드를 찾을 수 없습니다.");
      if (q.control() == Control.NUMBER && !Set.of(Operator.PRESENT, Operator.ABSENT).contains(condition.operator())) {
        require(safeNumber(condition.lower()), "숫자 조건 비교값을 입력해 주세요.");
        if (condition.operator() == Operator.RANGE) {
          require(safeNumber(condition.upper()) && condition.lower().compareTo(condition.upper()) <= 0, "숫자 조건 범위를 확인해 주세요.");
          require(condition.lower().compareTo(condition.upper()) != 0 || condition.lowerInclusive() && condition.upperInclusive(), "같은 시작·끝 값은 양쪽 포함이어야 합니다.");
        }
      } else if (q.control() == Control.FILE) require(Set.of(Operator.PRESENT, Operator.ABSENT).contains(condition.operator()), "파일 조건은 있음/없음만 지원합니다.");
      else if (q.control() != Control.NUMBER) require(Set.of(Operator.EQ, Operator.NE, Operator.PRESENT, Operator.ABSENT).contains(condition.operator()), "문자 조건 연산자가 잘못되었습니다.");
    }
  }

  public static Calculation calculate(Process process, List<PricingGroup> groups,
      Definition definition, Map<String, Answer> submitted) {
    List<String> validation = validate(process, groups, definition);
    if (!validation.isEmpty()) return new Calculation(null, false, validation, List.of(), null);
    return calculateValidated(process, definition, submitted);
  }

  /** Internal batch entry after validate() has succeeded for this exact process and definition. */
  public static Calculation calculateValidated(Process process, Definition definition, Map<String, Answer> submitted) {
    Definition storedDefinition = definition;
    definition = ProductPricingBranches.expand(definition);
    Evaluation evaluated = ProductStudioEngine.evaluate(process, map(submitted));
    List<String> errors = new ArrayList<>(evaluated.errors());
    Map<String, QuestionState> states = evaluated.questions().stream()
        .collect(Collectors.toMap(s -> s.question().key(), s -> s));
    ProductPricingGraph.Outcome outcome = ProductPricingGraph.calculate(definition.terms(), states,
        term -> rawTerm(term, evaluated, states));
    errors.addAll(outcome.errors());
    List<TermResult> results = outcome.terms();
    BigDecimal total = results.stream().filter(result -> result.role() == TermRole.PRICE
        && result.applied() && result.amount() != null).map(TermResult::amount)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
    Set<String> applied = results.stream().filter(TermResult::applied).map(TermResult::key).collect(Collectors.toSet());
    List<Term> tables = definition.terms().stream().filter(t -> role(t) == TermRole.PRICE && matrix(t) && applied.contains(t.key())).toList();
    for (int i = 0; i < tables.size(); i++) for (int j = i + 1; j < tables.size(); j++) {
      Term a = tables.get(i), b = tables.get(j);
      if (!a.groupKey().equals(b.groupKey()) || !Objects.equals(a.optionTarget(), b.optionTarget())) continue;
      List<String> shared = a.fieldKeys().stream().filter(b.fieldKeys()::contains).toList();
      if (shared.isEmpty()) continue;
      Question q = states.get(a.groupKey()).question();
      String values = shared.stream().map(k -> fieldLabel(q, k) + "=" + map(evaluated.answers().get(a.groupKey()).fields()).get(k))
          .collect(Collectors.joining(", "));
      errors.add("가격표 중복: ‘" + a.name() + "’와 ‘" + b.name() + "’가 같은 입력(" + values
          + ")에 동시에 적용됩니다. 같은 가격 대상의 같은 숫자에는 가격표 한 개만 적용되어야 합니다. 중복 표를 삭제하거나 적용 조건을 겹치지 않게 나누세요. 예: W 600 이하 / W 600 초과.");
    }
    record TableScope(String groupKey, String fieldKey, OptionTarget target) {}
    Map<TableScope, List<Term>> scopes = new LinkedHashMap<>();
    for (Term term : definition.terms()) if (role(term) == TermRole.PRICE && matrix(term)) for (String field : term.fieldKeys())
      scopes.computeIfAbsent(new TableScope(term.groupKey(), field, term.optionTarget()), k -> new ArrayList<>()).add(term);
    for (Map.Entry<TableScope, List<Term>> entry : scopes.entrySet()) {
      TableScope scope = entry.getKey(); QuestionState state = states.get(scope.groupKey());
      Answer answer = evaluated.answers().get(scope.groupKey());
      if (state == null || !state.visible() && !state.question().fixed() || !state.errors().isEmpty()
          || !state.allowed().contains(scope.fieldKey()) || answer == null || !map(answer.fields()).containsKey(scope.fieldKey())) continue;
      if (scope.target() != null) {
        QuestionState target = states.get(scope.target().groupKey());
        if (target == null || !target.visible() && !target.question().fixed()
            || !list(target.answer().choices()).contains(scope.target().choiceKey())) continue;
      }
      if (entry.getValue().stream().noneMatch(t -> applied.contains(t.key()))) errors.add(
          "가격표 범위 누락: " + fieldLabel(state.question(), scope.fieldKey()) + "=" + answer.fields().get(scope.fieldKey())
              + "에서 적용되는 가격표가 없습니다: " + entry.getValue().stream().map(Term::name).collect(Collectors.joining(" / "))
              + ". 허용 입력 범위를 빠짐없이 나누세요. 예: 600 이하 / 600 초과. 무료 구간은 0원 가격표로 명시하세요.");
    }
    if (total.compareTo(MAX_MONEY) > 0) errors.add("최종가격이 허용 범위를 벗어났습니다. 계산 합계: "
        + total.toPlainString() + "원 / 허용 한도: " + MAX_MONEY.toPlainString() + "원. 각 항의 입력 수량·단가와 동시 적용 조건을 확인해 주세요.");
    boolean complete = errors.isEmpty();
    return new Calculation(complete ? total.setScale(2, RoundingMode.HALF_UP) : null, complete,
        List.copyOf(errors), ProductPricingBranches.results(storedDefinition,results), evaluated);
  }

  private static ProductPricingGraph.Raw rawTerm(Term term, Evaluation evaluated,
      Map<String, QuestionState> states) {
    QuestionState state = states.get(term.groupKey());
    String inactive = inactiveReason(term, state, evaluated, states);
    if (inactive != null) return new ProductPricingGraph.Raw(false, BigDecimal.ZERO, inactive);
      try {
        BigDecimal amount;
        String explanation;
        switch (term.type()) {
          case FIXED -> {
            OptionRangePricing pricing = term.optionPricing();
            if (pricing == null) { amount = term.amount(); explanation = (role(term) == TermRole.FACTOR ? "고정 배율 " : "고정 ") + amount.toPlainString() + (role(term) == TermRole.FACTOR ? "" : "원"); }
            else {
              QuestionState numericState = states.get(pricing.groupKey());
              require(numericState != null && (numericState.visible() || numericState.question().fixed())
                  && numericState.errors().isEmpty() && numericState.allowed().contains(pricing.axis().fieldKey()),
                  "옵션 가격에 연결한 숫자 입력이 현재 분기에서 표시·허용되지 않거나 입력 오류가 있습니다. 적용 조건을 확인해 주세요.");
              BigDecimal value = input(evaluated.answers(), pricing.groupKey(), pricing.axis().fieldKey());
              int selected = matchingIndex(pricing.axis(), value);
              amount = selected < 0 ? pricing.aboveAmount() : pricing.amounts().get(selected);
              explanation = fieldLabel(numericState.question(), pricing.axis().fieldKey()) + " " + value.toPlainString()
                  + " → " + (selected < 0 ? "마지막 기준 " + pricing.axis().headers().get(pricing.axis().headers().size() - 1).toPlainString()
                      + (pricing.axis().operatorAt(pricing.axis().headers().size() - 1) == Threshold.LE ? " 초과" : " 이상")
                      : thresholdLabel(pricing.axis(), selected)) + " · 옵션 금액 " + amount.toPlainString()
                  + (role(term) == TermRole.FACTOR ? " (선택한 배율 적용)" : "원 (기본금액과 중복 가산하지 않음)");
            }
          }
          case MULTIPLY -> {
            amount = BigDecimal.ZERO;
            List<String> pieces = new ArrayList<>();
            for (String key : term.fieldKeys()) {
              BigDecimal value = input(evaluated.answers(), term.groupKey(), key);
              BigDecimal rate = term.rates().get(key);
              amount = amount.add(value.multiply(rate));
              pieces.add(fieldLabel(state.question(), key) + " " + value.toPlainString() + " × " + rate.toPlainString());
            }
            explanation = String.join(" + ", pieces);
          }
          default -> {
            BigDecimal x = input(evaluated.answers(), term.groupKey(), term.xAxis().fieldKey());
            int column = index(term.xAxis(), x);
            int row = 0;
            explanation = fieldLabel(state.question(), term.xAxis().fieldKey()) + " " + x.toPlainString()
                + " → " + appliedLabel(term.xAxis(), x, column);
            if (term.type() == TermType.MATRIX_2D) {
              BigDecimal y = input(evaluated.answers(), term.groupKey(), term.yAxis().fieldKey());
              row = index(term.yAxis(), y);
              explanation += ", " + fieldLabel(state.question(), term.yAxis().fieldKey()) + " " + y.toPlainString()
                  + " → " + appliedLabel(term.yAxis(), y, row);
            }
            amount = term.values().get(row).get(column);
          }
        }
        require(amount.signum() >= 0 && amount.compareTo(MAX_MONEY) <= 0, (role(term) == TermRole.FACTOR ? "계산한 배율이 허용 범위를 벗어났습니다." : "계산한 항 금액이 허용 범위를 벗어났습니다.") + " 계산값: " + amount.toPlainString());
        BigDecimal baseAmount = amount;
        List<AdjustmentResult> adjustments = new ArrayList<>();
        for (PriceAdjustment adjustment : list(term.adjustments())) {
          QuestionState source = states.get(adjustment.groupKey());
          require(source != null && (source.visible() || source.question().fixed()) && source.errors().isEmpty()
              && source.allowed().contains(adjustment.axis().fieldKey()),
              adjustment.name() + ": 보정 기준 숫자 입력이 현재 분기에서 표시·허용되지 않거나 입력 오류가 있습니다. 금액 항의 적용 조건을 확인해 주세요.");
          BigDecimal numeric = input(evaluated.answers(), adjustment.groupKey(), adjustment.axis().fieldKey());
          int selected = matchingIndex(adjustment.axis(), numeric);
          AdjustmentValue operation = selected < 0 ? adjustment.above() : adjustment.values().get(selected);
          BigDecimal before = amount;
          BigDecimal operand = operation.useInput() ? numeric.multiply(operation.value()) : operation.value();
          amount = operation.operation() == AdjustmentOperation.ADD
              ? amount.add(operand) : amount.multiply(operand);
          require(amount.signum() >= 0 && amount.compareTo(MAX_MONEY) <= 0,
              adjustment.name() + ": 보정 후 금액이 허용 범위를 벗어났습니다. 계산값: " + amount.toPlainString());
          String range = adjustmentRange(adjustment.axis(), selected);
          adjustments.add(new AdjustmentResult(adjustment.key(), adjustment.name(), adjustment.groupKey(),
              adjustment.axis().fieldKey(), numeric, operation.operation(), operand, before, amount, range));
          explanation += " → " + adjustment.name() + " (" + fieldLabel(source.question(), adjustment.axis().fieldKey())
              + " " + numeric.toPlainString() + ", " + range + "): " + before.stripTrailingZeros().toPlainString()
              + (operation.operation() == AdjustmentOperation.ADD ? " + " : " × ")
              + operand.stripTrailingZeros().toPlainString() + " = " + amount.stripTrailingZeros().toPlainString();
        }
        return new ProductPricingGraph.Raw(true, amount, explanation, baseAmount, List.copyOf(adjustments));
      } catch (IllegalArgumentException ex) {
        return new ProductPricingGraph.Raw(true, null, ex.getMessage());
      }
  }

  static boolean matrix(Term term) {
    return term.type() == TermType.MATRIX_1D || term.type() == TermType.MATRIX_2D;
  }

  private static String inactiveReason(Term term, QuestionState source, Evaluation evaluation,
      Map<String, QuestionState> states) {
    if (source == null) return "계산에 연결한 입력 그룹을 찾을 수 없습니다.";
    if (!(source.visible() || source.question().fixed())) return "현재 분기에서 계산에 연결한 질문이 표시되지 않습니다.";
    if (!source.errors().isEmpty()) return "연결한 질문의 입력 오류를 먼저 수정해 주세요: " + String.join(" / ", source.errors());
    if (source.question().control() == Control.NUMBER
        && !source.allowed().containsAll(term.fieldKeys())) return "현재 분기에서 이 항의 숫자 입력 항목이 허용되지 않습니다.";
    if (choice(source.question().control())) {
      List<String> selected = list(source.answer().choices());
      if (selected.isEmpty() || !list(term.choiceKeys()).isEmpty()
          && selected.stream().noneMatch(term.choiceKeys()::contains)) return "이 항에 지정한 옵션이 선택되지 않았습니다.";
    }
    if (list(term.conditions()).isEmpty()) return null;
    java.util.function.Predicate<Condition> matches = condition -> {
      QuestionState state = states.get(condition.groupKey());
      // Reuse process matching; hidden questions/fields never satisfy even NE or ABSENT.
      return state != null && (state.visible() || state.question().fixed()) && state.errors().isEmpty()
          && evaluation.answers().containsKey(condition.groupKey())
          && (text(condition.fieldKey()).isBlank() || state.allowed().contains(condition.fieldKey()))
          && ProductStudioEngine.matches(condition, evaluation.answers().get(condition.groupKey()));
    };
    boolean applied = "ANY".equals(term.match()) ? term.conditions().stream().anyMatch(matches)
        : term.conditions().stream().allMatch(matches);
    return applied ? null : "추가 적용 조건이 충족되지 않았습니다 ("
        + ("ANY".equals(term.match()) ? "하나 이상 일치 필요" : "모두 일치 필요")
        + "). 조건의 질문·옵션이 현재 분기에 표시되는지도 확인해 주세요.";
  }

  private static String fieldLabel(Question question, String key) {
    return list(question.fields()).stream().filter(f -> f.key().equals(key))
        .map(f -> f.labels().customer()).findFirst().orElse(key);
  }

  private static BigDecimal input(Map<String, Answer> answers, String group, String field) {
    Object value = map(answers.getOrDefault(group, empty()).fields()).get(field);
    require(value != null, "가격계산에 필요한 입력값이 없습니다. 해당 항의 입력 필드를 작성해 주세요.");
    BigDecimal numeric = number(value);
    require(numeric.signum() >= 0, "가격계산 입력값은 음수를 사용할 수 없습니다.");
    return numeric;
  }

  public static boolean lowerAxis(Axis axis) {
    return axis.operator() == Threshold.GE || axis.operator() == Threshold.GT;
  }

  private static String appliedLabel(Axis axis, BigDecimal value, int index) {
    return matchingIndex(axis, value) < 0 && axis.usesEdgeOutside()
        ? axis.headers().get(index).toPlainString() + " 기준 (표 밖 입력 · 끝 가격 적용)"
        : thresholdLabel(axis, index);
  }

  private static String thresholdLabel(Axis axis, int index) {
    String suffix = switch (axis.operatorAt(index)) {
      case LE -> " 이하"; case LT -> " 미만"; case GE -> " 이상"; case GT -> " 초과";
    };
    return axis.headers().get(index).toPlainString() + suffix;
  }

  private static String adjustmentRange(Axis axis, int selected) {
    int previous = selected < 0 ? axis.headers().size() - 1 : selected - 1;
    String lower = previous < 0 ? "" : axis.headers().get(previous).toPlainString()
        + (axis.operatorAt(previous) == Threshold.LE ? " 초과" : " 이상");
    return selected < 0 ? lower : (lower.isEmpty() ? "" : lower + " ~ ") + thresholdLabel(axis, selected);
  }

  private static int matchingIndex(Axis axis, BigDecimal value) {
    if (lowerAxis(axis)) {
      for (int i = axis.headers().size() - 1; i >= 0; i--) {
        int compare = value.compareTo(axis.headers().get(i));
        if (compare > 0 || compare == 0 && axis.operatorAt(i) == Threshold.GE) return i;
      }
    } else for (int i = 0; i < axis.headers().size(); i++) {
      int compare = value.compareTo(axis.headers().get(i));
      if (compare < 0 || compare == 0 && axis.operatorAt(i) == Threshold.LE) return i;
    }
    return -1;
  }

  public static int index(Axis axis, BigDecimal value) {
    int selected = matchingIndex(axis, value);
    if (selected >= 0) return selected;
    int edge = lowerAxis(axis) ? 0 : axis.headers().size() - 1;
    if (axis.usesEdgeOutside()) return edge;
    throw new IllegalArgumentException("가격표 범위 밖: " + value.toPlainString() + "은 가격표의 "
        + (lowerAxis(axis) ? "최소" : "최대") + " 기준 " + thresholdLabel(axis, edge)
        + " 범위 밖입니다. 기준 행·열 또는 입력 제한을 확인해 주세요.");
  }

  private static void require(boolean valid, String message) {
    if (!valid) throw new IllegalArgumentException(message);
  }
}
