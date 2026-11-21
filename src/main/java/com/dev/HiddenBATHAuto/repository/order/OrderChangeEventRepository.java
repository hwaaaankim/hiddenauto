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
    // 현재 주문과 과거 이벤트의 Task가 일치해야 합니다. 삭제 주문은 마지막 소속을 입증하는 삭제 이력이 필요합니다.
    String CUSTOMER_OWNERSHIP = """
          and (event.customerCompanyIdSnapshot = :companyId or event.customerCompanyIdSnapshot is null)
          and (exists (select live.id from Order live where live = event.order
                  and live.task.id = event.taskIdSnapshot and live.task.requestedBy.company.id = :companyId)
            or (event.order is null and exists (
                  select deletion.id from OrderChangeEvent deletion
                  where deletion.orderIdSnapshot = event.orderIdSnapshot
                    and deletion.taskIdSnapshot = event.taskIdSnapshot
                    and deletion.customerCompanyIdSnapshot = :companyId
                    and exists (select marker.id from OrderChangeField marker
                        where marker.event = deletion and marker.fieldKey = 'deleted'))))
        """;

    @EntityGraph(attributePaths = {"fields", "order", "order.task", "order.orderItem"})
    @Query("""
        select distinct event from OrderChangeEvent event
        where (:allTasks = true or event.taskIdSnapshot in :taskIds)
        """ + CUSTOMER_OWNERSHIP + """
          and exists (select f.id from OrderChangeField f where f.event = event
            and f.fieldKey in ('preferredDeliveryDate','deliveryAddress','siteAddress','deliveryMethod','deleted','requesterCompany'))
        order by event.id asc
        """)
    List<OrderChangeEvent> findCustomerHistory(@Param("companyId") Long companyId,
        @Param("allTasks") boolean allTasks, @Param("taskIds") Collection<Long> taskIds);
    @Query("""
        select distinct event.taskIdSnapshot from OrderChangeEvent event
        where event.taskIdSnapshot is not null
        """ + CUSTOMER_OWNERSHIP + """
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
    interface TransferBoundary {
        Long getTaskId();
        Long getEventId();
    }

    // 과거 전환을 기록한 오더가 이후 이동·삭제되어도 같은 Task의 형제 오더에는 그 경계가 적용됩니다.
    @Query("""
        select f.event.taskIdSnapshot as taskId, max(f.event.id) as eventId from OrderChangeField f
        where f.fieldKey = 'requesterCompany' and f.event.taskIdSnapshot in :taskIds
        group by f.event.taskIdSnapshot
        """)
    List<TransferBoundary> findCustomerTransferBoundaries(@Param("taskIds") Collection<Long> taskIds);

    @Query("select t.id from Task t where t.id in :taskIds and t.requestedBy.company.id = :companyId and t.orders is not empty")
    List<Long> findCustomerAccessibleTaskIds(@Param("companyId") Long companyId,
        @Param("taskIds") Collection<Long> taskIds);

    @Query("""
        select event.taskIdSnapshot from OrderChangeEvent event
        where event.customerCompanyIdSnapshot = :companyId and event.order is null
          and not exists (select t.id from Task t where t.id = event.taskIdSnapshot and t.orders is not empty)
          and exists (select f.id from OrderChangeField f where f.event = event and f.fieldKey = 'deleted')
        order by event.id desc
        """)
    List<Long> findRecentlyDeletedCustomerTaskIds(@Param("companyId") Long companyId, Pageable pageable);
}
