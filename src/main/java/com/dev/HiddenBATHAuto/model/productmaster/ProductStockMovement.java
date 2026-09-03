package com.dev.HiddenBATHAuto.model.productmaster;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.dev.HiddenBATHAuto.enums.productmaster.ProductStockMovementType;

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
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "tb_pm_stock_movement",
        indexes = {
                @Index(name = "idx_pm_stock_product_created", columnList = "product_id,created_at"),
                @Index(name = "idx_pm_stock_voided", columnList = "voided")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class ProductStockMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private ProductMaster product;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, length = 20)
    private ProductStockMovementType movementType;

    @Column(name = "quantity_delta", nullable = false)
    private int quantityDelta;

    @Column(name = "stock_before", nullable = false)
    private int stockBefore;

    @Column(name = "stock_after", nullable = false)
    private int stockAfter;

    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    @Column(name = "created_by", nullable = false, length = 100, updatable = false)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "voided", nullable = false)
    private boolean voided;

    @Column(name = "voided_by", length = 100)
    private String voidedBy;

    @Column(name = "voided_at")
    private LocalDateTime voidedAt;

    @Column(name = "void_reason", length = 500)
    private String voidReason;

    @OneToMany(mappedBy = "movement", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<ProductStockMovementAddon> addonLines = new ArrayList<>();

    public void addAddonLine(ProductStockMovementAddon line) {
        addonLines.add(line);
        line.setMovement(this);
    }

    public void voidMovement(String actor, String reasonText) {
        voided = true;
        voidedBy = actor;
        voidReason = reasonText;
        voidedAt = LocalDateTime.now();
    }

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
    }
}
