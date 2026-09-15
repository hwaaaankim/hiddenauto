package com.dev.HiddenBATHAuto.service.productmaster;

import static com.dev.HiddenBATHAuto.dto.productmaster.ProductStudioDtos.*;
import static com.dev.HiddenBATHAuto.service.productmaster.ProductStudioEngine.*;

import com.dev.HiddenBATHAuto.dto.productmaster.ProductStudioDtos.Process;
import com.dev.HiddenBATHAuto.enums.productmaster.*;
import com.dev.HiddenBATHAuto.model.productmaster.*;
import com.dev.HiddenBATHAuto.repository.productmaster.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductStudioService {
  public static final int MAX_GENERATE = 10_000;
  private final ProductMasterRepository products;
  private final ProductComponentRepository components;
  private final ProductAttributeValueRepository values;
  private final ProductStudioAttributeService attributes;
  private final ProductStudioAssetService assets;
  private final ProductMasterCodeService codes;
  private final ProductInventoryService inventory;
  private final ProductStockMovementRepository movements;
  private final ProductStudioJson json;
  private final EntityManager entityManager;

  private record Plan(
      List<ProductAttributeGroup> groups,
      List<List<Variant>> axes,
      List<NameToken> tokens,
      long count,
      String stamp,
      boolean custom) {}

  private Plan plan(GenerateRequest request) {
    if (request == null
        || list(request.groups()).size() < 3
        || request.groups().size() > MAX_QUESTIONS)
      throw new IllegalArgumentException("대분류·중분류·시리즈를 포함하여 3~40개 그룹을 선택해 주세요.");
    List<ProductAttributeGroup> groups = new ArrayList<>();
    Set<Long> ids = new HashSet<>();
    Set<String> roles = new HashSet<>();
    for (GroupSelection selected : request.groups()) {
      if (selected == null || selected.groupId() == null || !ids.add(selected.groupId()))
        throw new IllegalArgumentException("그룹이 중복되었거나 올바르지 않습니다.");
      ProductAttributeGroup g = attributes.require(selected.groupId());
      if (!g.isActive())
        throw new IllegalArgumentException(g.getManagementLabel() + " 그룹이 비활성화되어 있습니다.");
      groups.add(g);
      if (ProductStudioAttributeService.BASE.contains(g.getSystemRole().name())
          && !roles.add(g.getSystemRole().name()))
        throw new IllegalArgumentException("각 기본그룹은 한 번씩만 선택해 주세요.");
    }
    if (!roles.containsAll(ProductStudioAttributeService.BASE))
      throw new IllegalArgumentException("대분류·중분류·시리즈는 필수입니다.");
    boolean custom = groups.stream().anyMatch(ProductAttributeGroup::isNonStandard);
    if (custom
        && groups.stream()
            .anyMatch(
                g ->
                    !ProductStudioAttributeService.BASE.contains(g.getSystemRole().name())
                        && !g.isNonStandard()))
      throw new IllegalArgumentException(
          "비규격 제품은 세 기본그룹을 제외한 모든 그룹이 비규격이어야 합니다. 규격 그룹을 제거하거나 비규격 그룹으로 교체해 주세요.");
    List<List<Variant>> axes = new ArrayList<>();
    long count = 1;
    for (int i = 0; i < groups.size(); i++) {
      ProductAttributeGroup g = groups.get(i);
      GroupSelection s = request.groups().get(i);
      List<Variant> axis = new ArrayList<>();
      if (g.isNonStandard()) {
        if (!list(s.valueIds()).isEmpty() || !map(s.inputs()).isEmpty())
          throw new IllegalArgumentException("비규격 그룹의 보기·입력 필드는 비규격 관리에서 설정합니다.");
        axis.add(new Variant(g.getId(), List.of(), Map.of()));
      } else if (choice(attributes.control(g))) {
        List<Long> selected =
            s.valueIds() == null
                ? g.getValues().stream()
                    .filter(ProductAttributeValue::isActive)
                    .map(ProductAttributeValue::getId)
                    .toList()
                : s.valueIds();
        if (selected.isEmpty()
            || selected.size() > 200
            || new HashSet<>(selected).size() != selected.size())
          throw new IllegalArgumentException(
              g.getManagementLabel() + ": 보기를 하나 이상 활성화해 주세요. 중복은 허용하지 않습니다.");
        Map<Long, ProductAttributeValue> available =
            g.getValues().stream().collect(Collectors.toMap(ProductAttributeValue::getId, v -> v));
        for (Long id : selected) {
          ProductAttributeValue v = available.get(id);
          if (v == null || !v.isActive())
            throw new IllegalArgumentException("다른 그룹 또는 비활성화된 보기가 포함되어 있습니다.");
          if (v.getDimensionType() == ProductDimensionType.CUSTOM)
            throw new IllegalArgumentException(
                "기존 '비규격 옵션값'은 새 비규격 그룹으로 분리해 주세요: " + g.getManagementLabel());
          Map<String, Object> input = normalizeDimension(v, s.inputs());
          axis.add(new Variant(g.getId(), List.of(id), input));
        }
      } else {
        if (!list(s.valueIds()).isEmpty())
          throw new IllegalArgumentException("입력형 그룹은 선택 보기를 사용할 수 없습니다.");
        axis.add(new Variant(g.getId(), List.of(), normalizeInputs(g, s.inputs())));
      }
      if (count > MAX_GENERATE / axis.size())
        throw new IllegalArgumentException("한 번에 최대 10,000개까지 생성할 수 있습니다. 활성 옵션을 나누어 생성해 주세요.");
      count *= axis.size();
      axes.add(axis);
    }
    List<NameToken> tokens = validateTokens(request.nameTokens(), groups);
    String stamp =
        codes.configurationHash(
            json.write(
                    groups.stream()
                        .map(
                            g ->
                                List.of(
                                    g.getId(),
                                    g.getRowVersion(),
                                    g.getValues().stream()
                                        .map(v -> List.of(v.getId(), v.getRowVersion()))
                                        .toList()))
                        .toList())
                + json.write(request.groups())
                + json.write(tokens));
    return new Plan(groups, axes, tokens, count, stamp, custom);
  }

  private Map<String, Object> normalizeDimension(ProductAttributeValue v, Map<String, Object> raw) {
    Map<String, Object> input = map(raw);
    Map<String, Object> result = new LinkedHashMap<>();
    if (v.getDimensionType() == ProductDimensionType.NONE) {
      if (!input.isEmpty()) throw new IllegalArgumentException("일반 선택형에는 입력값을 섞을 수 없습니다.");
      return result;
    }
    List<String> keys =
        v.getDimensionType() == ProductDimensionType.WIDTH_HEIGHT
            ? List.of("widthMm", "heightMm")
            : List.of("widthMm", "heightMm", "depthMm");
    for (String key : keys) {
      try {
        int n = number(input.get(key)).intValueExact();
        if (n < 1 || n > 100000) throw new ArithmeticException();
        result.put(key, n);
      } catch (RuntimeException e) {
        throw new IllegalArgumentException("기존 치수형 보기는 W/H/D를 1~100,000 정수로 입력해 주세요.");
      }
    }
    // A shared generation axis may contain both W/H and W/H/D variants; retain only its dimensions.
    if (!Set.of("widthMm", "heightMm", "depthMm").containsAll(input.keySet()))
      throw new IllegalArgumentException("치수 입력 항목을 확인해 주세요.");
    return result;
  }

  private String dimensionSuffix(Map<String, Object> input) {
    List<String> parts = new ArrayList<>();
    for (String key : List.of("widthMm", "heightMm", "depthMm")) {
      Object value = map(input).get(key);
      if (value != null)
        parts.add(
            switch (key) {
                  case "widthMm" -> "W";
                  case "heightMm" -> "H";
                  default -> "D";
                }
                + display(value));
    }
    return parts.isEmpty() ? "" : " " + String.join("×", parts);
  }

  private Labels dimensionLabels(Labels labels, Map<String, Object> input) {
    String suffix = dimensionSuffix(input);
    return new Labels(
        labels.customer() + suffix, labels.production() + suffix, labels.management() + suffix);
  }

  private Map<String, Object> normalizeInputs(ProductAttributeGroup g, Map<String, Object> raw) {
    List<Field> fields = attributes.fieldsFor(g);
    fields(attributes.control(g), fields, false);
    Map<String, Object> result = new LinkedHashMap<>();
    Map<String, Object> input = map(raw);
    Set<String> keys = fields.stream().map(Field::key).collect(Collectors.toSet());
    if (!keys.containsAll(input.keySet()))
      throw new IllegalArgumentException("알 수 없는 입력 필드가 있습니다.");
    for (Field f : fields) {
      Object v = input.get(f.key());
      boolean missing =
          v == null
              || v instanceof String s && s.isBlank()
              || v instanceof List<?> l && l.isEmpty();
      if (missing) {
        if (f.required())
          throw new IllegalArgumentException(f.labels().management() + " 값을 입력해 주세요.");
        continue;
      }
      String error = valueError(attributes.control(g), f, v);
      if (error != null) throw new IllegalArgumentException(f.labels().management() + ": " + error);
      result.put(
          f.key(), attributes.control(g) == Control.NUMBER ? number(v).stripTrailingZeros() : v);
    }
    return result;
  }

  private List<NameToken> validateTokens(
      List<NameToken> supplied, List<ProductAttributeGroup> groups) {
    List<NameToken> tokens =
        supplied == null
            ? groups.stream()
                .filter(ProductAttributeGroup::isIncludeInName)
                .map(g -> new NameToken(g.getId(), "-", "", ""))
                .toList()
            : supplied;
    Set<Long>
        allowed =
            groups.stream()
                .filter(ProductAttributeGroup::isIncludeInName)
                .map(ProductAttributeGroup::getId)
                .collect(Collectors.toSet()),
        seen = new HashSet<>();
    if (tokens.size() > MAX_QUESTIONS) throw new IllegalArgumentException("제품명 요소가 너무 많습니다.");
    for (NameToken t : tokens)
      if (t == null
          || !allowed.contains(t.groupId())
          || !seen.add(t.groupId())
          || text(t.before()).length() > 30
          || text(t.prefix()).length() > 30
          || text(t.suffix()).length() > 30)
        throw new IllegalArgumentException("제품명 구성요소 또는 구분문자를 확인해 주세요.");
    return List.copyOf(tokens);
  }

  private List<Variant> variant(Plan plan, long index) {
    if (index < 0 || index >= plan.count())
      throw new IllegalArgumentException("미리보기 항목 번호가 잘못되었습니다.");
    List<Variant> result = new ArrayList<>(Collections.nCopies(plan.axes().size(), null));
    for (int i = plan.axes().size() - 1; i >= 0; i--) {
      List<Variant> axis = plan.axes().get(i);
      result.set(i, axis.get((int) (index % axis.size())));
      index /= axis.size();
    }
    return result;
  }

  public String identity(List<Variant> selected) {
    List<Object> parts = new ArrayList<>();
    for (Variant v : selected) {
      ProductAttributeGroup g = attributes.require(v.groupId());
      List<String> optionCodes =
          list(v.valueIds()).stream()
              .map(
                  id ->
                      g.getValues().stream()
                          .filter(x -> id.equals(x.getId()))
                          .findFirst()
                          .orElseThrow(() -> new IllegalArgumentException("그룹과 보기가 일치하지 않습니다."))
                          .getValueCode())
              .sorted()
              .toList();
      parts.add(
          List.of(
              g.getGroupCode(),
              g.isNonStandard() ? "CUSTOM" : "FIXED",
              optionCodes,
              canonical(map(v.inputs()))));
    }
    parts.sort(Comparator.comparing(o -> ((List<?>) o).get(0).toString()));
    return codes.configurationHash(json.write(parts));
  }

  private Object canonical(Object value) {
    if (value instanceof Map<?, ?> m) {
      Map<String, Object> sorted = new TreeMap<>();
      m.forEach((k, v) -> sorted.put(String.valueOf(k), canonical(v)));
      return sorted;
    }
    if (value instanceof Number n)
      return new BigDecimal(n.toString()).stripTrailingZeros().toPlainString();
    if (value instanceof List<?> l)
      return l.stream().map(this::canonical).sorted(Comparator.comparing(json::write)).toList();
    return value;
  }

  private String generatedName(List<Variant> selected, List<NameToken> tokens) {
    Map<Long, Variant> byId = selected.stream().collect(Collectors.toMap(Variant::groupId, v -> v));
    StringBuilder name = new StringBuilder();
    for (NameToken token : tokens) {
      Variant v = byId.get(token.groupId());
      if (v == null) continue;
      ProductAttributeGroup g = attributes.require(v.groupId());
      String part;
      if (g.isNonStandard()) continue;
      if (choice(attributes.control(g)))
        part =
            list(v.valueIds()).stream()
                .map(
                    id ->
                        g.getValues().stream()
                            .filter(x -> id.equals(x.getId()))
                            .findFirst()
                            .orElseThrow())
                .map(
                    x ->
                        attributes.namePart(x).isBlank()
                            ? ""
                            : attributes.namePart(x) + dimensionSuffix(v.inputs()))
                .filter(s -> !s.isBlank())
                .collect(Collectors.joining("+"));
      else
        part =
            attributes.fieldsFor(g).stream()
                .filter(f -> map(v.inputs()).containsKey(f.key()))
                .map(f -> text(f.namePart()) + display(map(v.inputs()).get(f.key())))
                .collect(Collectors.joining("*"));
      if (part.isBlank()) continue;
      if (name.length() > 0) name.append(text(token.before()));
      name.append(text(token.prefix())).append(part).append(text(token.suffix()));
    }
    return name.toString();
  }

  private String display(Object value) {
    return value instanceof BigDecimal n
        ? n.stripTrailingZeros().toPlainString()
        : String.valueOf(value);
  }

  public Preview preview(GenerateRequest request) {
    Plan plan = plan(request);
    int offset = Math.max(0, request.offset()),
        limit = request.limit() <= 0 ? 100 : Math.min(200, request.limit());
    if (offset >= plan.count()) throw new IllegalArgumentException("미리보기 페이지 범위를 확인해 주세요.");
    List<PreviewRow> rows = new ArrayList<>();
    for (long i = offset; i < Math.min(plan.count(), (long) offset + limit); i++) {
      List<Variant> v = variant(plan, i);
      String hash = identity(v), name = generatedName(v, plan.tokens());
      rows.add(
          new PreviewRow(
              i + ":" + hash,
              name,
              v,
              null,
              null,
              name.isBlank() || name.length() > 160
                  ? List.of("제품명을 1~160자로 입력해 주세요.")
                  : List.of()));
    }
    Map<String, ProductMaster> existing =
        duplicates(rows.stream().map(r -> r.key().substring(r.key().indexOf(':') + 1)).toList());
    rows =
        rows.stream()
            .map(
                r -> {
                  ProductMaster duplicate =
                      existing.get(r.key().substring(r.key().indexOf(':') + 1));
                  return new PreviewRow(
                      r.key(),
                      r.productName(),
                      r.variants(),
                      duplicate == null ? null : duplicate.getId(),
                      duplicate == null ? null : duplicate.getProductName(),
                      r.errors());
                })
            .toList();
    return new Preview(plan.count(), offset, limit, rows, plan.stamp());
  }

  private Map<String, ProductMaster> duplicates(Collection<String> identities) {
    Map<String, ProductMaster> result = new HashMap<>();
    List<String> keys = new ArrayList<>(identities);
    for (int start = 0; start < keys.size(); start += 500)
      for (ProductMaster p :
          products.findByStudioIdentityIn(keys.subList(start, Math.min(keys.size(), start + 500))))
        result.put(p.getStudioIdentity(), p);
    // Legacy items retain their original codes. Compare their actual components without rewriting
    // their identity or inventory.
    List<ProductMaster> legacy =
        products.findAll(
            (Specification<ProductMaster>) (r, q, b) -> b.isNull(r.get("studioIdentity")));
    Set<String> requested = new HashSet<>(identities);
    for (ProductMaster p : legacy) {
      String hash = identity(variants(p));
      if (requested.contains(hash)) result.put(hash, p);
    }
    return result;
  }

  @Transactional
  public List<Long> register(RegisterRequest request, String actor) {
    if (request == null || list(request.rows()).isEmpty() || request.rows().size() > MAX_GENERATE)
      throw new IllegalArgumentException("등록할 미리보기 항목을 선택해 주세요.");
    Plan plan = plan(request.generation());
    plan.groups().stream()
        .sorted(Comparator.comparing(ProductAttributeGroup::getId))
        .forEach(g -> entityManager.lock(g, LockModeType.PESSIMISTIC_WRITE));
    // Re-read locked definitions before accepting the preview version.
    for (ProductAttributeGroup g : plan.groups()) entityManager.refresh(g);
    plan = plan(request.generation());
    if (!Objects.equals(plan.stamp(), request.definitionStamp()))
      throw new IllegalStateException("그룹·옵션 또는 생성 조건이 변경되었습니다. 미리보기를 다시 생성해 주세요.");
    List<List<Variant>> selections = new ArrayList<>();
    List<String> names = new ArrayList<>();
    Set<String> hashes = new HashSet<>(), fileIds = new HashSet<>();
    for (RegistrationRow row : request.rows()) {
      if (row == null || row.key() == null) throw new IllegalArgumentException("미리보기 행 정보가 없습니다.");
      long index;
      try {
        index = Long.parseLong(row.key().split(":", 2)[0]);
      } catch (RuntimeException e) {
        throw new IllegalArgumentException("미리보기 행 정보가 잘못되었습니다.");
      }
      List<Variant> v = variant(plan, index);
      String hash = identity(v);
      if (!row.key().equals(index + ":" + hash) || !hashes.add(hash))
        throw new IllegalArgumentException("미리보기 사양이 변경되었거나 같은 제품이 중복 선택되었습니다.");
      String name =
          row.productName() == null ? generatedName(v, plan.tokens()) : row.productName().trim();
      validateName(name);
      if (row.initialStock() < 0 || row.initialStock() > 10_000_000)
        throw new IllegalArgumentException("최초재고는 0~10,000,000입니다.");
      assets.validateStaged(row.assetIds(), actor, "PRODUCT", null);
      for (String id : list(row.assetIds()))
        if (!fileIds.add(id))
          throw new IllegalArgumentException("같은 임시 파일을 여러 제품에 공유할 수 없습니다. 각 제품에 파일을 다시 추가해 주세요.");
      selections.add(v);
      names.add(name);
    }
    Map<String, ProductMaster> existing = duplicates(hashes);
    if (!existing.isEmpty())
      throw new IllegalStateException(
          "동일 제품이 이미 등록되어 있습니다: "
              + existing.values().stream()
                  .map(p -> p.getProductName() + " (#" + p.getId() + ")")
                  .limit(10)
                  .collect(Collectors.joining(", "))
              + ". 미리보기를 다시 열어 중복 행을 삭제해 주세요.");
    List<Long> ids = new ArrayList<>();
    for (int i = 0; i < request.rows().size(); i++) {
      RegistrationRow row = request.rows().get(i);
      List<Variant> v = selections.get(i);
      String hash = identity(v);
      ProductMaster p = new ProductMaster();
      p.setProductName(names.get(i));
      p.setProductCode("PM2|" + hash);
      p.setConfigurationHash(codes.configurationHash(p.getProductCode()));
      p.setStudioIdentity(hash);
      p.setCatalogCode(codes.newCatalogCode(p.getProductCode()));
      p.setQrPublicToken(UUID.randomUUID().toString());
      p.setNonStandard(plan.custom());
      p.setStatus(plan.custom() ? ProductMasterStatus.DRAFT : ProductMasterStatus.ACTIVE);
      p.setPricingMode(ProductPricingMode.FIXED);
      p.setCreatedBy(actor);
      p.setUpdatedBy(actor);
      p.setStudioDefinitionJson(json.write(v));
      p.setNameTokensJson(json.write(plan.tokens()));
      addComponents(p, v);
      products.saveAndFlush(p);
      p.setStudioProcessJson(json.write(defaultProcess(v)));
      bindInputFiles(v, actor);
      assets.attach("PRODUCT", p.getId(), row.assetIds(), actor);
      if (row.initialStock() > 0)
        inventory.recordInitialStock(
            p.getId(), row.initialStock(), "제품 자동생성 최초재고", List.of(), actor);
      ids.add(p.getId());
    }
    products.flush();
    return ids;
  }

  private static void validateName(String name) {
    if (name == null || name.isBlank() || name.length() > 160)
      throw new IllegalArgumentException("제품명은 1~160자여야 합니다.");
  }

  private void addComponents(ProductMaster p, List<Variant> selected) {
    int sort = 0;
    for (Variant variant : selected) {
      ProductAttributeGroup g = attributes.require(variant.groupId());
      List<Long> ids =
          list(variant.valueIds()).isEmpty() ? Collections.singletonList(null) : variant.valueIds();
      for (Long id : ids) {
        ProductComponent c = new ProductComponent();
        c.setGroup(g);
        c.setSortOrder(sort++);
        if (id != null) c.setValue(values.findById(id).orElseThrow());
        Map<String, Object> input = map(variant.inputs());
        if (input.containsKey("widthMm"))
          c.setWidthMm(number(input.get("widthMm")).intValueExact());
        if (input.containsKey("heightMm"))
          c.setHeightMm(number(input.get("heightMm")).intValueExact());
        if (input.containsKey("depthMm"))
          c.setDepthMm(number(input.get("depthMm")).intValueExact());
        if (attributes.control(g) == Control.NUMBER && input.size() == 1)
          c.setNumericValue(number(input.values().iterator().next()));
        if (attributes.control(g) == Control.TEXT && input.size() == 1)
          c.setTextValue(String.valueOf(input.values().iterator().next()));
        p.addComponent(c);
      }
      if (!g.isNonStandard() && !choice(attributes.control(g)))
        for (var field : map(variant.inputs()).entrySet()) {
          ProductStudioInput input = new ProductStudioInput();
          input.setProduct(p);
          input.setGroup(g);
          input.setFieldKey(field.getKey());
          if (attributes.control(g) == Control.NUMBER)
            input.setNumberValue(number(field.getValue()));
          else if (attributes.control(g) == Control.TEXT
              || attributes.control(g) == Control.TEXTAREA)
            input.setTextValue(String.valueOf(field.getValue()));
          p.getStudioInputs().add(input);
        }
    }
  }

  public ProductMaster require(Long id) {
    return products.findById(id).orElseThrow(() -> new NoSuchElementException("제품을 찾을 수 없습니다."));
  }

  public List<Variant> variants(ProductMaster p) {
    if (p.getStudioDefinitionJson() != null)
      return json.list(p.getStudioDefinitionJson(), Variant.class);
    Map<Long, List<ProductComponent>> grouped =
        components.findDetailedByProductId(p.getId()).stream()
            .collect(
                Collectors.groupingBy(
                    c -> c.getGroup().getId(), LinkedHashMap::new, Collectors.toList()));
    List<Variant> selected = new ArrayList<>();
    grouped.forEach(
        (id, items) -> {
          ProductComponent c = items.get(0);
          Map<String, Object> input = new LinkedHashMap<>();
          if (c.getWidthMm() != null) input.put("widthMm", c.getWidthMm());
          if (c.getHeightMm() != null) input.put("heightMm", c.getHeightMm());
          if (c.getDepthMm() != null) input.put("depthMm", c.getDepthMm());
          if (c.getNumericValue() != null) input.put("legacy", c.getNumericValue());
          if (c.getTextValue() != null) input.put("legacy", c.getTextValue());
          selected.add(
              new Variant(
                  id,
                  items.stream()
                      .map(ProductComponent::getValue)
                      .filter(Objects::nonNull)
                      .map(ProductAttributeValue::getId)
                      .toList(),
                  input));
        });
    return selected;
  }

  public ProductView detail(Long id) {
    return view(require(id), true);
  }

  public String status(ProductMaster p) {
    if (p.getStatus() == ProductMasterStatus.DRAFT) return "DRAFT";
    if (p.getStatus() == ProductMasterStatus.DISCONTINUED) return "DISCONTINUED";
    return p.getCurrentStock() <= 0 ? "OUT_OF_STOCK" : "ACTIVE";
  }

  private ProductView view(ProductMaster p, boolean detailed) {
    List<Variant> v = variants(p);
    return new ProductView(
        p.getId(),
        p.getRowVersion(),
        p.getProductName(),
        p.getProductCode(),
        p.getCatalogCode(),
        p.getQrPublicToken(),
        p.isNonStandard(),
        status(p),
        p.getStatus() == ProductMasterStatus.DRAFT ? "등록중" : "등록완료",
        p.getCurrentStock(),
        p.getDescription(),
        v,
        json.list(p.getNameTokensJson(), NameToken.class),
        detailed && p.getStudioProcessJson() != null
            ? json.read(p.getStudioProcessJson(), Process.class)
            : null,
        detailed ? assets.owned("PRODUCT", p.getId()) : List.of(),
        detailed ? assets.owned("PROCESS", p.getId()) : List.of(),
        detailed
            ? v.stream().map(x -> attributes.view(attributes.require(x.groupId()))).toList()
            : List.of(),
        p.getStudioDefinitionJson() == null);
  }

  public ProductPage search(ProductFilter filter) {
    if (filter == null) throw new IllegalArgumentException("검색 조건이 필요합니다.");
    int size = filter.size() == 0 ? 50 : filter.size();
    if (!Set.of(20, 50, 100, 200).contains(size))
      throw new IllegalArgumentException("페이지 크기는 20·50·100·200 중 선택해 주세요.");
    if (text(filter.keyword()).length() > 160
        || map(filter.options()).size() > 40
        || list(filter.customGroups()).size() > 40)
      throw new IllegalArgumentException("검색 조건이 너무 많거나 깁니다.");
    Specification<ProductMaster> spec =
        (root, query, b) -> {
          List<jakarta.persistence.criteria.Predicate> where = new ArrayList<>();
          where.add(b.equal(root.get("nonStandard"), filter.nonStandard()));
          if (!text(filter.keyword()).isBlank())
            where.add(
                b.like(
                    b.lower(root.get("productName")),
                    "%"
                        + filter
                            .keyword()
                            .trim()
                            .toLowerCase(Locale.ROOT)
                            .replace("\\", "\\\\")
                            .replace("%", "\\%")
                            .replace("_", "\\_")
                        + "%",
                    '\\'));
          if (!text(filter.status()).isBlank())
            switch (filter.status()) {
              case "DRAFT" -> where.add(b.equal(root.get("status"), ProductMasterStatus.DRAFT));
              case "DISCONTINUED" ->
                  where.add(b.equal(root.get("status"), ProductMasterStatus.DISCONTINUED));
              case "ACTIVE", "OUT_OF_STOCK" -> {
                where.add(b.equal(root.get("status"), ProductMasterStatus.ACTIVE));
                where.add(
                    filter.status().equals("ACTIVE")
                        ? b.gt(root.get("currentStock"), 0)
                        : b.le(root.get("currentStock"), 0));
              }
              default -> throw new IllegalArgumentException("제품 상태 검색값을 확인해 주세요.");
            }
          for (var entry : map(filter.options()).entrySet())
            if (!list(entry.getValue()).isEmpty()) {
              if (entry.getValue().size() > 200)
                throw new IllegalArgumentException("그룹별 옵션 검색은 최대 200개입니다.");
              var sub = query.subquery(Long.class);
              var c = sub.from(ProductComponent.class);
              sub.select(c.get("id"));
              sub.where(
                  b.equal(c.get("product").get("id"), root.get("id")),
                  b.equal(c.get("group").get("id"), entry.getKey()),
                  c.get("value").get("id").in(entry.getValue()));
              where.add(b.exists(sub));
            }
          if (list(filter.inputs()).size() > 80)
            throw new IllegalArgumentException("입력 필터는 최대 80개입니다.");
          for (InputFilter input : list(filter.inputs())) {
            if (input == null
                || input.groupId() == null
                || text(input.fieldKey()).isBlank()
                || text(input.contains()).length() > 500)
              throw new IllegalArgumentException("입력 검색 조건을 확인해 주세요.");
            if (input.min() != null
                && input.max() != null
                && input.min().compareTo(input.max()) > 0)
              throw new IllegalArgumentException("검색 최소값은 최대값보다 클 수 없습니다.");
            if (input.min() == null && input.max() == null && text(input.contains()).isBlank())
              continue;
            var sub = query.subquery(Long.class);
            var row = sub.from(ProductStudioInput.class);
            sub.select(row.get("id"));
            List<jakarta.persistence.criteria.Predicate> constraints = new ArrayList<>();
            constraints.add(b.equal(row.get("product").get("id"), root.get("id")));
            constraints.add(b.equal(row.get("group").get("id"), input.groupId()));
            constraints.add(b.equal(row.get("fieldKey"), input.fieldKey()));
            if (input.min() != null) constraints.add(b.ge(row.get("numberValue"), input.min()));
            if (input.max() != null) constraints.add(b.le(row.get("numberValue"), input.max()));
            if (!text(input.contains()).isBlank())
              constraints.add(
                  b.like(
                      b.lower(row.get("textValue")),
                      "%"
                          + input
                              .contains()
                              .toLowerCase(Locale.ROOT)
                              .replace("\\", "\\\\")
                              .replace("%", "\\%")
                              .replace("_", "\\_")
                          + "%",
                      '\\'));
            sub.where(constraints.toArray(jakarta.persistence.criteria.Predicate[]::new));
            where.add(b.exists(sub));
          }
          for (Long group : list(filter.customGroups())) {
            var sub = query.subquery(Long.class);
            var c = sub.from(ProductComponent.class);
            sub.select(c.get("id"));
            sub.where(
                b.equal(c.get("product").get("id"), root.get("id")),
                b.equal(c.get("group").get("id"), group),
                b.isTrue(c.get("group").get("nonStandard")));
            where.add(b.exists(sub));
          }
          return b.and(where.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    int page = Math.max(0, filter.page());
    var result =
        products.findAll(spec, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id")));
    if (page > 0 && result.getTotalPages() <= page) {
      page = Math.max(0, result.getTotalPages() - 1);
      result =
          products.findAll(spec, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id")));
    }
    return new ProductPage(
        result.getContent().stream().map(p -> view(p, false)).toList(),
        result.getTotalElements(),
        result.getTotalPages(),
        page,
        size);
  }

  @Transactional
  public ProductView update(Long id, ProductEdit edit, String actor) {
    ProductMaster p =
        products.findForUpdate(id).orElseThrow(() -> new NoSuchElementException("제품을 찾을 수 없습니다."));
    ProductStudioAttributeService.version(edit.version(), p.getRowVersion());
    if (p.getStudioDefinitionJson() == null)
      throw new IllegalStateException("기존 제품은 기존 상세 화면에서 수정해 주세요. 코드와 재고 이력을 보존합니다.");
    if (!Set.of("ACTIVE", "OUT_OF_STOCK", "DISCONTINUED", "DRAFT").contains(text(edit.status())))
      throw new IllegalArgumentException("제품 상태를 확인해 주세요.");
    validateName(edit.productName());
    if (text(edit.description()).length() > 1000)
      throw new IllegalArgumentException("제품 설명은 최대 1,000자입니다.");
    List<Variant> selected = validateVariants(edit.variants());
    boolean custom =
        selected.stream().anyMatch(v -> attributes.require(v.groupId()).isNonStandard());
    if (custom != p.isNonStandard())
      throw new IllegalArgumentException("규격·비규격 제품 유형을 변경할 수 없습니다.");
    String hash = identity(selected);
    ProductMaster duplicate = duplicates(List.of(hash)).get(hash);
    if (duplicate != null && !duplicate.getId().equals(id))
      throw new IllegalStateException(
          "동일한 제품이 있습니다: " + duplicate.getProductName() + " (#" + duplicate.getId() + ")");
    if (!hash.equals(p.getStudioIdentity())) {
      // Inventory remains tied to the same product ID; record a complete before/after specification
      // audit.
      ProductStudioAudit audit = new ProductStudioAudit();
      audit.setProduct(p);
      audit.setBeforeJson(p.getStudioDefinitionJson());
      audit.setAfterJson(json.write(selected));
      audit.setActor(actor);
      audit.setCreatedAt(LocalDateTime.now());
      entityManager.persist(audit);
      p.getComponents().clear();
      p.getStudioInputs().clear();
      products.flush();
      addComponents(p, selected);
      p.setStudioIdentity(hash);
      p.setProductCode("PM2|" + hash);
      p.setConfigurationHash(codes.configurationHash(p.getProductCode()));
    }
    List<ProductAttributeGroup> groups =
        selected.stream().map(v -> attributes.require(v.groupId())).toList();
    List<NameToken> tokens = validateTokens(edit.nameTokens(), groups);
    boolean processChanged =
        !new HashSet<>(variants(p).stream().map(Variant::groupId).toList())
            .equals(new HashSet<>(selected.stream().map(Variant::groupId).toList()));
    Process previousProcess = readProcess(p);
    p.setStudioDefinitionJson(json.write(selected));
    p.setNameTokensJson(json.write(tokens));
    p.setProductName(edit.productName().trim());
    p.setDescription(text(edit.description()));
    if (!custom) p.setStudioProcessJson(json.write(defaultProcess(selected)));
    else {
      Map<Long, Question> previousByGroup =
          previousProcess.questions().stream().collect(Collectors.toMap(Question::groupId, q -> q));
      List<Question> reordered =
          defaultProcess(selected).questions().stream()
              .map(q -> q.fixed() ? q : previousByGroup.getOrDefault(q.groupId(), q))
              .toList();
      p.setStudioProcessJson(json.write(new Process(2, reordered, previousProcess.rules())));
    }
    if (custom && processChanged) p.setStatus(ProductMasterStatus.DRAFT);
    else if ("DISCONTINUED".equals(edit.status())) p.setStatus(ProductMasterStatus.DISCONTINUED);
    else if (p.getStatus() != ProductMasterStatus.DRAFT) {
      if (custom) {
        Validation check = validateProcess(p, readProcess(p));
        if (!check.valid()) throw new IllegalArgumentException("질문 프로세스 검증 후 사용중으로 변경해 주세요.");
      }
      p.setStatus(ProductMasterStatus.ACTIVE);
    }
    bindInputFiles(selected, actor);
    p.setUpdatedBy(actor);
    p.setUpdatedAt(LocalDateTime.now());
    assets.attach("PRODUCT", id, edit.assetIds(), actor);
    products.flush();
    return view(p, true);
  }

  private List<Variant> validateVariants(List<Variant> selected) {
    if (selected == null) throw new IllegalArgumentException("제품 사양이 필요합니다.");
    // Generation deliberately uses one option per axis. Detail editing also permits a checkbox
    // group's exact set.
    List<GroupSelection> singles = new ArrayList<>();
    Set<Long> seen = new HashSet<>();
    for (Variant v : selected) {
      if (v == null || !seen.add(v.groupId())) throw new IllegalArgumentException("중복된 그룹입니다.");
      ProductAttributeGroup g = attributes.require(v.groupId());
      if (list(v.valueIds()).size() > 1 && attributes.control(g) != Control.CHECKBOX)
        throw new IllegalArgumentException("하나선택형은 옵션을 하나만 선택합니다.");
      if (ProductStudioAttributeService.BASE.contains(g.getSystemRole().name())
          && list(v.valueIds()).size() != 1)
        throw new IllegalArgumentException("기본그룹은 옵션을 하나씩 선택합니다.");
      singles.add(new GroupSelection(v.groupId(), v.valueIds(), v.inputs()));
    }
    plan(new GenerateRequest(singles, null, 0, 1));
    List<Variant> normalized = new ArrayList<>();
    for (Variant v : selected) {
      ProductAttributeGroup g = attributes.require(v.groupId());
      Map<String, Object> input =
          g.isNonStandard()
              ? Map.of()
              : choice(attributes.control(g))
                  ? normalizeDimension(
                      g.getValues().stream()
                          .filter(x -> list(v.valueIds()).contains(x.getId()))
                          .findFirst()
                          .orElseThrow(),
                      v.inputs())
                  : normalizeInputs(g, v.inputs());
      normalized.add(new Variant(v.groupId(), list(v.valueIds()), input));
    }
    return normalized;
  }

  public Process defaultProcess(List<Variant> selected) {
    List<Question> questions = new ArrayList<>();
    for (Variant v : selected) {
      ProductAttributeGroup g = attributes.require(v.groupId());
      Control control = attributes.control(g);
      boolean fixed = !g.isNonStandard();
      List<Choice> choices =
          fixed && choice(control)
              ? g.getValues().stream()
                  .filter(x -> list(v.valueIds()).contains(x.getId()))
                  .map(
                      x ->
                          new Choice(
                              x.getValueCode(),
                              dimensionLabels(attributes.labelsOf(x), v.inputs()),
                              attributes.namePart(x),
                              List.of()))
                  .toList()
              : List.of();
      questions.add(
          new Question(
              g.getGroupCode(),
              g.getId(),
              attributes.labelsOf(g),
              control,
              fixed,
              true,
              true,
              false,
              g.getQuestionText(),
              g.getCustomerGuide(),
              choices,
              g.isNonStandard() ? List.of() : attributes.fieldsFor(g),
              List.of()));
    }
    return new Process(2, questions, List.of());
  }

  private Process readProcess(ProductMaster p) {
    return p.getStudioProcessJson() == null
        ? defaultProcess(variants(p))
        : json.read(p.getStudioProcessJson(), Process.class);
  }

  public Map<String, Answer> fixedAnswers(ProductMaster p) {
    Map<String, Answer> fixed = new LinkedHashMap<>();
    for (Variant v : variants(p)) {
      ProductAttributeGroup g = attributes.require(v.groupId());
      if (g.isNonStandard()) continue;
      fixed.put(
          g.getGroupCode(),
          new Answer(
              g.getValues().stream()
                  .filter(x -> list(v.valueIds()).contains(x.getId()))
                  .map(ProductAttributeValue::getValueCode)
                  .toList(),
              map(v.inputs())));
    }
    return fixed;
  }

  private Process normalizeProcess(ProductMaster p, Process process) {
    if (process == null || list(process.questions()).size() != variants(p).size())
      throw new IllegalArgumentException("제품에 등록된 모든 그룹을 한 번씩 포함해야 합니다.");
    Map<Long, Question> expected =
        defaultProcess(variants(p)).questions().stream()
            .collect(Collectors.toMap(Question::groupId, q -> q));
    Set<Long> seen = new HashSet<>();
    List<Question> normalized = new ArrayList<>();
    for (Question q : process.questions()) {
      if (q == null || !seen.add(q.groupId()) || !expected.containsKey(q.groupId()))
        throw new IllegalArgumentException("프로세스에 다른 그룹 또는 중복 그룹이 있습니다.");
      Question original = expected.get(q.groupId());
      if (!original.key().equals(q.key()) || original.control() != q.control())
        throw new IllegalArgumentException("그룹 value와 입력 유형을 변경할 수 없습니다.");
      if (original.fixed()) normalized.add(original);
      else
        normalized.add(
            new Question(
                original.key(),
                q.groupId(),
                q.labels(),
                q.control(),
                false,
                q.visible(),
                q.required(),
                q.requireRule(),
                q.question(),
                q.guide(),
                q.choices(),
                q.fields(),
                q.assetIds()));
    }
    return new Process(process.schemaVersion(), normalized, list(process.rules()));
  }

  public Validation validateProcess(Long id, Process process) {
    return validateProcess(require(id), process);
  }

  private Validation validateProcess(ProductMaster p, Process process) {
    try {
      return ProductStudioEngine.validate(normalizeProcess(p, process), fixedAnswers(p));
    } catch (IllegalArgumentException e) {
      return new Validation(
          false, false, 0, List.of(new Issue("ERROR", "PRODUCT", "프로세스", e.getMessage())));
    }
  }

  @Transactional
  public ProductView saveProcess(Long id, ProcessSave save, String actor) {
    ProductMaster p =
        products.findForUpdate(id).orElseThrow(() -> new NoSuchElementException("제품을 찾을 수 없습니다."));
    ProductStudioAttributeService.version(save.version(), p.getRowVersion());
    if (!p.isNonStandard() || p.getStudioDefinitionJson() == null)
      throw new IllegalArgumentException("신규 비규격 제품에서만 프로세스를 편집합니다.");
    Process normalized = normalizeProcess(p, save.process());
    if (save.publish()) {
      Validation validation = validateProcess(p, normalized);
      if (!validation.valid() || !validation.exhaustive())
        throw new IllegalArgumentException(
            validation.issues().stream()
                .filter(i -> i.severity().equals("ERROR"))
                .map(Issue::message)
                .limit(10)
                .collect(Collectors.joining(" / ")));
    } else {
      // Drafts may have empty options/fields, but cannot exceed structural payload limits.
      if (normalized.schemaVersion() != 2
          || list(normalized.rules()).size() > MAX_RULES
          || normalized.questions().stream()
              .anyMatch(q -> list(q.choices()).size() > 200 || list(q.fields()).size() > 20))
        throw new IllegalArgumentException("질문/보기/규칙 개수 또는 버전을 확인해 주세요.");
      for (Question q : normalized.questions()) {
        labels(q.labels(), 80);
        fields(q.control(), q.fields(), true);
        if (text(q.question()).length() > 300 || text(q.guide()).length() > 1000)
          throw new IllegalArgumentException("질문 또는 안내 문구가 너무 깁니다.");
        Set<String> keys = new HashSet<>();
        for (Choice c : list(q.choices())) {
          if (c == null
              || c.key() == null
              || !c.key().matches("[A-Za-z0-9_-]{1,80}")
              || !keys.add(c.key()))
            throw new IllegalArgumentException("보기 value가 중복되거나 올바르지 않습니다.");
          labels(c.labels(), 120);
          if (text(c.namePart()).length() > 160)
            throw new IllegalArgumentException("보기 제품명 문자는 최대 160자입니다.");
        }
      }
      for (Rule r : normalized.rules())
        if (r == null
            || list(r.conditions()).size() > 20
            || list(r.actions()).size() > 40
            || list(r.conditions()).stream().anyMatch(Objects::isNull)
            || list(r.actions()).stream().anyMatch(Objects::isNull))
          throw new IllegalArgumentException("커스텀 조건과 결과 형식을 확인해 주세요.");
    }
    Set<String> allFiles = new LinkedHashSet<>();
    for (Question q : normalized.questions()) {
      allFiles.addAll(list(q.assetIds()));
      for (Choice c : list(q.choices())) allFiles.addAll(list(c.assetIds()));
    }
    assets.attach("PROCESS", id, new ArrayList<>(allFiles), actor);
    // Question order in the product and chatbot is the saved process order; identity ignores it.
    Map<Long, Variant> byGroup =
        variants(p).stream().collect(Collectors.toMap(Variant::groupId, v -> v));
    List<Variant> ordered =
        normalized.questions().stream().map(q -> byGroup.get(q.groupId())).toList();
    p.setStudioDefinitionJson(json.write(ordered));
    p.setStudioProcessJson(json.write(normalized));
    p.setStatus(save.publish() ? ProductMasterStatus.ACTIVE : ProductMasterStatus.DRAFT);
    p.setUpdatedBy(actor);
    p.setUpdatedAt(LocalDateTime.now());
    products.flush();
    return view(p, true);
  }

  public Evaluation evaluate(Long id, EvaluateRequest request, String actor) {
    ProductMaster p = require(id);
    Process process =
        normalizeProcess(p, request.process() == null ? readProcess(p) : request.process());
    Map<String, Answer> answers = new LinkedHashMap<>(map(request.answers()));
    answers.putAll(fixedAnswers(p));
    Evaluation evaluated = ProductStudioEngine.evaluate(process, answers);
    assets.validateAnswers(process, evaluated.answers(), actor);
    return evaluated;
  }

  public ProductMaster publicProduct(String token) {
    ProductMaster p =
        products
            .findByQrPublicToken(token)
            .filter(
                x ->
                    x.getStatus() == ProductMasterStatus.ACTIVE
                        && x.getStudioDefinitionJson() != null)
            .orElseThrow(() -> new NoSuchElementException("공개된 제품을 찾을 수 없습니다."));
    if (variants(p).stream()
        .anyMatch(
            v -> {
              ProductAttributeGroup g = attributes.require(v.groupId());
              return !g.isActive()
                  || g.getValues().stream()
                      .anyMatch(x -> list(v.valueIds()).contains(x.getId()) && !x.isActive());
            })) throw new NoSuchElementException("현재 선택할 수 없는 제품입니다.");
    return p;
  }

  public PublicProduct publicSchema(String token) {
    ProductMaster p = publicProduct(token);
    Process process = normalizeProcess(p, readProcess(p));
    return new PublicProduct(
        p.getProductName(),
        p.getCatalogCode(),
        status(p),
        assets.publicViews(allProductAssets(p), token),
        assets.owned("PRODUCT", p.getId()).stream().map(AssetView::id).toList(),
        customerProcess(process));
  }

  private Process customerProcess(Process process) {
    List<Question> safe = new ArrayList<>();
    for (Question q : process.questions()) {
      GroupView g = attributes.view(attributes.require(q.groupId()));
      List<String> questionAssets = new ArrayList<>(list(q.assetIds()));
      questionAssets.addAll(g.assets().stream().map(AssetView::id).toList());
      List<Choice> choices = new ArrayList<>();
      for (Choice c : list(q.choices())) {
        List<String> ids = new ArrayList<>(list(c.assetIds()));
        g.values().stream()
            .filter(v -> v.key().equals(c.key()))
            .findFirst()
            .ifPresent(v -> ids.addAll(v.assets().stream().map(AssetView::id).toList()));
        choices.add(
            new Choice(c.key(), customer(c.labels()), null, ids.stream().distinct().toList()));
      }
      safe.add(
          new Question(
              q.key(),
              q.groupId(),
              customer(q.labels()),
              q.control(),
              q.fixed(),
              q.visible(),
              q.required(),
              q.requireRule(),
              q.question(),
              q.guide(),
              choices,
              list(q.fields()).stream()
                  .map(
                      f ->
                          new Field(
                              f.key(),
                              customer(f.labels()),
                              null,
                              f.required(),
                              f.allowNegative(),
                              f.min(),
                              f.max(),
                              f.step(),
                              f.minLength(),
                              f.maxLength(),
                              f.format(),
                              f.unit(),
                              f.extensions(),
                              f.minFiles(),
                              f.maxFiles(),
                              f.maxFileMB()))
                  .toList(),
              questionAssets.stream().distinct().toList()));
    }
    return new Process(process.schemaVersion(), safe, List.of());
  }

  private Labels customer(Labels labels) {
    return new Labels(labels.customer(), labels.customer(), labels.customer());
  }

  public Evaluation evaluatePublic(String token, Map<String, Answer> submitted, String actor) {
    ProductMaster p = publicProduct(token);
    Evaluation result = evaluate(p.getId(), new EvaluateRequest(null, submitted), actor);
    Map<String, Question> safe =
        customerProcess(normalizeProcess(p, readProcess(p))).questions().stream()
            .collect(Collectors.toMap(Question::key, q -> q));
    return new Evaluation(
        result.questions().stream()
            .map(
                s ->
                    new QuestionState(
                        safe.get(s.question().key()),
                        s.visible(),
                        s.required(),
                        s.allowed(),
                        s.answer(),
                        s.errors(),
                        List.of()))
            .toList(),
        result.answers(),
        List.of(),
        result.complete());
  }

  public List<AssetView> allProductAssets(ProductMaster p) {
    List<AssetView> files = new ArrayList<>(assets.owned("PRODUCT", p.getId()));
    files.addAll(assets.owned("PROCESS", p.getId()));
    for (Variant v : variants(p)) {
      GroupView g = attributes.view(attributes.require(v.groupId()));
      files.addAll(g.assets());
      for (ValueView x : g.values())
        if (list(v.valueIds()).contains(x.id())) files.addAll(x.assets());
    }
    for (Variant v : variants(p))
      if (attributes.control(attributes.require(v.groupId())) == Control.FILE)
        for (Object value : map(v.inputs()).values())
          if (value instanceof List<?> ids)
            for (Object id : ids) files.add(assets.view(assets.require(String.valueOf(id))));
    return files.stream()
        .collect(Collectors.toMap(AssetView::id, x -> x, (a, b) -> a, LinkedHashMap::new))
        .values()
        .stream()
        .toList();
  }

  private void bindInputFiles(List<Variant> selected, String actor) {
    for (Variant v : selected) {
      ProductAttributeGroup g = attributes.require(v.groupId());
      if (!g.isNonStandard() && attributes.control(g) == Control.FILE)
        for (Field field : attributes.fieldsFor(g)) {
          Object raw = map(v.inputs()).get(field.key());
          if (raw == null) continue;
          if (!(raw instanceof List<?> ids))
            throw new IllegalArgumentException("고정 파일 입력을 확인해 주세요.");
          List<String> names = ids.stream().map(String::valueOf).toList();
          for (String id : names) {
            ProductStudioAsset a = assets.require(id);
            String name = a.getOriginalName();
            String ext = name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
            if (!field.extensions().contains(ext)
                || a.getFileSize() > field.maxFileMB() * 1024L * 1024L)
              throw new IllegalArgumentException("고정 입력 파일의 형식 또는 용량을 확인해 주세요.");
          }
          assets.bindFixedInputs(names, actor);
        }
    }
  }

  public ProductStudioAsset publicAsset(String token, String id) {
    ProductMaster p = publicProduct(token);
    if (allProductAssets(p).stream().noneMatch(a -> a.id().equals(id)))
      throw new NoSuchElementException("이 제품의 첨부파일이 아닙니다.");
    return assets.require(id);
  }

  public void validateAttachmentOwner(String type, Long id) {
    switch (type) {
      case "PRODUCT", "PROCESS" -> require(id);
      case "GROUP" -> attributes.require(id);
      case "VALUE" -> {
        if (!values.existsById(id)) throw new NoSuchElementException("보기를 찾을 수 없습니다.");
      }
      default -> throw new IllegalArgumentException("첨부 대상을 확인해 주세요.");
    }
  }

  public CatalogResult catalog(Map<Long, String> selections, int page) {
    if (map(selections).size() > MAX_QUESTIONS)
      throw new IllegalArgumentException("선택 조건이 너무 많습니다.");
    List<ProductMaster> candidates =
        products.findAll(
            (Specification<ProductMaster>)
                (r, q, b) ->
                    b.and(
                        b.equal(r.get("status"), ProductMasterStatus.ACTIVE),
                        b.isNotNull(r.get("studioDefinitionJson"))),
            Sort.by("id"));
    Map<Long, List<Variant>> byProduct = new LinkedHashMap<>();
    for (ProductMaster p : candidates) {
      List<Variant> selected = variants(p);
      boolean valid = true;
      for (Variant v : selected) {
        ProductAttributeGroup g = attributes.require(v.groupId());
        if (!g.isActive()
            || g.getValues().stream()
                .anyMatch(x -> list(v.valueIds()).contains(x.getId()) && !x.isActive())) {
          valid = false;
          break;
        }
      }
      for (var pick : map(selections).entrySet()) {
        Variant v =
            selected.stream()
                .filter(x -> x.groupId().equals(pick.getKey()))
                .findFirst()
                .orElse(null);
        if ("0".equals(pick.getValue())) {
          if (v != null) valid = false;
        } else if (text(pick.getValue()).startsWith("I:")) {
          if (v == null
              || !("I:" + codes.configurationHash(json.write(canonical(v.inputs()))))
                  .equals(pick.getValue())) valid = false;
        } else if (v == null
            || list(v.valueIds()).stream().noneMatch(id -> id.toString().equals(pick.getValue())))
          valid = false;
      }
      if (valid) byProduct.put(p.getId(), selected);
    }
    candidates = candidates.stream().filter(p -> byProduct.containsKey(p.getId())).toList();
    CatalogStep next = null;
    if (!candidates.isEmpty()) {
      Long nextGroup = null;
      for (Variant v : byProduct.get(candidates.get(0).getId()))
        if (!map(selections).containsKey(v.groupId())
            && (!v.valueIds().isEmpty() || !map(v.inputs()).isEmpty())) {
          nextGroup = v.groupId();
          break;
        }
      if (nextGroup != null) {
        final Long groupId = nextGroup;
        ProductAttributeGroup g = attributes.require(groupId);
        GroupView gv = attributes.view(g);
        Set<Long> options = new LinkedHashSet<>();
        boolean absent = false;
        for (ProductMaster p : candidates) {
          Variant v =
              byProduct.get(p.getId()).stream()
                  .filter(x -> groupId.equals(x.groupId()))
                  .findFirst()
                  .orElse(null);
          if (v == null) absent = true;
          else options.addAll(v.valueIds());
        }
        String imageToken = candidates.get(0).getQrPublicToken();
        List<CatalogOption> items = new ArrayList<>();
        for (ValueView v : gv.values())
          if (options.contains(v.id())) {
            String token =
                candidates.stream()
                    .filter(
                        p ->
                            byProduct.get(p.getId()).stream()
                                .anyMatch(
                                    x ->
                                        groupId.equals(x.groupId())
                                            && x.valueIds().contains(v.id())))
                    .findFirst()
                    .orElseThrow()
                    .getQrPublicToken();
            items.add(
                new CatalogOption(
                    v.id().toString(),
                    v.labels().customer(),
                    assets.publicViews(v.assets(), token)));
          }
        if (!choice(gv.control())) {
          Set<String> inputKeys = new HashSet<>();
          for (ProductMaster p : candidates)
            for (Variant v : byProduct.get(p.getId()))
              if (v.groupId().equals(groupId)) {
                String key = "I:" + codes.configurationHash(json.write(canonical(v.inputs())));
                if (inputKeys.add(key))
                  items.add(
                      new CatalogOption(
                          key,
                          gv.fields().stream()
                              .filter(f -> map(v.inputs()).containsKey(f.key()))
                              .map(
                                  f ->
                                      f.labels().customer()
                                          + ": "
                                          + (gv.control() == Control.FILE
                                              ? "첨부파일 "
                                                  + ((List<?>) v.inputs().get(f.key())).size()
                                                  + "개"
                                              : display(v.inputs().get(f.key()))))
                              .collect(Collectors.joining(" / ")),
                          List.of()));
              }
        }
        if (absent) items.add(new CatalogOption("0", "이 항목이 없는 제품", List.of()));
        next =
            new CatalogStep(
                groupId, g.getCustomerLabel(), items, assets.publicViews(gv.assets(), imageToken));
      }
    }
    int totalPages = (candidates.size() + 199) / 200,
        safePage = Math.max(0, Math.min(page, Math.max(0, totalPages - 1)));
    List<CatalogItem> result =
        next == null
            ? candidates.stream()
                .skip(safePage * 200L)
                .limit(200)
                .map(
                    p ->
                        new CatalogItem(
                            p.getProductName(),
                            p.getCatalogCode(),
                            p.getQrPublicToken(),
                            p.isNonStandard(),
                            status(p)))
                .toList()
            : List.of();
    return new CatalogResult(next, candidates.size(), result, safePage, totalPages);
  }
}
