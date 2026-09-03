package com.dev.HiddenBATHAuto.model.productmaster;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.dev.HiddenBATHAuto.enums.productmaster.ProductRuleMatchMode;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
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
        name = "tb_pm_configuration_rule",
        uniqueConstraints = @UniqueConstraint(name = "uk_pm_configuration_rule_code", columnNames = "rule_code"),
        indexes = {
                @Index(name = "idx_pm_configuration_rule_scope", columnList = "scope_product_id,active,priority,id"),
                @Index(name = "idx_pm_configuration_rule_active", columnList = "active,priority,id")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class ProductConfigurationRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rule_code", nullable = false, length = 14, updatable = false)
    private String ruleCode;

    @Column(name = "rule_name", nullable = false, length = 120)
    private String ruleName;

    @Column(name = "description", length = 500)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scope_product_id")
    private ProductMaster scopeProduct;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_mode", nullable = false, length = 10)
    private ProductRuleMatchMode matchMode = ProductRuleMatchMode.ALL;

    @Column(name = "priority", nullable = false)
    private int priority = 100;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @OneToMany(mappedBy = "rule", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC, id ASC")
    private List<ProductRuleCondition> conditions = new ArrayList<>();

    @OneToMany(mappedBy = "rule", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC, id ASC")
    private List<ProductRuleAction> actions = new ArrayList<>();

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

    public void addCondition(ProductRuleCondition condition) {
        conditions.add(condition);
        condition.setRule(this);
    }

    public void addAction(ProductRuleAction action) {
        actions.add(action);
        action.setRule(this);
    }

    public void clearDefinition() {
        conditions.clear();
        actions.clear();
    }

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
