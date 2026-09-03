package com.dev.HiddenBATHAuto.repository.productmaster;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dev.HiddenBATHAuto.model.productmaster.ProductDynamicPriceRule;

public interface ProductDynamicPriceRuleRepository extends JpaRepository<ProductDynamicPriceRule, Long> {

    boolean existsByPriceRuleCode(String priceRuleCode);
    boolean existsByTriggerValueId(Long valueId);
    boolean existsByQuantityGroupIdOrSourceGroupId(Long quantityGroupId, Long sourceGroupId);
    boolean existsByMatrixId(Long matrixId);

    @EntityGraph(attributePaths = {
            "scopeProduct", "triggerValue", "triggerValue.group", "quantityGroup",
            "sourceGroup", "matrix", "matrix.xGroup", "matrix.yGroup", "matrix.cells"
    })
    List<ProductDynamicPriceRule> findAllByOrderByPriorityAscIdAsc();

    @EntityGraph(attributePaths = {
            "scopeProduct", "triggerValue", "triggerValue.group", "quantityGroup",
            "sourceGroup", "matrix", "matrix.xGroup", "matrix.yGroup", "matrix.cells"
    })
    @Query("""
            select distinct r from ProductDynamicPriceRule r
            where r.active = true and (r.scopeProduct is null or r.scopeProduct.id = :productId)
            order by r.priority asc, r.id asc
            """)
    List<ProductDynamicPriceRule> findActiveForProduct(@Param("productId") Long productId);
}
