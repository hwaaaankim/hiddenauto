package com.dev.HiddenBATHAuto.orderExcelUpload.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dev.HiddenBATHAuto.model.caculate.DeliveryMethod;

public interface OrderExcelDeliveryMethodRepository extends JpaRepository<DeliveryMethod, Long> {
    List<DeliveryMethod> findAllByOrderByMethodNameAsc();

    List<DeliveryMethod> findByMethodName(String methodName);

    @Query(value = "select * from tb_delivery_method d where replace(coalesce(d.method_name, ''), ' ', '') = :normalizedMethodName order by d.id asc", nativeQuery = true)
    List<DeliveryMethod> findByMethodNameWithoutSpaces(@Param("normalizedMethodName") String normalizedMethodName);
}
