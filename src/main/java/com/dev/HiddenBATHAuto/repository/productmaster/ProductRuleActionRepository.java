package com.dev.HiddenBATHAuto.repository.productmaster;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dev.HiddenBATHAuto.model.productmaster.ProductRuleAction;

public interface ProductRuleActionRepository extends JpaRepository<ProductRuleAction, Long> {
    boolean existsByTargetGroupId(Long groupId);
    boolean existsByTargetValueId(Long valueId);
}
