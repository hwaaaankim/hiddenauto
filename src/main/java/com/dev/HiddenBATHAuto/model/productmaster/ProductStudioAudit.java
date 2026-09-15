package com.dev.HiddenBATHAuto.model.productmaster;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * Inventory remains associated with product ID; preserve specification changes for traceability.
 */
@Entity
@Table(
    name = "tb_pm_studio_audit",
    indexes = @Index(name = "idx_pm_studio_audit_product", columnList = "product_id,created_at"))
@Getter
@Setter
public class ProductStudioAudit {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "product_id", nullable = false)
  private ProductMaster product;

  @Column(name = "before_json", columnDefinition = "LONGTEXT", nullable = false)
  private String beforeJson;

  @Column(name = "after_json", columnDefinition = "LONGTEXT", nullable = false)
  private String afterJson;

  @Column(nullable = false, length = 100)
  private String actor;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;
}
