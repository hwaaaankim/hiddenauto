package com.dev.HiddenBATHAuto.repository.productmaster;

import com.dev.HiddenBATHAuto.model.productmaster.ProductStockMovement;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductStockMovementRepository extends JpaRepository<ProductStockMovement, Long> {
  boolean existsByProductIdAndVoidedFalse(Long productId);

  @Query(
      "select m from ProductStockMovement m where m.product.id=:id order by m.createdAt desc,m.id"
          + " desc")
  List<ProductStockMovement> findDetailedByProductId(@Param("id") Long id);

  @Query("select m from ProductStockMovement m where m.id=:movementId and m.product.id=:productId")
  Optional<ProductStockMovement> findDetailedByIdAndProductId(
      @Param("movementId") Long movementId, @Param("productId") Long productId);
}
