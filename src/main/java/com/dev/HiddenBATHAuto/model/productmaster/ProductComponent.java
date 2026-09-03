package com.dev.HiddenBATHAuto.model.productmaster;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "tb_pm_product_component",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_pm_product_component", columnNames = {"product_id", "group_id", "value_id"})
        },
        indexes = {
                @Index(name = "idx_pm_component_product", columnList = "product_id,sort_order,id"),
                @Index(name = "idx_pm_component_group_value", columnList = "group_id,value_id"),
                @Index(name = "idx_pm_component_value", columnList = "value_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class ProductComponent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private ProductMaster product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private ProductAttributeGroup group;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "value_id")
    private ProductAttributeValue value;

    @Column(name = "width_mm")
    private Integer widthMm;

    @Column(name = "depth_mm")
    private Integer depthMm;

    @Column(name = "height_mm")
    private Integer heightMm;

    @Column(name = "numeric_value", precision = 14, scale = 3)
    private BigDecimal numericValue;

    @Column(name = "text_value", length = 500)
    private String textValue;

    @Column(name = "price_adjustment_snapshot", nullable = false)
    private int priceAdjustmentSnapshot;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
    }
}
