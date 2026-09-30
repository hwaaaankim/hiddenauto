package com.dev.HiddenBATHAuto.service.productmaster;

import static com.dev.HiddenBATHAuto.dto.productmaster.ProductStudioCustomerDtos.*;
import static com.dev.HiddenBATHAuto.dto.productmaster.ProductStudioDtos.*;
import static com.dev.HiddenBATHAuto.service.productmaster.ProductStudioEngine.*;

import com.dev.HiddenBATHAuto.enums.productmaster.ProductMasterStatus;
import com.dev.HiddenBATHAuto.model.productmaster.ProductMaster;
import com.dev.HiddenBATHAuto.repository.productmaster.ProductMasterRepository;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductStudioCustomerService {
  private final ProductMasterRepository products;
  private final ProductStudioService studio;
  private final ProductStudioAttributeService attributes;
  private final ProductStudioAssetService assets;
  private final ProductMasterCodeService codes;
  private final ProductStudioJson json;

  public BrowseResult browse(BrowseRequest request, boolean admin) {
    if (request == null) throw new IllegalArgumentException("제품 선택 정보가 필요합니다.");
    Map<Long, String> selected = map(request.selections());
    Map<Long, List<String>> filters = map(request.filters());
    if (selected.size() > 3 || filters.size() > MAX_QUESTIONS
        || filters.values().stream().anyMatch(x -> x == null || x.size() > 200
            || x.stream().anyMatch(v -> v == null || v.length() > 100)))
      throw new IllegalArgumentException("선택 조건의 개수 또는 값을 확인해 주세요.");
    int size = request.size() == 0 ? 12 : request.size();
    if (!Set.of(12, 24, 48).contains(size))
      throw new IllegalArgumentException("제품 카드는 12·24·48개씩 조회할 수 있습니다.");
    if (!admin && request.productId() != null)
      throw new IllegalArgumentException("공개 제품은 공개 코드로 선택해 주세요.");
    if (request.productId() != null && !text(request.token()).isBlank())
      throw new IllegalArgumentException("제품 선택 범위를 하나만 지정해 주세요.");

    List<GroupView> groups = attributes.catalog();
    Map<Long, GroupView> byGroup = groups.stream()
        .collect(Collectors.toMap(GroupView::id, g -> g, (a, b) -> a, LinkedHashMap::new));
    List<GroupView> basics = new ArrayList<>();
    for (String role : List.of("CATEGORY", "SUBCATEGORY", "SERIES")) {
      List<GroupView> matching = groups.stream().filter(g -> role.equals(g.role())).toList();
      if (matching.size() != 1)
        throw new IllegalStateException("필수 분류 그룹을 확인해 주세요: " + role);
      GroupView g = matching.get(0);
      if (!"SUBCATEGORY".equals(role) || g.askQuestion()) basics.add(g);
    }
    Set<Long> basicIds = basics.stream().map(GroupView::id).collect(Collectors.toSet());
    if (selected.keySet().stream().anyMatch(id -> !basicIds.contains(id)))
      throw new IllegalArgumentException("고객에게 표시하는 기본 분류만 선택할 수 있습니다.");
    // A forged request cannot skip category/series or submit options before its preceding question.
    boolean missing = false;
    for (GroupView g : basics) {
      if (!selected.containsKey(g.id())) missing = true;
      else {
        String value = selected.get(g.id());
        if (missing || value == null || g.values().stream()
            .noneMatch(v -> v.active() && v.id().toString().equals(value)))
          throw new IllegalArgumentException("대분류부터 순서대로 올바른 분류를 선택해 주세요.");
      }
    }
    if (missing && filters.values().stream().anyMatch(x -> !x.isEmpty()))
      throw new IllegalArgumentException("기본 분류를 먼저 선택해 주세요.");
    if (request.nonStandard() && filters.values().stream().anyMatch(x -> !x.isEmpty()))
      throw new IllegalArgumentException("비규격 옵션은 제품의 질문 프로세스에서 선택해 주세요.");

    List<ProductMaster> candidates;
    if (request.productId() != null || !text(request.token()).isBlank()) {
      ProductMaster p = request.productId() != null
          ? studio.require(request.productId()) : studio.publicProduct(request.token());
      if (p.isNonStandard() != request.nonStandard())
        throw new IllegalArgumentException("선택한 제품의 규격 구분이 다릅니다.");
      candidates = List.of(p);
    } else {
      candidates = products.findAll((Specification<ProductMaster>) (r, q, b) -> b.and(
          b.equal(r.get("nonStandard"), request.nonStandard()),
          admin ? b.notEqual(r.get("status"), ProductMasterStatus.DISCONTINUED)
              : b.equal(r.get("status"), ProductMasterStatus.ACTIVE),
          b.isNotNull(r.get("studioDefinitionJson"))), Sort.by("id"));
    }
    Map<Long, List<Variant>> definitions = new LinkedHashMap<>();
    List<ProductMaster> scope = new ArrayList<>();
    for (ProductMaster p : candidates) {
      if (p.getStudioDefinitionJson() == null || p.getStatus() == ProductMasterStatus.DISCONTINUED
          || !admin && p.getStatus() != ProductMasterStatus.ACTIVE) continue;
      List<Variant> variants = studio.variants(p);
      if (!available(variants, byGroup)) continue;
      if (selected.entrySet().stream().allMatch(pick -> variants.stream().anyMatch(v ->
          v.groupId().equals(pick.getKey()) && list(v.valueIds()).stream()
              .anyMatch(id -> id.toString().equals(pick.getValue()))))) {
        definitions.put(p.getId(), variants);
        scope.add(p);
      }
    }
    if (missing) {
      GroupView g = basics.stream().filter(x -> !selected.containsKey(x.id())).findFirst().orElseThrow();
      return new BrowseResult(facet(g, scope, definitions, admin), List.of(), List.of(),
          scope.size(), scope.size(), 0, 0);
    }

    List<BrowseGroup> facets = new ArrayList<>();
    if (!request.nonStandard()) {
      Set<Long> used = definitions.values().stream().flatMap(List::stream).map(Variant::groupId)
          .collect(Collectors.toSet());
      for (GroupView g : groups)
        if (!ProductStudioAttributeService.BASE.contains(g.role()) && used.contains(g.id()))
          facets.add(facet(g, scope, definitions, admin));
    }
    Map<Long, Set<String>> allowed = facets.stream().collect(Collectors.toMap(BrowseGroup::groupId,
        g -> g.options().stream().map(CatalogOption::key).collect(Collectors.toSet())));
    for (var filter : filters.entrySet()) {
      if (filter.getValue().isEmpty()) continue;
      if (!allowed.containsKey(filter.getKey()) || !allowed.get(filter.getKey()).containsAll(filter.getValue()))
        throw new IllegalArgumentException("이 분류에서 선택할 수 없는 옵션이 있습니다. 선택 조건을 초기화해 주세요.");
    }
    List<ProductMaster> found = scope.stream().filter(p -> filters.entrySet().stream().allMatch(f -> {
      if (f.getValue().isEmpty()) return true;
      Variant v = variant(definitions.get(p.getId()), f.getKey());
      return v != null && optionKeys(v).containsAll(f.getValue());
    })).toList();
    int pages = (found.size() + size - 1) / size;
    int page = Math.max(0, Math.min(request.page(), Math.max(0, pages - 1)));
    List<ProductCard> cards = found.stream().skip((long) page * size).limit(size)
        .map(p -> card(p, definitions.get(p.getId()), byGroup, admin)).toList();
    return new BrowseResult(null, facets, cards, found.size(), scope.size(), page, pages);
  }

  private boolean available(List<Variant> variants, Map<Long, GroupView> groups) {
    for (String role : ProductStudioAttributeService.BASE) {
      long count = variants.stream().filter(v -> groups.containsKey(v.groupId())
          && role.equals(groups.get(v.groupId()).role()) && list(v.valueIds()).size() == 1).count();
      if (count != 1) return false;
    }
    for (Variant v : variants) {
      GroupView g = groups.get(v.groupId());
      if (g == null || !g.active() || g.values().stream()
          .filter(x -> list(v.valueIds()).contains(x.id())).anyMatch(x -> !x.active())) return false;
      if (!g.values().stream().map(ValueView::id).collect(Collectors.toSet()).containsAll(list(v.valueIds())))
        return false;
    }
    return true;
  }

  private Variant variant(List<Variant> variants, Long groupId) {
    return variants.stream().filter(v -> groupId.equals(v.groupId())).findFirst().orElse(null);
  }

  private List<String> optionKeys(Variant v) {
    return list(v.valueIds()).isEmpty()
        ? map(v.inputs()).isEmpty() ? List.of() : List.of(inputKey(v))
        : v.valueIds().stream().map(String::valueOf).toList();
  }

  private String inputKey(Variant v) {
    return "I:" + codes.configurationHash(json.write(canonical(v.inputs())));
  }

  private Object canonical(Object value) {
    if (value instanceof Map<?, ?> map) {
      Map<String, Object> sorted = new TreeMap<>();
      map.forEach((key, item) -> sorted.put(String.valueOf(key), canonical(item)));
      return sorted;
    }
    if (value instanceof Number number)
      return new BigDecimal(number.toString()).stripTrailingZeros().toPlainString();
    if (value instanceof List<?> list)
      return list.stream().map(this::canonical).sorted(Comparator.comparing(json::write)).toList();
    return value;
  }

  private String display(Object value) {
    return value instanceof BigDecimal number
        ? number.stripTrailingZeros().toPlainString() : String.valueOf(value);
  }

  private BrowseGroup facet(GroupView g, List<ProductMaster> scope,
      Map<Long, List<Variant>> definitions, boolean admin) {
    Set<String> used = new LinkedHashSet<>();
    Map<String, ProductMaster> owners = new LinkedHashMap<>();
    for (ProductMaster p : scope) {
      Variant v = variant(definitions.get(p.getId()), g.id());
      if (v != null) for (String key : optionKeys(v)) {
        used.add(key); owners.putIfAbsent(key, p);
      }
    }
    List<CatalogOption> options = new ArrayList<>();
    if (choice(g.control())) {
      for (ValueView v : g.values()) if (v.active() && used.contains(v.id().toString())) {
        String token = owners.get(v.id().toString()).getQrPublicToken();
        options.add(new CatalogOption(v.id().toString(), v.labels().customer(),
            admin ? v.assets() : assets.publicViews(v.assets(), token), v.guide()));
      }
    } else {
      for (String key : used) {
        ProductMaster p = owners.get(key);
        Variant v = variant(definitions.get(p.getId()), g.id());
        String label = g.fields().stream().filter(f -> map(v.inputs()).containsKey(f.key()))
            .map(f -> f.labels().customer() + ": " + (g.control() == Control.FILE
                ? ((List<?>) v.inputs().get(f.key())).size() + "개 파일"
                : display(v.inputs().get(f.key())) + (text(f.unit()).isBlank() ? "" : " " + f.unit())))
            .collect(Collectors.joining(" / "));
        String guide = g.fields().stream().filter(f -> map(v.inputs()).containsKey(f.key()))
            .map(Field::guide).filter(x -> x != null && !x.isBlank()).collect(Collectors.joining("\n"));
        options.add(new CatalogOption(key, label, List.of(), guide));
      }
    }
    List<AssetView> media = scope.isEmpty() ? List.of() : admin ? g.assets()
        : assets.publicViews(g.assets(), scope.get(0).getQrPublicToken());
    return new BrowseGroup(g.id(), g.key(), g.role(), g.labels().customer(), g.question(),
        g.guide(), g.control().name(), options, media);
  }

  private ProductCard card(ProductMaster p, List<Variant> variants,
      Map<Long, GroupView> groups, boolean admin) {
    List<AssetView> media = assets.owned("PRODUCT", p.getId());
    if (media.stream().noneMatch(AssetView::image))
      media = studio.allProductAssets(p).stream().filter(AssetView::image).limit(1).toList();
    if (!admin) media = assets.publicViews(media, p.getQrPublicToken());
    List<String> tags = variants.stream().filter(v -> !ProductStudioAttributeService.BASE
        .contains(groups.get(v.groupId()).role())).flatMap(v -> groups.get(v.groupId()).values()
            .stream().filter(x -> list(v.valueIds()).contains(x.id())).map(x -> x.labels().customer()))
        .limit(4).toList();
    return new ProductCard(admin ? p.getId() : null, p.getProductName(), p.getCatalogCode(),
        p.getQrPublicToken(), p.isNonStandard(), studio.status(p), p.getProductionHours(),
        p.getUnitPrice(), p.getCurrentStock(), media, tags);
  }

  public CustomerDetail detail(String token) {
    ProductMaster p = studio.publicProduct(token);
    PublicProduct schema = studio.publicSchema(token);
    List<CustomerGroup> groups = studio.variants(p).stream().map(v -> attributes.view(attributes.require(v.groupId())))
        .map(g -> new CustomerGroup(g.id(), g.key(),
            new Labels(g.labels().customer(), g.labels().customer(), g.labels().customer()),
            g.role(), g.askQuestion(), g.priceImpact())).toList();
    List<AssetView> productAssets = schema.assets().stream()
        .filter(a -> schema.productAssetIds().contains(a.id())).toList();
    return new CustomerDetail(p.getProductName(), p.getCatalogCode(), p.isNonStandard(),
        studio.status(p), p.getDescription(), p.getCurrentStock(), p.getProductionHours(),
        p.getUnitPrice(), p.getFaqTopicId(), productAssets, schema.assets(), groups,
        schema.process(), studio.fixedAnswers(p));
  }
}
