package com.dev.HiddenBATHAuto.orderExcelUpload.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dev.HiddenBATHAuto.model.task.OrderImage;

public interface OrderExcelOrderImageRepository extends JpaRepository<OrderImage, Long> {
}
