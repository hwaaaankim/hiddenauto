package com.dev.HiddenBATHAuto.dto;

import java.time.LocalDate;
import java.util.Map;

import lombok.Data;

@Data
public class OrderRequestItemDTO {
	private int pointUsed;
	private int quantity;
    private int price;
    private int deliveryPrice;
    private String mainAddress;
    private String detailAddress;
    private String zipCode;
    private String doName;
    private String siName;
    private String guName;
    private Map<String, Object> optionJson;
    private Long deliveryMethodId; // ✅ 반드시 Long으로
    private LocalDate preferredDeliveryDate;

}