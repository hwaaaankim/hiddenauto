package com.dev.HiddenBATHAuto.service.productmaster;

import static com.dev.HiddenBATHAuto.service.productmaster.ProductStudioEngine.*;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductPricingDtos.*;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductStudioDtos.*;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductStudioDtos.Process;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Exact schema identity plus semantic copy/remap, independent of Spring/JPA/JSON libraries. */
public final class ProductPricingCompatibility {
  private ProductPricingCompatibility() {}
  public static String fingerprint(Process process, Map<String, Answer> fixed) {
    return hash(Map.of("process", process, "fixedAnswers", fixed));
  }
  public static String signature(Process process, Map<String, Answer> fixed, Set<Long> impactGroups) {
    KeyMap keys = new KeyMap(process);
    return hash(Map.of("process", canonicalProcess(process, keys),
        "fixedAnswers", mapAnswers(fixed, keys), "priceGroups", impactGroups.stream().sorted().toList()));
  }
  private static String hash(Object value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
          .digest(encode(canonical(value)).getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException ex) {
      throw new IllegalStateException("가격 설정 해시를 생성할 수 없습니다.", ex);
    }
  }
  public static Object canonical(Object value) {
    if (value == null) return null;
    if (value instanceof Map<?, ?> source) {
      Map<String, Object> result = new TreeMap<>();
      source.forEach((key, item) -> result.put(key.toString(), canonical(item)));
      return result;
    }
    if (value instanceof Collection<?> items) return items.stream().map(ProductPricingCompatibility::canonical).toList();
    if (value instanceof Number number) return new BigDecimal(number.toString()).stripTrailingZeros().toPlainString();
    if (value instanceof Enum<?> item) return item.name();
    if (value.getClass().isRecord()) {
      Map<String, Object> result = new TreeMap<>();
      try {
        for (java.lang.reflect.RecordComponent component : value.getClass().getRecordComponents())
          result.put(component.getName(), canonical(component.getAccessor().invoke(value)));
      } catch (ReflectiveOperationException ex) {
        throw new IllegalStateException("가격 설정의 구조를 읽을 수 없습니다.", ex);
      }
      return result;
    }
    return value;
  }
  private static String encode(Object value) {
    if (value == null) return "n";
    if (value instanceof Map<?, ?> items) {
      StringBuilder result = new StringBuilder("m" + items.size() + ":");
      items.forEach((key, item) -> result.append(encode(key)).append(encode(item)));
      return result.toString();
    }
    if (value instanceof List<?> items) {
      StringBuilder result = new StringBuilder("l" + items.size() + ":");
      items.forEach(item -> result.append(encode(item)));
      return result.toString();
    }
    String string = String.valueOf(value);
    return (value instanceof Boolean ? "b" : "s") + string.length() + ":" + string;
  }

  /** Stable positional names preserve structure while allowing product-local UUID remapping. */
  private static final class KeyMap {
    final Map<String, String> questions = new LinkedHashMap<>();
    final Map<String, String> fields = new LinkedHashMap<>(), choices = new LinkedHashMap<>(), bundles = new LinkedHashMap<>();
    KeyMap(Process process) {
      for (int index = 0; index < process.questions().size(); index++) {
        Question q = process.questions().get(index);
        String prefix = "q" + index;
        questions.put(q.key(), prefix);
        for (int i = 0; i < list(q.fields()).size(); i++) {
          Field f = q.fields().get(i);
          fields.put(q.key() + ":" + f.key(), prefix + "_f" + i);
          bundles.computeIfAbsent(q.key() + ":" + ProductPricingEngine.bundleKey(f), key -> prefix + "_b" + bundles.size());
        }
        for (int i = 0; i < list(q.choices()).size(); i++) choices.put(q.key() + ":" + q.choices().get(i).key(), prefix + "_c" + i);
      }
    }
    String q(String key) { return questions.getOrDefault(key, key); }
    String f(String q, String key) { return key == null ? null : fields.getOrDefault(q + ":" + key, key); }
    String c(String q, String key) { return choices.getOrDefault(q + ":" + key, key); }
    String b(String q, String key) { return key == null ? null : bundles.getOrDefault(q + ":" + key, key); }
    Condition condition(Condition c) {
      return new Condition(q(c.groupKey()), f(c.groupKey(), c.fieldKey()), c.operator(),
          list(c.choiceKeys()).stream().map(key -> c(c.groupKey(), key)).toList(), c.text(), c.lower(), c.upper(), c.lowerInclusive(), c.upperInclusive());
    }
  }

