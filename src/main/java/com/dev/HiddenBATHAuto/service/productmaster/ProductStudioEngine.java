package com.dev.HiddenBATHAuto.service.productmaster;

import static com.dev.HiddenBATHAuto.dto.productmaster.ProductStudioDtos.*;

import com.dev.HiddenBATHAuto.dto.productmaster.ProductStudioDtos.Process;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Deterministic forward-only rule interpreter; shared by preview, publication and public
 * evaluation.
 */
public final class ProductStudioEngine {
  private ProductStudioEngine() {}

  public static final int MAX_QUESTIONS = 40;
  public static final int MAX_RULES = 200;
  public static final int MAX_SCENARIOS = 20_000;
  private static final Set<String> FORMATS = Set.of("ANY", "EMAIL", "PHONE", "ALPHANUMERIC");

  public static <T> List<T> list(List<T> value) {
    return value == null ? List.of() : value;
  }

  public static <K, V> Map<K, V> map(Map<K, V> value) {
    return value == null ? Map.of() : value;
  }

  public static boolean choice(Control type) {
    return type == Control.RADIO || type == Control.CHECKBOX;
  }

  public static String text(String value) {
    return value == null ? "" : value;
  }

  public static Answer empty() {
    return new Answer(List.of(), Map.of());
  }

  public static boolean blank(Answer a) {
    return a == null || (list(a.choices()).isEmpty() && map(a.fields()).isEmpty());
  }

  public static String label(Question q) {
    return q.labels() == null ? q.key() : q.labels().management();
  }

  private static void issue(
      List<Issue> issues, String severity, String code, String at, String message) {
    if (issues.size() >= 200) {
      if (issues.size() == 200)
        issues.add(
            new Issue(
                "WARNING",
                "ISSUE_LIMIT",
                "검증 결과",
                "오류·안내가 많아 처음 200개를 표시합니다. 표시된 내용을 수정하고 다시 검증해 주세요."));
      return;
    }
    Issue issue = new Issue(severity, code, at, message);
    if (!issues.contains(issue)) issues.add(issue);
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalArgumentException(message);
  }

  public static void labels(Labels labels, int max) {
    require(labels != null, "표시명을 입력해 주세요.");
    for (String s :
        List.of(text(labels.customer()), text(labels.production()), text(labels.management())))
      require(!s.isBlank() && s.length() <= max, "고객·생산팀·관리팀 표시명은 1~" + max + "자여야 합니다.");
  }

  public static String normalizedLabel(String value) {
    return java.text.Normalizer.normalize(text(value).strip(), java.text.Normalizer.Form.NFC);
  }

  public static void uniqueLabels(List<Labels> items, String subject) {
    for (int audience = 0; audience < 3; audience++) {
      Set<String> seen = new HashSet<>();
      for (Labels item : items) {
        require(item != null, subject + " 표시명을 입력해 주세요.");
        String value =
            switch (audience) {
              case 0 -> item.customer();
              case 1 -> item.production();
              default -> item.management();
            };
        String canonical = normalizedLabel(value).toLowerCase(java.util.Locale.ROOT);
        require(
            seen.add(canonical),
            subject
                + "의 "
                + List.of("고객용", "생산팀용", "관리팀용").get(audience)
                + " 표시명이 중복됩니다: "
                + text(value));
      }
    }
  }

