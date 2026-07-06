package com.dev.HiddenBATHAuto.orderExcelUpload.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dev.HiddenBATHAuto.model.task.Order;

public interface OrderExcelOrderRepository extends JpaRepository<Order, Long> {
}