  private static Process canonicalProcess(Process process, KeyMap keys) {
    List<Question> questions = new ArrayList<>();
    for (Question q : process.questions()) {
      List<Field> fields = list(q.fields()).stream().map(f -> new Field(keys.f(q.key(), f.key()), f.labels(), f.namePart(),
          f.required(), f.allowNegative(), f.min(), f.max(), f.step(), f.minLength(), f.maxLength(), f.format(), f.unit(),
          f.extensions(), f.minFiles(), f.maxFiles(), f.maxFileMB(), null,
          text(f.bundleKey()).isBlank() ? null : keys.b(q.key(), f.bundleKey()), List.of())).toList();
      List<Choice> choices = list(q.choices()).stream().map(c -> new Choice(keys.c(q.key(), c.key()), c.labels(), c.namePart(), List.of(), null)).toList();
      List<NumberCase> cases = new ArrayList<>();
      for (int i = 0; i < list(q.numberCases()).size(); i++) {
        NumberCase c = q.numberCases().get(i);
        cases.add(new NumberCase("case" + i, "case" + i, list(c.conditions()).stream().map(keys::condition).toList(), null));
      }
      Answer preset = q.preset() == null ? null : mapAnswer(q.key(), q.preset(), keys);
      questions.add(new Question(keys.q(q.key()), q.groupId(), q.labels(), q.control(), q.fixed(), q.visible(), q.required(),
          q.requireRule(), q.question(), null, choices, fields, List.of(), cases, preset));
    }
    List<Rule> rules = new ArrayList<>();
    for (int i = 0; i < list(process.rules()).size(); i++) {
      Rule r = process.rules().get(i);
      rules.add(new Rule("rule" + i, "rule" + i, r.match(), list(r.conditions()).stream().map(keys::condition).toList(),
          r.actions().stream().map(a -> new Action(keys.q(a.targetKey()), a.effect(), list(a.choiceKeys()).stream()
              .map(k -> keys.fields.containsKey(a.targetKey() + ":" + k) ? keys.f(a.targetKey(), k) : keys.c(a.targetKey(), k)).toList(), a.questionText())).toList()));
    }
    List<InputLimit> limits = new ArrayList<>();
    for (int i = 0; i < list(process.inputLimits()).size(); i++) {
      InputLimit limit = process.inputLimits().get(i);
      List<LimitCase> cases = new ArrayList<>();
      for (int j = 0; j < list(limit.cases()).size(); j++) {
        LimitCase c = limit.cases().get(j);
        cases.add(new LimitCase("case" + j, "case" + j, list(c.conditions()).stream().map(keys::condition).toList(), c.min(), c.max()));
      }
      limits.add(new InputLimit("limit" + i, "limit" + i, keys.q(limit.targetKey()), list(limit.fieldKeys()).stream().map(k -> keys.f(limit.targetKey(), k)).toList(),
          limit.aggregation(), limit.min(), limit.max(), keys.q(limit.sourceKey()), cases));
    }
    List<InputNotice> notices = new ArrayList<>();
    for (int i = 0; i < list(process.notices()).size(); i++) {
      InputNotice n = process.notices().get(i);
      notices.add(new InputNotice("notice" + i, keys.q(n.targetKey()), keys.f(n.targetKey(), n.fieldKey()), n.message(), n.mode(), list(n.conditions()).stream().map(keys::condition).toList()));
    }
    return new Process(process.schemaVersion(), questions, rules, limits, notices);
  }

  private static Answer mapAnswer(String question, Answer answer, KeyMap keys) {
    Map<String, Object> fields = new LinkedHashMap<>();
    map(answer.fields()).forEach((key, value) -> fields.put(keys.f(question, key), value));
    return new Answer(list(answer.choices()).stream().map(k -> keys.c(question, k)).toList(), fields);
  }
  private static Map<String, Answer> mapAnswers(Map<String, Answer> answers, KeyMap keys) {
    Map<String, Answer> result = new LinkedHashMap<>();
    map(answers).forEach((q, answer) -> result.put(keys.q(q), mapAnswer(q, answer, keys)));
    return result;
  }

