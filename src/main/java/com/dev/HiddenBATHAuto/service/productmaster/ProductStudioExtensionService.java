package com.dev.HiddenBATHAuto.service.productmaster;

import static com.dev.HiddenBATHAuto.dto.productmaster.ProductStudioDtos.*;
import static com.dev.HiddenBATHAuto.service.productmaster.ProductStudioEngine.*;

import com.dev.HiddenBATHAuto.dto.productmaster.ProductStudioDtos.Process;
import com.dev.HiddenBATHAuto.enums.productmaster.ProductMasterStatus;
import com.dev.HiddenBATHAuto.model.productmaster.*;
import com.dev.HiddenBATHAuto.repository.productmaster.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductStudioExtensionService {
  private final ProductMasterRepository products;
  private final ProductActualRepository actuals;
  private final ProductActualMovementRepository movements;
  private final ProductFaqTopicRepository topics;
  private final ProductStudioService studio;
  private final ProductStudioJson json;
  private final ProductMasterCodeService codes;
  private final ProductStudioAssetService assets;
  private final EntityManager em;

  public record FaqEntry(String key, String title, String content, List<String> assetIds) {}

  public record TopicEdit(
      Long id, Long version, String title, String phone, String link, List<FaqEntry> entries) {}

  public record TopicView(
      Long id,
      long version,
      String title,
      String phone,
      String link,
      List<FaqEntry> entries,
      List<AssetView> assets) {}

  public record ActualEdit(
      Map<String, Answer> answers, int quantity, String reason, boolean allocateLegacy) {
    public ActualEdit(Map<String, Answer> answers, int quantity, String reason) {
      this(answers, quantity, reason, false);
    }
  }

  public record StockEdit(Long version, int delta, String reason, boolean allocateLegacy) {
    public StockEdit(Long version, int delta, String reason) {
      this(version, delta, reason, false);
    }
  }

  public record SpecLine(String group, String value) {}

  public record ActualView(
      Long id,
      long version,
      Long productId,
      String parentCode,
      String productName,
      String code,
      int stock,
      List<SpecLine> specs,
      Map<String, Answer> answers) {}

  public List<TopicView> topics() {
    return topics.findAllByOrderByIdDesc().stream().map(this::topicView).toList();
  }

  private TopicView topicView(ProductFaqTopic t) {
    return new TopicView(
        t.getId(),
        t.getRowVersion(),
        t.getTitle(),
        t.getPhone(),
        t.getLink(),
        json.list(t.getEntriesJson(), FaqEntry.class),
        assets.owned("FAQ", t.getId()));
  }

  @Transactional
  public TopicView saveTopic(TopicEdit r, String actor) {
    if (r == null
        || text(r.title()).isBlank()
        || r.title().length() > 160
        || text(r.phone()).length() > 60
        || text(r.link()).length() > 2000)
      throw new IllegalArgumentException("FAQ 주제/전화번호/링크 길이를 확인해주세요.");
    if (!text(r.link()).isBlank()) {
      try {
        var uri = java.net.URI.create(r.link());
        if (!Set.of("https", "http").contains(uri.getScheme()) || uri.getHost() == null)
          throw new IllegalArgumentException();
      } catch (RuntimeException ex) {
        throw new IllegalArgumentException("문의 링크는 https:// 또는 http://로 시작하는 정상 URL을 입력해주세요.");
      }
    }
    if (!text(r.phone()).isBlank() && !r.phone().matches("[+0-9() -]{3,60}"))
      throw new IllegalArgumentException("전화번호 형식을 확인해주세요.");
    if (list(r.entries()).size() > 200) throw new IllegalArgumentException("주제별 FAQ는 최대 200개입니다.");
    ProductFaqTopic t =
        r.id() == null ? new ProductFaqTopic() : topics.findById(r.id()).orElseThrow();
    if (r.id() != null) {
      em.lock(t, LockModeType.PESSIMISTIC_WRITE);
      ProductStudioAttributeService.version(r.version(), t.getRowVersion());
    }
    Set<String> keys = new HashSet<>();
    List<String> fileIds = new ArrayList<>();
    for (FaqEntry f : list(r.entries())) {
      if (f == null
          || text(f.key()).isBlank()
          || !keys.add(f.key())
          || text(f.title()).isBlank()
          || f.title().length() > 200
          || text(f.content()).isBlank()
          || f.content().length() > 10000)
        throw new IllegalArgumentException("FAQ 제목/내용을 입력하고 중복 항목을 확인해주세요.");
      for (String id : list(f.assetIds())) {
        if (!assets.require(id).getContentType().startsWith("image/"))
          throw new IllegalArgumentException("FAQ에는 이미지 파일만 첨부해주세요.");
        fileIds.add(id);
      }
    }
    if (new HashSet<>(fileIds).size() != fileIds.size())
      throw new IllegalArgumentException("동일 이미지를 중복 첨부할 수 없습니다.");
    t.setTitle(r.title().trim());
    t.setPhone(text(r.phone()).trim());
    t.setLink(text(r.link()).trim());
    t.setEntriesJson(json.write(list(r.entries())));
    t.setUpdatedBy(actor);
    t.setUpdatedAt(LocalDateTime.now());
    topics.saveAndFlush(t);
    assets.attach("FAQ", t.getId(), fileIds, actor);
    return topicView(t);
  }

  @Transactional
  public void deleteTopic(Long id, Long version, String actor) {
    ProductFaqTopic t = topics.findById(id).orElseThrow();
    em.lock(t, LockModeType.PESSIMISTIC_WRITE);
    ProductStudioAttributeService.version(version, t.getRowVersion());
    // Detach affected products explicitly; no dangling topic IDs.
    for (ProductMaster p : products.findAll((r, q, b) -> b.equal(r.get("faqTopicId"), id))) {
      p.setFaqTopicId(null);
      p.setUpdatedBy(actor);
    }
    assets.attach("FAQ", id, List.of(), actor);
    topics.delete(t);
  }

  public TopicView publicFaq(String token) {
    ProductMaster p = studio.publicProduct(token);
    if (p.getFaqTopicId() == null) return null;
    return topics
        .findById(p.getFaqTopicId())
        .map(
            t -> {
              TopicView v = topicView(t);
              return new TopicView(
                  v.id(),
                  v.version(),
                  v.title(),
                  v.phone(),
                  v.link(),
                  v.entries(),
                  v.assets().stream()
                      .map(
                          a ->
                              new AssetView(
                                  a.id(),
                                  a.name(),
                                  a.type(),
                                  a.size(),
                                  "/product-spec/studio/" + token + "/faq/assets/" + a.id(),
                                  a.image()))
                      .toList());
            })
        .orElse(null);
  }

  public ProductStudioAsset faqAsset(String token, String id) {
    ProductMaster p = studio.publicProduct(token);
    ProductStudioAsset a = assets.require(id);
    if (!"FAQ".equals(a.getOwnerType()) || !Objects.equals(p.getFaqTopicId(), a.getOwnerId()))
      throw new NoSuchElementException("이미지가 없습니다.");
    return a;
  }

  public List<ActualView> actuals(Long parent) {
    studio.require(parent);
    return actuals.findByProductIdOrderByIdDesc(parent).stream().map(this::actualView).toList();
  }

  private Object canonical(Object v) {
    if (v instanceof Map<?, ?> m) {
      Map<String, Object> x = new TreeMap<>();
      m.forEach((k, val) -> x.put(k.toString(), canonical(val)));
      return x;
    }
    if (v instanceof Answer a)
      return Map.of("choices", canonical(list(a.choices())), "fields", canonical(map(a.fields())));
    if (v instanceof Number n)
      return new BigDecimal(n.toString()).stripTrailingZeros().toPlainString();
    if (v instanceof List<?> l)
      return l.stream().map(this::canonical).sorted(Comparator.comparing(json::write)).toList();
    return v;
  }

  @Transactional
  public ActualView createActual(Long id, ActualEdit r, String actor) {
    ProductMaster p = products.findForUpdate(id).orElseThrow();
    if (!p.isNonStandard()) throw new IllegalArgumentException("비규격 제품에만 실제품을 등록합니다.");
    if (p.getStatus() != ProductMasterStatus.ACTIVE)
      throw new IllegalArgumentException("원제품의 프로세스를 검증하고 사용중으로 저장한 뒤 실제품을 등록해주세요.");
    Process process = studio.detail(id).process();
    Validation validation = studio.validateProcess(id, process);
    if (!validation.valid() || !validation.exhaustive())
      throw new IllegalArgumentException("원제품 프로세스가 유효하지 않습니다.");
    Evaluation result = studio.evaluate(id, new EvaluateRequest(null, map(r.answers())), actor);
    if (!result.complete()) throw new IllegalArgumentException(String.join(" / ", result.errors()));
    Set<String> known = process.questions().stream().map(Question::key).collect(Collectors.toSet());
    if (!known.containsAll(map(r.answers()).keySet()))
      throw new IllegalArgumentException("원제품에 없는 질문이 포함되었습니다.");
    for (QuestionState state : result.questions()) {
      Answer raw = map(r.answers()).get(state.question().key());
      if (raw != null && !blank(raw) && !Objects.equals(canonical(raw), canonical(state.answer())))
        throw new IllegalArgumentException(
            state.question().labels().management() + ": 허용되지 않거나 건너뛴 사양이 포함되었습니다.");
    }
    String hash = codes.configurationHash(json.write(canonical(result.answers())));
    if (actuals.existsByProductIdAndSpecHash(id, hash))
      throw new IllegalArgumentException("같은 실제 사양이 이미 등록되어 있습니다. 해당 실제품의 재고를 변경해주세요.");
    if (r.quantity() < 0 || r.quantity() > 10000000)
      throw new IllegalArgumentException("재고는 0~10,000,000입니다.");
    ProductActual a = new ProductActual();
    a.setProduct(p);
    a.setSpecHash(hash);
    a.setAnswersJson(json.write(result.answers()));
    a.setCreatedBy(actor);
    a.setCode(
        p.getCatalogCode()
            + "-"
            + UUID.randomUUID()
                .toString()
                .substring(0, 12)
                .replace("-", "")
                .toUpperCase(Locale.ROOT));
    actuals.saveAndFlush(a);
    List<String> fileIds = new ArrayList<>();
    for (Question q : process.questions())
      if (q.control() == Control.FILE && !q.fixed())
        for (Object field : map(result.answers().getOrDefault(q.key(), empty()).fields()).values())
          if (field instanceof List<?> files) files.forEach(x -> fileIds.add(x.toString()));
    if (!fileIds.isEmpty()) assets.attach("ACTUAL", a.getId(), fileIds, actor);
    if (r.allocateLegacy()) {
      if (r.quantity() <= 0 || r.quantity() > p.getLegacyUnallocatedStock())
        throw new IllegalArgumentException("기존 미배정 재고 이내의 양수를 입력해주세요.");
      p.setLegacyUnallocatedStock(p.getLegacyUnallocatedStock() - r.quantity());
    }
    if (r.quantity() > 0)
      applyStock(
          a,
          r.quantity(),
          (r.allocateLegacy() ? "기존 재고 사양 배정: " : "")
              + (text(r.reason()).isBlank() ? "실제품 최초재고" : r.reason()),
          actor);
    return actualView(a);
  }

  @Transactional
  public ActualView stock(Long productId, Long actualId, StockEdit r, String actor) {
    ProductMaster p = products.findForUpdate(productId).orElseThrow();
    ProductActual a = actuals.findById(actualId).orElseThrow();
    if (!a.getProduct().getId().equals(p.getId()))
      throw new IllegalArgumentException("다른 원제품의 실제품입니다.");
    ProductStudioAttributeService.version(r.version(), a.getRowVersion());
    if (r.allocateLegacy()) {
      if (r.delta() <= 0 || r.delta() > p.getLegacyUnallocatedStock())
        throw new IllegalArgumentException("기존 미배정 재고 이내의 양수를 입력해주세요.");
      p.setLegacyUnallocatedStock(p.getLegacyUnallocatedStock() - r.delta());
    }
    applyStock(a, r.delta(), (r.allocateLegacy() ? "기존 재고 사양 배정: " : "") + r.reason(), actor);
    actuals.flush();
    return actualView(a);
  }

  private void applyStock(ProductActual a, int delta, String reason, String actor) {
    if (delta == 0
        || Math.abs((long) delta) > 10000000
        || (long) a.getCurrentStock() + delta < 0
        || (long) a.getCurrentStock() + delta > 10000000)
      throw new IllegalArgumentException("변경 후 재고는 0~10,000,000이어야 합니다.");
    if (text(reason).isBlank() || reason.length() > 500)
      throw new IllegalArgumentException("변경 사유를 1~500자로 입력해주세요.");
    ProductMaster p = a.getProduct();
    long total = (long) p.getCurrentStock() + delta;
    if (total < 0 || total > 10000000) throw new IllegalArgumentException("원제품 총재고 허용 범위를 초과했습니다.");
    a.setCurrentStock(a.getCurrentStock() + delta);
    p.setCurrentStock((int) total);
    p.setUpdatedBy(actor);
    ProductActualMovement m = new ProductActualMovement();
    m.setActual(a);
    m.setDelta(delta);
    m.setStockAfter(a.getCurrentStock());
    m.setReason(reason.trim());
    m.setActor(actor);
    movements.save(m);
  }

  public List<Map<String, Object>> history(Long parent, Long id) {
    ProductActual a = actuals.findById(id).orElseThrow();
    if (!a.getProduct().getId().equals(parent)) throw new IllegalArgumentException("다른 제품입니다.");
    return movements.findByActualIdOrderByIdDesc(id).stream()
        .map(
            m ->
                Map.<String, Object>of(
                    "delta",
                    m.getDelta(),
                    "stockAfter",
                    m.getStockAfter(),
                    "reason",
                    m.getReason(),
                    "actor",
                    m.getActor(),
                    "createdAt",
                    m.getCreatedAt()))
        .toList();
  }

  @SuppressWarnings("unchecked")
  private ActualView actualView(ProductActual a) {
    Map<String, Answer> answers =
        json.read(
            a.getAnswersJson(),
            new com.fasterxml.jackson.core.type.TypeReference<Map<String, Answer>>() {});
    ProductMaster p = a.getProduct();
    Process process = studio.detail(p.getId()).process();
    List<SpecLine> specs = new ArrayList<>();
    for (Question q : process.questions()) {
      Answer value = answers.get(q.key());
      if (value == null) continue;
      String display =
          choice(q.control())
              ? list(value.choices()).stream()
                  .map(
                      key ->
                          q.choices().stream()
                              .filter(c -> c.key().equals(key))
                              .map(c -> c.labels().management())
                              .findFirst()
                              .orElse(key))
                  .collect(Collectors.joining(", "))
              : q.fields().stream()
                  .filter(f -> map(value.fields()).containsKey(f.key()))
                  .map(
                      f ->
                          f.labels().management()
                              + ": "
                              + value.fields().get(f.key())
                              + " "
                              + text(f.unit()))
                  .collect(Collectors.joining(" / "));
      specs.add(new SpecLine(q.labels().management(), display));
    }
    return new ActualView(
        a.getId(),
        a.getRowVersion(),
        p.getId(),
        p.getCatalogCode(),
        p.getProductName(),
        a.getCode(),
        a.getCurrentStock(),
        specs,
        answers);
  }

  public Object decode(String code) {
    var actual = actuals.findByCode(code.trim());
    if (actual.isPresent()) return actualView(actual.get());
    return products
        .findByCatalogCode(code.trim())
        .map(p -> (Object) studio.detail(p.getId()))
        .orElseThrow(() -> new NoSuchElementException("등록된 제품 코드가 없습니다."));
  }
}
