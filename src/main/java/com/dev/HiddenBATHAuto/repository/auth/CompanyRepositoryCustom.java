package com.dev.HiddenBATHAuto.repository.auth;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import com.dev.HiddenBATHAuto.dto.client.CompanyListRowDto;
import com.dev.HiddenBATHAuto.model.auth.Company;

@Repository
public interface CompanyRepositoryCustom {
    Page<CompanyListRowDto> searchCompanyList(String keyword, String searchType, String sortField, String sortDir, Pageable pageable);

    List<Company> findAllForExcel(String keyword, String searchType, String sortField, String sortDir);
}