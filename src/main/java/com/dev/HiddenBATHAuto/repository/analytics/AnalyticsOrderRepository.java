package com.dev.HiddenBATHAuto.repository.analytics;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dev.HiddenBATHAuto.model.task.Order;
import com.dev.HiddenBATHAuto.model.task.OrderStatus;

public interface AnalyticsOrderRepository extends JpaRepository<Order, Long> {

    // A single order belongs to exactly one analytics row. Never fetch Task.orders here.
    @EntityGraph(attributePaths = {"task", "task.requestedBy", "task.requestedBy.company", "orderItem"})
    @Query("""
            select o from Order o
            where (:from is null or o.createdAt >= :from)
              and (:to is null or o.createdAt < :to)
              and (o.status is null or o.status <> :excludedStatus)
            order by o.id
            """)
    List<Order> findAllForAnalytics(@Param("from") LocalDateTime from,
                                    @Param("to") LocalDateTime to,
                                    @Param("excludedStatus") OrderStatus excludedStatus);
}
