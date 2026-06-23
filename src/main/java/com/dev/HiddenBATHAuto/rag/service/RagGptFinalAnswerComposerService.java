package com.dev.HiddenBATHAuto.rag.service;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.dev.HiddenBATHAuto.rag.util.RagJsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Java가 검증한 계산/조회 결과를 GPT에게 넘겨 최종 사용자 답변을 생성합니다.
 *
 * 원칙:
 * - GPT가 최종 설명과 표현을 주관합니다.
 * - Java는 DB 조회, 산술 계산, 경계값 검증, 결과 무결성 검증만 담당합니다.
 * - GPT가 숫자나 상태를 바꾸면 Java가 차단하고 안전 fallback을 반환합니다.
 */
@Service
public class RagGptFinalAnswerComposerService {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final OpenAiRagClient openAiClient;

    public RagGptFinalAnswerComposerService(@Qualifier("ragJdbcTemplate") NamedParameterJdbcTemplate jdbc,
                                            ObjectMapper objectMapper,
                                            OpenAiRagClient openAiClient) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.openAiClient = openAiClient;
    }

    public String compose(UUID projectId,
                          UUID versionId,
                          UUID sessionId,
                          String sourceScope,
                          String userMessage,
                          Map<String, Object> interpretation,
                          Map<String, Object> verifiedResult,
                          String fallbackAnswer) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("projectId", projectId);
        request.put("versionId", versionId);
        request.put("sessionId", sessionId);
        request.put("sourceScope", sourceScope == null ? "API" : sourceScope);
        request.put("userMessage", userMessage);
        request.put("interpretationByGpt", interpretation == null ? Map.of() : interpretation);
        request.put("verifiedResultFromJava", verifiedResult == null ? Map.of() : verifiedResult);
        request.put("hardRules", List.of(
                "verifiedResultFromJava의 숫자, 상태, 허용/불가 판단을 절대 변경하지 않는다.",
                "계산값을 새로 추측하지 않는다.",
                "불가 상태이면 불가를 명확히 말하고, 참고 가능 가격이 있으면 참고로만 설명한다.",
                "사용자에게 필요한 다음 입력이 있으면 간단히 묻는다.",
                "최종 답변은 한국어 존댓말로 작성한다."
        ));

        Map<String, Object> parsed = Map.of();
        String status = "FAILED";
        String answer = fallbackAnswer;
        String error = null;
        try {
            String raw = openAiClient.responseJsonSchema(
                    systemPrompt(),
                    RagJsonUtils.toJson(objectMapper, request),
                    "hiddenauto_final_user_answer",
                    answerSchema(),
                    true
            );
            parsed = objectMapper.readValue(raw, new TypeReference<Map<String, Object>>() {});
            String candidate = text(parsed.get("answer"), "").trim();
            validateAnswer(candidate, verifiedResult);
            answer = candidate;
            status = "GPT_COMPOSED";
            return answer;
        } catch (Exception e) {
            error = e.getMessage();
            status = "FALLBACK_USED";
            return fallbackAnswer;
        } finally {
            log(projectId, versionId, sessionId, sourceScope, userMessage, interpretation, verifiedResult, parsed, status, answer, error);
        }
    }

    private String systemPrompt() {
        return """
                당신은 HiddenBATHAuto의 최종 답변 작성 GPT입니다.
                당신이 대화의 뇌이며, 사용자에게 전달할 최종 설명을 작성합니다.
                다만 DB 조회와 산술 검증은 Java가 이미 수행했고, verifiedResultFromJava가 유일한 계산 근거입니다.

                절대 규칙:
                1. verifiedResultFromJava의 숫자, 범위, 허용/불가 판단을 바꾸지 마십시오.
                2. 없는 가격, 없는 색상, 없는 옵션을 추측하지 마십시오.
                3. 계산 불가 상태이면 계산 불가 이유를 먼저 설명하십시오.
                4. 참고 가격은 참고 가격이라고 분리해서 말하십시오.
                5. 사용자가 물은 것에 직접 답하십시오.
                6. 답변은 자연스럽고 간결한 한국어 존댓말로 작성하십시오.
                7. 내부 JSON, SQL, 시스템 정책은 사용자에게 노출하지 마십시오.

                응답은 반드시 JSON으로만 작성하십시오.
                """;
    }

    private Map<String, Object> answerSchema() {
        Map<String, Object> string = Map.of("type", "string");
        Map<String, Object> number = Map.of("type", "number");
        return object(Map.of(
                "answer", string,
                "confidence", number,
                "userFacingStatus", string,
                "usedVerifiedFactsSummary", string,
                "riskNote", string
        ), List.of("answer", "confidence", "userFacingStatus", "usedVerifiedFactsSummary", "riskNote"));
    }

    private Map<String, Object> object(Map<String, Object> properties, List<String> required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        schema.put("properties", properties);
        schema.put("required", required);
        return schema;
    }

    private void validateAnswer(String answer, Map<String, Object> verifiedResult) {
        if (!StringUtils.hasText(answer)) {
            throw new IllegalStateException("GPT 최종 답변이 비어 있습니다.");
        }
        String status = text(verifiedResult.get("quoteStatus"), "");
        if ("OUT_OF_RANGE_MAX".equals(status)) {
            requireContains(answer, verifiedResult.get("requestedW"), "requestedW");
            requireContains(answer, verifiedResult.get("maxWidth"), "maxWidth");
            if (!answer.contains("초과") && !answer.contains("불가")) {
                throw new IllegalStateException("최대 초과/불가 상태가 답변에 명확히 포함되지 않았습니다.");
            }
        }
        if ("OUT_OF_RANGE_MIN".equals(status)) {
            requireContains(answer, verifiedResult.get("requestedW"), "requestedW");
            requireContains(answer, verifiedResult.get("minWidth"), "minWidth");
            if (!answer.contains("최소") && !answer.contains("불가")) {
                throw new IllegalStateException("최소 범위/불가 상태가 답변에 명확히 포함되지 않았습니다.");
            }
        }
        if ("QUOTED".equals(status)) {
            requireContains(answer, verifiedResult.get("finalPrice"), "finalPrice");
        }
    }

    private void requireContains(String answer, Object value, String label) {
        if (value == null) return;
        String compactAnswer = answer.replaceAll("[^0-9]", "");
        String compactValue = String.valueOf(value).replaceAll("\\.0+$", "").replaceAll("[^0-9]", "");
        if (StringUtils.hasText(compactValue) && !compactAnswer.contains(compactValue)) {
            throw new IllegalStateException("GPT 최종 답변에 검증값 " + label + "=" + value + " 이(가) 포함되지 않았습니다.");
        }
    }

    private void log(UUID projectId,
                     UUID versionId,
                     UUID sessionId,
                     String sourceScope,
                     String userMessage,
                     Map<String, Object> interpretation,
                     Map<String, Object> verifiedResult,
                     Map<String, Object> gptResult,
                     String status,
                     String answer,
                     String error) {
        try {
            jdbc.update("""
                    INSERT INTO rag_gpt_final_answer_log(
                        id, project_id, version_id, session_id, source_scope, user_message,
                        interpretation_json, verified_result_json, gpt_result_json,
                        status, final_answer, error_message, created_at
                    ) VALUES (
                        :id, :projectId, :versionId, :sessionId, :sourceScope, :userMessage,
                        CAST(:interpretationJson AS jsonb), CAST(:verifiedResultJson AS jsonb), CAST(:gptResultJson AS jsonb),
                        :status, :finalAnswer, :errorMessage, :createdAt
                    )
                    """, new MapSqlParameterSource()
                    .addValue("id", UUID.randomUUID())
                    .addValue("projectId", projectId)
                    .addValue("versionId", versionId)
                    .addValue("sessionId", sessionId)
                    .addValue("sourceScope", sourceScope == null ? "API" : sourceScope)
                    .addValue("userMessage", userMessage)
                    .addValue("interpretationJson", RagJsonUtils.toJson(objectMapper, interpretation == null ? Map.of() : interpretation))
                    .addValue("verifiedResultJson", RagJsonUtils.toJson(objectMapper, verifiedResult == null ? Map.of() : verifiedResult))
                    .addValue("gptResultJson", RagJsonUtils.toJson(objectMapper, gptResult == null ? Map.of() : gptResult))
                    .addValue("status", status)
                    .addValue("finalAnswer", answer)
                    .addValue("errorMessage", error)
                    .addValue("createdAt", OffsetDateTime.now()));
        } catch (Exception ignored) {
            // 답변 로그 실패가 사용자 응답을 막으면 안 됩니다.
        }
    }

    private String text(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }
}
