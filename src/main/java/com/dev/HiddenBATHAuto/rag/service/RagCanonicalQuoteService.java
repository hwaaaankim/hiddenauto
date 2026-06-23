package com.dev.HiddenBATHAuto.rag.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
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

@Service
public class RagCanonicalQuoteService {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public RagCanonicalQuoteService(@Qualifier("ragJdbcTemplate") NamedParameterJdbcTemplate jdbc,
                                    ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> quote(UUID projectId, UUID versionId, Map<String, Object> request) {
        Map<String, Object> dataset = activeDataset(projectId, versionId);
        if (dataset.isEmpty()) {
            return Map.of("quoted", false, "message", "활성 정본 데이터셋이 없습니다. canonical rebuild를 먼저 실행해 주세요.");
        }
        UUID datasetId = (UUID) dataset.get("id");
        List<Map<String, Object>> facts = jdbc.queryForList("""
                SELECT id, subject_key, subject_name, fact_key, factor_json, value_json, confidence
                FROM rag_canonical_fact
                WHERE dataset_id = :datasetId
                  AND active = true
                  AND fact_type = 'PRICE'
                ORDER BY created_at ASC
                LIMIT 5000
                """, Map.of("datasetId", datasetId));
        List<Map<String, Object>> scored = new ArrayList<>();
        for (Map<String, Object> fact : facts) {
            int score = score(request, fact);
            if (score <= 0) continue;
            Map<String, Object> one = new LinkedHashMap<>(fact);
            one.put("score", score);
            one.put("matched", matched(request, fact));
            scored.add(one);
        }
        scored.sort(Comparator.comparingInt((Map<String, Object> m) -> ((Number) m.get("score")).intValue()).reversed());
        List<Map<String, Object>> top = scored.stream().limit(20).toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("quoted", !top.isEmpty());
        result.put("datasetId", datasetId);
        result.put("request", request);
        result.put("candidates", top);
        result.put("message", top.isEmpty() ? "입력 조건과 매칭되는 정본 가격 후보가 없습니다. GPT가 부족한 요소를 질문해야 합니다." : "정본 가격 후보를 조회했습니다.");
        insertQuoteLog(projectId, versionId, datasetId, request, result);
        return result;
    }

    private int score(Map<String, Object> request, Map<String, Object> fact) {
        Map<String, Object> factor = parseMap(fact.get("factor_json"));
        int score = 0;
        for (Map.Entry<String, Object> entry : request.entrySet()) {
            String rv = norm(entry.getValue());
            if (!StringUtils.hasText(rv)) continue;
            for (Map.Entry<String, Object> f : factor.entrySet()) {
                String fv = norm(f.getValue());
                if (!StringUtils.hasText(fv)) continue;
                if (fv.equals(rv) || fv.contains(rv) || rv.contains(fv)) score += 10;
            }
            String subject = norm(fact.get("subject_name"));
            if (StringUtils.hasText(subject) && (subject.equals(rv) || subject.contains(rv) || rv.contains(subject))) score += 20;
        }
        return score;
    }

    private Map<String, Object> matched(Map<String, Object> request, Map<String, Object> fact) {
        Map<String, Object> factor = parseMap(fact.get("factor_json"));
        Map<String, Object> matched = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : request.entrySet()) {
            String rv = norm(entry.getValue());
            if (!StringUtils.hasText(rv)) continue;
            for (Map.Entry<String, Object> f : factor.entrySet()) {
                String fv = norm(f.getValue());
                if (StringUtils.hasText(fv) && (fv.equals(rv) || fv.contains(rv) || rv.contains(fv))) matched.put(entry.getKey(), f.getKey());
            }
        }
        return matched;
    }

    private void insertQuoteLog(UUID projectId, UUID versionId, UUID datasetId, Object request, Object result) {
        jdbc.update("""
                INSERT INTO rag_canonical_quote_log(id, project_id, version_id, dataset_id, request_json, result_json, created_at)
                VALUES (:id, :projectId, :versionId, :datasetId, CAST(:requestJson AS jsonb), CAST(:resultJson AS jsonb), now())
                """, new MapSqlParameterSource()
                .addValue("id", UUID.randomUUID())
                .addValue("projectId", projectId)
                .addValue("versionId", versionId)
                .addValue("datasetId", datasetId)
                .addValue("requestJson", RagJsonUtils.toJson(objectMapper, request))
                .addValue("resultJson", RagJsonUtils.toJson(objectMapper, result)));
    }

    private Map<String, Object> activeDataset(UUID projectId, UUID versionId) {
        try {
            return jdbc.queryForMap("""
                    SELECT * FROM rag_canonical_dataset
                    WHERE project_id = :projectId AND version_id = :versionId AND active = true
                    ORDER BY created_at DESC LIMIT 1
                    """, new MapSqlParameterSource().addValue("projectId", projectId).addValue("versionId", versionId));
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private Map<String, Object> parseMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> r = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) if (e.getKey() != null) r.put(String.valueOf(e.getKey()), e.getValue());
            return r;
        }
        try {
            Object parsed = objectMapper.readValue(String.valueOf(value), new TypeReference<Object>() {});
            if (parsed instanceof Map<?, ?> map) {
                Map<String, Object> r = new LinkedHashMap<>();
                for (Map.Entry<?, ?> e : map.entrySet()) if (e.getKey() != null) r.put(String.valueOf(e.getKey()), e.getValue());
                return r;
            }
        } catch (Exception ignored) {}
        return new LinkedHashMap<>();
    }

    private String norm(Object value) {
        if (value == null) return "";
        return String.valueOf(value).trim().toLowerCase().replaceAll("\\s+", "");
    }
}
