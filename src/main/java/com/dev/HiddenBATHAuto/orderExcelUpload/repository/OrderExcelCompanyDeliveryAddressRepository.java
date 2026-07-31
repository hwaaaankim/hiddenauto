package com.dev.HiddenBATHAuto.orderExcelUpload.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dev.HiddenBATHAuto.model.auth.CompanyDeliveryAddress;

public interface OrderExcelCompanyDeliveryAddressRepository extends JpaRepository<CompanyDeliveryAddress, Long> {
    List<CompanyDeliveryAddress> findByCompany_IdOrderByIdAsc(Long companyId);
}
