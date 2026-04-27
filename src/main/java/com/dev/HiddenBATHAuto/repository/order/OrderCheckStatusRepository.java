package com.dev.HiddenBATHAuto.repository.order;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dev.HiddenBATHAuto.model.task.OrderCheckStatus;

public interface OrderCheckStatusRepository extends JpaRepository<OrderCheckStatus, Long> {

    Optional<OrderCheckStatus> findByOrder_Id(Long orderId);

    List<OrderCheckStatus> findByOrder_IdIn(Collection<Long> orderIds);
}