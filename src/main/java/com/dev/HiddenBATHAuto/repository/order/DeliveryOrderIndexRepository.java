package com.dev.HiddenBATHAuto.repository.order;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.task.DeliveryOrderIndex;
import com.dev.HiddenBATHAuto.model.task.Order;
import com.dev.HiddenBATHAuto.model.task.OrderStatus;

@Repository
public interface DeliveryOrderIndexRepository extends JpaRepository<DeliveryOrderIndex, Long> {

    List<DeliveryOrderIndex> findByDeliveryHandlerAndDeliveryDateOrderByOrderIndex(Member handler, LocalDate date);

    Optional<DeliveryOrderIndex> findByOrder_Id(Long orderId);

    int countByDeliveryHandlerAndDeliveryDate(Member handler, LocalDate date);

    boolean existsByOrder_Id(Long orderId);
    boolean existsByOrder(Order order);

    @Query("""
        SELECT doi
          FROM DeliveryOrderIndex doi
         WHERE doi.deliveryHandler.id = :handlerId
           AND doi.deliveryDate = :deliveryDate
           AND doi.order.status IN :statuses
      ORDER BY doi.orderIndex ASC
    """)
    Page<DeliveryOrderIndex> findByHandlerAndDateAndStatusIn(
            @Param("handlerId") Long handlerId,
            @Param("deliveryDate") LocalDate deliveryDate,
            @Param("statuses") List<OrderStatus> statuses,
            Pageable pageable
    );

    // (D) 담당자/날짜 해제 시 제거용
    void deleteByOrder(Order order);
    
    
    @Query("""
        SELECT doi
          FROM DeliveryOrderIndex doi
         WHERE doi.deliveryHandler.id = :handlerId
           AND doi.deliveryDate = :deliveryDate
           AND doi.order.status IN :statuses
      ORDER BY
           CASE WHEN doi.order.status = com.dev.HiddenBATHAuto.model.task.OrderStatus.DELIVERY_DONE THEN 1 ELSE 0 END ASC,
           doi.orderIndex ASC
    """)
    List<DeliveryOrderIndex> findListByHandlerAndDateAndStatusIn(
            @Param("handlerId") Long handlerId,
            @Param("deliveryDate") LocalDate deliveryDate,
            @Param("statuses") List<OrderStatus> statuses
    );

    java.util.Optional<DeliveryOrderIndex> findByOrder(Order order);

    @Query("""
        SELECT MAX(doi.orderIndex)
          FROM DeliveryOrderIndex doi
         WHERE doi.deliveryHandler.id = :handlerId
           AND doi.deliveryDate = :deliveryDate
    """)
    Integer findMaxIndexByHandlerAndDate(@Param("handlerId") Long handlerId,
                                        @Param("deliveryDate") LocalDate deliveryDate);

    @Query("""
        SELECT doi
          FROM DeliveryOrderIndex doi
         WHERE doi.deliveryHandler.id = :handlerId
           AND doi.deliveryDate = :deliveryDate
      ORDER BY
           CASE WHEN doi.order.status = com.dev.HiddenBATHAuto.model.task.OrderStatus.DELIVERY_DONE THEN 1 ELSE 0 END ASC,
           doi.orderIndex ASC
    """)
    List<DeliveryOrderIndex> findAllByHandlerAndDateForGuard(
            @Param("handlerId") Long handlerId,
            @Param("deliveryDate") LocalDate deliveryDate
    );

    java.util.Optional<DeliveryOrderIndex> findByDeliveryHandlerIdAndDeliveryDateAndOrderId(
            Long handlerId,
            LocalDate deliveryDate,
            Long orderId
    );
}
