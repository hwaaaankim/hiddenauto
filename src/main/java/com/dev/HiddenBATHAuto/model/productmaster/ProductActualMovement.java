package com.dev.HiddenBATHAuto.model.productmaster;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "tb_pm_actual_movement")
@Getter
@Setter
public class ProductActualMovement {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Version private long rowVersion;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "actual_id", nullable = false)
  private ProductActual actual;

  @Column(nullable = false)
  private int delta;

  @Column(nullable = false)
  private int stockAfter;

  @Column(nullable = false, length = 500)
  private String reason;

  @Column(nullable = false, length = 100)
  private String actor;

  private java.time.LocalDateTime createdAt = java.time.LocalDateTime.now();
}
