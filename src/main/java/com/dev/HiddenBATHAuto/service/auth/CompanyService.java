package com.dev.HiddenBATHAuto.service.auth;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.dev.HiddenBATHAuto.model.auth.Company;
import com.dev.HiddenBATHAuto.repository.auth.CompanyRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CompanyService {
	
	private final CompanyRepository companyRepository;

    public Page<Company> getCompanyList(String keyword, String searchType, Pageable pageable) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return companyRepository.findAll(pageable); // 전체 조회
        }
        return companyRepository.findWithSearch(keyword.trim(), searchType, pageable);
    }

}