  public static void fields(Control type, List<Field> fields, boolean allowEmpty) {
    require(type != null, "입력 방식을 선택해 주세요.");
    require(list(fields).size() <= 20, "한 그룹의 입력 필드는 최대 20개입니다.");
    if (choice(type)) {
      require(list(fields).isEmpty(), "선택형과 입력형을 같은 그룹에 섞을 수 없습니다.");
      return;
    }
    require(allowEmpty || !list(fields).isEmpty(), "입력 필드를 한 개 이상 등록해 주세요.");
    uniqueLabels(list(fields).stream().map(f -> f == null ? null : f.labels()).toList(), "입력 필드");
    Set<String> ids = new HashSet<>();
    for (Field f : list(fields)) {
      require(f != null, "빈 입력 필드는 허용하지 않습니다.");
      require(
          f.key() != null && f.key().matches("[A-Za-z0-9_-]{1,80}") && ids.add(f.key()),
          "입력 필드 value가 중복되거나 올바르지 않습니다.");
      labels(f.labels(), 120);
      require(
          text(f.namePart()).length() <= 160 && text(f.unit()).length() <= 20,
          "필드 이름 구성 문자 또는 단위가 너무 깁니다.");
      if (type == Control.NUMBER) {
        for (BigDecimal n : Arrays.asList(f.min(), f.max(), f.step()))
          require(
              n == null || (n.abs().compareTo(new BigDecimal("1000000000")) <= 0 && n.scale() <= 3),
              "숫자 조건은 ±10억 이하, 소수 셋째 자리까지 가능합니다.");
        require(
            f.min() == null || f.max() == null || f.min().compareTo(f.max()) <= 0,
            "최소값은 최대값보다 클 수 없습니다.");
        require(f.step() == null || f.step().signum() > 0, "입력 간격은 0보다 커야 합니다.");
        require(
            f.allowNegative() || (f.min() == null || f.min().signum() >= 0),
            "음수 불가 필드의 최소값은 음수일 수 없습니다.");
        require(
            f.allowNegative() || (f.max() == null || f.max().signum() >= 0),
            "음수 불가 필드의 최대값은 음수일 수 없습니다.");
      }
      if (type == Control.TEXT || type == Control.TEXTAREA) {
        int lo = f.minLength() == null ? 0 : f.minLength(),
            hi = f.maxLength() == null ? 500 : f.maxLength();
        require(
            lo >= 0 && hi >= 1 && hi <= 10000 && lo <= hi, "문자 길이는 최소 0~최대 10,000자 범위로 설정해 주세요.");
        require(f.format() == null || FORMATS.contains(f.format()), "지원하지 않는 문자 검증 형식입니다.");
      }
      if (type == Control.FILE) {
        require(
            f.maxFiles() != null && f.maxFiles() >= 1 && f.maxFiles() <= 20,
            "파일 수는 1~20개로 설정해 주세요.");
        require(
            f.minFiles() == null || (f.minFiles() >= 0 && f.minFiles() <= f.maxFiles()),
            "최소 파일 수가 잘못되었습니다.");
        require(
            f.maxFileMB() != null && f.maxFileMB() >= 1 && f.maxFileMB() <= 20,
            "파일당 용량은 1~20MB입니다.");
        require(!list(f.extensions()).isEmpty(), "허용 확장자를 하나 이상 선택해 주세요.");
        require(
            Set.of(
                    "jpg", "jpeg", "png", "gif", "webp", "pdf", "txt", "csv", "xlsx", "xls", "docx",
                    "doc", "pptx", "ppt", "zip", "hwp", "hwpx")
                .containsAll(f.extensions()),
            "지원하지 않는 파일 확장자입니다.");
      }
    }
  }

