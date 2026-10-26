package com.dev.HiddenBATHAuto.service.productmaster;

import static com.dev.HiddenBATHAuto.dto.productmaster.ProductPricingDtos.*;
import static com.dev.HiddenBATHAuto.dto.productmaster.ProductStudioDtos.*;
import static com.dev.HiddenBATHAuto.service.productmaster.ProductStudioEngine.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Explicit price/factor graph. Only PRICE roots contribute to the total. */
public final class ProductPricingGraph {
  private ProductPricingGraph() {}

  public static TermRole role(Term term) {
    return term.role() == null ? TermRole.PRICE : term.role();
  }

  public static MultiplierAggregation aggregation(MultiplierLink link) {
    return link.aggregation() == null ? MultiplierAggregation.EXACT_ONE : link.aggregation();
  }

  public static List<String> validate(List<Term> terms, Map<String, Question> questions) {
    List<String> errors = new ArrayList<>();
    Map<String, Term> byKey = new LinkedHashMap<>();
    for (Term term : list(terms)) if (term != null && term.key() != null) byKey.put(term.key(), term);
    if (byKey.values().stream().noneMatch(term -> role(term) == TermRole.PRICE))
      errors.add("최종가격에 합산할 금액 항을 한 개 이상 등록해 주세요. 배율 항만으로는 가격을 저장할 수 없습니다.");
    Set<String> linkKeys = new HashSet<>();
    for (Term term : byKey.values()) {
      if (list(term.multipliers()).size() > 20) errors.add(term.name() + ": 연결 배율은 항당 최대 20개입니다.");
      for (MultiplierLink link : list(term.multipliers())) try {
        require(link != null, "빈 배율 연결이 있습니다.");
        require(link.key() != null && link.key().matches("[A-Za-z0-9_-]{1,80}") && linkKeys.add(link.key()),
            "배율 연결 식별자가 없거나 중복되었습니다.");
        require(!text(link.name()).isBlank() && link.name().length() <= 120, "배율 연결 이름은 1~120자입니다.");
        require(!list(link.termKeys()).isEmpty() && list(link.termKeys()).size() <= 100,
            link.name() + ": 연결할 배율 후보를 1~100개 선택해 주세요.");
        require(new HashSet<>(link.termKeys()).size() == link.termKeys().size(), link.name() + ": 같은 배율 항을 중복 연결했습니다.");
        String checkboxGroup = null;
        for (String key : link.termKeys()) {
          Term factor = byKey.get(key);
          require(factor != null, link.name() + ": 연결한 배율 항이 없습니다: " + key);
          require(!Objects.equals(term.key(), key), link.name() + ": 자기 자신을 배율로 연결할 수 없습니다.");
          require(role(factor) == TermRole.FACTOR, link.name() + ": ‘" + factor.name() + "’은 금액 항입니다. 배율 항으로 명시한 항만 연결할 수 있습니다.");
          Question question = questions.get(factor.groupKey());
          if (aggregation(link) == MultiplierAggregation.EXACT_ONE) {
            require(question == null || question.control() != Control.CHECKBOX,
                link.name() + ": 여러선택형은 선택한 배율의 합 또는 곱을 명시해 주세요.");
          } else {
            require(question != null && question.control() == Control.CHECKBOX
                && list(factor.choiceKeys()).size() == 1,
                link.name() + ": 합/곱 배율은 같은 여러선택형 그룹의 옵션 한 개씩에 연결한 후보만 사용합니다.");
            if (checkboxGroup == null) checkboxGroup = factor.groupKey();
            require(Objects.equals(checkboxGroup, factor.groupKey()), link.name() + ": 서로 다른 여러선택형 그룹을 한 배율에 섞을 수 없습니다.");
          }
        }
      } catch (IllegalArgumentException ex) { errors.add(term.name() + ": " + ex.getMessage()); }
    }
    Set<String> visited = new HashSet<>(), visiting = new LinkedHashSet<>();
    for (String key : byKey.keySet()) visit(key, byKey, visited, visiting, errors);
    Set<String> reachable = new HashSet<>();
    for (Term term : byKey.values()) if (role(term) == TermRole.PRICE) reachable(term.key(), byKey, reachable);
    for (Term term : byKey.values()) if (role(term) == TermRole.FACTOR && !reachable.contains(term.key()))
      errors.add(term.name() + ": 최종 금액 항에 연결되지 않은 배율 항입니다. 사용할 금액 항에 연결하거나 삭제해 주세요.");
    return List.copyOf(errors);
  }

