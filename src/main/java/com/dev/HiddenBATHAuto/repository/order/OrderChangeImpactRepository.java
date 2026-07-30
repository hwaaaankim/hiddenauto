package com.dev.HiddenBATHAuto.repository.order;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dev.HiddenBATHAuto.enums.order.OrderWorkArea;
import com.dev.HiddenBATHAuto.model.task.audit.OrderChangeImpact;

public interface OrderChangeImpactRepository extends JpaRepository<OrderChangeImpact, Long> {

    @EntityGraph(attributePaths = {"event", "event.fields"})
    @Query("""
            select impact
            from OrderChangeImpact impact
            where impact.event.order.id = :orderId
              and impact.workArea = :workArea
              and impact.version > :afterVersion
            order by impact.version asc
            """)
    List<OrderChangeImpact> findPendingImpacts(
            @Param("orderId") Long orderId,
            @Param("workArea") OrderWorkArea workArea,
            @Param("afterVersion") long afterVersion
    );

    @EntityGraph(attributePaths = {"event"})
    @Query("""
            select impact
            from OrderChangeImpact impact
            where impact.event.order.id in :orderIds
              and impact.workArea = :workArea
            order by impact.event.order.id asc, impact.version desc
            """)
    List<OrderChangeImpact> findLatestCandidates(
            @Param("orderIds") Collection<Long> orderIds,
            @Param("workArea") OrderWorkArea workArea
    );
}
