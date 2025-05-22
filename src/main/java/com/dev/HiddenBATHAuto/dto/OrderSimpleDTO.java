package com.dev.HiddenBATHAuto.dto;

import java.time.LocalDateTime;

import com.dev.HiddenBATHAuto.model.task.OrderStatus;

import lombok.Data;

// 관리자 생산팀 조회용 DTO
@Data
public class OrderSimpleDTO {
    private Long id;
    private String productCategoryName;
    private String address;
    private LocalDateTime preferredDeliveryDate;
    private OrderStatus status;
}
