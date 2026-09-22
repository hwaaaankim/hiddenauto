package com.dev.HiddenBATHAuto.repository.productmaster;

import com.dev.HiddenBATHAuto.model.productmaster.ProductActual;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ProductActualRepository extends JpaRepository<ProductActual, Long> {

  @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_READ)
  @Query("select a from ProductActual a join a.product.components c where c.group.id=:id")
  List<ProductActual> lockForGroup(@Param("id") Long id);

  List<ProductActual> findByProductIdOrderByIdDesc(Long productId);

  long countByProductId(Long productId);

  boolean existsByProductId(Long productId);

  boolean existsByProductIdAndSpecHash(Long productId, String specHash);

  Optional<ProductActual> findByCode(String code);

  @Query("select coalesce(sum(a.currentStock),0) from ProductActual a where a.product.id=:id")
  long totalStock(@Param("id") Long id);
}
