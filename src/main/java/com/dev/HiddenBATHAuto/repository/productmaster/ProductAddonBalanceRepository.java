package com.dev.HiddenBATHAuto.repository.productmaster;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dev.HiddenBATHAuto.model.productmaster.ProductAddonBalance;

public interface ProductAddonBalanceRepository extends JpaRepository<ProductAddonBalance, Long> {

    Optional<ProductAddonBalance> findByProductIdAndOptionValueId(Long productId, Long optionValueId);

    @Query("""
            select b from ProductAddonBalance b
            join fetch b.optionValue v
            join fetch v.group g
            where b.product.id in :productIds
            order by b.product.id, g.sortOrder, v.sortOrder
            """)
    List<ProductAddonBalance> findDetailedByProductIds(@Param("productIds") Collection<Long> productIds);

    @Query("""
            select b from ProductAddonBalance b
            join fetch b.optionValue v
            join fetch v.group g
            where b.product.id = :productId
            order by g.sortOrder, v.sortOrder
            """)
    List<ProductAddonBalance> findDetailedByProductId(@Param("productId") Long productId);
}
