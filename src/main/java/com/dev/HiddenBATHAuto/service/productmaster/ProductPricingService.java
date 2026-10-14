package com.dev.HiddenBATHAuto.service.productmaster;

import static com.dev.HiddenBATHAuto.service.productmaster.ProductStudioEngine.*;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductPricingDtos.*;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductStudioDtos.*;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductStudioDtos.Process;
import com.dev.HiddenBATHAuto.enums.productmaster.ProductMasterStatus;
import com.dev.HiddenBATHAuto.model.productmaster.*;
import com.dev.HiddenBATHAuto.repository.productmaster.*;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductPricingService {
  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ProductPricingService.class);
  private final ProductMasterRepository products;
  private final ProductAttributeGroupRepository attributeGroups;
  private final ProductActualRepository actuals;
  private final ProductStudioService studio;
  private final ProductStudioJson json;
  private final EntityManager entityManager;

  public Context context(Long id) { return context(require(id), true); }

  private Context context(ProductMaster product, boolean exhaustive) {
    Long id = product.getId();
    Process process = process(product);
    List<String> issues = readyIssues(product, process, exhaustive);
    Definition definition = read(product);
    String fingerprint = fingerprint(process, studio.fixedAnswers(product));
    List<PricingGroup> groups = pricingGroups(product, process);
    boolean stale = definition != null && (!Objects.equals(definition.schemaFingerprint(), fingerprint)
        || !ProductPricingEngine.validate(process, groups, definition).isEmpty());
    if (stale) issues.add("등록한 가격계산식과 현재 프로세스가 다릅니다. 가격계산식을 삭제한 뒤 다시 등록해 주세요.");
    return new Context(id, product.getRowVersion(), issues.isEmpty(), List.copyOf(issues), process,
        studio.fixedAnswers(product), groups, fingerprint, definition != null, stale, definition);
  }

  public List<Status> status(List<Long> ids) {
    if (ids == null || ids.size() > 200 || ids.stream().anyMatch(Objects::isNull))
      throw new IllegalArgumentException("가격 등록상태는 제품 ID 200개 이하로 조회해 주세요.");
    Map<Long, ProductMaster> byId = products.findAllById(new LinkedHashSet<>(ids)).stream()
        .collect(Collectors.toMap(ProductMaster::getId, p -> p));
    return ids.stream().map(id -> {
      ProductMaster p = byId.get(id);
      if (p == null) return new Status(id, false, false, false, "제품을 찾을 수 없습니다.");
      try {
        Process process = process(p);
        Definition definition = read(p);
        List<String> issues = readyIssues(p, process, false);
        boolean stale = definition != null && (!Objects.equals(definition.schemaFingerprint(), fingerprint(process, studio.fixedAnswers(p)))
            || !ProductPricingEngine.validate(process, pricingGroups(p, process), definition).isEmpty());
        return new Status(id, definition != null, issues.isEmpty() && !stale, stale,
            stale ? "프로세스 변경 확인 필요" : issues.isEmpty() ? definition == null ? "미등록" : "등록됨" : String.join(" / ", issues));
      } catch (RuntimeException ex) {
        log.warn("가격계산 상태 확인 실패: productId={}", id);
        return new Status(id, !text(p.getStudioPriceJson()).isBlank(), false, true,
            "저장된 프로세스·가격계산식 설정을 확인해 주세요.");
      }
    }).toList();
  }

  @Transactional
  public Context save(Long id, SaveRequest request, String actor) {
    if (request == null || request.definition() == null) throw new IllegalArgumentException("가격계산식이 필요합니다.");
    ProductMaster p = products.findForUpdate(id).orElseThrow(() -> new NoSuchElementException("제품을 찾을 수 없습니다."));
    ProductStudioAttributeService.version(request.version(), p.getRowVersion());
    lockImpactGroups(p);
    Process process = process(p);
    requireReady(p, process);
    Definition definition = request.definition();
    requireFingerprint(p, process, definition);
    List<String> errors = ProductPricingEngine.validate(process, pricingGroups(p, process), definition);
    if (!errors.isEmpty()) throw new IllegalArgumentException(String.join(" / ", errors));
    requireCoverage(p, process, definition);
    Calculation test = calculate(p, process, definition, definition.testAnswers());
    if (!test.complete()) throw new IllegalArgumentException("테스트 입력을 완료하고 계산 결과를 확인해 주세요. " + String.join(" / ", test.errors()));
    Definition clean = new Definition(1, definition.schemaFingerprint(), definition.terms(), test.evaluation().answers());
    p.setStudioPriceJson(json.write(clean));
    p.setUpdatedBy(actor);
    products.saveAndFlush(p);
    return context(p, false);
  }

  @Transactional
  public Context delete(Long id, Long version, String actor) {
    ProductMaster p = products.findForUpdate(id).orElseThrow(() -> new NoSuchElementException("제품을 찾을 수 없습니다."));
    ProductStudioAttributeService.version(version, p.getRowVersion());
    p.setStudioPriceJson(null);
    p.setUpdatedBy(actor);
    products.saveAndFlush(p);
    return context(id);
  }

  public Calculation evaluate(Long id, com.dev.HiddenBATHAuto.dto.productmaster.ProductPricingDtos.EvaluateRequest request) {
    if (request == null) throw new IllegalArgumentException("가격 테스트 입력이 필요합니다.");
    ProductMaster p = require(id);
    Process process = process(p);
    requireReadyFast(p, process);
    Definition definition = request.definition() == null ? read(p) : request.definition();
    if (definition == null) throw new IllegalArgumentException("가격계산식을 먼저 등록해 주세요.");
    requireFingerprint(p, process, definition);
    return calculate(p, process, definition, request.answers());
  }

  public List<CopySource> copySources(Long id) {
    ProductMaster target = require(id);
    Process targetProcess = process(target);
    requireReady(target, targetProcess);
    if (read(target) != null) throw new IllegalStateException("가격계산식이 없는 제품에서만 불러오기를 사용합니다. 등록된 식을 먼저 삭제해 주세요.");
    String signature = copySignature(target, targetProcess);
    return products.findAll((root, query, b) -> b.and(b.isTrue(root.get("nonStandard")),
        b.isNotNull(root.get("studioPriceJson")), b.notEqual(root.get("id"), id)))
        .stream().filter(p -> copySourceCompatible(p, signature)).sorted(Comparator.comparing(ProductMaster::getProductName).thenComparing(ProductMaster::getId))
        .map(p -> new CopySource(p.getId(), p.getProductName(), p.getCatalogCode(), p.getRowVersion())).toList();
  }

  private boolean copySourceCompatible(ProductMaster candidate, String targetSignature) {
    try {
      Process process = process(candidate);
      Definition definition = read(candidate);
      if (definition == null || !readyIssues(candidate, process, false).isEmpty()
          || !targetSignature.equals(copySignature(candidate, process))
          || !Objects.equals(definition.schemaFingerprint(), fingerprint(process, studio.fixedAnswers(candidate)))
          || !ProductPricingEngine.validate(process, pricingGroups(candidate, process), definition).isEmpty()) return false;
      Validation coverage = ProductPricingCoverage.validate(process, pricingGroups(candidate, process),
          definition, studio.fixedAnswers(candidate));
      return coverage.valid() && coverage.exhaustive();
    } catch (RuntimeException ex) {
      log.warn("가격계산 불러오기 후보 제외: productId={}", candidate.getId());
      return false;
    }
  }

  @Transactional
  public Context copy(Long id, CopyRequest request, String actor) {
    if (request == null || request.sourceProductId() == null || id.equals(request.sourceProductId()))
      throw new IllegalArgumentException("불러올 원제품을 선택해 주세요.");
    // Lock both products in global ID order so simultaneous copies cannot deadlock.
    Map<Long, ProductMaster> locked = new HashMap<>();
    List.of(id, request.sourceProductId()).stream().sorted().forEach(productId -> locked.put(productId,
        products.findForUpdate(productId).orElseThrow(() -> new NoSuchElementException("제품을 찾을 수 없습니다."))));
    ProductMaster target = locked.get(id), source = locked.get(request.sourceProductId());
    ProductStudioAttributeService.version(request.version(), target.getRowVersion());
    ProductStudioAttributeService.version(request.sourceVersion(), source.getRowVersion());
    if (read(target) != null) throw new IllegalStateException("이미 가격계산식이 있습니다. 기존 식을 삭제한 뒤 불러와 주세요.");
    lockImpactGroups(target);
    Process targetProcess = process(target), sourceProcess = process(source);
    requireReady(target, targetProcess);
    requireReady(source, sourceProcess);
    Definition sourceDefinition = read(source);
    if (sourceDefinition == null) throw new IllegalArgumentException("원제품에 등록한 가격계산식이 없습니다.");
    requireFingerprint(source, sourceProcess, sourceDefinition);
    if (!copySignature(target, targetProcess).equals(copySignature(source, sourceProcess)))
      throw new IllegalArgumentException("그룹·옵션·입력묶음·프로세스 분기·입력제한이 모두 같은 제품의 가격계산식만 불러올 수 있습니다.");
    Definition copied = ProductPricingCompatibility.remap(sourceDefinition, sourceProcess, targetProcess,
        fingerprint(targetProcess, studio.fixedAnswers(target)));
    List<String> errors = ProductPricingEngine.validate(targetProcess, pricingGroups(target, targetProcess), copied);
    if (!errors.isEmpty()) throw new IllegalArgumentException(String.join(" / ", errors));
    requireCoverage(target, targetProcess, copied);
    Calculation test = calculate(target, targetProcess, copied, copied.testAnswers());
    if (!test.complete()) throw new IllegalArgumentException("복사한 테스트 입력이 현재 프로세스에서 유효하지 않습니다. " + String.join(" / ", test.errors()));
    target.setStudioPriceJson(json.write(copied));
    target.setUpdatedBy(actor);
    products.saveAndFlush(target);
    return context(target, false);
  }

  public List<ActualCalculation> actuals(Long id) {
    ProductMaster p = require(id);
    Process process = process(p);
    Definition definition = read(p);
    List<String> ready = readyIssues(p, process, false);
    if (definition == null) ready.add("원제품에 가격계산식이 등록되지 않았습니다.");
    else if (!Objects.equals(definition.schemaFingerprint(), fingerprint(process, studio.fixedAnswers(p))))
      ready.add("등록한 가격계산식과 현재 프로세스가 다릅니다.");
    if (definition != null && ready.isEmpty()) ready.addAll(ProductPricingEngine.validate(process, pricingGroups(p, process), definition));
    Map<String, Answer> fixed = studio.fixedAnswers(p);
    return actuals.findByProductIdOrderByIdDesc(id).stream().map(a -> {
      if (!ready.isEmpty()) return new ActualCalculation(a.getId(), null, false, List.copyOf(ready), List.of(), null);
      try {
        Map<String, Answer> stored = new LinkedHashMap<>(answers(a));
        stored.putAll(fixed);
        Calculation calculation = ProductPricingEngine.calculateValidated(process, definition, stored);
        return actualResult(a.getId(), calculation);
      } catch (RuntimeException ex) {
        log.warn("실제품 가격계산 실패: actualId={}", a.getId());
        return new ActualCalculation(a.getId(), null, false,
            List.of("저장된 실제품 사양을 확인해 주세요."), List.of(), null);
      }
    }).toList();
  }

  public ActualCalculation actual(Long id, Long actualId) {
    ProductActual actual = actuals.findById(actualId).orElseThrow(() -> new NoSuchElementException("실제품을 찾을 수 없습니다."));
    if (!actual.getProduct().getId().equals(id)) throw new NoSuchElementException("해당 제품에 소속된 실제품이 아닙니다.");
    ProductMaster p = require(id);
    Process process = process(p);
    requireReadyFast(p, process);
    Definition definition = read(p);
    if (definition == null) throw new IllegalArgumentException("원제품에 가격계산식을 먼저 등록해 주세요.");
    requireFingerprint(p, process, definition);
    return actualResult(actualId, calculate(p, process, definition, answers(actual)));
  }

  private ActualCalculation actualResult(Long id, Calculation c) {
    return new ActualCalculation(id, c.total(), c.complete(), c.errors(), c.terms(), c.evaluation());
  }
  private Map<String, Answer> answers(ProductActual a) {
    return json.read(a.getAnswersJson(), new TypeReference<Map<String, Answer>>() {});
  }
  private Calculation calculate(ProductMaster p, Process process, Definition definition, Map<String, Answer> submitted) {
    Map<String, Answer> answers = new LinkedHashMap<>(map(submitted));
    answers.putAll(studio.fixedAnswers(p));
    return ProductPricingEngine.calculate(process, pricingGroups(p, process), definition, answers);
  }

  public void assertProcessChangeAllowed(ProductMaster product, Process proposed) {
    if (read(product) == null) return;
    if (!fingerprint(process(product), studio.fixedAnswers(product)).equals(fingerprint(proposed, studio.fixedAnswers(product))))
      throw new IllegalStateException("가격계산식에 연결된 프로세스입니다. 가격계산식을 삭제한 뒤 그룹·옵션·분기·입력제한을 변경하고 다시 등록해 주세요.");
  }

  public void assertProductChangeAllowed(ProductMaster product, List<Variant> proposed) {
    if (read(product) == null) return;
    if (!canonical(studio.variants(product)).equals(canonical(proposed)))
      throw new IllegalStateException("가격계산식에 연결된 제품 사양입니다. 가격계산식을 삭제한 뒤 그룹 구성을 변경해 주세요.");
  }

  public void assertGroupPriceImpactCanDisable(Long groupId) {
    if (groupParticipates(groupId))
      throw new IllegalStateException("이 그룹은 등록된 제품 가격계산식의 항에서 사용 중입니다. 해당 가격계산식을 삭제하거나 이 그룹의 항을 제거한 뒤 가격영향을 해제해 주세요.");
  }

  public boolean groupParticipates(Long groupId) {
    return products.findAll((root, query, b) -> {
      query.distinct(true);
      return b.and(b.isNotNull(root.get("studioPriceJson")),
          b.equal(root.join("components").get("group").get("id"), groupId));
    }).stream().anyMatch(p -> {
      Set<String> groupKeys = process(p).questions().stream().filter(q -> Objects.equals(q.groupId(), groupId))
          .map(Question::key).collect(Collectors.toSet());
      return list(read(p).terms()).stream().anyMatch(t -> t != null && groupKeys.contains(t.groupKey()));
    });
  }

  private ProductMaster require(Long id) {
    return products.findById(id).orElseThrow(() -> new NoSuchElementException("제품을 찾을 수 없습니다."));
  }
  private Process process(ProductMaster p) {
    return p.getStudioProcessJson() == null ? studio.defaultProcess(studio.variants(p))
        : json.read(p.getStudioProcessJson(), Process.class);
  }
  private Definition read(ProductMaster p) {
    return text(p.getStudioPriceJson()).isBlank() ? null : json.read(p.getStudioPriceJson(), Definition.class);
  }
  private Set<Long> impactGroups(ProductMaster p) {
    return p.getComponents().stream().map(ProductComponent::getGroup).filter(ProductAttributeGroup::isPriceImpact)
        .map(ProductAttributeGroup::getId).collect(Collectors.toSet());
  }
  private List<PricingGroup> pricingGroups(ProductMaster p, Process process) {
    return ProductPricingEngine.groups(process, impactGroups(p));
  }
  private void lockImpactGroups(ProductMaster p) {
    p.getComponents().stream().map(c -> c.getGroup().getId()).distinct().sorted().forEach(id -> {
      ProductAttributeGroup group = attributeGroups.findById(id).orElseThrow(() -> new NoSuchElementException("그룹을 찾을 수 없습니다."));
      entityManager.lock(group, LockModeType.PESSIMISTIC_READ);
      entityManager.refresh(group);
    });
  }
  private List<String> readyIssues(ProductMaster p, Process process, boolean exhaustive) {
    List<String> issues = new ArrayList<>();
    if (!p.isNonStandard()) issues.add("비규격 제품에만 가격계산식을 등록합니다.");
    if (p.getStatus() == ProductMasterStatus.DRAFT || p.getStudioProcessJson() == null)
      issues.add("프로세스 구성을 완료하고 검증·사용중 저장한 제품만 가격계산식을 등록합니다.");
    if (issues.isEmpty()) {
      if (exhaustive) {
        Validation validation = studio.validateProcess(p.getId(), process);
        if (!validation.valid() || !validation.exhaustive()) {
          issues.add("현재 프로세스의 분기·입력제한 검증을 완료할 수 없습니다.");
          issues.addAll(validation.issues().stream().filter(i -> "ERROR".equals(i.severity())).map(Issue::message).limit(10).toList());
        }
      } else issues.addAll(ProductStudioEngine.structure(process).stream().filter(i -> "ERROR".equals(i.severity())).map(Issue::message).limit(5).toList());
    }
    if (issues.isEmpty() && pricingGroups(p, process).stream()
        .noneMatch(g -> g.control() == Control.NUMBER || choice(g.control())))
      issues.add("가격 항을 구성할 숫자입력형·선택형 가격영향 그룹이 없습니다. 그룹의 가격영향 설정을 확인해 주세요.");
    return issues;
  }
  private void requireCoverage(ProductMaster product, Process process, Definition definition) {
    Validation validation = ProductPricingCoverage.validate(process, pricingGroups(product, process),
        definition, studio.fixedAnswers(product));
    if (!validation.valid() || !validation.exhaustive()) {
      String details = validation.issues().stream().filter(issue -> "ERROR".equals(issue.severity()))
          .map(Issue::message).distinct().limit(12).collect(Collectors.joining(" / "));
      throw new IllegalArgumentException("프로세스와 가격계산식의 연결을 확인해 주세요. "
          + (details.isBlank() ? "전체 경로 검증을 완료할 수 없습니다." : details));
    }
  }

  /** Publication has already passed exhaustive validation; interactive reads evaluate the real path. */
  private void requireReadyFast(ProductMaster p, Process process) {
    List<String> issues = readyIssues(p, process, false);
    if (!issues.isEmpty()) throw new IllegalStateException(String.join(" / ", issues));
  }

  private void requireReady(ProductMaster p, Process process) {
    List<String> issues = readyIssues(p, process, true);
    if (!issues.isEmpty()) throw new IllegalStateException(String.join(" / ", issues));
  }
  private void requireFingerprint(ProductMaster p, Process process, Definition definition) {
    if (!Objects.equals(definition.schemaFingerprint(), fingerprint(process, studio.fixedAnswers(p))))
      throw new IllegalStateException("프로세스가 변경되었습니다. 화면을 새로고침하고 현재 그룹·옵션·입력묶음에 맞춰 가격계산식을 다시 확인해 주세요.");
  }

  private String fingerprint(Process process, Map<String, Answer> fixed) {
    return ProductPricingCompatibility.fingerprint(process, fixed);
  }
  private String copySignature(ProductMaster p, Process process) {
    return ProductPricingCompatibility.signature(process, studio.fixedAnswers(p), impactGroups(p));
  }
  private Object canonical(Object value) { return ProductPricingCompatibility.canonical(value); }
}
