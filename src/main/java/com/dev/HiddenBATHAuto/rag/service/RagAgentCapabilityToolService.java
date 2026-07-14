package com.dev.HiddenBATHAuto.rag.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.dev.HiddenBATHAuto.rag.config.RagOpenAiProperties;

/** Agent가 현재 사용할 수 있는 도구와 실행 계약을 GPT가 스스로 확인할 수 있게 합니다. */
@Service
public class RagAgentCapabilityToolService {

    private final RagOpenAiProperties properties;

    public RagAgentCapabilityToolService(RagOpenAiProperties properties) {
        this.properties = properties;
    }

    public Map<String, Object> capabilities(RagAgentToolContext context, List<String> categories) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("capabilityVersion", properties.getAgentCapabilityVersion());
        result.put("agentMode", "GPT_CENTRIC_FUNCTION_TOOLS_V4");
        result.put("gptOnlyAnswer", true);
        result.put("technicalErrorsAreSystemEvents", true);
        result.put("projectId", context.projectId());
        result.put("versionId", context.versionId());
        result.put("sourceScope", context.sourceScope());
        result.put("categories", categories == null ? List.of() : categories);
        result.put("tools", RagAgentToolDefinitionFactory.capabilitySummary(categories, context.sourceScope()));
        result.put("executionContract", Map.of(
                "interpretationOwner", "GPT",
                "userFacingLanguageOwner", "GPT",
                "databaseExecutionOwner", "JAVA_VALIDATED_TOOL",
                "deterministicPriceOwner", "JAVA_PRICE_CALCULATOR",
                "mutationOwner", "JAVA_TRANSACTIONAL_CHANGE_SET",
                "technicalFailure", "SYSTEM_EVENT_WITHOUT_ANSWER"
        ));
        return result;
    }
}
