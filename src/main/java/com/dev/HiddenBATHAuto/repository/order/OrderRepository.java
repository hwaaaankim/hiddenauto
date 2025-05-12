package com.dev.HiddenBATHAuto.repository.order;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.dev.HiddenBATHAuto.model.task.Order;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long>{

}
