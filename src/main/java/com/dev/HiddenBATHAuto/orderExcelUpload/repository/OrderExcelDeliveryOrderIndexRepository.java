package com.dev.HiddenBATHAuto.orderExcelUpload.repository;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dev.HiddenBATHAuto.model.task.DeliveryOrderIndex;

public interface OrderExcelDeliveryOrderIndexRepository extends JpaRepository<DeliveryOrderIndex, Long> {
    @Query("select max(d.orderIndex) from DeliveryOrderIndex d where d.deliveryHandler.id = :deliveryHandlerId and d.deliveryDate = :deliveryDate")
    Optional<Integer> findMaxOrderIndexByDeliveryHandlerAndDeliveryDate(
            @Param("deliveryHandlerId") Long deliveryHandlerId,
            @Param("deliveryDate") LocalDate deliveryDate
    );
}
