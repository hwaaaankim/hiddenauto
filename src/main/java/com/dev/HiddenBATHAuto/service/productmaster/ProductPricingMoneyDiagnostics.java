package com.dev.HiddenBATHAuto.service.productmaster;

import static com.dev.HiddenBATHAuto.dto.productmaster.ProductPricingDtos.*;
import static com.dev.HiddenBATHAuto.dto.productmaster.ProductStudioDtos.*;
import static com.dev.HiddenBATHAuto.service.productmaster.ProductStudioEngine.*;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/** Explains a real failing witness; never changes its inputs or the monetary limit. */
final class ProductPricingMoneyDiagnostics {
  private ProductPricingMoneyDiagnostics() {}

  static boolean monetary(String message) {
    return message != null && message.contains("허용 범위를 벗어났습니다.");
  }

  static List<Issue> issues(Definition definition, Calculation calculation) {
    List<Issue> issues = new ArrayList<>();
    Map<String, Term> terms = definition.terms().stream()
        .collect(Collectors.toMap(Term::key, t -> t));
    Map<String, TermResult> results = calculation.terms().stream()
        .collect(Collectors.toMap(TermResult::key, t -> t));
    for (TermResult result : calculation.terms()) {
      if (!result.applied() || result.amount() != null || !monetary(result.explanation())) continue;
      Term term = terms.get(result.key());
      if (term == null) continue;
      Set<String> needed = new LinkedHashSet<>();
      dependencies(term.key(), terms, results, needed);
      issues.add(new Issue("ERROR", "PRICE_TERM_MONEY_LIMIT", term.name(),
          "‘" + term.name() + "’ 계산 중 금액·배율이 허용 범위를 벗어났습니다. "
              + result.explanation() + " 허용 범위: 0~" + amount(ProductPricingEngine.MAX_MONEY) + ". "
              + inputs(needed, terms, results, calculation.evaluation()) + guidance(),
          "terms." + term.key()));
    }
    BigDecimal total = calculation.terms().stream().filter(r -> r.role() == TermRole.PRICE
        && r.applied() && r.amount() != null).map(TermResult::amount)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
    if (total.compareTo(ProductPricingEngine.MAX_MONEY) > 0) {
      Set<String> needed = new LinkedHashSet<>();
      List<TermResult> prices = calculation.terms().stream().filter(r -> r.role() == TermRole.PRICE
          && r.applied() && r.amount() != null).toList();
      for (TermResult price : prices) dependencies(price.key(), terms, results, needed);
      String components = prices.stream().map(r -> r.name() + " = " + amount(r.amount()) + "원")
          .collect(Collectors.joining(" / "));
      issues.add(new Issue("ERROR", "PRICE_TOTAL_MONEY_LIMIT", "동시에 적용되는 금액 항의 합계",
          "각 항이 개별 한도 이내여도 합계는 한도를 넘을 수 있습니다. 재현 합계: " + amount(total)
              + "원 / 허용 한도: " + amount(ProductPricingEngine.MAX_MONEY) + "원. "
              + "동시 적용 항: " + components + ". "
              + inputs(needed, terms, results, calculation.evaluation()) + guidance(), null));
    }
    return issues;
  }

  private static void dependencies(String key, Map<String, Term> terms,
      Map<String, TermResult> results, Set<String> needed) {
    TermResult result = results.get(key);
    if (result == null || !result.applied() || !needed.add(key)) return;
    Term term = terms.get(key);
    if (term != null) for (MultiplierLink link : list(term.multipliers()))
      for (String child : list(link.termKeys())) dependencies(child, terms, results, needed);
  }

  private static String inputs(Set<String> needed, Map<String, Term> terms,
      Map<String, TermResult> results, Evaluation evaluation) {
    List<String> lines = new ArrayList<>();
    Map<String, QuestionState> states = evaluation.questions().stream()
        .collect(Collectors.toMap(s -> s.question().key(), s -> s));
    for (String key : needed) {
      Term term = terms.get(key);
      if (term == null) continue;
      QuestionState state = states.get(term.groupKey());
      if (term.type() == TermType.MULTIPLY && state != null) for (String fieldKey : term.fieldKeys()) {
        Field field = state.question().fields().stream().filter(f -> f.key().equals(fieldKey))
            .findFirst().orElse(null);
        if (field == null) continue;
        Object input = map(state.answer().fields()).get(fieldKey);
        lines.add(field.labels().customer() + " = " + input + " × 단가"
            + " " + amount(term.rates().get(fieldKey))
            + (role(term) == TermRole.FACTOR ? " (배율용)" : "원")
            + " [이 경로의 개별 최대: " + (field.max() == null ? "미설정" : amount(field.max())) + "]");
      }
      for (PriceAdjustment adjustment : list(term.adjustments())) {
        QuestionState source = states.get(adjustment.groupKey());
        if (source == null) continue;
        Field field = source.question().fields().stream()
            .filter(f -> f.key().equals(adjustment.axis().fieldKey())).findFirst().orElse(null);
        if (field != null) lines.add(adjustment.name() + " 기준 " + field.labels().customer() + " = "
            + map(source.answer().fields()).get(field.key()) + " [이 경로의 개별 최대: "
            + (field.max() == null ? "미설정" : amount(field.max())) + "]");
      }
      TermResult result = results.get(key);
      if (result != null) for (MultiplierResult link : list(result.multipliers()))
        lines.add(term.name() + " × " + link.name() + " = " + amount(link.value()) + "배");
    }
    return lines.isEmpty() ? "" : "관련 계산 입력: " + String.join(" / ", lines) + ". ";
  }

  private static String guidance() {
    return "실제 고객이 입력한 수량이라는 뜻이 아니라, 프로세스가 허용하는 값으로 만든 검증 사례입니다. "
        + "최대 미설정 숫자는 엔진의 절댓값 1,000,000,000 이내 범위까지 검사하며 입력 간격·분기·합계 제한도 반영합니다. "
        + "프로세스에서 해당 수량의 실제 주문 가능 최대값과 입력 간격을 정하고 검증·등록완료하거나, "
        + "단가·배율 또는 동시에 적용되는 조건이 의도한 값인지, 음수 입력으로 금액이 0 미만이 되는지도 확인하세요. "
        + "일괄 수량 상한이나 금액 한도를 임의로 바꾸지 않습니다. 수정 후 최신 프로세스를 불러와 전체 검사·테스트·저장을 다시 진행하세요.";
  }

  private static TermRole role(Term term) { return ProductPricingEngine.role(term); }
  private static String amount(BigDecimal value) {
    return value == null ? "계산 불가" : value.stripTrailingZeros().toPlainString();
  }
}