  public static List<Issue> structure(Process process) {
    List<Issue> issues = new ArrayList<>();
    if (process == null
        || process.schemaVersion() != 2
        || list(process.questions()).isEmpty()
        || list(process.questions()).size() > MAX_QUESTIONS
        || list(process.rules()).size() > MAX_RULES) {
      issue(issues, "ERROR", "SCHEMA", "프로세스", "질문 1~40개, 규칙 200개 이하의 버전 2 프로세스가 필요합니다.");
      return issues;
    }
    Map<String, Question> byKey = new LinkedHashMap<>();
    Map<String, Integer> order = new HashMap<>();
    for (Question q : process.questions()) {
      if (q == null) {
        issue(issues, "ERROR", "QUESTION", "프로세스", "빈 질문이 있습니다.");
        continue;
      }
      if (q.key() == null
          || !q.key().matches("[A-Za-z0-9_-]{1,80}")
          || byKey.put(q.key(), q) != null)
        issue(issues, "ERROR", "KEY", text(q.key()), "질문 value가 중복되거나 올바르지 않습니다.");
      order.put(q.key(), order.size());
      try {
        labels(q.labels(), 80);
        fields(q.control(), q.fields(), q.fixed());
        require(
            text(q.question()).length() <= 300 && text(q.guide()).length() <= 1000,
            "질문/도움말 길이를 확인해 주세요.");
        if (choice(q.control())) {
          require(!list(q.choices()).isEmpty() && q.choices().size() <= 200, "보기는 1~200개여야 합니다.");
          uniqueLabels(q.choices().stream().map(c -> c == null ? null : c.labels()).toList(), "보기");
          Set<String> keys = new HashSet<>();
          for (Choice c : q.choices()) {
            require(
                c != null
                    && c.key() != null
                    && c.key().matches("[A-Za-z0-9_-]{1,80}")
                    && keys.add(c.key()),
                "보기 value가 중복되거나 올바르지 않습니다.");
            labels(c.labels(), q.fixed() ? 200 : 120);
            require(text(c.namePart()).length() <= 160, "제품명 구성 문자는 160자 이하입니다.");
          }
        } else require(list(q.choices()).isEmpty(), "입력형에는 선택 보기를 등록할 수 없습니다.");
      } catch (IllegalArgumentException e) {
        issue(issues, "ERROR", "QUESTION", label(q), e.getMessage());
      }
    }
    Set<String> ruleKeys = new HashSet<>();
    for (Rule r : list(process.rules())) {
      if (r == null) {
        issue(issues, "ERROR", "RULE", "프로세스", "빈 규칙이 있습니다.");
        continue;
      }
      String at = text(r.name());
      try {
        require(
            r.key() != null && r.key().matches("[A-Za-z0-9_-]{1,80}") && ruleKeys.add(r.key()),
            "규칙 value가 중복되거나 올바르지 않습니다.");
        require(!at.isBlank() && at.length() <= 120, "규칙명을 1~120자로 입력해 주세요.");
        require("ALL".equals(r.match()) || "ANY".equals(r.match()), "조건 결합은 ALL 또는 ANY입니다.");
        require(
            !list(r.conditions()).isEmpty()
                && r.conditions().size() <= 20
                && !list(r.actions()).isEmpty()
                && r.actions().size() <= 40,
            "규칙에 조건(최대 20개)과 결과(최대 40개)가 필요합니다.");
        int lastSource = -1;
        for (Condition c : r.conditions()) {
          require(
              c != null && c.operator() != null && byKey.containsKey(c.groupKey()),
              "조건의 원본 질문이 없습니다.");
          Question q = byKey.get(c.groupKey());
          lastSource = Math.max(lastSource, order.get(c.groupKey()));
          if (choice(q.control())) {
            require(c.fieldKey() == null || c.fieldKey().isBlank(), "선택형 조건에 입력 필드를 지정할 수 없습니다.");
            require(
                Set.of(Operator.EQ, Operator.NE, Operator.PRESENT, Operator.ABSENT)
                    .contains(c.operator()),
                "선택형 조건의 연산자가 잘못되었습니다.");
            if (c.operator() == Operator.EQ || c.operator() == Operator.NE)
              require(
                  !list(c.choiceKeys()).isEmpty()
                      && q.choices().stream()
                          .map(Choice::key)
                          .collect(Collectors.toSet())
                          .containsAll(c.choiceKeys()),
                  "조건에서 선택한 보기가 없습니다.");
          } else {
            Field f =
                list(q.fields()).stream()
                    .filter(x -> x.key().equals(c.fieldKey()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("조건의 입력 필드가 없습니다."));
            if (q.control() == Control.NUMBER
                && c.operator() != Operator.PRESENT
                && c.operator() != Operator.ABSENT) {
              require(c.lower() != null, "숫자 비교값을 입력해 주세요.");
              require(
                  c.operator() != Operator.RANGE
                      || (c.upper() != null && c.lower().compareTo(c.upper()) <= 0),
                  "숫자 구간의 시작과 끝을 확인해 주세요.");
              if (c.operator() == Operator.RANGE && c.lower().compareTo(c.upper()) == 0)
                require(c.lowerInclusive() && c.upperInclusive(), "시작과 끝이 같은 구간은 양쪽을 포함해야 합니다.");
            } else if (q.control() == Control.FILE)
              require(
                  c.operator() == Operator.PRESENT || c.operator() == Operator.ABSENT,
                  "파일 조건은 있음/없음만 가능합니다.");
            else if (q.control() != Control.NUMBER)
              require(
                  Set.of(Operator.EQ, Operator.NE, Operator.PRESENT, Operator.ABSENT)
                      .contains(c.operator()),
                  "문자 조건의 연산자가 잘못되었습니다.");
            require(f != null, "입력 필드를 확인해 주세요.");
          }
        }
        for (Action a : r.actions()) {
          require(
              a != null && a.effect() != null && byKey.containsKey(a.targetKey()),
              "결과 질문을 선택해 주세요.");
          Question target = byKey.get(a.targetKey());
          require(
              order.get(a.targetKey()) > lastSource,
              "조건은 원본보다 뒤에 있는 질문에만 연결할 수 있습니다. 질문 순서를 확인해 주세요.");
          require(!target.fixed(), "제품의 고정 사양은 조건으로 변경할 수 없습니다.");
          if (a.effect() == Effect.ALLOW)
            require(
                choice(target.control())
                    && !list(a.choiceKeys()).isEmpty()
                    && target.choices().stream()
                        .map(Choice::key)
                        .collect(Collectors.toSet())
                        .containsAll(a.choiceKeys()),
                "허용할 보기를 한 개 이상 선택해 주세요. 질문 생략은 '건너뜀'을 사용합니다.");
        }
      } catch (IllegalArgumentException e) {
        issue(issues, "ERROR", "RULE", at, e.getMessage());
      }
    }
    return issues;
  }

  public static Evaluation evaluate(Process process, Map<String, Answer> submitted) {
    List<Issue> schema = structure(process);
    if (!schema.isEmpty())
      throw new IllegalArgumentException(
          schema.stream().map(Issue::message).distinct().collect(Collectors.joining(" / ")));
    return evaluateChecked(process, submitted);
  }

  private static Evaluation evaluateChecked(Process process, Map<String, Answer> submitted) {
    Map<String, Answer> normalized = new LinkedHashMap<>();
    List<QuestionState> states = new ArrayList<>();
    List<String> allErrors = new ArrayList<>();
    Set<String> visited = new HashSet<>();
    for (Question q : process.questions()) {
      boolean visible = q.visible(), required = q.required();
      LinkedHashSet<String> allowed =
          list(q.choices()).stream()
              .map(Choice::key)
              .collect(Collectors.toCollection(LinkedHashSet::new));
      List<Rule> matched =
          list(process.rules()).stream()
              .filter(r -> matches(r, normalized, visited))
              .filter(r -> r.actions().stream().anyMatch(a -> a.targetKey().equals(q.key())))
              .toList();
      Set<Effect> effects = new HashSet<>();
      for (Rule r : matched)
        for (Action a : r.actions())
          if (a.targetKey().equals(q.key())) {
            effects.add(a.effect());
            switch (a.effect()) {
              case SHOW -> visible = true;
              case HIDE -> visible = false;
              case REQUIRE -> required = true;
              case OPTIONAL -> required = false;
              case ALLOW -> allowed.retainAll(list(a.choiceKeys()));
            }
          }
      List<String> errors = new ArrayList<>();
      if (effects.contains(Effect.SHOW) && effects.contains(Effect.HIDE))
        errors.add("표시/건너뜀 규칙이 동시에 일치합니다.");
      if (effects.contains(Effect.REQUIRE) && effects.contains(Effect.OPTIONAL))
        errors.add("필수/선택 규칙이 동시에 일치합니다.");
      Answer clean = empty();
      if (q.requireRule() && matched.isEmpty())
        errors.add("일치하는 경우의 수가 없습니다. 조건 범위 또는 기본 동작을 확인해 주세요.");
      if (visible) {
        if (choice(q.control()) && allowed.isEmpty()) errors.add("선택 가능한 보기가 없습니다.");
        Answer raw = map(submitted).getOrDefault(q.key(), empty());
        if (choice(q.control())) {
          List<String> selected =
              list(raw.choices()).stream().filter(allowed::contains).distinct().toList();
          if (q.control() == Control.RADIO && selected.size() > 1) {
            errors.add("하나만 선택해 주세요.");
            selected = List.of();
          }
          clean = new Answer(selected, Map.of());
          if (required && selected.isEmpty()) errors.add("보기를 선택해 주세요.");
        } else {
          Map<String, Object> fields = new LinkedHashMap<>();
          for (Field f : list(q.fields())) {
            Object value = map(raw.fields()).get(f.key());
            boolean absent = absent(value);
            if (absent) {
              if (required && f.required()) errors.add(f.labels().customer() + ": 입력해 주세요.");
              continue;
            }
            String error = valueError(q.control(), f, value);
            if (error != null) errors.add(f.labels().customer() + ": " + error);
            else fields.put(f.key(), q.control() == Control.NUMBER ? number(value) : value);
          }
          clean = new Answer(List.of(), fields);
        }
        if (errors.isEmpty()) normalized.put(q.key(), clean);
      }
      // A hidden or invalid source cannot drive downstream conditions, including NE/ABSENT.
      if (visible && errors.isEmpty()) visited.add(q.key());
      for (String e : errors) allErrors.add(label(q) + ": " + e);
      states.add(
          new QuestionState(
              q,
              visible,
              required,
              List.copyOf(allowed),
              clean,
              List.copyOf(errors),
              matched.stream().map(Rule::key).toList()));
    }
    return new Evaluation(
        List.copyOf(states), normalized, List.copyOf(allErrors), allErrors.isEmpty());
  }

  private static boolean matches(Rule r, Map<String, Answer> answers, Set<String> visited) {
    // Hidden/invalid sources never match, including NE and ABSENT. A valid OR branch is sufficient.
    if ("ANY".equals(r.match()))
      return r.conditions().stream()
          .filter(c -> visited.contains(c.groupKey()))
          .anyMatch(c -> matches(c, answers.get(c.groupKey())));
    if (r.conditions().stream().anyMatch(c -> !visited.contains(c.groupKey()))) return false;
    return r.conditions().stream().allMatch(c -> matches(c, answers.get(c.groupKey())));
  }

  private static boolean matches(Condition c, Answer answer) {
    if (answer == null) return false;
    Object value =
        text(c.fieldKey()).isBlank()
            ? list(answer.choices())
            : map(answer.fields()).get(c.fieldKey());
    if (c.operator() == Operator.PRESENT) return !absent(value);
    if (c.operator() == Operator.ABSENT) return absent(value);
    if (absent(value)) return false;
    if (text(c.fieldKey()).isBlank()) {
      boolean equal = list(answer.choices()).stream().anyMatch(list(c.choiceKeys())::contains);
      return c.operator() == Operator.EQ ? equal : !equal;
    }
    if (c.lower() != null) {
      BigDecimal n;
      try {
        n = number(value);
      } catch (RuntimeException e) {
        return false;
      }
      int cmp = n.compareTo(c.lower());
      return switch (c.operator()) {
        case EQ -> cmp == 0;
        case NE -> cmp != 0;
        case GT -> cmp > 0;
        case GE -> cmp >= 0;
        case LT -> cmp < 0;
        case LE -> cmp <= 0;
        case RANGE ->
            (c.lowerInclusive() ? cmp >= 0 : cmp > 0)
                && (c.upperInclusive() ? n.compareTo(c.upper()) <= 0 : n.compareTo(c.upper()) < 0);
        default -> false;
      };
    }
    boolean equal = String.valueOf(value).equals(text(c.text()));
    return c.operator() == Operator.EQ ? equal : !equal;
  }

  private static boolean absent(Object value) {
    return value == null
        || value instanceof String s && s.isBlank()
        || value instanceof Collection<?> c && c.isEmpty();
  }

  public static BigDecimal number(Object value) {
    if (!(value instanceof Number) && !(value instanceof String))
      throw new IllegalArgumentException("숫자가 아닙니다.");
    String s = String.valueOf(value);
    if (s.length() > 40) throw new IllegalArgumentException("숫자 길이가 너무 깁니다.");
    return new BigDecimal(s);
  }

  public static String valueError(Control control, Field f, Object value) {
    try {
      if (control == Control.NUMBER) {
        BigDecimal n = number(value), step = f.step() == null ? BigDecimal.ONE : f.step();
        if (n.scale() > 3 || n.abs().compareTo(new BigDecimal("1000000000")) > 0)
          return "±10억 이하, 소수 셋째 자리까지 입력해 주세요.";
        if (!f.allowNegative() && n.signum() < 0) return "음수를 입력할 수 없습니다.";
        if (f.min() != null && n.compareTo(f.min()) < 0) return "최소 " + f.min() + " 이상이어야 합니다.";
        if (f.max() != null && n.compareTo(f.max()) > 0) return "최대 " + f.max() + " 이하여야 합니다.";
        if (n.subtract(f.min() == null ? BigDecimal.ZERO : f.min()).remainder(step).signum() != 0)
          return step + " 간격으로 입력해 주세요.";
      } else if (control == Control.FILE) {
        if (!(value instanceof List<?> files)) return "파일 목록이 올바르지 않습니다.";
        if (files.size() < (f.minFiles() == null ? 0 : f.minFiles()) || files.size() > f.maxFiles())
          return "파일 개수를 확인해 주세요.";
      } else {
        if (!(value instanceof String s)) return "문자 형식으로 입력해 주세요.";
        int len = s.codePointCount(0, s.length());
        if (len < (f.minLength() == null ? 0 : f.minLength())
            || len > (f.maxLength() == null ? 500 : f.maxLength())) return "허용 문자 길이를 확인해 주세요.";
        String format = f.format() == null ? "ANY" : f.format();
        if ("EMAIL".equals(format) && !s.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
          return "이메일 형식으로 입력해 주세요.";
        if ("PHONE".equals(format) && !s.matches("[+0-9() -]{7,30}")) return "전화번호 형식으로 입력해 주세요.";
        if ("ALPHANUMERIC".equals(format) && !s.matches("[A-Za-z0-9_-]+"))
          return "영문·숫자·밑줄·하이픈만 가능합니다.";
      }
      return null;
    } catch (RuntimeException e) {
      return "입력값 형식을 확인해 주세요.";
    }
  }

  public static Validation validate(Process process, Map<String, Answer> fixed) {
    List<Issue> issues = structure(process);
    if (!issues.isEmpty()) return new Validation(false, false, 0, issues);
    Probe probe = new Probe(process, fixed, issues);
    probe.walk(0, new LinkedHashMap<>(map(fixed)));
    if (!probe.exhaustive)
      issue(
          issues,
          "ERROR",
          "LIMIT",
          "검증 범위",
          "검증 한도 20,000개 상태를 초과했습니다. 조건 또는 질문을 나눠 다시 검증해 주세요. 미검증 상태로 등록완료할 수 없습니다.");
    for (Rule r : list(process.rules()))
      if (!probe.hitRules.contains(r.key()))
        issue(issues, "WARNING", "UNREACHABLE_RULE", r.name(), "허용된 입력 범위에서 이 규칙에 도달하지 못했습니다.");
    for (Question q : process.questions())
      if (!q.fixed() && !probe.visibleQuestions.contains(q.key()))
        issue(issues, "WARNING", "UNREACHABLE_QUESTION", label(q), "이 질문이 표시되는 경로를 찾지 못했습니다.");
    return new Validation(
        issues.stream().noneMatch(i -> i.severity().equals("ERROR")),
        probe.exhaustive,
        probe.leaves,
        List.copyOf(issues));
  }

  private static final class Probe {
    final Process process;
    final Map<String, Answer> fixed;
    final List<Issue> issues;
    final Set<String> hitRules = new HashSet<>(), visibleQuestions = new HashSet<>();
    long visits, leaves;
    boolean exhaustive = true;

    Probe(Process p, Map<String, Answer> f, List<Issue> i) {
      process = p;
      fixed = map(f);
      issues = i;
    }

    void walk(int index, Map<String, Answer> answers) {
      if (++visits > MAX_SCENARIOS) {
        exhaustive = false;
        return;
      }
      if (index == process.questions().size()) {
        leaves++;
        return;
      }
      Evaluation evaluation = evaluateChecked(process, answers);
      QuestionState state = evaluation.questions().get(index);
      Question q = state.question();
      hitRules.addAll(state.matchedRules());
      // Input-required errors are expected until this question receives its sample answer.
      for (String e : state.errors())
        if (e.contains("동시에") || e.contains("경우의 수") || e.contains("보기가 없습니다"))
          issue(issues, "ERROR", "PATH", label(q), e + " 예시: " + example(process, answers));
      if (!state.visible()) {
        walk(index + 1, answers);
        return;
      }
      visibleQuestions.add(q.key());
      if (q.fixed()) {
        if (!state.errors().isEmpty()) issue(issues, "ERROR", "FIXED", label(q), "고정 사양을 확인해 주세요.");
        walk(index + 1, answers);
        return;
      }
      List<Answer> samples = samples(q, state.required(), state.allowed(), process);
      if (samples.size() > MAX_SCENARIOS) {
        exhaustive = false;
        return;
      }
      if (samples.isEmpty())
        issue(issues, "ERROR", "EMPTY_DOMAIN", label(q), "검증 조건을 만족하는 입력값이 없습니다.");
      for (Answer a : samples) {
        Map<String, Answer> next = new LinkedHashMap<>(answers);
        next.put(q.key(), a);
        QuestionState tested = evaluateChecked(process, next).questions().get(index);
        if (tested.errors().isEmpty()) walk(index + 1, next);
        if (!exhaustive) return;
      }
    }
  }

  private static String example(Process process, Map<String, Answer> answers) {
    List<String> parts = new ArrayList<>();
    for (Question q : process.questions())
      if (answers.containsKey(q.key())) {
        Answer a = answers.get(q.key());
        String value;
        if (choice(q.control()))
          value =
              list(q.choices()).stream()
                  .filter(c -> list(a.choices()).contains(c.key()))
                  .map(c -> c.labels().management())
                  .collect(Collectors.joining("+"));
        else
          value =
              list(q.fields()).stream()
                  .filter(f -> map(a.fields()).containsKey(f.key()))
                  .map(f -> f.labels().management() + "=" + map(a.fields()).get(f.key()))
                  .collect(Collectors.joining(","));
        parts.add(label(q) + ": " + value);
      }
    String s = String.join(" / ", parts);
    return s.length() > 400 ? s.substring(0, 400) + "…" : s;
  }

  private static List<Answer> samples(
      Question q, boolean required, List<String> allowed, Process p) {
    List<Answer> result = new ArrayList<>();
    if (!required) result.add(empty());
    if (choice(q.control())) {
      List<Condition> predicates =
          list(p.rules()).stream()
              .flatMap(r -> r.conditions().stream())
              .filter(c -> c.groupKey().equals(q.key()))
              .toList();
      // Only predicate truth vectors can affect later questions. Equivalent values are checked
      // once.
      Map<String, Answer> projected = new LinkedHashMap<>();
      if (!required) projected.put(signature(predicates, empty()), empty());
      if (q.control() == Control.RADIO) {
        for (String key : allowed) {
          Answer a = new Answer(List.of(key), Map.of());
          projected.putIfAbsent(signature(predicates, a), a);
        }
      } else {
        Map<String, List<String>> optionClasses = new LinkedHashMap<>();
        for (String key : allowed) {
          Answer a = new Answer(List.of(key), Map.of());
          optionClasses.putIfAbsent(signature(predicates, a), List.of(key));
        }
        Map<String, Answer> combinations = new LinkedHashMap<>();
        combinations.put(signature(predicates, empty()), empty());
        for (List<String> group : optionClasses.values()) {
          List<Answer> previous = new ArrayList<>(combinations.values());
          for (Answer previousAnswer : previous) {
            List<String> combined = new ArrayList<>(previousAnswer.choices());
            combined.addAll(group);
            Answer a = new Answer(combined, Map.of());
            combinations.putIfAbsent(signature(predicates, a), a);
            // Keep a non-empty representative even if there are no conditions.
            projected.putIfAbsent(signature(predicates, a), a);
          }
          if (combinations.size() > MAX_SCENARIOS)
            return Collections.nCopies(MAX_SCENARIOS + 1, empty());
        }
      }
      result.clear();
      result.addAll(projected.values());
      return result;
    }
    List<Map<String, Object>> rows = new ArrayList<>();
    rows.add(new LinkedHashMap<>());
    for (Field f : list(q.fields())) {
      List<Object> domain = domain(q, f, p);
      if (!required || !f.required()) domain.add(null);
      List<Condition> predicates =
          list(p.rules()).stream()
              .flatMap(r -> r.conditions().stream())
              .filter(c -> q.key().equals(c.groupKey()) && f.key().equals(c.fieldKey()))
              .toList();
      Map<String, Object> projected = new LinkedHashMap<>();
      for (Object value : domain) {
        Answer a = value == null ? empty() : new Answer(List.of(), Map.of(f.key(), value));
        String signature = signature(predicates, a);
        if (!projected.containsKey(signature)) projected.put(signature, value);
      }
      domain = new ArrayList<>(projected.values());
      if ((long) rows.size() * domain.size() > MAX_SCENARIOS)
        return Collections.nCopies(MAX_SCENARIOS + 1, empty());
      List<Map<String, Object>> expanded = new ArrayList<>();
      for (Map<String, Object> row : rows)
        for (Object v : domain) {
          Map<String, Object> copy = new LinkedHashMap<>(row);
          if (v != null) copy.put(f.key(), v);
          expanded.add(copy);
        }
      rows = expanded;
    }
    for (Map<String, Object> row : rows) result.add(new Answer(List.of(), row));
    return result;
  }

  private static String signature(List<Condition> predicates, Answer answer) {
    StringBuilder signature = new StringBuilder();
    for (Condition c : predicates) signature.append(matches(c, answer) ? '1' : '0');
    return signature.toString();
  }

  private static List<Object> domain(Question q, Field f, Process p) {
    List<Condition> conditions =
        list(p.rules()).stream()
            .flatMap(r -> r.conditions().stream())
            .filter(c -> q.key().equals(c.groupKey()) && f.key().equals(c.fieldKey()))
            .toList();
    Set<Object> values = new LinkedHashSet<>();
    if (q.control() == Control.NUMBER) {
      BigDecimal lo =
          f.min() == null
              ? (f.allowNegative() ? new BigDecimal("-1000000000") : BigDecimal.ZERO)
              : f.min();
      BigDecimal hi = f.max() == null ? new BigDecimal("1000000000") : f.max();
      BigDecimal step = f.step() == null ? BigDecimal.ONE : f.step(),
          anchor = f.min() == null ? BigDecimal.ZERO : f.min();
      List<BigDecimal> edges = new ArrayList<>(List.of(lo, hi));
      for (Condition c : conditions) {
        if (c.lower() != null) edges.add(c.lower());
        if (c.upper() != null) edges.add(c.upper());
      }
      // Representatives adjacent to every predicate boundary cover each equivalence interval on the
      // input grid.
      for (BigDecimal edge : edges) {
        BigDecimal floor =
            edge.subtract(anchor).divide(step, 0, RoundingMode.FLOOR).multiply(step).add(anchor);
        for (BigDecimal n : List.of(floor.subtract(step), floor, floor.add(step)))
          if (valueError(Control.NUMBER, f, n) == null) values.add(n);
      }
    } else if (q.control() == Control.FILE) {
      int count = Math.max(f.required() ? 1 : 0, f.minFiles() == null ? 0 : f.minFiles());
      values.add(Collections.nCopies(Math.max(count, 1), "validation-file"));
    } else {
      for (Condition c : conditions) if (c.text() != null) values.add(c.text());
      String format = f.format() == null ? "ANY" : f.format();
      int min = f.minLength() == null ? 0 : f.minLength(),
          max = f.maxLength() == null ? 500 : f.maxLength();
      Set<String> constants =
          conditions.stream()
              .map(Condition::text)
              .filter(Objects::nonNull)
              .collect(Collectors.toSet());
      int length = Math.max(1, min);
      if (format.equals("EMAIL")) length = Math.max(5, min);
      if (format.equals("PHONE")) length = Math.max(7, min);
      if (length <= max && (!format.equals("PHONE") || length <= 30)) {
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_-";
        long capacity = 1;
        for (int i = 0; i < length && capacity <= constants.size() + 1; i++) capacity *= 64;
        int attempts =
            format.equals("ALPHANUMERIC")
                ? (int) Math.min(capacity, constants.size() + 1)
                : constants.size() + 1;
        for (int index = 0; index < attempts; index++) {
          String candidate;
          if (format.equals("ALPHANUMERIC")) {
            char[] chars = new char[length];
            Arrays.fill(chars, 'A');
            int n = index;
            for (int i = length - 1; i >= 0 && n > 0; i--) {
              chars[i] = alphabet.charAt(n % 64);
              n /= 64;
            }
            candidate = new String(chars);
          } else if (format.equals("EMAIL"))
            candidate = String.valueOf((char) (0x4e00 + index)) + "a".repeat(length - 5) + "@b.c";
          else if (format.equals("PHONE"))
            candidate = String.format(Locale.ROOT, "%0" + length + "d", index);
          else candidate = String.valueOf((char) (0x4e00 + index)).repeat(length);
          if (valueError(q.control(), f, candidate) == null && !constants.contains(candidate)) {
            values.add(candidate);
            break;
          }
        }
      }
      values.removeIf(v -> valueError(q.control(), f, v) != null);
    }
    return new ArrayList<>(values);
  }
}
