package com.dev.HiddenBATHAuto.dto;

import java.util.List;

import lombok.Data;

@Data
public class OrderSubmitRequestDTO {
    private int pointUsed; // 전체 사용 포인트
    private List<OrderRequestItemDTO> items;
}
