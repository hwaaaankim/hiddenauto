package com.dev.HiddenBATHAuto.service.productmaster;

import static com.dev.HiddenBATHAuto.dto.productmaster.ProductMasterDtos.*;

import com.dev.HiddenBATHAuto.enums.productmaster.ProductStockMovementType;
import com.dev.HiddenBATHAuto.model.productmaster.*;
import com.dev.HiddenBATHAuto.repository.productmaster.*;
import java.util.NoSuchElementException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductInventoryService {
  private static final int MAX_STOCK = 10_000_000;
  private final ProductMasterRepository products;
  private final ProductStockMovementRepository movements;

  public StockView detail(Long id) {
    ProductMaster p =
        products.findById(id).orElseThrow(() -> new NoSuchElementException("제품을 찾을 수 없습니다."));
    return new StockView(
        id,
        p.getCurrentStock(),
        movements.findDetailedByProductId(id).stream()
            .map(
                m ->
                    new StockMovementView(
                        m.getId(),
                        m.getMovementType().getLabelKr(),
                        m.getQuantityDelta(),
                        m.getStockBefore(),
                        m.getStockAfter(),
                        m.getReason(),
                        m.getCreatedBy(),
                        m.getCreatedAt(),
                        m.isVoided(),
                        m.getVoidReason()))
            .toList());
  }

  @Transactional
  public StockView adjust(Long id, StockAdjustmentRequest r, String actor) {
    if (r == null || r.movementType() == null || r.quantityDelta() == null)
      throw new IllegalArgumentException("재고 변경 유형과 수량이 필요합니다.");
    if (r.movementType() == ProductStockMovementType.INITIAL)
      throw new IllegalArgumentException("최초재고는 제품 등록 시에만 사용할 수 있습니다.");
    return apply(id, r.movementType(), r.quantityDelta(), r.reason(), actor);
  }

  @Transactional
  public StockView recordInitialStock(Long id, int quantity, String reason, String actor) {
    ProductMaster p = lock(id);
    if (p.getCurrentStock() != 0 || movements.existsByProductIdAndVoidedFalse(id))
      throw new IllegalStateException("최초재고가 이미 등록되어 있습니다.");
    return apply(id, ProductStockMovementType.INITIAL, quantity, reason, actor);
  }

  private StockView apply(
      Long id, ProductStockMovementType type, int delta, String reason, String actor) {
    ProductMaster p = lock(id);
    if (Math.abs((long) delta) > MAX_STOCK)
      throw new IllegalArgumentException("변경 수량의 허용 범위를 초과했습니다.");
    switch (type) {
      case INITIAL, INBOUND, RETURN -> {
        if (delta <= 0) throw new IllegalArgumentException("입고 수량은 양수로 입력해 주세요.");
      }
      case OUTBOUND, DAMAGE -> {
        if (delta >= 0) throw new IllegalArgumentException("출고·폐기 수량은 음수로 입력해 주세요.");
      }
      case ADJUSTMENT -> {
        if (delta == 0) throw new IllegalArgumentException("변경 수량을 입력해 주세요.");
      }
    }
    int next = range((long) p.getCurrentStock() + delta);
    ProductStockMovement m = new ProductStockMovement();
    m.setProduct(p);
    m.setMovementType(type);
    m.setQuantityDelta(delta);
    m.setStockBefore(p.getCurrentStock());
    m.setStockAfter(next);
    m.setReason(reason(reason));
    m.setCreatedBy(actor);
    p.setCurrentStock(next);
    p.setUpdatedBy(actor);
    movements.saveAndFlush(m);
    products.flush();
    return detail(id);
  }

  @Transactional
  public StockView voidMovement(Long id, Long movementId, String reason, String actor) {
    ProductMaster p = lock(id);
    ProductStockMovement m =
        movements
            .findDetailedByIdAndProductId(movementId, id)
            .orElseThrow(() -> new NoSuchElementException("재고 이력을 찾을 수 없습니다."));
    if (m.isVoided()) throw new IllegalStateException("이미 취소된 이력입니다.");
    p.setCurrentStock(range((long) p.getCurrentStock() - m.getQuantityDelta()));
    p.setUpdatedBy(actor);
    m.voidMovement(actor, reason(reason));
    movements.flush();
    products.flush();
    return detail(id);
  }

  private ProductMaster lock(Long id) {
    return products
        .findForUpdate(id)
        .orElseThrow(() -> new NoSuchElementException("제품을 찾을 수 없습니다."));
  }

  private int range(long value) {
    if (value < 0 || value > MAX_STOCK)
      throw new IllegalStateException("변경 후 재고는 0~10,000,000이어야 합니다.");
    return (int) value;
  }

  private String reason(String s) {
    if (s == null || s.isBlank() || s.trim().length() > 500)
      throw new IllegalArgumentException("변경 사유는 1~500자로 입력해 주세요.");
    return s.trim();
  }
}
