package com.dev.HiddenBATHAuto.model.productmaster;

import java.time.LocalDateTime;

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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "tb_pm_attribute_image",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_pm_attribute_image_token", columnNames = "public_token"),
                @UniqueConstraint(name = "uk_pm_attribute_image_storage", columnNames = "storage_key")
        },
        indexes = {
                @Index(name = "idx_pm_attribute_image_group", columnList = "group_id,sort_order,id"),
                @Index(name = "idx_pm_attribute_image_value", columnList = "value_id,sort_order,id")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class ProductAttributeImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    private ProductAttributeGroup group;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "value_id")
    private ProductAttributeValue optionValue;

    @Column(name = "public_token", nullable = false, length = 36, updatable = false)
    private String publicToken;

    @Column(name = "storage_key", nullable = false, length = 180, updatable = false)
    private String storageKey;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "content_type", nullable = false, length = 30)
    private String contentType;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_by", nullable = false, length = 100, updatable = false)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;

    @PrePersist
    void prePersist() {
        if ((group == null) == (optionValue == null)) {
            throw new IllegalStateException("옵션 그룹 또는 옵션값 중 하나에만 이미지를 연결해야 합니다.");
        }
        createdAt = LocalDateTime.now();
    }
}
