package com.dev.HiddenBATHAuto.repository.order;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import com.dev.HiddenBATHAuto.model.task.Order;

/** Order 자체를 조회하여 Task.orders의 부분 fetch / 첫 Order 기준 필터를 방지합니다. */
public interface CustomerOrderQueryRepository extends Repository<Order, Long> {
    @Query("""
        select o from Order o
        join fetch o.task t join fetch t.requestedBy requester
        left join fetch o.orderItem left join fetch o.deliveryMethod
        left join fetch o.productCategory left join fetch o.assignedDeliveryHandler
        where requester.company.id = :companyId
        and ((:process = true and o.preferredDeliveryDate is not null
            and (:startAt is null or o.preferredDeliveryDate >= :startAt)
            and (:endAt is null or o.preferredDeliveryDate < :endAt))
          or (:process = false
            and (:startAt is null or coalesce(o.createdAt,t.createdAt) >= :startAt)
            and (:endAt is null or coalesce(o.createdAt,t.createdAt) < :endAt)))
        order by t.id desc, o.id asc
        """)
    List<Order> findCalendarOrders(@Param("companyId") Long companyId,
        @Param("process") boolean process, @Param("startAt") LocalDateTime startAt,
        @Param("endAt") LocalDateTime endAt);
    @Query("""
        select o from Order o join fetch o.task t join fetch t.requestedBy requester
        left join fetch o.orderItem left join fetch o.deliveryMethod left join fetch o.productCategory
        where requester.company.id = :companyId and o.status = :status
        and o.updatedAt >= :startAt and o.updatedAt < :endAt
        """)
    List<Order> findCompletedOrders(@Param("companyId") Long companyId,
        @Param("status") com.dev.HiddenBATHAuto.model.task.OrderStatus status,
        @Param("startAt") LocalDateTime startAt, @Param("endAt") LocalDateTime endAt);
    @Query("""
        select o from Order o join fetch o.task t left join fetch o.deliveryMethod
        where t.requestedBy.company.id = :companyId and t.id in :taskIds
        """)
    List<Order> findTaskDeliveryOrders(@Param("companyId") Long companyId,
        @Param("taskIds") java.util.Collection<Long> taskIds);
}
