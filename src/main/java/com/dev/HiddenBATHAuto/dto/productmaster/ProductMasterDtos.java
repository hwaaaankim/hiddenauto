package com.dev.HiddenBATHAuto.dto.productmaster;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.dev.HiddenBATHAuto.enums.productmaster.ProductAttributeGroupType;
import com.dev.HiddenBATHAuto.enums.productmaster.ProductAttributeInputType;
import com.dev.HiddenBATHAuto.enums.productmaster.ProductAttributeRole;
import com.dev.HiddenBATHAuto.enums.productmaster.ProductAttributeSelectionMode;
import com.dev.HiddenBATHAuto.enums.productmaster.ProductDimensionType;
import com.dev.HiddenBATHAuto.enums.productmaster.ProductMasterStatus;
import com.dev.HiddenBATHAuto.enums.productmaster.ProductPricingMode;
import com.dev.HiddenBATHAuto.enums.productmaster.ProductStockMovementType;

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

public final class ProductMasterDtos {

    private ProductMasterDtos() {
    }

    public record ApiResponse<T>(boolean success, String message, T data) {
        public static <T> ApiResponse<T> ok(T data) {
            return new ApiResponse<>(true, null, data);
        }

        public static <T> ApiResponse<T> ok(String message, T data) {
            return new ApiResponse<>(true, message, data);
        }

        public static <T> ApiResponse<T> fail(String message) {
            return new ApiResponse<>(false, message, null);
        }
    }

    public record GroupSaveRequest(
            @NotBlank @Size(max = 80) String customerLabel,
            @NotBlank @Size(max = 80) String managementLabel,
            @NotBlank @Size(max = 80) String productionLabel,
            @NotNull ProductAttributeGroupType groupType,
            @NotNull ProductAttributeSelectionMode selectionMode,
            @NotNull ProductAttributeRole systemRole,
            @NotNull ProductAttributeInputType inputType,
            @Size(max = 300) String questionText,
            @Size(max = 1000) String customerGuide,
            boolean requiredByDefault,
            @Size(max = 20) String unitLabel,
            @Digits(integer = 10, fraction = 3) @DecimalMin("-1000000000.000") @DecimalMax("1000000000.000") BigDecimal minimumValue,
            @Digits(integer = 10, fraction = 3) @DecimalMin("-1000000000.000") @DecimalMax("1000000000.000") BigDecimal maximumValue,
            @Digits(integer = 10, fraction = 3) @DecimalMin("0.001") @DecimalMax("1000000000.000") BigDecimal stepValue,
            ProductDimensionType customDimensionType,
            @Size(max = 500) String description,
            boolean active,
            @Min(0) Long rowVersion
    ) {
    }

    public record ValueSaveRequest(
            @NotBlank @Size(max = 120) String customerLabel,
            @NotBlank @Size(max = 120) String managementLabel,
            @NotBlank @Size(max = 120) String productionLabel,
            @NotNull ProductDimensionType dimensionType,
            @Min(-1_000_000_000) @Max(1_000_000_000) Integer priceAdjustment,
            @Size(max = 500) String description,
            @Size(max = 1000) String customerGuide,
            boolean active,
            @Min(0) Long rowVersion
    ) {
    }

    public record ReorderRequest(@NotEmpty @Size(max = 1000) List<@NotNull Long> ids) {
    }

    public record AttributeImageResponse(
            Long id,
            String contentPath,
            String originalFilename,
            String contentType,
            long fileSize,
            int sortOrder
    ) {
    }

    public record AttributeImagePolicyResponse(
            long maxFileSizeBytes,
            int maxFilesPerOwner,
            List<String> allowedContentTypes
    ) {
    }

    public record ValueResponse(
            Long id,
            Long groupId,
            String valueCode,
            String customerLabel,
            String managementLabel,
            String productionLabel,
            ProductDimensionType dimensionType,
            String dimensionTypeLabel,
            int priceAdjustment,
            String description,
            String customerGuide,
            boolean active,
            int sortOrder,
            long rowVersion,
            List<AttributeImageResponse> images
    ) {
    }

    public record GroupResponse(
            Long id,
            String groupCode,
            String customerLabel,
            String managementLabel,
            String productionLabel,
            ProductAttributeGroupType groupType,
            String groupTypeLabel,
            String groupTypeDescription,
            ProductAttributeSelectionMode selectionMode,
            String selectionModeLabel,
            ProductAttributeRole systemRole,
            String systemRoleLabel,
            ProductAttributeInputType inputType,
            String inputTypeLabel,
            String inputTypeDescription,
            String questionText,
            String customerGuide,
            boolean requiredByDefault,
            String unitLabel,
            BigDecimal minimumValue,
            BigDecimal maximumValue,
            BigDecimal stepValue,
            ProductDimensionType customDimensionType,
            String description,
            boolean active,
            int sortOrder,
            long rowVersion,
            List<AttributeImageResponse> images,
            List<ValueResponse> values
    ) {
    }

    public record ProductComponentRequest(
            @NotNull Long groupId,
            Long valueId,
            @Min(1) @Max(100_000) Integer widthMm,
            @Min(1) @Max(100_000) Integer depthMm,
            @Min(1) @Max(100_000) Integer heightMm,
            @Digits(integer = 10, fraction = 3) @DecimalMin("-1000000000.000") @DecimalMax("1000000000.000") BigDecimal numericValue,
            @Size(max = 500) String textValue,
            @Min(0) @Max(999) Integer sortOrder
    ) {
    }

    public record AddonQuantityRequest(
            @NotNull Long valueId,
            @NotNull @Min(-10_000_000) @Max(10_000_000) Integer quantityDelta
    ) {
    }

