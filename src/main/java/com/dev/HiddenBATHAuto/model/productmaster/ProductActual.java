package com.dev.HiddenBATHAuto.model.productmaster;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(
    name = "tb_pm_actual",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_pm_actual_spec",
            columnNames = {"product_id", "spec_hash"}))
@Getter
@Setter
public class ProductActual {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Version private long rowVersion;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "product_id", nullable = false)
  private ProductMaster product;

  @Column(nullable = false, unique = true, length = 50)
  private String code;

  @Column(name = "spec_hash", nullable = false, length = 64)
  private String specHash;

  @Column(name = "answers_json", nullable = false, columnDefinition = "LONGTEXT")
  private String answersJson;

  @Column(name = "current_stock", nullable = false)
  private int currentStock;

  @Column(name = "created_by", nullable = false, length = 100)
  private String createdBy;

  private java.time.LocalDateTime createdAt = java.time.LocalDateTime.now();
}
