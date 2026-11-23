package com.dev.HiddenBATHAuto.service.order;

/** 총 발주금액의 1%. 회수 금액도 같은 비율을 적용하고 1P 미만은 절사한다. */
final class OrderRewardPoints {
    private OrderRewardPoints() {}

    static int earned(int amount) {
        return amount / 100;
    }
}
