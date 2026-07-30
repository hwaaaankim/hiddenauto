package com.dev.HiddenBATHAuto.repository.order;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dev.HiddenBATHAuto.enums.order.OrderWorkArea;
import com.dev.HiddenBATHAuto.model.task.audit.OrderMemberCheckStatus;

import jakarta.persistence.LockModeType;

public interface OrderMemberCheckStatusRepository extends JpaRepository<OrderMemberCheckStatus, Long> {

    List<OrderMemberCheckStatus> findByOrder_IdInAndMember_IdAndWorkArea(
            Collection<Long> orderIds,
            Long memberId,
            OrderWorkArea workArea
    );

    List<OrderMemberCheckStatus> findByOrder_IdInAndWorkArea(
            Collection<Long> orderIds,
            OrderWorkArea workArea
    );

    @Query("""
            select status
            from OrderMemberCheckStatus status
            join fetch status.member member
            where status.order.id = :orderId
              and status.workArea = :workArea
            order by member.name asc, member.username asc
            """)
    List<OrderMemberCheckStatus> findMemberStates(
            @Param("orderId") Long orderId,
            @Param("workArea") OrderWorkArea workArea
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select status
            from OrderMemberCheckStatus status
            join fetch status.member member
            where status.order.id = :orderId
              and member.id = :memberId
              and status.workArea = :workArea
            """)
    Optional<OrderMemberCheckStatus> findForUpdate(
            @Param("orderId") Long orderId,
            @Param("memberId") Long memberId,
            @Param("workArea") OrderWorkArea workArea
    );
}
