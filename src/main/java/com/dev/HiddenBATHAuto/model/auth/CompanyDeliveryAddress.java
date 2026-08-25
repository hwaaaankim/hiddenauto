package com.dev.HiddenBATHAuto.model.auth;

import java.io.Serializable;
import java.time.LocalDateTime;

import com.dev.HiddenBATHAuto.utils.KoreanAdministrativeRegionNormalizer;
import com.fasterxml.jackson.annotation.JsonBackReference;

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
import lombok.Data;

@Entity
@Table(name = "tb_company_delivery_address", indexes = {
        @Index(name = "idx_delivery_company_id", columnList = "company_id")
})
@Data
public class CompanyDeliveryAddress implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonBackReference("company-deliveryAddress")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(name = "zip_code", length = 20)
    private String zipCode;

    @Column(name = "do_name", length = 50)
    private String doName;

    @Column(name = "si_name", length = 50)
    private String siName;

    @Column(name = "gu_name", length = 50)
    private String guName;

    @Column(name = "road_address", length = 255, nullable = false)
    private String roadAddress;

    @Column(name = "detail_address", length = 255)
    private String detailAddress;

    /**
     * 추가 배송지의 시/도 값도 프로젝트 표준 명칭으로 저장합니다.
     */
    public void setDoName(String doName) {
        this.doName = normalizeProvincePreservingNull(doName);
    }

    @PrePersist
    @PreUpdate
    private void normalizeProvinceNameBeforeSave() {
        this.doName = normalizeProvincePreservingNull(this.doName);
    }

    private String normalizeProvincePreservingNull(String value) {
        if (value == null) {
            return null;
        }
        return KoreanAdministrativeRegionNormalizer.canonicalProvinceName(value);
    }

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}