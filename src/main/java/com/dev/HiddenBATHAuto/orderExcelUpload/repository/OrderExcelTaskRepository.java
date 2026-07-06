package com.dev.HiddenBATHAuto.orderExcelUpload.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dev.HiddenBATHAuto.model.task.Task;

public interface OrderExcelTaskRepository extends JpaRepository<Task, Long> {
}
