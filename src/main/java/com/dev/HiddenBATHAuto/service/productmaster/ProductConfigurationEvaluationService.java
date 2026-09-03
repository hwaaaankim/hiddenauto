package com.dev.HiddenBATHAuto.service.productmaster;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dev.HiddenBATHAuto.dto.productmaster.ProductMasterDtos.AttributeImageResponse;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductMasterRuleDtos.ConfigurationEvaluationRequest;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductMasterRuleDtos.ConfigurationEvaluationResponse;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductMasterRuleDtos.ConfigurationGroupState;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductMasterRuleDtos.ConfigurationInput;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductMasterRuleDtos.CustomerValueResponse;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductMasterRuleDtos.PriceLineResponse;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductMasterRuleDtos.PriceQuoteResponse;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductMasterRuleDtos.RuleExecutionResponse;
import com.dev.HiddenBATHAuto.enums.productmaster.ProductAttributeGroupType;
import com.dev.HiddenBATHAuto.enums.productmaster.ProductAttributeInputType;
import com.dev.HiddenBATHAuto.enums.productmaster.ProductAttributeSelectionMode;
import com.dev.HiddenBATHAuto.enums.productmaster.ProductDimensionType;
import com.dev.HiddenBATHAuto.enums.productmaster.ProductDynamicPriceApplyMode;
import com.dev.HiddenBATHAuto.enums.productmaster.ProductDynamicPriceRuleType;
import com.dev.HiddenBATHAuto.enums.productmaster.ProductPriceMatrixLookupMode;
import com.dev.HiddenBATHAuto.enums.productmaster.ProductPricingMode;
import com.dev.HiddenBATHAuto.enums.productmaster.ProductRuleActionType;
import com.dev.HiddenBATHAuto.enums.productmaster.ProductRuleMatchMode;
import com.dev.HiddenBATHAuto.enums.productmaster.ProductRuleOperator;
import com.dev.HiddenBATHAuto.enums.productmaster.ProductRuleSourceField;
import com.dev.HiddenBATHAuto.model.productmaster.ProductAttributeGroup;
import com.dev.HiddenBATHAuto.model.productmaster.ProductAttributeValue;
import com.dev.HiddenBATHAuto.model.productmaster.ProductComponent;
import com.dev.HiddenBATHAuto.model.productmaster.ProductConfigurationRule;
import com.dev.HiddenBATHAuto.model.productmaster.ProductDynamicPriceRule;
import com.dev.HiddenBATHAuto.model.productmaster.ProductMaster;
import com.dev.HiddenBATHAuto.model.productmaster.ProductPriceMatrix;
import com.dev.HiddenBATHAuto.model.productmaster.ProductPriceMatrixCell;
import com.dev.HiddenBATHAuto.model.productmaster.ProductRuleAction;
import com.dev.HiddenBATHAuto.model.productmaster.ProductRuleCondition;
import com.dev.HiddenBATHAuto.repository.productmaster.ProductAttributeGroupRepository;
import com.dev.HiddenBATHAuto.repository.productmaster.ProductComponentRepository;
import com.dev.HiddenBATHAuto.repository.productmaster.ProductConfigurationRuleRepository;
import com.dev.HiddenBATHAuto.repository.productmaster.ProductDynamicPriceRuleRepository;
import com.dev.HiddenBATHAuto.repository.productmaster.ProductMasterRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductConfigurationEvaluationService {

    private static final int MAX_RULE_PASSES = 8;
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final ProductMasterRepository productRepository;
    private final ProductAttributeGroupRepository groupRepository;
    private final ProductComponentRepository componentRepository;
    private final ProductConfigurationRuleRepository configurationRuleRepository;
    private final ProductDynamicPriceRuleRepository priceRuleRepository;
    private final ProductAttributeImageService imageService;

    public ConfigurationEvaluationResponse evaluate(
            Long productId,
            ConfigurationEvaluationRequest request,
            boolean adminPreview
    ) {
        ProductMaster product = requireProduct(productId);
        List<ProductAttributeGroup> groups = groupRepository.findAllByOrderBySortOrderAscIdAsc().stream()
                .filter(group -> group.isActive() || adminPreview)
                .toList();
        List<ProductComponent> components = componentRepository.findDetailedByProductId(productId);
        Map<Long, List<ProductComponent>> componentsByGroup = components.stream()
                .collect(Collectors.groupingBy(component -> component.getGroup().getId(), LinkedHashMap::new,
                        Collectors.toList()));

        LinkedHashMap<Long, State> states = new LinkedHashMap<>();
        for (ProductAttributeGroup group : groups) {
            List<ProductComponent> defaults = componentsByGroup.getOrDefault(group.getId(), List.of());
            State state = new State(group);
            state.visible = !defaults.isEmpty();
            state.required = state.visible && group.isRequiredByDefault()
                    && group.getGroupType() != ProductAttributeGroupType.ADD_ON;
            applyDefaults(state, defaults);
            states.put(group.getId(), state);
        }

        List<String> warnings = new ArrayList<>();
        applyInputs(states, request == null ? null : request.inputs(), warnings, adminPreview);
        List<String> notices = new ArrayList<>();
        LinkedHashMap<String, RuleExecutionResponse> fired = new LinkedHashMap<>();
        applyConfigurationRules(productId, states, notices, warnings, fired);

        List<String> errors = validateConfiguration(states);
        PriceQuoteResponse price = calculatePrice(product, states, warnings, errors);
        return new ConfigurationEvaluationResponse(
                product.getId(), product.getProductName(), product.getCatalogCode(), product.getProductCode(),
                product.getCurrentStock(), errors.isEmpty(), distinct(errors), distinct(warnings), distinct(notices),
                List.copyOf(fired.values()), toGroupResponses(states), price
        );
    }

    public ConfigurationEvaluationResponse evaluatePublicToken(
            String token,
            ConfigurationEvaluationRequest request
    ) {
        String normalized = token == null ? "" : token.trim();
        ProductMaster product = productRepository.findByQrPublicToken(normalized)
                .filter(item -> item.getStatus() != com.dev.HiddenBATHAuto.enums.productmaster.ProductMasterStatus.DRAFT)
                .orElseThrow(() -> new java.util.NoSuchElementException("공개된 제품 정보를 찾을 수 없습니다."));
        return evaluate(product.getId(), request, false);
    }

    private void applyDefaults(State state, List<ProductComponent> defaults) {
        for (ProductComponent component : defaults) {
            if (component.getValue() != null) {
                state.allowedValueIds.add(component.getValue().getId());
                if (state.group.getGroupType() != ProductAttributeGroupType.ADD_ON) {
                    state.selectedValueIds.add(component.getValue().getId());
                    state.defaultSelectedValueIds.add(component.getValue().getId());
                }
            }
            state.widthMm = component.getWidthMm();
            state.depthMm = component.getDepthMm();
            state.heightMm = component.getHeightMm();
            state.numberValue = component.getNumericValue();
            state.textValue = component.getTextValue();
        }
        boolean hasCustomChoice = defaults.stream().map(ProductComponent::getValue).filter(Objects::nonNull)
                .anyMatch(value -> value.getDimensionType() == ProductDimensionType.CUSTOM);
        state.locked = state.group.getGroupType() == ProductAttributeGroupType.CORE
                && !defaults.isEmpty() && !hasCustomChoice;
    }

    private void applyInputs(
            Map<Long, State> states,
            List<ConfigurationInput> inputs,
            List<String> warnings,
            boolean adminPreview
    ) {
        if (inputs == null) return;
        if (inputs.size() > 100) throw new IllegalArgumentException("구성 입력은 최대 100개까지 전송할 수 있습니다.");
        Set<Long> duplicateGuard = new HashSet<>();
        for (ConfigurationInput input : inputs) {
            if (input == null || input.groupId() == null || !duplicateGuard.add(input.groupId())) {
                throw new IllegalArgumentException("구성 입력에 중복되거나 잘못된 그룹이 있습니다.");
            }
            State state = states.get(input.groupId());
            if (state == null) throw new IllegalArgumentException("존재하지 않거나 사용중지된 옵션 그룹이 포함되어 있습니다.");
            if (state.locked && !adminPreview) {
                warnings.add(state.group.getCustomerLabel() + "은 규격제품 고정 사양이므로 변경하지 않았습니다.");
                continue;
            }
            state.selectedValueIds.clear();
            if (input.valueIds() != null) state.selectedValueIds.addAll(input.valueIds());
            state.widthMm = input.widthMm();
            state.depthMm = input.depthMm();
            state.heightMm = input.heightMm();
            state.numberValue = input.numberValue();
            state.textValue = normalizeText(input.textValue());
        }
    }

    private void applyConfigurationRules(
            Long productId,
            Map<Long, State> states,
            List<String> notices,
            List<String> warnings,
            Map<String, RuleExecutionResponse> fired
    ) {
        List<ProductConfigurationRule> rules = configurationRuleRepository.findActiveForProduct(productId);
        Set<String> signatures = new HashSet<>();
        for (int pass = 0; pass < MAX_RULE_PASSES; pass++) {
            String before = signature(states);
            if (!signatures.add(before)) {
                warnings.add("조건 규칙이 순환하는 상태가 감지되어 마지막 안정 상태에서 계산을 중단했습니다. 관리자에게 규칙 우선순위를 확인해 주세요.");
                break;
            }
            for (ProductConfigurationRule rule : rules) {
                boolean matches = rule.getMatchMode() == ProductRuleMatchMode.ALL
                        ? rule.getConditions().stream().allMatch(condition -> matches(condition, states))
                        : rule.getConditions().stream().anyMatch(condition -> matches(condition, states));
                if (!matches) continue;
                for (ProductRuleAction action : rule.getActions()) applyAction(action, states, notices);
                fired.putIfAbsent(rule.getRuleCode(), new RuleExecutionResponse(
                        rule.getRuleCode(), rule.getRuleName(), rule.getPriority(), ruleExplanation(rule)
                ));
            }
            if (before.equals(signature(states))) break;
            if (pass == MAX_RULE_PASSES - 1) {
                warnings.add("조건 규칙을 최대 " + MAX_RULE_PASSES + "회 적용했습니다. 규칙 간 순환 여부를 확인해 주세요.");
            }
        }
    }

    private boolean matches(ProductRuleCondition condition, Map<Long, State> states) {
        State state = states.get(condition.getSourceGroup().getId());
        if (state == null) return false;
        if (condition.getSourceField() == ProductRuleSourceField.SELECTED_VALUE) {
            boolean answered = !state.selectedValueIds.isEmpty();
            boolean selected = condition.getSourceValue() != null
                    && state.selectedValueIds.contains(condition.getSourceValue().getId());
            return condition.getOperator() == ProductRuleOperator.EQUALS
                    ? selected
                    : answered && !selected;
        }
        BigDecimal actual = sourceNumber(state, condition.getSourceField());
        if (actual == null || condition.getComparisonFrom() == null) return false;
        return switch (condition.getOperator()) {
            case EQUALS -> actual.compareTo(condition.getComparisonFrom()) == 0;
            case NOT_EQUALS -> actual.compareTo(condition.getComparisonFrom()) != 0;
            case GREATER_THAN_OR_EQUAL -> actual.compareTo(condition.getComparisonFrom()) >= 0;
            case LESS_THAN_OR_EQUAL -> actual.compareTo(condition.getComparisonFrom()) <= 0;
            case BETWEEN -> condition.getComparisonTo() != null
                    && actual.compareTo(condition.getComparisonFrom()) >= 0
                    && actual.compareTo(condition.getComparisonTo()) <= 0;
        };
    }

    private void applyAction(ProductRuleAction action, Map<Long, State> states, List<String> notices) {
        State state = states.get(action.getTargetGroup().getId());
        if (state == null) return;
        if (state.locked && Set.of(
                ProductRuleActionType.HIDE_GROUP,
                ProductRuleActionType.DISABLE_VALUE,
                ProductRuleActionType.SET_VALUE,
                ProductRuleActionType.SET_NUMBER
        ).contains(action.getActionType())) {
            return;
        }
        switch (action.getActionType()) {
            case SHOW_GROUP -> state.visible = true;
            case HIDE_GROUP -> {
                state.visible = false;
                state.required = false;
                state.clearAnswer();
            }
            case REQUIRE_GROUP -> {
                state.visible = true;
                state.required = true;
            }
            case OPTIONAL_GROUP -> state.required = false;
            case ENABLE_VALUE -> {
                if (action.getTargetValue() != null) {
                    state.allowedValueIds.add(action.getTargetValue().getId());
                    state.disabledValueIds.remove(action.getTargetValue().getId());
                }
            }
            case DISABLE_VALUE -> {
                if (action.getTargetValue() != null) {
                    state.disabledValueIds.add(action.getTargetValue().getId());
                    state.selectedValueIds.remove(action.getTargetValue().getId());
                }
            }
            case SET_VALUE -> {
                if (action.getTargetValue() != null) {
                    state.allowedValueIds.add(action.getTargetValue().getId());
                    if (state.group.getSelectionMode() == ProductAttributeSelectionMode.SINGLE) {
                        state.selectedValueIds.clear();
                    }
                    state.selectedValueIds.add(action.getTargetValue().getId());
                }
            }
            case SET_NUMBER -> state.numberValue = action.getActionNumber();
            case ADD_NOTICE -> {
                if (action.getMessage() != null && !action.getMessage().isBlank()) notices.add(action.getMessage());
            }
        }
    }

    private List<String> validateConfiguration(Map<Long, State> states) {
        List<String> errors = new ArrayList<>();
        for (State state : states.values()) {
            if (!state.visible) continue;
            ProductAttributeGroup group = state.group;
            if (group.getInputType() == ProductAttributeInputType.CHOICE
                    || group.getInputType() == ProductAttributeInputType.DIMENSION) {
                Set<Long> knownValueIds = availableValues(state).stream()
                        .map(ProductAttributeValue::getId).collect(Collectors.toSet());
                if (!knownValueIds.containsAll(state.selectedValueIds)) {
                    errors.add(group.getCustomerLabel() + "에 존재하지 않는 선택값이 포함되어 있습니다.");
                    continue;
                }
                if (group.getSelectionMode() == ProductAttributeSelectionMode.SINGLE && state.selectedValueIds.size() > 1) {
                    errors.add(group.getCustomerLabel() + "은 하나만 선택할 수 있습니다.");
                }
                boolean customSelected = availableValues(state).stream()
                        .filter(value -> state.selectedValueIds.contains(value.getId()))
                        .anyMatch(value -> value.getDimensionType() == ProductDimensionType.CUSTOM);
                if (customSelected && state.selectedValueIds.size() > 1) {
                    errors.add(group.getCustomerLabel() + "의 비규격 값은 다른 값과 함께 선택할 수 없습니다.");
                }
                if (state.selectedValueIds.stream().anyMatch(state.disabledValueIds::contains)) {
                    errors.add(group.getCustomerLabel() + "에 현재 조건에서 선택할 수 없는 값이 포함되어 있습니다.");
                }
                if (state.required && state.selectedValueIds.isEmpty()) {
                    errors.add(group.getCustomerLabel() + "을(를) 선택해 주세요.");
                    continue;
                }
                validateCustomOrDimension(state, errors);
            } else if (group.getInputType() == ProductAttributeInputType.NUMBER) {
                if (state.required && state.numberValue == null) {
                    errors.add(group.getCustomerLabel() + "을(를) 입력해 주세요.");
                } else if (state.numberValue != null) {
                    validateNumber(state, errors);
                }
            } else if (group.getInputType() == ProductAttributeInputType.TEXT
                    && state.required && (state.textValue == null || state.textValue.isBlank())) {
                errors.add(group.getCustomerLabel() + "을(를) 입력해 주세요.");
            }
        }
        return errors;
    }

    private void validateCustomOrDimension(State state, List<String> errors) {
        List<ProductAttributeValue> selected = state.group.getValues().stream()
                .filter(value -> state.selectedValueIds.contains(value.getId())).toList();
        for (ProductAttributeValue value : selected) {
            ProductDimensionType type = value.getDimensionType();
            if (type == ProductDimensionType.WIDTH_HEIGHT) {
                requireDimension(state.widthMm, "W", state.group, errors);
                requireDimension(state.heightMm, "H", state.group, errors);
                if (state.depthMm != null) errors.add(state.group.getCustomerLabel() + " 2차원 사이즈에는 D를 입력할 수 없습니다.");
            } else if (type == ProductDimensionType.WIDTH_DEPTH_HEIGHT) {
                requireDimension(state.widthMm, "W", state.group, errors);
                requireDimension(state.depthMm, "D", state.group, errors);
                requireDimension(state.heightMm, "H", state.group, errors);
            } else if (type == ProductDimensionType.CUSTOM) {
                if (state.group.getInputType() == ProductAttributeInputType.DIMENSION) {
                    ProductDimensionType custom = state.group.getCustomDimensionType();
                    requireDimension(state.widthMm, "W", state.group, errors);
                    if (custom == ProductDimensionType.WIDTH_DEPTH_HEIGHT) requireDimension(state.depthMm, "D", state.group, errors);
                    requireDimension(state.heightMm, "H", state.group, errors);
                } else if (state.textValue == null || state.textValue.isBlank()) {
                    errors.add(state.group.getCustomerLabel() + "의 비규격 요청 내용을 입력해 주세요.");
                }
            }
        }
    }

    private void validateNumber(State state, List<String> errors) {
        ProductAttributeGroup group = state.group;
        if (group.getMinimumValue() != null && state.numberValue.compareTo(group.getMinimumValue()) < 0) {
            errors.add(group.getCustomerLabel() + "은(는) " + group.getMinimumValue() + group.getUnitLabel() + " 이상이어야 합니다.");
        }
        if (group.getMaximumValue() != null && state.numberValue.compareTo(group.getMaximumValue()) > 0) {
            errors.add(group.getCustomerLabel() + "은(는) " + group.getMaximumValue() + group.getUnitLabel() + " 이하여야 합니다.");
        }
        if (group.getStepValue() != null && group.getStepValue().signum() > 0) {
            BigDecimal start = group.getMinimumValue() == null ? BigDecimal.ZERO : group.getMinimumValue();
            BigDecimal remainder = state.numberValue.subtract(start).remainder(group.getStepValue()).abs();
            if (remainder.compareTo(BigDecimal.ZERO) != 0) {
                errors.add(group.getCustomerLabel() + "은(는) " + group.getStepValue() + group.getUnitLabel() + " 단위로 입력해 주세요.");
            }
        }
    }

    private PriceQuoteResponse calculatePrice(
            ProductMaster product,
            Map<Long, State> states,
            List<String> warnings,
            List<String> errors
    ) {
        List<ProductDynamicPriceRule> rules = priceRuleRepository.findActiveForProduct(product.getId());
        Set<Long> handledTriggers = rules.stream()
                .filter(rule -> rule.getRuleType() == ProductDynamicPriceRuleType.FIXED_ADD
                        || rule.getRuleType() == ProductDynamicPriceRuleType.OPTION_X_NUMBER)
                .map(ProductDynamicPriceRule::getTriggerValue).filter(Objects::nonNull)
                .map(ProductAttributeValue::getId).collect(Collectors.toSet());
        Set<Long> selectedIds = states.values().stream().filter(state -> state.visible)
                .map(state -> state.selectedValueIds).flatMap(Collection::stream)
                .collect(Collectors.toSet());

        long base = product.getPricingMode() == ProductPricingMode.RULE_ENGINE
                ? product.getBaseSupplyPrice()
                : product.getSupplyPrice();
        long additions = 0;
        List<PriceLineResponse> lines = new ArrayList<>();
        List<String> explanations = new ArrayList<>();
        for (State state : states.values()) {
            if (!state.visible) continue;
            for (ProductAttributeValue value : state.group.getValues()) {
                if (!selectedIds.contains(value.getId()) || handledTriggers.contains(value.getId())
                        || value.getPriceAdjustment() == 0) continue;
                boolean includedInStoredBaseline = product.getPricingMode() == ProductPricingMode.FIXED
                        && state.defaultSelectedValueIds.contains(value.getId());
                includedInStoredBaseline = includedInStoredBaseline
                        || (product.getPricingMode() == ProductPricingMode.BASE_PLUS_COMPONENTS
                        && state.group.getGroupType() == ProductAttributeGroupType.CORE
                        && state.defaultSelectedValueIds.contains(value.getId()));
                if (includedInStoredBaseline) continue;
                additions = safeAdd(additions, value.getPriceAdjustment());
                lines.add(new PriceLineResponse(
                        "VALUE:" + value.getValueCode(), value.getCustomerLabel(), "옵션 고정금액",
                        value.getPriceAdjustment(), value.getPriceAdjustment(), BigDecimal.ONE, null
                ));
            }
            if (product.getPricingMode() == ProductPricingMode.BASE_PLUS_COMPONENTS
                    && state.group.getGroupType() == ProductAttributeGroupType.CORE
                    && !state.locked) {
                for (ProductAttributeValue defaultValue : state.group.getValues()) {
                    if (!state.defaultSelectedValueIds.contains(defaultValue.getId())
                            || selectedIds.contains(defaultValue.getId())
                            || defaultValue.getPriceAdjustment() == 0) continue;
                    int reversal = -defaultValue.getPriceAdjustment();
                    additions = safeAdd(additions, reversal);
                    lines.add(new PriceLineResponse(
                            "BASE-REVERSAL:" + defaultValue.getValueCode(),
                            defaultValue.getCustomerLabel() + " 기준 조정 해제",
                            "등록 기준 사양에서 고객 선택으로 변경",
                            reversal, reversal, BigDecimal.ONE, null
                    ));
                }
            }
        }

        for (ProductDynamicPriceRule rule : rules) {
            if (rule.getTriggerValue() != null && !selectedIds.contains(rule.getTriggerValue().getId())) continue;
            PriceResult result = evaluatePriceRule(rule, states, warnings, errors);
            if (result == null) continue;
            if (rule.getApplyMode() == ProductDynamicPriceApplyMode.REPLACE_BASE) {
                base = result.amount();
                explanations.add(rule.getRuleName() + " 규칙이 기본 공급가를 " + formatMoney(result.amount()) + "원으로 대체했습니다.");
            } else {
                additions = safeAdd(additions, result.amount());
            }
            lines.add(new PriceLineResponse(
                    rule.getPriceRuleCode(), rule.getRuleName(), result.formula(), safeInt(result.amount()),
                    result.unitPrice(), result.quantity(), rule.getId()
            ));
        }
        long supplyLong = safeAdd(base, additions);
        if (supplyLong < 0 || supplyLong > 2_000_000_000L) {
            warnings.add("계산 공급가가 저장 가능 범위를 벗어났습니다. 관리자에게 가격 규칙을 확인해 주세요.");
            supplyLong = Math.max(0, Math.min(2_000_000_000L, supplyLong));
        }
        int supply = (int) supplyLong;
        BigDecimal vatRate = product.getVatRate();
        int vat = BigDecimal.valueOf(supply).multiply(vatRate).divide(HUNDRED, 0, RoundingMode.HALF_UP).intValue();
        long total = (long) supply + vat;
        return new PriceQuoteResponse(
                safeInt(base), List.copyOf(lines), supply, vatRate, vat, safeInt(total), List.copyOf(explanations)
        );
    }

    private PriceResult evaluatePriceRule(
            ProductDynamicPriceRule rule,
            Map<Long, State> states,
            List<String> warnings,
            List<String> errors
    ) {
        return switch (rule.getRuleType()) {
            case FIXED_ADD -> {
                if (rule.getTriggerValue() == null && rule.getAmount() == null) {
                    errors.add(rule.getRuleName() + " 가격 규칙의 기준 옵션과 금액이 비어 있습니다. 관리팀에 문의해 주세요.");
                    yield null;
                }
                int amount = rule.getAmount() == null ? rule.getTriggerValue().getPriceAdjustment() : rule.getAmount();
                yield new PriceResult(amount, "선택 시 고정금액", amount, BigDecimal.ONE);
            }
            case OPTION_X_NUMBER -> {
                State quantityState = state(states, rule.getQuantityGroup());
                if (quantityState == null || !quantityState.visible) {
                    errors.add(rule.getRuleName() + " 가격 계산에 필요한 "
                            + customerLabel(rule.getQuantityGroup(), "수량") + " 질문이 활성화되지 않았습니다. 관리팀에 문의해 주세요.");
                    yield null;
                }
                if (quantityState.numberValue == null) {
                    errors.add(customerLabel(rule.getQuantityGroup(), "수량") + "을(를) 입력해야 "
                            + rule.getRuleName() + " 금액을 계산할 수 있습니다.");
                    yield null;
                }
                if (rule.getTriggerValue() == null && rule.getAmount() == null) {
                    errors.add(rule.getRuleName() + " 가격 규칙의 기준 옵션과 단가가 비어 있습니다. 관리팀에 문의해 주세요.");
                    yield null;
                }
                int unit = rule.getAmount() == null ? rule.getTriggerValue().getPriceAdjustment() : rule.getAmount();
                long amount = BigDecimal.valueOf(unit).multiply(quantityState.numberValue)
                        .setScale(0, RoundingMode.HALF_UP).longValueExact();
                yield new PriceResult(amount,
                        formatMoney(unit) + "원 × " + quantityState.numberValue.stripTrailingZeros().toPlainString()
                                + unit(quantityState.group), unit, quantityState.numberValue);
            }
            case MATRIX -> evaluateMatrix(rule, states, warnings, errors);
            case STEP_ADD -> {
                State source = state(states, rule.getSourceGroup());
                if ((source == null || !source.visible) && rule.getTriggerValue() == null) {
                    yield null;
                }
                BigDecimal actual = source == null ? null : sourceNumber(source, rule.getSourceField());
                if (actual == null) {
                    errors.add(rule.getRuleName() + " 가격 계산에 필요한 "
                            + customerLabel(rule.getSourceGroup(), "기준") + " 값을 입력해 주세요.");
                    yield null;
                }
                if (rule.getBaseNumber() == null || rule.getStepNumber() == null
                        || rule.getStepNumber().signum() <= 0 || rule.getStepAmount() == null) {
                    errors.add(rule.getRuleName() + " 가격 규칙의 구간 기준이 완전하지 않습니다. 관리팀에 문의해 주세요.");
                    yield null;
                }
                if (actual.compareTo(rule.getBaseNumber()) <= 0) yield null;
                BigDecimal excess = actual.subtract(rule.getBaseNumber());
                BigDecimal steps = excess.divide(rule.getStepNumber(), 0, RoundingMode.CEILING);
                long amount = steps.multiply(BigDecimal.valueOf(rule.getStepAmount())).longValueExact();
                yield new PriceResult(amount,
                        "(" + actual.stripTrailingZeros().toPlainString() + " - " + rule.getBaseNumber()
                                + ") ÷ " + rule.getStepNumber() + " 올림 × " + formatMoney(rule.getStepAmount()) + "원",
                        rule.getStepAmount(), steps);
            }
        };
    }

    private PriceResult evaluateMatrix(
            ProductDynamicPriceRule rule,
            Map<Long, State> states,
            List<String> warnings,
            List<String> errors
    ) {
        ProductPriceMatrix matrix = rule.getMatrix();
        if (matrix == null || !matrix.isActive()) {
            errors.add(rule.getRuleName() + "에 연결된 가격표를 사용할 수 없습니다. 관리팀에 문의해 주세요.");
            return null;
        }
        State xState = states.get(matrix.getXGroup().getId());
        State yState = states.get(matrix.getYGroup().getId());
        if (rule.getTriggerValue() == null
                && (xState == null || yState == null || !xState.visible || !yState.visible)) {
            return null;
        }
        BigDecimal rawX = xState == null ? null : sourceNumber(xState, matrix.getXField());
        BigDecimal rawY = yState == null ? null : sourceNumber(yState, matrix.getYField());
        if (rawX == null || rawY == null) {
            errors.add(rule.getRuleName() + " 가격 계산에 필요한 "
                    + matrix.getXGroup().getCustomerLabel() + " / "
                    + matrix.getYGroup().getCustomerLabel() + " 값을 입력해 주세요.");
            return null;
        }

        BigDecimal lookupRawX = roundUp(rawX, matrix.getXRoundUnit());
        BigDecimal lookupRawY = roundUp(rawY, matrix.getYRoundUnit());
        BigDecimal matrixY = lookupRawY;
        long extensionAmount = 0;
        BigDecimal extensionSteps = BigDecimal.ZERO;
        if (matrix.isExtensionEnabled() && matrix.getExtensionStart() != null
                && lookupRawY.compareTo(matrix.getExtensionStart()) > 0) {
            if (matrix.getExtensionUnit() == null || matrix.getExtensionUnit().signum() <= 0
                    || matrix.getExtensionAmount() == null) {
                errors.add(matrix.getMatrixName() + " 가격표의 초과 구간 규칙이 완전하지 않습니다. 관리팀에 문의해 주세요.");
                return null;
            }
            matrixY = matrix.getExtensionStart();
            extensionSteps = lookupRawY.subtract(matrix.getExtensionStart())
                    .divide(matrix.getExtensionUnit(), 0, RoundingMode.CEILING);
            extensionAmount = extensionSteps.multiply(BigDecimal.valueOf(matrix.getExtensionAmount())).longValueExact();
        }
        List<BigDecimal> xAxis = matrix.getCells().stream().map(ProductPriceMatrixCell::getXValue).distinct().sorted().toList();
        List<BigDecimal> yAxis = matrix.getCells().stream().map(ProductPriceMatrixCell::getYValue).distinct().sorted().toList();
        BigDecimal x = chooseAxis(lookupRawX, xAxis, matrix.getLookupMode());
        BigDecimal y = chooseAxis(matrixY, yAxis, matrix.getLookupMode());
        if (x == null || y == null) {
            errors.add(matrix.getMatrixName() + " 가격표에서 입력 치수에 맞는 구간을 찾지 못했습니다. 관리팀에 문의해 주세요.");
            return null;
        }
        ProductPriceMatrixCell cell = matrix.getCells().stream()
                .filter(item -> item.getXValue().compareTo(x) == 0 && item.getYValue().compareTo(y) == 0)
                .findFirst().orElse(null);
        if (cell == null) {
            errors.add(matrix.getMatrixName() + " 가격표의 X=" + x + ", Y=" + y
                    + " 교차 금액이 비어 있습니다. 관리팀에 문의해 주세요.");
            return null;
        }
        long amount = safeAdd(cell.getAmount(), extensionAmount);
        String formula = "입력 " + rawX.stripTrailingZeros().toPlainString() + "×"
                + rawY.stripTrailingZeros().toPlainString() + " → 가격표 " + x + "×" + y;
        if (extensionAmount != 0) {
            formula += " + 초과 " + extensionSteps.toPlainString() + "구간 × "
                    + formatMoney(matrix.getExtensionAmount()) + "원";
        }
        return new PriceResult(amount, formula, null, null);
    }

    private List<ConfigurationGroupState> toGroupResponses(Map<Long, State> states) {
        List<Long> groupIds = states.keySet().stream().toList();
        List<Long> valueIds = states.values().stream().map(state -> state.group.getValues()).flatMap(Collection::stream)
                .map(ProductAttributeValue::getId).toList();
        Map<Long, List<AttributeImageResponse>> groupImages = imageService.getGroupImageMap(groupIds);
        Map<Long, List<AttributeImageResponse>> valueImages = imageService.getValueImageMap(valueIds);
        return states.values().stream().map(state -> {
            ProductAttributeGroup group = state.group;
            List<CustomerValueResponse> values = availableValues(state).stream()
                    .filter(value -> value.isActive() || state.selectedValueIds.contains(value.getId()))
                    .map(value -> new CustomerValueResponse(
                            value.getId(), value.getValueCode(), value.getCustomerLabel(), value.getCustomerGuide(),
                            value.getDimensionType(), value.getPriceAdjustment(),
                            state.disabledValueIds.contains(value.getId()),
                            valueImages.getOrDefault(value.getId(), List.of())
                    )).toList();
            return new ConfigurationGroupState(
                    group.getId(), group.getGroupCode(), group.getCustomerLabel(),
                    group.getQuestionText() == null ? group.getCustomerLabel() + "을(를) 선택해 주세요." : group.getQuestionText(),
                    group.getCustomerGuide(), group.getGroupType(), group.getGroupType().getLabelKr(),
                    group.getInputType(), group.getInputType().getLabelKr(),
                    group.getSelectionMode(), group.getSelectionMode().getLabelKr(),
                    state.visible, state.required, state.locked,
                    group.getUnitLabel(), group.getMinimumValue(), group.getMaximumValue(), group.getStepValue(),
                    group.getCustomDimensionType(), List.copyOf(state.selectedValueIds), state.widthMm, state.depthMm,
                    state.heightMm, state.numberValue, state.textValue, values,
                    groupImages.getOrDefault(group.getId(), List.of()), group.getSortOrder()
            );
        }).sorted(Comparator.comparingInt(ConfigurationGroupState::sortOrder)).toList();
    }

    private BigDecimal sourceNumber(State state, ProductRuleSourceField field) {
        if (field == null) return null;
        return switch (field) {
            case WIDTH_MM -> state.widthMm == null ? null : BigDecimal.valueOf(state.widthMm);
            case DEPTH_MM -> state.depthMm == null ? null : BigDecimal.valueOf(state.depthMm);
            case HEIGHT_MM -> state.heightMm == null ? null : BigDecimal.valueOf(state.heightMm);
            case NUMBER_VALUE -> state.numberValue;
            case SELECTED_VALUE -> null;
        };
    }

    private List<ProductAttributeValue> availableValues(State state) {
        if (state.allowedValueIds.isEmpty()) {
            return state.group.getValues();
        }
        return state.group.getValues().stream()
                .filter(value -> state.allowedValueIds.contains(value.getId()))
                .toList();
    }

    private BigDecimal chooseAxis(BigDecimal value, List<BigDecimal> axis, ProductPriceMatrixLookupMode mode) {
        if (mode == ProductPriceMatrixLookupMode.EXACT) {
            return axis.stream().filter(item -> item.compareTo(value) == 0).findFirst().orElse(null);
        }
        if (mode == ProductPriceMatrixLookupMode.CEILING) {
            return axis.stream().filter(item -> item.compareTo(value) >= 0).findFirst().orElse(null);
        }
        return axis.stream().filter(item -> item.compareTo(value) <= 0).reduce((left, right) -> right).orElse(null);
    }

    private BigDecimal roundUp(BigDecimal value, BigDecimal unit) {
        if (unit == null || unit.signum() <= 0) return value;
        return value.divide(unit, 0, RoundingMode.CEILING).multiply(unit).stripTrailingZeros();
    }

    private void requireDimension(Integer value, String axis, ProductAttributeGroup group, List<String> errors) {
        if (value == null || value < 1 || value > 100_000) {
            errors.add(group.getCustomerLabel() + "의 " + axis + " 치수를 1~100000mm 범위로 입력해 주세요.");
        }
    }

    private String signature(Map<Long, State> states) {
        return states.values().stream().map(state -> state.group.getId() + ":" + state.visible + ":" + state.required
                + ":" + state.selectedValueIds + ":" + state.disabledValueIds + ":" + state.numberValue)
                .collect(Collectors.joining("|"));
    }

    private String ruleExplanation(ProductConfigurationRule rule) {
        String when = rule.getConditions().stream().map(this::conditionText).collect(Collectors.joining(
                rule.getMatchMode() == ProductRuleMatchMode.ALL ? " 그리고 " : " 또는 "));
        String then = rule.getActions().stream().map(this::actionText).collect(Collectors.joining(", "));
        return when + "이면 " + then;
    }

    private String conditionText(ProductRuleCondition condition) {
        String subject = condition.getSourceGroup().getCustomerLabel();
        if (condition.getSourceField() == ProductRuleSourceField.SELECTED_VALUE) {
            return subject + "이(가) " + condition.getSourceValue().getCustomerLabel() + " " + condition.getOperator().getLabelKr();
        }
        return subject + " " + condition.getSourceField().getLabelKr() + " "
                + condition.getOperator().getLabelKr() + " " + condition.getComparisonFrom();
    }

    private String actionText(ProductRuleAction action) {
        String target = action.getTargetGroup().getCustomerLabel();
        if (action.getTargetValue() != null) target += " / " + action.getTargetValue().getCustomerLabel();
        return target + " " + action.getActionType().getLabelKr();
    }

    private ProductMaster requireProduct(Long productId) {
        if (productId == null) throw new IllegalArgumentException("제품 ID가 필요합니다.");
        return productRepository.findById(productId)
                .orElseThrow(() -> new java.util.NoSuchElementException("제품을 찾을 수 없습니다."));
    }

    private State state(Map<Long, State> states, ProductAttributeGroup group) {
        return group == null ? null : states.get(group.getId());
    }

    private List<String> distinct(List<String> values) {
        return List.copyOf(new LinkedHashSet<>(values));
    }

    private String normalizeText(String text) {
        if (text == null || text.isBlank()) return null;
        String normalized = text.trim();
        if (normalized.length() > 500) throw new IllegalArgumentException("비규격 입력 내용은 500자 이하여야 합니다.");
        return normalized;
    }

    private long safeAdd(long left, long right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("가격 계산 결과가 허용 범위를 초과했습니다.");
        }
    }

    private int safeInt(long value) {
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("가격 계산 결과가 저장 가능한 범위를 초과했습니다.");
        }
        return (int) value;
    }

    private String formatMoney(long value) {
        return String.format(java.util.Locale.KOREA, "%,d", value);
    }

    private String unit(ProductAttributeGroup group) {
        return group.getUnitLabel() == null ? "" : group.getUnitLabel();
    }

    private String customerLabel(ProductAttributeGroup group, String fallback) {
        return group == null || group.getCustomerLabel() == null ? fallback : group.getCustomerLabel();
    }

    private static final class State {
        private final ProductAttributeGroup group;
        private final LinkedHashSet<Long> selectedValueIds = new LinkedHashSet<>();
        private final LinkedHashSet<Long> defaultSelectedValueIds = new LinkedHashSet<>();
        private final LinkedHashSet<Long> allowedValueIds = new LinkedHashSet<>();
        private final LinkedHashSet<Long> disabledValueIds = new LinkedHashSet<>();
        private boolean visible;
        private boolean required;
        private boolean locked;
        private Integer widthMm;
        private Integer depthMm;
        private Integer heightMm;
        private BigDecimal numberValue;
        private String textValue;

        private State(ProductAttributeGroup group) {
            this.group = group;
        }

        private void clearAnswer() {
            if (locked) return;
            selectedValueIds.clear();
            widthMm = null;
            depthMm = null;
            heightMm = null;
            numberValue = null;
            textValue = null;
        }
    }

    private record PriceResult(long amount, String formula, Integer unitPrice, BigDecimal quantity) {
    }
}