  public static Definition remap(Definition definition, Process source, Process target, String fingerprint) {
    KeyMap from = new KeyMap(source), to = new KeyMap(target);
    Map<String, String> targetQuestion = invert(to.questions);
    Map<String, String> targetField = invert(to.fields), targetChoice = invert(to.choices), targetBundle = invert(to.bundles);
    java.util.function.Function<String, String> question = key -> requiredKey(targetQuestion.get(from.q(key)));
    java.util.function.BiFunction<String, String, String> field = (q, key) -> key == null ? null
        : unqualified(requiredKey(targetField.get(from.f(q, key))));
    java.util.function.BiFunction<String, String, String> choice = (q, key) -> unqualified(requiredKey(targetChoice.get(from.c(q, key))));
    java.util.function.Function<Condition, Condition> condition = c -> new Condition(question.apply(c.groupKey()),
        text(c.fieldKey()).isBlank() ? c.fieldKey() : field.apply(c.groupKey(), c.fieldKey()), c.operator(),
        list(c.choiceKeys()).stream().map(k -> choice.apply(c.groupKey(), k)).toList(), c.text(), c.lower(), c.upper(), c.lowerInclusive(), c.upperInclusive());
    List<Term> terms = new ArrayList<>();
    for (Term term : definition.terms()) {
      Map<String, BigDecimal> rates = new LinkedHashMap<>();
      map(term.rates()).forEach((k, v) -> rates.put(field.apply(term.groupKey(), k), v));
      Axis x = term.xAxis() == null ? null : new Axis(field.apply(term.groupKey(), term.xAxis().fieldKey()), term.xAxis().operator(), term.xAxis().headers(), term.xAxis().operators());
      Axis y = term.yAxis() == null ? null : new Axis(field.apply(term.groupKey(), term.yAxis().fieldKey()), term.yAxis().operator(), term.yAxis().headers(), term.yAxis().operators());
      String bundle = text(term.bundleKey()).isBlank() ? null : unqualified(requiredKey(targetBundle.get(from.b(term.groupKey(), term.bundleKey()))));
      OptionRangePricing pricing = term.optionPricing();
      OptionRangePricing mappedPricing = pricing == null ? null : new OptionRangePricing(
          question.apply(pricing.groupKey()), new Axis(field.apply(pricing.groupKey(), pricing.axis().fieldKey()),
              pricing.axis().operator(), pricing.axis().headers(), pricing.axis().operators()), pricing.amounts(), pricing.aboveAmount());
      terms.add(new Term(UUID.randomUUID().toString(), term.name(), question.apply(term.groupKey()), bundle,
          list(term.fieldKeys()).stream().map(k -> field.apply(term.groupKey(), k)).toList(), term.type(), term.amount(), rates, x, y,
          term.values(), term.match(), list(term.conditions()).stream().map(condition).toList(), list(term.choiceKeys()).stream().map(k -> choice.apply(term.groupKey(), k)).toList(), mappedPricing,
          term.optionTarget() == null ? null : new OptionTarget(question.apply(term.optionTarget().groupKey()),
              choice.apply(term.optionTarget().groupKey(), term.optionTarget().choiceKey()))));
    }
    Map<String, Answer> answers = new LinkedHashMap<>();
    map(definition.testAnswers()).forEach((q, answer) -> {
      Map<String, Object> fields = new LinkedHashMap<>();
      map(answer.fields()).forEach((key, value) -> fields.put(field.apply(q, key), value));
      answers.put(question.apply(q), new Answer(list(answer.choices()).stream().map(k -> choice.apply(q, k)).toList(), fields));
    });
    return new Definition(1, fingerprint, terms, answers);
  }
  private static String requiredKey(String key) {
    if (key == null) throw new IllegalArgumentException("복사할 프로세스 키를 연결할 수 없습니다. 그룹·옵션·입력묶음 구성을 확인해 주세요.");
    return key;
  }
  private static String unqualified(String key) { return key.substring(key.indexOf(':') + 1); }
  private static Map<String, String> invert(Map<String, String> original) {
    Map<String, String> inverse = new HashMap<>();
    original.forEach((key, value) -> inverse.put(value, key));
    return inverse;
  }
}
