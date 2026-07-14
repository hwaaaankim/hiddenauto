package com.dev.HiddenBATHAuto.rag.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * 사용자 대화형 RAG API에서 Java/JavaScript 고정 문장이 assistant 답변으로 섞이는 것을
 * 최종 직렬화 단계에서 차단합니다.
 */
@ControllerAdvice(basePackages = "com.dev.HiddenBATHAuto.rag.controller")
public class RagGptOnlyResponseBodyAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType,
                            Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body,
                                  MethodParameter returnType,
                                  MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request,
                                  ServerHttpResponse response) {
        if (!(body instanceof Map<?, ?> raw) || !isGptAnswerEndpoint(request)) {
            return body;
        }

        Map<String, Object> result = copy(raw);
        String answer = text(result.get("answer"));
        String answerSource = text(result.get("answerSource"));
        String responseType = text(result.get("responseType"));

        if (!answer.isBlank()) {
            boolean validSource = answerSource.startsWith("GPT_");
            boolean validType = "GPT_ANSWER".equals(responseType);
            if (!validSource || !validType) {
                return contractViolation(result, request);
            }
        } else if ("GPT_ANSWER".equals(responseType)) {
            return contractViolation(result, request);
        }

        result.put("gptAnswerContractEnforced", true);
        return result;
    }

    private boolean isGptAnswerEndpoint(ServerHttpRequest request) {
        String path = request.getURI().getPath();
        return path.startsWith("/admin/rag/api/ai-chat")
                || path.startsWith("/admin/rag/api/learning-conversation")
                || path.startsWith("/admin/rag/api/knowledge-interaction");
    }

    private Map<String, Object> contractViolation(Map<String, Object> original,
                                                   ServerHttpRequest request) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", false);
        result.put("handled", true);
        result.put("responseType", "TECHNICAL_ERROR");
        result.put("answerSource", "NONE");
        result.put("actionStatus", "GPT_ANSWER_CONTRACT_VIOLATION");
        result.put("gptAnswerContractEnforced", true);
        result.put("systemError", Map.of(
                "code", "GPT_ANSWER_CONTRACT_VIOLATION",
                "message", "검증되지 않은 응답이 assistant 답변으로 전달되는 것을 차단했습니다.",
                "path", request.getURI().getPath()
        ));
        copyIfPresent(original, result, "agentRunId");
        copyIfPresent(original, result, "capabilityVersion");
        copyIfPresent(original, result, "sessionId");
        return result;
    }

    private Map<String, Object> copy(Map<?, ?> raw) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (entry.getKey() != null) result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }

    private void copyIfPresent(Map<String, Object> source,
                               Map<String, Object> target,
                               String key) {
        if (source.containsKey(key) && source.get(key) != null) target.put(key, source.get(key));
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
