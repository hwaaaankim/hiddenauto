package com.dev.HiddenBATHAuto.model.productmaster;

import java.math.BigDecimal;

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
        name = "tb_pm_price_matrix_cell",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_pm_price_matrix_cell_axis",
                columnNames = {"matrix_id", "x_value", "y_value"}
        ),
        indexes = @Index(name = "idx_pm_price_matrix_cell_lookup", columnList = "matrix_id,x_value,y_value")
)
@Getter
@Setter
@NoArgsConstructor
public class ProductPriceMatrixCell {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "matrix_id", nullable = false)
    private ProductPriceMatrix matrix;

    @Column(name = "x_value", nullable = false, precision = 14, scale = 3)
    private BigDecimal xValue;

    @Column(name = "y_value", nullable = false, precision = 14, scale = 3)
    private BigDecimal yValue;

    @Column(name = "amount", nullable = false)
    private int amount;
}
