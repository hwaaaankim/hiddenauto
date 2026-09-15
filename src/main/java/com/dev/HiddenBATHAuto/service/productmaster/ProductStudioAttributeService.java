package com.dev.HiddenBATHAuto.service.productmaster;

import static com.dev.HiddenBATHAuto.dto.productmaster.ProductStudioDtos.*;
import static com.dev.HiddenBATHAuto.service.productmaster.ProductStudioEngine.*;

import com.dev.HiddenBATHAuto.enums.productmaster.*;
import com.dev.HiddenBATHAuto.model.productmaster.*;
import com.dev.HiddenBATHAuto.repository.productmaster.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductStudioAttributeService {
  public static final Set<String> BASE = Set.of("CATEGORY", "SUBCATEGORY", "SERIES");
  private final ProductAttributeGroupRepository groups;
  private final ProductAttributeValueRepository values;
  private final ProductComponentRepository components;
  private final ProductAttributeService legacy;
  private final ProductAttributeImageService legacyImages;
  private final ProductStudioAssetService assets;
  private final ProductMasterCodeService codes;
  private final ProductStudioJson json;

  @Transactional
  public List<GroupView> bootstrap(String actor) {
    List<ProductAttributeGroup> all = groups.findAllByOrderBySortOrderAscIdAsc();
    for (String role : List.of("CATEGORY", "SUBCATEGORY", "SERIES")) {
      List<ProductAttributeGroup> found =
          all.stream().filter(g -> g.getSystemRole().name().equals(role)).toList();
      if (found.size() > 1)
        throw new IllegalStateException(
            "기존 " + role + " 역할 그룹이 여러 개입니다. 그룹관리에서 기본으로 사용할 그룹 하나만 해당 역할로 지정해 주세요.");
      if (found.size() == 1) {
        ProductAttributeGroup g = found.get(0);
        if (!choice(control(g))
            || g.getSelectionMode() != ProductAttributeSelectionMode.SINGLE
            || g.isNonStandard())
          throw new IllegalStateException(
              "기본그룹의 입력 방식을 규격 하나선택형으로 수정해 주세요: " + g.getManagementLabel());
        if (!role.equals(g.getBaseRole())) {
          g.setBaseRole(role);
          g.setUpdatedBy(actor);
        }
        continue;
      }
      String name = ProductAttributeRole.valueOf(role).getLabelKr();
      if (groups.existsByCustomerLabelIgnoreCase(name))
        throw new IllegalStateException("기존 '" + name + "' 그룹에 기본 역할을 지정해 주세요.");
      saveGroup(
          new GroupEdit(
              null,
              null,
              new Labels(name, name, name),
              role,
              Control.RADIO,
              false,
              true,
              true,
              name + "를 선택해 주세요.",
              "",
              List.of()),
          actor);
    }
    return catalog();
  }

  public List<GroupView> catalog() {
    return groups.findAllByOrderBySortOrderAscIdAsc().stream().map(this::view).toList();
  }

  public ProductAttributeGroup require(Long id) {
    return groups.findById(id).orElseThrow(() -> new NoSuchElementException("그룹을 찾을 수 없습니다."));
  }

  public Control control(ProductAttributeGroup g) {
    if (g.getStudioControl() != null) return Control.valueOf(g.getStudioControl());
    return switch (g.getInputType()) {
      case CHOICE, DIMENSION ->
          g.getSelectionMode() == ProductAttributeSelectionMode.MULTIPLE
              ? Control.CHECKBOX
              : Control.RADIO;
      case NUMBER -> Control.NUMBER;
      case TEXT -> Control.TEXT;
    };
  }

  public List<Field> fieldsFor(ProductAttributeGroup g) {
    if (g.getStudioFieldsJson() != null) return json.list(g.getStudioFieldsJson(), Field.class);
    if (choice(control(g))) return List.of();
    return List.of(
        new Field(
            "legacy",
            labelsOf(g),
            g.getCustomerLabel(),
            true,
            g.getMinimumValue() != null && g.getMinimumValue().signum() < 0,
            g.getMinimumValue(),
            g.getMaximumValue(),
            g.getStepValue() == null ? BigDecimal.ONE : g.getStepValue(),
            0,
            500,
            "ANY",
            g.getUnitLabel(),
            List.of(),
            0,
            1,
            10));
  }

  public Labels labelsOf(ProductAttributeGroup g) {
    return new Labels(g.getCustomerLabel(), g.getProductionLabel(), g.getManagementLabel());
  }

  public Labels labelsOf(ProductAttributeValue v) {
    return new Labels(v.getCustomerLabel(), v.getProductionLabel(), v.getManagementLabel());
  }

  public String namePart(ProductAttributeValue v) {
    return v.getNamePart() == null ? v.getCustomerLabel() : v.getNamePart();
  }

  public GroupView view(ProductAttributeGroup g) {
    List<AssetView> groupAssets = new ArrayList<>(assets.owned("GROUP", g.getId()));
    legacyImages
        .getGroupImages(g.getId())
        .forEach(
            i ->
                groupAssets.add(
                    new AssetView(
                        "legacy-" + i.id(),
                        i.originalFilename(),
                        i.contentType(),
                        i.fileSize(),
                        i.contentPath(),
                        true)));
    List<ValueView> options =
        g.getValues().stream()
            .sorted(
                Comparator.comparingInt(ProductAttributeValue::getSortOrder)
                    .thenComparing(ProductAttributeValue::getId))
            .map(this::view)
            .toList();
    return new GroupView(
        g.getId(),
        g.getRowVersion(),
        g.getGroupCode(),
        labelsOf(g),
        g.getSystemRole().name(),
        control(g),
        g.isNonStandard(),
        g.isIncludeInName(),
        g.isActive(),
        g.getQuestionText(),
        g.getCustomerGuide(),
        fieldsFor(g),
        options,
        groupAssets);
  }

  public ValueView view(ProductAttributeValue v) {
    List<AssetView> files = new ArrayList<>(assets.owned("VALUE", v.getId()));
    legacyImages
        .getValueImages(v.getId())
        .forEach(
            i ->
                files.add(
                    new AssetView(
                        "legacy-" + i.id(),
                        i.originalFilename(),
                        i.contentType(),
                        i.fileSize(),
                        i.contentPath(),
                        true)));
    return new ValueView(
        v.getId(),
        v.getRowVersion(),
        v.getValueCode(),
        labelsOf(v),
        namePart(v),
        v.isActive(),
        v.getDimensionType().name(),
        files);
  }

  @Transactional
  public GroupView saveGroup(GroupEdit request, String actor) {
    if (request == null) throw new IllegalArgumentException("그룹 정보가 필요합니다.");
    labels(request.labels(), 80);
    fields(request.control(), request.fields(), request.nonStandard());
    ProductAttributeRole role;
    try {
      role = ProductAttributeRole.valueOf(request.role());
    } catch (RuntimeException e) {
      throw new IllegalArgumentException("시스템 역할을 확인해 주세요.");
    }
    boolean base = BASE.contains(role.name());
    if (base && (request.nonStandard() || request.control() != Control.RADIO || !request.active()))
      throw new IllegalArgumentException("대분류·중분류·시리즈는 활성화된 규격 하나선택형 기본그룹이어야 합니다.");
    if (base
        && (request.id() == null
            ? groups.existsBySystemRole(role)
            : groups.existsBySystemRoleAndIdNot(role, request.id())))
      throw new IllegalStateException("기본 역할 그룹은 하나만 만들 수 있습니다.");
    if (text(request.question()).length() > 300 || text(request.guide()).length() > 1000)
      throw new IllegalArgumentException("질문 또는 도움말이 너무 깁니다.");
    ProductAttributeGroup g =
        request.id() == null ? new ProductAttributeGroup() : require(request.id());
    if (request.id() != null) {
      version(request.version(), g.getRowVersion());
      if (BASE.contains(g.getSystemRole().name())
          && !g.getSystemRole().equals(role)
          && groups.findAllByOrderBySortOrderAscIdAsc().stream()
                  .filter(x -> x.getSystemRole() == g.getSystemRole())
                  .count()
              == 1) throw new IllegalArgumentException("유일한 기본그룹의 역할은 해제할 수 없습니다.");
      if (components.existsByGroupId(g.getId())
          && (control(g) != request.control()
              || g.isNonStandard() != request.nonStandard()
              || g.getSystemRole() != role
              || !json.write(fieldsFor(g)).equals(json.write(list(request.fields())))))
        throw new IllegalStateException(
            "제품이 사용하는 그룹의 유형·역할·입력 필드는 변경할 수 없습니다. 제품별 비규격 필드는 비규격 제품에서 편집해 주세요.");
      if (!g.getValues().isEmpty() && (!choice(request.control()) || request.nonStandard()))
        throw new IllegalArgumentException("보기가 등록된 그룹을 입력형 또는 비규격으로 바꿀 수 없습니다.");
    }
    Labels l = request.labels();
    boolean duplicate =
        request.id() == null
            ? groups.existsByCustomerLabelIgnoreCase(l.customer().trim())
                || groups.existsByProductionLabelIgnoreCase(l.production().trim())
                || groups.existsByManagementLabelIgnoreCase(l.management().trim())
            : groups.existsByCustomerLabelIgnoreCaseAndIdNot(l.customer().trim(), g.getId())
                || groups.existsByProductionLabelIgnoreCaseAndIdNot(
                    l.production().trim(), g.getId())
                || groups.existsByManagementLabelIgnoreCaseAndIdNot(
                    l.management().trim(), g.getId());
    if (duplicate) throw new IllegalStateException("같은 표시명을 사용하는 그룹이 있습니다.");
    if (request.id() == null) {
      g.setGroupCode(codes.newGroupCode());
      g.setCreatedBy(actor);
      g.setSortOrder(groups.findMaxSortOrder() + 10);
      g.setGroupType(ProductAttributeGroupType.CORE);
    }
    g.setCustomerLabel(l.customer().trim());
    g.setProductionLabel(l.production().trim());
    g.setManagementLabel(l.management().trim());
    g.setSystemRole(role);
    g.setBaseRole(base ? role.name() : null);
    g.setStudioControl(request.control().name());
    g.setNonStandard(request.nonStandard());
    g.setIncludeInName(request.includeInName());
    g.setSelectionMode(
        request.control() == Control.CHECKBOX
            ? ProductAttributeSelectionMode.MULTIPLE
            : ProductAttributeSelectionMode.SINGLE);
    // Legacy enum retains its existing values; Studio's uniform control is stored separately.
    if (g.getInputType() != ProductAttributeInputType.DIMENSION)
      g.setInputType(
          choice(request.control())
              ? ProductAttributeInputType.CHOICE
              : request.control() == Control.NUMBER
                  ? ProductAttributeInputType.NUMBER
                  : ProductAttributeInputType.TEXT);
    g.setStudioFieldsJson(json.write(request.nonStandard() ? List.of() : list(request.fields())));
    g.setQuestionText(text(request.question()));
    g.setCustomerGuide(text(request.guide()));
    g.setActive(request.active());
    g.setUpdatedBy(actor);
    g.setUpdatedAt(LocalDateTime.now());
    groups.saveAndFlush(g);
    return view(g);
  }

  @Transactional
  public List<ValueView> saveValues(Long groupId, List<ValueEdit> requests, String actor) {
    ProductAttributeGroup g = require(groupId);
    if (!choice(control(g)) || g.isNonStandard())
      throw new IllegalArgumentException("비규격 보기는 해당 제품의 프로세스에서 등록해 주세요. 입력형에는 보기를 등록할 수 없습니다.");
    if (requests == null || requests.isEmpty() || requests.size() > 200)
      throw new IllegalArgumentException("보기는 한 번에 1~200개까지 등록합니다.");
    Set<Long> ids = new HashSet<>();
    List<ValueView> result = new ArrayList<>();
    int order = values.findMaxSortOrderByGroupId(groupId);
    for (ValueEdit request : requests) {
      if (request == null) throw new IllegalArgumentException("빈 보기입니다.");
      labels(request.labels(), 120);
      if (text(request.namePart()).length() > 160)
        throw new IllegalArgumentException("제품명 구성 문자는 최대 160자입니다.");
      ProductAttributeValue v =
          request.id() == null
              ? new ProductAttributeValue()
              : values
                  .findWithGroupById(request.id())
                  .orElseThrow(() -> new NoSuchElementException("보기가 없습니다."));
      if (request.id() != null) {
        if (!ids.add(request.id()) || !v.getGroup().getId().equals(groupId))
          throw new IllegalArgumentException("보기 ID가 중복되었거나 다른 그룹의 보기입니다.");
        version(request.version(), v.getRowVersion());
      }
      Labels l = request.labels();
      boolean duplicate =
          request.id() == null
              ? values.existsByGroupIdAndCustomerLabelIgnoreCase(groupId, l.customer().trim())
                  || values.existsByGroupIdAndProductionLabelIgnoreCase(
                      groupId, l.production().trim())
                  || values.existsByGroupIdAndManagementLabelIgnoreCase(
                      groupId, l.management().trim())
              : values.existsByGroupIdAndCustomerLabelIgnoreCaseAndIdNot(
                      groupId, l.customer().trim(), v.getId())
                  || values.existsByGroupIdAndProductionLabelIgnoreCaseAndIdNot(
                      groupId, l.production().trim(), v.getId())
                  || values.existsByGroupIdAndManagementLabelIgnoreCaseAndIdNot(
                      groupId, l.management().trim(), v.getId());
      if (duplicate) throw new IllegalStateException("같은 그룹에 동일한 표시명의 보기가 있습니다: " + l.customer());
      if (request.id() == null) {
        v.setGroup(g);
        v.setValueCode(codes.newValueCode());
        v.setCreatedBy(actor);
        v.setSortOrder(order += 10);
        v.setDimensionType(ProductDimensionType.NONE);
      }
      v.setCustomerLabel(l.customer().trim());
      v.setProductionLabel(l.production().trim());
      v.setManagementLabel(l.management().trim());
      v.setNamePart(request.namePart() == null ? l.customer() : request.namePart());
      v.setActive(request.active());
      v.setUpdatedBy(actor);
      v.setUpdatedAt(LocalDateTime.now());
      if (request.id() == null) g.addValue(v);
      values.saveAndFlush(v);
      result.add(view(v));
    }
    return result;
  }

  @Transactional
  public void deleteGroup(Long id, String actor) {
    ProductAttributeGroup g = require(id);
    if (BASE.contains(g.getSystemRole().name()))
      throw new IllegalArgumentException("기본그룹은 삭제할 수 없습니다.");
    legacy.deleteGroup(id);
    assets.attach("GROUP", id, List.of(), actor);
  }

  @Transactional
  public void deleteValue(Long id, String actor) {
    legacy.deleteValue(id);
    assets.attach("VALUE", id, List.of(), actor);
  }

  @Transactional
  public List<GroupView> reorderGroups(List<Long> ids, String actor) {
    legacy.reorderGroups(
        new com.dev.HiddenBATHAuto.dto.productmaster.ProductMasterDtos.ReorderRequest(ids), actor);
    return catalog();
  }

  @Transactional
  public GroupView reorderValues(Long id, List<Long> ids, String actor) {
    legacy.reorderValues(
        id,
        new com.dev.HiddenBATHAuto.dto.productmaster.ProductMasterDtos.ReorderRequest(ids),
        actor);
    return view(require(id));
  }

  public static void version(Long requested, long actual) {
    if (requested == null || requested != actual)
      throw new IllegalStateException("다른 사용자가 변경했거나 저장 버전이 없습니다. 새로고침 후 다시 시도해 주세요.");
  }
}
