package com.dev.HiddenBATHAuto.orderExcelUpload.dto;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class OrderExcelLookupOptionsResponse {
    private List<OrderExcelDeliveryMethodOptionResponse> deliveryMethods = new ArrayList<>();
    private List<OrderExcelOptionDto> productionCategories = new ArrayList<>();
    private List<OrderExcelOrderStatusOptionResponse> orderStatuses = new ArrayList<>();
    private List<OrderExcelOptionDto> middleCategories = new ArrayList<>();
    private Map<String, List<OrderExcelOptionDto>> middleCategoriesByCategory = new LinkedHashMap<>();
    private List<OrderExcelOptionDto> managers = new ArrayList<>();
    private List<OrderExcelOptionDto> deliveryHandlers = new ArrayList<>();

    /** 이미지 1개 최대 용량. 프론트 선택 검증과 서버 검증이 같은 값을 사용합니다. */
    private long imageMaxFileSizeBytes;

    /** 저장 대상 이미지 전체 최대 용량. */
    private long imageMaxTotalSizeBytes;
}
