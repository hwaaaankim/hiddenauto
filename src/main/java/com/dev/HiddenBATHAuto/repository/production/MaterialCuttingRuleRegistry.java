package com.dev.HiddenBATHAuto.repository.production;

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

    public MaterialCuttingRule resolve(Order order, MaterialCuttingParsedOptionsDto options) {
        return rules.stream()
                .filter(rule -> rule.supports(order, options))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("적용 가능한 자재재단 공식이 없습니다."));
    }

    public boolean canResolve(Order order, MaterialCuttingParsedOptionsDto options) {
        return rules.stream().anyMatch(rule -> rule.supports(order, options));
    }
}
