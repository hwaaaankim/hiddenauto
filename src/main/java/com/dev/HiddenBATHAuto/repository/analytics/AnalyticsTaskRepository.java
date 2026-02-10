package com.dev.HiddenBATHAuto.repository.analytics;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dev.HiddenBATHAuto.model.task.Task;

public interface AnalyticsTaskRepository extends JpaRepository<Task, Long> {

    @EntityGraph(attributePaths = {
            "requestedBy",
            "requestedBy.company",
            "orders",
            "orders.orderItem"
    })
    @Query("""
        select distinct t
        from Task t
        left join t.orders o
        left join o.orderItem oi
        where t.createdAt >= :from
          and t.createdAt < :to
    """)
    List<Task> findAllForAnalytics(@Param("from") LocalDateTime from,
                                   @Param("to") LocalDateTime to);
}