package com.dev.HiddenBATHAuto.repository.productmaster;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dev.HiddenBATHAuto.model.productmaster.ProductRuleCondition;

public interface ProductRuleConditionRepository extends JpaRepository<ProductRuleCondition, Long> {
    boolean existsBySourceGroupId(Long groupId);
    boolean existsBySourceValueId(Long valueId);
}
