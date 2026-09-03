package com.dev.HiddenBATHAuto.service.productmaster;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.dev.HiddenBATHAuto.dto.productmaster.ProductMasterRuleDtos.AutomationBootstrapResponse;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductMasterRuleDtos.ConfigurationRuleResponse;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductMasterRuleDtos.ConfigurationRuleSaveRequest;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductMasterRuleDtos.DynamicPriceRuleResponse;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductMasterRuleDtos.DynamicPriceRuleSaveRequest;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductMasterRuleDtos.ImpactEdgeResponse;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductMasterRuleDtos.MatrixCellRequest;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductMasterRuleDtos.MatrixCellResponse;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductMasterRuleDtos.MatrixImportPreview;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductMasterRuleDtos.PriceMatrixResponse;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductMasterRuleDtos.PriceMatrixSaveRequest;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductMasterRuleDtos.ProductReferenceResponse;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductMasterRuleDtos.RuleActionRequest;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductMasterRuleDtos.RuleActionResponse;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductMasterRuleDtos.RuleConditionRequest;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductMasterRuleDtos.RuleConditionResponse;
import com.dev.HiddenBATHAuto.enums.productmaster.ProductAttributeInputType;
import com.dev.HiddenBATHAuto.enums.productmaster.ProductDimensionType;
import com.dev.HiddenBATHAuto.enums.productmaster.ProductDynamicPriceRuleType;
import com.dev.HiddenBATHAuto.enums.productmaster.ProductMasterStatus;
import com.dev.HiddenBATHAuto.enums.productmaster.ProductRuleActionType;
import com.dev.HiddenBATHAuto.enums.productmaster.ProductRuleOperator;
import com.dev.HiddenBATHAuto.enums.productmaster.ProductRuleSourceField;
import com.dev.HiddenBATHAuto.model.productmaster.ProductAttributeGroup;
import com.dev.HiddenBATHAuto.model.productmaster.ProductAttributeValue;
import com.dev.HiddenBATHAuto.model.productmaster.ProductConfigurationRule;
import com.dev.HiddenBATHAuto.model.productmaster.ProductDynamicPriceRule;
import com.dev.HiddenBATHAuto.model.productmaster.ProductMaster;
import com.dev.HiddenBATHAuto.model.productmaster.ProductPriceMatrix;
import com.dev.HiddenBATHAuto.model.productmaster.ProductPriceMatrixCell;
import com.dev.HiddenBATHAuto.model.productmaster.ProductRuleAction;
import com.dev.HiddenBATHAuto.model.productmaster.ProductRuleCondition;
import com.dev.HiddenBATHAuto.repository.productmaster.ProductAttributeGroupRepository;
import com.dev.HiddenBATHAuto.repository.productmaster.ProductAttributeValueRepository;
import com.dev.HiddenBATHAuto.repository.productmaster.ProductConfigurationRuleRepository;
import com.dev.HiddenBATHAuto.repository.productmaster.ProductDynamicPriceRuleRepository;
import com.dev.HiddenBATHAuto.repository.productmaster.ProductMasterRepository;
import com.dev.HiddenBATHAuto.repository.productmaster.ProductPriceMatrixRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductMasterAutomationService {

    private static final int MAX_MATRIX_AXIS = 200;
    private static final int MAX_MATRIX_CELLS = 10_000;

    private final ProductConfigurationRuleRepository ruleRepository;
    private final ProductPriceMatrixRepository matrixRepository;
    private final ProductDynamicPriceRuleRepository priceRuleRepository;
    private final ProductAttributeGroupRepository groupRepository;
    private final ProductAttributeValueRepository valueRepository;
    private final ProductMasterRepository productRepository;
    private final ProductMasterCodeService codeService;
    private final ProductAttributeService attributeService;

    public AutomationBootstrapResponse getBootstrap() {
        List<ConfigurationRuleResponse> rules = getRules();
        return new AutomationBootstrapResponse(
                attributeService.getGroups(true),
                productRepository.findAll(Sort.by(Sort.Order.asc("productName"), Sort.Order.asc("id"))).stream()
                        .map(product -> new ProductReferenceResponse(
                                product.getId(), product.getProductName(), product.getCatalogCode(), product.getQrPublicToken(),
                                product.getStatus() == ProductMasterStatus.ACTIVE
                        ))
                        .toList(),
                rules,
                getMatrices(),
                getPriceRules(),
                impactEdges(rules)
        );
    }

    public List<ConfigurationRuleResponse> getRules() {
        return ruleRepository.findAllByOrderByPriorityAscIdAsc().stream().map(this::toRuleResponse).toList();
    }

    @Transactional
    public ConfigurationRuleResponse createRule(ConfigurationRuleSaveRequest request, String actor) {
        ProductConfigurationRule rule = new ProductConfigurationRule();
        rule.setRuleCode(codeService.newConfigurationRuleCode());
        rule.setCreatedBy(normalizeActor(actor));
        applyRule(rule, request, actor);
        return toRuleResponse(ruleRepository.saveAndFlush(rule));
    }

    @Transactional
    public ConfigurationRuleResponse updateRule(Long ruleId, ConfigurationRuleSaveRequest request, String actor) {
        ProductConfigurationRule rule = requireRule(ruleId);
        verifyVersion(request.rowVersion(), rule.getRowVersion(), "조건 규칙");
        applyRule(rule, request, actor);
        rule.setUpdatedAt(LocalDateTime.now());
        ruleRepository.flush();
        return toRuleResponse(rule);
    }

    @Transactional
    public void deleteRule(Long ruleId) {
        ruleRepository.delete(requireRule(ruleId));
    }

    public List<PriceMatrixResponse> getMatrices() {
        return matrixRepository.findAllByOrderByIdDesc().stream().map(this::toMatrixResponse).toList();
    }

    @Transactional
    public PriceMatrixResponse createMatrix(PriceMatrixSaveRequest request, String actor) {
        ProductPriceMatrix matrix = new ProductPriceMatrix();
        matrix.setMatrixCode(codeService.newPriceMatrixCode());
        matrix.setCreatedBy(normalizeActor(actor));
        applyMatrix(matrix, request, actor);
        return toMatrixResponse(matrixRepository.saveAndFlush(matrix));
    }

    @Transactional
    public PriceMatrixResponse updateMatrix(Long matrixId, PriceMatrixSaveRequest request, String actor) {
        ProductPriceMatrix matrix = requireMatrix(matrixId);
        verifyVersion(request.rowVersion(), matrix.getRowVersion(), "가격표");
        applyMatrix(matrix, request, actor);
        matrix.setUpdatedAt(LocalDateTime.now());
        matrixRepository.flush();
        return toMatrixResponse(matrix);
    }

    @Transactional
    public void deleteMatrix(Long matrixId) {
        ProductPriceMatrix matrix = requireMatrix(matrixId);
        if (priceRuleRepository.existsByMatrixId(matrixId)) {
            throw new IllegalStateException("가격 규칙에서 사용하는 가격표는 삭제할 수 없습니다. 가격 규칙의 연결을 먼저 해제해 주세요.");
        }
        matrixRepository.delete(matrix);
    }

    public List<DynamicPriceRuleResponse> getPriceRules() {
        return priceRuleRepository.findAllByOrderByPriorityAscIdAsc().stream()
                .map(this::toPriceRuleResponse)
                .toList();
    }

    @Transactional
    public DynamicPriceRuleResponse createPriceRule(DynamicPriceRuleSaveRequest request, String actor) {
        ProductDynamicPriceRule rule = new ProductDynamicPriceRule();
        rule.setPriceRuleCode(codeService.newDynamicPriceRuleCode());
        rule.setCreatedBy(normalizeActor(actor));
        applyPriceRule(rule, request, actor);
        return toPriceRuleResponse(priceRuleRepository.saveAndFlush(rule));
    }

    @Transactional
    public DynamicPriceRuleResponse updatePriceRule(
            Long ruleId,
            DynamicPriceRuleSaveRequest request,
            String actor
    ) {
        ProductDynamicPriceRule rule = requirePriceRule(ruleId);
        verifyVersion(request.rowVersion(), rule.getRowVersion(), "가격 규칙");
        applyPriceRule(rule, request, actor);
        rule.setUpdatedAt(LocalDateTime.now());
        priceRuleRepository.flush();
        return toPriceRuleResponse(rule);
    }

    @Transactional
    public void deletePriceRule(Long ruleId) {
        priceRuleRepository.delete(requirePriceRule(ruleId));
    }

    public MatrixImportPreview previewMatrix(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("업로드할 엑셀 또는 CSV 파일을 선택해 주세요.");
        }
        if (file.getSize() > 10L * 1024 * 1024) {
            throw new IllegalArgumentException("가격표 파일은 10MB 이하여야 합니다.");
        }
        String filename = safeFilename(file.getOriginalFilename());
        String extension = extension(filename);
        try {
            if ("csv".equals(extension)) {
                return parseCsv(file, filename);
            }
            if (!Set.of("xlsx", "xls").contains(extension)) {
                throw new IllegalArgumentException(".xlsx, .xls, .csv 가격표만 업로드할 수 있습니다.");
            }
            return parseWorkbook(file, filename);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new IllegalArgumentException("가격표 파일을 읽지 못했습니다. 손상 여부와 형식을 확인해 주세요.", exception);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(
                    "가격표 파일을 계산하지 못했습니다. 암호·외부참조·지원하지 않는 수식이 있는지 확인해 주세요.",
                    exception
            );
        }
    }

    private void applyRule(ProductConfigurationRule rule, ConfigurationRuleSaveRequest request, String actor) {
        if (request.conditions() == null || request.conditions().isEmpty()
                || request.actions() == null || request.actions().isEmpty()) {
            throw new IllegalArgumentException("조건과 실행 항목을 각각 하나 이상 등록해 주세요.");
        }
        ProductMaster scope = request.scopeProductId() == null ? null : requireProduct(request.scopeProductId());

        Set<Long> groupIds = new LinkedHashSet<>();
        Set<Long> valueIds = new LinkedHashSet<>();
        request.conditions().forEach(item -> {
            groupIds.add(item.sourceGroupId());
            if (item.sourceValueId() != null) valueIds.add(item.sourceValueId());
        });
        request.actions().forEach(item -> {
            groupIds.add(item.targetGroupId());
            if (item.targetValueId() != null) valueIds.add(item.targetValueId());
        });
        Map<Long, ProductAttributeGroup> groups = requireGroups(groupIds);
        Map<Long, ProductAttributeValue> values = requireValues(valueIds);

        validateRuleDefinition(request, groups, values);
        rule.setRuleName(requiredText(request.ruleName(), "규칙명", 120));
        rule.setDescription(optionalText(request.description(), "규칙 설명", 500));
        rule.setScopeProduct(scope);
        rule.setMatchMode(Objects.requireNonNull(request.matchMode(), "조건 결합 방식이 필요합니다."));
        rule.setPriority(request.priority());
        rule.setActive(request.active());
        rule.setUpdatedBy(normalizeActor(actor));
        rule.clearDefinition();

        int index = 0;
        for (RuleConditionRequest item : request.conditions()) {
            ProductRuleCondition condition = new ProductRuleCondition();
            condition.setSourceGroup(groups.get(item.sourceGroupId()));
            condition.setSourceValue(item.sourceValueId() == null ? null : values.get(item.sourceValueId()));
            condition.setSourceField(item.sourceField());
            condition.setOperator(item.operator());
            condition.setComparisonFrom(item.comparisonFrom());
            condition.setComparisonTo(item.comparisonTo());
            condition.setSortOrder(item.sortOrder() == null ? index++ * 10 : item.sortOrder());
            rule.addCondition(condition);
        }
        index = 0;
        for (RuleActionRequest item : request.actions()) {
            ProductRuleAction action = new ProductRuleAction();
            action.setActionType(item.actionType());
            action.setTargetGroup(groups.get(item.targetGroupId()));
            action.setTargetValue(item.targetValueId() == null ? null : values.get(item.targetValueId()));
            action.setActionNumber(item.actionNumber());
            action.setMessage(optionalText(item.message(), "안내 메시지", 500));
            action.setSortOrder(item.sortOrder() == null ? index++ * 10 : item.sortOrder());
            rule.addAction(action);
        }
    }

    private void validateRuleDefinition(
            ConfigurationRuleSaveRequest request,
            Map<Long, ProductAttributeGroup> groups,
            Map<Long, ProductAttributeValue> values
    ) {
        for (RuleConditionRequest item : request.conditions()) {
            ProductAttributeGroup group = groups.get(item.sourceGroupId());
            ProductAttributeValue value = item.sourceValueId() == null ? null : values.get(item.sourceValueId());
            if (value != null && !value.getGroup().getId().equals(group.getId())) {
                throw new IllegalArgumentException("조건의 옵션값이 선택한 옵션 그룹에 속하지 않습니다.");
            }
            if (item.sourceField() == ProductRuleSourceField.SELECTED_VALUE) {
                if (value == null) {
                    throw new IllegalArgumentException("선택 옵션 조건에는 비교할 옵션값이 필요합니다.");
                }
                if (item.operator() != ProductRuleOperator.EQUALS
                        && item.operator() != ProductRuleOperator.NOT_EQUALS) {
                    throw new IllegalArgumentException("선택 옵션 조건은 같음/같지 않음 연산만 사용할 수 있습니다.");
                }
            } else {
                validateSourceCompatibility(group, item.sourceField());
                if (item.comparisonFrom() == null) {
                    throw new IllegalArgumentException("숫자 비교 조건에는 기준값이 필요합니다.");
                }
                if (item.operator() == ProductRuleOperator.BETWEEN
                        && (item.comparisonTo() == null
                        || item.comparisonFrom().compareTo(item.comparisonTo()) > 0)) {
                    throw new IllegalArgumentException("범위 조건은 시작값 이하의 종료값이 필요합니다.");
                }
            }
        }

        Map<String, Set<ProductRuleActionType>> targetActions = new HashMap<>();
        for (RuleActionRequest item : request.actions()) {
            ProductAttributeGroup group = groups.get(item.targetGroupId());
            ProductAttributeValue value = item.targetValueId() == null ? null : values.get(item.targetValueId());
            if (value != null && !value.getGroup().getId().equals(group.getId())) {
                throw new IllegalArgumentException("실행 항목의 옵션값이 대상 옵션 그룹에 속하지 않습니다.");
            }
            if (Set.of(ProductRuleActionType.ENABLE_VALUE, ProductRuleActionType.DISABLE_VALUE,
                    ProductRuleActionType.SET_VALUE).contains(item.actionType()) && value == null) {
                throw new IllegalArgumentException(item.actionType().getLabelKr() + " 실행에는 대상 옵션값이 필요합니다.");
            }
            if (item.actionType() == ProductRuleActionType.SET_NUMBER) {
                if (group.getInputType() != ProductAttributeInputType.NUMBER || item.actionNumber() == null) {
                    throw new IllegalArgumentException("숫자 자동 입력은 숫자형 그룹과 입력값이 필요합니다.");
                }
                validateNumberBounds(group, item.actionNumber());
            }
            if (item.actionType() == ProductRuleActionType.ADD_NOTICE
                    && (item.message() == null || item.message().isBlank())) {
                throw new IllegalArgumentException("안내 메시지 실행에는 고객에게 보여줄 문구가 필요합니다.");
            }
            String targetKey = group.getId() + ":" + (value == null ? "GROUP" : value.getId());
            targetActions.computeIfAbsent(targetKey, ignored -> new HashSet<>()).add(item.actionType());
        }
        targetActions.values().forEach(actions -> {
            if ((actions.contains(ProductRuleActionType.SHOW_GROUP) && actions.contains(ProductRuleActionType.HIDE_GROUP))
                    || (actions.contains(ProductRuleActionType.REQUIRE_GROUP) && actions.contains(ProductRuleActionType.OPTIONAL_GROUP))
                    || (actions.contains(ProductRuleActionType.ENABLE_VALUE) && actions.contains(ProductRuleActionType.DISABLE_VALUE))) {
                throw new IllegalArgumentException("하나의 규칙 안에서 같은 대상에 서로 반대되는 실행을 등록할 수 없습니다.");
            }
        });
    }

    private void applyMatrix(ProductPriceMatrix matrix, PriceMatrixSaveRequest request, String actor) {
        ProductAttributeGroup xGroup = requireGroup(request.xGroupId());
        ProductAttributeGroup yGroup = requireGroup(request.yGroupId());
        validateMatrixField(xGroup, request.xField(), "가로축");
        validateMatrixField(yGroup, request.yField(), "세로축");
        if (request.cells() == null || request.cells().isEmpty() || request.cells().size() > MAX_MATRIX_CELLS) {
            throw new IllegalArgumentException("가격표 셀은 1~" + MAX_MATRIX_CELLS + "개까지 등록할 수 있습니다.");
        }
        Set<String> coordinateGuard = new HashSet<>();
        Set<BigDecimal> xAxis = new HashSet<>();
        Set<BigDecimal> yAxis = new HashSet<>();
        for (MatrixCellRequest cell : request.cells()) {
            BigDecimal x = normalizedNumber(cell.xValue(), "가로축 값");
            BigDecimal y = normalizedNumber(cell.yValue(), "세로축 값");
            if (x.signum() < 0 || y.signum() < 0) {
                throw new IllegalArgumentException("가격표 축 값은 0 이상이어야 합니다.");
            }
            if (!coordinateGuard.add(x.toPlainString() + ":" + y.toPlainString())) {
                throw new IllegalArgumentException("가격표에 중복 좌표가 있습니다: " + x + " / " + y);
            }
            xAxis.add(x);
            yAxis.add(y);
        }
        if (xAxis.size() > MAX_MATRIX_AXIS || yAxis.size() > MAX_MATRIX_AXIS) {
            throw new IllegalArgumentException("가격표의 각 축은 최대 " + MAX_MATRIX_AXIS + "개까지 등록할 수 있습니다.");
        }
        if (request.extensionEnabled()) {
            if (request.extensionStart() == null || request.extensionUnit() == null
                    || request.extensionUnit().signum() <= 0 || request.extensionAmount() == null) {
                throw new IllegalArgumentException("높이 추가 규칙에는 기준값, 증가 단위, 단위당 금액이 모두 필요합니다.");
            }
        }

        matrix.setMatrixName(requiredText(request.matrixName(), "가격표명", 120));
        matrix.setDescription(optionalText(request.description(), "가격표 설명", 500));
        matrix.setXGroup(xGroup);
        matrix.setXField(request.xField());
        matrix.setYGroup(yGroup);
        matrix.setYField(request.yField());
        matrix.setLookupMode(Objects.requireNonNull(request.lookupMode(), "조회 방식이 필요합니다."));
        matrix.setXRoundUnit(positive(request.xRoundUnit(), "가로축 올림 단위"));
        matrix.setYRoundUnit(positive(request.yRoundUnit(), "세로축 올림 단위"));
        matrix.setExtensionEnabled(request.extensionEnabled());
        matrix.setExtensionStart(request.extensionEnabled() ? request.extensionStart() : null);
        matrix.setExtensionUnit(request.extensionEnabled() ? request.extensionUnit() : null);
        matrix.setExtensionAmount(request.extensionEnabled() ? request.extensionAmount() : null);
        matrix.setActive(request.active());
        matrix.setUpdatedBy(normalizeActor(actor));
        Map<String, ProductPriceMatrixCell> existingCells = matrix.getCells().stream()
                .collect(Collectors.toMap(
                        item -> coordinateKey(item.getXValue(), item.getYValue()),
                        Function.identity(),
                        (left, right) -> left
                ));
        Set<ProductPriceMatrixCell> retainedCells = new HashSet<>();
        request.cells().stream()
                .sorted(Comparator.comparing(MatrixCellRequest::yValue).thenComparing(MatrixCellRequest::xValue))
                .forEach(item -> {
                    BigDecimal x = normalizedNumber(item.xValue(), "가로축 값");
                    BigDecimal y = normalizedNumber(item.yValue(), "세로축 값");
                    ProductPriceMatrixCell cell = existingCells.get(coordinateKey(x, y));
                    if (cell == null) {
                        cell = new ProductPriceMatrixCell();
                        matrix.addCell(cell);
                    }
                    cell.setXValue(x);
                    cell.setYValue(y);
                    cell.setAmount(item.amount());
                    retainedCells.add(cell);
                });
        matrix.getCells().removeIf(cell -> !retainedCells.contains(cell));
    }

    private void applyPriceRule(ProductDynamicPriceRule rule, DynamicPriceRuleSaveRequest request, String actor) {
        ProductMaster scope = request.scopeProductId() == null ? null : requireProduct(request.scopeProductId());
        ProductAttributeValue trigger = request.triggerValueId() == null ? null : requireValue(request.triggerValueId());
        ProductAttributeGroup quantity = request.quantityGroupId() == null ? null : requireGroup(request.quantityGroupId());
        ProductAttributeGroup source = request.sourceGroupId() == null ? null : requireGroup(request.sourceGroupId());
        ProductPriceMatrix matrix = request.matrixId() == null ? null : requireMatrix(request.matrixId());
        ProductDynamicPriceRuleType type = Objects.requireNonNull(request.ruleType(), "가격 규칙 유형이 필요합니다.");

        switch (type) {
            case FIXED_ADD -> {
                if (trigger == null) throw new IllegalArgumentException("조건부 고정금액에는 기준 옵션값이 필요합니다.");
                if (request.amount() == null && trigger.getPriceAdjustment() == 0) {
                    throw new IllegalArgumentException("적용 금액을 입력하거나 옵션값의 고정금액을 먼저 등록해 주세요.");
                }
            }
            case OPTION_X_NUMBER -> {
                if (trigger == null || quantity == null || quantity.getInputType() != ProductAttributeInputType.NUMBER) {
                    throw new IllegalArgumentException("옵션 단가 × 수량에는 기준 옵션값과 숫자형 수량 그룹이 필요합니다.");
                }
                if (request.amount() == null && trigger.getPriceAdjustment() == 0) {
                    throw new IllegalArgumentException("단가를 입력하거나 기준 옵션값에 단가를 등록해 주세요.");
                }
            }
            case MATRIX -> {
                if (matrix == null || !matrix.isActive()) {
                    throw new IllegalArgumentException("사용중인 2축 가격표를 선택해 주세요.");
                }
            }
            case STEP_ADD -> {
                if (source == null || request.sourceField() == null) {
                    throw new IllegalArgumentException("구간별 증분에는 기준 그룹과 숫자 항목이 필요합니다.");
                }
                validateSourceCompatibility(source, request.sourceField());
                if (request.baseNumber() == null || request.stepNumber() == null
                        || request.stepNumber().signum() <= 0 || request.stepAmount() == null) {
                    throw new IllegalArgumentException("구간별 증분의 기준값, 증가 단위, 단위당 금액을 모두 입력해 주세요.");
                }
            }
        }

        rule.setRuleName(requiredText(request.ruleName(), "가격 규칙명", 120));
        rule.setDescription(optionalText(request.description(), "가격 규칙 설명", 500));
        rule.setScopeProduct(scope);
        rule.setRuleType(type);
        rule.setApplyMode(Objects.requireNonNull(request.applyMode(), "금액 적용 방식이 필요합니다."));
        rule.setTriggerValue(trigger);
        rule.setQuantityGroup(quantity);
        rule.setSourceGroup(source);
        rule.setSourceField(request.sourceField());
        rule.setMatrix(matrix);
        rule.setAmount(request.amount());
        rule.setBaseNumber(request.baseNumber());
        rule.setStepNumber(request.stepNumber());
        rule.setStepAmount(request.stepAmount());
        rule.setPriority(request.priority());
        rule.setActive(request.active());
        rule.setUpdatedBy(normalizeActor(actor));
    }

    private MatrixImportPreview parseWorkbook(MultipartFile file, String filename) throws IOException {
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new IllegalArgumentException("가격표 엑셀에 시트가 없습니다.");
            }
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter(Locale.KOREA);
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            if (sheet.getLastRowNum() > MAX_MATRIX_AXIS) {
                throw new IllegalArgumentException("가격표의 세로축은 최대 " + MAX_MATRIX_AXIS + "개까지 업로드할 수 있습니다.");
            }
            List<List<String>> rows = new ArrayList<>();
            int lastRow = sheet.getLastRowNum();
            for (int rowIndex = 0; rowIndex <= lastRow; rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                List<String> values = new ArrayList<>();
                int rawLastCell = row == null ? 0 : row.getLastCellNum();
                if (rawLastCell > MAX_MATRIX_AXIS + 1) {
                    throw new IllegalArgumentException("가격표의 가로축은 최대 " + MAX_MATRIX_AXIS + "개까지 업로드할 수 있습니다.");
                }
                int lastCell = Math.max(rawLastCell, 0);
                for (int cellIndex = 0; cellIndex < lastCell; cellIndex++) {
                    Cell cell = row.getCell(cellIndex, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    values.add(cell == null ? "" : formatter.formatCellValue(cell, evaluator).trim());
                }
                rows.add(values);
            }
            return parseGrid(filename, rows);
        }
    }

    private MatrixImportPreview parseCsv(MultipartFile file, String filename) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (rows.size() >= MAX_MATRIX_AXIS + 1) {
                    throw new IllegalArgumentException("가격표의 세로축은 최대 " + MAX_MATRIX_AXIS + "개까지 업로드할 수 있습니다.");
                }
                rows.add(parseCsvLine(line));
            }
        }
        return parseGrid(filename, rows);
    }

    private MatrixImportPreview parseGrid(String filename, List<List<String>> rows) {
        if (rows.size() < 2 || rows.get(0).size() < 2) {
            throw new IllegalArgumentException("첫 행 B열부터 가로축, 첫 열 2행부터 세로축, 교차 셀에는 금액을 입력해 주세요.");
        }
        List<BigDecimal> xAxis = new ArrayList<>();
        for (int column = 1; column < rows.get(0).size(); column++) {
            String raw = rows.get(0).get(column);
            if (raw == null || raw.isBlank()) break;
            xAxis.add(parseDecimal(raw, "가로축 " + (column + 1) + "열"));
        }
        if (xAxis.isEmpty()) throw new IllegalArgumentException("가로축 값이 없습니다.");

        List<BigDecimal> yAxis = new ArrayList<>();
        List<MatrixCellRequest> cells = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        for (int rowIndex = 1; rowIndex < rows.size(); rowIndex++) {
            List<String> row = rows.get(rowIndex);
            if (row.isEmpty() || row.get(0).isBlank()) break;
            BigDecimal y = parseDecimal(row.get(0), "세로축 " + (rowIndex + 1) + "행");
            yAxis.add(y);
            for (int column = 0; column < xAxis.size(); column++) {
                String raw = row.size() > column + 1 ? row.get(column + 1) : "";
                if (raw == null || raw.isBlank()) {
                    warnings.add("Y=" + y + ", X=" + xAxis.get(column) + " 금액 셀이 비어 있어 제외했습니다.");
                    continue;
                }
                int amount = parseAmount(raw, "금액 " + (rowIndex + 1) + "행 " + (column + 2) + "열");
                cells.add(new MatrixCellRequest(xAxis.get(column), y, amount));
            }
        }
        if (cells.isEmpty()) throw new IllegalArgumentException("등록 가능한 가격 셀이 없습니다.");
        if (cells.size() > MAX_MATRIX_CELLS) throw new IllegalArgumentException("가격표는 최대 10,000셀까지 업로드할 수 있습니다.");
        if (new HashSet<>(xAxis).size() != xAxis.size() || new HashSet<>(yAxis).size() != yAxis.size()) {
            throw new IllegalArgumentException("가격표 축에 중복값이 있습니다.");
        }
        return new MatrixImportPreview(filename, xAxis, yAxis, cells, warnings.stream().limit(50).toList());
    }

    private List<String> parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < line.length(); index++) {
            char ch = line.charAt(index);
            if (ch == '"') {
                if (quoted && index + 1 < line.length() && line.charAt(index + 1) == '"') {
                    current.append('"');
                    index++;
                } else {
                    quoted = !quoted;
                }
            } else if (ch == ',' && !quoted) {
                values.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        if (quoted) {
            throw new IllegalArgumentException("CSV 가격표의 따옴표가 닫히지 않았습니다.");
        }
        values.add(current.toString().trim());
        return values;
    }

    private ConfigurationRuleResponse toRuleResponse(ProductConfigurationRule rule) {
        List<RuleConditionResponse> conditions = rule.getConditions().stream()
                .sorted(Comparator.comparingInt(ProductRuleCondition::getSortOrder).thenComparing(ProductRuleCondition::getId,
                        Comparator.nullsLast(Long::compareTo)))
                .map(item -> new RuleConditionResponse(
                        item.getId(), item.getSourceGroup().getId(), item.getSourceGroup().getGroupCode(),
                        item.getSourceGroup().getManagementLabel(),
                        item.getSourceValue() == null ? null : item.getSourceValue().getId(),
                        item.getSourceValue() == null ? null : item.getSourceValue().getValueCode(),
                        item.getSourceValue() == null ? null : item.getSourceValue().getManagementLabel(),
                        item.getSourceField(), item.getSourceField().getLabelKr(),
                        item.getOperator(), item.getOperator().getLabelKr(),
                        item.getComparisonFrom(), item.getComparisonTo(), item.getSortOrder()
                )).toList();
        List<RuleActionResponse> actions = rule.getActions().stream()
                .sorted(Comparator.comparingInt(ProductRuleAction::getSortOrder).thenComparing(ProductRuleAction::getId,
                        Comparator.nullsLast(Long::compareTo)))
                .map(item -> new RuleActionResponse(
                        item.getId(), item.getActionType(), item.getActionType().getLabelKr(),
                        item.getTargetGroup().getId(), item.getTargetGroup().getGroupCode(),
                        item.getTargetGroup().getManagementLabel(),
                        item.getTargetValue() == null ? null : item.getTargetValue().getId(),
                        item.getTargetValue() == null ? null : item.getTargetValue().getValueCode(),
                        item.getTargetValue() == null ? null : item.getTargetValue().getManagementLabel(),
                        item.getActionNumber(), item.getMessage(), item.getSortOrder()
                )).toList();
        String summary = (conditions.isEmpty() ? "조건 없음" : conditions.get(0).sourceGroupLabel() + " 조건")
                + " → " + (actions.isEmpty() ? "실행 없음" : actions.get(0).actionTypeLabel())
                + (actions.size() > 1 ? " 외 " + (actions.size() - 1) + "개" : "");
        return new ConfigurationRuleResponse(
                rule.getId(), rule.getRuleCode(), rule.getRuleName(), rule.getDescription(),
                rule.getScopeProduct() == null ? null : rule.getScopeProduct().getId(),
                rule.getScopeProduct() == null ? "전체 제품" : rule.getScopeProduct().getProductName(),
                rule.getMatchMode(), rule.getMatchMode().getLabelKr(), rule.getPriority(), rule.isActive(),
                conditions, actions, summary, rule.getCreatedBy(), rule.getUpdatedBy(),
                rule.getCreatedAt(), rule.getUpdatedAt(), rule.getRowVersion()
        );
    }

    private PriceMatrixResponse toMatrixResponse(ProductPriceMatrix matrix) {
        List<MatrixCellResponse> cells = matrix.getCells().stream()
                .sorted(Comparator.comparing(ProductPriceMatrixCell::getYValue)
                        .thenComparing(ProductPriceMatrixCell::getXValue))
                .map(item -> new MatrixCellResponse(item.getId(), item.getXValue(), item.getYValue(), item.getAmount()))
                .toList();
        List<BigDecimal> xAxis = cells.stream().map(MatrixCellResponse::xValue).distinct().sorted().toList();
        List<BigDecimal> yAxis = cells.stream().map(MatrixCellResponse::yValue).distinct().sorted().toList();
        return new PriceMatrixResponse(
                matrix.getId(), matrix.getMatrixCode(), matrix.getMatrixName(), matrix.getDescription(),
                matrix.getXGroup().getId(), matrix.getXGroup().getManagementLabel(),
                matrix.getXField(), matrix.getXField().getLabelKr(),
                matrix.getYGroup().getId(), matrix.getYGroup().getManagementLabel(),
                matrix.getYField(), matrix.getYField().getLabelKr(),
                matrix.getLookupMode(), matrix.getLookupMode().getLabelKr(), matrix.getXRoundUnit(), matrix.getYRoundUnit(),
                matrix.isExtensionEnabled(), matrix.getExtensionStart(), matrix.getExtensionUnit(), matrix.getExtensionAmount(),
                matrix.isActive(), xAxis, yAxis, cells, matrix.getCreatedBy(), matrix.getUpdatedBy(),
                matrix.getCreatedAt(), matrix.getUpdatedAt(), matrix.getRowVersion()
        );
    }

    private DynamicPriceRuleResponse toPriceRuleResponse(ProductDynamicPriceRule rule) {
        String summary = switch (rule.getRuleType()) {
            case FIXED_ADD -> label(rule.getTriggerValue()) + " 선택 시 " + money(rule.getAmount(), rule.getTriggerValue()) + "원";
            case OPTION_X_NUMBER -> label(rule.getTriggerValue()) + " 단가 × " + label(rule.getQuantityGroup());
            case MATRIX -> (rule.getMatrix() == null ? "가격표 미지정" : rule.getMatrix().getMatrixName()) + " 조회";
            case STEP_ADD -> label(rule.getSourceGroup()) + " " + rule.getBaseNumber() + " 초과분 계산";
        };
        return new DynamicPriceRuleResponse(
                rule.getId(), rule.getPriceRuleCode(), rule.getRuleName(), rule.getDescription(),
                rule.getScopeProduct() == null ? null : rule.getScopeProduct().getId(),
                rule.getScopeProduct() == null ? "전체 제품" : rule.getScopeProduct().getProductName(),
                rule.getRuleType(), rule.getRuleType().getLabelKr(), rule.getApplyMode(), rule.getApplyMode().getLabelKr(),
                id(rule.getTriggerValue()), label(rule.getTriggerValue()), id(rule.getQuantityGroup()), label(rule.getQuantityGroup()),
                id(rule.getSourceGroup()), label(rule.getSourceGroup()), rule.getSourceField(),
                rule.getSourceField() == null ? null : rule.getSourceField().getLabelKr(),
                rule.getMatrix() == null ? null : rule.getMatrix().getId(),
                rule.getMatrix() == null ? null : rule.getMatrix().getMatrixName(),
                rule.getAmount(), rule.getBaseNumber(), rule.getStepNumber(), rule.getStepAmount(),
                rule.getPriority(), rule.isActive(), summary, rule.getCreatedBy(), rule.getUpdatedBy(),
                rule.getCreatedAt(), rule.getUpdatedAt(), rule.getRowVersion()
        );
    }

    private List<ImpactEdgeResponse> impactEdges(List<ConfigurationRuleResponse> rules) {
        List<ImpactEdgeResponse> result = new ArrayList<>();
        for (ConfigurationRuleResponse rule : rules) {
            Set<Long> sources = rule.conditions().stream().map(RuleConditionResponse::sourceGroupId)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            for (Long sourceId : sources) {
                String sourceLabel = rule.conditions().stream()
                        .filter(item -> item.sourceGroupId().equals(sourceId)).findFirst()
                        .map(RuleConditionResponse::sourceGroupLabel).orElse("");
                for (RuleActionResponse action : rule.actions()) {
                    result.add(new ImpactEdgeResponse(
                            rule.id(), rule.ruleCode(), rule.priority(), sourceId, sourceLabel,
                            action.targetGroupId(), action.targetGroupLabel(), action.actionType(), action.actionTypeLabel()
                    ));
                }
            }
        }
        return result;
    }

    private void validateSourceCompatibility(ProductAttributeGroup group, ProductRuleSourceField field) {
        if (field == ProductRuleSourceField.NUMBER_VALUE && group.getInputType() != ProductAttributeInputType.NUMBER) {
            throw new IllegalArgumentException(group.getManagementLabel() + " 그룹은 숫자형 입력이 아닙니다.");
        }
        if (Set.of(ProductRuleSourceField.WIDTH_MM, ProductRuleSourceField.DEPTH_MM,
                ProductRuleSourceField.HEIGHT_MM).contains(field)
                && group.getInputType() != ProductAttributeInputType.DIMENSION) {
            throw new IllegalArgumentException(group.getManagementLabel() + " 그룹은 사이즈형 입력이 아닙니다.");
        }
    }

    private void validateMatrixField(ProductAttributeGroup group, ProductRuleSourceField field, String axis) {
        if (field == null || field == ProductRuleSourceField.SELECTED_VALUE) {
            throw new IllegalArgumentException(axis + "은 W/D/H 또는 숫자 입력값이어야 합니다.");
        }
        validateSourceCompatibility(group, field);
    }

    private void validateNumberBounds(ProductAttributeGroup group, BigDecimal value) {
        if (group.getMinimumValue() != null && value.compareTo(group.getMinimumValue()) < 0) {
            throw new IllegalArgumentException(group.getManagementLabel() + " 값이 최소값보다 작습니다.");
        }
        if (group.getMaximumValue() != null && value.compareTo(group.getMaximumValue()) > 0) {
            throw new IllegalArgumentException(group.getManagementLabel() + " 값이 최대값보다 큽니다.");
        }
    }

    private Map<Long, ProductAttributeGroup> requireGroups(Set<Long> ids) {
        if (ids.contains(null)) throw new IllegalArgumentException("옵션 그룹 ID가 필요합니다.");
        Map<Long, ProductAttributeGroup> result = groupRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(ProductAttributeGroup::getId, Function.identity()));
        if (result.size() != ids.size()) throw new IllegalArgumentException("존재하지 않는 옵션 그룹이 포함되어 있습니다.");
        return result;
    }

    private Map<Long, ProductAttributeValue> requireValues(Set<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        Map<Long, ProductAttributeValue> result = valueRepository.findAllWithGroupByIdIn(ids).stream()
                .collect(Collectors.toMap(ProductAttributeValue::getId, Function.identity()));
        if (result.size() != ids.size()) throw new IllegalArgumentException("존재하지 않는 옵션값이 포함되어 있습니다.");
        return result;
    }

    private ProductConfigurationRule requireRule(Long id) {
        return ruleRepository.findById(id)
                .orElseThrow(() -> new java.util.NoSuchElementException("조건 규칙을 찾을 수 없습니다."));
    }

    private ProductPriceMatrix requireMatrix(Long id) {
        return matrixRepository.findDetailedById(id)
                .orElseThrow(() -> new java.util.NoSuchElementException("가격표를 찾을 수 없습니다."));
    }

    private ProductDynamicPriceRule requirePriceRule(Long id) {
        return priceRuleRepository.findById(id)
                .orElseThrow(() -> new java.util.NoSuchElementException("가격 규칙을 찾을 수 없습니다."));
    }

    private ProductAttributeGroup requireGroup(Long id) {
        return groupRepository.findById(id)
                .orElseThrow(() -> new java.util.NoSuchElementException("옵션 그룹을 찾을 수 없습니다."));
    }

    private ProductAttributeValue requireValue(Long id) {
        return valueRepository.findWithGroupById(id)
                .orElseThrow(() -> new java.util.NoSuchElementException("옵션값을 찾을 수 없습니다."));
    }

    private ProductMaster requireProduct(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new java.util.NoSuchElementException("제품을 찾을 수 없습니다."));
    }

    private String safeFilename(String filename) {
        String value = filename == null ? "price-matrix" : filename.replace('\\', '/');
        value = value.substring(value.lastIndexOf('/') + 1).trim();
        return value.isEmpty() ? "price-matrix" : value.substring(0, Math.min(value.length(), 255));
    }

    private String extension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private BigDecimal parseDecimal(String raw, String field) {
        try {
            return normalizedNumber(new BigDecimal(raw.replace(",", "").trim()), field);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(field + " 값이 숫자가 아닙니다: " + raw);
        }
    }

    private String coordinateKey(BigDecimal x, BigDecimal y) {
        return normalizedNumber(x, "가로축 값").toPlainString()
                + ":" + normalizedNumber(y, "세로축 값").toPlainString();
    }

    private int parseAmount(String raw, String field) {
        try {
            BigDecimal value = new BigDecimal(raw.replace(",", "").trim()).stripTrailingZeros();
            if (value.scale() > 0) throw new ArithmeticException("decimal");
            int amount = value.intValueExact();
            if (amount < 0 || amount > 2_000_000_000) throw new ArithmeticException("range");
            return amount;
        } catch (NumberFormatException | ArithmeticException exception) {
            throw new IllegalArgumentException(field + "은 0~2,000,000,000 사이의 정수 금액이어야 합니다: " + raw);
        }
    }

    private BigDecimal normalizedNumber(BigDecimal value, String field) {
        if (value == null) throw new IllegalArgumentException(field + "이 필요합니다.");
        BigDecimal normalized = value.stripTrailingZeros();
        if (normalized.precision() > 14 || normalized.scale() > 3) {
            throw new IllegalArgumentException(field + "은 소수 셋째 자리까지 입력할 수 있습니다.");
        }
        return normalized;
    }

    private BigDecimal positive(BigDecimal value, String field) {
        BigDecimal normalized = normalizedNumber(value, field);
        if (normalized.signum() <= 0) throw new IllegalArgumentException(field + "은 0보다 커야 합니다.");
        return normalized;
    }

    private String requiredText(String value, String field, int max) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + "을(를) 입력해 주세요.");
        if (normalized.length() > max) throw new IllegalArgumentException(field + "은(는) " + max + "자 이하여야 합니다.");
        return normalized;
    }

    private String optionalText(String value, String field, int max) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() > max) throw new IllegalArgumentException(field + "은(는) " + max + "자 이하여야 합니다.");
        return normalized;
    }

    private String normalizeActor(String actor) {
        String normalized = actor == null || actor.isBlank() ? "SYSTEM" : actor.trim();
        return normalized.length() <= 100 ? normalized : normalized.substring(0, 100);
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

    private Long id(ProductAttributeValue value) { return value == null ? null : value.getId(); }
    private Long id(ProductAttributeGroup group) { return group == null ? null : group.getId(); }
    private String label(ProductAttributeValue value) { return value == null ? null : value.getManagementLabel(); }
    private String label(ProductAttributeGroup group) { return group == null ? null : group.getManagementLabel(); }
    private int money(Integer amount, ProductAttributeValue value) {
        return amount == null ? (value == null ? 0 : value.getPriceAdjustment()) : amount;
    }
}
