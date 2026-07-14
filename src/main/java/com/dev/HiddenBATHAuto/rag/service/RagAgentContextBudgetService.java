package com.dev.HiddenBATHAuto.rag.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.dev.HiddenBATHAuto.rag.config.RagOpenAiProperties;
import com.dev.HiddenBATHAuto.rag.util.RagJsonUtils;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Responses API 입력을 문자 예산 안에서 유지합니다.
 * 최초 사용자 요청은 head/tail 압축으로 항상 남기고, 이후에는 최근의 연속된 tool trace를 우선 보존합니다.
 */
@Service
public class RagAgentContextBudgetService {
    private final RagOpenAiProperties properties;
    private final ObjectMapper objectMapper;

    public RagAgentContextBudgetService(RagOpenAiProperties properties,
                                        ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public List<Map<String, Object>> compact(List<Map<String, Object>> input) {
        if (input == null || input.isEmpty()) return List.of();
        int budget = properties.getAgentMaxContextChars();
        int initialBudget = Math.min(Math.max(30000, budget / 3), 90000);

        Map<String, Object> initial = compactItem(input.get(0), initialBudget, "INITIAL_CONTEXT");
        int initialSize = jsonLength(initial);
        int remaining = Math.max(10000, budget - initialSize);

        List<Map<String, Object>> suffixReverse = new ArrayList<>();
        int used = 0;
        int startIndex = input.size();
        for (int i = input.size() - 1; i >= 1; i--) {
            int singleBudget = Math.max(3000, Math.min(properties.getAgentMaxToolOutputChars(), remaining - used));
            if (singleBudget <= 3000 && used > 0) break;
            Map<String, Object> compacted = compactItem(input.get(i), singleBudget, "RECENT_TRACE");
            int size = jsonLength(compacted);
            if (used + size > remaining) break;
            suffixReverse.add(compacted);
            used += size;
            startIndex = i;
        }

        List<Map<String, Object>> suffix = new ArrayList<>();
        for (int i = suffixReverse.size() - 1; i >= 0; i--) suffix.add(suffixReverse.get(i));
        removeOrphanFunctionPairs(suffix);

        List<Map<String, Object>> result = new ArrayList<>();
        result.add(initial);
        if (startIndex > 1) {
            result.add(compactionNotice(input.size(), startIndex, budget));
        }
        result.addAll(suffix);
        return List.copyOf(result);
    }

    private Map<String, Object> compactItem(Map<String, Object> item,
                                            int maxChars,
                                            String reason) {
        if (item == null) return Map.of("type", "compaction_notice", "reason", reason);
        if (jsonLength(item) <= maxChars) return item;

        if (isFunctionOutput(item)) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("type", "function_call_output");
            result.put("call_id", item.get("call_id"));
            result.put("output", truncateHeadTail(
                    String.valueOf(item.getOrDefault("output", "")),
                    Math.max(1000, maxChars - 500),
                    "TOOL_OUTPUT"));
            return result;
        }

        Object role = item.get("role");
        Object content = item.get("content");
        if (role != null && content instanceof List<?> list && !list.isEmpty()) {
            String text = extractText(list);
            return Map.of(
                    "role", String.valueOf(role),
                    "content", List.of(Map.of(
                            "type", "input_text",
                            "text", truncateHeadTail(text, Math.max(1000, maxChars - 500), reason)
                    ))
            );
        }

        String json = RagJsonUtils.toJson(objectMapper, item);
        return Map.of(
                "role", "user",
                "content", List.of(Map.of(
                        "type", "input_text",
                        "text", truncateHeadTail(json, Math.max(1000, maxChars - 500), reason)
                ))
        );
    }

    private String extractText(List<?> content) {
        StringBuilder builder = new StringBuilder();
        for (Object entry : content) {
            if (entry instanceof Map<?, ?> map) {
                Object text = map.get("text");
                if (text != null) {
                    if (!builder.isEmpty()) builder.append('\n');
                    builder.append(text);
                }
            }
        }
        return builder.toString();
    }

    private String truncateHeadTail(String value, int maxChars, String reason) {
        String text = value == null ? "" : value;
        if (text.length() <= maxChars) return text;
        String marker = "\n...[CONTEXT_COMPACTED reason=" + reason
                + ", originalChars=" + text.length() + "]...\n";
        int available = Math.max(200, maxChars - marker.length());
        int head = (int) Math.floor(available * 0.72);
        int tail = available - head;
        return text.substring(0, head) + marker + text.substring(text.length() - tail);
    }

    private Map<String, Object> compactionNotice(int originalItems,
                                                  int retainedFromIndex,
                                                  int budget) {
        return Map.of(
                "role", "user",
                "content", List.of(Map.of(
                        "type", "input_text",
                        "text", RagJsonUtils.toJson(objectMapper, Map.of(
                                "agentContextCompacted", true,
                                "originalItemCount", originalItems,
                                "retainedRecentFromIndex", retainedFromIndex,
                                "characterBudget", budget,
                                "instruction", "이전 도구 결과 일부가 입력 예산 때문에 제외되었습니다. 필요한 근거는 해당 전용 도구로 다시 조회하십시오."
                        ))
                ))
        );
    }

    /**
     * Responses API에 과거 function_call/function_call_output을 다시 전달할 때는 call_id 쌍이 모두 있어야 합니다.
     * 문자 예산 압축 과정에서 한쪽만 남은 경우 쌍 전체를 제거하고 필요하면 GPT가 해당 도구를 다시 호출하게 합니다.
     */
    private void removeOrphanFunctionPairs(List<Map<String, Object>> items) {
        Set<String> calls = items.stream()
                .filter(this::isFunctionCall)
                .map(item -> String.valueOf(item.get("call_id")))
                .filter(id -> !id.isBlank() && !"null".equals(id))
                .collect(java.util.stream.Collectors.toSet());
        Set<String> outputs = items.stream()
                .filter(this::isFunctionOutput)
                .map(item -> String.valueOf(item.get("call_id")))
                .filter(id -> !id.isBlank() && !"null".equals(id))
                .collect(java.util.stream.Collectors.toSet());
        Set<String> complete = calls.stream().filter(outputs::contains)
                .collect(java.util.stream.Collectors.toSet());
        items.removeIf(item -> {
            if (!isFunctionCall(item) && !isFunctionOutput(item)) return false;
            return !complete.contains(String.valueOf(item.get("call_id")));
        });
    }

    private boolean isFunctionCall(Map<String, Object> item) {
        return item != null && "function_call".equals(String.valueOf(item.get("type")));
    }

    private boolean isFunctionOutput(Map<String, Object> item) {
        return item != null && "function_call_output".equals(String.valueOf(item.get("type")));
    }

    private int jsonLength(Map<String, Object> item) {
        return RagJsonUtils.toJson(objectMapper, item).length();
    }
}
