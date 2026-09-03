package com.dev.HiddenBATHAuto.model.productmaster;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.dev.HiddenBATHAuto.enums.productmaster.ProductPricingMode;

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
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "tb_pm_price_history",
        indexes = {
                @Index(name = "idx_pm_price_product_created", columnList = "product_id,created_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class ProductPriceHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private ProductMaster product;

    @Enumerated(EnumType.STRING)
    @Column(name = "pricing_mode", nullable = false, length = 30)
    private ProductPricingMode pricingMode;

    @Column(name = "base_supply_price", nullable = false)
    private int baseSupplyPrice;

    @Column(name = "component_supply_price", nullable = false)
    private int componentSupplyPrice;

    @Column(name = "supply_price", nullable = false)
    private int supplyPrice;

    @Column(name = "vat_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal vatRate;

    @Column(name = "vat_amount", nullable = false)
    private int vatAmount;

    @Column(name = "total_price", nullable = false)
    private int totalPrice;

    @Column(name = "change_reason", nullable = false, length = 300)
    private String changeReason;

    @Column(name = "created_by", nullable = false, length = 100, updatable = false)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
    }
}
