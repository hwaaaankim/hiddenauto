package com.dev.HiddenBATHAuto.dto;

import java.util.Map;

import lombok.Data;

@Data
public class OrderRequestItemDTO {
	private Map<String, Object> optionJson;   // 선택 옵션 전체 JSON string (전체 JSON 그대로)
    private int quantity;        // 수량
    private int price;
}