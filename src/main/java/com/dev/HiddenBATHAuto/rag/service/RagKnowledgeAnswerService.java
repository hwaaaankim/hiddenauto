package com.dev.HiddenBATHAuto.rag.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.dev.HiddenBATHAuto.rag.util.RagJsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class RagKnowledgeAnswerService {

    private final OpenAiRagClient openAi;
    private final ObjectMapper objectMapper;

    public RagKnowledgeAnswerService(OpenAiRagClient openAi, ObjectMapper objectMapper) {
        this.openAi = openAi;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> compose(String userMessage, Map<String, Object> plan, Map<String, Object> retrieved) {
        Map<String, Object> fallback = fallbackAnswer(userMessage, plan, retrieved);
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("userMessage", userMessage);
            payload.put("semanticPlan", plan);
            payload.put("retrieved", retrieved);
            payload.put("fallbackAnswer", fallback);
            String raw = openAi.responseJsonSchema(
                    systemPrompt(),
                    RagJsonUtils.pretty(objectMapper, payload),
                    "rag_knowledge_answer",
                    RagSemanticSchemaFactory.answerSchema(),
                    true
            );
            JsonNode node = RagJsonUtils.extractObjectNode(objectMapper, raw);
            Map<String, Object> answer = RagJsonUtils.toMap(objectMapper, node.toString());
            String text = text(answer.get("answer"));
            if (!StringUtils.hasText(text)) return fallback;
            answer.putIfAbsent("naturalSummary", fallback.get("naturalSummary"));
            answer.putIfAbsent("confidence", plan.getOrDefault("confidence", 0.8));
            return answer;
        } catch (Exception e) {
            fallback.put("answerComposerError", e.getMessage());
            return fallback;
        }
    }

    private String systemPrompt() {
        return """
                당신은 HiddenBATHAuto의 RAG 답변 작성자입니다.
                Java 서버가 조회한 raw data와 GPT semantic plan을 받아, 사용자가 실제로 원하는 답변을 자연스럽게 작성합니다.

                원칙:
                1. main answer는 semanticPlan.primaryEntity 중심으로 답합니다.
                2. relatedEntities나 relatedRows는 main answer에 섞지 말고 "참고/관련 품목"으로 제안합니다.
                   예: 코지장을 물었고 라운드 코지장이 관련 후보라면, 코지장 답변 후 "라운드 코지장도 별도 품목으로 확인됩니다"라고 말합니다.
                3. activeOverrideRules는 원본 엑셀 행보다 우선입니다.
                   예: HC 제외 규칙이 있으면, 엑셀에 HC 행이 있어도 가능 색상에 넣지 말고 제외 사유를 설명합니다.
                4. dialogRules는 비규격 주문의 질문흐름/조건/검증/가격식 규칙입니다. 주문 상담이나 학습 상태 설명에서 반드시 반영하세요.
                5. 데이터가 없으면 추정하지 말고 어떤 저장 데이터가 부족한지 말합니다.
                6. 한국어 존댓말로 답변합니다.

                반환 JSON:
                {
                  "answer":"사용자에게 보여줄 최종 답변",
                  "naturalSummary":"짧은 내부 요약",
                  "usedRules":[...],
                  "relatedSuggestions":[...],
                  "warnings":[...]
                }
                JSON 하나만 반환하세요.
                """;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fallbackAnswer(String userMessage, Map<String, Object> plan, Map<String, Object> retrieved) {
        String intent = text(plan.get("intentType"));
        String entity = childText(plan, "primaryEntity", "name");
        List<Map<String, Object>> rows = mapList(retrieved.get("effectiveRows"));
        List<Map<String, Object>> excludedRows = mapList(retrieved.get("excludedRows"));
        List<Map<String, Object>> rules = mapList(retrieved.get("activeOverrideRules"));
        List<Map<String, Object>> relatedRows = mapList(retrieved.get("relatedRows"));
        List<Map<String, Object>> artifacts = mapList(retrieved.get("artifacts"));
        List<Map<String, Object>> nodes = mapList(retrieved.get("knowledgeNodes"));
        List<Map<String, Object>> assets = mapList(retrieved.get("assets"));
        List<Map<String, Object>> pricingRules = mapList(retrieved.get("pricingRules"));
        List<Map<String, Object>> dialogRules = mapList(retrieved.get("dialogRules"));

        StringBuilder sb = new StringBuilder();
        List<String> warnings = new ArrayList<>();
        List<String> relatedSuggestions = new ArrayList<>();

        if ("ASK_PRODUCT_AVAILABILITY".equals(intent)) {
            if (StringUtils.hasText(entity)) {
                sb.append("현재 저장된 지식에서 '").append(entity).append("' 기준으로 확인했습니다.\n\n");
            } else {
                sb.append("현재 저장된 지식에서 제품 가능 조건을 확인했습니다.\n\n");
            }
            if (rows.isEmpty()) {
                sb.append("[확인 결과]\n");
                sb.append("- 해당 제품의 구조화 행 데이터가 조회되지 않았습니다. 엑셀 행이 구조화 저장되어 있는지 확인이 필요합니다.\n");
            } else {
                Map<String, Object> summary = map(retrieved.get("availabilitySummary"));
                sb.append("[가능 조건]\n");
                appendList(sb, "가능 색상", objectList(summary.get("colors")));
                appendList(sb, "가능 사이즈", objectList(summary.get("sizes")));
                appendList(sb, "확인된 가격/금액", objectList(summary.get("prices")));
                sb.append("\n[구조화 행 데이터]\n");
                int count = 0;
                for (Map<String, Object> row : rows) {
                    if (count++ >= 30) {
                        sb.append("- ... 나머지 ").append(rows.size() - 30).append("행 생략\n");
                        break;
                    }
                    sb.append("- 제품명=").append(firstText(row, "제품명", "품목", "productName", "name"));
                    String color = firstText(row, "색상", "색", "컬러", "color", "Color", "COLOR");
                    String size = firstText(row, "사이즈", "규격", "크기", "size", "Size", "SIZE");
                    String price = firstText(row, "금액", "가격", "단가", "price", "Price", "PRICE");
                    if (StringUtils.hasText(color)) sb.append(" / 색상=").append(color);
                    if (StringUtils.hasText(size)) sb.append(" / 사이즈=").append(size);
                    if (StringUtils.hasText(price)) sb.append(" / 금액=").append(price);
                    sb.append("\n");
                }
            }
            if (!rules.isEmpty() || !excludedRows.isEmpty()) {
                sb.append("\n[최신 수정/제외 규칙]\n");
                for (Map<String, Object> rule : rules) {
                    sb.append("- ").append(text(rule.get("entity_key")))
                            .append(" / ").append(text(rule.get("field_name")))
                            .append("=").append(text(rule.get("rule_value")))
                            .append(" ").append(text(rule.get("rule_type")))
                            .append(" / 사유: ").append(text(rule.get("reason"))).append("\n");
                }
                if (!excludedRows.isEmpty()) {
                    sb.append("- 위 규칙으로 제외된 원본 행: ").append(excludedRows.size()).append("개\n");
                }
            }
            Set<String> relatedProducts = relatedProducts(entity, relatedRows);
            if (!relatedProducts.isEmpty()) {
                sb.append("\n[관련 품목 참고]\n");
                sb.append("- ").append(String.join(", ", relatedProducts)).append("도 별도 품목으로 확인됩니다. 이것도 함께 확인하시겠습니까?\n");
                relatedSuggestions.addAll(relatedProducts);
            }
        } else if ("ASK_KNOWLEDGE_SUMMARY".equals(intent)) {
            sb.append("현재 저장된 RAG 지식 요약입니다.\n\n");
            if (!artifacts.isEmpty()) {
                sb.append("[구조화 자료]\n");
                for (Map<String, Object> a : artifacts) {
                    sb.append("- ").append(text(a.get("topic"))).append(" / ").append(text(a.get("semantic_role")))
                            .append(" / ").append(text(a.get("title"))).append(" / 파일: ").append(text(a.get("original_filename"))).append("\n");
                }
            }
            if (!rows.isEmpty()) {
                sb.append("\n[저장된 제품/가능 조건]\n");
                Map<String, Map<String, Set<String>>> grouped = groupProductRows(rows);
                for (Map.Entry<String, Map<String, Set<String>>> entry : grouped.entrySet()) {
                    String product = entry.getKey();
                    Map<String, Set<String>> values = entry.getValue();
                    sb.append("- ").append(product);
                    if (!values.getOrDefault("colors", Set.of()).isEmpty()) {
                        sb.append(" / 색상: ").append(String.join(", ", values.get("colors")));
                    }
                    if (!values.getOrDefault("sizes", Set.of()).isEmpty()) {
                        sb.append(" / 사이즈: ").append(String.join(", ", values.get("sizes")));
                    }
                    sb.append("\n");
                }
                sb.append("\n[대표 구조화 행]\n");
                int count = 0;
                for (Map<String, Object> row : rows) {
                    if (count++ >= 40) {
                        sb.append("- ... 나머지 ").append(rows.size() - 40).append("행 생략\n");
                        break;
                    }
                    sb.append("- 제품명=").append(firstText(row, "제품명", "품목", "productName", "name"));
                    String color = firstText(row, "색상", "색", "컬러", "color", "Color", "COLOR");
                    String size = firstText(row, "사이즈", "규격", "크기", "size", "Size", "SIZE");
                    if (StringUtils.hasText(color)) sb.append(" / 색상=").append(color);
                    if (StringUtils.hasText(size)) sb.append(" / 사이즈=").append(size);
                    sb.append("\n");
                }
            }
            if (!dialogRules.isEmpty()) {
                sb.append("\n[저장된 대화/주문 규칙]\n");
                int drCount = 0;
                for (Map<String, Object> rule : dialogRules) {
                    if (drCount++ >= 30) {
                        sb.append("- ... 나머지 ").append(dialogRules.size() - 30).append("개 규칙 생략\n");
                        break;
                    }
                    sb.append("- ").append(text(rule.get("rule_type")))
                            .append(" / step=").append(text(rule.get("step_key")))
                            .append(" / field=").append(text(rule.get("field_name")))
                            .append(" / entity=").append(text(rule.get("entity_key")))
                            .append("\n");
                }
            }
            if (!pricingRules.isEmpty()) {
                sb.append("\n[저장된 가격 규칙]\n");
                for (Map<String, Object> rule : pricingRules) {
                    sb.append("- ").append(text(rule.get("entity_key")))
                            .append(" / ").append(text(rule.get("option_field"))).append("=").append(text(rule.get("option_value")))
                            .append(" / 기준넓이=").append(text(rule.get("base_width")))
                            .append(" / 기준가=").append(text(rule.get("base_price"))).append("원")
                            .append(" / 넓이 ").append(text(rule.get("width_step"))).append(" 증가당 ")
                            .append(text(rule.get("width_step_price"))).append("원")
                            .append("\n");
                }
            }
            if (!rules.isEmpty()) {
                sb.append("\n[저장된 수정/제외 규칙]\n");
                for (Map<String, Object> rule : rules) {
                    sb.append("- ").append(text(rule.get("entity_key"))).append(" / ")
                            .append(text(rule.get("field_name"))).append("=").append(text(rule.get("rule_value")))
                            .append(" ").append(text(rule.get("rule_type"))).append("\n");
                }
            }
            if (!nodes.isEmpty()) {
                sb.append("\n[지식 노드]\n");
                int count = 0;
                for (Map<String, Object> n : nodes) {
                    if (count++ >= 20) break;
                    sb.append("- ").append(text(n.get("title"))).append(" : ").append(text(n.get("summary"))).append("\n");
                }
            }
            if (artifacts.isEmpty() && rows.isEmpty() && nodes.isEmpty() && dialogRules.isEmpty()) {
                sb.append("- 현재 설명 가능한 구조화 데이터나 지식 노드가 조회되지 않았습니다.\n");
            }
        } else {
            sb.append("요청을 처리했습니다.\n");
        }

        if (!assets.isEmpty()) {
            sb.append("\n[연결 이미지/파일]\n");
            for (Map<String, Object> asset : assets) {
                sb.append("- ").append(text(asset.get("original_filename"))).append(" → ").append(text(asset.get("file_url"))).append("\n");
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("answer", sb.toString().trim());
        result.put("naturalSummary", intent + " fallback answer");
        List<Map<String, Object>> usedRules = new ArrayList<>();
        usedRules.addAll(rules);
        usedRules.addAll(dialogRules);
        result.put("usedRules", usedRules);
        result.put("relatedSuggestions", relatedSuggestions);
        result.put("warnings", warnings);
        return result;
    }


    private Map<String, Map<String, Set<String>>> groupProductRows(List<Map<String, Object>> rows) {
        Map<String, Map<String, Set<String>>> grouped = new LinkedHashMap<>();
        for (Map<String, Object> row : rows == null ? List.<Map<String, Object>>of() : rows) {
            String product = firstText(row, "제품명", "품목", "productName", "name");
            if (!StringUtils.hasText(product)) product = "미지정 제품";
            Map<String, Set<String>> values = grouped.computeIfAbsent(product, k -> {
                Map<String, Set<String>> m = new LinkedHashMap<>();
                m.put("colors", new LinkedHashSet<>());
                m.put("sizes", new LinkedHashSet<>());
                return m;
            });
            String color = firstText(row, "색상", "색", "컬러", "color", "Color", "COLOR");
            String size = firstText(row, "사이즈", "규격", "크기", "size", "Size", "SIZE");
            if (StringUtils.hasText(color)) values.get("colors").add(color);
            if (StringUtils.hasText(size)) values.get("sizes").add(size);
        }
        return grouped;
    }

    private void appendList(StringBuilder sb, String label, List<Object> values) {
        if (values == null || values.isEmpty()) return;
        sb.append("- ").append(label).append(": ");
        List<String> text = values.stream().map(String::valueOf).filter(StringUtils::hasText).distinct().toList();
        sb.append(String.join(", ", text)).append("\n");
    }

    private Set<String> relatedProducts(String entity, List<Map<String, Object>> rows) {
        Set<String> result = new LinkedHashSet<>();
        for (Map<String, Object> row : rows == null ? List.<Map<String, Object>>of() : rows) {
            String p = firstText(row, "제품명", "품목", "productName", "name");
            if (StringUtils.hasText(p) && !p.equals(entity)) result.add(p);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        if (value instanceof Map<?, ?> m) {
            Map<String, Object> r = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (e.getKey() != null) r.put(String.valueOf(e.getKey()), e.getValue());
            }
            return r;
        }
        return new LinkedHashMap<>();
    }

    private List<Map<String, Object>> mapList(Object value) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> m) result.add(map(m));
            }
        }
        return result;
    }

    private List<Object> objectList(Object value) {
        if (value instanceof List<?> list) return new ArrayList<>(list);
        return new ArrayList<>();
    }

    private String childText(Map<String, Object> map, String child, String key) {
        return text(map(map.get(child)).get(key));
    }

    private String firstText(Map<String, Object> row, String... keys) {
        if (row == null) return "";
        for (String key : keys) {
            Object v = row.get(key);
            if (v != null && StringUtils.hasText(String.valueOf(v))) return String.valueOf(v).trim();
        }
        return "";
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
