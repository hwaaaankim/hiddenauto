package com.dev.HiddenBATHAuto.repository.productmaster;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dev.HiddenBATHAuto.model.productmaster.ProductPriceMatrix;

public interface ProductPriceMatrixRepository extends JpaRepository<ProductPriceMatrix, Long> {

    boolean existsByMatrixCode(String matrixCode);

    @Query("""
            select count(m.id)
            from ProductPriceMatrix m
            where m.xGroup.id = :groupId or m.yGroup.id = :groupId
            """)
    long countReferencesToGroup(@Param("groupId") Long groupId);

    @EntityGraph(attributePaths = {"xGroup", "yGroup", "cells"})
    List<ProductPriceMatrix> findAllByOrderByIdDesc();

    @EntityGraph(attributePaths = {"xGroup", "yGroup", "cells"})
    @Query("select m from ProductPriceMatrix m where m.id = :id")
    Optional<ProductPriceMatrix> findDetailedById(@Param("id") Long id);
}
