package com.dev.HiddenBATHAuto.service.productmaster;

import static com.dev.HiddenBATHAuto.dto.productmaster.ProductStudioCustomerDtos.*;
import static com.dev.HiddenBATHAuto.dto.productmaster.ProductStudioDtos.*;

import com.dev.HiddenBATHAuto.model.productmaster.ProductMaster;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only collection and exact standard-configuration matching for the admin storefront. */
@Service
@Transactional(readOnly = true)
public class ProductStorefrontService {
  private final ProductStudioCustomerService customer;
  private final ProductStudioService studio;
  private final ProductStudioAssetService assets;
  private final ProductMasterCodeService codes;
  private final ProductStudioJson json;

  public ProductStorefrontService(ProductStudioCustomerService customer, ProductStudioService studio,
      ProductStudioAssetService assets, ProductMasterCodeService codes, ProductStudioJson json) {
    this.customer = customer;
    this.studio = studio;
    this.assets = assets;
    this.codes = codes;
    this.json = json;
  }

  public record SeriesCard(String key, String categoryKey, String category, String series,
      boolean nonStandard, Long productId, long total, List<AssetView> assets) {}
  public record Collection(String key, String category, List<SeriesCard> series) {}
  public record SeriesDetail(SeriesCard series, ProductView representative, List<ProductCard> products) {}
  public record DisplayRow(Labels group, String question, Labels answer) {}
  public record Configuration(List<BrowseGroup> steps, BrowseGroup next,
      int totalGroups, long remaining, boolean complete, List<ProductCard> products, List<DisplayRow> display) {}

  public List<Collection> collections(boolean nonStandard) {
    var data = customer.showroomData(nonStandard);
    Map<String, List<SeriesCard>> categories = new LinkedHashMap<>();
    for (var entry : data.buckets()) {
      SeriesCard card = seriesCard(data, entry, nonStandard);
      categories.computeIfAbsent(card.categoryKey(), k -> new ArrayList<>()).add(card);
    }
    return categories.entrySet().stream().map(entry ->
        new Collection(entry.getKey(), entry.getValue().get(0).category(), entry.getValue())).toList();
  }

  private SeriesCard seriesCard(ProductStudioCustomerService.ShowroomData data,
      Map.Entry<String, List<ProductMaster>> entry, boolean nonStandard) {
    Map<Long, String> path = data.paths().get(entry.getKey());
    var category = path.entrySet().stream()
        .filter(p -> "CATEGORY".equals(data.groups().get(p.getKey()).role())).findFirst().orElseThrow();
    var names = data.names().get(entry.getKey());
    ProductMaster first = entry.getValue().get(0);
    return new SeriesCard(entry.getKey(), category.getKey() + ":" + category.getValue(),
        names.get("CATEGORY"), names.get("SERIES"), nonStandard, first.getId(),
        entry.getValue().size(), assets.owned("PRODUCT", first.getId()).stream()
            .filter(AssetView::image).limit(1).toList());
  }

  private Map.Entry<String, List<ProductMaster>> bucket(
      ProductStudioCustomerService.ShowroomData data, Long productId) {
    return data.buckets().stream().filter(entry -> entry.getValue().stream()
        .anyMatch(p -> p.getId().equals(productId))).findFirst()
        .orElseThrow(() -> new NoSuchElementException("현재 표시할 수 없는 시리즈입니다. 컬렉션에서 다시 선택해 주세요."));
  }

  public SeriesDetail series(Long productId) {
    ProductMaster anchor = studio.require(productId);
    var data = customer.showroomData(anchor.isNonStandard());
    var entry = bucket(data, productId);
    // Standard prices are deliberately withheld until all configuration questions are answered.
    return new SeriesDetail(seriesCard(data, entry, anchor.isNonStandard()), studio.detail(productId),
        anchor.isNonStandard() ? entry.getValue().stream().map(p -> customer.card(p,
            data.definitions().get(p.getId()), data.groups(), true)).toList() : List.of());
  }

