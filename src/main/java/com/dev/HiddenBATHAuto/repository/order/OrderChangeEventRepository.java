package com.dev.HiddenBATHAuto.repository.order;

import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dev.HiddenBATHAuto.model.task.audit.OrderChangeEvent;

public interface OrderChangeEventRepository extends JpaRepository<OrderChangeEvent, Long> {

    @EntityGraph(attributePaths = {"fields"})
    @Query("""
            select distinct event
            from OrderChangeEvent event
            where event.order.id = :orderId
            order by event.id desc
            """)
    List<OrderChangeEvent> findHistory(@Param("orderId") Long orderId, Pageable pageable);

    @EntityGraph(attributePaths = {"fields"})
    @Query("""
            select distinct event
            from OrderChangeEvent event
            where event.id in :eventIds
            order by event.id desc
            """)
    List<OrderChangeEvent> findAllDetailedByIdIn(@Param("eventIds") Collection<Long> eventIds);

    @Query("""
            select event
            from OrderChangeEvent event
            where event.order.id in :orderIds
            order by event.order.id asc, event.id desc
            """)
    List<OrderChangeEvent> findLatestCandidates(@Param("orderIds") Collection<Long> orderIds);
}
