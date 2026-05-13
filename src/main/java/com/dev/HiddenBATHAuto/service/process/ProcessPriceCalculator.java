package com.dev.HiddenBATHAuto.service.process;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
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
import com.dev.HiddenBATHAuto.enums.calculator.ProcessPriceOperandType;
import com.dev.HiddenBATHAuto.enums.calculator.ProcessPriceRuleType;
import com.dev.HiddenBATHAuto.model.calculator.ProcessUnitPriceRule;
import com.dev.HiddenBATHAuto.model.process.ProcessAnswerOption;
import com.dev.HiddenBATHAuto.model.process.ProcessDefinition;
import com.dev.HiddenBATHAuto.model.process.ProcessExecutionAnswer;
import com.dev.HiddenBATHAuto.model.process.ProcessExecutionSession;
import com.dev.HiddenBATHAuto.model.process.ProcessStep;
import com.dev.HiddenBATHAuto.model.process.ProcessUnit;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ProcessPriceCalculator {

    private final ObjectMapper objectMapper;

    /**
     * 기존 호출부 호환용입니다.
     * 가격 규칙은 ProcessExecutionAnswer만으로는 계산할 수 없으므로 실제 계산에는 사용하지 않습니다.
     */
    public PriceCalculationResult calculate(List<ProcessExecutionAnswer> answers) {
        try {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("message", "ProcessDefinition 없이 답변 목록만 전달되어 가격 규칙 계산을 수행할 수 없습니다.");
            detail.put("answerCount", answers == null ? 0 : answers.size());
            detail.put("totalAmount", BigDecimal.ZERO);
            detail.put("rules", List.of());

            return new PriceCalculationResult(BigDecimal.ZERO, objectMapper.writeValueAsString(detail));
        } catch (Exception e) {
            return new PriceCalculationResult(BigDecimal.ZERO, "{\"message\":\"가격 계산 상세 생성 실패\"}");
        }
    }

    public PriceCalculationResult calculate(ProcessExecutionSession session) {
        List<ProcessExecutionAnswer> answers = session == null || session.getAnswers() == null
                ? List.of()
                : session.getAnswers();

        return calculate(session, answers);
    }

    public PriceCalculationResult calculate(ProcessExecutionSession session, List<ProcessExecutionAnswer> answers) {
        try {
            if (session == null || session.getProcess() == null) {
                return emptyResult("세션 또는 프로세스 정보가 없습니다.");
            }

            PriceContext context = PriceContext.from(session, answers, objectMapper);

            BigDecimal totalAmount = BigDecimal.ZERO;
            List<Map<String, Object>> ruleResults = new ArrayList<>();

            List<ProcessUnit> units = collectUnits(session.getProcess());

            for (ProcessUnit unit : units) {
                List<ProcessUnitPriceRule> priceRules = unit.getPriceRules() == null
                        ? List.of()
                        : unit.getPriceRules()
                                .stream()
                                .filter(ProcessUnitPriceRule::isEnabledYn)
                                .sorted(Comparator.comparingInt(ProcessUnitPriceRule::getSortOrder))
                                .toList();

                for (ProcessUnitPriceRule rule : priceRules) {
                    BigDecimal amount = calculateRule(context, unit, rule);
                    totalAmount = totalAmount.add(amount);

                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("unitKey", unit.getUnitKey());
                    row.put("unitTitle", unit.getTitle());
                    row.put("ruleKey", rule.getRuleKey());
                    row.put("ruleName", rule.getRuleName());
                    row.put("ruleType", rule.getRuleType().name());
                    row.put("amount", amount);

                    ruleResults.add(row);
                }
            }

            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("totalAmount", totalAmount);
            detail.put("rules", ruleResults);

            return new PriceCalculationResult(totalAmount, objectMapper.writeValueAsString(detail));
        } catch (Exception e) {
            try {
                Map<String, Object> detail = new LinkedHashMap<>();
                detail.put("message", "가격 계산 중 오류가 발생했습니다.");
                detail.put("error", e.getMessage());
                detail.put("totalAmount", BigDecimal.ZERO);
                detail.put("rules", List.of());

                return new PriceCalculationResult(BigDecimal.ZERO, objectMapper.writeValueAsString(detail));
            } catch (Exception ignored) {
                return new PriceCalculationResult(BigDecimal.ZERO, "{\"message\":\"가격 계산 실패\"}");
            }
        }
    }

    private BigDecimal calculateRule(
            PriceContext context,
            ProcessUnit currentUnit,
            ProcessUnitPriceRule rule
    ) throws Exception {
        ProcessPriceRuleType type = rule.getRuleType();

        if (type == ProcessPriceRuleType.SELECT_OPTION_AMOUNT) {
            SelectOptionAmountRule parsed = objectMapper.readValue(rule.getRuleJson(), SelectOptionAmountRule.class);
            return calculateSelectOptionAmount(context, currentUnit, parsed);
        }

        if (type == ProcessPriceRuleType.NUMBER_RANGE_TABLE) {
            NumberRangeTableRule parsed = objectMapper.readValue(rule.getRuleJson(), NumberRangeTableRule.class);
            return calculateNumberRangeTable(context, currentUnit, parsed);
        }

        if (type == ProcessPriceRuleType.NUMBER_CONDITION_AMOUNT) {
            NumberConditionAmountRule parsed = objectMapper.readValue(rule.getRuleJson(), NumberConditionAmountRule.class);
            return calculateNumberConditionAmount(context, currentUnit, parsed);
        }

        if (type == ProcessPriceRuleType.MULTIPLY_VALUE) {
            MultiplyValueRule parsed = objectMapper.readValue(rule.getRuleJson(), MultiplyValueRule.class);
            return calculateMultiplyValue(context, currentUnit, parsed);
        }

        if (type == ProcessPriceRuleType.LINEAR_SUM_BY_OPTION_RATE) {
            LinearSumByOptionRateRule parsed = objectMapper.readValue(rule.getRuleJson(), LinearSumByOptionRateRule.class);
            return calculateLinearSumByOptionRate(context, currentUnit, parsed);
        }

        return BigDecimal.ZERO;
    }

    private BigDecimal calculateSelectOptionAmount(
            PriceContext context,
            ProcessUnit currentUnit,
            SelectOptionAmountRule rule
    ) {
        ProcessExecutionAnswer answer = context.answerMap.get(currentUnit.getUnitKey());

        if (answer == null || answer.getSelectedOptionKey() == null) {
            return BigDecimal.ZERO;
        }

        return findOptionAmount(rule.getOptionAmounts(), answer.getSelectedOptionKey());
    }

    private BigDecimal calculateNumberRangeTable(
            PriceContext context,
            ProcessUnit currentUnit,
            NumberRangeTableRule rule
    ) {
        List<String> matchedRangeKeys = new ArrayList<>();

        for (Axis axis : safeList(rule.getAxes())) {
            String unitKey = normalizeUnitKey(currentUnit, axis.getUnitKey());
            BigDecimal value = context.getNumberValue(unitKey, axis.getFieldKey());

            if (value == null) {
                return BigDecimal.ZERO;
            }

            Range matched = findMatchedRange(axis.getRanges(), value);

            if (matched == null) {
                return BigDecimal.ZERO;
            }

            matchedRangeKeys.add(matched.getRangeKey());
        }

        for (Cell cell : safeList(rule.getCells())) {
            if (cell.getRangeKeys() != null && cell.getRangeKeys().equals(matchedRangeKeys)) {
                return nullToZero(cell.getAmount());
            }
        }

        return BigDecimal.ZERO;
    }

    private BigDecimal calculateNumberConditionAmount(
            PriceContext context,
            ProcessUnit currentUnit,
            NumberConditionAmountRule rule
    ) {
        if (!matchesConditions(context, currentUnit, rule.getConditions())) {
            return BigDecimal.ZERO;
        }

        return nullToZero(rule.getAmount());
    }

    private BigDecimal calculateMultiplyValue(
            PriceContext context,
            ProcessUnit currentUnit,
            MultiplyValueRule rule
    ) {
        if (!matchesConditions(context, currentUnit, rule.getConditions())) {
            return BigDecimal.ZERO;
        }

        BigDecimal left = resolveOperand(context, currentUnit, rule.getLeftOperand());
        BigDecimal right = resolveOperand(context, currentUnit, rule.getRightOperand());
        BigDecimal multiplier = rule.getMultiplier() == null ? BigDecimal.ONE : rule.getMultiplier();

        return left.multiply(right).multiply(multiplier);
    }

    private BigDecimal calculateLinearSumByOptionRate(
            PriceContext context,
            ProcessUnit currentUnit,
            LinearSumByOptionRateRule rule
    ) {
        if (!matchesConditions(context, currentUnit, rule.getConditions())) {
            return BigDecimal.ZERO;
        }

        ProcessExecutionAnswer rateAnswer = context.answerMap.get(rule.getRateSourceUnitKey());

        if (rateAnswer == null || rateAnswer.getSelectedOptionKey() == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal rate = findOptionAmount(rule.getOptionRates(), rateAnswer.getSelectedOptionKey());

        if (BigDecimal.ZERO.compareTo(rate) == 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal lengthSum = BigDecimal.ZERO;

        for (LengthTerm term : safeList(rule.getLengthTerms())) {
            if (!shouldApplyLengthTerm(context, term)) {
                continue;
            }

            BigDecimal value = context.getNumberValue(term.getUnitKey(), term.getFieldKey());

            if (value == null) {
                continue;
            }

            BigDecimal multiplier = term.getMultiplier() == null ? BigDecimal.ONE : term.getMultiplier();
            lengthSum = lengthSum.add(value.multiply(multiplier));
        }

        BigDecimal divisor = rule.getLengthDivisor() == null ? BigDecimal.ONE : rule.getLengthDivisor();

        if (BigDecimal.ZERO.compareTo(divisor) == 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal multiplier = rule.getMultiplier() == null ? BigDecimal.ONE : rule.getMultiplier();
        BigDecimal normalizedLength = lengthSum.divide(divisor, 6, RoundingMode.HALF_UP);

        return rate.multiply(normalizedLength).multiply(multiplier);
    }

    private boolean shouldApplyLengthTerm(PriceContext context, LengthTerm term) {
        if (isBlank(term.getRequiredUnitKey()) && isBlank(term.getRequiredOptionKey())) {
            return true;
        }

        ProcessExecutionAnswer answer = context.answerMap.get(term.getRequiredUnitKey());

        if (answer == null) {
            return false;
        }

        return term.getRequiredOptionKey().equals(answer.getSelectedOptionKey());
    }

    private BigDecimal resolveOperand(
            PriceContext context,
            ProcessUnit currentUnit,
            Operand operand
    ) {
        if (operand == null || operand.getOperandType() == null) {
            return BigDecimal.ZERO;
        }

        ProcessPriceOperandType type = operand.getOperandType();

        if (type == ProcessPriceOperandType.FIXED) {
            return nullToZero(operand.getFixedValue());
        }

        if (type == ProcessPriceOperandType.CURRENT_NUMBER_FIELD_VALUE) {
            return nullToZero(context.getNumberValue(currentUnit.getUnitKey(), operand.getFieldKey()));
        }

        if (type == ProcessPriceOperandType.UNIT_NUMBER_FIELD_VALUE) {
            return nullToZero(context.getNumberValue(operand.getUnitKey(), operand.getFieldKey()));
        }

        if (type == ProcessPriceOperandType.CURRENT_SELECTED_OPTION_VALUE_NUMBER) {
            return nullToZero(context.getSelectedOptionValueNumber(currentUnit.getUnitKey()));
        }

        if (type == ProcessPriceOperandType.UNIT_SELECTED_OPTION_VALUE_NUMBER) {
            return nullToZero(context.getSelectedOptionValueNumber(operand.getUnitKey()));
        }

        if (type == ProcessPriceOperandType.CURRENT_SELECTED_OPTION_AMOUNT) {
            ProcessExecutionAnswer answer = context.answerMap.get(currentUnit.getUnitKey());

            if (answer == null) {
                return BigDecimal.ZERO;
            }

            return findOptionAmount(operand.getOptionAmounts(), answer.getSelectedOptionKey());
        }

        if (type == ProcessPriceOperandType.UNIT_SELECTED_OPTION_AMOUNT) {
            ProcessExecutionAnswer answer = context.answerMap.get(operand.getUnitKey());

            if (answer == null) {
                return BigDecimal.ZERO;
            }

            return findOptionAmount(operand.getOptionAmounts(), answer.getSelectedOptionKey());
        }

        return BigDecimal.ZERO;
    }

    private boolean matchesConditions(
            PriceContext context,
            ProcessUnit currentUnit,
            List<Condition> conditions
    ) {
        for (Condition condition : safeList(conditions)) {
            String unitKey = normalizeUnitKey(currentUnit, condition.getUnitKey());

            if (!isBlank(condition.getFieldKey())) {
                BigDecimal actual = context.getNumberValue(unitKey, condition.getFieldKey());

                if (!compareNumber(actual, condition.getOperator(), condition.getValue())) {
                    return false;
                }

                continue;
            }

            if (!isBlank(condition.getOptionKey())) {
                ProcessExecutionAnswer answer = context.answerMap.get(unitKey);
                String selected = answer == null ? null : answer.getSelectedOptionKey();

                if (!compareOption(selected, condition.getOperator(), condition.getOptionKey())) {
                    return false;
                }
            }
        }

        return true;
    }

    private boolean compareNumber(BigDecimal actual, String operator, BigDecimal expected) {
        if (actual == null || operator == null || expected == null) {
            return false;
        }

        int compare = actual.compareTo(expected);

        return switch (operator) {
            case "EQ" -> compare == 0;
            case "NE" -> compare != 0;
            case "GT" -> compare > 0;
            case "GTE" -> compare >= 0;
            case "LT" -> compare < 0;
            case "LTE" -> compare <= 0;
            default -> false;
        };
    }

    private boolean compareOption(String selectedOptionKey, String operator, String expectedOptionKey) {
        if (operator == null || expectedOptionKey == null) {
            return false;
        }

        boolean equals = expectedOptionKey.equals(selectedOptionKey);

        return switch (operator) {
            case "EQ" -> equals;
            case "NE" -> !equals;
            default -> false;
        };
    }

    private Range findMatchedRange(List<Range> ranges, BigDecimal value) {
        for (Range range : safeList(ranges)) {
            boolean minOk = range.getMinValue() == null
                    || (
                    range.isMinInclusive()
                            ? value.compareTo(range.getMinValue()) >= 0
                            : value.compareTo(range.getMinValue()) > 0
            );

            boolean maxOk = range.getMaxValue() == null
                    || (
                    range.isMaxInclusive()
                            ? value.compareTo(range.getMaxValue()) <= 0
                            : value.compareTo(range.getMaxValue()) < 0
            );

            if (minOk && maxOk) {
                return range;
            }
        }

        return null;
    }

    private BigDecimal findOptionAmount(List<OptionAmount> optionAmounts, String optionKey) {
        if (optionKey == null) {
            return BigDecimal.ZERO;
        }

        for (OptionAmount optionAmount : safeList(optionAmounts)) {
            if (optionKey.equals(optionAmount.getOptionKey())) {
                return nullToZero(optionAmount.getAmount());
            }
        }

        return BigDecimal.ZERO;
    }

    private List<ProcessUnit> collectUnits(ProcessDefinition process) {
        List<ProcessUnit> units = new ArrayList<>();

        if (process == null || process.getSteps() == null) {
            return units;
        }

        List<ProcessStep> steps = process.getSteps()
                .stream()
                .sorted(Comparator.comparingInt(ProcessStep::getSortOrder))
                .toList();

        for (ProcessStep step : steps) {
            if (step.getUnits() == null) {
                continue;
            }

            units.addAll(
                    step.getUnits()
                            .stream()
                            .filter(ProcessUnit::isUseYn)
                            .sorted(Comparator.comparingInt(ProcessUnit::getSortOrder))
                            .toList()
            );
        }

        return units;
    }

    private PriceCalculationResult emptyResult(String message) {
        try {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("message", message);
            detail.put("totalAmount", BigDecimal.ZERO);
            detail.put("rules", List.of());

            return new PriceCalculationResult(BigDecimal.ZERO, objectMapper.writeValueAsString(detail));
        } catch (Exception e) {
            return new PriceCalculationResult(BigDecimal.ZERO, "{\"message\":\"가격 계산 불가\"}");
        }
    }

    private String normalizeUnitKey(ProcessUnit currentUnit, String unitKey) {
        if (unitKey == null || unitKey.isBlank() || "CURRENT".equals(unitKey)) {
            return currentUnit.getUnitKey();
        }

        return unitKey;
    }

    private BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private <T> List<T> safeList(List<T> list) {
        return list == null ? List.of() : list;
    }

    @Getter
    public static class PriceCalculationResult {
        private final BigDecimal amount;
        private final String detailJson;

        public PriceCalculationResult(BigDecimal amount, String detailJson) {
            this.amount = amount;
            this.detailJson = detailJson;
        }
    }

    private static class PriceContext {
        private final Map<String, ProcessExecutionAnswer> answerMap = new LinkedHashMap<>();
        private final Map<String, Map<String, BigDecimal>> numberValueMap = new LinkedHashMap<>();
        private final Map<String, Map<String, String>> optionValueTextMap = new LinkedHashMap<>();

        static PriceContext from(
                ProcessExecutionSession session,
                List<ProcessExecutionAnswer> answers,
                ObjectMapper objectMapper
        ) {
            PriceContext context = new PriceContext();

            List<ProcessExecutionAnswer> safeAnswers = answers == null ? List.of() : answers;

            for (ProcessExecutionAnswer answer : safeAnswers) {
                if (answer == null || answer.getUnitKey() == null) {
                    continue;
                }

                context.answerMap.put(answer.getUnitKey(), answer);
                context.numberValueMap.put(answer.getUnitKey(), parseNumberValues(answer, objectMapper));
            }

            ProcessDefinition process = session == null ? null : session.getProcess();

            if (process != null && process.getSteps() != null) {
                for (ProcessStep step : process.getSteps()) {
                    if (step.getUnits() == null) {
                        continue;
                    }

                    for (ProcessUnit unit : step.getUnits()) {
                        if (unit.getQuestion() == null || unit.getQuestion().getOptions() == null) {
                            continue;
                        }

                        Map<String, String> optionValues = new LinkedHashMap<>();

                        for (ProcessAnswerOption option : unit.getQuestion().getOptions()) {
                            optionValues.put(option.getOptionKey(), option.getValueText());
                        }

                        context.optionValueTextMap.put(unit.getUnitKey(), optionValues);
                    }
                }
            }

            return context;
        }

        BigDecimal getNumberValue(String unitKey, String fieldKey) {
            Map<String, BigDecimal> fields = numberValueMap.get(unitKey);

            if (fields == null) {
                return null;
            }

            return fields.get(fieldKey);
        }

        BigDecimal getSelectedOptionValueNumber(String unitKey) {
            ProcessExecutionAnswer answer = answerMap.get(unitKey);

            if (answer == null || answer.getSelectedOptionKey() == null) {
                return BigDecimal.ZERO;
            }

            Map<String, String> values = optionValueTextMap.get(unitKey);

            if (values == null) {
                return BigDecimal.ZERO;
            }

            String valueText = values.get(answer.getSelectedOptionKey());

            if (valueText == null || valueText.isBlank()) {
                return BigDecimal.ZERO;
            }

            try {
                return new BigDecimal(valueText.trim());
            } catch (Exception e) {
                return BigDecimal.ZERO;
            }
        }

        private static Map<String, BigDecimal> parseNumberValues(
                ProcessExecutionAnswer answer,
                ObjectMapper objectMapper
        ) {
            Map<String, BigDecimal> result = new LinkedHashMap<>();

            if (answer.getAnswerValueJson() == null || answer.getAnswerValueJson().isBlank()) {
                return result;
            }

            try {
                Map<String, Object> root = objectMapper.readValue(
                        answer.getAnswerValueJson(),
                        new TypeReference<Map<String, Object>>() {
                        }
                );

                /*
                 * 현재 processTest.js는 숫자 답변을 아래처럼 저장합니다.
                 * {
                 *   "width": 600,
                 *   "depth": 500
                 * }
                 *
                 * 혹시 나중에 values 래퍼를 쓰더라도 대응합니다.
                 */
                Object values = root.get("values");

                if (values instanceof Map<?, ?> valueMap) {
                    putNumberMap(result, valueMap);
                } else {
                    putNumberMap(result, root);
                }

                /*
                 * 혹시 나중에 fields 배열 구조를 쓰더라도 대응합니다.
                 */
                Object fields = root.get("fields");

                if (fields instanceof List<?> fieldList) {
                    for (Object item : fieldList) {
                        if (!(item instanceof Map<?, ?> fieldMap)) {
                            continue;
                        }

                        Object key = fieldMap.get("fieldKey");
                        Object value = fieldMap.get("value");

                        if (key != null && value != null) {
                            putDecimal(result, String.valueOf(key), value);
                        }
                    }
                }
            } catch (Exception ignored) {
                return result;
            }

            return result;
        }

        private static void putNumberMap(Map<String, BigDecimal> result, Map<?, ?> source) {
            for (Map.Entry<?, ?> entry : source.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) {
                    continue;
                }

                putDecimal(result, String.valueOf(entry.getKey()), entry.getValue());
            }
        }

        private static void putDecimal(Map<String, BigDecimal> result, String key, Object value) {
            try {
                result.put(key, new BigDecimal(String.valueOf(value)));
            } catch (Exception ignored) {
            }
        }
    }
}