package com.dev.HiddenBATHAuto.service.productmaster;

import static com.dev.HiddenBATHAuto.dto.productmaster.ProductStudioDeletionDtos.*;

import com.dev.HiddenBATHAuto.model.productmaster.ProductActual;
import com.dev.HiddenBATHAuto.model.productmaster.ProductMaster;
import com.dev.HiddenBATHAuto.repository.productmaster.ProductActualRepository;
import com.dev.HiddenBATHAuto.repository.productmaster.ProductMasterRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductStudioDeletionService {
  public static final int MAX_DELETE = 10_000;
  private static final int DELETE_BATCH_SIZE = 200;
  private static final Pattern ASSET_ID =
      Pattern.compile("\"([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})\"");

  private final ProductMasterRepository products;
  private final ProductActualRepository actuals;
  private final ProductStudioAssetService assets;
  private final EntityManager entityManager;

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public DeleteResult deleteProducts(ProductDeleteRequest request, String actor) {
    if (request == null || request.nonStandard() == null)
      throw new IllegalArgumentException("삭제할 제품 유형을 확인해 주세요.");
    validateSize(request.items());
    Map<Long, ProductDeleteTarget> selected = new TreeMap<>();
    for (ProductDeleteTarget target : request.items()) {
      if (target == null) throw new IllegalArgumentException("삭제할 제품 정보가 없습니다.");
      validateTarget(target.id(), target.version());
      if (target.actualCount() == null || target.actualCount() < 0)
        throw new IllegalArgumentException("실 제품 종류 수를 확인한 뒤 다시 삭제해 주세요.");
      if (selected.put(target.id(), target) != null)
        throw new IllegalArgumentException("삭제할 제품이 중복 선택되었습니다.");
    }

    // Lock in a stable order, and validate every target before deleting any row.
    List<ProductMaster> locked = new ArrayList<>();
    for (ProductDeleteTarget target : selected.values()) {
      ProductMaster product = lockProduct(target.id());
      validateVersion(target.version(), product.getRowVersion());
      if (product.isNonStandard() != request.nonStandard())
        throw new IllegalArgumentException("규격·비규격 목록에 맞는 제품만 삭제할 수 있습니다.");
      if (actuals.countByProductId(product.getId()) != target.actualCount())
        throw new IllegalStateException(
            "소속 실 제품 목록이 변경되었습니다. 목록을 다시 조회하고 삭제 범위를 확인해 주세요.");
      locked.add(product);
    }
    DeleteResult result = deleteLockedProducts(locked);
    log.info("제품 선택 삭제: actor={}, products={}, actuals={}, stock={}",
        actor, result.deletedProducts(), result.deletedActuals(), result.deletedStock());
    return result;
  }

  /** Retains the existing single-product DELETE endpoint with permanent-deletion semantics. */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public void deleteProduct(Long id) {
    if (id == null || id <= 0) throw new IllegalArgumentException("제품 ID를 확인해 주세요.");
    deleteLockedProducts(List.of(lockProduct(id)));
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public DeleteResult deleteActuals(Long productId, ActualDeleteRequest request, String actor) {
    if (request == null || request.productVersion() == null || request.productVersion() < 0)
      throw new IllegalArgumentException("원제품 정보를 다시 조회해 주세요.");
    validateSize(request.items());
    Map<Long, ActualDeleteTarget> selected = new TreeMap<>();
    for (ActualDeleteTarget target : request.items()) {
      if (target == null) throw new IllegalArgumentException("삭제할 실 제품 정보가 없습니다.");
      validateTarget(target.id(), target.version());
      if (selected.put(target.id(), target) != null)
        throw new IllegalArgumentException("삭제할 실 제품이 중복 선택되었습니다.");
    }
    ProductMaster product = lockProduct(productId);
    if (!product.isNonStandard())
      throw new IllegalArgumentException("비규격 제품에 소속된 실 제품만 삭제할 수 있습니다.");
    validateVersion(request.productVersion(), product.getRowVersion());
    List<ProductActual> locked = new ArrayList<>();
    for (ActualDeleteTarget target : selected.values()) {
      ProductActual actual = actuals.findById(target.id())
          .orElseThrow(() -> new NoSuchElementException("실 제품을 찾을 수 없습니다. 목록을 다시 조회해 주세요."));
      if (!product.getId().equals(actual.getProduct().getId()))
        throw new IllegalArgumentException("다른 원제품의 실 제품은 삭제할 수 없습니다.");
      validateVersion(target.version(), actual.getRowVersion());
      locked.add(actual);
    }

    long deletedStock = locked.stream().mapToLong(ProductActual::getCurrentStock).sum();
    Set<String> inputIds = new TreeSet<>();
    for (ProductActual actual : locked) collectInputIds(inputIds, actual.getAnswersJson());
    removeActuals(locked);
    // Recalculate from remaining specifications, keeping legacy unallocated stock intact.
    long remainingStock = actuals.totalStock(product.getId()) + product.getLegacyUnallocatedStock();
    if (remainingStock < 0 || remainingStock > 10_000_000)
      throw new IllegalStateException("남은 실 제품과 미배정 재고의 합계를 확인해 주세요.");
    product.setCurrentStock((int) remainingStock);
    product.setUpdatedBy(actor);
    product.setUpdatedAt(LocalDateTime.now());
    products.flush();
    assets.deleteUnusedInputs(inputIds);
    log.info("실 제품 선택 삭제: actor={}, productId={}, actuals={}, stock={}",
        actor, productId, locked.size(), deletedStock);
    return new DeleteResult(0, locked.size(), deletedStock);
  }

  private DeleteResult deleteLockedProducts(List<ProductMaster> locked) {
    int deletedActuals = 0;
    long deletedStock = 0;
    Set<String> inputIds = new TreeSet<>();
    for (ProductMaster product : locked) {
      deletedStock += product.getCurrentStock();
      collectInputIds(inputIds, product.getStudioDefinitionJson());
      collectInputIds(inputIds, product.getStudioProcessJson());
      for (Object[] audit : entityManager.createQuery(
              "select a.beforeJson, a.afterJson from ProductStudioAudit a where a.product.id = :id",
              Object[].class).setParameter("id", product.getId()).getResultList()) {
        collectInputIds(inputIds, (String) audit[0]);
        collectInputIds(inputIds, (String) audit[1]);
      }
      List<ProductActual> children = actuals.findByProductIdOrderByIdDesc(product.getId());
      deletedActuals += children.size();
      for (ProductActual actual : children) collectInputIds(inputIds, actual.getAnswersJson());
      removeActuals(children);
      entityManager.createQuery("delete from ProductStockMovement m where m.product.id = :id")
          .setParameter("id", product.getId()).executeUpdate();
      entityManager.createQuery("delete from ProductStudioAudit a where a.product.id = :id")
          .setParameter("id", product.getId()).executeUpdate();
      assets.deleteOwned("PRODUCT", product.getId());
      assets.deleteOwned("PROCESS", product.getId());
      // Only product components and input indexes cascade; shared groups/options never cascade.
      products.delete(product);
    }
    products.flush();
    assets.deleteUnusedInputs(inputIds);
    return new DeleteResult(locked.size(), deletedActuals, deletedStock);
  }

  private void removeActuals(List<ProductActual> selected) {
    // A parent or an actual-product list can contain many specifications.
    for (int start = 0; start < selected.size(); start += DELETE_BATCH_SIZE) {
      List<ProductActual> batch = selected.subList(start, Math.min(selected.size(), start + DELETE_BATCH_SIZE));
      List<Long> ids = batch.stream().map(ProductActual::getId).toList();
      entityManager.createQuery("delete from ProductActualMovement m where m.actual.id in :ids")
          .setParameter("ids", ids).executeUpdate();
      for (ProductActual actual : batch) assets.deleteOwned("ACTUAL", actual.getId());
      actuals.deleteAll(batch);
      actuals.flush();
    }
  }

  private ProductMaster lockProduct(Long id) {
    if (id == null || id <= 0) throw new IllegalArgumentException("제품 ID를 확인해 주세요.");
    return products.findForUpdate(id)
        .orElseThrow(() -> new NoSuchElementException("제품을 찾을 수 없습니다. 목록을 다시 조회해 주세요."));
  }

  private static void collectInputIds(Set<String> ids, String document) {
    if (document == null) return;
    var matcher = ASSET_ID.matcher(document);
    while (matcher.find()) ids.add(matcher.group(1));
  }

  private static void validateSize(Collection<?> selected) {
    if (selected == null || selected.isEmpty() || selected.size() > MAX_DELETE)
      throw new IllegalArgumentException("삭제할 항목을 1~10,000개 선택해 주세요.");
  }

  private static void validateTarget(Long id, Long version) {
    if (id == null || id <= 0 || version == null || version < 0)
      throw new IllegalArgumentException("삭제할 항목 정보를 다시 조회해 주세요.");
  }

  private static void validateVersion(Long expected, long current) {
    if (expected == null || expected != current)
      throw new IllegalStateException("제품 또는 재고가 변경되었습니다. 목록을 다시 조회하고 삭제 범위를 확인해 주세요.");
  }
}
