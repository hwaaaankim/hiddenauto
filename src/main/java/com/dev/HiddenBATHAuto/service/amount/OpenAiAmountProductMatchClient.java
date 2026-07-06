package com.dev.HiddenBATHAuto.service.amount;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.dev.HiddenBATHAuto.config.amount.AmountOpenAiProperties;
import com.dev.HiddenBATHAuto.dto.amount.AmountParsedOrderProduct;
import com.dev.HiddenBATHAuto.model.amount.AmountItemMaster;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class OpenAiAmountProductMatchClient {

    private final AmountOpenAiProperties properties;
    private final ObjectMapper objectMapper;

    public Optional<AiProductChoice> chooseBest(AmountParsedOrderProduct parsed, List<AmountItemMaster> candidates) {
        if (!StringUtils.hasText(properties.getApiKey()) || properties.getApiKey().contains("YOUR")) {
            return Optional.empty();
        }
        if (candidates == null || candidates.isEmpty()) {
            return Optional.empty();
        }

        try {
            Map<String, Object> payload = buildPayload(parsed, candidates);
            String requestJson = objectMapper.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(trimRightSlash(properties.getBaseUrl()) + "/v1/responses"))
                    .timeout(Duration.ofSeconds(Math.max(30, properties.getReadTimeoutSeconds())))
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                    .build();

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(20))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return Optional.empty();
            }
            String text = extractOutputText(objectMapper.readTree(response.body()));
            if (!StringUtils.hasText(text)) {
                return Optional.empty();
            }
            JsonNode json = objectMapper.readTree(extractJsonObject(text));
            String itemCode = json.path("itemCode").asText("").trim();
            String itemName = json.path("itemName").asText("").trim();
            int confidence = Math.max(0, Math.min(100, json.path("confidence").asInt(0)));
            String reason = json.path("reason").asText("AI 후보 선택");
            if (!StringUtils.hasText(itemCode) && !StringUtils.hasText(itemName)) {
                return Optional.empty();
            }
            return Optional.of(new AiProductChoice(itemCode, itemName, confidence, reason));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private Map<String, Object> buildPayload(AmountParsedOrderProduct parsed, List<AmountItemMaster> candidates) {
        List<Map<String, Object>> candidateRows = new ArrayList<>();
        for (AmountItemMaster item : candidates.stream().limit(20).toList()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("itemCode", item.getItemCode());
            row.put("itemName", item.getItemName());
            row.put("unit", item.getUnit());
            row.put("specification", item.getSpecification());
            row.put("categoryName", item.getCategoryName());
            row.put("middleCategoryName", item.getMiddleCategoryName());
            row.put("standard", item.isStandard() ? "규격" : "비규격");
            row.put("mirrorCuttingProduct", item.isMirrorCuttingProduct());
            row.put("salesPrice", item.getSalesPrice());
            candidateRows.add(row);
        }

        Map<String, Object> orderContext = new LinkedHashMap<>();
        orderContext.put("orderId", parsed.orderId());
        orderContext.put("category", parsed.category());
        orderContext.put("series", parsed.series());
        orderContext.put("productName", parsed.productName());
        orderContext.put("color", parsed.color());
        orderContext.put("sizeText", parsed.sizeText());
        orderContext.put("width", parsed.width());
        orderContext.put("height", parsed.height());
        orderContext.put("depth", parsed.depth());
        orderContext.put("doorCount", parsed.doorCount());
        orderContext.put("optionMap", parsed.optionMap());

        String system = "너는 욕실가구 ERP 주문 옵션을 얼마에요 품목 마스터와 매칭하는 검수자다. "
                + "후보 목록 중 가장 정확한 품목 1개만 선택한다. "
                + "규격/비규격, 대분류, 중분류, 색상, 시리즈, 제품명, 넓이(W), 규격, 단위, 도어 수를 우선순위로 비교한다. "
                + "반드시 JSON 객체만 출력한다.";

        String user = "주문 옵션과 후보 품목 목록을 비교해서 가장 적절한 품목을 선택해라.\n"
                + "출력 형식: {\"itemCode\":\"...\",\"itemName\":\"...\",\"confidence\":0-100,\"reason\":\"...\"}\n"
                + "주문 옵션:\n" + toJson(orderContext) + "\n후보 품목:\n" + toJson(candidateRows);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", properties.getChatModel());
        payload.put("reasoning", Map.of("effort", StringUtils.hasText(properties.getReasoningEffort()) ? properties.getReasoningEffort() : "medium"));
        payload.put("input", List.of(
                Map.of("role", "system", "content", List.of(Map.of("type", "input_text", "text", system))),
                Map.of("role", "user", "content", List.of(Map.of("type", "input_text", "text", user)))
        ));
        return payload;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    private String extractOutputText(JsonNode root) {
        String direct = root.path("output_text").asText("");
        if (StringUtils.hasText(direct)) {
            return direct;
        }
        StringBuilder sb = new StringBuilder();
        collectText(root, sb);
        return sb.toString().trim();
    }

    private void collectText(JsonNode node, StringBuilder sb) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            if (node.has("text") && node.get("text").isTextual()) {
                sb.append(node.get("text").asText()).append('\n');
            }
            node.fields().forEachRemaining(e -> collectText(e.getValue(), sb));
        } else if (node.isArray()) {
            node.forEach(child -> collectText(child, sb));
        }
    }

    private String extractJsonObject(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }

    private String trimRightSlash(String url) {
        if (!StringUtils.hasText(url)) {
            return "https://api.openai.com";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    public record AiProductChoice(String itemCode, String itemName, int confidence, String reason) {
    }
}
