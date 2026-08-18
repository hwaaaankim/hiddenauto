package com.dev.HiddenBATHAuto.repository.order;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.task.OrderStatus;
import com.dev.HiddenBATHAuto.model.task.Task;

@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {

    // ✅ 신청일 기준에서도 모달 상세에 orders가 필요하므로 fetch join
    @Query("select distinct t from Task t left join fetch t.orders o where t.requestedBy = :member")
    List<Task> findByRequestedByFetchOrders(@Param("member") Member member);

    // ✅ 처리일 기준: preferredDeliveryDate 있는 주문이 있는 Task만 + fetch join
    @Query("""
        select distinct t
        from Task t
        join fetch t.orders o
        where t.requestedBy = :member
          and o.preferredDeliveryDate is not null
        """)
    List<Task> findByRequestedByAndPreferredDeliveryNotNullFetchOrders(@Param("member") Member member);

    /**
     * index 달력/오버뷰 신청일 기준 전용.
     * - Task.createdAt이 현재 FullCalendar 표시 기간에 포함되는 발주서만 조회
     * - 오버뷰에서 필요한 OrderItem(optionJson), DeliveryMethod, productCategory까지 한 번에 로딩
     * - 기존 달력의 "Task 신청일 기준" 의미를 그대로 유지
     */
    @Query("""
        select distinct t
        from Task t
        left join fetch t.orders o
        left join fetch o.orderItem oi
        left join fetch o.deliveryMethod dm
        left join fetch o.productCategory pc
        left join fetch o.assignedDeliveryHandler adh
        where t.requestedBy = :member
          and t.createdAt >= :start
          and t.createdAt < :end
        """)
    List<Task> findCalendarRequestedRange(
            @Param("member") Member member,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    /**
     * index 달력/오버뷰 처리일 기준 전용.
     *
     * <p>기존 달력은 Task 안에서 첫 번째 preferredDeliveryDate를 처리일로 사용합니다.
     * 여기서는 조회 후보를 기간으로 먼저 줄인 뒤 APIController에서 기존 extractTaskDate 규칙으로
     * 다시 필터링하여 기존 화면 의미가 바뀌지 않도록 합니다.</p>
     */
    @Query("""
        select distinct t
        from Task t
        join t.orders calendarOrder
        join fetch t.orders o
        left join fetch o.orderItem oi
        left join fetch o.deliveryMethod dm
        left join fetch o.productCategory pc
        left join fetch o.assignedDeliveryHandler adh
        where t.requestedBy = :member
          and o.preferredDeliveryDate is not null
          and calendarOrder.preferredDeliveryDate is not null
          and calendarOrder.preferredDeliveryDate >= :start
          and calendarOrder.preferredDeliveryDate < :end
        """)
    List<Task> findCalendarProcessedRangeCandidates(
            @Param("member") Member member,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    /**
     * index 메인 "최근 7일 처리완료" 전용.
     *
     * <p>현재 Order 모델에는 deliveryCompletedAt 전용 컬럼이 없고,
     * 배송완료 처리 로직에서 DELIVERY_DONE 전환 시점의 LocalDateTime을 updatedAt에 저장하고 있으므로
     * DELIVERY_DONE + updatedAt 범위를 완료시점으로 사용합니다.</p>
     */
    @Query("""
        select distinct t
        from Task t
        join fetch t.orders o
        left join fetch o.orderItem oi
        left join fetch o.deliveryMethod dm
        left join fetch o.productCategory pc
        left join fetch o.assignedDeliveryHandler adh
        where t.requestedBy = :member
          and o.status = :deliveryDoneStatus
          and o.updatedAt is not null
          and o.updatedAt >= :start
          and o.updatedAt < :end
        """)
    List<Task> findIndexRecentDeliveryCompletedRange(
            @Param("member") Member member,
            @Param("deliveryDoneStatus") OrderStatus deliveryDoneStatus,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    /**
     * index 메인 "앞으로 7일 처리예정" 발주 전용.
     * 배송희망일이 기간 안에 있고 이미 배송완료/취소된 오더는 제외합니다.
     */
    @Query("""
        select distinct t
        from Task t
        join fetch t.orders o
        left join fetch o.orderItem oi
        left join fetch o.deliveryMethod dm
        left join fetch o.productCategory pc
        left join fetch o.assignedDeliveryHandler adh
        where t.requestedBy = :member
          and o.preferredDeliveryDate is not null
          and o.preferredDeliveryDate >= :start
          and o.preferredDeliveryDate < :end
          and (o.status is null or o.status not in :excludedStatuses)
        """)
    List<Task> findIndexUpcomingDeliveryRange(
            @Param("member") Member member,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("excludedStatuses") List<OrderStatus> excludedStatuses);

    Page<Task> findAllByOrderByIdDesc(Pageable pageable);

    List<Task> findByRequestedBy(Member member);

    @Query("SELECT t FROM Task t WHERE t.requestedBy.company.id = :companyId")
    Page<Task> findByCompanyId(@Param("companyId") Long companyId, Pageable pageable);

    List<Task> findByRequestedByAndCreatedAtBetween(Member member, LocalDateTime start, LocalDateTime end);

    @Query("""
        SELECT t FROM Task t
        WHERE t.requestedBy.company.id = :companyId
          AND (:start IS NULL OR t.createdAt >= :start)
          AND (:end IS NULL OR t.createdAt <= :end)
        """)
    Page<Task> findByCompanyIdAndCreatedAtBetween(
            @Param("companyId") Long companyId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            Pageable pageable
    );
}
