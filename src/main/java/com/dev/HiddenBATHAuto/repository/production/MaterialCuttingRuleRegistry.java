package com.dev.HiddenBATHAuto.repository.production;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Component;

import com.dev.HiddenBATHAuto.dto.production.MaterialCuttingDtos.MaterialCuttingParsedOptionsDto;
import com.dev.HiddenBATHAuto.model.task.Order;
import com.dev.HiddenBATHAuto.service.production.MaterialCuttingRule;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class MaterialCuttingRuleRegistry {

    private final List<MaterialCuttingRule> rules;

    public MaterialCuttingRule resolve(Order order, MaterialCuttingParsedOptionsDto parsedOptions) {
        return rules.stream()
                .filter(rule -> rule.supports(order, parsedOptions))
                .sorted(Comparator.comparingInt(MaterialCuttingRule::getPriority).reversed())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("적용 가능한 재단 규칙이 없습니다."));
    }
}