package com.dev.HiddenBATHAuto.repository.productmaster;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dev.HiddenBATHAuto.model.productmaster.ProductConfigurationRule;

public interface ProductConfigurationRuleRepository extends JpaRepository<ProductConfigurationRule, Long> {

    boolean existsByRuleCode(String ruleCode);

    List<ProductConfigurationRule> findAllByOrderByPriorityAscIdAsc();

    @Query("""
            select distinct r from ProductConfigurationRule r
            where r.active = true and (r.scopeProduct is null or r.scopeProduct.id = :productId)
            order by r.priority asc, r.id asc
            """)
    List<ProductConfigurationRule> findActiveForProduct(@Param("productId") Long productId);
}
