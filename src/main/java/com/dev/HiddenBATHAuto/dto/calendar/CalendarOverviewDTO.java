package com.dev.HiddenBATHAuto.dto.calendar;

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * index 메인 달력 오버뷰 응답 DTO.
 *
 * <p>달력에 현재 표시 중인 기간(start inclusive, end exclusive)에 대해
 * 발주/오더와 AS 데이터를 한 번에 내려주기 위한 전용 DTO입니다.</p>
 */
@Data
@NoArgsConstructor
public class CalendarOverviewDTO {

    private String basis;
    private String startDate;
    private String endDate;

    private OrderOverviewDTO order = new OrderOverviewDTO();
    private AsOverviewDTO as = new AsOverviewDTO();

    @Data
    @NoArgsConstructor
    public static class OrderOverviewDTO {
        /** Task(발주서) 개수 */
        private long taskCount;

        /** Task 내부 Order 개수 */
        private long orderCount;

        /** Order.totalAmount 합계 */
        private long totalAmount;

        /** OrderStatus 전체 상태별 집계 */
        private List<CountItemDTO> statusCounts = new ArrayList<>();

        /** OrderItem.optionJson의 "카테고리" 값 기준 */
        private List<CountItemDTO> categoryCounts = new ArrayList<>();

        /** 현장 배송지가 있으면 siteDoName + siteSiName, 없으면 기본 doName + siName 기준 */
        private List<CountItemDTO> regionCounts = new ArrayList<>();

        /** DeliveryMethod.methodName 기준 */
        private List<CountItemDTO> deliveryMethodCounts = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    public static class AsOverviewDTO {
        private long totalCount;

        /** price > 0인 유상 AS 금액 합계 */
        private long totalAmount;

        /** 기존 AS 화면 기준: price == 0은 "무상/미정" */
        private long zeroPriceCount;

        /** price > 0 */
        private long chargedCount;

        /** 유상 건 중 paymentCollected=true 금액 */
        private long collectedAmount;

        /** 유상 건 중 paymentCollected=false 금액 */
        private long uncollectedAmount;

        /** 유상 건 평균 비용 */
        private long averageChargedAmount;

        /** 유상 건 최고 비용 */
        private long maxChargedAmount;

        /** AsStatus 전체 상태별 집계 */
        private List<CountItemDTO> statusCounts = new ArrayList<>();

        /** 제품명 기준 */
        private List<CountItemDTO> productCounts = new ArrayList<>();

        /** doName + siName 기준 */
        private List<CountItemDTO> regionCounts = new ArrayList<>();

        /** AsBillingTarget 한글 라벨 기준 */
        private List<CountItemDTO> billingTargetCounts = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CountItemDTO {
        private String key;
        private String label;
        private long count;
    }
}
