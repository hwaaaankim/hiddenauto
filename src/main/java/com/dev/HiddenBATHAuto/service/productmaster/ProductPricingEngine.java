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

  public static List<String> validate(Process process, List<PricingGroup> groups, Definition definition) {
    List<String> errors = new ArrayList<>();
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
        switch (term.type()) {
          case FIXED -> money(term.amount(), "고정금액", 2);
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
              for (BigDecimal amount : row) money(amount, "매트릭스 금액", 2);
            }
          }
        }
      } catch (IllegalArgumentException ex) { errors.add(at + ": " + ex.getMessage()); }
    }
    return List.copyOf(errors);
  }

  private static void validateAxis(Axis axis, List<String> fields, String name) {
    require(axis != null && fields.contains(axis.fieldKey()) && axis.operator() != null, name + " 입력 필드와 이하/미만을 선택해 주세요.");
    require(!list(axis.headers()).isEmpty() && list(axis.headers()).size() <= MAX_AXIS, name + " 기준값을 1~200개 입력해 주세요.");
    require(axis.operators() == null || axis.operators().isEmpty()
        || axis.operators().size() == axis.headers().size(), name + " 기준값과 개별 이하/미만 설정 수가 다릅니다.");
    BigDecimal previous = null;
    for (BigDecimal header : axis.headers()) {
      require(header != null && header.signum() >= 0 && header.precision() <= 24 && (long) header.precision() - header.scale() <= 24 && Math.max(0, header.scale()) <= 6,
          name + " 기준값은 0 이상의 소수 6자리 이내 숫자여야 합니다.");
      require(previous == null || header.compareTo(previous) > 0, name + " 기준값은 중복 없이 작은 값부터 입력해 주세요.");
      previous = header;
    }
  }

  private static void money(BigDecimal amount, String name, int scale) {
    require(amount != null && amount.signum() >= 0 && amount.compareTo(MAX_MONEY) <= 0
        && Math.max(0, amount.stripTrailingZeros().scale()) <= scale,
        name + "은 0~" + MAX_MONEY.toPlainString() + " 범위, 소수 " + scale + "자리 이내로 입력해 주세요.");
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
    Evaluation evaluated = ProductStudioEngine.evaluate(process, map(submitted));
    List<String> errors = new ArrayList<>(evaluated.errors());
    Map<String, QuestionState> states = evaluated.questions().stream()
        .collect(Collectors.toMap(s -> s.question().key(), s -> s));
    List<TermResult> results = new ArrayList<>();
    BigDecimal total = BigDecimal.ZERO;
    for (Term term : definition.terms()) {
      QuestionState state = states.get(term.groupKey());
      String inactive = inactiveReason(term, state, evaluated, states);
      if (inactive != null) {
        results.add(new TermResult(term.key(), term.name(), false, BigDecimal.ZERO, inactive));
        continue;
      }
      try {
        BigDecimal amount;
        String explanation;
        switch (term.type()) {
          case FIXED -> { amount = term.amount(); explanation = "고정 " + amount.toPlainString() + "원"; }
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
                + " → " + thresholdLabel(term.xAxis(), column);
            if (term.type() == TermType.MATRIX_2D) {
              BigDecimal y = input(evaluated.answers(), term.groupKey(), term.yAxis().fieldKey());
              row = index(term.yAxis(), y);
              explanation += ", " + fieldLabel(state.question(), term.yAxis().fieldKey()) + " " + y.toPlainString()
                  + " → " + thresholdLabel(term.yAxis(), row);
            }
            amount = term.values().get(row).get(column);
          }
        }
        require(amount.signum() >= 0 && amount.compareTo(MAX_MONEY) <= 0, "계산한 항 금액이 허용 범위를 벗어났습니다.");
        // Round each payable term to a won-cent boundary, then sum the displayed term prices.
        amount = amount.setScale(2, RoundingMode.HALF_UP);
        total = total.add(amount);
        results.add(new TermResult(term.key(), term.name(), true, amount, explanation));
      } catch (IllegalArgumentException ex) {
        errors.add(term.name() + ": " + ex.getMessage());
        results.add(new TermResult(term.key(), term.name(), true, null, ex.getMessage()));
      }
    }
    if (total.compareTo(MAX_MONEY) > 0) errors.add("최종가격이 허용 범위를 벗어났습니다.");
    boolean complete = errors.isEmpty();
    return new Calculation(complete ? total.setScale(2, RoundingMode.HALF_UP) : null, complete,
        List.copyOf(errors), List.copyOf(results), evaluated);
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

  private static String thresholdLabel(Axis axis, int index) {
    return axis.headers().get(index).toPlainString() + (axis.operatorAt(index) == Threshold.LE ? " 이하" : " 미만");
  }

  public static int index(Axis axis, BigDecimal value) {
    for (int i = 0; i < axis.headers().size(); i++) {
      int compare = value.compareTo(axis.headers().get(i));
      if (compare < 0 || compare == 0 && axis.operatorAt(i) == Threshold.LE) return i;
    }
    throw new IllegalArgumentException(value.toPlainString() + "은 매트릭스 최대 기준 "
        + axis.headers().get(axis.headers().size() - 1).toPlainString()
        + (axis.operatorAt(axis.headers().size() - 1) == Threshold.LE ? " 이하" : " 미만") + "를 벗어났습니다. 기준 행·열을 추가해 주세요.");
  }

  private static void require(boolean valid, String message) {
    if (!valid) throw new IllegalArgumentException(message);
  }
}
