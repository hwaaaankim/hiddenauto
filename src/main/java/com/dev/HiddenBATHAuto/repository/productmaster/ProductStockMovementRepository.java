package com.dev.HiddenBATHAuto.repository.productmaster;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dev.HiddenBATHAuto.model.productmaster.ProductStockMovement;

public interface ProductStockMovementRepository extends JpaRepository<ProductStockMovement, Long> {

    boolean existsByProductIdAndVoidedFalse(Long productId);

    @Query("""
            select distinct m from ProductStockMovement m
            left join fetch m.addonLines l
            left join fetch l.optionValue v
            left join fetch v.group g
            where m.product.id = :productId
            order by m.createdAt desc, m.id desc
            """)
    List<ProductStockMovement> findDetailedByProductId(@Param("productId") Long productId);

    @Query("""
            select distinct m from ProductStockMovement m
            left join fetch m.addonLines l
            left join fetch l.optionValue v
            left join fetch v.group g
            where m.id = :movementId and m.product.id = :productId
            """)
    Optional<ProductStockMovement> findDetailedByIdAndProductId(
            @Param("movementId") Long movementId,
            @Param("productId") Long productId
    );
}