    public record ProductSaveRequest(
            @NotBlank @Size(max = 160) String productName,
            @Size(max = 1000) String description,
            @NotNull ProductMasterStatus status,
            @NotNull ProductPricingMode pricingMode,
            @NotNull @Min(0) @Max(2_000_000_000) Integer baseSupplyPrice,
            @NotNull @Digits(integer = 3, fraction = 2) @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal vatRate,
            @NotNull @Min(0) @Max(10_000_000) Integer safetyStock,
            @Min(0) @Max(10_000_000) Integer initialStock,
            @Size(max = 500) String initialStockReason,
            @Valid @Size(max = 100) List<@NotNull AddonQuantityRequest> initialAddonQuantities,
            @NotEmpty @Valid @Size(max = 100) List<@NotNull ProductComponentRequest> components,
            @Min(0) Long rowVersion
    ) {
    }

    public record StockAdjustmentRequest(
            @NotNull ProductStockMovementType movementType,
            @NotNull @Min(-10_000_000) @Max(10_000_000) Integer quantityDelta,
            @NotBlank @Size(max = 500) String reason,
            @Valid @Size(max = 100) List<@NotNull AddonQuantityRequest> addonQuantities
    ) {
    }

    public record VoidStockMovementRequest(@NotBlank @Size(max = 500) String reason) {
    }

    public record ComponentResponse(
            Long id,
            Long groupId,
            String groupCode,
            String groupCustomerLabel,
            String groupManagementLabel,
            String groupProductionLabel,
            ProductAttributeGroupType groupType,
            String groupTypeLabel,
            ProductAttributeRole systemRole,
            ProductAttributeInputType inputType,
            Long valueId,
            String valueCode,
            String valueCustomerLabel,
            String valueManagementLabel,
            String valueProductionLabel,
            ProductDimensionType dimensionType,
            String dimensionTypeLabel,
            Integer widthMm,
            Integer depthMm,
            Integer heightMm,
            BigDecimal numericValue,
            String textValue,
            String customerDimensionText,
            String managementDimensionText,
            String productionDimensionText,
            int priceAdjustmentSnapshot,
            int sortOrder,
            boolean valueActive
    ) {
    }

    public record AddonBalanceResponse(
            Long id,
            Long valueId,
            String valueCode,
            String groupManagementLabel,
            String customerLabel,
            String managementLabel,
            String productionLabel,
            int quantity,
            int unitPrice
    ) {
    }

    public record StockAddonLineResponse(
            Long valueId,
            String groupLabel,
            String valueLabel,
            int quantityDelta,
            int balanceBefore,
            int balanceAfter
    ) {
    }

    public record StockMovementResponse(
            Long id,
            ProductStockMovementType movementType,
            String movementTypeLabel,
            int quantityDelta,
            int stockBefore,
            int stockAfter,
            String reason,
            String createdBy,
            LocalDateTime createdAt,
            boolean voided,
            String voidedBy,
            LocalDateTime voidedAt,
            String voidReason,
            List<StockAddonLineResponse> addonLines
    ) {
    }

    public record PriceHistoryResponse(
            Long id,
            ProductPricingMode pricingMode,
            String pricingModeLabel,
            int baseSupplyPrice,
            int componentSupplyPrice,
            int supplyPrice,
            BigDecimal vatRate,
            int vatAmount,
            int totalPrice,
            String changeReason,
            String createdBy,
            LocalDateTime createdAt
    ) {
    }

    public record ProductDetailResponse(
            Long id,
            String productName,
            String productCode,
            String catalogCode,
            String qrPublicToken,
            String publicSpecPath,
            ProductMasterStatus status,
            String statusLabel,
            String description,
            ProductPricingMode pricingMode,
            String pricingModeLabel,
            String pricingModeDescription,
            int baseSupplyPrice,
            int componentSupplyPrice,
            int supplyPrice,
            BigDecimal vatRate,
            int vatAmount,
            int totalPrice,
            int currentStock,
            int safetyStock,
            String stockStatus,
            String stockStatusLabel,
            List<ComponentResponse> components,
            List<AddonBalanceResponse> addonBalances,
            List<StockMovementResponse> stockMovements,
            List<PriceHistoryResponse> priceHistory,
            String createdBy,
            String updatedBy,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            long rowVersion
    ) {
    }

    public record ProductListItemResponse(
            Long id,
            String productName,
            String productCode,
            String catalogCode,
            String qrPublicToken,
            String publicSpecPath,
            ProductMasterStatus status,
            String statusLabel,
            ProductPricingMode pricingMode,
            int supplyPrice,
            int vatAmount,
            int totalPrice,
            int currentStock,
            int safetyStock,
            String stockStatus,
            String stockStatusLabel,
            List<ComponentResponse> components,
            List<AddonBalanceResponse> addonBalances,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    public record PageResponse<T>(
            List<T> content,
            int page,
            int size,
            long totalElements,
            int totalPages,
            boolean first,
            boolean last
    ) {
    }

    public record PublicComponentResponse(
            String groupLabel,
            String valueLabel,
            String dimensionText,
            ProductAttributeGroupType groupType,
            String groupTypeLabel,
            List<AttributeImageResponse> groupImages,
            List<AttributeImageResponse> valueImages
    ) {
    }

    public record PublicProductResponse(
            String productName,
            String catalogCode,
            ProductMasterStatus status,
            String statusLabel,
            String description,
            List<PublicComponentResponse> coreSpecifications,
            List<PublicComponentResponse> availableAddons,
            LocalDateTime updatedAt
    ) {
    }
}
