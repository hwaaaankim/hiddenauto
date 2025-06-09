package com.dev.HiddenBATHAuto.dto;

import java.time.LocalDate;

import lombok.Data;

@Data
public class OrderRequestItemDTO {
	
	private Long cartId;
    private int deliveryPrice;
    private String mainAddress;
    private String detailAddress;
    private String zipCode;
    private String doName;
    private String siName;
    private String guName;
    private Long deliveryMethodId; // ✅ 반드시 Long으로
    private LocalDate preferredDeliveryDate;

}