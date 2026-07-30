package com.dev.HiddenBATHAuto.repository.order;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dev.HiddenBATHAuto.enums.order.OrderWorkArea;
import com.dev.HiddenBATHAuto.model.task.audit.OrderWorkRevision;

import jakarta.persistence.LockModeType;

public interface OrderWorkRevisionRepository extends JpaRepository<OrderWorkRevision, Long> {

    Optional<OrderWorkRevision> findByOrder_IdAndWorkArea(Long orderId, OrderWorkArea workArea);

    List<OrderWorkRevision> findByOrder_IdInAndWorkArea(Collection<Long> orderIds, OrderWorkArea workArea);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select revision
            from OrderWorkRevision revision
            where revision.order.id = :orderId
              and revision.workArea = :workArea
            """)
    Optional<OrderWorkRevision> findForUpdate(
            @Param("orderId") Long orderId,
            @Param("workArea") OrderWorkArea workArea
    );
}
