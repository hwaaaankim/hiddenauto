package com.dev.HiddenBATHAuto.rag.service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.dev.HiddenBATHAuto.rag.repository.RagRepository;

/** GPT는 입력을 구성하고, 최종 숫자는 기존 결정론적 가격 계산기가 계산하도록 연결합니다. */
@Service
public class RagAgentPricingToolService {

    private final RagSemanticMemoryService semanticMemoryService;
    private final RagOrderPriceCalculator priceCalculator;
    private final RagRepository repository;

    public RagAgentPricingToolService(RagSemanticMemoryService semanticMemoryService,
                                      RagOrderPriceCalculator priceCalculator,
                                      RagRepository repository) {
        this.semanticMemoryService = semanticMemoryService;
        this.priceCalculator = priceCalculator;
        this.repository = repository;
    }

    public Map<String, Object> findCandidates(RagAgentToolContext context,
                                               String query,
                                               String entityType,
                                               int limit) {
        Map<String, Object> searched = semanticMemoryService.search(
                context,
                query,
                List.of("PRICE", "PRODUCT", "STRUCTURED", "ORDER", "KNOWLEDGE"),
                List.of("PRICE", "CANONICAL", "STRUCTURED", "ALIAS", "KNOWLEDGE"),
                limit,
                new BigDecimal("0.050000"),
                false);
        Map<String, Object> result = new LinkedHashMap<>(searched);
        result.put("candidateCount", searched.getOrDefault("resultCount", 0));
        result.put("entityTypeHint", StringUtils.hasText(entityType) ? entityType.trim() : null);
        result.put("nextInstruction", "후보가 여러 개이거나 점수가 낮으면 사용자 확인 질문을 하고, 확정 전 원본 row를 조회하십시오.");
        return result;
    }

    public Map<String, Object> calculate(RagAgentToolContext context,
                                         Map<String, Object> answers) {
        Map<String, Object> version = repository.findVersion(context.versionId())
                .orElseThrow(() -> new IllegalArgumentException("버전을 찾을 수 없습니다: " + context.versionId()));
        Map<String, Object> safeAnswers = answers == null ? Map.of() : new LinkedHashMap<>(answers);
        if (safeAnswers.isEmpty()) {
            return Map.of(
                    "success", true,
                    "calculated", false,
                    "missingInputs", List.of("answers"),
                    "message", "가격 계산에 필요한 주문 답변이 비어 있습니다. 사용자에게 제품·수량·규격·옵션을 질문하십시오.");
        }
        Map<String, Object> calculated = priceCalculator.calculateFromAnswers(
                context.projectId(), context.versionId(), version, safeAnswers);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("calculated", Boolean.TRUE.equals(calculated.get("calculated")));
        result.put("projectId", context.projectId());
        result.put("versionId", context.versionId());
        result.put("inputAnswers", safeAnswers);
        result.put("price", calculated);
        copyIfPresent(calculated, result, "total", "totalPrice", "finalPrice", "currency", "warnings", "missingInputs");
        Object missing = result.get("missingInputs");
        if (missing instanceof List<?> list && !list.isEmpty()) {
            result.put("calculated", false);
            result.put("message", "필수 가격 입력이 부족합니다. missingInputs를 사용자에게 질문하십시오.");
        } else if (!Boolean.TRUE.equals(result.get("calculated"))) {
            result.put("message", calculated.getOrDefault("reason", "일치하는 가격 규칙을 찾지 못했습니다. 제품·규격·옵션을 추가로 확인하십시오."));
        }
        return result;
    }

    private void copyIfPresent(Map<String, Object> source, Map<String, Object> target, String... keys) {
        for (String key : keys) {
            if (source.containsKey(key)) target.put(key, source.get(key));
        }
    }
}
