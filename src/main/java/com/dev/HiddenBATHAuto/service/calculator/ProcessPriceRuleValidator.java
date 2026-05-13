package com.dev.HiddenBATHAuto.service.calculator;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.dev.HiddenBATHAuto.dto.calculator.ProcessPriceRuleDtos.Axis;
import com.dev.HiddenBATHAuto.dto.calculator.ProcessPriceRuleDtos.Cell;
import com.dev.HiddenBATHAuto.dto.calculator.ProcessPriceRuleDtos.Condition;
import com.dev.HiddenBATHAuto.dto.calculator.ProcessPriceRuleDtos.LengthTerm;
import com.dev.HiddenBATHAuto.dto.calculator.ProcessPriceRuleDtos.LinearSumByOptionRateRule;
import com.dev.HiddenBATHAuto.dto.calculator.ProcessPriceRuleDtos.MultiplyValueRule;
import com.dev.HiddenBATHAuto.dto.calculator.ProcessPriceRuleDtos.NumberConditionAmountRule;
import com.dev.HiddenBATHAuto.dto.calculator.ProcessPriceRuleDtos.NumberRangeTableRule;
import com.dev.HiddenBATHAuto.dto.calculator.ProcessPriceRuleDtos.Operand;
import com.dev.HiddenBATHAuto.dto.calculator.ProcessPriceRuleDtos.OptionAmount;
import com.dev.HiddenBATHAuto.dto.calculator.ProcessPriceRuleDtos.Range;
import com.dev.HiddenBATHAuto.dto.calculator.ProcessPriceRuleDtos.SelectOptionAmountRule;
import com.dev.HiddenBATHAuto.dto.process.ProcessMakerDtos.AnswerFieldDto;
import com.dev.HiddenBATHAuto.dto.process.ProcessMakerDtos.AnswerOptionDto;
import com.dev.HiddenBATHAuto.dto.process.ProcessMakerDtos.PriceRuleDto;
import com.dev.HiddenBATHAuto.dto.process.ProcessMakerDtos.ProcessDetailRequest;
import com.dev.HiddenBATHAuto.dto.process.ProcessMakerDtos.QuestionDto;
import com.dev.HiddenBATHAuto.dto.process.ProcessMakerDtos.StepDto;
import com.dev.HiddenBATHAuto.dto.process.ProcessMakerDtos.UnitDto;
import com.dev.HiddenBATHAuto.enums.calculator.ProcessPriceOperandType;
import com.dev.HiddenBATHAuto.enums.calculator.ProcessPriceRuleType;
import com.dev.HiddenBATHAuto.enums.process.ProcessAnswerType;
import com.dev.HiddenBATHAuto.enums.process.ProcessInputValueType;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ProcessPriceRuleValidator {

    private final ObjectMapper objectMapper;

    public void validate(ProcessDetailRequest request) {
        if (request == null || request.getSteps() == null) {
            return;
        }

        PriceValidationContext context = PriceValidationContext.from(request);

        for (StepDto step : request.getSteps()) {
            if (step.getUnits() == null) {
                continue;
            }

            for (UnitDto unit : step.getUnits()) {
                validateUnitPriceRules(context, unit);
            }
        }
    }

    private void validateUnitPriceRules(PriceValidationContext context, UnitDto unit) {
        List<PriceRuleDto> rules = unit.getPriceRules() == null ? List.of() : unit.getPriceRules();

        if (rules.isEmpty()) {
            return;
        }

        QuestionDto question = unit.getQuestion();
        if (question == null) {
            throw new IllegalArgumentException(unitTitle(unit) + ": 가격 규칙을 등록하려면 질문 정보가 필요합니다.");
        }

        ProcessAnswerType answerType = normalizeAnswerType(question.getAnswerType());

        if (answerType != ProcessAnswerType.SINGLE_SELECT && answerType != ProcessAnswerType.NUMBER_INPUT) {
            throw new IllegalArgumentException(unitTitle(unit) + ": 가격 규칙은 선택형 또는 숫자 입력형 답변에만 등록할 수 있습니다.");
        }

        HashSet<String> ruleKeys = new HashSet<>();

        for (int i = 0; i < rules.size(); i++) {
            PriceRuleDto rule = rules.get(i);

            if (isBlank(rule.getRuleKey())) {
                throw new IllegalArgumentException(unitTitle(unit) + ": 가격 규칙 key가 비어있습니다.");
            }

            if (!ruleKeys.add(rule.getRuleKey())) {
                throw new IllegalArgumentException(unitTitle(unit) + ": 가격 규칙 key가 중복되었습니다: " + rule.getRuleKey());
            }

            if (rule.getRuleType() == null) {
                throw new IllegalArgumentException(unitTitle(unit) + ": 가격 규칙 타입이 없습니다.");
            }

            if (isBlank(rule.getRuleJson())) {
                throw new IllegalArgumentException(unitTitle(unit) + ": 가격 규칙 JSON이 비어있습니다.");
            }

            validateRuleByType(context, unit, answerType, rule);
        }
    }

    private void validateRuleByType(
            PriceValidationContext context,
            UnitDto unit,
            ProcessAnswerType answerType,
            PriceRuleDto rule
    ) {
        try {
            if (rule.getRuleType() == ProcessPriceRuleType.SELECT_OPTION_AMOUNT) {
                requireAnswerType(unit, answerType, ProcessAnswerType.SINGLE_SELECT, rule);
                SelectOptionAmountRule parsed = objectMapper.readValue(rule.getRuleJson(), SelectOptionAmountRule.class);
                validateSelectOptionAmount(context, unit, parsed);
                return;
            }

            if (rule.getRuleType() == ProcessPriceRuleType.NUMBER_RANGE_TABLE) {
                requireAnswerType(unit, answerType, ProcessAnswerType.NUMBER_INPUT, rule);
                NumberRangeTableRule parsed = objectMapper.readValue(rule.getRuleJson(), NumberRangeTableRule.class);
                validateNumberRangeTable(context, unit, parsed);
                return;
            }

            if (rule.getRuleType() == ProcessPriceRuleType.NUMBER_CONDITION_AMOUNT) {
                requireAnswerType(unit, answerType, ProcessAnswerType.NUMBER_INPUT, rule);
                NumberConditionAmountRule parsed = objectMapper.readValue(rule.getRuleJson(), NumberConditionAmountRule.class);
                validateNumberConditionAmount(context, unit, parsed);
                return;
            }

            if (rule.getRuleType() == ProcessPriceRuleType.MULTIPLY_VALUE) {
                MultiplyValueRule parsed = objectMapper.readValue(rule.getRuleJson(), MultiplyValueRule.class);
                validateMultiplyValue(context, unit, parsed);
                return;
            }

            if (rule.getRuleType() == ProcessPriceRuleType.LINEAR_SUM_BY_OPTION_RATE) {
                LinearSumByOptionRateRule parsed = objectMapper.readValue(rule.getRuleJson(), LinearSumByOptionRateRule.class);
                validateLinearSumByOptionRate(context, unit, parsed);
                return;
            }

            throw new IllegalArgumentException(unitTitle(unit) + ": 지원하지 않는 가격 규칙 타입입니다: " + rule.getRuleType());
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException(unitTitle(unit) + ": 가격 규칙 JSON 형식이 올바르지 않습니다. [" + rule.getRuleName() + "]");
        }
    }

    private void validateSelectOptionAmount(
            PriceValidationContext context,
            UnitDto unit,
            SelectOptionAmountRule rule
    ) {
        List<OptionAmount> optionAmounts = rule.getOptionAmounts() == null ? List.of() : rule.getOptionAmounts();

        if (optionAmounts.isEmpty()) {
            throw new IllegalArgumentException(unitTitle(unit) + ": 선택형 가격 규칙은 옵션별 금액이 최소 1개 필요합니다.");
        }

        HashSet<String> used = new HashSet<>();

        for (OptionAmount item : optionAmounts) {
            if (isBlank(item.getOptionKey())) {
                throw new IllegalArgumentException(unitTitle(unit) + ": 선택형 가격 규칙의 optionKey가 비어있습니다.");
            }

            if (!context.hasOption(unit.getUnitKey(), item.getOptionKey())) {
                throw new IllegalArgumentException(unitTitle(unit) + ": 존재하지 않는 선택 옵션에 가격이 설정되었습니다: " + item.getOptionKey());
            }

            if (!used.add(item.getOptionKey())) {
                throw new IllegalArgumentException(unitTitle(unit) + ": 동일 선택 옵션의 가격이 중복 설정되었습니다: " + item.getOptionKey());
            }

            if (item.getAmount() == null) {
                throw new IllegalArgumentException(unitTitle(unit) + ": 선택 옵션 가격은 null일 수 없습니다.");
            }
        }
    }

    private void validateNumberRangeTable(
            PriceValidationContext context,
            UnitDto unit,
            NumberRangeTableRule rule
    ) {
        List<Axis> axes = rule.getAxes() == null ? List.of() : rule.getAxes();

        if (axes.size() < 1 || axes.size() > 2) {
            throw new IllegalArgumentException(unitTitle(unit) + ": 숫자 범위 테이블은 1차원 또는 2차원만 가능합니다.");
        }

        HashSet<String> axisKeys = new HashSet<>();
        HashSet<String> allRangeKeys = new HashSet<>();

        for (Axis axis : axes) {
            if (isBlank(axis.getAxisKey())) {
                throw new IllegalArgumentException(unitTitle(unit) + ": 가격 테이블 축 axisKey가 비어있습니다.");
            }

            if (!axisKeys.add(axis.getAxisKey())) {
                throw new IllegalArgumentException(unitTitle(unit) + ": 가격 테이블 축 axisKey가 중복되었습니다: " + axis.getAxisKey());
            }

            String targetUnitKey = normalizeUnitKey(unit, axis.getUnitKey());

            if (!context.hasNumberField(targetUnitKey, axis.getFieldKey())) {
                throw new IllegalArgumentException(unitTitle(unit) + ": 가격 테이블 축에 존재하지 않는 숫자 필드가 사용되었습니다: " + targetUnitKey + "." + axis.getFieldKey());
            }

            validateRanges(unit, axis.getRanges(), rule.isRequireFullCoverage());

            for (Range range : axis.getRanges()) {
                if (!allRangeKeys.add(range.getRangeKey())) {
                    throw new IllegalArgumentException(unitTitle(unit) + ": 가격 테이블 rangeKey가 중복되었습니다: " + range.getRangeKey());
                }
            }
        }

        List<Cell> cells = rule.getCells() == null ? List.of() : rule.getCells();

        if (cells.isEmpty()) {
            throw new IllegalArgumentException(unitTitle(unit) + ": 가격 테이블 cell 금액이 필요합니다.");
        }

        HashSet<String> cellKeys = new HashSet<>();

        for (Cell cell : cells) {
            if (cell.getRangeKeys() == null || cell.getRangeKeys().size() != axes.size()) {
                throw new IllegalArgumentException(unitTitle(unit) + ": 가격 테이블 cell의 rangeKey 개수가 축 개수와 맞지 않습니다.");
            }

            for (String rangeKey : cell.getRangeKeys()) {
                if (!allRangeKeys.contains(rangeKey)) {
                    throw new IllegalArgumentException(unitTitle(unit) + ": 가격 테이블 cell에 존재하지 않는 rangeKey가 있습니다: " + rangeKey);
                }
            }

            String key = String.join("|", cell.getRangeKeys());
            if (!cellKeys.add(key)) {
                throw new IllegalArgumentException(unitTitle(unit) + ": 가격 테이블 cell 조합이 중복되었습니다: " + key);
            }

            if (cell.getAmount() == null) {
                throw new IllegalArgumentException(unitTitle(unit) + ": 가격 테이블 cell 금액은 null일 수 없습니다.");
            }
        }
    }

    private void validateNumberConditionAmount(
            PriceValidationContext context,
            UnitDto unit,
            NumberConditionAmountRule rule
    ) {
        if (rule.getAmount() == null) {
            throw new IllegalArgumentException(unitTitle(unit) + ": 숫자 조건 가격의 amount가 필요합니다.");
        }

        validateConditions(context, unit, rule.getConditions(), true);
    }

    private void validateMultiplyValue(
            PriceValidationContext context,
            UnitDto unit,
            MultiplyValueRule rule
    ) {
        validateConditions(context, unit, rule.getConditions(), false);
        validateOperand(context, unit, rule.getLeftOperand());
        validateOperand(context, unit, rule.getRightOperand());

        if (rule.getMultiplier() == null) {
            throw new IllegalArgumentException(unitTitle(unit) + ": 곱셈 가격 규칙의 multiplier가 필요합니다.");
        }
    }

    private void validateLinearSumByOptionRate(
            PriceValidationContext context,
            UnitDto unit,
            LinearSumByOptionRateRule rule
    ) {
        validateConditions(context, unit, rule.getConditions(), false);

        if (isBlank(rule.getRateSourceUnitKey())) {
            throw new IllegalArgumentException(unitTitle(unit) + ": 옵션별 단가 기준 UNIT이 필요합니다.");
        }

        context.requirePreviousOrCurrentUnit(unit.getUnitKey(), rule.getRateSourceUnitKey(), unitTitle(unit));

        if (!context.isSingleSelectUnit(rule.getRateSourceUnitKey())) {
            throw new IllegalArgumentException(unitTitle(unit) + ": 옵션별 단가 기준 UNIT은 선택형 답변이어야 합니다.");
        }

        if (rule.getOptionRates() == null || rule.getOptionRates().isEmpty()) {
            throw new IllegalArgumentException(unitTitle(unit) + ": 옵션별 단가 목록이 필요합니다.");
        }

        HashSet<String> rateOptionKeys = new HashSet<>();

        for (OptionAmount optionRate : rule.getOptionRates()) {
            if (isBlank(optionRate.getOptionKey())) {
                throw new IllegalArgumentException(unitTitle(unit) + ": 옵션별 단가 optionKey가 비어있습니다.");
            }

            if (!context.hasOption(rule.getRateSourceUnitKey(), optionRate.getOptionKey())) {
                throw new IllegalArgumentException(unitTitle(unit) + ": 옵션별 단가 기준 UNIT에 존재하지 않는 optionKey입니다: " + optionRate.getOptionKey());
            }

            if (!rateOptionKeys.add(optionRate.getOptionKey())) {
                throw new IllegalArgumentException(unitTitle(unit) + ": 옵션별 단가 optionKey가 중복되었습니다: " + optionRate.getOptionKey());
            }

            if (optionRate.getAmount() == null) {
                throw new IllegalArgumentException(unitTitle(unit) + ": 옵션별 단가는 null일 수 없습니다.");
            }
        }

        if (rule.getLengthTerms() == null || rule.getLengthTerms().isEmpty()) {
            throw new IllegalArgumentException(unitTitle(unit) + ": 길이 합산 항목이 필요합니다.");
        }

        for (LengthTerm term : rule.getLengthTerms()) {
            if (isBlank(term.getUnitKey()) || isBlank(term.getFieldKey())) {
                throw new IllegalArgumentException(unitTitle(unit) + ": 길이 합산 항목의 unitKey/fieldKey가 필요합니다.");
            }

            context.requirePreviousOrCurrentUnit(unit.getUnitKey(), term.getUnitKey(), unitTitle(unit));

            if (!context.hasNumberField(term.getUnitKey(), term.getFieldKey())) {
                throw new IllegalArgumentException(unitTitle(unit) + ": 길이 합산 항목에 존재하지 않는 숫자 필드가 사용되었습니다: " + term.getUnitKey() + "." + term.getFieldKey());
            }

            if (term.getMultiplier() == null) {
                throw new IllegalArgumentException(unitTitle(unit) + ": 길이 합산 multiplier가 필요합니다.");
            }

            if (!isBlank(term.getRequiredUnitKey()) || !isBlank(term.getRequiredOptionKey())) {
                if (isBlank(term.getRequiredUnitKey()) || isBlank(term.getRequiredOptionKey())) {
                    throw new IllegalArgumentException(unitTitle(unit) + ": 길이 합산 조건은 requiredUnitKey와 requiredOptionKey가 함께 필요합니다.");
                }

                context.requirePreviousOrCurrentUnit(unit.getUnitKey(), term.getRequiredUnitKey(), unitTitle(unit));

                if (!context.hasOption(term.getRequiredUnitKey(), term.getRequiredOptionKey())) {
                    throw new IllegalArgumentException(unitTitle(unit) + ": 길이 합산 조건 optionKey가 존재하지 않습니다: " + term.getRequiredUnitKey() + "." + term.getRequiredOptionKey());
                }
            }
        }

        if (rule.getLengthDivisor() == null || BigDecimal.ZERO.compareTo(rule.getLengthDivisor()) == 0) {
            throw new IllegalArgumentException(unitTitle(unit) + ": lengthDivisor는 0일 수 없습니다.");
        }

        if (rule.getMultiplier() == null) {
            throw new IllegalArgumentException(unitTitle(unit) + ": multiplier가 필요합니다.");
        }
    }

    private void validateOperand(PriceValidationContext context, UnitDto currentUnit, Operand operand) {
        if (operand == null || operand.getOperandType() == null) {
            throw new IllegalArgumentException(unitTitle(currentUnit) + ": 곱셈 가격 규칙 operand가 필요합니다.");
        }

        ProcessPriceOperandType type = operand.getOperandType();

        if (type == ProcessPriceOperandType.FIXED) {
            if (operand.getFixedValue() == null) {
                throw new IllegalArgumentException(unitTitle(currentUnit) + ": FIXED operand는 fixedValue가 필요합니다.");
            }
            return;
        }

        if (type == ProcessPriceOperandType.CURRENT_SELECTED_OPTION_AMOUNT
                || type == ProcessPriceOperandType.CURRENT_SELECTED_OPTION_VALUE_NUMBER) {
            if (!context.isSingleSelectUnit(currentUnit.getUnitKey())) {
                throw new IllegalArgumentException(unitTitle(currentUnit) + ": 현재 UNIT은 선택형 답변이 아니므로 선택형 operand를 사용할 수 없습니다.");
            }
            return;
        }

        if (type == ProcessPriceOperandType.CURRENT_NUMBER_FIELD_VALUE) {
            if (!context.hasNumberField(currentUnit.getUnitKey(), operand.getFieldKey())) {
                throw new IllegalArgumentException(unitTitle(currentUnit) + ": 현재 UNIT 숫자 필드 operand가 올바르지 않습니다: " + operand.getFieldKey());
            }
            return;
        }

        if (type == ProcessPriceOperandType.UNIT_SELECTED_OPTION_AMOUNT
                || type == ProcessPriceOperandType.UNIT_SELECTED_OPTION_VALUE_NUMBER) {
            if (isBlank(operand.getUnitKey())) {
                throw new IllegalArgumentException(unitTitle(currentUnit) + ": UNIT 선택형 operand는 unitKey가 필요합니다.");
            }

            context.requirePreviousOrCurrentUnit(currentUnit.getUnitKey(), operand.getUnitKey(), unitTitle(currentUnit));

            if (!context.isSingleSelectUnit(operand.getUnitKey())) {
                throw new IllegalArgumentException(unitTitle(currentUnit) + ": 참조 UNIT이 선택형 답변이 아닙니다: " + operand.getUnitKey());
            }
            return;
        }

        if (type == ProcessPriceOperandType.UNIT_NUMBER_FIELD_VALUE) {
            if (isBlank(operand.getUnitKey()) || isBlank(operand.getFieldKey())) {
                throw new IllegalArgumentException(unitTitle(currentUnit) + ": UNIT 숫자 필드 operand는 unitKey/fieldKey가 필요합니다.");
            }

            context.requirePreviousOrCurrentUnit(currentUnit.getUnitKey(), operand.getUnitKey(), unitTitle(currentUnit));

            if (!context.hasNumberField(operand.getUnitKey(), operand.getFieldKey())) {
                throw new IllegalArgumentException(unitTitle(currentUnit) + ": 참조 UNIT 숫자 필드 operand가 올바르지 않습니다: " + operand.getUnitKey() + "." + operand.getFieldKey());
            }
        }
    }

    private void validateConditions(
            PriceValidationContext context,
            UnitDto currentUnit,
            List<Condition> conditions,
            boolean requireAtLeastOne
    ) {
        List<Condition> safeConditions = conditions == null ? List.of() : conditions;

        if (requireAtLeastOne && safeConditions.isEmpty()) {
            throw new IllegalArgumentException(unitTitle(currentUnit) + ": 조건 가격 규칙은 조건이 최소 1개 필요합니다.");
        }

        for (Condition condition : safeConditions) {
            String targetUnitKey = normalizeUnitKey(currentUnit, condition.getUnitKey());

            context.requirePreviousOrCurrentUnit(currentUnit.getUnitKey(), targetUnitKey, unitTitle(currentUnit));

            boolean hasField = !isBlank(condition.getFieldKey());
            boolean hasOption = !isBlank(condition.getOptionKey());

            if (hasField == hasOption) {
                throw new IllegalArgumentException(unitTitle(currentUnit) + ": 조건은 fieldKey 또는 optionKey 중 하나만 가져야 합니다.");
            }

            if (hasField) {
                if (!context.hasNumberField(targetUnitKey, condition.getFieldKey())) {
                    throw new IllegalArgumentException(unitTitle(currentUnit) + ": 조건에 존재하지 않는 숫자 필드가 사용되었습니다: " + targetUnitKey + "." + condition.getFieldKey());
                }

                if (isBlank(condition.getOperator())) {
                    throw new IllegalArgumentException(unitTitle(currentUnit) + ": 숫자 조건 operator가 필요합니다.");
                }

                if (condition.getValue() == null) {
                    throw new IllegalArgumentException(unitTitle(currentUnit) + ": 숫자 조건 value가 필요합니다.");
                }
            }

            if (hasOption) {
                if (!context.hasOption(targetUnitKey, condition.getOptionKey())) {
                    throw new IllegalArgumentException(unitTitle(currentUnit) + ": 조건에 존재하지 않는 optionKey가 사용되었습니다: " + targetUnitKey + "." + condition.getOptionKey());
                }

                String operator = condition.getOperator();
                if (!"EQ".equals(operator) && !"NE".equals(operator)) {
                    throw new IllegalArgumentException(unitTitle(currentUnit) + ": 선택 조건 operator는 EQ 또는 NE만 가능합니다.");
                }
            }
        }
    }

    private void validateRanges(UnitDto unit, List<Range> ranges, boolean requireFullCoverage) {
        if (ranges == null || ranges.isEmpty()) {
            throw new IllegalArgumentException(unitTitle(unit) + ": 가격 테이블 범위가 필요합니다.");
        }

        HashSet<String> rangeKeys = new HashSet<>();

        for (Range range : ranges) {
            if (isBlank(range.getRangeKey())) {
                throw new IllegalArgumentException(unitTitle(unit) + ": 가격 테이블 rangeKey가 비어있습니다.");
            }

            if (!rangeKeys.add(range.getRangeKey())) {
                throw new IllegalArgumentException(unitTitle(unit) + ": 가격 테이블 rangeKey가 중복되었습니다: " + range.getRangeKey());
            }

            if (range.getMinValue() != null && range.getMaxValue() != null
                    && range.getMinValue().compareTo(range.getMaxValue()) > 0) {
                throw new IllegalArgumentException(unitTitle(unit) + ": 가격 테이블 범위 시작값이 종료값보다 클 수 없습니다.");
            }
        }

        List<Range> sorted = new ArrayList<>(ranges);
        sorted.sort(Comparator.comparing(
                Range::getMinValue,
                Comparator.nullsFirst(BigDecimal::compareTo)
        ));

        if (requireFullCoverage && sorted.get(0).getMinValue() != null) {
            throw new IllegalArgumentException(unitTitle(unit) + ": 가격 테이블 첫 범위는 최소값 없이 시작해야 전체 범위를 커버할 수 있습니다.");
        }

        if (requireFullCoverage && sorted.get(sorted.size() - 1).getMaxValue() != null) {
            throw new IllegalArgumentException(unitTitle(unit) + ": 가격 테이블 마지막 범위는 최대값 없이 끝나야 전체 범위를 커버할 수 있습니다.");
        }

        for (int i = 0; i < sorted.size() - 1; i++) {
            Range current = sorted.get(i);
            Range next = sorted.get(i + 1);

            if (current.getMaxValue() == null) {
                throw new IllegalArgumentException(unitTitle(unit) + ": 가격 테이블 범위가 겹칩니다. 무한 종료 범위 뒤에 다른 범위가 있습니다.");
            }

            if (next.getMinValue() == null) {
                throw new IllegalArgumentException(unitTitle(unit) + ": 가격 테이블 범위가 겹칩니다. 무한 시작 범위가 중간에 있습니다.");
            }

            int compare = current.getMaxValue().compareTo(next.getMinValue());

            if (compare > 0) {
                throw new IllegalArgumentException(unitTitle(unit) + ": 가격 테이블 범위가 겹칩니다.");
            }

            if (compare == 0 && current.isMaxInclusive() && next.isMinInclusive()) {
                throw new IllegalArgumentException(unitTitle(unit) + ": 가격 테이블 경계값이 중복 포함됩니다: " + current.getMaxValue());
            }

            if (requireFullCoverage && compare < 0) {
                throw new IllegalArgumentException(unitTitle(unit) + ": 가격 테이블 범위 사이에 빈 구간이 있습니다: "
                        + current.getMaxValue() + " ~ " + next.getMinValue());
            }

            if (requireFullCoverage && compare == 0 && !current.isMaxInclusive() && !next.isMinInclusive()) {
                throw new IllegalArgumentException(unitTitle(unit) + ": 가격 테이블 경계값이 양쪽에서 모두 제외됩니다: " + current.getMaxValue());
            }
        }
    }

    private void requireAnswerType(
            UnitDto unit,
            ProcessAnswerType actual,
            ProcessAnswerType required,
            PriceRuleDto rule
    ) {
        if (actual != required) {
            throw new IllegalArgumentException(unitTitle(unit) + ": " + rule.getRuleName() + " 규칙은 "
                    + required + " 답변에서만 사용할 수 있습니다.");
        }
    }

    private ProcessAnswerType normalizeAnswerType(ProcessAnswerType answerType) {
        if (answerType == null) {
            return ProcessAnswerType.SINGLE_SELECT;
        }

        if (answerType == ProcessAnswerType.MULTI_INPUT) {
            return ProcessAnswerType.NUMBER_INPUT;
        }

        return answerType;
    }

    private String normalizeUnitKey(UnitDto currentUnit, String unitKey) {
        if (isBlank(unitKey) || "CURRENT".equals(unitKey)) {
            return currentUnit.getUnitKey();
        }

        return unitKey;
    }

    private String unitTitle(UnitDto unit) {
        if (unit == null) {
            return "UNIT";
        }

        if (!isBlank(unit.getTitle())) {
            return unit.getTitle();
        }

        return isBlank(unit.getUnitKey()) ? "UNIT" : unit.getUnitKey();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static class PriceValidationContext {
        private final Map<String, UnitMeta> unitMap = new HashMap<>();
        private final Map<String, Integer> orderMap = new HashMap<>();

        static PriceValidationContext from(ProcessDetailRequest request) {
            PriceValidationContext context = new PriceValidationContext();
            int order = 0;

            for (StepDto step : request.getSteps()) {
                if (step.getUnits() == null) {
                    continue;
                }

                for (UnitDto unit : step.getUnits()) {
                    UnitMeta meta = UnitMeta.from(unit);
                    context.unitMap.put(unit.getUnitKey(), meta);
                    context.orderMap.put(unit.getUnitKey(), order++);
                }
            }

            return context;
        }

        boolean isSingleSelectUnit(String unitKey) {
            UnitMeta meta = unitMap.get(unitKey);
            return meta != null && meta.answerType == ProcessAnswerType.SINGLE_SELECT;
        }

        boolean hasOption(String unitKey, String optionKey) {
            UnitMeta meta = unitMap.get(unitKey);
            return meta != null && meta.optionKeys.contains(optionKey);
        }

        boolean hasNumberField(String unitKey, String fieldKey) {
            UnitMeta meta = unitMap.get(unitKey);
            return meta != null && meta.numberFieldKeys.contains(fieldKey);
        }

        void requirePreviousOrCurrentUnit(String currentUnitKey, String targetUnitKey, String unitTitle) {
            if (!unitMap.containsKey(targetUnitKey)) {
                throw new IllegalArgumentException(unitTitle + ": 참조 UNIT을 찾을 수 없습니다: " + targetUnitKey);
            }

            Integer currentOrder = orderMap.get(currentUnitKey);
            Integer targetOrder = orderMap.get(targetUnitKey);

            if (currentOrder == null || targetOrder == null) {
                throw new IllegalArgumentException(unitTitle + ": UNIT 순서를 확인할 수 없습니다.");
            }

            if (targetOrder > currentOrder) {
                throw new IllegalArgumentException(unitTitle + ": 가격 규칙은 현재 UNIT보다 뒤에 있는 UNIT을 참조할 수 없습니다: " + targetUnitKey);
            }
        }
    }

    private static class UnitMeta {
        private ProcessAnswerType answerType;
        private final HashSet<String> optionKeys = new HashSet<>();
        private final HashSet<String> numberFieldKeys = new HashSet<>();

        static UnitMeta from(UnitDto unit) {
            UnitMeta meta = new UnitMeta();

            QuestionDto question = unit.getQuestion();

            ProcessAnswerType type = question == null || question.getAnswerType() == null
                    ? ProcessAnswerType.SINGLE_SELECT
                    : question.getAnswerType();

            if (type == ProcessAnswerType.MULTI_INPUT) {
                type = ProcessAnswerType.NUMBER_INPUT;
            }

            meta.answerType = type;

            if (question != null && question.getOptions() != null) {
                for (AnswerOptionDto option : question.getOptions()) {
                    if (option.getOptionKey() != null && !option.getOptionKey().isBlank()) {
                        meta.optionKeys.add(option.getOptionKey());
                    }
                }
            }

            if (question != null && question.getFields() != null) {
                for (AnswerFieldDto field : question.getFields()) {
                    if (field.getInputValueType() == ProcessInputValueType.NUMBER
                            && field.getFieldKey() != null
                            && !field.getFieldKey().isBlank()) {
                        meta.numberFieldKeys.add(field.getFieldKey());
                    }
                }
            }

            return meta;
        }
    }
}