package com.dev.HiddenBATHAuto.model.productmaster;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.dev.HiddenBATHAuto.enums.productmaster.ProductMasterStatus;
import com.dev.HiddenBATHAuto.enums.productmaster.ProductPricingMode;

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
        name = "tb_pm_product",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_pm_product_name", columnNames = "product_name"),
                @UniqueConstraint(name = "uk_pm_product_code", columnNames = "product_code"),
                @UniqueConstraint(name = "uk_pm_catalog_code", columnNames = "catalog_code"),
                @UniqueConstraint(name = "uk_pm_configuration_hash", columnNames = "configuration_hash"),
                @UniqueConstraint(name = "uk_pm_qr_token", columnNames = "qr_public_token")
        },
        indexes = {
                @Index(name = "idx_pm_product_status", columnList = "status"),
                @Index(name = "idx_pm_product_stock", columnList = "current_stock,safety_stock"),
                @Index(name = "idx_pm_product_created", columnList = "created_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class ProductMaster {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_name", nullable = false, length = 160)
    private String productName;

    @Column(name = "product_code", nullable = false, length = 700, updatable = true)
    private String productCode;

    @Column(name = "catalog_code", nullable = false, length = 10, updatable = false)
    private String catalogCode;

    @Column(name = "configuration_hash", nullable = false, length = 64)
    private String configurationHash;

    @Column(name = "qr_public_token", nullable = false, length = 36, updatable = false)
    private String qrPublicToken;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ProductMasterStatus status = ProductMasterStatus.DRAFT;

    @Column(name = "description", length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "pricing_mode", nullable = false, length = 30)
    private ProductPricingMode pricingMode = ProductPricingMode.FIXED;

    @Column(name = "base_supply_price", nullable = false)
    private int baseSupplyPrice;

    @Column(name = "component_supply_price", nullable = false)
    private int componentSupplyPrice;

    @Column(name = "supply_price", nullable = false)
    private int supplyPrice;

    @Column(name = "vat_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal vatRate = new BigDecimal("10.00");

    @Column(name = "vat_amount", nullable = false)
    private int vatAmount;

    @Column(name = "total_price", nullable = false)
    private int totalPrice;

    @Column(name = "current_stock", nullable = false)
    private int currentStock;

    @Column(name = "safety_stock", nullable = false)
    private int safetyStock;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC, id ASC")
    private List<ProductComponent> components = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<ProductAddonBalance> addonBalances = new ArrayList<>();

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

    public void addComponent(ProductComponent component) {
        components.add(component);
        component.setProduct(this);
    }

    public void removeComponent(ProductComponent component) {
        components.remove(component);
        component.setProduct(null);
    }

    public void addAddonBalance(ProductAddonBalance balance) {
        addonBalances.add(balance);
        balance.setProduct(this);
    }

    public void removeAddonBalance(ProductAddonBalance balance) {
        addonBalances.remove(balance);
        balance.setProduct(null);
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
