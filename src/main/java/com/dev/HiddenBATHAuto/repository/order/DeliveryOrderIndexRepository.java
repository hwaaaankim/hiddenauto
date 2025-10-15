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
    Optional<DeliveryOrderIndex> findByOrder(Order order);

    int countByDeliveryHandlerAndDeliveryDate(Member handler, LocalDate date);

    boolean existsByOrder_Id(Long orderId);
    boolean existsByOrder(Order order);

    @Query("""
        SELECT MAX(d.orderIndex)
          FROM DeliveryOrderIndex d
         WHERE d.deliveryHandler.id = :handlerId
           AND d.deliveryDate = :date
    """)
    Integer findMaxIndexByHandlerAndDate(@Param("handlerId") Long handlerId, @Param("date") LocalDate date);

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

    Optional<DeliveryOrderIndex> findByDeliveryHandlerIdAndDeliveryDateAndOrderId(
        Long handlerId, LocalDate deliveryDate, Long orderId);

    // (D) 담당자/날짜 해제 시 제거용
    void deleteByOrder(Order order);
}
