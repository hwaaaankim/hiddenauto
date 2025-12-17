package com.dev.HiddenBATHAuto.repository.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dev.HiddenBATHAuto.model.auth.CompanyDeliveryAddress;

public interface CompanyDeliveryAddressRepository extends JpaRepository<CompanyDeliveryAddress, Long> {
}