package com.dev.HiddenBATHAuto.repository.auth;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import com.dev.HiddenBATHAuto.dto.CompanyListDTO;

@Repository
public interface CompanyRepositoryCustom {
    Page<CompanyListDTO> findCompanyList(String keyword, Pageable pageable);
}