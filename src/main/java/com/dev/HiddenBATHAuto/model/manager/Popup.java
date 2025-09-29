package com.dev.HiddenBATHAuto.model.manager;

import java.time.LocalDateTime;

import org.hibernate.annotations.Comment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "tb_popup",
       indexes = {
           @Index(name = "idx_popup_start_at", columnList = "start_at"),
           @Index(name = "idx_popup_end_at", columnList = "end_at"),
           @Index(name = "idx_popup_link_enabled", columnList = "link_enabled"),
           @Index(name = "idx_popup_disp_order", columnList = "disp_order") // ✅ 노출순서 인덱스
       }
)
public class Popup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("PK")
    private Long id;

    @NotBlank
    @Column(name = "image_path", nullable = false, length = 500)
    @Comment("서버 파일 시스템 절대경로")
    private String imagePath;

    @NotBlank
    @Column(name = "image_url", nullable = false, length = 500)
    @Comment("클라이언트 접근 URL: /upload/popup/{yyyy-MM-dd}/{filename}")
    private String imageUrl;

    @NotNull
    @Column(name = "link_enabled", nullable = false)
    @Comment("연결 URL 사용여부 (기본 false)")
    private Boolean linkEnabled;

    @Column(name = "link_url", length = 1000)
    @Comment("연결 URL (link_enabled=true일 때만 의미)")
    private String linkUrl;

    @NotNull
    @Column(name = "start_at", nullable = false)
    @Comment("게시 시작일시")
    private LocalDateTime startAt;

    @NotNull
    @Column(name = "end_at", nullable = false)
    @Comment("게시 종료일시")
    private LocalDateTime endAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Comment("등록일시")
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    @Comment("수정일시")
    private LocalDateTime updatedAt;

    @NotNull
    @Column(name = "disp_order", nullable = false)
    @Comment("노출순서(작을수록 먼저)")
    private Integer dispOrder; // ✅ 신규 필드

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.linkEnabled == null) this.linkEnabled = false;
        if (this.dispOrder == null) this.dispOrder = 0;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
        if (this.linkEnabled == null) this.linkEnabled = false;
        if (this.dispOrder == null) this.dispOrder = 0;
    }
}