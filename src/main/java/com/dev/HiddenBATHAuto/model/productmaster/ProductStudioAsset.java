package com.dev.HiddenBATHAuto.model.productmaster;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Staged uploads are private to their creator until explicitly attached. */
@Entity
@Table(
    name = "tb_pm_studio_asset",
    indexes = {
      @Index(name = "idx_pm_asset_owner", columnList = "owner_type,owner_id"),
      @Index(name = "idx_pm_asset_staged", columnList = "created_at,owner_type")
    })
@Getter
@Setter
public class ProductStudioAsset {
  @Id
  @Column(length = 36)
  private String id;

  @Column(name = "owner_type", nullable = false, length = 20)
  private String ownerType;

  @Column(name = "owner_id")
  private Long ownerId;

  @Column(name = "original_name", nullable = false, length = 240)
  private String originalName;

  @Column(name = "content_type", nullable = false, length = 100)
  private String contentType;

  @Column(name = "file_size", nullable = false)
  private long fileSize;

  @Column(name = "disk_name", nullable = false, length = 80)
  private String diskName;

  @Column(name = "created_by", nullable = false, length = 100)
  private String createdBy;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;
}
