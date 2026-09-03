package com.dev.HiddenBATHAuto.model.productmaster;

import java.math.BigDecimal;

import com.dev.HiddenBATHAuto.enums.productmaster.ProductRuleActionType;

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
        name = "tb_pm_rule_action",
        indexes = {
                @Index(name = "idx_pm_rule_action_rule", columnList = "rule_id,sort_order,id"),
                @Index(name = "idx_pm_rule_action_target", columnList = "target_group_id,target_value_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class ProductRuleAction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rule_id", nullable = false)
    private ProductConfigurationRule rule;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 30)
    private ProductRuleActionType actionType;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_group_id", nullable = false)
    private ProductAttributeGroup targetGroup;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_value_id")
    private ProductAttributeValue targetValue;

    @Column(name = "action_number", precision = 14, scale = 3)
    private BigDecimal actionNumber;

    @Column(name = "message", length = 500)
    private String message;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
