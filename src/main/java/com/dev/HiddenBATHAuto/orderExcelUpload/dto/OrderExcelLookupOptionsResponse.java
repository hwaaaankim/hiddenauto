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
    private List<OrderExcelOptionDto> middleCategories = new ArrayList<>();
    private Map<String, List<OrderExcelOptionDto>> middleCategoriesByCategory = new LinkedHashMap<>();
    private List<OrderExcelOptionDto> managers = new ArrayList<>();
    private List<OrderExcelOptionDto> deliveryHandlers = new ArrayList<>();
}
