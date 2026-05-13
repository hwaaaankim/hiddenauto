package com.dev.HiddenBATHAuto.enums.calculator;
public enum ProcessPriceOperandType {

    /**
     * 고정 숫자.
     */
    FIXED,

    /**
     * 현재 UNIT 선택 답변의 옵션별 금액.
     */
    CURRENT_SELECTED_OPTION_AMOUNT,

    /**
     * 현재 UNIT 선택 답변의 valueText를 숫자로 변환한 값.
     */
    CURRENT_SELECTED_OPTION_VALUE_NUMBER,

    /**
     * 현재 UNIT 숫자 필드 값.
     */
    CURRENT_NUMBER_FIELD_VALUE,

    /**
     * 지정 UNIT 선택 답변의 옵션별 금액.
     */
    UNIT_SELECTED_OPTION_AMOUNT,

    /**
     * 지정 UNIT 선택 답변의 valueText를 숫자로 변환한 값.
     */
    UNIT_SELECTED_OPTION_VALUE_NUMBER,

    /**
     * 지정 UNIT 숫자 필드 값.
     */
    UNIT_NUMBER_FIELD_VALUE
}