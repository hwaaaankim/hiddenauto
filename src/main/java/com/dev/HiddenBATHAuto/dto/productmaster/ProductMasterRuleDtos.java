package com.dev.HiddenBATHAuto.dto.productmaster;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.dev.HiddenBATHAuto.dto.productmaster.ProductMasterDtos.AttributeImageResponse;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductMasterDtos.GroupResponse;
import com.dev.HiddenBATHAuto.enums.productmaster.ProductAttributeGroupType;
import com.dev.HiddenBATHAuto.enums.productmaster.ProductAttributeInputType;
import com.dev.HiddenBATHAuto.enums.productmaster.ProductAttributeSelectionMode;
import com.dev.HiddenBATHAuto.enums.productmaster.ProductDimensionType;
import com.dev.HiddenBATHAuto.enums.productmaster.ProductDynamicPriceApplyMode;
import com.dev.HiddenBATHAuto.enums.productmaster.ProductDynamicPriceRuleType;
import com.dev.HiddenBATHAuto.enums.productmaster.ProductPriceMatrixLookupMode;
import com.dev.HiddenBATHAuto.enums.productmaster.ProductRuleActionType;
import com.dev.HiddenBATHAuto.enums.productmaster.ProductRuleMatchMode;
import com.dev.HiddenBATHAuto.enums.productmaster.ProductRuleOperator;
import com.dev.HiddenBATHAuto.enums.productmaster.ProductRuleSourceField;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class ProductMasterRuleDtos {

    private ProductMasterRuleDtos() {
    }

    public record RuleConditionRequest(
            @NotNull Long sourceGroupId,
            Long sourceValueId,
            @NotNull ProductRuleSourceField sourceField,
            @NotNull ProductRuleOperator operator,
            @Digits(integer = 10, fraction = 3) @DecimalMin("-1000000000.000") @DecimalMax("1000000000.000") BigDecimal comparisonFrom,
            @Digits(integer = 10, fraction = 3) @DecimalMin("-1000000000.000") @DecimalMax("1000000000.000") BigDecimal comparisonTo,
            @Min(0) @Max(999) Integer sortOrder
    ) {
    }

    public record RuleActionRequest(
            @NotNull ProductRuleActionType actionType,
            @NotNull Long targetGroupId,
            Long targetValueId,
            @Digits(integer = 10, fraction = 3) @DecimalMin("-1000000000.000") @DecimalMax("1000000000.000") BigDecimal actionNumber,
            @Size(max = 500) String message,
            @Min(0) @Max(999) Integer sortOrder
    ) {
    }

    public record ConfigurationRuleSaveRequest(
            @NotBlank @Size(max = 120) String ruleName,
            @Size(max = 500) String description,
            Long scopeProductId,
            @NotNull ProductRuleMatchMode matchMode,
            @Min(0) @Max(10000) int priority,
            boolean active,
            @NotEmpty @Size(max = 30) List<@NotNull @Valid RuleConditionRequest> conditions,
            @NotEmpty @Size(max = 30) List<@NotNull @Valid RuleActionRequest> actions,
            @Min(0) Long rowVersion
    ) {
    }

    public record RuleConditionResponse(
            Long id,
            Long sourceGroupId,
            String sourceGroupCode,
            String sourceGroupLabel,
            Long sourceValueId,
            String sourceValueCode,
            String sourceValueLabel,
            ProductRuleSourceField sourceField,
            String sourceFieldLabel,
            ProductRuleOperator operator,
            String operatorLabel,
            BigDecimal comparisonFrom,
            BigDecimal comparisonTo,
            int sortOrder
    ) {
    }

    public record RuleActionResponse(
            Long id,
            ProductRuleActionType actionType,
            String actionTypeLabel,
            Long targetGroupId,
            String targetGroupCode,
            String targetGroupLabel,
            Long targetValueId,
            String targetValueCode,
            String targetValueLabel,
            BigDecimal actionNumber,
            String message,
            int sortOrder
    ) {
    }

    public record ConfigurationRuleResponse(
            Long id,
            String ruleCode,
            String ruleName,
            String description,
            Long scopeProductId,
            String scopeProductName,
            ProductRuleMatchMode matchMode,
            String matchModeLabel,
            int priority,
            boolean active,
            List<RuleConditionResponse> conditions,
            List<RuleActionResponse> actions,
            String summary,
            String createdBy,
            String updatedBy,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            long rowVersion
    ) {
    }

    public record MatrixCellRequest(
            @NotNull @Digits(integer = 10, fraction = 3) @DecimalMin("0.000") @DecimalMax("1000000000.000") BigDecimal xValue,
            @NotNull @Digits(integer = 10, fraction = 3) @DecimalMin("0.000") @DecimalMax("1000000000.000") BigDecimal yValue,
            @NotNull @Min(0) @Max(2_000_000_000) Integer amount
    ) {
    }

    public record PriceMatrixSaveRequest(
            @NotBlank @Size(max = 120) String matrixName,
            @Size(max = 500) String description,
            @NotNull Long xGroupId,
            @NotNull ProductRuleSourceField xField,
            @NotNull Long yGroupId,
            @NotNull ProductRuleSourceField yField,
            @NotNull ProductPriceMatrixLookupMode lookupMode,
            @NotNull @Digits(integer = 10, fraction = 3) @DecimalMin("0.001") @DecimalMax("1000000.000") BigDecimal xRoundUnit,
            @NotNull @Digits(integer = 10, fraction = 3) @DecimalMin("0.001") @DecimalMax("1000000.000") BigDecimal yRoundUnit,
            boolean extensionEnabled,
            @Digits(integer = 10, fraction = 3) @DecimalMin("0.000") @DecimalMax("1000000000.000") BigDecimal extensionStart,
            @Digits(integer = 10, fraction = 3) @DecimalMin("0.001") @DecimalMax("1000000000.000") BigDecimal extensionUnit,
            @Min(0) @Max(2_000_000_000) Integer extensionAmount,
            boolean active,
            @NotEmpty @Size(max = 10000) List<@NotNull @Valid MatrixCellRequest> cells,
            @Min(0) Long rowVersion
    ) {
    }

    public record MatrixCellResponse(Long id, BigDecimal xValue, BigDecimal yValue, int amount) {
    }

    public record PriceMatrixResponse(
            Long id,
            String matrixCode,
            String matrixName,
            String description,
            Long xGroupId,
            String xGroupLabel,
            ProductRuleSourceField xField,
            String xFieldLabel,
            Long yGroupId,
            String yGroupLabel,
            ProductRuleSourceField yField,
            String yFieldLabel,
            ProductPriceMatrixLookupMode lookupMode,
            String lookupModeLabel,
            BigDecimal xRoundUnit,
            BigDecimal yRoundUnit,
            boolean extensionEnabled,
            BigDecimal extensionStart,
            BigDecimal extensionUnit,
            Integer extensionAmount,
            boolean active,
            List<BigDecimal> xAxis,
            List<BigDecimal> yAxis,
            List<MatrixCellResponse> cells,
            String createdBy,
            String updatedBy,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            long rowVersion
    ) {
    }

    public record MatrixImportPreview(
            String filename,
            List<BigDecimal> xAxis,
            List<BigDecimal> yAxis,
            List<MatrixCellRequest> cells,
            List<String> warnings
    ) {
    }

    public record DynamicPriceRuleSaveRequest(
            @NotBlank @Size(max = 120) String ruleName,
            @Size(max = 500) String description,
            Long scopeProductId,
            @NotNull ProductDynamicPriceRuleType ruleType,
            @NotNull ProductDynamicPriceApplyMode applyMode,
            Long triggerValueId,
            Long quantityGroupId,
            Long sourceGroupId,
            ProductRuleSourceField sourceField,
            Long matrixId,
            @Min(-1_000_000_000) @Max(2_000_000_000) Integer amount,
            @Digits(integer = 10, fraction = 3) @DecimalMin("-1000000000.000") @DecimalMax("1000000000.000") BigDecimal baseNumber,
            @Digits(integer = 10, fraction = 3) @DecimalMin("0.001") @DecimalMax("1000000000.000") BigDecimal stepNumber,
            @Min(-1_000_000_000) @Max(2_000_000_000) Integer stepAmount,
            @Min(0) @Max(10000) int priority,
            boolean active,
            @Min(0) Long rowVersion
    ) {
    }

    public record DynamicPriceRuleResponse(
            Long id,
            String priceRuleCode,
            String ruleName,
            String description,
            Long scopeProductId,
            String scopeProductName,
            ProductDynamicPriceRuleType ruleType,
            String ruleTypeLabel,
            ProductDynamicPriceApplyMode applyMode,
            String applyModeLabel,
            Long triggerValueId,
            String triggerValueLabel,
            Long quantityGroupId,
            String quantityGroupLabel,
            Long sourceGroupId,
            String sourceGroupLabel,
            ProductRuleSourceField sourceField,
            String sourceFieldLabel,
            Long matrixId,
            String matrixName,
            Integer amount,
            BigDecimal baseNumber,
            BigDecimal stepNumber,
            Integer stepAmount,
            int priority,
            boolean active,
            String summary,
            String createdBy,
            String updatedBy,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            long rowVersion
    ) {
    }

    public record ConfigurationInput(
            @NotNull Long groupId,
            @Size(max = 100) List<@NotNull Long> valueIds,
            @Min(1) @Max(100_000) Integer widthMm,
            @Min(1) @Max(100_000) Integer depthMm,
            @Min(1) @Max(100_000) Integer heightMm,
            @Digits(integer = 10, fraction = 3) @DecimalMin("-1000000000.000") @DecimalMax("1000000000.000") BigDecimal numberValue,
            @Size(max = 500) String textValue
    ) {
    }

    public record ConfigurationEvaluationRequest(
            @Valid @Size(max = 100) List<@NotNull ConfigurationInput> inputs
    ) {
    }

    public record CustomerValueResponse(
            Long id,
            String valueCode,
            String label,
            String guide,
            ProductDimensionType dimensionType,
            int priceAdjustment,
            boolean disabled,
            List<AttributeImageResponse> images
    ) {
    }

    public record ConfigurationGroupState(
            Long groupId,
            String groupCode,
            String label,
            String question,
            String guide,
            ProductAttributeGroupType groupType,
            String groupTypeLabel,
            ProductAttributeInputType inputType,
            String inputTypeLabel,
            ProductAttributeSelectionMode selectionMode,
            String selectionModeLabel,
            boolean visible,
            boolean required,
            boolean locked,
            String unitLabel,
            BigDecimal minimumValue,
            BigDecimal maximumValue,
            BigDecimal stepValue,
            ProductDimensionType customDimensionType,
            List<Long> selectedValueIds,
            Integer widthMm,
            Integer depthMm,
            Integer heightMm,
            BigDecimal numberValue,
            String textValue,
            List<CustomerValueResponse> values,
            List<AttributeImageResponse> images,
            int sortOrder
    ) {
    }

    public record RuleExecutionResponse(String ruleCode, String ruleName, int priority, String explanation) {
    }

    public record PriceLineResponse(
            String code,
            String label,
            String formula,
            int amount,
            Integer unitPrice,
            BigDecimal quantity,
            Long sourceRuleId
    ) {
    }

    public record PriceQuoteResponse(
            int baseSupplyPrice,
            List<PriceLineResponse> lines,
            int supplyPrice,
            BigDecimal vatRate,
            int vatAmount,
            int totalPrice,
            List<String> explanations
    ) {
    }

    public record ConfigurationEvaluationResponse(
            Long productId,
            String productName,
            String catalogCode,
            String productCode,
            int currentStock,
            boolean valid,
            List<String> errors,
            List<String> warnings,
            List<String> notices,
            List<RuleExecutionResponse> firedRules,
            List<ConfigurationGroupState> groups,
            PriceQuoteResponse price
    ) {
    }

    public record ImpactEdgeResponse(
            Long ruleId,
            String ruleCode,
            int priority,
            Long sourceGroupId,
            String sourceGroupLabel,
            Long targetGroupId,
            String targetGroupLabel,
            ProductRuleActionType actionType,
            String actionTypeLabel
    ) {
    }

    public record ProductReferenceResponse(
            Long id,
            String name,
            String catalogCode,
            String qrPublicToken,
            boolean active
    ) {
    }

    public record AutomationBootstrapResponse(
            List<GroupResponse> groups,
            List<ProductReferenceResponse> products,
            List<ConfigurationRuleResponse> configurationRules,
            List<PriceMatrixResponse> matrices,
            List<DynamicPriceRuleResponse> priceRules,
            List<ImpactEdgeResponse> impactEdges
    ) {
    }
}
