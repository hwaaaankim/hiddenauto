package com.dev.HiddenBATHAuto.model.productmaster;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "tb_pm_stock_movement_addon",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_pm_stock_addon", columnNames = {"movement_id", "value_id"})
        },
        indexes = {
                @Index(name = "idx_pm_stock_addon_value", columnList = "value_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class ProductStockMovementAddon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "movement_id", nullable = false)
    private ProductStockMovement movement;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "value_id", nullable = false)
    private ProductAttributeValue optionValue;

    @Column(name = "quantity_delta", nullable = false)
    private int quantityDelta;

    @Column(name = "balance_before", nullable = false)
    private int balanceBefore;

    @Column(name = "balance_after", nullable = false)
    private int balanceAfter;
}
