package com.dev.HiddenBATHAuto.service.productmaster;

import static com.dev.HiddenBATHAuto.service.productmaster.ProductStudioEngine.*;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductStudioDtos.*;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductStudioDtos.Process;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.function.Function;

/** Bounded arithmetic interpreter. Never executes user text or trusts client-derived values. */
public final class ProductStudioArithmetic {
  private ProductStudioArithmetic() {}
  public static final String VALUE = "__numeric_value";
  private static final BigDecimal MAX = new BigDecimal("1000000000");
  public static boolean numeric(Question q) {
    return q.control() == Control.RADIO && !list(q.choices()).isEmpty()
        && q.choices().stream().allMatch(c -> c.numericValue() != null);
  }
  public static Field valueField() {
    return new Field(VALUE, new Labels("선택값의 계산용 숫자", "계산용 숫자", "계산용 숫자"), null,
        true, false, BigDecimal.ZERO, MAX, new BigDecimal("0.001"), null, null, null, null,
        List.of(), null, null, null, null, VALUE, List.of());
  }
  public static Field valueField(Process process, Question question) {
    try {
      BigDecimal min=question.choices().stream().map(c -> range(process,c.numericValue().expression()).min()).min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
      BigDecimal max=question.choices().stream().map(c -> range(process,c.numericValue().expression()).max()).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
      return ProductStudioConstraints.bound(valueField(),min.max(BigDecimal.ZERO),max.compareTo(MAX)>0?null:max);
    } catch (IllegalArgumentException uncertain) {
      return ProductStudioConstraints.bound(valueField(),BigDecimal.ZERO,null);
    }
  }
  public static List<ArithmeticToken> references(List<ArithmeticToken> tokens) {
    return list(tokens).stream().filter(t -> t != null && "FIELD".equals(t.kind())).toList();
  }
  public static void validate(Process p, String target, List<ArithmeticToken> expression) {
    Map<String, Question> byKey = new LinkedHashMap<>();
    for (Question q : p.questions()) {
      if (q.key().equals(target)) break;
      byKey.put(q.key(), q);
    }
    parse(expression, t -> {
      Question source = byKey.get(t.groupKey());
      if (source == null || source.control() != Control.NUMBER
          || list(source.fields()).stream().noneMatch(f -> f.key().equals(t.fieldKey())))
        throw new IllegalArgumentException("계산식은 이 질문보다 앞에 있는 숫자 질문의 입력 필드만 참조할 수 있습니다. 삭제·순서·필드를 확인해 주세요.");
      return BigDecimal.ONE;
    }, true);
  }
  public static BigDecimal evaluate(List<ArithmeticToken> expression, Map<String, Answer> answers) {
    return parse(expression, t -> {
      Object value = map(answers.getOrDefault(t.groupKey(), empty()).fields()).get(t.fieldKey());
      if (value == null) throw new IllegalArgumentException("계산식이 참조하는 앞 숫자 입력이 없거나 현재 분기에서 숨겨졌습니다 (" + t.groupKey() + " / " + t.fieldKey() + ").");
      return number(value);
    }, false);
  }
  private static BigDecimal parse(List<ArithmeticToken> expression, Function<ArithmeticToken, BigDecimal> read, boolean syntaxOnly) {
    if (expression == null || expression.isEmpty() || expression.size() > 100)
      throw new IllegalArgumentException("계산식에 숫자 또는 앞 입력값을 넣어 주세요. 최대 100개 요소입니다.");
    Parser parser = new Parser(expression, read, syntaxOnly);
    BigDecimal result = parser.sum();
    if (parser.index != expression.size()) throw new IllegalArgumentException("계산식의 연산자·괄호 순서를 확인해 주세요.");
    if (!syntaxOnly && result.abs().compareTo(MAX) > 0) throw new IllegalArgumentException("계산 결과는 ±10억 이하여야 합니다.");
    return result.stripTrailingZeros();
  }
  private static final class Parser {
    final List<ArithmeticToken> tokens; final Function<ArithmeticToken, BigDecimal> read; final boolean syntaxOnly;
    int index;
    Parser(List<ArithmeticToken> tokens, Function<ArithmeticToken, BigDecimal> read, boolean syntaxOnly) {
      this.tokens = tokens; this.read = read; this.syntaxOnly = syntaxOnly;
    }
    boolean take(String kind) {
      if (index < tokens.size() && tokens.get(index) != null && kind.equals(tokens.get(index).kind())) { index++; return true; }
      return false;
    }
    BigDecimal sum() {
      BigDecimal n = product();
      while (true) { if (take("ADD")) n = n.add(product()); else if (take("SUB")) n = n.subtract(product()); else return n; }
    }
    BigDecimal product() {
      BigDecimal n = atom();
      while (true) {
        if (take("MUL")) n = n.multiply(atom());
        else if (take("DIV")) {
          BigDecimal d = atom();
          if (!syntaxOnly && d.signum() == 0) throw new IllegalArgumentException("계산식에서 0으로 나눌 수 없습니다. 앞 입력 범위와 식을 확인해 주세요.");
          n = syntaxOnly ? BigDecimal.ONE : n.divide(d, 12, RoundingMode.HALF_UP);
        } else return n;
        if (n.precision() > 120) throw new IllegalArgumentException("계산식의 중간 결과가 너무 큽니다.");
      }
    }
    BigDecimal atom() {
      if (take("SUB")) return atom().negate();
      if (take("ADD")) return atom();
      if (take("LPAREN")) {
        BigDecimal n = sum();
        if (!take("RPAREN")) throw new IllegalArgumentException("계산식의 닫는 괄호가 없습니다.");
        return n;
      }
      if (index >= tokens.size() || tokens.get(index) == null) throw new IllegalArgumentException("연산자 뒤에 숫자 또는 앞 입력값을 넣어 주세요.");
      ArithmeticToken t = tokens.get(index++);
      if ("FIELD".equals(t.kind())) return read.apply(t);
      if ("NUMBER".equals(t.kind()) && t.value() != null && t.value().abs().compareTo(MAX) <= 0 && t.value().scale() <= 6) return t.value();
      throw new IllegalArgumentException("계산식은 숫자·앞 입력값·사칙연산·괄호만 사용할 수 있습니다.");
    }
  }
  /** Local editor validation cannot resolve upstream questions; final process validation must. */
  public static List<Issue> localStructure(Question q) {
    List<Issue> issues=new ArrayList<>();
    if(list(q.choices()).stream().noneMatch(c -> c.numericValue()!=null))return issues;
    try {
      if(!numeric(q))throw new IllegalArgumentException("계산용 숫자값은 단일선택의 모든 옵션에 설정해야 합니다. 다중선택에는 사용할 수 없습니다.");
      for(Choice c:q.choices()) {
        parse(c.numericValue().expression(),t -> BigDecimal.ONE,true);
        if(references(c.numericValue().expression()).isEmpty() && evaluate(c.numericValue().expression(),Map.of()).signum()<0)
          throw new IllegalArgumentException("계산용 숫자값은 0 이상이어야 합니다.");
      }
    } catch(IllegalArgumentException ex) {issues.add(new Issue("ERROR","NUMERIC_CHOICE",label(q),ex.getMessage(),q.key()));}
    return issues;
  }
  public static List<Issue> structure(Process p) {
    List<Issue> issues = new ArrayList<>();
    if (text(p.confirmationMessage()).length() > 2000)
      issues.add(new Issue("ERROR", "CONFIRMATION", "마지막 확인창", "확인 문구는 2,000자 이하로 입력해 주세요."));
    for (Question q : p.questions()) {
      boolean any = list(q.choices()).stream().anyMatch(c -> c.numericValue() != null);
      if (!any) continue;
      try {
        if (!numeric(q)) throw new IllegalArgumentException("계산용 숫자값은 단일선택의 모든 옵션에 설정해야 합니다. 다중선택에는 사용할 수 없습니다.");
        for (Choice c : q.choices()) {
          validate(p, q.key(), c.numericValue().expression());
          if (references(c.numericValue().expression()).isEmpty()
              && evaluate(c.numericValue().expression(),Map.of()).signum()<0)
            throw new IllegalArgumentException(c.labels().customer()+": 계산용 숫자값은 0 이상이어야 합니다.");
        }
      } catch (IllegalArgumentException e) { issues.add(new Issue("ERROR", "NUMERIC_CHOICE", label(q), e.getMessage(), q.key())); }
    }
    return issues;
  }
  /** Unproven interval bounds are advisories, not proof that a valid formula is impossible. */
  public static List<Issue> advisories(Process p) {
    List<Issue> issues=new ArrayList<>();
    for(Question q:p.questions()) for(Choice c:list(q.choices())) if(c.numericValue()!=null) {
      String reason=null;
      try {Range r=range(p,c.numericValue().expression());
        if(r.min().signum()<0 || r.max().compareTo(MAX)>0)reason="독립적으로 계산한 보수적 범위 "+r.min()+" ~ "+r.max()+"가 계산 허용범위 0~10억을 벗어납니다.";
      }catch(IllegalArgumentException e){reason=e.getMessage();}
      if(reason!=null)issues.add(new Issue("WARNING","NUMERIC_EXPRESSION_RANGE_REVIEW",label(q),
          "선택 ‘"+c.labels().customer()+"’: "+reason+" 이것만으로 실제 입력이 잘못되었다고 단정하지 않습니다. "
          +"실제 허용할 입력·분기와 식을 확인하고 등록할 수 있습니다. 대표 입력 외의 모든 조합을 증명한 것은 아니며, 실행 시 값 누락·0 나눗셈·음수·한도 초과는 차단합니다.",q.key()));
    }
    return issues;
  }
  public record Range(BigDecimal min, BigDecimal max) {}
  public static Range range(Process process, List<ArithmeticToken> expression) {
    return new RangeParser(process, expression).read();
  }
  public static BigDecimal maximum(Process process, Question q) {
    try { return q.choices().stream().map(c -> range(process, c.numericValue().expression()).max()).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO).min(MAX).max(BigDecimal.ZERO); }
    catch (IllegalArgumentException uncertain) { return MAX; } // Runtime rejects larger results; never writes a field maximum.

  }
  private static final class RangeParser {
    final Process process; final List<ArithmeticToken> tokens; int index;
    RangeParser(Process p, List<ArithmeticToken> tokens) { process = p; this.tokens = tokens; }
    Range read() { Range r = sum(); if(index != tokens.size()) throw new IllegalArgumentException("계산식 순서를 확인해 주세요."); return r; }
    boolean take(String kind) { if(index < tokens.size() && kind.equals(tokens.get(index).kind())) {index++; return true;} return false; }
    Range sum() {
      Range a = product();
      while(true) {
        if(take("ADD")) {Range b = product(); a = new Range(a.min().add(b.min()), a.max().add(b.max()));}
        else if(take("SUB")) {Range b = product(); a = new Range(a.min().subtract(b.max()), a.max().subtract(b.min()));}
        else return a;
      }
    }
    Range product() {
      Range a = atom();
      while(true) {
        boolean divide;
        if(take("MUL")) divide = false; else if(take("DIV")) divide = true; else return a;
        Range b = atom();
        if(divide && b.min().signum() <= 0 && b.max().signum() >= 0)
          throw new IllegalArgumentException("나누는 값의 허용 범위에 0이 포함됩니다. 앞 숫자 질문의 최소·최대 또는 나눗셈 식을 수정해 주세요.");
        List<BigDecimal> values = new ArrayList<>();
        for(BigDecimal x : List.of(a.min(), a.max())) for(BigDecimal y : List.of(b.min(), b.max()))
          values.add(divide ? x.divide(y, 12, RoundingMode.HALF_UP) : x.multiply(y));
        a = new Range(Collections.min(values), Collections.max(values));
        if (a.min().precision() > 120 || a.max().precision() > 120) throw new IllegalArgumentException("계산 범위가 너무 큽니다.");
      }
    }
    Range atom() {
      if(take("ADD")) return atom();
      if(take("SUB")) {Range a = atom(); return new Range(a.max().negate(),a.min().negate());}
      if(take("LPAREN")) {Range a=sum(); if(!take("RPAREN")) throw new IllegalArgumentException("닫는 괄호가 필요합니다."); return a;}
      ArithmeticToken t = tokens.get(index++);
      if("NUMBER".equals(t.kind())) return new Range(t.value(),t.value());
      Question q = process.questions().stream().filter(x -> x.key().equals(t.groupKey())).findFirst().orElseThrow();
      Field f = q.fields().stream().filter(x -> x.key().equals(t.fieldKey())).findFirst().orElseThrow();
      if(q.fixed() && q.preset()!=null && map(q.preset().fields()).containsKey(f.key())) {
        BigDecimal n = number(q.preset().fields().get(f.key())); return new Range(n,n);
      }
      if (f.max() == null || (f.allowNegative() && f.min() == null))
        throw new IllegalArgumentException(label(q) + " · " + f.labels().customer()
            + ": 참조 필드의 실제 "
            + (f.max() == null ? "최대값" : "최소값")
            + "이 미설정이어서 범위를 확정할 수 없습니다. 미설정 값을 임의의 큰 숫자로 채우지 않습니다.");
      return new Range(f.min()==null ? BigDecimal.ZERO : f.min(), f.max());
    }
  }
  public static boolean references(Process process, String question, String field) {
    return process.questions().stream().flatMap(q -> list(q.choices()).stream())
        .filter(c -> c.numericValue()!=null).flatMap(c -> references(c.numericValue().expression()).stream())
        .anyMatch(t -> question.equals(t.groupKey()) && field.equals(t.fieldKey()))
        || list(process.inputLimits()).stream().flatMap(l -> references(l.equalTo()).stream())
        .anyMatch(t -> question.equals(t.groupKey()) && field.equals(t.fieldKey()));
  }

  /** Conservative interval proof covers interior expression values, not just sampled endpoints. */
  public static List<Issue> priceRanges(Process process, com.dev.HiddenBATHAuto.dto.productmaster.ProductPricingDtos.Definition definition) {
    List<Issue> result=new ArrayList<>();
    for(var term:definition.terms()) {
      Question owner=process.questions().stream().filter(q -> q.key().equals(term.groupKey())).findFirst().orElse(null);
      if(owner==null || !numeric(owner) || term.xAxis()==null || !VALUE.equals(term.xAxis().fieldKey()) || term.xAxis().usesEdgeOutside())continue;
      Process scoped=process;
      if("ALL".equals(term.match())) {
        List<Question> questions=new ArrayList<>();
        for(Question q:process.questions()) {
          List<Field> fields=new ArrayList<>();
          for(Field f:list(q.fields())) {
            BigDecimal lo=f.min(),hi=f.max(),step=f.step()==null?BigDecimal.ONE:f.step();
            for(Condition c:list(term.conditions())) if(q.key().equals(c.groupKey()) && f.key().equals(c.fieldKey()) && c.lower()!=null) {
              BigDecimal lower=null,upper=null;
              switch(c.operator()) {
                case EQ -> {lower=c.lower();upper=c.lower();}
                case GE -> lower=c.lower(); case GT -> lower=c.lower();
                case LE -> upper=c.lower(); case LT -> upper=c.lower();
                case RANGE -> {lower=c.lower();upper=c.upper();}
                default -> {}
              }
              if(lower!=null)lo=lo==null?lower:lo.max(lower);
              if(upper!=null)hi=hi==null?upper:hi.min(upper);
            }
            fields.add(ProductStudioConstraints.bound(f,lo,hi));
          }
          questions.add(ProductStudioConstraints.copy(q,fields));
        }
        scoped=new Process(process.schemaVersion(),questions,process.rules(),process.inputLimits(),process.notices());
      }
      for(Choice option:owner.choices()) {
        if(!list(term.choiceKeys()).isEmpty() && !term.choiceKeys().contains(option.key()))continue;
        if("ALL".equals(term.match()) && list(term.conditions()).stream().filter(c -> owner.key().equals(c.groupKey()) && text(c.fieldKey()).isBlank())
            .anyMatch(c -> c.operator()==Operator.EQ && !list(c.choiceKeys()).contains(option.key()) || c.operator()==Operator.NE && list(c.choiceKeys()).contains(option.key())))continue;
        Range range;
        try { range=range(scoped,option.numericValue().expression()); }
        catch(IllegalArgumentException uncertain) {
          result.add(new Issue("WARNING","PRICE_EXPRESSION_RANGE_REVIEW",term.name(),
              "선택 ‘"+option.labels().customer()+"’의 가격표 범위를 확정하지 못했습니다: "+uncertain.getMessage()
              +" 실제 허용 입력과 표 밖 처리를 확인하고 등록할 수 있습니다. 실행 시 표 밖 값을 임의 가격으로 처리하지 않습니다.","terms."+term.key()));
          continue;
        }
        try {
          if(!term.xAxis().zeroAt(range.min()))ProductPricingEngine.index(term.xAxis(),range.min());
          if(!term.xAxis().zeroAt(range.max()))ProductPricingEngine.index(term.xAxis(),range.max());
        }catch(IllegalArgumentException ex) {
          result.add(new Issue("WARNING","PRICE_EXPRESSION_RANGE_REVIEW",term.name(),"선택 ‘"+option.labels().customer()+"’의 계산식 범위 검증: 앞 숫자 질문의 최소·최대와 이 항의 조건으로 계산한 보수적 범위는 "
              +range.min().stripTrailingZeros().toPlainString()+" ~ "+range.max().stripTrailingZeros().toPlainString()
              +"입니다. 가격표가 이 보수적 범위를 덮지 못하지만 실제 가능한 값의 누락으로 단정하지 않습니다. 실제 입력 범위·적용 조건·표 기준을 확인하거나 ‘표 밖 입력 처리’를 지정해 주세요. 입력 범위나 가격표에 임의의 값을 추가하지 않습니다.","terms."+(term.branchScope()==null?term.key():term.branchScope().ownerKey())));
        }
      }
    }
    return result;
  }

  public static Process confirmation(Process p, String message) {
    if (text(message).length() > 2000) throw new IllegalArgumentException("확인 문구는 2,000자 이하로 입력해 주세요.");
    return new Process(p.schemaVersion(), p.questions(), p.rules(), p.inputLimits(), p.notices(), text(message).strip());
  }
}
