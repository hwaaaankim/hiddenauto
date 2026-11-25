package com.dev.HiddenBATHAuto.dto.analytics;

/** Shared definitions for analytics table, graph and report/export columns. */
public enum AnalyticsMetric {
    SALES("sales", "매출액", "원", "저장된 부가세 포함 상품 합계금액입니다. 배송·포장비는 제외하고 회수 건도 저장된 금액 부호를 그대로 사용합니다."),
    TASK_COUNT("taskCount", "전체 건수", "건", "개별 품목(Order)의 전체 건수입니다. 유상 주문·0원 반품용·회수·기타 조정을 포함하고 취소는 제외합니다."),
    QTY("qty", "순수량", "개", "저장 수량의 합계입니다. 양수 수량에서 회수 수량을 뺀 값으로 생산 요청수량과 구별합니다."),
    PAID_COUNT("paidOrderCount", "유상 주문건수", "건", "수량과 상품 금액이 모두 양수인 개별 품목 건수입니다."),
    PAID_QTY("paidQty", "유상 주문수량", "개", "수량과 상품 금액이 모두 양수인 주문 수량입니다."),
    PAID_SALES("paidSales", "유상 상품금액", "원", "유상 주문의 부가세 포함 상품 합계금액입니다."),
    RETURN_COUNT("returnOrderCount", "0원 반품용 건수", "건", "상품 금액이 0원이고 수량이 양수인 반품 대응 제작 품목의 건수입니다."),
    RETURN_QTY("returnQty", "0원 반품용 수량", "개", "상품 금액이 0원이고 수량이 양수인 반품 대응 제작 수량입니다. 생산 요청·완료 수량에 포함합니다."),
    PRODUCTION_REQUEST_QTY("productionRequestQty", "생산 요청수량", "개", "양수 수량 전체입니다. 유상 주문과 0원 반품용을 포함하고 회수는 차감하지 않습니다."),
    PRODUCTION_DONE_QTY("productionDoneQty", "생산 완료수량", "개", "조회한 발주건 중 현재 생산완료·출고완료·배송완료 상태인 양수 수량입니다. 생산 완료일 기준 집계가 아닙니다."),
    PRODUCTION_PENDING_QTY("productionPendingQty", "생산 대기수량", "개", "생산 요청수량에서 생산 완료수량을 뺀 값입니다."),
    COLLECTION_COUNT("collectionOrderCount", "회수건수", "건", "수량이 음수인 개별 품목 건수입니다. 상품 금액이 0원이어도 반품용 건수와 중복 집계하지 않습니다."),
    COLLECTION_QTY("collectionQty", "회수수량", "개", "음수 수량의 절댓값 합계입니다. 생산 요청수량과 별도로 표시합니다."),
    COLLECTION_SALES("collectionSales", "회수건 금액", "원", "회수 건에 저장된 상품 금액을 부호 그대로 합산합니다. 회수라는 이유로 자동으로 매출에서 차감하지 않습니다."),
    ADJUSTMENT_COUNT("adjustmentOrderCount", "기타 조정건수", "건", "수량이 0이거나 양수 수량에 음수 상품 금액이 있는 품목입니다. 유상·반품용·회수와 구별합니다."),
    ADJUSTMENT_QTY("adjustmentQty", "기타 조정수량", "개", "기타 조정 품목의 양수 수량입니다. 생산 요청수량과 유상·0원 반품용 수량의 차이를 확인할 수 있습니다."),
    ADJUSTMENT_SALES("adjustmentSales", "기타 조정금액", "원", "기타 조정 품목의 저장 상품 금액 합계입니다.");

    private final String key;
    private final String label;
    private final String unit;
    private final String help;
    AnalyticsMetric(String key, String label, String unit, String help) { this.key=key; this.label=label; this.unit=unit; this.help=help; }
    public String key() { return key; }
    public String label() { return label; }
    public String unit() { return unit; }
    public String help() { return help; }
    public static AnalyticsMetric forKey(String key) {
        for (AnalyticsMetric metric : values()) if (metric.key.equals(key)) return metric;
        return null;
    }
}
