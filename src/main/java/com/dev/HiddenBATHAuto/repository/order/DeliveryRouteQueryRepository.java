package com.dev.HiddenBATHAuto.repository.order;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.dev.HiddenBATHAuto.model.task.DeliveryOrderIndex;
import com.dev.HiddenBATHAuto.model.task.OrderStatus;

/**
 * 신규 업체별 배송 묶음/인쇄 화면 전용 읽기 저장소입니다.
 * 기존 DeliveryOrderIndexRepository의 저장 및 재정렬 API와 분리하여 영향 범위를 줄였습니다.
 */
public interface DeliveryRouteQueryRepository extends Repository<DeliveryOrderIndex, Long> {

    @Query("""
            select distinct doi
            from DeliveryOrderIndex doi
            join fetch doi.deliveryHandler handler
            join fetch doi.order o
            left join fetch o.task task
            left join fetch task.requestedBy requestedBy
            left join fetch requestedBy.company company
            left join fetch o.deliveryMethod deliveryMethod
            left join fetch o.orderItem orderItem
            left join fetch o.productCategory productCategory
            where handler.id = :handlerId
              and doi.deliveryDate = :deliveryDate
              and o.status in :statuses
            order by doi.orderIndex asc, o.id asc
            """)
    List<DeliveryOrderIndex> findRouteRows(
            @Param("handlerId") Long handlerId,
            @Param("deliveryDate") LocalDate deliveryDate,
            @Param("statuses") List<OrderStatus> statuses
    );
}