  public Configuration configure(Long productId, Map<Long, String> selections) {
    if (selections == null || selections.size() > 200 || selections.entrySet().stream()
        .anyMatch(e -> e.getKey() == null || e.getValue() == null || e.getValue().length() > 200))
      throw new IllegalArgumentException("규격 옵션 선택값을 확인해 주세요.");
    if (studio.require(productId).isNonStandard())
      throw new IllegalArgumentException("비규격 제품은 제품별 맞춤 구성에서 선택해 주세요.");
    var data = customer.showroomData(false);
    List<ProductMaster> scope = bucket(data, productId).getValue();
    // Saved product composition order, with later models' additional groups appended deterministically.
    Set<Long> ordered = new LinkedHashSet<>();
    scope.forEach(p -> data.definitions().get(p.getId()).forEach(v -> ordered.add(v.groupId())));
    List<GroupView> groups = ordered.stream().map(data.groups()::get)
        .filter(g -> !Set.of("CATEGORY", "SERIES").contains(g.role()))
        .filter(g -> !"SUBCATEGORY".equals(g.role()) || scope.stream()
            .map(p -> optionKey(variant(data.definitions().get(p.getId()), g.id()))).distinct().count() > 1)
        .toList();
    Set<Long> valid = groups.stream().map(GroupView::id).collect(Collectors.toSet());
    if (!valid.containsAll(selections.keySet()))
      throw new IllegalArgumentException("이 시리즈에 없는 옵션 그룹입니다. 옵션을 다시 구성해 주세요.");
    List<ProductMaster> remaining = scope;
    List<BrowseGroup> steps = new ArrayList<>();
    List<DisplayRow> display = new ArrayList<>();
    for (GroupView g : groups) {
      BrowseGroup question = question(g, remaining, data.definitions());
      String picked = selections.get(g.id());
      if (picked == null) {
        if (selections.size() != steps.size())
          throw new IllegalArgumentException("앞 질문부터 순서대로 선택해 주세요.");
        display.add(new DisplayRow(g.labels(), g.question(), null));
        return new Configuration(steps, question, groups.size(), remaining.size(), false, List.of(), display);
      }
      if (question.options().stream().noneMatch(o -> o.key().equals(picked)))
        throw new IllegalArgumentException("앞서 선택한 옵션과 조합할 수 없습니다. 이 질문부터 다시 선택해 주세요.");
      steps.add(question);
      remaining = remaining.stream().filter(p -> picked.equals(optionKey(
          variant(data.definitions().get(p.getId()), g.id())))).toList();
      Variant selected = variant(data.definitions().get(remaining.get(0).getId()), g.id());
      display.add(new DisplayRow(g.labels(), g.question(), new Labels(
          variantLabel(g, selected, "customer"), variantLabel(g, selected, "production"),
          variantLabel(g, selected, "management"))));
    }
    return new Configuration(steps, null, groups.size(), remaining.size(), true,
        remaining.stream().map(p -> customer.card(p, data.definitions().get(p.getId()), data.groups(), true)).toList(), display);
  }

  private String label(Labels labels, String role) {
    String value = switch (role) {
      case "production" -> labels.production();
      case "management" -> labels.management();
      default -> labels.customer();
    };
    return value == null || value.isBlank() ? labels.customer() : value;
  }

  private String fieldValue(Control control, Object raw) {
    return control == Control.FILE && raw instanceof List<?> files ? files.size() + "개 파일"
        : raw instanceof Number n ? new BigDecimal(n.toString()).stripTrailingZeros().toPlainString()
        : String.valueOf(raw);
  }

  private String variantLabel(GroupView g, Variant v, String role) {
    if (v == null) return "해당 없음";
    List<String> labels = new ArrayList<>();
    for (ValueView value : g.values())
      if (v.valueIds() != null && v.valueIds().contains(value.id())) labels.add(label(value.labels(), role));
    Map<String, Object> inputs = v.inputs() == null ? Map.of() : v.inputs();
    for (Field f : g.fields()) if (inputs.containsKey(f.key()))
      labels.add(label(f.labels(), role) + ": " + fieldValue(g.control(), inputs.get(f.key()))
          + (f.unit() == null || f.unit().isBlank() ? "" : " " + f.unit()));
    return labels.isEmpty() ? "기본 구성" : String.join(" / ", labels);
  }

  private Variant variant(List<Variant> variants, Long groupId) {
    return variants.stream().filter(v -> groupId.equals(v.groupId())).findFirst().orElse(null);
  }

  private String optionKey(Variant variant) {
    if (variant == null) return "ABSENT";
    return "V:" + codes.configurationHash(json.write(customer.canonical(Map.of(
        "values", variant.valueIds() == null ? List.of() : variant.valueIds(),
        "inputs", variant.inputs() == null ? Map.of() : variant.inputs()))));
  }

  private BrowseGroup question(GroupView g, List<ProductMaster> scope,
      Map<Long, List<Variant>> definitions) {
    Map<String, Variant> distinct = new LinkedHashMap<>();
    for (ProductMaster p : scope) {
      Variant v = variant(definitions.get(p.getId()), g.id());
      distinct.putIfAbsent(optionKey(v), v);
    }
    List<CatalogOption> options = distinct.entrySet().stream().map(entry -> {
      Variant v = entry.getValue();
      if (v == null) return new CatalogOption(entry.getKey(), "해당 없음", List.of());
      List<AssetView> media = new ArrayList<>();
      List<CatalogHelp> help = new ArrayList<>();
      for (ValueView value : g.values()) if (v.valueIds() != null && v.valueIds().contains(value.id())) {
        media.addAll(value.assets());
        help.add(new CatalogHelp(value.labels().customer(), value.guide(), value.assets(), value.helpDisplay()));
      }
      Map<String, Object> inputs = v.inputs() == null ? Map.of() : v.inputs();
      for (Field f : g.fields()) if (inputs.containsKey(f.key())) {
        List<AssetView> fieldMedia = g.assets().stream()
            .filter(a -> f.assetIds() != null && f.assetIds().contains(a.id())).toList();
        help.add(new CatalogHelp(f.labels().customer(), f.guide(), fieldMedia, f.helpDisplay()));
      }
      return new CatalogOption(entry.getKey(), variantLabel(g, v, "customer"),
          media.stream().distinct().toList(), null, "ALWAYS", help);
    }).toList();
    return new BrowseGroup(g.id(), g.key(), g.role(), g.labels().customer(), g.question(),
        g.guide(), "RADIO", options, ProductStudioMedia.questionAssets(g), g.helpDisplay());
  }
}
