package com.dev.HiddenBATHAuto.model.productmaster;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.dev.HiddenBATHAuto.enums.productmaster.ProductPriceMatrixLookupMode;
import com.dev.HiddenBATHAuto.enums.productmaster.ProductRuleSourceField;

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
        name = "tb_pm_price_matrix",
        uniqueConstraints = @UniqueConstraint(name = "uk_pm_price_matrix_code", columnNames = "matrix_code"),
        indexes = @Index(name = "idx_pm_price_matrix_active", columnList = "active,id")
)
@Getter
@Setter
@NoArgsConstructor
public class ProductPriceMatrix {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "matrix_code", nullable = false, length = 14, updatable = false)
    private String matrixCode;

    @Column(name = "matrix_name", nullable = false, length = 120)
    private String matrixName;

    @Column(name = "description", length = 500)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "x_group_id", nullable = false)
    private ProductAttributeGroup xGroup;

    @Enumerated(EnumType.STRING)
    @Column(name = "x_field", nullable = false, length = 30)
    private ProductRuleSourceField xField;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "y_group_id", nullable = false)
    private ProductAttributeGroup yGroup;

    @Enumerated(EnumType.STRING)
    @Column(name = "y_field", nullable = false, length = 30)
    private ProductRuleSourceField yField;

    @Enumerated(EnumType.STRING)
    @Column(name = "lookup_mode", nullable = false, length = 20)
    private ProductPriceMatrixLookupMode lookupMode = ProductPriceMatrixLookupMode.CEILING;

    @Column(name = "x_round_unit", nullable = false, precision = 14, scale = 3)
    private BigDecimal xRoundUnit = BigDecimal.ONE;

    @Column(name = "y_round_unit", nullable = false, precision = 14, scale = 3)
    private BigDecimal yRoundUnit = BigDecimal.ONE;

    @Column(name = "extension_enabled", nullable = false)
    private boolean extensionEnabled;

    @Column(name = "extension_start", precision = 14, scale = 3)
    private BigDecimal extensionStart;

    @Column(name = "extension_unit", precision = 14, scale = 3)
    private BigDecimal extensionUnit;

    @Column(name = "extension_amount")
    private Integer extensionAmount;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @OneToMany(mappedBy = "matrix", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("yValue ASC, xValue ASC, id ASC")
    private List<ProductPriceMatrixCell> cells = new ArrayList<>();

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

    public void addCell(ProductPriceMatrixCell cell) {
        cells.add(cell);
        cell.setMatrix(this);
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
