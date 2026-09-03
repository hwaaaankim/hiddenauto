package com.dev.HiddenBATHAuto.service.productmaster;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.dev.HiddenBATHAuto.dto.productmaster.ProductMasterDtos.AddonBalanceResponse;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductMasterDtos.AttributeImageResponse;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductMasterDtos.ComponentResponse;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductMasterDtos.PriceHistoryResponse;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductMasterDtos.ProductDetailResponse;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductMasterDtos.ProductListItemResponse;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductMasterDtos.PublicComponentResponse;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductMasterDtos.PublicProductResponse;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductMasterDtos.StockAddonLineResponse;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductMasterDtos.StockMovementResponse;
import com.dev.HiddenBATHAuto.enums.productmaster.ProductAttributeGroupType;
import com.dev.HiddenBATHAuto.enums.productmaster.ProductAttributeRole;
import com.dev.HiddenBATHAuto.enums.productmaster.ProductDimensionType;
import com.dev.HiddenBATHAuto.model.productmaster.ProductAddonBalance;
import com.dev.HiddenBATHAuto.model.productmaster.ProductComponent;
import com.dev.HiddenBATHAuto.model.productmaster.ProductMaster;
import com.dev.HiddenBATHAuto.model.productmaster.ProductPriceHistory;
import com.dev.HiddenBATHAuto.model.productmaster.ProductStockMovement;
import com.dev.HiddenBATHAuto.repository.productmaster.ProductAddonBalanceRepository;
import com.dev.HiddenBATHAuto.repository.productmaster.ProductComponentRepository;
import com.dev.HiddenBATHAuto.repository.productmaster.ProductPriceHistoryRepository;
import com.dev.HiddenBATHAuto.repository.productmaster.ProductStockMovementRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductMasterResponseMapper {

    private final ProductComponentRepository componentRepository;
    private final ProductAddonBalanceRepository addonBalanceRepository;
    private final ProductStockMovementRepository movementRepository;
    private final ProductPriceHistoryRepository priceHistoryRepository;
    private final ProductAttributeImageService imageService;

    public ProductDetailResponse toDetail(ProductMaster product) {
        List<ComponentResponse> components = componentRepository.findDetailedByProductId(product.getId()).stream()
                .map(this::toComponent)
                .toList();
        List<AddonBalanceResponse> balances = addonBalanceRepository.findDetailedByProductId(product.getId()).stream()
                .map(this::toAddonBalance)
                .toList();
        List<StockMovementResponse> movements = movementRepository.findDetailedByProductId(product.getId()).stream()
                .map(this::toStockMovement)
                .toList();
        List<PriceHistoryResponse> prices = priceHistoryRepository
                .findByProductIdOrderByCreatedAtDescIdDesc(product.getId()).stream()
                .map(this::toPriceHistory)
                .toList();

        StockState stockState = stockState(product.getCurrentStock(), product.getSafetyStock());
        return new ProductDetailResponse(
                product.getId(),
                product.getProductName(),
                product.getProductCode(),
                product.getCatalogCode(),
                product.getQrPublicToken(),
                "/product-spec/" + product.getQrPublicToken(),
                product.getStatus(),
                product.getStatus().getLabelKr(),
                product.getDescription(),
                product.getPricingMode(),
                product.getPricingMode().getLabelKr(),
                product.getPricingMode().getDescription(),
                product.getBaseSupplyPrice(),
                product.getComponentSupplyPrice(),
                product.getSupplyPrice(),
                product.getVatRate(),
                product.getVatAmount(),
                product.getTotalPrice(),
                product.getCurrentStock(),
                product.getSafetyStock(),
                stockState.code(),
                stockState.label(),
                components,
                balances,
                movements,
                prices,
                product.getCreatedBy(),
                product.getUpdatedBy(),
                product.getCreatedAt(),
                product.getUpdatedAt(),
                product.getRowVersion()
        );
    }

    public List<ProductListItemResponse> toListItems(Collection<ProductMaster> products) {
        if (products == null || products.isEmpty()) {
            return List.of();
        }

        List<Long> productIds = products.stream().map(ProductMaster::getId).toList();
        Map<Long, List<ComponentResponse>> componentsByProduct = componentRepository
                .findDetailedByProductIds(productIds).stream()
                .collect(Collectors.groupingBy(
                        component -> component.getProduct().getId(),
                        LinkedHashMap::new,
                        Collectors.mapping(this::toComponent, Collectors.toList())
                ));
        Map<Long, List<AddonBalanceResponse>> balancesByProduct = addonBalanceRepository
                .findDetailedByProductIds(productIds).stream()
                .collect(Collectors.groupingBy(
                        balance -> balance.getProduct().getId(),
                        LinkedHashMap::new,
                        Collectors.mapping(this::toAddonBalance, Collectors.toList())
                ));

        return products.stream().map(product -> {
            StockState stockState = stockState(product.getCurrentStock(), product.getSafetyStock());
            return new ProductListItemResponse(
                    product.getId(),
                    product.getProductName(),
                    product.getProductCode(),
                    product.getCatalogCode(),
                    product.getQrPublicToken(),
                    "/product-spec/" + product.getQrPublicToken(),
                    product.getStatus(),
                    product.getStatus().getLabelKr(),
                    product.getPricingMode(),
                    product.getSupplyPrice(),
                    product.getVatAmount(),
                    product.getTotalPrice(),
                    product.getCurrentStock(),
                    product.getSafetyStock(),
                    stockState.code(),
                    stockState.label(),
                    componentsByProduct.getOrDefault(product.getId(), Collections.emptyList()),
                    balancesByProduct.getOrDefault(product.getId(), Collections.emptyList()),
                    product.getCreatedAt(),
                    product.getUpdatedAt()
            );
        }).toList();
    }

    public PublicProductResponse toPublic(ProductMaster product) {
        List<ProductComponent> components = componentRepository.findDetailedByProductId(product.getId());
        Map<Long, List<AttributeImageResponse>> groupImages = imageService.getGroupImageMap(
                components.stream().map(component -> component.getGroup().getId()).distinct().toList()
        );
        Map<Long, List<AttributeImageResponse>> valueImages = imageService.getValueImageMap(
                components.stream().map(ProductComponent::getValue).filter(java.util.Objects::nonNull)
                        .map(value -> value.getId()).distinct().toList()
        );
        List<PublicComponentResponse> core = components.stream()
                .filter(component -> component.getGroup().getGroupType() != ProductAttributeGroupType.ADD_ON)
                .map(component -> toPublicComponent(component, groupImages, valueImages))
                .toList();
        List<PublicComponentResponse> addons = components.stream()
                .filter(component -> component.getGroup().getGroupType() == ProductAttributeGroupType.ADD_ON)
                .map(component -> toPublicComponent(component, groupImages, valueImages))
                .toList();
        return new PublicProductResponse(
                product.getProductName(),
                product.getCatalogCode(),
                product.getStatus(),
                product.getStatus().getLabelKr(),
                product.getDescription(),
                core,
                addons,
                product.getUpdatedAt()
        );
    }

    private ComponentResponse toComponent(ProductComponent component) {
        var group = component.getGroup();
        var value = component.getValue();
        ProductDimensionType dimensionType = value == null ? ProductDimensionType.NONE : value.getDimensionType();
        return new ComponentResponse(
                component.getId(),
                group.getId(),
                group.getGroupCode(),
                group.getCustomerLabel(),
                group.getManagementLabel(),
                group.getProductionLabel(),
                group.getGroupType(),
                group.getGroupType().getLabelKr(),
                group.getSystemRole(),
                group.getInputType(),
                value == null ? null : value.getId(),
                value == null ? null : value.getValueCode(),
                value == null ? directValue(component) : value.getCustomerLabel(),
                value == null ? directValue(component) : value.getManagementLabel(),
                value == null ? directValue(component) : value.getProductionLabel(),
                dimensionType,
                dimensionType.getLabelKr(),
                component.getWidthMm(),
                component.getDepthMm(),
                component.getHeightMm(),
                component.getNumericValue(),
                component.getTextValue(),
                dimensionText(dimensionType, component, Audience.CUSTOMER),
                dimensionText(dimensionType, component, Audience.MANAGEMENT),
                dimensionText(dimensionType, component, Audience.PRODUCTION),
                component.getPriceAdjustmentSnapshot(),
                component.getSortOrder(),
                value == null ? group.isActive() : value.isActive()
        );
    }

    private AddonBalanceResponse toAddonBalance(ProductAddonBalance balance) {
        var value = balance.getOptionValue();
        return new AddonBalanceResponse(
                balance.getId(),
                value.getId(),
                value.getValueCode(),
                value.getGroup().getManagementLabel(),
                value.getCustomerLabel(),
                value.getManagementLabel(),
                value.getProductionLabel(),
                balance.getQuantity(),
                value.getPriceAdjustment()
        );
    }

    private StockMovementResponse toStockMovement(ProductStockMovement movement) {
        List<StockAddonLineResponse> lines = movement.getAddonLines().stream()
                .sorted(Comparator.comparing(line -> line.getOptionValue().getValueCode()))
                .map(line -> new StockAddonLineResponse(
                        line.getOptionValue().getId(),
                        line.getOptionValue().getGroup().getManagementLabel(),
                        line.getOptionValue().getManagementLabel(),
                        line.getQuantityDelta(),
                        line.getBalanceBefore(),
                        line.getBalanceAfter()
                ))
                .toList();
        return new StockMovementResponse(
                movement.getId(),
                movement.getMovementType(),
                movement.getMovementType().getLabelKr(),
                movement.getQuantityDelta(),
                movement.getStockBefore(),
                movement.getStockAfter(),
                movement.getReason(),
                movement.getCreatedBy(),
                movement.getCreatedAt(),
                movement.isVoided(),
                movement.getVoidedBy(),
                movement.getVoidedAt(),
                movement.getVoidReason(),
                lines
        );
    }

    private PriceHistoryResponse toPriceHistory(ProductPriceHistory history) {
        return new PriceHistoryResponse(
                history.getId(),
                history.getPricingMode(),
                history.getPricingMode().getLabelKr(),
                history.getBaseSupplyPrice(),
                history.getComponentSupplyPrice(),
                history.getSupplyPrice(),
                history.getVatRate(),
                history.getVatAmount(),
                history.getTotalPrice(),
                history.getChangeReason(),
                history.getCreatedBy(),
                history.getCreatedAt()
        );
    }

    private PublicComponentResponse toPublicComponent(
            ProductComponent component,
            Map<Long, List<AttributeImageResponse>> groupImages,
            Map<Long, List<AttributeImageResponse>> valueImages
    ) {
        return new PublicComponentResponse(
                component.getGroup().getCustomerLabel(),
                component.getValue() == null ? directValue(component) : component.getValue().getCustomerLabel(),
                dimensionText(component.getValue() == null ? ProductDimensionType.NONE
                        : component.getValue().getDimensionType(), component, Audience.CUSTOMER),
                component.getGroup().getGroupType(),
                component.getGroup().getGroupType().getLabelKr(),
                groupImages.getOrDefault(component.getGroup().getId(), Collections.emptyList()),
                component.getValue() == null ? Collections.emptyList()
                        : valueImages.getOrDefault(component.getValue().getId(), Collections.emptyList())
        );
    }

    private String directValue(ProductComponent component) {
        if (component.getNumericValue() != null) {
            String unit = component.getGroup().getUnitLabel() == null ? "" : component.getGroup().getUnitLabel();
            return component.getNumericValue().stripTrailingZeros().toPlainString() + unit;
        }
        return component.getTextValue() == null ? "직접 입력" : component.getTextValue();
    }

    private String dimensionText(ProductDimensionType type, ProductComponent component, Audience audience) {
        if (type == null || type == ProductDimensionType.NONE) {
            return null;
        }
        if (type == ProductDimensionType.CUSTOM) {
            if (audience != Audience.CUSTOMER) {
                return "비규격";
            }
            return component.getGroup().getSystemRole() == ProductAttributeRole.SIZE
                    ? "주문 시 원하는 사이즈 입력"
                    : "주문 시 원하는 세부 사양 입력";
        }
        if (audience == Audience.CUSTOMER) {
            if (type == ProductDimensionType.WIDTH_HEIGHT) {
                return "W:" + component.getWidthMm() + " / H:" + component.getHeightMm() + " mm";
            }
            return "W:" + component.getWidthMm()
                    + " / D:" + component.getDepthMm()
                    + " / H:" + component.getHeightMm() + " mm";
        }
        if (type == ProductDimensionType.WIDTH_HEIGHT) {
            return component.getWidthMm() + "*" + component.getHeightMm();
        }
        return component.getWidthMm() + "*" + component.getDepthMm() + "*" + component.getHeightMm();
    }

    private StockState stockState(int currentStock, int safetyStock) {
        if (currentStock <= 0) {
            return new StockState("OUT_OF_STOCK", "재고없음");
        }
        if (currentStock <= safetyStock) {
            return new StockState("LOW_STOCK", "안전재고 이하");
        }
        return new StockState("IN_STOCK", "재고정상");
    }

    private enum Audience {
        CUSTOMER,
        MANAGEMENT,
        PRODUCTION
    }

    private record StockState(String code, String label) {
    }
}