  private static void visit(String key, Map<String, Term> terms, Set<String> visited,
      Set<String> visiting, List<String> errors) {
    if (visited.contains(key) || !terms.containsKey(key)) return;
    if (!visiting.add(key)) {
      errors.add("배율 항이 순환 연결되었습니다: " + visiting.stream().map(k -> terms.get(k).name())
          .collect(Collectors.joining(" → ")) + " → " + terms.get(key).name());
      return;
    }
    for (MultiplierLink link : list(terms.get(key).multipliers())) if (link != null)
      for (String child : list(link.termKeys())) visit(child, terms, visited, visiting, errors);
    visiting.remove(key); visited.add(key);
  }

  private static void reachable(String key, Map<String, Term> terms, Set<String> reached) {
    if (!reached.add(key) || !terms.containsKey(key)) return;
    for (MultiplierLink link : list(terms.get(key).multipliers())) if (link != null)
      for (String child : list(link.termKeys())) reachable(child, terms, reached);
  }

  public record Raw(boolean applied, BigDecimal amount, String explanation) {}
  public record Outcome(List<TermResult> terms, List<String> errors) {}

  public static Outcome calculate(List<Term> terms, Map<String, QuestionState> states,
      Function<Term, Raw> raw) {
    Resolver resolver = new Resolver(terms, states, raw);
    for (Term term : terms) if (role(term) == TermRole.PRICE) resolver.resolve(term.key());
    List<TermResult> result = terms.stream().map(term -> resolver.results.getOrDefault(term.key(),
        new TermResult(term.key(), term.name(), false, BigDecimal.ZERO,
            "현재 적용되는 금액 항에서 사용하지 않는 배율입니다.", role(term), null, List.of()))).toList();
    return new Outcome(result, List.copyOf(resolver.errors));
  }

  private static final class Resolver {
    final Map<String, Term> terms;
    final Map<String, QuestionState> states;
    final Function<Term, Raw> calculateRaw;
    final Map<String, Raw> raw = new HashMap<>();
    final Map<String, TermResult> results = new HashMap<>();
    final Set<String> visiting = new HashSet<>();
    final List<String> errors = new ArrayList<>();

    Resolver(List<Term> terms, Map<String, QuestionState> states, Function<Term, Raw> calculateRaw) {
      this.terms = terms.stream().collect(Collectors.toMap(Term::key, term -> term));
      this.states = states; this.calculateRaw = calculateRaw;
    }

    Raw raw(Term term) { return raw.computeIfAbsent(term.key(), key -> calculateRaw.apply(term)); }

