package com.dev.HiddenBATHAuto.service.process;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.dev.HiddenBATHAuto.model.process.ProcessExecutionAnswer;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ProcessPriceCalculator {

    private final ObjectMapper objectMapper;

    public PriceCalculationResult calculate(List<ProcessExecutionAnswer> answers) {
        try {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("message", "가격 룰 미연결 상태입니다. 현재는 답변 저장 테스트용 계산 결과입니다.");
            detail.put("answerCount", answers.size());

            return new PriceCalculationResult(
                    BigDecimal.ZERO,
                    objectMapper.writeValueAsString(detail)
            );
        } catch (Exception e) {
            return new PriceCalculationResult(
                    BigDecimal.ZERO,
                    "{\"message\":\"가격 계산 상세 생성 실패\"}"
            );
        }
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
}