package com.dev.HiddenBATHAuto.service.productmaster;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dev.HiddenBATHAuto.dto.productmaster.ProductMasterDtos.AddonQuantityRequest;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductMasterDtos.ProductDetailResponse;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductMasterDtos.StockAdjustmentRequest;
import com.dev.HiddenBATHAuto.enums.productmaster.ProductStockMovementType;
import com.dev.HiddenBATHAuto.model.productmaster.ProductAddonBalance;
import com.dev.HiddenBATHAuto.model.productmaster.ProductMaster;
import com.dev.HiddenBATHAuto.model.productmaster.ProductStockMovement;
import com.dev.HiddenBATHAuto.model.productmaster.ProductStockMovementAddon;
import com.dev.HiddenBATHAuto.repository.productmaster.ProductAddonBalanceRepository;
import com.dev.HiddenBATHAuto.repository.productmaster.ProductMasterRepository;
import com.dev.HiddenBATHAuto.repository.productmaster.ProductStockMovementRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProductInventoryService {

    private static final int MAX_STOCK = 10_000_000;

    private final ProductMasterRepository productRepository;
    private final ProductAddonBalanceRepository addonBalanceRepository;
    private final ProductStockMovementRepository movementRepository;
    private final ProductMasterResponseMapper responseMapper;

    @Transactional
    public ProductDetailResponse adjust(
            Long productId,
            StockAdjustmentRequest request,
            String actor
    ) {
        if (request == null) {
            throw new IllegalArgumentException("재고 변경 내용이 필요합니다.");
        }
        ProductStockMovementType type = Objects.requireNonNull(request.movementType(), "재고 처리 유형이 필요합니다.");
        if (type == ProductStockMovementType.INITIAL) {
            throw new IllegalArgumentException("최초재고 유형은 제품 등록 시에만 사용할 수 있습니다.");
        }
        return applyMovement(
                productId,
                type,
                request.quantityDelta() == null ? 0 : request.quantityDelta(),
                request.reason(),
                request.addonQuantities(),
                normalizeActor(actor),
                false
        );
    }

    @Transactional
    public ProductDetailResponse recordInitialStock(
            Long productId,
            int initialStock,
            String reason,
            List<AddonQuantityRequest> addonQuantities,
            String actor
    ) {
        if (initialStock < 0) {
            throw new IllegalArgumentException("최초재고는 0 이상이어야 합니다.");
        }
        return applyMovement(
                productId,
                ProductStockMovementType.INITIAL,
                initialStock,
                reason == null || reason.isBlank() ? "제품 등록 최초재고" : reason,
                addonQuantities,
                normalizeActor(actor),
                true
        );
    }

    @Transactional
    public ProductDetailResponse voidMovement(
            Long productId,
            Long movementId,
            String reason,
            String actor
    ) {
        String normalizedReason = requiredText(reason, "삭제 사유", 500);
        String normalizedActor = normalizeActor(actor);
        ProductMaster product = lockProduct(productId);
        ProductStockMovement movement = movementRepository.findDetailedByIdAndProductId(movementId, productId)
                .orElseThrow(() -> new java.util.NoSuchElementException("재고 이력을 찾을 수 없습니다."));
        if (movement.isVoided()) {
            throw new IllegalStateException("이미 삭제 처리된 재고 이력입니다.");
        }

        long reversedStock = (long) product.getCurrentStock() - movement.getQuantityDelta();
        validateStockRange(reversedStock);

        Map<Long, ProductAddonBalance> balances = addonBalanceRepository.findDetailedByProductId(productId).stream()
                .collect(Collectors.toMap(balance -> balance.getOptionValue().getId(), Function.identity()));
        Map<Long, Integer> reversedAddonValues = new HashMap<>();
        for (ProductStockMovementAddon line : movement.getAddonLines()) {
            ProductAddonBalance balance = balances.get(line.getOptionValue().getId());
            if (balance == null) {
                throw new IllegalStateException("재고 이력의 추가 옵션 잔액 정보를 찾을 수 없습니다.");
            }
            long reversed = (long) balance.getQuantity() - line.getQuantityDelta();
            if (reversed < 0 || reversed > reversedStock) {
                throw new IllegalStateException(
                        line.getOptionValue().getManagementLabel()
                                + " 포함 수량 때문에 이 재고 이력을 삭제할 수 없습니다. 이후 재고 이력을 먼저 확인해 주세요."
                );
            }
            reversedAddonValues.put(line.getOptionValue().getId(), (int) reversed);
        }

        for (ProductAddonBalance balance : balances.values()) {
            int finalQuantity = reversedAddonValues.getOrDefault(
                    balance.getOptionValue().getId(),
                    balance.getQuantity()
            );
            if (finalQuantity > reversedStock) {
                throw new IllegalStateException(
                        balance.getOptionValue().getManagementLabel()
                                + " 포함 수량이 변경 후 총재고보다 많아 이력을 삭제할 수 없습니다."
                );
            }
        }

        reversedAddonValues.forEach((valueId, quantity) -> balances.get(valueId).setQuantity(quantity));
        product.setCurrentStock((int) reversedStock);
        product.setUpdatedBy(normalizedActor);
        movement.voidMovement(normalizedActor, normalizedReason);
        addonBalanceRepository.flush();
        productRepository.flush();
        movementRepository.flush();
        return responseMapper.toDetail(product);
    }

    private ProductDetailResponse applyMovement(
            Long productId,
            ProductStockMovementType type,
            int quantityDelta,
            String reason,
            List<AddonQuantityRequest> addonRequests,
            String actor,
            boolean initial
    ) {
        ProductMaster product = lockProduct(productId);
        if (initial && product.getCurrentStock() != 0) {
            throw new IllegalStateException("이미 재고가 존재하는 제품에는 최초재고를 다시 등록할 수 없습니다.");
        }

        Map<Long, Integer> addonDeltas = normalizeAddonDeltas(addonRequests);
        boolean hasAddonChange = addonDeltas.values().stream().anyMatch(value -> value != 0);
        validateMovementDirection(type, quantityDelta, hasAddonChange);
        validateAddonDirection(type, addonDeltas.values());
        String normalizedReason = requiredText(reason, "재고 변경 사유", 500);

        long nextStock = (long) product.getCurrentStock() + quantityDelta;
        validateStockRange(nextStock);

        Map<Long, ProductAddonBalance> balances = addonBalanceRepository.findDetailedByProductId(productId).stream()
                .collect(Collectors.toMap(balance -> balance.getOptionValue().getId(), Function.identity()));
        if (!balances.keySet().containsAll(addonDeltas.keySet())) {
            throw new IllegalArgumentException("제품 구성에 등록되지 않은 추가 옵션의 수량 변경이 포함되어 있습니다.");
        }

        Map<Long, Integer> nextAddonBalances = new HashMap<>();
        for (ProductAddonBalance balance : balances.values()) {
            int delta = addonDeltas.getOrDefault(balance.getOptionValue().getId(), 0);
            long next = (long) balance.getQuantity() + delta;
            if (next < 0) {
                throw new IllegalStateException(
                        balance.getOptionValue().getManagementLabel() + " 포함 수량은 0보다 작아질 수 없습니다."
                );
            }
            if (next > nextStock) {
                throw new IllegalStateException(
                        balance.getOptionValue().getManagementLabel()
                                + " 포함 수량은 변경 후 제품 총재고(" + nextStock + ")보다 많을 수 없습니다."
                );
            }
            nextAddonBalances.put(balance.getOptionValue().getId(), (int) next);
        }

        ProductStockMovement movement = new ProductStockMovement();
        movement.setProduct(product);
        movement.setMovementType(type);
        movement.setQuantityDelta(quantityDelta);
        movement.setStockBefore(product.getCurrentStock());
        movement.setStockAfter((int) nextStock);
        movement.setReason(normalizedReason);
        movement.setCreatedBy(actor);

        for (Map.Entry<Long, Integer> entry : addonDeltas.entrySet()) {
            if (entry.getValue() == 0) {
                continue;
            }
            ProductAddonBalance balance = balances.get(entry.getKey());
            ProductStockMovementAddon line = new ProductStockMovementAddon();
            line.setOptionValue(balance.getOptionValue());
            line.setQuantityDelta(entry.getValue());
            line.setBalanceBefore(balance.getQuantity());
            line.setBalanceAfter(nextAddonBalances.get(entry.getKey()));
            movement.addAddonLine(line);
        }

        for (ProductAddonBalance balance : balances.values()) {
            balance.setQuantity(nextAddonBalances.get(balance.getOptionValue().getId()));
        }
        product.setCurrentStock((int) nextStock);
        product.setUpdatedBy(actor);
        movementRepository.save(movement);
        addonBalanceRepository.flush();
        productRepository.flush();
        movementRepository.flush();
        return responseMapper.toDetail(product);
    }

    private Map<Long, Integer> normalizeAddonDeltas(List<AddonQuantityRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return Map.of();
        }
        if (requests.size() > 100) {
            throw new IllegalArgumentException("추가 옵션 수량 변경은 최대 100개까지 등록할 수 있습니다.");
        }
        Set<Long> duplicateGuard = new HashSet<>();
        Map<Long, Integer> result = new HashMap<>();
        for (AddonQuantityRequest request : requests) {
            if (request == null || request.valueId() == null || request.quantityDelta() == null) {
                throw new IllegalArgumentException("추가 옵션 ID와 변경 수량이 필요합니다.");
            }
            if (!duplicateGuard.add(request.valueId())) {
                throw new IllegalArgumentException("동일한 추가 옵션의 수량 변경이 중복되어 있습니다.");
            }
            if (Math.abs((long) request.quantityDelta()) > MAX_STOCK) {
                throw new IllegalArgumentException("추가 옵션 변경 수량이 허용 범위를 초과합니다.");
            }
            result.put(request.valueId(), request.quantityDelta());
        }
        return result;
    }

    private void validateMovementDirection(
            ProductStockMovementType type,
            int quantityDelta,
            boolean hasAddonChange
    ) {
        if (Math.abs((long) quantityDelta) > MAX_STOCK) {
            throw new IllegalArgumentException("재고 변경 수량이 허용 범위를 초과합니다.");
        }
        switch (type) {
            case INITIAL, INBOUND, RETURN -> {
                if (quantityDelta <= 0) {
                    throw new IllegalArgumentException(type.getLabelKr() + " 수량은 0보다 커야 합니다.");
                }
            }
            case OUTBOUND, DAMAGE -> {
                if (quantityDelta >= 0) {
                    throw new IllegalArgumentException(type.getLabelKr() + " 수량은 음수여야 합니다.");
                }
            }
            case ADJUSTMENT -> {
                if (quantityDelta == 0 && !hasAddonChange) {
                    throw new IllegalArgumentException("재고조정에는 총재고 또는 추가 옵션 포함 수량의 변경이 필요합니다.");
                }
            }
        }
    }

    private void validateAddonDirection(ProductStockMovementType type, java.util.Collection<Integer> deltas) {
        boolean hasNegative = deltas.stream().anyMatch(delta -> delta < 0);
        boolean hasPositive = deltas.stream().anyMatch(delta -> delta > 0);
        switch (type) {
            case INITIAL, INBOUND, RETURN -> {
                if (hasNegative) {
                    throw new IllegalArgumentException(type.getLabelKr() + "의 추가 옵션 포함 수량은 음수로 변경할 수 없습니다.");
                }
            }
            case OUTBOUND, DAMAGE -> {
                if (hasPositive) {
                    throw new IllegalArgumentException(type.getLabelKr() + "의 추가 옵션 포함 수량은 양수로 변경할 수 없습니다.");
                }
            }
            case ADJUSTMENT -> {
                // 실사 및 옵션 재분류를 위해 양수·음수 조정을 모두 허용합니다.
            }
        }
    }

    private ProductMaster lockProduct(Long productId) {
        if (productId == null) {
            throw new IllegalArgumentException("제품 ID가 필요합니다.");
        }
        return productRepository.findForUpdate(productId)
                .orElseThrow(() -> new java.util.NoSuchElementException("제품을 찾을 수 없습니다."));
    }

    private void validateStockRange(long stock) {
        if (stock < 0) {
            throw new IllegalStateException("재고는 0보다 작아질 수 없습니다.");
        }
        if (stock > MAX_STOCK) {
            throw new IllegalStateException("재고는 " + MAX_STOCK + "개를 초과할 수 없습니다.");
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

    private String normalizeActor(String actor) {
        String normalized = actor == null || actor.isBlank() ? "SYSTEM" : actor.trim();
        return normalized.length() <= 100 ? normalized : normalized.substring(0, 100);
    }
}
