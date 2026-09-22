package com.dev.HiddenBATHAuto.model.productmaster;

import com.dev.HiddenBATHAuto.enums.productmaster.ProductAttributeRole;
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
    name = "tb_pm_attribute_group",
    uniqueConstraints = {
      @UniqueConstraint(name = "uk_pm_base_role", columnNames = "base_role"),
      @UniqueConstraint(name = "uk_pm_group_code", columnNames = "group_code"),
      @UniqueConstraint(name = "uk_pm_group_customer_label", columnNames = "customer_label"),
      @UniqueConstraint(name = "uk_pm_group_management_label", columnNames = "management_label"),
      @UniqueConstraint(name = "uk_pm_group_production_label", columnNames = "production_label")
    },
    indexes = {
      @Index(name = "idx_pm_group_sort", columnList = "sort_order,id"),
      @Index(name = "idx_pm_group_active", columnList = "active")
    })
@Getter
@Setter
@NoArgsConstructor
public class ProductAttributeGroup {

  @Column(name = "ask_question", nullable = false, columnDefinition = "boolean default true")
  private boolean askQuestion = true;

  @Column(name = "price_impact", nullable = false, columnDefinition = "boolean default false")
  private boolean priceImpact;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "group_code", nullable = false, length = 12, updatable = false)
  private String groupCode;

  @Column(name = "customer_label", nullable = false, length = 80)
  private String customerLabel;

  @Column(name = "management_label", nullable = false, length = 80)
  private String managementLabel;

  @Column(name = "production_label", nullable = false, length = 80)
  private String productionLabel;

  @Enumerated(EnumType.STRING)
  @Column(name = "system_role", nullable = false, length = 30)
  private ProductAttributeRole systemRole = ProductAttributeRole.GENERAL;

  @Column(name = "question_text", length = 300)
  private String questionText;

  @Column(name = "customer_guide", length = 1000)
  private String customerGuide;

  @Column(name = "active", nullable = false)
  private boolean active = true;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  @OneToMany(mappedBy = "group", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("sortOrder ASC, id ASC")
  private List<ProductAttributeValue> values = new ArrayList<>();

  @Column(name = "base_role", length = 30)
  private String baseRole;

  @Column(name = "studio_control", length = 20, nullable = false)
  private String studioControl;

  @Column(name = "non_standard", nullable = false, columnDefinition = "boolean default false")
  private boolean nonStandard;

  @Column(name = "include_in_name", nullable = false, columnDefinition = "boolean default true")
  private boolean includeInName = true;

  @Column(name = "studio_fields_json", columnDefinition = "LONGTEXT")
  private String studioFieldsJson;

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

  public void addValue(ProductAttributeValue value) {
    values.add(value);
    value.setGroup(this);
  }

  public void removeValue(ProductAttributeValue value) {
    values.remove(value);
    value.setGroup(null);
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
