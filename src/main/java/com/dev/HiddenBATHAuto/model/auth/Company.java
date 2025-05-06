package com.dev.HiddenBATHAuto.model.auth;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "tb_company")
@Data
public class Company {

	@Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String businessNo;
    private String address;
    private String registrationKey;

    private String businessLicenseFilename; // 업로드된 원본 파일명
    private String businessLicensePath;     // 저장된 파일 경로
    private String businessLicenseUrl;      // 클라이언트에서 접근할 URL

    private LocalDateTime createdAt = LocalDateTime.now(); // 회사 등록일
    private LocalDateTime updatedAt; // 회사정보 수정일
}
