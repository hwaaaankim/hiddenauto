package com.dev.HiddenBATHAuto.rag.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

import com.dev.HiddenBATHAuto.rag.config.RagOpenAiProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import reactor.core.publisher.Mono;

@Component
public class OpenAiRagClient {

    private final RagOpenAiProperties properties;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public OpenAiRagClient(RagOpenAiProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder()
                .baseUrl(properties.getBaseUrl())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public List<Double> embedding(String input) {
        requireApiKey();
        if (!StringUtils.hasText(input)) {
            throw new IllegalArgumentException("embedding input이 비어 있습니다.");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.getEmbeddingModel());
        body.put("input", input);
        body.put("encoding_format", "float");

        JsonNode root = postJson("/v1/embeddings", body);
        JsonNode embeddingNode = root.path("data").path(0).path("embedding");
        if (!embeddingNode.isArray()) {
            throw new IllegalStateException("OpenAI embeddings 응답에서 embedding 배열을 찾지 못했습니다: " + root);
        }

        List<Double> vector = new ArrayList<>(embeddingNode.size());
        for (JsonNode item : embeddingNode) {
            vector.add(item.asDouble());
        }
        return vector;
    }

    public String responseText(String systemPrompt, String userPrompt) {
        requireApiKey();
        Map<String, Object> body = baseResponseBody(systemPrompt, userPrompt);
        JsonNode root = postJson("/v1/responses", body);
        return extractOutputText(root);
    }

    public String responseJson(String systemPrompt, String userPrompt) {
        requireApiKey();
        Map<String, Object> body = baseResponseBody(systemPrompt, userPrompt);
        body.put("text", Map.of("format", Map.of("type", "json_object")));
        JsonNode root = postJson("/v1/responses", body);
        return extractOutputText(root);
    }

    public String responseJsonSchema(String systemPrompt,
                                     String userPrompt,
                                     String schemaName,
                                     Map<String, Object> jsonSchema,
                                     boolean strict) {
        requireApiKey();
        if (!StringUtils.hasText(schemaName)) {
            throw new IllegalArgumentException("Structured Outputs schemaName이 비어 있습니다.");
        }
        if (jsonSchema == null || jsonSchema.isEmpty()) {
            throw new IllegalArgumentException("Structured Outputs jsonSchema가 비어 있습니다.");
        }
        Map<String, Object> body = baseResponseBody(systemPrompt, userPrompt);
        Map<String, Object> format = new LinkedHashMap<>();
        format.put("type", "json_schema");
        format.put("name", schemaName);
        format.put("schema", jsonSchema);
        format.put("strict", strict);
        body.put("text", Map.of("format", format));
        JsonNode root = postJson("/v1/responses", body);
        return extractOutputText(root);
    }

    private Map<String, Object> baseResponseBody(String systemPrompt, String userPrompt) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.getChatModel());
        body.put("input", List.of(
                Map.of(
                        "role", "system",
                        "content", List.of(Map.of("type", "input_text", "text", systemPrompt == null ? "" : systemPrompt))
                ),
                Map.of(
                        "role", "user",
                        "content", List.of(Map.of("type", "input_text", "text", userPrompt == null ? "" : userPrompt))
                )
        ));
        if (StringUtils.hasText(properties.getReasoningEffort())) {
            body.put("reasoning", Map.of("effort", properties.getReasoningEffort()));
        }
        return body;
    }

    private String extractOutputText(JsonNode root) {
        String outputText = root.path("output_text").asText(null);
        if (StringUtils.hasText(outputText)) {
            return outputText;
        }

        StringBuilder sb = new StringBuilder();
        JsonNode output = root.path("output");
        if (output.isArray()) {
            for (JsonNode item : output) {
                JsonNode content = item.path("content");
                if (content.isArray()) {
                    for (JsonNode c : content) {
                        String text = c.path("text").asText(null);
                        if (StringUtils.hasText(text)) {
                            sb.append(text).append('\n');
                        }
                    }
                }
            }
        }
        if (sb.length() > 0) {
            return sb.toString().trim();
        }
        throw new IllegalStateException("OpenAI Responses 응답에서 텍스트를 찾지 못했습니다: " + root);
    }

    private JsonNode postJson(String uri, Map<String, Object> body) {
        int attempts = Math.max(1, properties.getRetryCount() + 1);
        RuntimeException last = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                JsonNode node = webClient.post()
                        .uri(uri)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                        .bodyValue(body)
                        .retrieve()
                        .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(), response ->
                                response.bodyToMono(String.class)
                                        .defaultIfEmpty("")
                                        .flatMap(errorBody -> Mono.error(new IllegalStateException("OpenAI API 오류: " + response.statusCode() + " / " + errorBody)))
                        )
                        .bodyToMono(JsonNode.class)
                        .timeout(Duration.ofSeconds(properties.getReadTimeoutSeconds()))
                        .block();
                if (node == null) {
                    throw new IllegalStateException("OpenAI API 응답이 비어 있습니다.");
                }
                return node;
            } catch (RuntimeException e) {
                last = e;
                if (attempt >= attempts || !isRetryable(e)) {
                    throw normalizeOpenAiException(e);
                }
                sleepBeforeRetry(attempt);
            }
        }
        throw normalizeOpenAiException(last == null ? new IllegalStateException("OpenAI API 호출 실패") : last);
    }

    private boolean isRetryable(RuntimeException e) {
        String message = String.valueOf(e.getMessage()).toLowerCase();
        return message.contains("timeoutexception")
                || message.contains("timeout")
                || message.contains("timed out")
                || message.contains("connection reset")
                || message.contains("connection prematurely closed");
    }

    private RuntimeException normalizeOpenAiException(RuntimeException e) {
        String message = String.valueOf(e.getMessage());
        if (message.contains("TimeoutException") || message.toLowerCase().contains("timeout")) {
            return new IllegalStateException("OpenAI API 호출이 " + properties.getReadTimeoutSeconds()
                    + "초 안에 끝나지 않았습니다. 긴 학습 입력은 계층형 청크 해석으로 재시도해야 합니다. 원인: " + message, e);
        }
        return e;
    }

    private void sleepBeforeRetry(int attempt) {
        try {
            Thread.sleep(Math.min(1500L * attempt, 5000L));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("OpenAI API 재시도 대기 중 인터럽트되었습니다.", interrupted);
        }
    }

    private void requireApiKey() {
        if (!StringUtils.hasText(properties.getApiKey())) {
            throw new IllegalStateException("OpenAI API Key가 비어 있습니다. hiddenbath.rag.openai.api-key 또는 OPENAI_API_KEY를 설정해 주세요.");
        }
    }

    public String chatModel() { return properties.getChatModel(); }
    public String embeddingModel() { return properties.getEmbeddingModel(); }
    public ObjectMapper objectMapper() { return objectMapper; }
}
