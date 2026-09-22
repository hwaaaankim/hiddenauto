package com.dev.HiddenBATHAuto.model.productmaster;

import com.dev.HiddenBATHAuto.enums.productmaster.ProductMasterStatus;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "tb_pm_product",
    uniqueConstraints = {
      @UniqueConstraint(name = "uk_pm_studio_identity", columnNames = "studio_identity"),
      @UniqueConstraint(name = "uk_pm_product_code", columnNames = "product_code"),
      @UniqueConstraint(name = "uk_pm_catalog_code", columnNames = "catalog_code"),
      @UniqueConstraint(name = "uk_pm_configuration_hash", columnNames = "configuration_hash"),
      @UniqueConstraint(name = "uk_pm_qr_token", columnNames = "qr_public_token")
    },
    indexes = {
      @Index(name = "idx_pm_studio_kind_status", columnList = "non_standard,status,id"),
      @Index(name = "idx_pm_product_name", columnList = "product_name"),
      @Index(name = "idx_pm_product_status", columnList = "status"),
      @Index(name = "idx_pm_product_stock", columnList = "current_stock"),
      @Index(name = "idx_pm_product_created", columnList = "created_at")
    })
@Getter
@Setter
@NoArgsConstructor
public class ProductMaster {

  @Column(
      name = "legacy_unallocated_stock",
      nullable = false,
      columnDefinition = "integer default 0")
  private int legacyUnallocatedStock;

  @Column(name = "production_hours", precision = 12, scale = 3)
  private java.math.BigDecimal productionHours;

  @Column(name = "unit_price", precision = 15, scale = 2)
  private java.math.BigDecimal unitPrice;

  @Column(name = "faq_topic_id")
  private Long faqTopicId;

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

  @Column(name = "current_stock", nullable = false)
  private int currentStock;

  @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("sortOrder ASC, id ASC")
  private List<ProductComponent> components = new ArrayList<>();

  @Column(name = "non_standard", nullable = false, columnDefinition = "boolean default false")
  private boolean nonStandard;

  @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<ProductStudioInput> studioInputs = new ArrayList<>();

  @Column(name = "studio_identity", length = 64, nullable = false)
  private String studioIdentity;

  @Column(name = "studio_definition_json", columnDefinition = "LONGTEXT", nullable = false)
  private String studioDefinitionJson;

  @Column(name = "name_tokens_json", columnDefinition = "LONGTEXT")
  private String nameTokensJson;

  @Column(name = "studio_process_json", columnDefinition = "LONGTEXT")
  private String studioProcessJson;

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
