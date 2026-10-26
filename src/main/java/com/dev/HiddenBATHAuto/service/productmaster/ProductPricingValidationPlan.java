package com.dev.HiddenBATHAuto.service.productmaster;

import static com.dev.HiddenBATHAuto.dto.productmaster.ProductPricingDtos.*;
import static com.dev.HiddenBATHAuto.dto.productmaster.ProductStudioDtos.*;
import static com.dev.HiddenBATHAuto.service.productmaster.ProductStudioEngine.*;

import com.dev.HiddenBATHAuto.dto.productmaster.ProductStudioDtos.Process;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/** Price components with their complete backwards process dependencies.
 * A validated full process is required before evaluating these projections. */
final class ProductPricingValidationPlan {
  private ProductPricingValidationPlan() {}
  record Part(Definition definition, Process process, Map<String, Answer> fixed, String name) {}
  record Bound(BigDecimal total, boolean unsafeIntermediate, boolean sumCoupledQuantity) {}

  /** SUM feasibility witnesses cover predicate cells, not continuous products inside
   * a cell. This interval bound proves monetary safety without treating a witness as
   * an optimiser. Saturation keeps malicious chains from allocating unbounded decimals. */
  static Bound conservativeBound(Part part) {
    return new Bounds(part).calculate();
  }
  private static final class Bounds {
    static final BigDecimal TOO_LARGE = ProductPricingEngine.MAX_MONEY.add(BigDecimal.ONE);
    final Part part;
    final Map<String, Term> terms;
    final Map<String, BigDecimal> values = new HashMap<>();
    boolean unsafe;
    Bounds(Part part) {
      this.part = part;
      terms = part.definition().terms().stream().collect(Collectors.toMap(Term::key, t -> t));
    }
    Bound calculate() {
      BigDecimal total = BigDecimal.ZERO;
      for (Term term : part.definition().terms()) if (ProductPricingEngine.role(term) == TermRole.PRICE)
        total = capped(total.add(value(term.key())));
      boolean coupled = list(part.process().inputLimits()).stream().filter(l -> "SUM".equals(l.aggregation()))
          .anyMatch(l -> part.definition().terms().stream().anyMatch(t ->
              t.type() == TermType.MULTIPLY && l.targetKey().equals(t.groupKey()) && !Collections.disjoint(l.fieldKeys(),t.fieldKeys())
              || list(t.adjustments()).stream().anyMatch(adjustment -> l.targetKey().equals(adjustment.groupKey())
                  && l.fieldKeys().contains(adjustment.axis().fieldKey())
                  && (adjustment.values().stream().anyMatch(AdjustmentValue::useInput) || adjustment.above().useInput()))));
      return new Bound(total, unsafe, coupled);
    }
    BigDecimal capped(BigDecimal value) { return value.min(TOO_LARGE); }
    BigDecimal value(String key) {
      if (values.containsKey(key)) return values.get(key);
      Term term = terms.get(key);
      BigDecimal value;
      if (term.type() == TermType.FIXED) {
        value = term.optionPricing() == null ? term.amount()
            : list(term.optionPricing().amounts()).stream().reduce(term.optionPricing().aboveAmount(), BigDecimal::max);
      } else if (term.type() == TermType.MULTIPLY) {
        value = BigDecimal.ZERO;
        Question q = part.process().questions().stream().filter(x -> x.key().equals(term.groupKey())).findFirst().orElseThrow();
        for (String field : term.fieldKeys()) {
          Field f = q.fields().stream().filter(x -> x.key().equals(field)).findFirst().orElseThrow();
          BigDecimal maximum = f.max() == null ? new BigDecimal("1000000000") : f.max().max(BigDecimal.ZERO);
          value = capped(value.add(maximum.multiply(term.rates().get(field))));
        }
      } else value = term.values().stream().flatMap(List::stream).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
      if (value.compareTo(ProductPricingEngine.MAX_MONEY) > 0) unsafe = true;
      // Corrections belong to the raw unit price and run before quantity/factor links.
      // Both supported operations are monotone for the validated non-negative amounts.
      for (PriceAdjustment adjustment : list(term.adjustments())) {
        List<AdjustmentValue> candidates = new ArrayList<>(adjustment.values());
        candidates.add(adjustment.above());
        BigDecimal maximum = BigDecimal.ZERO;
        for (AdjustmentValue candidate : candidates) {
          BigDecimal operand = candidate.value();
          if (candidate.useInput()) {
            Field source = part.process().questions().stream().filter(q -> q.key().equals(adjustment.groupKey())).flatMap(q -> q.fields().stream()).filter(f -> f.key().equals(adjustment.axis().fieldKey())).findFirst().orElseThrow();
            operand = operand.multiply(source.max() == null ? new BigDecimal("1000000000") : source.max().max(BigDecimal.ZERO));
          }
          maximum = maximum.max(candidate.operation() == AdjustmentOperation.ADD
              ? capped(value.add(operand)) : capped(value.multiply(operand)));
        }
        value = maximum;
        if (value.compareTo(ProductPricingEngine.MAX_MONEY) > 0) unsafe = true;
      }
      for (MultiplierLink link : list(term.multipliers())) {
        MultiplierAggregation aggregation = ProductPricingEngine.aggregation(link);
        BigDecimal multiplier = aggregation == MultiplierAggregation.PRODUCT ? BigDecimal.ONE : BigDecimal.ZERO;
        for (String child : link.termKeys()) {
          BigDecimal candidate = value(child);
          multiplier = switch (aggregation) {
            case EXACT_ONE -> multiplier.max(candidate);
            case SUM -> capped(multiplier.add(candidate));
            // Any non-empty subset may be selected. Values below one cannot lower an upper bound.
            case PRODUCT -> capped(multiplier.multiply(candidate.max(BigDecimal.ONE)));
          };
        }
        value = capped(value.multiply(multiplier));
        if (value.compareTo(ProductPricingEngine.MAX_MONEY) > 0) unsafe = true;
      }
      if (ProductPricingEngine.role(term) == TermRole.PRICE) value = value.setScale(2, RoundingMode.HALF_UP);
      values.put(key, value);
      return value;
    }
  }

