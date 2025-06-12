package com.dev.HiddenBATHAuto.dto;

import lombok.Data;

@Data
public class StandardCalculateRequest {
    private Long productId;
    private Long sizeId; // null 허용
    private Long colorId; // null 허용

    private String tissuePositionName;
    private String dryPositionName;
    private String outletPositionName;
    private String ledPositionName;
    
    private Integer quantity; // ✅ 수량 추가
}