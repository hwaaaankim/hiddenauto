package com.dev.HiddenBATHAuto.repository.productmaster;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dev.HiddenBATHAuto.model.productmaster.ProductComponent;

public interface ProductComponentRepository extends JpaRepository<ProductComponent, Long> {

    boolean existsByGroupId(Long groupId);

    boolean existsByValueId(Long valueId);

    @Query("""
            select c from ProductComponent c
            join fetch c.group g
            left join fetch c.value v
            where c.product.id in :productIds
            order by c.product.id, c.sortOrder, c.id
            """)
    List<ProductComponent> findDetailedByProductIds(@Param("productIds") Collection<Long> productIds);

    @Query("""
            select c from ProductComponent c
            join fetch c.group g
            left join fetch c.value v
            where c.product.id = :productId
            order by c.sortOrder, c.id
            """)
    List<ProductComponent> findDetailedByProductId(@Param("productId") Long productId);
}