  static List<Part> build(Process process, Definition definition, Map<String, Answer> fixed) {
    Map<String, Term> terms = definition.terms().stream()
        .collect(Collectors.toMap(Term::key, t -> t, (a, b) -> a, LinkedHashMap::new));
    Map<String, Question> questions = process.questions().stream()
        .collect(Collectors.toMap(Question::key, q -> q));
    List<Term> roots = definition.terms().stream()
        .filter(t -> ProductPricingEngine.role(t) == TermRole.PRICE).toList();
    List<Set<String>> closures = new ArrayList<>(), scopes = new ArrayList<>();
    for (Term root : roots) {
      Set<String> closure = new LinkedHashSet<>(); closure(root.key(), terms, closure);
      closures.add(closure); scopes.add(scopes(root, questions));
    }
    int[] parent = new int[roots.size()];
    for (int i = 0; i < parent.length; i++) parent[i] = i;
    for (int i = 0; i < roots.size(); i++) for (int j = 0; j < i; j++)
      if (!Collections.disjoint(closures.get(i), closures.get(j))
          || !Collections.disjoint(scopes.get(i), scopes.get(j))) parent[find(parent, i)] = find(parent, j);
    Map<Integer, Set<String>> components = new LinkedHashMap<>();
    for (int i = 0; i < roots.size(); i++)
      components.computeIfAbsent(find(parent, i), k -> new LinkedHashSet<>()).addAll(closures.get(i));
    List<Part> result = new ArrayList<>();
    for (Set<String> keys : components.values()) {
      List<Term> selected = definition.terms().stream().filter(t -> keys.contains(t.key())).toList();
      Set<String> needed = new LinkedHashSet<>();
      for (Term term : selected) {
        needed.add(term.groupKey());
        list(term.conditions()).forEach(c -> needed.add(c.groupKey()));
        if (term.optionTarget() != null) needed.add(term.optionTarget().groupKey());
        if (term.optionPricing() != null) needed.add(term.optionPricing().groupKey());
        list(term.adjustments()).forEach(a -> needed.add(a.groupKey()));
      }
      boolean expanded;
      do {
        int before = needed.size();
        for (Rule rule : list(process.rules()))
          if (list(rule.actions()).stream().anyMatch(a -> needed.contains(a.targetKey())))
            list(rule.conditions()).forEach(c -> needed.add(c.groupKey()));
        for (InputLimit limit : list(process.inputLimits())) if (needed.contains(limit.targetKey())) {
          if (!text(limit.sourceKey()).isBlank()) needed.add(limit.sourceKey());
          list(limit.cases()).forEach(c -> list(c.conditions()).forEach(x -> needed.add(x.groupKey())));
        }
        expanded = needed.size() != before;
      } while (expanded);
      // Keep every field in a needed question. Field-level/SUM constraints and numeric
      // bundle visibility must never be weakened by splitting W/H and D price tables.
      List<Question> projectedQuestions = process.questions().stream().filter(q -> needed.contains(q.key()))
          .map(q -> new Question(q.key(), q.groupId(), q.labels(), q.control(), q.fixed(), q.visible(),
              q.required(), q.requireRule(), q.question(), q.guide(), q.choices(), q.fields(),
              q.assetIds(), List.of(), q.preset())).toList();
      List<Rule> rules = list(process.rules()).stream()
          .filter(r -> list(r.actions()).stream().anyMatch(a -> needed.contains(a.targetKey())))
          .map(r -> new Rule(r.key(), r.name(), r.match(), r.conditions(),
              list(r.actions()).stream().filter(a -> needed.contains(a.targetKey())).toList())).toList();
      List<InputLimit> limits = list(process.inputLimits()).stream()
          .filter(l -> needed.contains(l.targetKey())).toList();
      // Number-case/notice text changes no price or permitted input. Their own consistency
      // was checked on the full process; retaining their predicates would multiply work.
      Process projected = new Process(process.schemaVersion(), projectedQuestions, rules, limits, List.of());
      Map<String, Answer> answers = new LinkedHashMap<>();
      map(fixed).forEach((key, value) -> { if (needed.contains(key)) answers.put(key, value); });
      String name = selected.stream().filter(t -> ProductPricingEngine.role(t) == TermRole.PRICE)
          .map(Term::name).collect(Collectors.joining(" / "));
      result.add(new Part(new Definition(definition.schemaVersion(), definition.schemaFingerprint(),
          selected, definition.testAnswers()), projected, answers, name));
    }
    return List.copyOf(result);
  }

  private static int find(int[] parent, int index) {
    while (parent[index] != index) { parent[index] = parent[parent[index]]; index = parent[index]; }
    return index;
  }
  private static void closure(String key, Map<String, Term> terms, Set<String> keys) {
    if (!keys.add(key)) return;
    for (MultiplierLink link : list(terms.get(key).multipliers()))
      for (String child : list(link.termKeys())) closure(child, terms, keys);
  }
  private static Set<String> scopes(Term term, Map<String, Question> questions) {
    if (term.optionTarget() != null)
      return Set.of(term.optionTarget().groupKey() + ":" + term.optionTarget().choiceKey());
    Question question = questions.get(term.groupKey());
    List<String> keys = choice(question.control())
        ? list(term.choiceKeys()).isEmpty() ? question.choices().stream().map(Choice::key).toList() : term.choiceKeys()
        : list(term.fieldKeys());
    return keys.stream().map(key -> term.groupKey() + ":" + key).collect(Collectors.toSet());
  }
}
