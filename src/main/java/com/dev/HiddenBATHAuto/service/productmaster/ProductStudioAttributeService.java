package com.dev.HiddenBATHAuto.service.productmaster;

import static com.dev.HiddenBATHAuto.dto.productmaster.ProductStudioDtos.*;
import static com.dev.HiddenBATHAuto.service.productmaster.ProductStudioEngine.*;

import com.dev.HiddenBATHAuto.enums.productmaster.*;
import com.dev.HiddenBATHAuto.model.productmaster.*;
import com.dev.HiddenBATHAuto.repository.productmaster.*;
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
  private final ProductStudioAssetService assets;
  private final ProductMasterCodeService codes;
  private final ProductStudioJson json;

  @Transactional
  public List<GroupView> bootstrap(String actor) {
    List<ProductAttributeGroup> all = groups.findAllByOrderBySortOrderAscIdAsc();
    for (String role : List.of("CATEGORY", "SUBCATEGORY", "SERIES")) {
      List<ProductAttributeGroup> found =
          all.stream().filter(g -> g.getSystemRole().name().equals(role)).toList();
      if (found.size() > 1) throw new IllegalStateException("필수그룹 데이터가 중복되어 초기화할 수 없습니다: " + role);
      if (found.size() == 1) {
        ProductAttributeGroup g = found.get(0);
        if (!choice(control(g)) || control(g) != Control.RADIO || g.isNonStandard())
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
        throw new IllegalStateException("필수그룹과 같은 이름의 옵션그룹이 있습니다. 해당 옵션그룹의 이름을 변경해 주세요: " + name);
      saveGroupInternal(
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
          actor,
          true);
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
    return Control.valueOf(g.getStudioControl());
  }

  public List<Field> fieldsFor(ProductAttributeGroup g) {
    return json.list(g.getStudioFieldsJson(), Field.class);
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
    return new ValueView(
        v.getId(),
        v.getRowVersion(),
        v.getValueCode(),
        labelsOf(v),
        namePart(v),
        v.isActive(),
        files);
  }

  @Transactional
  public GroupView saveGroup(GroupEdit request, String actor) {
    return saveGroupInternal(request, actor, false);
  }

  private GroupView saveGroupInternal(GroupEdit request, String actor, boolean initializeBase) {
    if (request == null) throw new IllegalArgumentException("그룹 정보가 필요합니다.");
    Map<String, String> errors = new LinkedHashMap<>();
    checkLabels(request.labels(), "", 80, errors);
    if (!errors.isEmpty()) throw new ProductStudioValidationException(errors);
    try {
      fields(request.control(), request.fields(), request.nonStandard());
    } catch (IllegalArgumentException e) {
      throw new ProductStudioValidationException(Map.of("fields", e.getMessage()));
    }
    ProductAttributeGroup current = request.id() == null ? null : require(request.id());
    // Classification is determined by the stored base group, never by an editor selection.
    ProductAttributeRole role =
        initializeBase
            ? ProductAttributeRole.valueOf(request.role())
            : current != null && BASE.contains(current.getSystemRole().name())
                ? current.getSystemRole()
                : ProductAttributeRole.GENERAL;
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
              || (BASE.contains(g.getSystemRole().name()) && g.getSystemRole() != role)
              || !json.write(fieldsFor(g)).equals(json.write(list(request.fields())))))
        throw new IllegalStateException(
            "제품이 사용하는 그룹의 유형·역할·입력 필드는 변경할 수 없습니다. 제품별 비규격 필드는 비규격 제품에서 편집해 주세요.");
      if (!g.getValues().isEmpty() && (!choice(request.control()) || request.nonStandard()))
        throw new IllegalArgumentException("보기가 등록된 그룹을 입력형 또는 비규격으로 바꿀 수 없습니다.");
    }
    Labels l =
        new Labels(
            normalizedLabel(request.labels().customer()),
            normalizedLabel(request.labels().production()),
            normalizedLabel(request.labels().management()));
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
    if (duplicate) {
      for (var existing : groups.findAllByOrderBySortOrderAscIdAsc()) {
        if (java.util.Objects.equals(existing.getId(), request.id())) continue;
        duplicateLabels(l, labelsOf(existing), "", errors, "같은 표시명을 사용하는 그룹이 있습니다.");
      }
      throw new ProductStudioValidationException(errors);
    }
    if (request.id() == null) {
      g.setGroupCode(codes.newGroupCode());
      g.setCreatedBy(actor);
      g.setSortOrder(groups.findMaxSortOrder() + 10);
    }
    g.setCustomerLabel(l.customer().trim());
    g.setProductionLabel(l.production().trim());
    g.setManagementLabel(l.management().trim());
    g.setSystemRole(role);
    g.setBaseRole(base ? role.name() : null);
    g.setStudioControl(request.control().name());
    g.setNonStandard(request.nonStandard());
    g.setIncludeInName(request.includeInName());
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
    Map<String, String> inputErrors = new LinkedHashMap<>();
    Set<Long> editedIds =
        requests.stream()
            .filter(java.util.Objects::nonNull)
            .map(ValueEdit::id)
            .filter(java.util.Objects::nonNull)
            .collect(java.util.stream.Collectors.toSet());
    for (int i = 0; i < requests.size(); i++) {
      ValueEdit item = requests.get(i);
      if (item == null) {
        inputErrors.put(String.valueOf(i), "빈 보기입니다.");
        continue;
      }
      checkLabels(item.labels(), i + ".", 120, inputErrors);
      if (text(item.namePart()).length() > 160)
        inputErrors.put(i + ".namePart", "제품명 구성 문자는 160자 이하입니다.");
      if (item.labels() != null) {
        for (int j = 0; j < i; j++)
          if (requests.get(j) != null && requests.get(j).labels() != null) {
            duplicateLabels(
                item.labels(),
                requests.get(j).labels(),
                i + ".",
                inputErrors,
                "같은 그룹의 보기 표시명이 중복됩니다. (" + (j + 1) + "번째 보기)");
            duplicateLabels(
                requests.get(j).labels(),
                item.labels(),
                j + ".",
                inputErrors,
                "같은 그룹의 보기 표시명이 중복됩니다. (" + (i + 1) + "번째 보기)");
          }
        for (var existing : g.getValues())
          if (!editedIds.contains(existing.getId()))
            duplicateLabels(
                item.labels(), labelsOf(existing), i + ".", inputErrors, "같은 그룹의 보기 표시명이 중복됩니다.");
      }
    }
    if (!inputErrors.isEmpty()) throw new ProductStudioValidationException(inputErrors);
    Set<Long> requestedIds = new HashSet<>();
    List<Labels> pendingLabels = new ArrayList<>();
    for (ValueEdit item : requests) {
      if (item == null) throw new IllegalArgumentException("빈 보기입니다.");
      labels(item.labels(), 120);
      if (item.id() != null && !requestedIds.add(item.id()))
        throw new IllegalArgumentException("보기 ID가 중복됩니다.");
      pendingLabels.add(item.labels());
    }
    for (ProductAttributeValue existing : g.getValues()) {
      if (!requestedIds.contains(existing.getId()))
        pendingLabels.add(
            new Labels(
                existing.getCustomerLabel(),
                existing.getProductionLabel(),
                existing.getManagementLabel()));
    }
    uniqueLabels(pendingLabels, "같은 그룹의 보기");
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
      Labels l =
          new Labels(
              normalizedLabel(request.labels().customer()),
              normalizedLabel(request.labels().production()),
              normalizedLabel(request.labels().management()));
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
    if (components.existsByGroupId(id))
      throw new IllegalStateException("제품에서 사용하는 그룹은 삭제할 수 없습니다. 비활성화를 사용해 주세요.");
    for (ProductAttributeValue value : new ArrayList<>(g.getValues()))
      assets.attach("VALUE", value.getId(), List.of(), actor);
    assets.attach("GROUP", id, List.of(), actor);
    groups.delete(g);
    groups.flush();
  }

  @Transactional
  public void deleteValue(Long id, String actor) {
    ProductAttributeValue value =
        values
            .findWithGroupById(id)
            .orElseThrow(() -> new NoSuchElementException("보기를 찾을 수 없습니다."));
    if (components.existsByValueId(id))
      throw new IllegalStateException("제품에서 사용하는 보기는 삭제할 수 없습니다. 비활성화를 사용해 주세요.");
    assets.attach("VALUE", id, List.of(), actor);
    value.getGroup().removeValue(value);
    values.delete(value);
    values.flush();
  }

  @Transactional
  public List<GroupView> reorderGroups(List<Long> ids, String actor) {
    List<ProductAttributeGroup> all = groups.findAllByOrderBySortOrderAscIdAsc();
    validateOrder(ids, all.stream().map(ProductAttributeGroup::getId).toList());
    for (int i = 0; i < ids.size(); i++) {
      ProductAttributeGroup g = require(ids.get(i));
      g.setSortOrder(i * 10);
      g.setUpdatedBy(actor);
    }
    groups.flush();
    return catalog();
  }

  @Transactional
  public GroupView reorderValues(Long id, List<Long> ids, String actor) {
    ProductAttributeGroup group = require(id);
    validateOrder(ids, group.getValues().stream().map(ProductAttributeValue::getId).toList());
    for (int i = 0; i < ids.size(); i++) {
      ProductAttributeValue v = values.findById(ids.get(i)).orElseThrow();
      v.setSortOrder(i * 10);
      v.setUpdatedBy(actor);
    }
    values.flush();
    return view(group);
  }

  private void validateOrder(List<Long> ids, List<Long> expected) {
    if (ids == null
        || ids.size() != expected.size()
        || new HashSet<>(ids).size() != ids.size()
        || !new HashSet<>(ids).equals(new HashSet<>(expected)))
      throw new IllegalArgumentException("순서에 모든 항목을 한 번씩 포함해야 합니다. 목록을 새로고침해 주세요.");
  }

  public static void version(Long requested, long actual) {
    if (requested == null || requested != actual)
      throw new IllegalStateException("다른 사용자가 변경했거나 저장 버전이 없습니다. 새로고침 후 다시 시도해 주세요.");
  }

  private static void checkLabels(
      Labels labels, String prefix, int max, Map<String, String> errors) {
    String[] keys = {"customer", "production", "management"}, names = {"고객용", "생산팀용", "관리팀용"};
    String[] values =
        labels == null
            ? new String[] {"", "", ""}
            : new String[] {
              text(labels.customer()), text(labels.production()), text(labels.management())
            };
    for (int i = 0; i < 3; i++)
      if (values[i].isBlank() || values[i].length() > max)
        errors.put(prefix + "labels." + keys[i], names[i] + " 표시명은 1~" + max + "자로 입력해 주세요.");
  }

  private static void duplicateLabels(
      Labels a, Labels b, String prefix, Map<String, String> errors, String message) {
    String[] keys = {"customer", "production", "management"}, names = {"고객용", "생산팀용", "관리팀용"};
    String[] left = {a.customer(), a.production(), a.management()},
        right = {b.customer(), b.production(), b.management()};
    for (int i = 0; i < 3; i++)
      if (!normalizedLabel(left[i]).isBlank()
          && normalizedLabel(left[i]).equalsIgnoreCase(normalizedLabel(right[i])))
        errors.put(prefix + "labels." + keys[i], names[i] + " 표시명이 중복됩니다. " + message);
  }
}