    TermResult resolve(String key) {
      if (results.containsKey(key)) return results.get(key);
      Term term = terms.get(key);
      require(term != null, "연결된 배율 항을 찾을 수 없습니다: " + key);
      require(visiting.add(key), "배율 항이 순환 연결되었습니다: " + term.name());
      Raw source = raw(term);
      List<MultiplierResult> multipliers = new ArrayList<>();
      TermResult result;
      if (!source.applied()) {
        result = new TermResult(key, term.name(), false, BigDecimal.ZERO, source.explanation(),
            role(term), source.amount(), List.of());
      } else try {
        require(source.amount() != null, source.explanation());
        BigDecimal amount = source.amount();
        String explanation = source.explanation();
        for (MultiplierLink link : list(term.multipliers())) {
          List<Term> selected = selected(link);
          List<FactorResult> factors = new ArrayList<>();
          BigDecimal value = aggregation(link) == MultiplierAggregation.SUM ? BigDecimal.ZERO : BigDecimal.ONE;
          for (Term factor : selected) {
            TermResult computed = resolve(factor.key());
            require(computed.applied() && computed.amount() != null,
                link.name() + ": ‘" + factor.name() + "’ 배율을 계산하지 못했습니다. " + computed.explanation());
            factors.add(new FactorResult(factor.key(), factor.name(), computed.amount()));
            value = aggregation(link) == MultiplierAggregation.SUM
                ? value.add(computed.amount()) : value.multiply(computed.amount());
          }
          multipliers.add(new MultiplierResult(link.key(), link.name(), aggregation(link), List.copyOf(factors), value));
          amount = amount.multiply(value);
          explanation += " × " + link.name() + "(" + value.stripTrailingZeros().toPlainString() + ")";
        }
        require(amount.signum() >= 0 && amount.compareTo(ProductPricingEngine.MAX_MONEY) <= 0,
            role(term) == TermRole.FACTOR ? "계산한 배율이 허용 범위를 벗어났습니다." : "배율 적용 후 항 금액이 허용 범위를 벗어났습니다.");
        if (role(term) == TermRole.PRICE) amount = amount.setScale(2, RoundingMode.HALF_UP);
        result = new TermResult(key, term.name(), true, amount, explanation,
            role(term), source.amount(), List.copyOf(multipliers));
      } catch (IllegalArgumentException ex) {
        errors.add(term.name() + ": " + ex.getMessage());
        result = new TermResult(key, term.name(), true, null, ex.getMessage(), role(term),
            source.amount(), List.copyOf(multipliers));
      } finally { visiting.remove(key); }
      visiting.remove(key);
      results.put(key, result);
      return result;
    }

    List<Term> selected(MultiplierLink link) {
      List<Term> candidates = list(link.termKeys()).stream().map(terms::get).toList();
      require(!candidates.isEmpty() && candidates.stream().noneMatch(Objects::isNull),
          link.name() + ": 연결한 배율 항을 찾을 수 없습니다.");
      if (aggregation(link) == MultiplierAggregation.EXACT_ONE) {
        List<Term> active = candidates.stream().filter(term -> raw(term).applied()).toList();
        require(active.size() == 1, link.name() + ": 현재 입력에 적용되는 배율 후보가 " + active.size()
            + "개입니다. 정확히 한 개가 적용되도록 옵션·숫자 구간·적용 조건을 확인해 주세요. 후보: "
            + candidates.stream().map(Term::name).collect(Collectors.joining(" / ")));
        return active;
      }
      QuestionState state = states.get(candidates.get(0).groupKey());
      List<String> choices = state == null || state.answer() == null ? List.of() : list(state.answer().choices());
      require(state != null && (state.visible() || state.question().fixed()) && !choices.isEmpty(),
          link.name() + ": 배율에 연결한 여러선택형 옵션을 한 개 이상 선택해 주세요. 빈 선택을 자동으로 0 또는 1로 처리하지 않습니다.");
      List<Term> selected = new ArrayList<>();
      for (String choice : choices) {
        List<Term> matches = candidates.stream().filter(term -> list(term.choiceKeys()).contains(choice)
            && raw(term).applied()).toList();
        String label = list(state.question().choices()).stream().filter(item -> item.key().equals(choice))
            .map(item -> item.labels().customer()).findFirst().orElse(choice);
        require(matches.size() == 1, link.name() + ": 선택한 ‘" + label + "’에 적용되는 배율 후보가 "
            + matches.size() + "개입니다. 선택 옵션마다 정확히 한 개의 배율을 연결해 주세요.");
        selected.add(matches.get(0));
      }
      return selected;
    }
  }

  private static void require(boolean valid, String message) {
    if (!valid) throw new IllegalArgumentException(message);
  }
}
