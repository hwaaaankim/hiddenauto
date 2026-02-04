package com.dev.HiddenBATHAuto.repository.auth;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dev.HiddenBATHAuto.model.auth.CompanyDeliveryAddress;

public interface CompanyDeliveryAddressRepository extends JpaRepository<CompanyDeliveryAddress, Long> {
	
	@Query("""
        SELECT a
        FROM CompanyDeliveryAddress a
        WHERE a.company.id IN :companyIds
        ORDER BY a.company.id ASC, a.id ASC
    """)
    List<CompanyDeliveryAddress> findAllByCompanyIds(@Param("companyIds") List<Long> companyIds);
}