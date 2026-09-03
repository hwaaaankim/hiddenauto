package com.dev.HiddenBATHAuto.model.productmaster;

import java.time.LocalDateTime;

import com.dev.HiddenBATHAuto.enums.productmaster.ProductDimensionType;

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
        name = "tb_pm_attribute_value",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_pm_value_code", columnNames = "value_code"),
                @UniqueConstraint(name = "uk_pm_value_customer", columnNames = {"group_id", "customer_label"}),
                @UniqueConstraint(name = "uk_pm_value_management", columnNames = {"group_id", "management_label"}),
                @UniqueConstraint(name = "uk_pm_value_production", columnNames = {"group_id", "production_label"})
        },
        indexes = {
                @Index(name = "idx_pm_value_group_sort", columnList = "group_id,sort_order,id"),
                @Index(name = "idx_pm_value_active", columnList = "active")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class ProductAttributeValue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private ProductAttributeGroup group;

    @Column(name = "value_code", nullable = false, length = 16, updatable = false)
    private String valueCode;

    @Column(name = "customer_label", nullable = false, length = 120)
    private String customerLabel;

    @Column(name = "management_label", nullable = false, length = 120)
    private String managementLabel;

    @Column(name = "production_label", nullable = false, length = 120)
    private String productionLabel;

    @Enumerated(EnumType.STRING)
    @Column(name = "dimension_type", nullable = false, length = 30)
    private ProductDimensionType dimensionType = ProductDimensionType.NONE;

    @Column(name = "price_adjustment", nullable = false)
    private int priceAdjustment;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "customer_guide", length = 1000)
    private String customerGuide;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

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
