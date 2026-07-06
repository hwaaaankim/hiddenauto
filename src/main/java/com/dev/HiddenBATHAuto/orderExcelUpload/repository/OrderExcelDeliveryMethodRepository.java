package com.dev.HiddenBATHAuto.orderExcelUpload.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dev.HiddenBATHAuto.model.caculate.DeliveryMethod;

public interface OrderExcelDeliveryMethodRepository extends JpaRepository<DeliveryMethod, Long> {
    List<DeliveryMethod> findAllByOrderByMethodNameAsc();
}
