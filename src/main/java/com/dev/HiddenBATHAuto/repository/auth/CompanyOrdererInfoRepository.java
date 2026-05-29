package com.dev.HiddenBATHAuto.repository.auth;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dev.HiddenBATHAuto.model.auth.CompanyOrdererInfo;

public interface CompanyOrdererInfoRepository extends JpaRepository<CompanyOrdererInfo, Long> {

    List<CompanyOrdererInfo> findByCompanyIdOrderByIdAsc(Long companyId);

    Optional<CompanyOrdererInfo> findByIdAndCompanyId(Long id, Long companyId);
    
    List<CompanyOrdererInfo> findByCompanyIdOrderByCreatedAtDesc(Long companyId);
    
    List<CompanyOrdererInfo> findByCompany_IdOrderByCreatedAtDescIdDesc(Long companyId);
    
    List<CompanyOrdererInfo> findByCompany_IdOrderByIdAsc(Long companyId);
}