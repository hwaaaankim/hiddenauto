package com.dev.HiddenBATHAuto.repository.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.dev.HiddenBATHAuto.model.auth.Company;

@Repository
public interface CompanyRepository extends JpaRepository<Company, Long>{

	Company findByName(String name);
}
