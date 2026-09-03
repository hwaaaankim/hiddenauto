package com.dev.HiddenBATHAuto.model.productmaster;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.dev.HiddenBATHAuto.enums.productmaster.ProductDynamicPriceApplyMode;
import com.dev.HiddenBATHAuto.enums.productmaster.ProductDynamicPriceRuleType;
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
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "tb_pm_dynamic_price_rule",
        uniqueConstraints = @UniqueConstraint(name = "uk_pm_dynamic_price_rule_code", columnNames = "price_rule_code"),
        indexes = {
                @Index(name = "idx_pm_dynamic_price_rule_scope", columnList = "scope_product_id,active,priority,id"),
                @Index(name = "idx_pm_dynamic_price_rule_trigger", columnList = "trigger_value_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class ProductDynamicPriceRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "price_rule_code", nullable = false, length = 14, updatable = false)
    private String priceRuleCode;

    @Column(name = "rule_name", nullable = false, length = 120)
    private String ruleName;

    @Column(name = "description", length = 500)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scope_product_id")
    private ProductMaster scopeProduct;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_type", nullable = false, length = 30)
    private ProductDynamicPriceRuleType ruleType;

    @Enumerated(EnumType.STRING)
    @Column(name = "apply_mode", nullable = false, length = 20)
    private ProductDynamicPriceApplyMode applyMode = ProductDynamicPriceApplyMode.ADD;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trigger_value_id")
    private ProductAttributeValue triggerValue;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quantity_group_id")
    private ProductAttributeGroup quantityGroup;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_group_id")
    private ProductAttributeGroup sourceGroup;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_field", length = 30)
    private ProductRuleSourceField sourceField;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "matrix_id")
    private ProductPriceMatrix matrix;

    @Column(name = "amount")
    private Integer amount;

    @Column(name = "base_number", precision = 14, scale = 3)
    private BigDecimal baseNumber;

    @Column(name = "step_number", precision = 14, scale = 3)
    private BigDecimal stepNumber;

    @Column(name = "step_amount")
    private Integer stepAmount;

    @Column(name = "priority", nullable = false)
    private int priority = 100;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_by", nullable = false, length = 100, updatable = false)
    private String createdBy;

    @Column(name = "updated_by", nullable = false, length = 100)
    private String updatedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
