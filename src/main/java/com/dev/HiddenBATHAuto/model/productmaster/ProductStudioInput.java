package com.dev.HiddenBATHAuto.model.productmaster;

import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(
    name = "tb_pm_studio_input",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_pm_studio_input",
            columnNames = {"product_id", "group_id", "field_key"}),
    indexes =
        @Index(name = "idx_pm_studio_input_filter", columnList = "group_id,field_key,number_value"))
@Getter
@Setter
public class ProductStudioInput {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "product_id", nullable = false)
  private ProductMaster product;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "group_id", nullable = false)
  private ProductAttributeGroup group;

  @Column(name = "field_key", nullable = false, length = 80)
  private String fieldKey;

  @Column(name = "number_value", precision = 14, scale = 3)
  private BigDecimal numberValue;

  @Column(name = "text_value", columnDefinition = "LONGTEXT")
  private String textValue;
}
