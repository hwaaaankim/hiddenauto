package com.dev.HiddenBATHAuto.model.productmaster;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.dev.HiddenBATHAuto.enums.productmaster.ProductAttributeGroupType;
import com.dev.HiddenBATHAuto.enums.productmaster.ProductAttributeInputType;
import com.dev.HiddenBATHAuto.enums.productmaster.ProductAttributeRole;
import com.dev.HiddenBATHAuto.enums.productmaster.ProductAttributeSelectionMode;
import com.dev.HiddenBATHAuto.enums.productmaster.ProductDimensionType;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
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
        name = "tb_pm_attribute_group",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_pm_group_code", columnNames = "group_code"),
                @UniqueConstraint(name = "uk_pm_group_customer_label", columnNames = "customer_label"),
                @UniqueConstraint(name = "uk_pm_group_management_label", columnNames = "management_label"),
                @UniqueConstraint(name = "uk_pm_group_production_label", columnNames = "production_label")
        },
        indexes = {
                @Index(name = "idx_pm_group_sort", columnList = "sort_order,id"),
                @Index(name = "idx_pm_group_active", columnList = "active")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class ProductAttributeGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_code", nullable = false, length = 12, updatable = false)
    private String groupCode;

    @Column(name = "customer_label", nullable = false, length = 80)
    private String customerLabel;

    @Column(name = "management_label", nullable = false, length = 80)
    private String managementLabel;

    @Column(name = "production_label", nullable = false, length = 80)
    private String productionLabel;

    @Enumerated(EnumType.STRING)
    @Column(name = "group_type", nullable = false, length = 20)
    private ProductAttributeGroupType groupType = ProductAttributeGroupType.CORE;

    @Enumerated(EnumType.STRING)
    @Column(name = "selection_mode", nullable = false, length = 20)
    private ProductAttributeSelectionMode selectionMode = ProductAttributeSelectionMode.SINGLE;

    @Enumerated(EnumType.STRING)
    @Column(name = "system_role", nullable = false, length = 30)
    private ProductAttributeRole systemRole = ProductAttributeRole.GENERAL;

    @Enumerated(EnumType.STRING)
    @Column(name = "input_type", nullable = false, length = 20)
    private ProductAttributeInputType inputType = ProductAttributeInputType.CHOICE;

    @Column(name = "question_text", length = 300)
    private String questionText;

    @Column(name = "customer_guide", length = 1000)
    private String customerGuide;

    @Column(name = "required_by_default", nullable = false)
    private boolean requiredByDefault = true;

    @Column(name = "unit_label", length = 20)
    private String unitLabel;

    @Column(name = "minimum_value", precision = 14, scale = 3)
    private BigDecimal minimumValue;

    @Column(name = "maximum_value", precision = 14, scale = 3)
    private BigDecimal maximumValue;

    @Column(name = "step_value", precision = 14, scale = 3)
    private BigDecimal stepValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "custom_dimension_type", length = 30)
    private ProductDimensionType customDimensionType;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @OneToMany(mappedBy = "group", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC, id ASC")
    private List<ProductAttributeValue> values = new ArrayList<>();

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

    public void addValue(ProductAttributeValue value) {
        values.add(value);
        value.setGroup(this);
    }

    public void removeValue(ProductAttributeValue value) {
        values.remove(value);
        value.setGroup(null);
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
