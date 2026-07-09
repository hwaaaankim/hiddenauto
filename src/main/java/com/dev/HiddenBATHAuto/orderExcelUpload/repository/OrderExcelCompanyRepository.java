package com.dev.HiddenBATHAuto.orderExcelUpload.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dev.HiddenBATHAuto.model.auth.Company;

public interface OrderExcelCompanyRepository extends JpaRepository<Company, Long> {
    Optional<Company> findByBusinessNumber(String businessNumber);

    List<Company> findByCompanyName(String companyName);

    @Query(value = "select * from tb_company c where replace(coalesce(c.name, ''), ' ', '') = :normalizedName", nativeQuery = true)
    List<Company> findByCompanyNameWithoutSpaces(@Param("normalizedName") String normalizedName);
}
