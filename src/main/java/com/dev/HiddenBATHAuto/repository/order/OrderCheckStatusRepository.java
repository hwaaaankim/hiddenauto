package com.dev.HiddenBATHAuto.repository.order;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dev.HiddenBATHAuto.model.task.OrderCheckStatus;

import jakarta.persistence.LockModeType;

public interface OrderCheckStatusRepository extends JpaRepository<OrderCheckStatus, Long> {

    Optional<OrderCheckStatus> findByOrder_Id(Long orderId);

    List<OrderCheckStatus> findByOrder_IdIn(Collection<Long> orderIds);

    boolean existsByOrder_Id(Long orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select status
            from OrderCheckStatus status
            join fetch status.order orderEntity
            where orderEntity.id = :orderId
            """)
    Optional<OrderCheckStatus> findByOrderIdForUpdate(@Param("orderId") Long orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select status
            from OrderCheckStatus status
            join fetch status.order orderEntity
            where orderEntity.id in :orderIds
            """)
    List<OrderCheckStatus> findByOrderIdInForUpdate(@Param("orderIds") Collection<Long> orderIds);

}