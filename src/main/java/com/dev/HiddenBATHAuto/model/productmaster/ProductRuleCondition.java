package com.dev.HiddenBATHAuto.model.productmaster;

import java.math.BigDecimal;

import com.dev.HiddenBATHAuto.enums.productmaster.ProductRuleOperator;
import com.dev.HiddenBATHAuto.enums.productmaster.ProductRuleSourceField;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "tb_pm_rule_condition",
        indexes = {
                @Index(name = "idx_pm_rule_condition_rule", columnList = "rule_id,sort_order,id"),
                @Index(name = "idx_pm_rule_condition_source", columnList = "source_group_id,source_value_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class ProductRuleCondition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rule_id", nullable = false)
    private ProductConfigurationRule rule;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_group_id", nullable = false)
    private ProductAttributeGroup sourceGroup;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_value_id")
    private ProductAttributeValue sourceValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_field", nullable = false, length = 30)
    private ProductRuleSourceField sourceField;

    @Enumerated(EnumType.STRING)
    @Column(name = "operator", nullable = false, length = 30)
    private ProductRuleOperator operator;

    @Column(name = "comparison_from", precision = 14, scale = 3)
    private BigDecimal comparisonFrom;

    @Column(name = "comparison_to", precision = 14, scale = 3)
    private BigDecimal comparisonTo;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
