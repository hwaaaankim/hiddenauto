package com.dev.HiddenBATHAuto.dto.calculator;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.dev.HiddenBATHAuto.enums.calculator.ProcessPriceOperandType;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

public class ProcessPriceRuleDtos {

    @Getter
    @Setter
    @NoArgsConstructor
    public static class SelectOptionAmountRule {
        private List<OptionAmount> optionAmounts = new ArrayList<>();
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class OptionAmount {
        private String optionKey;
        private BigDecimal amount = BigDecimal.ZERO;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class NumberRangeTableRule {
        /**
         * 1개면 1차원, 2개면 2차원.
         */
        private List<Axis> axes = new ArrayList<>();

        /**
         * axes의 rangeKey 조합별 금액.
         */
        private List<Cell> cells = new ArrayList<>();

        /**
         * true면 각 축이 빈 구간 없이 전체 범위를 커버해야 합니다.
         * 전체 범위는 첫 구간 minValue null, 마지막 구간 maxValue null까지 허용합니다.
         */
        private boolean requireFullCoverage = true;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Axis {
        private String axisKey;
        private String unitKey;
        private String fieldKey;
        private List<Range> ranges = new ArrayList<>();
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Range {
        private String rangeKey;
        private BigDecimal minValue;
        private boolean minInclusive = true;
        private BigDecimal maxValue;
        private boolean maxInclusive = true;
        private String label;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Cell {
        private List<String> rangeKeys = new ArrayList<>();
        private BigDecimal amount = BigDecimal.ZERO;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class NumberConditionAmountRule {
        private List<Condition> conditions = new ArrayList<>();
        private BigDecimal amount = BigDecimal.ZERO;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class MultiplyValueRule {
        private List<Condition> conditions = new ArrayList<>();
        private Operand leftOperand;
        private Operand rightOperand;

        /**
         * 최종 결과에 추가 곱할 값.
         * 보통 1.
         */
        private BigDecimal multiplier = BigDecimal.ONE;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class LinearSumByOptionRateRule {
        private List<Condition> conditions = new ArrayList<>();

        /**
         * 단가를 선택하는 기준 UNIT.
         * 예: 상판 UNIT.
         */
        private String rateSourceUnitKey;

        /**
         * rateSourceUnitKey에서 선택된 optionKey별 단가.
         */
        private List<OptionAmount> optionRates = new ArrayList<>();

        /**
         * 길이 합산 항목.
         * 예: W * 1, D * 2
         */
        private List<LengthTerm> lengthTerms = new ArrayList<>();

        /**
         * mm -> m 변환이면 1000.
         */
        private BigDecimal lengthDivisor = BigDecimal.ONE;

        /**
         * 최종 결과 추가 배수.
         */
        private BigDecimal multiplier = BigDecimal.ONE;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class LengthTerm {
        private String unitKey;
        private String fieldKey;
        private BigDecimal multiplier = BigDecimal.ONE;

        /**
         * 특정 선택 답변일 때만 이 길이 항목을 적용하고 싶을 때 사용.
         * 비어 있으면 항상 적용.
         */
        private String requiredUnitKey;
        private String requiredOptionKey;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Operand {
        private ProcessPriceOperandType operandType;

        /**
         * UNIT 참조가 필요한 operand에서 사용.
         * CURRENT 계열이면 null 가능.
         */
        private String unitKey;

        /**
         * 숫자 필드 참조가 필요한 operand에서 사용.
         */
        private String fieldKey;

        /**
         * FIXED일 때 사용.
         */
        private BigDecimal fixedValue;

        /**
         * 선택 옵션별 금액이 필요한 operand에서 사용.
         */
        private List<OptionAmount> optionAmounts = new ArrayList<>();
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Condition {
        /**
         * CURRENT 또는 특정 unitKey.
         */
        private String unitKey;

        /**
         * 숫자 조건이면 fieldKey 사용.
         */
        private String fieldKey;

        /**
         * 선택 조건이면 optionKey 사용.
         */
        private String optionKey;

        /**
         * EQ, NE, GT, GTE, LT, LTE
         */
        private String operator;

        /**
         * 숫자 비교 값.
         */
        private BigDecimal value;
    }
}