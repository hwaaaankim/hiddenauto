package com.dev.HiddenBATHAuto.enums.calculator;

public enum ProcessPriceRuleType {

    /**
     * 선택형 답변에서 선택한 optionKey별 고정 증감 금액.
     * 예: 손잡이 A +3000, 손잡이 B +5000
     */
    SELECT_OPTION_AMOUNT,

    /**
     * 숫자 입력값 1차원 또는 2차원 범위 테이블 가격.
     * 예: W/D 매트릭스 기본 가격
     */
    NUMBER_RANGE_TABLE,

    /**
     * 숫자 입력값 조건 만족 시 추가/차감 금액.
     * 예: H > 500 이면 +20000
     */
    NUMBER_CONDITION_AMOUNT,

    /**
     * 다른 UNIT 또는 현재 UNIT의 값/금액을 곱해서 계산.
     * 예: 손잡이 단가 * 손잡이 개수
     */
    MULTIPLY_VALUE,

    /**
     * 기준 UNIT의 선택 옵션별 단가와, 다른 숫자 UNIT의 길이 합산값으로 계산.
     * 예: 상판 종류별 마구리 단가 * (W + D + D)
     */
    LINEAR_SUM_BY_OPTION_RATE
}