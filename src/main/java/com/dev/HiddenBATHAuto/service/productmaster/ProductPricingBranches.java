package com.dev.HiddenBATHAuto.service.productmaster;

import static com.dev.HiddenBATHAuto.dto.productmaster.ProductPricingDtos.*;
import static com.dev.HiddenBATHAuto.dto.productmaster.ProductStudioDtos.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

/** Persist one owner; reuse the existing exhaustive interpreter for conditional tables. */
public final class ProductPricingBranches {
  private ProductPricingBranches() {}
  private static <T> List<T> safe(List<T> items) { return items == null ? List.of() : items; }
  private static void require(boolean ok,String message) { if(!ok)throw new IllegalArgumentException(message); }
  private static String id(String text) { return UUID.nameUUIDFromBytes(text.getBytes(StandardCharsets.UTF_8)).toString(); }

  /** One diagnostic per failing family, using the same effective answers as real pricing.
   * A hidden material answer must never make a visible numeric family's missing table disappear. */
  public static List<Issue> coverageIssues(Definition expanded, Evaluation evaluation, List<TermResult> results) {
    Map<String, QuestionState> states = evaluation.questions().stream()
        .collect(Collectors.toMap(s -> s.question().key(), s -> s));
    Set<String> applied = results.stream().filter(TermResult::applied).map(TermResult::key).collect(Collectors.toSet());
    Map<String, List<Term>> families = new LinkedHashMap<>();
    for (Term term : safe(expanded.terms())) if (term.branchScope() != null)
      families.computeIfAbsent(term.branchScope().ownerKey(), k -> new ArrayList<>()).add(term);
    List<Issue> issues = new ArrayList<>();
    for (Map.Entry<String, List<Term>> entry : families.entrySet()) {
      List<Term> family = entry.getValue(); Term first = family.get(0);
      QuestionState state = states.get(first.groupKey());
      if (state == null || !state.visible() && !state.question().fixed() || !state.errors().isEmpty()
          || !state.allowed().containsAll(first.fieldKeys()) || state.answer() == null
          || first.fieldKeys().stream().anyMatch(k -> state.answer().fields() == null || !state.answer().fields().containsKey(k))) continue;
      if (!safe(first.branchScope().conditions()).stream().allMatch(c -> ProductPricingEngine.conditionMatches(c, evaluation, states))) continue;
      List<Term> selected = family.stream().filter(t -> applied.contains(t.key())).toList();
      if (selected.size() == 1) continue;
      Set<String> sourceKeys = family.stream().flatMap(t -> safe(t.conditions()).stream())
          .map(Condition::groupKey).collect(Collectors.toCollection(LinkedHashSet::new));
      String hidden = sourceKeys.stream().map(states::get).filter(Objects::nonNull)
          .filter(s -> !s.visible() && !s.question().fixed()).map(s -> ProductStudioEngine.label(s.question()))
          .collect(Collectors.joining(" / "));
      String message = (selected.isEmpty() ? "조건별 계산 누락: " : "조건별 계산 중복: ")
          + first.name() + " · 현재 표시된 ‘" + ProductStudioEngine.label(state.question())
          + "’ 입력에는 이 항의 조건별 계산이 정확히 하나 적용되어야 합니다. ";
      if (selected.isEmpty()) message += (hidden.isBlank() ? "" : "현재 경로에서 숨겨진 기준 질문: " + hidden + ". ")
          + "이 항이 특정 상판·재질에만 해당하면 ‘항 전체 적용 조건’을 지정하세요. 공통 입력을 함께 사용한다면 같은 항 안에 다른 경로의 조건별 가격표도 추가하세요. 숨겨진 질문의 이전 답변은 계산에 사용하지 않습니다.";
      else message += "동시에 적용된 계산: " + selected.stream().map(Term::name).collect(Collectors.joining(" / "))
          + ". 분기별 기준 질문·선택 보기·추가 조건을 서로 겹치지 않게 나누세요.";
      issues.add(new Issue("ERROR", selected.isEmpty() ? "PRICE_BRANCH_GAP" : "PRICE_BRANCH_OVERLAP",
          ProductStudioEngine.label(state.question()), message, "terms." + entry.getKey()));
    }
    return List.copyOf(issues);
  }
  public static List<TermResult> results(Definition original,List<TermResult> evaluated) {
    if(original.terms().stream().noneMatch(t->!safe(t.branches()).isEmpty()))return evaluated;
    Map<String,TermResult> byKey=new HashMap<>(); evaluated.forEach(t->byKey.put(t.key(),t));
    List<TermResult> result=new ArrayList<>();
    for(Term owner:original.terms()) {
      if(safe(owner.branches()).isEmpty()) { if(byKey.containsKey(owner.key()))result.add(byKey.get(owner.key()));continue; }
      List<TermResult> candidates=owner.branches().stream().map(b->byKey.get(id(owner.key()+":"+b.key()))).filter(Objects::nonNull).toList();
      TermResult chosen=candidates.stream().filter(TermResult::applied).findFirst().orElse(candidates.isEmpty()?null:candidates.get(0));
      if(chosen!=null)result.add(new TermResult(owner.key(),owner.name(),chosen.applied(),chosen.amount(),chosen.name()+": "+chosen.explanation(),chosen.role(),chosen.baseAmount(),chosen.multipliers(),chosen.adjustments()));
    }
    return List.copyOf(result);
  }
  public static Definition expand(Definition definition) {
    if(definition == null)return null;
    List<Term> terms=new ArrayList<>();
    Set<String> ownerKeys=new HashSet<>();
    for(Term owner:safe(definition.terms())) {
      require(owner!=null,"빈 가격 항이 있습니다.");
      require(owner.key()!=null && ownerKeys.add(owner.key()),"가격 항 식별자가 없거나 중복됩니다.");
      if(safe(owner.branches()).isEmpty()) { terms.add(owner);continue; }
      require(owner.key()!=null && owner.key().matches("[A-Za-z0-9_-]{1,80}") && owner.name()!=null && !owner.name().isBlank() && owner.name().length()<=120,"조건표를 소유한 항의 이름·식별자를 확인하세요.");
      require(owner.role()!=TermRole.FACTOR && "ALL".equals(owner.match()),"조건별 가격표는 금액 항에 연결하고 항의 적용 조건은 모두 일치로 설정하세요.");
      require(owner.bundleKey()!=null && !safe(owner.fieldKeys()).isEmpty(),"조건별 계산은 숫자 입력묶음과 연결하세요.");
      require(owner.branches().size()<=30,"한 항의 조건별 가격표는 최대 30개입니다.");
      Set<String> keys=new HashSet<>();
      for(Term branch:owner.branches()) {
        require(branch!=null && branch.key()!=null && branch.key().matches("[A-Za-z0-9_-]{1,80}") && branch.name()!=null && !branch.name().isBlank() && branch.name().length()<=120 && keys.add(branch.key()),"조건별 가격표 식별자가 없거나 중복됩니다.");
        require(safe(branch.branches()).isEmpty(),"조건별 가격표 안에 다시 조건별 가격표를 넣을 수 없습니다.");
        require(branch.role()!=TermRole.FACTOR && "ALL".equals(branch.match()) && !safe(branch.conditions()).isEmpty(),"각 가격표에 모두 일치 방식의 적용 조건을 지정하세요.");
        require(Objects.equals(owner.groupKey(),branch.groupKey()) && Objects.equals(owner.bundleKey(),branch.bundleKey()) && Objects.equals(owner.fieldKeys(),branch.fieldKeys()),"조건별 계산은 같은 질문·입력묶음·숫자 항목을 사용해야 합니다. 각 조건에서 고정·곱셈·가격표를 다르게 지정할 수 있습니다.");
        require(safe(branch.multipliers()).isEmpty() && safe(branch.adjustments()).isEmpty() && branch.optionTarget()==null && branch.optionPricing()==null,"연관식과 배율은 각 조건표가 아닌 기본 항에 연결하세요.");
        List<Condition> conditions=new ArrayList<>(safe(owner.conditions()));conditions.addAll(branch.conditions());
        List<MultiplierLink> links=safe(owner.multipliers()).stream().map(link->new MultiplierLink(id(owner.key()+":"+branch.key()+":"+link.key()),link.name(),link.termKeys(),link.aggregation())).toList();
        terms.add(new Term(id(owner.key()+":"+branch.key()),owner.name()+" · "+branch.name(),owner.groupKey(),owner.bundleKey(),owner.fieldKeys(),branch.type(),branch.amount(),branch.rates(),branch.xAxis(),branch.yAxis(),branch.values(),"ALL",conditions,owner.choiceKeys(),null,owner.optionTarget(),TermRole.PRICE,links,owner.adjustments(),List.of(),new BranchScope(owner.key(),safe(owner.conditions()))));
      }
    }
    require(terms.size()<=100,"조건별 표와 연결 항을 합쳐 최대 100개까지 검증할 수 있습니다.");
    return new Definition(definition.schemaVersion(),definition.schemaFingerprint(),List.copyOf(terms),definition.testAnswers());
  }
}
