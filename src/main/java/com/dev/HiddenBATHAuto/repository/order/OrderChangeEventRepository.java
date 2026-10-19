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
    /** 현재 고객사 권한을 우선하며, 완전히 삭제된 Task는 보존된 고객사로만 조회합니다. */
    @EntityGraph(attributePaths = {"fields", "order", "order.task", "order.orderItem"})
    @Query("""
        select distinct event from OrderChangeEvent event
        where (:allTasks = true or event.taskIdSnapshot in :taskIds)
          and (exists (select t.id from Task t where t.id = event.taskIdSnapshot
                and t.requestedBy.company.id = :companyId
                and (event.customerCompanyIdSnapshot is null or event.customerCompanyIdSnapshot = :companyId))
            or (event.customerCompanyIdSnapshot = :companyId
                and not exists (select t.id from Task t where t.id = event.taskIdSnapshot)))
          and exists (select f.id from OrderChangeField f where f.event = event
            and f.fieldKey in ('preferredDeliveryDate','deliveryAddress','siteAddress','deliveryMethod','deleted','requesterCompany'))
        order by event.id asc
        """)
    List<OrderChangeEvent> findCustomerHistory(@Param("companyId") Long companyId,
        @Param("allTasks") boolean allTasks, @Param("taskIds") Collection<Long> taskIds);
    @Query("""
        select distinct event.taskIdSnapshot from OrderChangeEvent event
        where event.taskIdSnapshot is not null
          and (exists (select t.id from Task t where t.id = event.taskIdSnapshot
                and t.requestedBy.company.id = :companyId
                and (event.customerCompanyIdSnapshot is null or event.customerCompanyIdSnapshot = :companyId))
            or (event.customerCompanyIdSnapshot = :companyId
                and not exists (select t.id from Task t where t.id = event.taskIdSnapshot)))
        and ((:process = true and (
            (event.customerDeliveryDateSnapshot >= :startAt and event.customerDeliveryDateSnapshot < :endAt)
            or exists (select f.id from OrderChangeField f where f.event = event and f.fieldKey = 'preferredDeliveryDate'
                and ((f.beforeValue >= :startText and f.beforeValue < :endText)
                    or (f.afterValue >= :startText and f.afterValue < :endText)))))
          or (:process = false and event.customerOrderCreatedAtSnapshot >= :startAt
              and event.customerOrderCreatedAtSnapshot < :endAt))
        """)
    List<Long> findCustomerCalendarTaskIds(@Param("companyId") Long companyId, @Param("process") boolean process,
        @Param("startAt") java.time.LocalDateTime startAt, @Param("endAt") java.time.LocalDateTime endAt,
        @Param("startText") String startText, @Param("endText") String endText);
    @Query("""
        select event.taskIdSnapshot from OrderChangeEvent event
        where event.customerCompanyIdSnapshot = :companyId
          and not exists (select t.id from Task t where t.id = event.taskIdSnapshot)
          and exists (select f.id from OrderChangeField f where f.event = event and f.fieldKey = 'deleted')
        order by event.id desc
        """)
    List<Long> findRecentlyDeletedCustomerTaskIds(@Param("companyId") Long companyId, Pageable pageable);
}
