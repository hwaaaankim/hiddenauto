package com.dev.HiddenBATHAuto.dto.production;

import java.util.ArrayList;
import java.util.List;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class StickerPrintDto {

    private Long orderId;

    /**
     * 보고서형 스티커 상단에 표시할 출고일입니다.
     * Order.preferredDeliveryDate를 yyyy-MM-dd 형식으로 변환합니다.
     */
    private String deliveryDateText;

    private String companyName;
    private boolean standard;

    /**
     * OrderItem.optionJson의 "카테고리" 값입니다.
     */
    private String category;

    /**
     * OrderItem.productName 값입니다.
     */
    private String productName;

    /**
     * OrderItem.optionJson의 "색상" 원문 값입니다.
     */
    private String color;

    /**
     * OrderItem.optionJson의 "사이즈" 원문 값입니다.
     */
    private String size;

    private String adminMemo;

    /**
     * MANAGEMENT 타입 이미지 중 첫 번째 이미지의 URL입니다.
     */
    private String adminImageUrl;

    /*
     * 아래 필드는 기존 호출부와의 호환을 위해 유지합니다.
     * 신규 보고서형 스티커 템플릿에서는 사용하지 않습니다.
     */
    private String modelName;
    private String productCode;
    private String colorDisplay;
    private List<String> optionFlags;

    @Builder.Default
    private List<String> debugRawKeys = new ArrayList<>();
}
