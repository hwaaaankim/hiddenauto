package com.dev.HiddenBATHAuto.service.productmaster;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.dev.HiddenBATHAuto.dto.productmaster.ProductMasterDtos.AttributeImageResponse;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductMasterDtos.GroupResponse;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductMasterDtos.GroupSaveRequest;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductMasterDtos.ReorderRequest;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductMasterDtos.ValueResponse;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductMasterDtos.ValueSaveRequest;
import com.dev.HiddenBATHAuto.enums.productmaster.ProductAttributeRole;
import com.dev.HiddenBATHAuto.enums.productmaster.ProductAttributeInputType;
import com.dev.HiddenBATHAuto.enums.productmaster.ProductDimensionType;
import com.dev.HiddenBATHAuto.model.productmaster.ProductAttributeGroup;
import com.dev.HiddenBATHAuto.model.productmaster.ProductAttributeValue;
import com.dev.HiddenBATHAuto.repository.productmaster.ProductAttributeGroupRepository;
import com.dev.HiddenBATHAuto.repository.productmaster.ProductAttributeValueRepository;
import com.dev.HiddenBATHAuto.repository.productmaster.ProductComponentRepository;
import com.dev.HiddenBATHAuto.repository.productmaster.ProductDynamicPriceRuleRepository;
import com.dev.HiddenBATHAuto.repository.productmaster.ProductPriceMatrixRepository;
import com.dev.HiddenBATHAuto.repository.productmaster.ProductRuleActionRepository;
import com.dev.HiddenBATHAuto.repository.productmaster.ProductRuleConditionRepository;
import com.dev.HiddenBATHAuto.repository.productmaster.ProductStockMovementAddonRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductAttributeService {

    private final ProductAttributeGroupRepository groupRepository;
    private final ProductAttributeValueRepository valueRepository;
    private final ProductComponentRepository componentRepository;
    private final ProductStockMovementAddonRepository movementAddonRepository;
    private final ProductRuleConditionRepository ruleConditionRepository;
    private final ProductRuleActionRepository ruleActionRepository;
    private final ProductPriceMatrixRepository priceMatrixRepository;
    private final ProductDynamicPriceRuleRepository dynamicPriceRuleRepository;
    private final ProductMasterCodeService codeService;
    private final ProductAttributeImageService imageService;

    public List<GroupResponse> getGroups(boolean includeInactive) {
        List<ProductAttributeGroup> groups = groupRepository.findAllByOrderBySortOrderAscIdAsc().stream()
                .filter(group -> includeInactive || group.isActive())
                .toList();
        return toResponses(groups, includeInactive);
    }

    public GroupResponse getGroup(Long groupId) {
        return toResponse(requireGroup(groupId), true);
    }

    @Transactional
    public GroupResponse createGroup(GroupSaveRequest request, String actor) {
        return createGroup(request, List.of(), actor);
    }

    @Transactional
    public GroupResponse createGroup(GroupSaveRequest request, List<MultipartFile> images, String actor) {
        validateGroupStructure(request, null);
        validateGroupLabels(request, null);

        ProductAttributeGroup group = new ProductAttributeGroup();
        group.setGroupCode(codeService.newGroupCode());
        applyGroup(group, request, normalizeActor(actor));
        group.setCreatedBy(normalizeActor(actor));
        group.setSortOrder(groupRepository.findMaxSortOrder() + 10);

        ProductAttributeGroup saved = groupRepository.saveAndFlush(group);
        if (hasUploadImages(images)) {
            imageService.storeForGroup(saved, images, actor);
        }
        return toResponse(saved, true);
    }

    @Transactional
    public GroupResponse updateGroup(Long groupId, GroupSaveRequest request, String actor) {
        return updateGroup(groupId, request, List.of(), actor);
    }

    @Transactional
    public GroupResponse updateGroup(
            Long groupId,
            GroupSaveRequest request,
            List<MultipartFile> images,
            String actor
    ) {
        ProductAttributeGroup group = requireGroup(groupId);
        verifyVersion(request.rowVersion(), group.getRowVersion(), "옵션 그룹");
        validateGroupStructure(request, group);
        validateGroupLabels(request, groupId);

        boolean structureReferenced = componentRepository.existsByGroupId(groupId)
                || ruleConditionRepository.existsBySourceGroupId(groupId)
                || ruleActionRepository.existsByTargetGroupId(groupId)
                || priceMatrixRepository.countReferencesToGroup(groupId) > 0
                || dynamicPriceRuleRepository.existsByQuantityGroupIdOrSourceGroupId(groupId, groupId);
        if (structureReferenced) {
            boolean structureChanged = group.getGroupType() != request.groupType()
                    || group.getSelectionMode() != request.selectionMode()
                    || group.getSystemRole() != request.systemRole()
                    || group.getInputType() != request.inputType();
            if (structureChanged) {
                throw new IllegalStateException(
                        "제품·조건규칙·가격규칙에서 참조하는 그룹은 재고 구분, 선택 방식, 시스템 역할, 입력 유형을 변경할 수 없습니다."
                );
            }
        }

        applyGroup(group, request, normalizeActor(actor));
        group.setUpdatedAt(LocalDateTime.now());
        groupRepository.flush();
        if (hasUploadImages(images)) {
            imageService.storeForGroup(group, images, actor);
        }
        return toResponse(group, true);
    }

    @Transactional
    public void deleteGroup(Long groupId) {
        ProductAttributeGroup group = requireGroup(groupId);
        if (componentRepository.existsByGroupId(groupId)
                || movementAddonRepository.existsByOptionValue_Group_Id(groupId)
                || ruleConditionRepository.existsBySourceGroupId(groupId)
                || ruleActionRepository.existsByTargetGroupId(groupId)
                || priceMatrixRepository.countReferencesToGroup(groupId) > 0
                || dynamicPriceRuleRepository.existsByQuantityGroupIdOrSourceGroupId(groupId, groupId)) {
            throw new IllegalStateException("제품·재고·조건규칙·가격규칙에서 참조 중인 옵션 그룹은 삭제할 수 없습니다. 참조 관계를 먼저 해제해 주세요.");
        }
        imageService.deleteGroupFilesAfterCommit(groupId);
        groupRepository.delete(group);
    }

    @Transactional
    public List<GroupResponse> reorderGroups(ReorderRequest request, String actor) {
        List<ProductAttributeGroup> all = groupRepository.findAllByOrderBySortOrderAscIdAsc();
        List<Long> orderedIds = validateAndAppendIds(request.ids(), all.stream().map(ProductAttributeGroup::getId).toList());
        java.util.Map<Long, ProductAttributeGroup> byId = all.stream()
                .collect(java.util.stream.Collectors.toMap(ProductAttributeGroup::getId, group -> group));

        int order = 10;
        for (Long id : orderedIds) {
            ProductAttributeGroup group = byId.get(id);
            group.setSortOrder(order);
            group.setUpdatedBy(normalizeActor(actor));
            order += 10;
        }
        groupRepository.flush();
        return getGroups(true);
    }

    @Transactional
    public ValueResponse createValue(Long groupId, ValueSaveRequest request, String actor) {
        return createValue(groupId, request, List.of(), actor);
    }

    @Transactional
    public ValueResponse createValue(
            Long groupId,
            ValueSaveRequest request,
            List<MultipartFile> images,
            String actor
    ) {
        ProductAttributeGroup group = requireGroup(groupId);
        validateValueRequest(group, request, null);

        ProductAttributeValue value = new ProductAttributeValue();
        value.setGroup(group);
        value.setValueCode(codeService.newValueCode());
        value.setCreatedBy(normalizeActor(actor));
        value.setSortOrder(valueRepository.findMaxSortOrderByGroupId(groupId) + 10);
        applyValue(value, request, normalizeActor(actor));

        ProductAttributeValue saved = valueRepository.saveAndFlush(value);
        if (hasUploadImages(images)) {
            imageService.storeForValue(saved, images, actor);
        }
        return toValueResponse(saved);
    }

    @Transactional
    public ValueResponse updateValue(Long valueId, ValueSaveRequest request, String actor) {
        return updateValue(valueId, request, List.of(), actor);
    }

    @Transactional
    public ValueResponse updateValue(
            Long valueId,
            ValueSaveRequest request,
            List<MultipartFile> images,
            String actor
    ) {
        ProductAttributeValue value = requireValue(valueId);
        verifyVersion(request.rowVersion(), value.getRowVersion(), "옵션값");
        validateValueRequest(value.getGroup(), request, valueId);

        if ((componentRepository.existsByValueId(valueId)
                || ruleConditionRepository.existsBySourceValueId(valueId)
                || ruleActionRepository.existsByTargetValueId(valueId)
                || dynamicPriceRuleRepository.existsByTriggerValueId(valueId))
                && value.getDimensionType() != request.dimensionType()) {
            throw new IllegalStateException("제품·조건규칙·가격규칙에서 참조하는 옵션값의 입력 방식은 변경할 수 없습니다.");
        }

        applyValue(value, request, normalizeActor(actor));
        value.setUpdatedAt(LocalDateTime.now());
        valueRepository.flush();
        if (hasUploadImages(images)) {
            imageService.storeForValue(value, images, actor);
        }
        return toValueResponse(value);
    }

    @Transactional
    public void deleteValue(Long valueId) {
        ProductAttributeValue value = requireValue(valueId);
        if (componentRepository.existsByValueId(valueId)
                || movementAddonRepository.existsByOptionValueId(valueId)
                || ruleConditionRepository.existsBySourceValueId(valueId)
                || ruleActionRepository.existsByTargetValueId(valueId)
                || dynamicPriceRuleRepository.existsByTriggerValueId(valueId)) {
            throw new IllegalStateException("제품·재고·조건규칙·가격규칙에서 참조 중인 옵션값은 삭제할 수 없습니다. 사용중지로 보존하거나 참조를 먼저 해제해 주세요.");
        }
        imageService.deleteValueFilesAfterCommit(valueId);
        valueRepository.delete(value);
    }

    @Transactional
    public GroupResponse reorderValues(Long groupId, ReorderRequest request, String actor) {
        ProductAttributeGroup group = requireGroup(groupId);
        List<ProductAttributeValue> values = new ArrayList<>(group.getValues());
        List<Long> orderedIds = validateAndAppendIds(request.ids(), values.stream().map(ProductAttributeValue::getId).toList());
        java.util.Map<Long, ProductAttributeValue> byId = values.stream()
                .collect(java.util.stream.Collectors.toMap(ProductAttributeValue::getId, value -> value));

        int order = 10;
        for (Long id : orderedIds) {
            ProductAttributeValue value = byId.get(id);
            value.setSortOrder(order);
            value.setUpdatedBy(normalizeActor(actor));
            order += 10;
        }
        valueRepository.flush();
        return toResponse(group, true);
    }

    private ProductAttributeGroup requireGroup(Long groupId) {
        if (groupId == null) {
            throw new IllegalArgumentException("옵션 그룹 ID가 필요합니다.");
        }
        return groupRepository.findWithValuesById(groupId)
                .orElseThrow(() -> new java.util.NoSuchElementException("옵션 그룹을 찾을 수 없습니다."));
    }

    private ProductAttributeValue requireValue(Long valueId) {
        if (valueId == null) {
            throw new IllegalArgumentException("옵션값 ID가 필요합니다.");
        }
        return valueRepository.findWithGroupById(valueId)
                .orElseThrow(() -> new java.util.NoSuchElementException("옵션값을 찾을 수 없습니다."));
    }

    private void validateGroupLabels(GroupSaveRequest request, Long currentId) {
        String customer = requiredText(request.customerLabel(), "고객용 그룹명", 80);
        String management = requiredText(request.managementLabel(), "관리팀용 그룹명", 80);
        String production = requiredText(request.productionLabel(), "생산팀용 그룹명", 80);

        boolean customerExists = currentId == null
                ? groupRepository.existsByCustomerLabelIgnoreCase(customer)
                : groupRepository.existsByCustomerLabelIgnoreCaseAndIdNot(customer, currentId);
        boolean managementExists = currentId == null
                ? groupRepository.existsByManagementLabelIgnoreCase(management)
                : groupRepository.existsByManagementLabelIgnoreCaseAndIdNot(management, currentId);
        boolean productionExists = currentId == null
                ? groupRepository.existsByProductionLabelIgnoreCase(production)
                : groupRepository.existsByProductionLabelIgnoreCaseAndIdNot(production, currentId);

        if (customerExists || managementExists || productionExists) {
            throw new IllegalStateException("같은 표시 이름을 사용하는 옵션 그룹이 이미 존재합니다.");
        }
    }

    private void validateGroupStructure(GroupSaveRequest request, ProductAttributeGroup current) {
        var groupType = java.util.Objects.requireNonNull(request.groupType(), "그룹 구분이 필요합니다.");
        var selectionMode = java.util.Objects.requireNonNull(request.selectionMode(), "선택 방식이 필요합니다.");
        var systemRole = java.util.Objects.requireNonNull(request.systemRole(), "시스템 역할이 필요합니다.");
        var inputType = java.util.Objects.requireNonNull(request.inputType(), "입력 유형이 필요합니다.");

        if ((systemRole == ProductAttributeRole.CATEGORY || systemRole == ProductAttributeRole.SIZE)
                && (groupType != com.dev.HiddenBATHAuto.enums.productmaster.ProductAttributeGroupType.CORE
                || selectionMode != com.dev.HiddenBATHAuto.enums.productmaster.ProductAttributeSelectionMode.SINGLE)) {
            throw new IllegalArgumentException("대분류와 사이즈 역할은 핵심 구성·하나 선택 방식으로만 등록할 수 있습니다.");
        }

        if (systemRole == ProductAttributeRole.CATEGORY) {
            boolean duplicate = current == null
                    ? groupRepository.existsBySystemRole(systemRole)
                    : groupRepository.existsBySystemRoleAndIdNot(systemRole, current.getId());
            if (duplicate) {
                throw new IllegalStateException(systemRole.getLabelKr() + " 시스템 역할을 사용하는 그룹이 이미 존재합니다.");
            }
        }

        if (systemRole == ProductAttributeRole.SIZE && inputType != ProductAttributeInputType.DIMENSION) {
            throw new IllegalArgumentException("사이즈 역할은 사이즈형 입력으로 등록해야 합니다.");
        }
        if (inputType == ProductAttributeInputType.DIMENSION && systemRole != ProductAttributeRole.SIZE) {
            throw new IllegalArgumentException("사이즈형 입력은 사이즈 역할에서만 사용할 수 있습니다.");
        }
        if (inputType != ProductAttributeInputType.CHOICE
                && selectionMode != com.dev.HiddenBATHAuto.enums.productmaster.ProductAttributeSelectionMode.SINGLE) {
            throw new IllegalArgumentException("숫자형·문자형·사이즈형 입력은 하나 선택 방식만 사용할 수 있습니다.");
        }
        if (groupType == com.dev.HiddenBATHAuto.enums.productmaster.ProductAttributeGroupType.CORE
                && inputType == ProductAttributeInputType.TEXT) {
            throw new IllegalArgumentException("제품코드의 재현성을 위해 제품 정체성 그룹은 자유 문자형을 사용할 수 없습니다. 비규격 옵션값을 등록해 사용해 주세요.");
        }
        if (groupType == com.dev.HiddenBATHAuto.enums.productmaster.ProductAttributeGroupType.ADD_ON
                && inputType != ProductAttributeInputType.CHOICE) {
            throw new IllegalArgumentException("외부 추가옵션의 포함 재고를 정확히 집계하려면 선택형 옵션값으로 등록해야 합니다.");
        }
        if (inputType == ProductAttributeInputType.NUMBER) {
            if (request.minimumValue() != null && request.maximumValue() != null
                    && request.minimumValue().compareTo(request.maximumValue()) > 0) {
                throw new IllegalArgumentException("숫자형 최소값은 최대값보다 클 수 없습니다.");
            }
            if (request.stepValue() != null && request.stepValue().signum() <= 0) {
                throw new IllegalArgumentException("숫자형 입력 간격은 0보다 커야 합니다.");
            }
        }
        if (inputType == ProductAttributeInputType.DIMENSION
                && request.customDimensionType() != ProductDimensionType.WIDTH_HEIGHT
                && request.customDimensionType() != ProductDimensionType.WIDTH_DEPTH_HEIGHT) {
            throw new IllegalArgumentException("비규격 주문에서 받을 치수축을 W-H 또는 W-D-H로 지정해 주세요.");
        }

        if (current == null || current.getValues().isEmpty()) {
            return;
        }
        if (inputType != ProductAttributeInputType.CHOICE
                && inputType != ProductAttributeInputType.DIMENSION) {
            throw new IllegalStateException(
                    "등록된 옵션값이 있는 그룹은 숫자형·문자형으로 변경할 수 없습니다. 옵션값을 먼저 삭제하거나 새 그룹을 만들어 주세요."
            );
        }
        boolean invalidDimension = current.getValues().stream().anyMatch(value -> {
            ProductDimensionType type = value.getDimensionType();
            if (systemRole == ProductAttributeRole.SIZE) {
                return type == ProductDimensionType.NONE;
            }
            return type == ProductDimensionType.WIDTH_HEIGHT
                    || type == ProductDimensionType.WIDTH_DEPTH_HEIGHT;
        });
        if (invalidDimension) {
            throw new IllegalStateException(
                    "기존 옵션값의 입력 방식과 새 시스템 역할이 맞지 않습니다. 옵션값을 먼저 정리해 주세요."
            );
        }
    }

    private void validateValueRequest(ProductAttributeGroup group, ValueSaveRequest request, Long currentId) {
        String customer = requiredText(request.customerLabel(), "고객용 옵션명", 120);
        String management = requiredText(request.managementLabel(), "관리팀용 옵션명", 120);
        String production = requiredText(request.productionLabel(), "생산팀용 옵션명", 120);

        if (group.getInputType() != ProductAttributeInputType.CHOICE
                && group.getInputType() != ProductAttributeInputType.DIMENSION) {
            throw new IllegalStateException("숫자형·문자형 그룹은 별도 옵션값을 등록하지 않고 그룹 자체에 값을 입력합니다.");
        }

        boolean customerExists = currentId == null
                ? valueRepository.existsByGroupIdAndCustomerLabelIgnoreCase(group.getId(), customer)
                : valueRepository.existsByGroupIdAndCustomerLabelIgnoreCaseAndIdNot(group.getId(), customer, currentId);
        boolean managementExists = currentId == null
                ? valueRepository.existsByGroupIdAndManagementLabelIgnoreCase(group.getId(), management)
                : valueRepository.existsByGroupIdAndManagementLabelIgnoreCaseAndIdNot(group.getId(), management, currentId);
        boolean productionExists = currentId == null
                ? valueRepository.existsByGroupIdAndProductionLabelIgnoreCase(group.getId(), production)
                : valueRepository.existsByGroupIdAndProductionLabelIgnoreCaseAndIdNot(group.getId(), production, currentId);

        if (customerExists || managementExists || productionExists) {
            throw new IllegalStateException("같은 그룹 안에 동일한 표시 이름을 사용하는 옵션값이 이미 존재합니다.");
        }

        if (group.getSystemRole() == ProductAttributeRole.SIZE
                && request.dimensionType() == ProductDimensionType.NONE) {
            throw new IllegalArgumentException("사이즈 그룹의 옵션값은 W-H, W-D-H 또는 비규격 입력방식 중 하나여야 합니다.");
        }
        if (group.getSystemRole() != ProductAttributeRole.SIZE
                && request.dimensionType() != ProductDimensionType.NONE
                && request.dimensionType() != ProductDimensionType.CUSTOM) {
            throw new IllegalArgumentException("사이즈 이외의 그룹은 일반 고정값 또는 비규격 주문입력만 선택할 수 있습니다.");
        }
    }

    private void applyGroup(ProductAttributeGroup group, GroupSaveRequest request, String actor) {
        group.setCustomerLabel(requiredText(request.customerLabel(), "고객용 그룹명", 80));
        group.setManagementLabel(requiredText(request.managementLabel(), "관리팀용 그룹명", 80));
        group.setProductionLabel(requiredText(request.productionLabel(), "생산팀용 그룹명", 80));
        group.setGroupType(java.util.Objects.requireNonNull(request.groupType(), "그룹 구분이 필요합니다."));
        group.setSelectionMode(java.util.Objects.requireNonNull(request.selectionMode(), "선택 방식이 필요합니다."));
        group.setSystemRole(java.util.Objects.requireNonNull(request.systemRole(), "시스템 역할이 필요합니다."));
        group.setInputType(java.util.Objects.requireNonNull(request.inputType(), "입력 유형이 필요합니다."));
        group.setQuestionText(optionalText(request.questionText(), 300));
        group.setCustomerGuide(optionalText(request.customerGuide(), 1000));
        group.setRequiredByDefault(request.requiredByDefault());
        group.setUnitLabel(optionalText(request.unitLabel(), 20));
        if (request.inputType() == ProductAttributeInputType.NUMBER) {
            group.setMinimumValue(request.minimumValue());
            group.setMaximumValue(request.maximumValue());
            group.setStepValue(request.stepValue() == null ? java.math.BigDecimal.ONE : request.stepValue());
        } else {
            group.setMinimumValue(null);
            group.setMaximumValue(null);
            group.setStepValue(null);
        }
        group.setCustomDimensionType(request.inputType() == ProductAttributeInputType.DIMENSION
                ? request.customDimensionType()
                : null);
        group.setDescription(optionalText(request.description(), 500));
        group.setActive(request.active());
        group.setUpdatedBy(actor);
    }

    private void applyValue(ProductAttributeValue value, ValueSaveRequest request, String actor) {
        value.setCustomerLabel(requiredText(request.customerLabel(), "고객용 옵션명", 120));
        value.setManagementLabel(requiredText(request.managementLabel(), "관리팀용 옵션명", 120));
        value.setProductionLabel(requiredText(request.productionLabel(), "생산팀용 옵션명", 120));
        value.setDimensionType(java.util.Objects.requireNonNull(request.dimensionType(), "옵션값 입력 방식이 필요합니다."));
        int priceAdjustment = request.priceAdjustment() == null ? 0 : request.priceAdjustment();
        if (priceAdjustment < -1_000_000_000 || priceAdjustment > 1_000_000_000) {
            throw new IllegalArgumentException("공급가 조정액은 -1,000,000,000~1,000,000,000원 범위여야 합니다.");
        }
        value.setPriceAdjustment(priceAdjustment);
        value.setDescription(optionalText(request.description(), 500));
        value.setCustomerGuide(optionalText(request.customerGuide(), 1000));
        value.setActive(request.active());
        value.setUpdatedBy(actor);
    }

    private List<Long> validateAndAppendIds(List<Long> requestedIds, List<Long> allIds) {
        if (requestedIds == null || requestedIds.isEmpty()) {
            throw new IllegalArgumentException("정렬할 ID 목록이 필요합니다.");
        }
        LinkedHashSet<Long> unique = new LinkedHashSet<>(requestedIds);
        if (unique.size() != requestedIds.size() || unique.contains(null)) {
            throw new IllegalArgumentException("정렬 목록에 중복되거나 잘못된 ID가 있습니다.");
        }
        Set<Long> validIds = new HashSet<>(allIds);
        if (!validIds.containsAll(unique)) {
            throw new IllegalArgumentException("다른 그룹에 속하거나 존재하지 않는 ID가 포함되어 있습니다.");
        }
        allIds.stream().filter(id -> !unique.contains(id)).forEach(unique::add);
        return List.copyOf(unique);
    }

    private List<GroupResponse> toResponses(
            List<ProductAttributeGroup> groups,
            boolean includeInactiveValues
    ) {
        if (groups.isEmpty()) {
            return List.of();
        }
        List<Long> groupIds = groups.stream().map(ProductAttributeGroup::getId).toList();
        List<Long> valueIds = groups.stream()
                .map(ProductAttributeGroup::getValues)
                .flatMap(Collection::stream)
                .map(ProductAttributeValue::getId)
                .toList();
        Map<Long, List<AttributeImageResponse>> groupImages = imageService.getGroupImageMap(groupIds);
        Map<Long, List<AttributeImageResponse>> valueImages = imageService.getValueImageMap(valueIds);
        return groups.stream()
                .map(group -> toResponse(group, includeInactiveValues, groupImages, valueImages))
                .toList();
    }

    private GroupResponse toResponse(ProductAttributeGroup group, boolean includeInactiveValues) {
        Map<Long, List<AttributeImageResponse>> groupImages = imageService.getGroupImageMap(List.of(group.getId()));
        List<Long> valueIds = group.getValues().stream().map(ProductAttributeValue::getId).toList();
        Map<Long, List<AttributeImageResponse>> valueImages = imageService.getValueImageMap(valueIds);
        return toResponse(group, includeInactiveValues, groupImages, valueImages);
    }

    private GroupResponse toResponse(
            ProductAttributeGroup group,
            boolean includeInactiveValues,
            Map<Long, List<AttributeImageResponse>> groupImages,
            Map<Long, List<AttributeImageResponse>> valueImages
    ) {
        List<ValueResponse> values = group.getValues().stream()
                .filter(value -> includeInactiveValues || value.isActive())
                .map(value -> toValueResponse(
                        value,
                        valueImages.getOrDefault(value.getId(), Collections.emptyList())
                ))
                .toList();
        return new GroupResponse(
                group.getId(),
                group.getGroupCode(),
                group.getCustomerLabel(),
                group.getManagementLabel(),
                group.getProductionLabel(),
                group.getGroupType(),
                group.getGroupType().getLabelKr(),
                group.getGroupType().getDescription(),
                group.getSelectionMode(),
                group.getSelectionMode().getLabelKr(),
                group.getSystemRole(),
                group.getSystemRole().getLabelKr(),
                group.getInputType(),
                group.getInputType().getLabelKr(),
                group.getInputType().getDescription(),
                group.getQuestionText(),
                group.getCustomerGuide(),
                group.isRequiredByDefault(),
                group.getUnitLabel(),
                group.getMinimumValue(),
                group.getMaximumValue(),
                group.getStepValue(),
                group.getCustomDimensionType(),
                group.getDescription(),
                group.isActive(),
                group.getSortOrder(),
                group.getRowVersion(),
                groupImages.getOrDefault(group.getId(), Collections.emptyList()),
                values
        );
    }

    private ValueResponse toValueResponse(ProductAttributeValue value) {
        return toValueResponse(value, imageService.getValueImages(value.getId()));
    }

    private ValueResponse toValueResponse(
            ProductAttributeValue value,
            List<AttributeImageResponse> images
    ) {
        return new ValueResponse(
                value.getId(),
                value.getGroup().getId(),
                value.getValueCode(),
                value.getCustomerLabel(),
                value.getManagementLabel(),
                value.getProductionLabel(),
                value.getDimensionType(),
                value.getDimensionType().getLabelKr(),
                value.getPriceAdjustment(),
                value.getDescription(),
                value.getCustomerGuide(),
                value.isActive(),
                value.getSortOrder(),
                value.getRowVersion(),
                images
        );
    }

    private String normalizeActor(String actor) {
        String normalized = actor == null || actor.isBlank() ? "SYSTEM" : actor.trim();
        return normalized.length() <= 100 ? normalized : normalized.substring(0, 100);
    }

    private boolean hasUploadImages(List<MultipartFile> images) {
        return images != null && images.stream().anyMatch(image -> image != null && !image.isEmpty());
    }

    private void verifyVersion(Long requested, long current, String target) {
        if (requested == null) {
            throw new IllegalStateException(target + "의 화면 버전이 없습니다. 새로고침 후 다시 시도해 주세요.");
        }
        if (requested.longValue() != current) {
            throw new IllegalStateException(
                    "다른 사용자가 " + target + "을(를) 먼저 변경했습니다. 새로고침 후 최신 내용에서 다시 수정해 주세요."
            );
        }
    }

    private String requiredText(String value, String fieldName, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + "을(를) 입력해 주세요.");
        }
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + "은(는) " + maxLength + "자 이하여야 합니다.");
        }
        return normalized;
    }

    private String optionalText(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException("설명은 " + maxLength + "자 이하여야 합니다.");
        }
        return normalized;
    }
}
