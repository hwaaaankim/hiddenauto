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
  public record StandardOption(String key, String label, List<AssetView> assets, String guide,
      String helpDisplay, List<CatalogHelp> helpSections, List<String> choices,
      Map<String, Object> inputs, boolean absent) {}
  public record StandardQuestion(Long groupId, String key, String role, String label, Labels labels,
      String question, String guide, String control, List<StandardOption> options,
      List<Choice> choices, List<Field> fields, List<AssetView> assets,
      List<String> assetIds, String helpDisplay) {}
  public record Configuration(List<StandardQuestion> steps, StandardQuestion next,
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
        // Developer attributes never become customer questions. If hidden specifications
        // distinguish otherwise matching products, keep those products as final candidates.
        .filter(g -> g.askQuestion() && !Set.of("CATEGORY", "SUBCATEGORY", "SERIES").contains(g.role()))
        .toList();
    Set<Long> valid = groups.stream().map(GroupView::id).collect(Collectors.toSet());
    if (!valid.containsAll(selections.keySet()))
      throw new IllegalArgumentException("이 시리즈에 없는 옵션 그룹입니다. 옵션을 다시 구성해 주세요.");
    List<ProductMaster> remaining = scope;
    List<StandardQuestion> steps = new ArrayList<>();
    List<DisplayRow> display = new ArrayList<>();
    for (GroupView g : groups) {
      StandardQuestion question = question(g, remaining, data.definitions());
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
      if (remaining.isEmpty())
        throw new IllegalArgumentException("선택하신 조합으로 등록된 규격제품이 없습니다. 다른 옵션이나 규격을 선택해 주세요.");
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

  private Map<String, Object> wireInputs(Variant variant) {
    Map<String, Object> result = new LinkedHashMap<>();
    if (variant != null && variant.inputs() != null) variant.inputs().forEach((key, value) ->
        result.put(key, value instanceof Number n
            ? new BigDecimal(n.toString()).stripTrailingZeros().toPlainString() : value));
    return result;
  }

  private Field selectionField(Field f, boolean required, Control control, List<StandardOption> present) {
    BigDecimal min = f.min(), max = f.max();
    if (control == Control.NUMBER) {
      List<BigDecimal> values = present.stream().map(option -> option.inputs().get(f.key()))
          .filter(Objects::nonNull).map(value -> new BigDecimal(value.toString())).toList();
      if (!values.isEmpty()) {
        min = values.stream().min(BigDecimal::compareTo).orElseThrow();
        max = values.stream().max(BigDecimal::compareTo).orElseThrow();
      }
    }
    return new Field(f.key(), f.labels(), f.namePart(), required, f.allowNegative(), min, max,
        f.step(), f.minLength(), f.maxLength(), f.format(), f.unit(), f.extensions(), f.minFiles(),
        f.maxFiles(), f.maxFileMB(), f.guide(), f.bundleKey(), f.assetIds(), f.helpDisplay());
  }

  private StandardQuestion question(GroupView g, List<ProductMaster> scope,
      Map<Long, List<Variant>> definitions) {
    Map<String, Variant> distinct = new LinkedHashMap<>();
    for (ProductMaster p : scope) {
      Variant v = variant(definitions.get(p.getId()), g.id());
      distinct.putIfAbsent(optionKey(v), v);
    }
    // Only real products remaining after preceding answers form the next question domain.
    List<StandardOption> options = distinct.entrySet().stream().map(entry -> {
      Variant v = entry.getValue();
      if (v == null) return new StandardOption(entry.getKey(), "해당 없음", List.of(), null,
          "ALWAYS", List.of(), List.of(), Map.of(), true);
      List<AssetView> media = new ArrayList<>();
      List<CatalogHelp> help = new ArrayList<>();
      List<String> selected = new ArrayList<>();
      for (ValueView value : g.values()) if (v.valueIds() != null && v.valueIds().contains(value.id())) {
        selected.add(value.key()); media.addAll(value.assets());
        help.add(new CatalogHelp(value.labels().customer(), value.guide(), value.assets(), value.helpDisplay()));
      }
      Map<String, Object> inputs = v.inputs() == null ? Map.of() : v.inputs();
      for (Field f : g.fields()) if (inputs.containsKey(f.key())) {
        List<AssetView> fieldMedia = g.assets().stream()
            .filter(a -> f.assetIds() != null && f.assetIds().contains(a.id())).toList();
        help.add(new CatalogHelp(f.labels().customer(), f.guide(), fieldMedia, f.helpDisplay()));
      }
      return new StandardOption(entry.getKey(), variantLabel(g, v, "customer"),
          media.stream().distinct().toList(), null, "ALWAYS", help, selected, wireInputs(v), false);
    }).toList();
    Set<String> usedChoices = options.stream().flatMap(option -> option.choices().stream()).collect(Collectors.toSet());
    List<Choice> choices = g.values().stream().filter(value -> value.active() && usedChoices.contains(value.key()))
        .map(value -> new Choice(value.key(), value.labels(), value.namePart(),
            value.assets().stream().map(AssetView::id).toList(), value.guide(), value.helpDisplay())).toList();
    List<StandardOption> present = options.stream().filter(o -> !o.absent()).toList();
    Set<String> usedFields = present.stream().flatMap(o -> o.inputs().keySet().stream()).collect(Collectors.toSet());
    List<Field> fields = g.fields().stream().filter(f -> usedFields.contains(f.key()))
        .map(f -> selectionField(f, present.stream().allMatch(o -> o.inputs().containsKey(f.key())), g.control(), present)).toList();
    Map<String, AssetView> media = new LinkedHashMap<>();
    Set<String> usedMedia = ProductStudioMedia.questionAssets(g).stream().map(AssetView::id)
        .collect(Collectors.toSet());
    fields.stream().flatMap(field -> (field.assetIds() == null ? List.<String>of() : field.assetIds()).stream())
        .forEach(usedMedia::add);
    g.assets().stream().filter(asset -> usedMedia.contains(asset.id())).forEach(a -> media.put(a.id(), a));
    g.values().stream().filter(value -> value.active() && usedChoices.contains(value.key()))
        .flatMap(value -> value.assets().stream()).forEach(a -> media.put(a.id(), a));
    if (g.control() == Control.FILE) for (StandardOption option : present)
      for (Object raw : option.inputs().values()) if (raw instanceof List<?> ids)
        for (Object id : ids) try {
          AssetView file = assets.view(assets.require(String.valueOf(id))); media.put(file.id(), file);
        } catch (NoSuchElementException ignored) { /* Existing specification can still be identified. */ }
    return new StandardQuestion(g.id(), g.key(), g.role(), g.labels().customer(), g.labels(),
        g.question(), g.guide(), g.control().name(), options, choices, fields,
        new ArrayList<>(media.values()), ProductStudioMedia.questionAssets(g).stream().map(AssetView::id).toList(),
        g.helpDisplay());
  }
}
