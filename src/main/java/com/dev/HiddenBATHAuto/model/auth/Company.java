package com.dev.HiddenBATHAuto.model.auth;

import java.io.Serializable;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "tb_company")
@Data
public class Company implements Serializable {
    private static final long serialVersionUID = 1L;
	@Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

	@Column(name="name")
    private String companyName;
   
    // 우편번호
    private String zipCode;

    // 행정구역
    private String doName;   // ex: 경기도
    private String siName;   // ex: 용인시
    private String guName;   // ex: 수지구

    // 주소
    private String roadAddress;     // ex: 경기도 용인시 수지구 죽전로 55
    private String detailAddress;   // ex: 302동 1502호
    
    private String registrationKey;

    private String businessLicenseFilename; // 업로드된 원본 파일명
    private String businessLicensePath;     // 저장된 파일 경로
    private String businessLicenseUrl;      // 클라이언트에서 접근할 URL

    private LocalDateTime createdAt = LocalDateTime.now(); // 회사 등록일
    private LocalDateTime updatedAt; // 회사정보 수정일
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sales_manager_id")
    private Member salesManager; // ✅ 담당 영업사원
}
