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
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/** A complete editable diagrams.net document; each page is one drawing view. */
@Entity
@Table(
    name = "tb_pm_drawing",
    uniqueConstraints = {
      @UniqueConstraint(name = "uk_pm_drawing_product", columnNames = "product_id"),
      @UniqueConstraint(name = "uk_pm_drawing_actual", columnNames = "actual_id")
    },
    indexes = @Index(name = "idx_pm_drawing_updated", columnList = "updated_at,id"))
@Getter
@Setter
public class ProductDrawing {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "product_id")
  @OnDelete(action = OnDeleteAction.CASCADE)
  private ProductMaster product;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "actual_id")
  @OnDelete(action = OnDeleteAction.CASCADE)
  private ProductActual actual;

  @Column(name = "document_xml", nullable = false, columnDefinition = "LONGTEXT")
  private String documentXml;

  @Column(name = "page_count", nullable = false)
  private int pageCount;

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

  @PrePersist
  void prePersist() {
    validateOwner();
    createdAt = LocalDateTime.now();
    updatedAt = createdAt;
  }

  @PreUpdate
  void preUpdate() {
    validateOwner();
    updatedAt = LocalDateTime.now();
  }

  private void validateOwner() {
    if ((product == null) == (actual == null))
      throw new IllegalStateException("도면은 규격 제품 또는 비규격 실 제품 하나에 연결되어야 합니다.");
  }
}
