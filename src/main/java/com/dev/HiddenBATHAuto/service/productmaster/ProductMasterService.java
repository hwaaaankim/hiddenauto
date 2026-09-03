package com.dev.HiddenBATHAuto.service.productmaster;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dev.HiddenBATHAuto.dto.productmaster.ProductMasterDtos.PageResponse;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductMasterDtos.ProductComponentRequest;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductMasterDtos.ProductDetailResponse;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductMasterDtos.ProductListItemResponse;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductMasterDtos.ProductSaveRequest;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductMasterDtos.PublicProductResponse;
import com.dev.HiddenBATHAuto.enums.productmaster.ProductAttributeGroupType;
import com.dev.HiddenBATHAuto.enums.productmaster.ProductAttributeInputType;
import com.dev.HiddenBATHAuto.enums.productmaster.ProductAttributeRole;
import com.dev.HiddenBATHAuto.enums.productmaster.ProductAttributeSelectionMode;
import com.dev.HiddenBATHAuto.enums.productmaster.ProductDimensionType;
import com.dev.HiddenBATHAuto.enums.productmaster.ProductMasterStatus;
import com.dev.HiddenBATHAuto.enums.productmaster.ProductPricingMode;
import com.dev.HiddenBATHAuto.model.productmaster.ProductAddonBalance;
import com.dev.HiddenBATHAuto.model.productmaster.ProductAttributeGroup;
import com.dev.HiddenBATHAuto.model.productmaster.ProductAttributeValue;
import com.dev.HiddenBATHAuto.model.productmaster.ProductComponent;
import com.dev.HiddenBATHAuto.model.productmaster.ProductMaster;
import com.dev.HiddenBATHAuto.model.productmaster.ProductPriceHistory;
import com.dev.HiddenBATHAuto.repository.productmaster.ProductAddonBalanceRepository;
import com.dev.HiddenBATHAuto.repository.productmaster.ProductAttributeValueRepository;
import com.dev.HiddenBATHAuto.repository.productmaster.ProductAttributeGroupRepository;
import com.dev.HiddenBATHAuto.repository.productmaster.ProductComponentRepository;
import com.dev.HiddenBATHAuto.repository.productmaster.ProductMasterRepository;
import com.dev.HiddenBATHAuto.repository.productmaster.ProductPriceHistoryRepository;
import com.dev.HiddenBATHAuto.repository.productmaster.ProductStockMovementAddonRepository;
import com.dev.HiddenBATHAuto.repository.productmaster.ProductStockMovementRepository;
import com.dev.HiddenBATHAuto.service.productmaster.ProductMasterCodeService.IdentityPart;
import com.dev.HiddenBATHAuto.service.productmaster.ProductMasterSpecifications.AttributeFilter;
import com.dev.HiddenBATHAuto.service.productmaster.ProductMasterSpecifications.DimensionFilter;
import com.dev.HiddenBATHAuto.service.productmaster.ProductMasterSpecifications.NumberAttributeFilter;
import com.dev.HiddenBATHAuto.service.productmaster.ProductMasterSpecifications.TextAttributeFilter;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductMasterService {

    private static final Set<Integer> ALLOWED_PAGE_SIZES = Set.of(30, 50, 100, 200);
    private static final Map<String, String> ALLOWED_SORTS = Map.of(
            "productName", "productName",
            "catalogCode", "catalogCode",
            "currentStock", "currentStock",
            "supplyPrice", "supplyPrice",
            "totalPrice", "totalPrice",
            "createdAt", "createdAt",
            "updatedAt", "updatedAt"
    );

    private final ProductMasterRepository productRepository;
    private final ProductAttributeValueRepository valueRepository;
    private final ProductAttributeGroupRepository groupRepository;
    private final ProductComponentRepository componentRepository;
    private final ProductAddonBalanceRepository addonBalanceRepository;
    private final ProductPriceHistoryRepository priceHistoryRepository;
    private final ProductStockMovementAddonRepository movementAddonRepository;
    private final ProductStockMovementRepository movementRepository;
    private final ProductMasterCodeService codeService;
    private final ProductMasterResponseMapper responseMapper;
    private final ProductInventoryService inventoryService;

    public PageResponse<ProductListItemResponse> search(
            int page,
            int size,
            String keyword,
            ProductMasterStatus status,
            String stockStatus,
            List<String> attributes,
            List<String> numberAttributes,
            List<String> textAttributes,
            List<String> sorts,
            Integer widthMin,
            Integer widthMax,
            Integer depthMin,
            Integer depthMax,
            Integer heightMin,
            Integer heightMax
    ) {
        int safePage = Math.max(page, 0);
        if (!ALLOWED_PAGE_SIZES.contains(size)) {
            throw new IllegalArgumentException("페이지 크기는 30, 50, 100, 200 중 하나여야 합니다.");
        }

        List<AttributeFilter> filters = parseAttributeFilters(attributes);
        List<NumberAttributeFilter> numberFilters = parseNumberAttributeFilters(numberAttributes);
        List<TextAttributeFilter> textFilters = parseTextAttributeFilters(textAttributes);
        DimensionFilter dimensions = validateDimensionFilter(
                widthMin, widthMax, depthMin, depthMax, heightMin, heightMax
        );
        Sort sort = parseSort(sorts);
        String normalizedKeyword = keyword == null ? null : keyword.trim();
        if (normalizedKeyword != null && normalizedKeyword.length() > 700) {
            throw new IllegalArgumentException("검색어는 700자 이하여야 합니다.");
        }
        var specification = ProductMasterSpecifications.byCriteria(
                normalizedKeyword,
                status,
                stockStatus,
                filters,
                numberFilters,
                textFilters,
                dimensions
        );
        Page<ProductMaster> result = productRepository.findAll(
                specification,
                PageRequest.of(safePage, size, sort)
        );
        if (result.getTotalPages() > 0 && safePage >= result.getTotalPages()) {
            safePage = result.getTotalPages() - 1;
            result = productRepository.findAll(specification, PageRequest.of(safePage, size, sort));
        }
        List<ProductListItemResponse> content = responseMapper.toListItems(result.getContent());
        return new PageResponse<>(
                content,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.isFirst(),
                result.isLast()
        );
    }

    public ProductDetailResponse getProduct(Long productId) {
        return responseMapper.toDetail(requireProduct(productId));
    }

    public ProductDetailResponse resolveCode(String code) {
        String normalized = requiredText(code, "제품 코드", 700);
        ProductMaster product = productRepository.findByCatalogCodeIgnoreCase(normalized)
                .or(() -> productRepository.findByProductCode(normalized))
                .orElseThrow(() -> new java.util.NoSuchElementException("해당 코드의 제품을 찾을 수 없습니다."));
        return responseMapper.toDetail(product);
    }

    public PublicProductResponse getPublicProduct(String token) {
        String normalized = requiredText(token, "QR 토큰", 36);
        ProductMaster product = productRepository.findByQrPublicToken(normalized)
                .filter(item -> item.getStatus() != ProductMasterStatus.DRAFT)
                .orElseThrow(() -> new java.util.NoSuchElementException("공개된 제품 정보를 찾을 수 없습니다."));
        return responseMapper.toPublic(product);
    }

    @Transactional
    public ProductDetailResponse create(ProductSaveRequest request, String actor) {
        String normalizedActor = normalizeActor(actor);
        String productName = requiredText(request.productName(), "제품명", 160);
        if (productRepository.existsByProductNameIgnoreCase(productName)) {
            throw new IllegalStateException("동일한 제품명이 이미 등록되어 있습니다.");
        }

        List<ResolvedComponent> resolved = resolveAndValidateComponents(request.components(), Set.of(), Set.of());
        ProductIdentity identity = buildIdentity(resolved);
        if (productRepository.existsByConfigurationHash(identity.hash())) {
            throw new IllegalStateException(
                    "동일한 핵심 사양의 제품이 이미 존재합니다. 기존 제품에서 추가 옵션과 재고를 관리해 주세요."
            );
        }

        ProductMaster product = new ProductMaster();
        product.setProductName(productName);
        product.setProductCode(identity.productCode());
        product.setConfigurationHash(identity.hash());
        product.setCatalogCode(codeService.newCatalogCode(identity.productCode()));
        product.setQrPublicToken(UUID.randomUUID().toString());
        product.setDescription(optionalText(request.description(), "제품 설명", 1000));
        product.setStatus(Objects.requireNonNull(request.status(), "제품 상태가 필요합니다."));
        product.setPricingMode(Objects.requireNonNull(request.pricingMode(), "가격계산 방식이 필요합니다."));
        product.setSafetyStock(nonNegative(request.safetyStock(), "안전재고"));
        product.setCurrentStock(0);
        product.setCreatedBy(normalizedActor);
        product.setUpdatedBy(normalizedActor);

        PriceCalculation price = calculatePrice(request, resolved);
        applyPrice(product, price);
        resolved.forEach(item -> product.addComponent(newComponent(item)));

        productRepository.saveAndFlush(product);
        syncAddonBalances(product, resolved);
        recordPriceHistory(product, "제품 최초 등록", normalizedActor);

        int initialStock = request.initialStock() == null ? 0 : request.initialStock();
        if (initialStock < 0) {
            throw new IllegalArgumentException("최초재고는 0 이상이어야 합니다.");
        }
        boolean hasInitialAddon = request.initialAddonQuantities() != null
                && request.initialAddonQuantities().stream()
                .filter(Objects::nonNull)
                .anyMatch(item -> item.quantityDelta() != null && item.quantityDelta() != 0);
        if (initialStock > 0 || hasInitialAddon) {
            inventoryService.recordInitialStock(
                    product.getId(),
                    initialStock,
                    request.initialStockReason(),
                    request.initialAddonQuantities(),
                    normalizedActor
            );
        }
        productRepository.flush();
        return responseMapper.toDetail(product);
    }

    @Transactional
    public ProductDetailResponse update(Long productId, ProductSaveRequest request, String actor) {
        String normalizedActor = normalizeActor(actor);
        ProductMaster product = productRepository.findForUpdate(productId)
                .orElseThrow(() -> new java.util.NoSuchElementException("제품을 찾을 수 없습니다."));
        verifyVersion(request.rowVersion(), product.getRowVersion(), "제품");
        String productName = requiredText(request.productName(), "제품명", 160);
        if (productRepository.existsByProductNameIgnoreCaseAndIdNot(productName, productId)) {
            throw new IllegalStateException("동일한 제품명이 이미 등록되어 있습니다.");
        }

        List<ProductComponent> existingComponents = componentRepository.findDetailedByProductId(productId);
        Set<Long> existingValueIds = existingComponents.stream()
                .map(ProductComponent::getValue)
                .filter(Objects::nonNull)
                .map(ProductAttributeValue::getId)
                .collect(Collectors.toSet());
        Set<Long> existingGroupIds = existingComponents.stream()
                .map(component -> component.getGroup().getId())
                .collect(Collectors.toSet());
        List<ResolvedComponent> resolved = resolveAndValidateComponents(
                request.components(), existingValueIds, existingGroupIds
        );
        ProductIdentity identity = buildIdentity(resolved);
        if (productRepository.existsByConfigurationHashAndIdNot(identity.hash(), productId)
                || productRepository.existsByProductCodeAndIdNot(identity.productCode(), productId)) {
            throw new IllegalStateException(
                    "동일한 핵심 사양의 제품이 이미 존재합니다. 두 제품을 하나의 재고 품목으로 관리해야 합니다."
            );
        }

        boolean identityChanged = !product.getConfigurationHash().equals(identity.hash());
        if (identityChanged && (product.getCurrentStock() != 0
                || movementRepository.existsByProductIdAndVoidedFalse(productId))) {
            throw new IllegalStateException(
                    "재고 또는 유효한 재고 원장이 있는 제품의 핵심 사양은 변경할 수 없습니다. 새 제품으로 등록해 주세요."
            );
        }

        validateRemovableAddons(productId, resolved);
        PriceSnapshot previousPrice = PriceSnapshot.from(product);

        product.setProductName(productName);
        product.setProductCode(identity.productCode());
        product.setConfigurationHash(identity.hash());
        product.setDescription(optionalText(request.description(), "제품 설명", 1000));
        product.setStatus(Objects.requireNonNull(request.status(), "제품 상태가 필요합니다."));
        product.setPricingMode(Objects.requireNonNull(request.pricingMode(), "가격계산 방식이 필요합니다."));
        product.setSafetyStock(nonNegative(request.safetyStock(), "안전재고"));
        product.setUpdatedBy(normalizedActor);
        product.setUpdatedAt(LocalDateTime.now());

        PriceCalculation price = calculatePrice(request, resolved);
        applyPrice(product, price);
        mergeComponents(product, existingComponents, resolved);
        componentRepository.flush();
        syncAddonBalances(product, resolved);
        productRepository.flush();

        if (!previousPrice.equals(PriceSnapshot.from(product))) {
            recordPriceHistory(product, "제품 정보 수정에 따른 가격 변경", normalizedActor);
        }
        return responseMapper.toDetail(product);
    }

    private List<ResolvedComponent> resolveAndValidateComponents(
            List<ProductComponentRequest> requests,
            Set<Long> allowedInactiveValueIds,
            Set<Long> allowedInactiveGroupIds
    ) {
        if (requests == null || requests.isEmpty()) {
            throw new IllegalArgumentException("제품을 구성하는 옵션을 하나 이상 선택해 주세요.");
        }
        if (requests.size() > 100) {
            throw new IllegalArgumentException("한 제품에는 최대 100개의 구성값을 등록할 수 있습니다.");
        }
        if (requests.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("제품 구성에 비어 있는 옵션값이 포함되어 있습니다.");
        }
        if (requests.stream().anyMatch(request -> request.groupId() == null || request.groupId() <= 0
                || (request.valueId() != null && request.valueId() <= 0))) {
            throw new IllegalArgumentException("옵션 그룹과 옵션값의 올바른 ID가 필요합니다.");
        }

        Set<Long> groupIds = requests.stream().map(ProductComponentRequest::groupId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, ProductAttributeGroup> groups = groupRepository.findAllById(groupIds).stream()
                .collect(Collectors.toMap(ProductAttributeGroup::getId, Function.identity()));
        if (groups.size() != groupIds.size()) {
            throw new IllegalArgumentException("존재하지 않는 옵션 그룹이 포함되어 있습니다.");
        }
        Set<Long> valueIds = requests.stream()
                .map(ProductComponentRequest::valueId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, ProductAttributeValue> values = valueRepository.findAllWithGroupByIdIn(valueIds).stream()
                .collect(Collectors.toMap(ProductAttributeValue::getId, Function.identity()));
        if (values.size() != valueIds.size()) {
            throw new IllegalArgumentException("존재하지 않는 옵션값이 포함되어 있습니다.");
        }

        List<ResolvedComponent> resolved = new ArrayList<>();
        Set<String> duplicateGuard = new HashSet<>();
        Map<Long, Integer> groupCounts = new HashMap<>();
        int fallbackOrder = 1;

        for (ProductComponentRequest request : requests) {
            ProductAttributeGroup group = groups.get(request.groupId());
            ProductAttributeValue value = request.valueId() == null ? null : values.get(request.valueId());
            if (value != null && !value.getGroup().getId().equals(request.groupId())) {
                throw new IllegalArgumentException("옵션값과 옵션 그룹의 연결이 올바르지 않습니다.");
            }
            if (!group.isActive() && !allowedInactiveGroupIds.contains(group.getId())) {
                throw new IllegalStateException("사용 중지된 옵션 그룹 또는 옵션값은 새 제품에 선택할 수 없습니다.");
            }
            if (value != null && !value.isActive() && !allowedInactiveValueIds.contains(value.getId())) {
                throw new IllegalStateException("사용 중지된 옵션 그룹 또는 옵션값은 새 제품에 선택할 수 없습니다.");
            }

            validateComponentInput(group, value, request);

            String duplicateKey = group.getId() + ":" + (value == null ? "INPUT" : value.getId());
            if (!duplicateGuard.add(duplicateKey)) {
                throw new IllegalArgumentException("동일한 옵션값이 중복 선택되어 있습니다.");
            }

            groupCounts.merge(group.getId(), 1, Integer::sum);
            int sortOrder = request.sortOrder() == null ? fallbackOrder : request.sortOrder();
            if (sortOrder < 0 || sortOrder > 999) {
                throw new IllegalArgumentException("제품 구성 표시 순서는 0~999 범위여야 합니다.");
            }
            fallbackOrder += 1;
            resolved.add(new ResolvedComponent(
                    group,
                    value,
                    request.widthMm(),
                    request.depthMm(),
                    request.heightMm(),
                    request.numericValue(),
                    normalizeComponentText(request.textValue()),
                    sortOrder
            ));
        }

        for (ResolvedComponent item : resolved) {
            ProductAttributeGroup group = item.group();
            if (group.getSelectionMode() == ProductAttributeSelectionMode.SINGLE
                    && groupCounts.getOrDefault(group.getId(), 0) > 1) {
                throw new IllegalArgumentException(group.getManagementLabel() + " 그룹에서는 하나의 값만 선택할 수 있습니다.");
            }
        }
        Map<Long, List<ResolvedComponent>> resolvedByGroup = resolved.stream()
                .collect(Collectors.groupingBy(item -> item.group().getId()));
        for (List<ResolvedComponent> groupItems : resolvedByGroup.values()) {
            boolean customSelected = groupItems.stream()
                    .map(ResolvedComponent::value)
                    .filter(Objects::nonNull)
                    .anyMatch(value -> value.getDimensionType() == ProductDimensionType.CUSTOM);
            if (customSelected && groupItems.size() > 1) {
                throw new IllegalArgumentException(
                        groupItems.get(0).group().getManagementLabel() + "의 비규격 값은 다른 값과 함께 선택할 수 없습니다."
                );
            }
        }

        long coreCount = resolved.stream()
                .filter(item -> item.group().getGroupType() == ProductAttributeGroupType.CORE)
                .count();
        long categoryCount = resolved.stream()
                .filter(item -> item.group().getGroupType() == ProductAttributeGroupType.CORE)
                .filter(item -> item.group().getSystemRole() == ProductAttributeRole.CATEGORY)
                .count();
        if (coreCount == 0) {
            throw new IllegalArgumentException("제품 재고 정체성을 결정할 핵심 구성요소가 하나 이상 필요합니다.");
        }
        if (categoryCount != 1) {
            throw new IllegalArgumentException("제품에는 핵심 구성요소인 대분류 값을 정확히 하나 선택해야 합니다.");
        }

        return resolved.stream()
                .sorted(Comparator.comparingInt(ResolvedComponent::sortOrder))
                .toList();
    }

    private void validateComponentInput(
            ProductAttributeGroup group,
            ProductAttributeValue value,
            ProductComponentRequest request
    ) {
        ProductAttributeInputType inputType = group.getInputType();
        if (inputType == ProductAttributeInputType.CHOICE || inputType == ProductAttributeInputType.DIMENSION) {
            if (value == null) {
                throw new IllegalArgumentException(group.getManagementLabel() + " 그룹의 옵션값을 선택해 주세요.");
            }
            if (request.numericValue() != null) {
                throw new IllegalArgumentException(group.getManagementLabel() + " 선택형 그룹에는 숫자 입력값을 저장할 수 없습니다.");
            }
            if (request.textValue() != null && !request.textValue().isBlank()) {
                throw new IllegalArgumentException(
                        "선택형 제품 마스터에는 주문별 비규격 문구를 저장하지 않습니다. 비규격 옵션값만 선택하고 실제 내용은 고객 구성에서 입력해 주세요."
                );
            }
            validateDimensions(value.getDimensionType(), request);
            return;
        }
        if (value != null) {
            throw new IllegalArgumentException(group.getManagementLabel() + " 그룹은 옵션값 대신 직접 입력값을 사용합니다.");
        }
        if (request.widthMm() != null || request.depthMm() != null || request.heightMm() != null) {
            throw new IllegalArgumentException(group.getManagementLabel() + " 그룹에는 치수를 저장할 수 없습니다.");
        }
        if (inputType == ProductAttributeInputType.NUMBER) {
            BigDecimal number = request.numericValue();
            if (number == null) throw new IllegalArgumentException(group.getManagementLabel() + " 숫자값을 입력해 주세요.");
            if (group.getMinimumValue() != null && number.compareTo(group.getMinimumValue()) < 0) {
                throw new IllegalArgumentException(group.getManagementLabel() + " 값은 " + group.getMinimumValue() + " 이상이어야 합니다.");
            }
            if (group.getMaximumValue() != null && number.compareTo(group.getMaximumValue()) > 0) {
                throw new IllegalArgumentException(group.getManagementLabel() + " 값은 " + group.getMaximumValue() + " 이하여야 합니다.");
            }
            if (group.getStepValue() != null && group.getStepValue().signum() > 0
                    && number.subtract(group.getMinimumValue() == null ? BigDecimal.ZERO : group.getMinimumValue())
                    .remainder(group.getStepValue()).signum() != 0) {
                throw new IllegalArgumentException(
                        group.getManagementLabel() + " 값은 " + group.getStepValue() + " 단위로 입력해 주세요."
                );
            }
            if (request.textValue() != null && !request.textValue().isBlank()) {
                throw new IllegalArgumentException("숫자형 그룹에는 문자 입력값을 저장할 수 없습니다.");
            }
            return;
        }
        if (inputType == ProductAttributeInputType.TEXT) {
            if (request.textValue() == null || request.textValue().isBlank()) {
                throw new IllegalArgumentException(group.getManagementLabel() + " 내용을 입력해 주세요.");
            }
            if (request.numericValue() != null) {
                throw new IllegalArgumentException("문자형 그룹에는 숫자 입력값을 저장할 수 없습니다.");
            }
        }
    }

    private void validateDimensions(ProductDimensionType type, ProductComponentRequest request) {
        if (type == ProductDimensionType.WIDTH_HEIGHT) {
            requireDimension(request.widthMm(), "W");
            requireDimension(request.heightMm(), "H");
            if (request.depthMm() != null) {
                throw new IllegalArgumentException("W-H 사이즈에는 D 값을 입력할 수 없습니다.");
            }
            return;
        }
        if (type == ProductDimensionType.WIDTH_DEPTH_HEIGHT) {
            requireDimension(request.widthMm(), "W");
            requireDimension(request.depthMm(), "D");
            requireDimension(request.heightMm(), "H");
            return;
        }
        if (request.widthMm() != null || request.depthMm() != null || request.heightMm() != null) {
            throw new IllegalArgumentException("일반 옵션 또는 비규격 옵션에는 고정 치수를 저장할 수 없습니다.");
        }
    }

    private void requireDimension(Integer value, String label) {
        if (value == null || value <= 0 || value > 100_000) {
            throw new IllegalArgumentException(label + " 사이즈는 1~100000mm 범위로 입력해 주세요.");
        }
    }

    private ProductIdentity buildIdentity(List<ResolvedComponent> resolved) {
        List<IdentityPart> parts = resolved.stream()
                .filter(item -> item.group().getGroupType() == ProductAttributeGroupType.CORE)
                .map(item -> new IdentityPart(
                        item.group().getGroupCode(),
                        item.value() == null ? null : item.value().getValueCode(),
                        item.group().getInputType(),
                        item.value() == null ? ProductDimensionType.NONE : item.value().getDimensionType(),
                        item.widthMm(),
                        item.depthMm(),
                        item.heightMm(),
                        item.numericValue(),
                        item.textValue()
                ))
                .toList();
        String productCode = codeService.buildProductCode(parts);
        return new ProductIdentity(productCode, codeService.configurationHash(productCode));
    }

    private PriceCalculation calculatePrice(ProductSaveRequest request, List<ResolvedComponent> resolved) {
        int basePrice = nonNegative(request.baseSupplyPrice(), "기본 공급가");
        BigDecimal vatRate = request.vatRate() == null ? new BigDecimal("10.00") : request.vatRate();
        if (vatRate.compareTo(BigDecimal.ZERO) < 0 || vatRate.compareTo(new BigDecimal("100.00")) > 0) {
            throw new IllegalArgumentException("부가세율은 0~100 범위로 입력해 주세요.");
        }
        vatRate = vatRate.setScale(2, RoundingMode.HALF_UP);

        long componentPrice = 0;
        if (request.pricingMode() == ProductPricingMode.BASE_PLUS_COMPONENTS) {
            componentPrice = resolved.stream()
                    .filter(item -> item.group().getGroupType() == ProductAttributeGroupType.CORE)
                    .mapToLong(item -> item.value() == null ? 0 : item.value().getPriceAdjustment())
                    .sum();
        }
        long supplyPrice = (long) basePrice + componentPrice;
        if (supplyPrice < 0 || supplyPrice > 2_000_000_000L) {
            throw new IllegalArgumentException("구성요소 반영 후 공급가는 0~2,000,000,000원 범위여야 합니다.");
        }
        int supply = (int) supplyPrice;
        int vatAmount = BigDecimal.valueOf(supply)
                .multiply(vatRate)
                .divide(new BigDecimal("100"), 0, RoundingMode.HALF_UP)
                .intValueExact();
        long total = (long) supply + vatAmount;
        if (total > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("부가세 포함 가격이 저장 가능한 범위를 초과합니다.");
        }
        return new PriceCalculation(basePrice, (int) componentPrice, supply, vatRate, vatAmount, (int) total);
    }

    private void applyPrice(ProductMaster product, PriceCalculation price) {
        product.setBaseSupplyPrice(price.baseSupplyPrice());
        product.setComponentSupplyPrice(price.componentSupplyPrice());
        product.setSupplyPrice(price.supplyPrice());
        product.setVatRate(price.vatRate());
        product.setVatAmount(price.vatAmount());
        product.setTotalPrice(price.totalPrice());
    }

    private ProductComponent newComponent(ResolvedComponent item) {
        ProductComponent component = new ProductComponent();
        applyComponent(component, item);
        return component;
    }

    private void applyComponent(ProductComponent component, ResolvedComponent item) {
        component.setGroup(item.group());
        component.setValue(item.value());
        component.setWidthMm(item.widthMm());
        component.setDepthMm(item.depthMm());
        component.setHeightMm(item.heightMm());
        component.setNumericValue(item.numericValue());
        component.setTextValue(item.textValue());
        component.setPriceAdjustmentSnapshot(item.value() == null ? 0 : item.value().getPriceAdjustment());
        component.setSortOrder(item.sortOrder());
    }

    private void mergeComponents(
            ProductMaster product,
            List<ProductComponent> existingComponents,
            List<ResolvedComponent> resolved
    ) {
        Map<String, ProductComponent> existing = existingComponents.stream()
                .collect(Collectors.toMap(
                        component -> componentKey(
                                component.getGroup().getId(),
                                component.getValue() == null ? null : component.getValue().getId()
                        ),
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        List<ProductComponent> additions = new ArrayList<>();
        for (ResolvedComponent item : resolved) {
            String key = componentKey(item.group().getId(), item.value() == null ? null : item.value().getId());
            ProductComponent component = existing.remove(key);
            if (component == null) {
                component = new ProductComponent();
                component.setProduct(product);
                additions.add(component);
            }
            applyComponent(component, item);
        }
        if (!existing.isEmpty()) {
            componentRepository.deleteAll(existing.values());
        }
        if (!additions.isEmpty()) {
            componentRepository.saveAll(additions);
        }
    }

    private void validateRemovableAddons(Long productId, List<ResolvedComponent> resolved) {
        Set<Long> selectedAddonValueIds = resolved.stream()
                .filter(item -> item.group().getGroupType() == ProductAttributeGroupType.ADD_ON)
                .filter(item -> item.value() != null)
                .map(item -> item.value().getId())
                .collect(Collectors.toSet());
        for (ProductAddonBalance balance : addonBalanceRepository.findDetailedByProductId(productId)) {
            Long valueId = balance.getOptionValue().getId();
            if (selectedAddonValueIds.contains(valueId)) {
                continue;
            }
            if (balance.getQuantity() != 0) {
                throw new IllegalStateException(
                        balance.getOptionValue().getManagementLabel()
                                + " 옵션의 포함 재고가 남아 있어 제품 구성에서 제거할 수 없습니다. 먼저 포함 수량을 0으로 조정해 주세요."
                );
            }
            if (movementAddonRepository.existsByMovement_Product_IdAndOptionValue_Id(productId, valueId)) {
                throw new IllegalStateException(
                        balance.getOptionValue().getManagementLabel()
                                + " 옵션은 재고 원장 이력이 있어 제품 구성에서 제거할 수 없습니다. 사용중지 상태로 유지해 주세요."
                );
            }
        }
    }

    private void syncAddonBalances(ProductMaster product, List<ResolvedComponent> resolved) {
        Map<Long, ProductAttributeValue> selectedAddons = resolved.stream()
                .filter(item -> item.group().getGroupType() == ProductAttributeGroupType.ADD_ON)
                .filter(item -> item.value() != null)
                .map(ResolvedComponent::value)
                .collect(Collectors.toMap(ProductAttributeValue::getId, Function.identity()));
        List<ProductAddonBalance> existing = addonBalanceRepository.findDetailedByProductId(product.getId());
        Map<Long, ProductAddonBalance> existingByValue = existing.stream()
                .collect(Collectors.toMap(balance -> balance.getOptionValue().getId(), Function.identity()));

        List<ProductAddonBalance> additions = new ArrayList<>();
        for (ProductAttributeValue value : selectedAddons.values()) {
            if (!existingByValue.containsKey(value.getId())) {
                ProductAddonBalance balance = new ProductAddonBalance();
                balance.setProduct(product);
                balance.setOptionValue(value);
                balance.setQuantity(0);
                additions.add(balance);
            }
        }
        List<ProductAddonBalance> removable = existing.stream()
                .filter(balance -> !selectedAddons.containsKey(balance.getOptionValue().getId()))
                .filter(balance -> balance.getQuantity() == 0)
                .toList();
        if (!removable.isEmpty()) {
            addonBalanceRepository.deleteAll(removable);
        }
        if (!additions.isEmpty()) {
            addonBalanceRepository.saveAll(additions);
        }
        addonBalanceRepository.flush();
    }

    private void recordPriceHistory(ProductMaster product, String reason, String actor) {
        ProductPriceHistory history = new ProductPriceHistory();
        history.setProduct(product);
        history.setPricingMode(product.getPricingMode());
        history.setBaseSupplyPrice(product.getBaseSupplyPrice());
        history.setComponentSupplyPrice(product.getComponentSupplyPrice());
        history.setSupplyPrice(product.getSupplyPrice());
        history.setVatRate(product.getVatRate());
        history.setVatAmount(product.getVatAmount());
        history.setTotalPrice(product.getTotalPrice());
        history.setChangeReason(reason);
        history.setCreatedBy(actor);
        priceHistoryRepository.save(history);
    }

    private ProductMaster requireProduct(Long productId) {
        if (productId == null) {
            throw new IllegalArgumentException("제품 ID가 필요합니다.");
        }
        return productRepository.findById(productId)
                .orElseThrow(() -> new java.util.NoSuchElementException("제품을 찾을 수 없습니다."));
    }

    private List<AttributeFilter> parseAttributeFilters(List<String> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return List.of();
        }
        if (attributes.size() > 30) {
            throw new IllegalArgumentException("옵션 필터는 최대 30개까지 사용할 수 있습니다.");
        }
        LinkedHashSet<AttributeFilter> result = new LinkedHashSet<>();
        for (String raw : attributes) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String[] parts = raw.split(":", -1);
            if (parts.length != 2) {
                throw new IllegalArgumentException("옵션 필터 형식이 올바르지 않습니다.");
            }
            try {
                long groupId = Long.parseLong(parts[0]);
                long valueId = Long.parseLong(parts[1]);
                if (groupId <= 0 || valueId <= 0) {
                    throw new NumberFormatException("non-positive");
                }
                result.add(new AttributeFilter(groupId, valueId));
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("옵션 필터 ID가 올바르지 않습니다.");
            }
        }
        return List.copyOf(result);
    }

    private List<NumberAttributeFilter> parseNumberAttributeFilters(List<String> values) {
        if (values == null || values.isEmpty()) return List.of();
        if (values.size() > 30) throw new IllegalArgumentException("숫자형 옵션 필터는 최대 30개까지 사용할 수 있습니다.");
        LinkedHashMap<Long, NumberAttributeFilter> result = new LinkedHashMap<>();
        for (String raw : values) {
            if (raw == null || raw.isBlank()) continue;
            String[] parts = raw.split(":", -1);
            if (parts.length != 3) throw new IllegalArgumentException("숫자형 옵션 필터 형식이 올바르지 않습니다.");
            long groupId = positiveId(parts[0], "숫자형 옵션 그룹");
            BigDecimal minimum = filterNumber(parts[1], "숫자형 옵션 최소값");
            BigDecimal maximum = filterNumber(parts[2], "숫자형 옵션 최대값");
            if (minimum == null && maximum == null) continue;
            if (minimum != null && maximum != null && minimum.compareTo(maximum) > 0) {
                throw new IllegalArgumentException("숫자형 옵션의 최소값은 최대값보다 클 수 없습니다.");
            }
            ProductAttributeGroup group = groupRepository.findById(groupId)
                    .orElseThrow(() -> new IllegalArgumentException("숫자형 옵션 그룹을 찾을 수 없습니다."));
            if (group.getInputType() != ProductAttributeInputType.NUMBER) {
                throw new IllegalArgumentException(group.getManagementLabel() + " 그룹은 숫자형 필터를 사용할 수 없습니다.");
            }
            result.put(groupId, new NumberAttributeFilter(groupId, minimum, maximum));
        }
        return List.copyOf(result.values());
    }

    private List<TextAttributeFilter> parseTextAttributeFilters(List<String> values) {
        if (values == null || values.isEmpty()) return List.of();
        if (values.size() > 30) throw new IllegalArgumentException("문자형 옵션 필터는 최대 30개까지 사용할 수 있습니다.");
        LinkedHashMap<Long, TextAttributeFilter> result = new LinkedHashMap<>();
        for (String raw : values) {
            if (raw == null || raw.isBlank()) continue;
            int separator = raw.indexOf(':');
            if (separator < 1) throw new IllegalArgumentException("문자형 옵션 필터 형식이 올바르지 않습니다.");
            long groupId = positiveId(raw.substring(0, separator), "문자형 옵션 그룹");
            String keyword = raw.substring(separator + 1).trim();
            if (keyword.isEmpty()) continue;
            if (keyword.length() > 100) throw new IllegalArgumentException("문자형 옵션 검색어는 100자 이하여야 합니다.");
            ProductAttributeGroup group = groupRepository.findById(groupId)
                    .orElseThrow(() -> new IllegalArgumentException("문자형 옵션 그룹을 찾을 수 없습니다."));
            if (group.getInputType() != ProductAttributeInputType.TEXT) {
                throw new IllegalArgumentException(group.getManagementLabel() + " 그룹은 문자형 필터를 사용할 수 없습니다.");
            }
            result.put(groupId, new TextAttributeFilter(groupId, keyword));
        }
        return List.copyOf(result.values());
    }

    private long positiveId(String raw, String field) {
        try {
            long value = Long.parseLong(raw);
            if (value <= 0) throw new NumberFormatException("non-positive");
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(field + " ID가 올바르지 않습니다.");
        }
    }

    private BigDecimal filterNumber(String raw, String field) {
        if (raw == null || raw.isBlank()) return null;
        try {
            BigDecimal value = new BigDecimal(raw).stripTrailingZeros();
            if (value.compareTo(new BigDecimal("-1000000000.000")) < 0
                    || value.compareTo(new BigDecimal("1000000000.000")) > 0
                    || value.scale() > 3) {
                throw new NumberFormatException("range");
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(field + "은 -1,000,000,000~1,000,000,000 범위의 숫자여야 합니다.");
        }
    }

    private Sort parseSort(List<String> sorts) {
        LinkedHashMap<String, Sort.Direction> ordered = new LinkedHashMap<>();
        if (sorts != null) {
            if (sorts.size() > 8) {
                throw new IllegalArgumentException("다중 정렬은 최대 8개 항목까지 사용할 수 있습니다.");
            }
            for (String raw : sorts) {
                if (raw == null || raw.isBlank()) {
                    continue;
                }
                String[] parts = raw.split(",", -1);
                String property = ALLOWED_SORTS.get(parts[0]);
                if (property == null) {
                    throw new IllegalArgumentException("지원하지 않는 정렬 항목입니다: " + parts[0]);
                }
                Sort.Direction direction = parts.length > 1 && "desc".equalsIgnoreCase(parts[1])
                        ? Sort.Direction.DESC
                        : Sort.Direction.ASC;
                ordered.put(property, direction);
            }
        }
        if (ordered.isEmpty()) {
            ordered.put("createdAt", Sort.Direction.DESC);
        }
        List<Sort.Order> orders = ordered.entrySet().stream()
                .map(entry -> new Sort.Order(entry.getValue(), entry.getKey()))
                .collect(Collectors.toCollection(ArrayList::new));
        if (!ordered.containsKey("id")) {
            orders.add(Sort.Order.desc("id"));
        }
        return Sort.by(orders);
    }

    private DimensionFilter validateDimensionFilter(
            Integer widthMin,
            Integer widthMax,
            Integer depthMin,
            Integer depthMax,
            Integer heightMin,
            Integer heightMax
    ) {
        validateDimensionBound(widthMin, "W 최소");
        validateDimensionBound(widthMax, "W 최대");
        validateDimensionBound(depthMin, "D 최소");
        validateDimensionBound(depthMax, "D 최대");
        validateDimensionBound(heightMin, "H 최소");
        validateDimensionBound(heightMax, "H 최대");
        validateDimensionOrder(widthMin, widthMax, "W");
        validateDimensionOrder(depthMin, depthMax, "D");
        validateDimensionOrder(heightMin, heightMax, "H");
        return new DimensionFilter(widthMin, widthMax, depthMin, depthMax, heightMin, heightMax);
    }

    private void validateDimensionBound(Integer value, String fieldName) {
        if (value != null && (value < 1 || value > 100_000)) {
            throw new IllegalArgumentException(fieldName + " 치수는 1~100000mm 범위여야 합니다.");
        }
    }

    private void validateDimensionOrder(Integer minimum, Integer maximum, String axis) {
        if (minimum != null && maximum != null && minimum > maximum) {
            throw new IllegalArgumentException(axis + " 최소 치수는 최대 치수보다 클 수 없습니다.");
        }
    }

    private int nonNegative(Integer value, String fieldName) {
        if (value == null || value < 0) {
            throw new IllegalArgumentException(fieldName + "은(는) 0 이상이어야 합니다.");
        }
        return value;
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

    private String optionalText(String value, String fieldName, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + "은(는) " + maxLength + "자 이하여야 합니다.");
        }
        return normalized;
    }

    private String normalizeActor(String actor) {
        String normalized = actor == null || actor.isBlank() ? "SYSTEM" : actor.trim();
        return normalized.length() <= 100 ? normalized : normalized.substring(0, 100);
    }

    private String componentKey(Long groupId, Long valueId) {
        return groupId + ":" + (valueId == null ? "INPUT" : valueId);
    }

    private String normalizeComponentText(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() > 500) throw new IllegalArgumentException("직접 입력 내용은 500자 이하여야 합니다.");
        return normalized;
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

    private record ResolvedComponent(
            ProductAttributeGroup group,
            ProductAttributeValue value,
            Integer widthMm,
            Integer depthMm,
            Integer heightMm,
            BigDecimal numericValue,
            String textValue,
            int sortOrder
    ) {
    }

    private record ProductIdentity(String productCode, String hash) {
    }

    private record PriceCalculation(
            int baseSupplyPrice,
            int componentSupplyPrice,
            int supplyPrice,
            BigDecimal vatRate,
            int vatAmount,
            int totalPrice
    ) {
    }

    private record PriceSnapshot(
            ProductPricingMode pricingMode,
            int baseSupplyPrice,
            int componentSupplyPrice,
            int supplyPrice,
            BigDecimal vatRate,
            int vatAmount,
            int totalPrice
    ) {
        static PriceSnapshot from(ProductMaster product) {
            return new PriceSnapshot(
                    product.getPricingMode(),
                    product.getBaseSupplyPrice(),
                    product.getComponentSupplyPrice(),
                    product.getSupplyPrice(),
                    product.getVatRate(),
                    product.getVatAmount(),
                    product.getTotalPrice()
            );
        }
    }
}
