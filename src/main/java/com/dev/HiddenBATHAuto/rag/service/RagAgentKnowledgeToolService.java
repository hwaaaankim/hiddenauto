package com.dev.HiddenBATHAuto.rag.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.dev.HiddenBATHAuto.rag.util.RagJsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 자유 SQL을 쓰지 않고도 GPT가 지식 원문, 엔티티, 유효 규칙, 주문 흐름을 안전하게 조사할 수 있게 하는 도구 모음입니다.
 */
@Service
public class RagAgentKnowledgeToolService {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public RagAgentKnowledgeToolService(
            @Qualifier("ragJdbcTemplate") NamedParameterJdbcTemplate jdbc,
            ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> searchKnowledgeSources(RagAgentToolContext context,
                                                       String query,
                                                       List<String> domains,
                                                       List<String> sourceKinds,
                                                       int limit,
                                                       boolean includeInactive) {
        if (!StringUtils.hasText(query)) {
            throw new IllegalArgumentException("지식 원문 검색어가 필요합니다.");
        }
        int safeLimit = Math.max(1, Math.min(limit, 100));
        MapSqlParameterSource p = scope(context)
                .addValue("query", query.trim())
                .addValue("pattern", "%" + query.trim() + "%")
                .addValue("domains", upperList(domains))
                .addValue("sourceKinds", upperList(sourceKinds))
                .addValue("limit", safeLimit)
                .addValue("includeInactive", includeInactive);
        String sql = """
                SELECT id, source_table, source_id, source_kind, domain_key,
                       entity_type, entity_key, title,
                       left(content, 12000) AS content,
                       keywords, aliases, metadata_json, active, embedding_status,
                       CASE
                           WHEN lower(coalesce(entity_key,'')) = lower(:query) THEN 1.00
                           WHEN lower(title) = lower(:query) THEN 0.98
                           WHEN lower(coalesce(entity_key,'')) LIKE lower(:pattern) THEN 0.90
                           WHEN lower(title) LIKE lower(:pattern) THEN 0.85
                           WHEN lower(content) LIKE lower(:pattern) THEN 0.65
                           ELSE 0.20
                       END AS lexical_score,
                       updated_at
                FROM rag_semantic_memory
                WHERE project_id=:projectId
                  AND version_id=:versionId
                  AND (:includeInactive=true OR active=true)
                  AND (cardinality(CAST(:domains AS text[]))=0 OR upper(domain_key)=ANY(CAST(:domains AS text[])))
                  AND (cardinality(CAST(:sourceKinds AS text[]))=0 OR upper(source_kind)=ANY(CAST(:sourceKinds AS text[])))
                  AND (
                        lower(coalesce(entity_key,'')) LIKE lower(:pattern)
                     OR lower(title) LIKE lower(:pattern)
                     OR lower(content) LIKE lower(:pattern)
                     OR EXISTS (SELECT 1 FROM unnest(aliases) a WHERE lower(a) LIKE lower(:pattern))
                     OR EXISTS (SELECT 1 FROM unnest(keywords) k WHERE lower(k) LIKE lower(:pattern))
                  )
                ORDER BY lexical_score DESC, updated_at DESC
                LIMIT :limit
                """;
        List<Map<String, Object>> rows = jdbc.queryForList(sql, p);
        return Map.of(
                "query", query.trim(),
                "resultCount", rows.size(),
                "results", rows,
                "guidance", "sourceTable/sourceId 원본 row가 필요한 경우 get_entity_context_bundle, get_document_context 또는 query_database로 재확인하십시오."
        );
    }

    public Map<String, Object> documentContext(RagAgentToolContext context,
                                                UUID documentId,
                                                String searchText,
                                                int beforeChunks,
                                                int afterChunks,
                                                int maxCharacters) {
        int safeBefore = Math.max(0, Math.min(beforeChunks, 20));
        int safeAfter = Math.max(0, Math.min(afterChunks, 20));
        int safeChars = Math.max(1000, Math.min(maxCharacters, 100000));
        MapSqlParameterSource p = scope(context).addValue("documentId", documentId);
        Map<String, Object> document;
        try {
            document = jdbc.queryForMap("""
                    SELECT id, topic, source_type, title, original_filename, metadata, created_at,
                           length(raw_text) AS raw_text_characters,
                           left(raw_text, :maxCharacters) AS raw_text_preview
                    FROM rag_document
                    WHERE id=:documentId AND project_id=:projectId AND version_id=:versionId
                    """, p.addValue("maxCharacters", safeChars));
        } catch (EmptyResultDataAccessException e) {
            throw new IllegalArgumentException("현재 프로젝트/버전에서 문서를 찾을 수 없습니다: " + documentId);
        }

        Integer centerChunk = null;
        if (StringUtils.hasText(searchText)) {
            MapSqlParameterSource centerParams = scope(context)
                    .addValue("documentId", documentId)
                    .addValue("pattern", "%" + searchText.trim() + "%");
            List<Integer> centers = jdbc.queryForList("""
                    SELECT chunk_no
                    FROM rag_chunk
                    WHERE document_id=:documentId AND project_id=:projectId AND version_id=:versionId
                      AND lower(content) LIKE lower(:pattern)
                    ORDER BY chunk_no
                    LIMIT 1
                    """, centerParams, Integer.class);
            if (!centers.isEmpty()) centerChunk = centers.get(0);
        }
        if (centerChunk == null) centerChunk = 0;
        MapSqlParameterSource chunkParams = scope(context)
                .addValue("documentId", documentId)
                .addValue("fromChunk", Math.max(0, centerChunk - safeBefore))
                .addValue("toChunk", centerChunk + safeAfter)
                .addValue("maxChunkChars", Math.max(1000, safeChars / Math.max(1, safeBefore + safeAfter + 1)));
        List<Map<String, Object>> chunks = jdbc.queryForList("""
                SELECT id, chunk_no, topic, left(content, :maxChunkChars) AS content, metadata, created_at
                FROM rag_chunk
                WHERE document_id=:documentId AND project_id=:projectId AND version_id=:versionId
                  AND chunk_no BETWEEN :fromChunk AND :toChunk
                ORDER BY chunk_no
                """, chunkParams);
        return Map.of(
                "document", document,
                "searchText", searchText == null ? "" : searchText,
                "centerChunk", centerChunk,
                "chunks", chunks,
                "chunkCount", chunks.size()
        );
    }

    public Map<String, Object> resolveEntityReference(RagAgentToolContext context,
                                                       String expression,
                                                       String entityType,
                                                       int limit,
                                                       BigDecimal minimumConfidence) {
        if (!StringUtils.hasText(expression)) {
            throw new IllegalArgumentException("해석할 사용자 표현이 필요합니다.");
        }
        int safeLimit = Math.max(1, Math.min(limit, 30));
        BigDecimal min = minimumConfidence == null ? BigDecimal.ZERO : minimumConfidence.max(BigDecimal.ZERO);
        List<Map<String, Object>> candidates;
        try {
            candidates = fuzzyEntityCandidates(context, expression.trim(), entityType, safeLimit);
        } catch (Exception e) {
            candidates = lexicalEntityCandidates(context, expression.trim(), entityType, safeLimit);
        }
        List<Map<String, Object>> filtered = candidates.stream()
                .filter(row -> decimal(row.get("confidence"), BigDecimal.ZERO).compareTo(min) >= 0)
                .toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("expression", expression.trim());
        result.put("entityTypeHint", nullable(entityType));
        result.put("candidateCount", filtered.size());
        result.put("candidates", filtered);
        result.put("resolved", filtered.size() == 1 && decimal(filtered.get(0).get("confidence"), BigDecimal.ZERO)
                .compareTo(new BigDecimal("0.8500")) >= 0);
        result.put("requiresClarification", filtered.size() != 1
                || decimal(filtered.get(0).get("confidence"), BigDecimal.ZERO).compareTo(new BigDecimal("0.8500")) < 0);
        persistResolution(context, expression, entityType, filtered, result);
        return result;
    }

    public Map<String, Object> entityContextBundle(RagAgentToolContext context,
                                                    String entityType,
                                                    String entityKey,
                                                    boolean includeInactive,
                                                    int limitPerSection) {
        if (!StringUtils.hasText(entityKey)) throw new IllegalArgumentException("entityKey가 필요합니다.");
        int limit = Math.max(1, Math.min(limitPerSection, 100));
        MapSqlParameterSource p = scope(context)
                .addValue("entityType", nullable(entityType))
                .addValue("entityKey", entityKey.trim())
                .addValue("includeInactive", includeInactive)
                .addValue("limit", limit);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("entityType", nullable(entityType));
        result.put("entityKey", entityKey.trim());
        result.put("aliases", jdbc.queryForList("""
                SELECT id, entity_type, entity_key, alias, source, active, created_at
                FROM (
                    SELECT id, entity_type, entity_key, alias, source, active, created_at
                    FROM rag_entity_alias
                    WHERE project_id=:projectId AND version_id=:versionId
                    UNION ALL
                    SELECT id, entity_type, entity_id AS entity_key, alias_text AS alias,
                           'AGENT_ALIAS'::varchar AS source, active, created_at
                    FROM rag_agent_entity_alias
                    WHERE project_id=:projectId AND (version_id IS NULL OR version_id=:versionId)
                ) x
                WHERE (:entityType IS NULL OR entity_type=:entityType)
                  AND (lower(entity_key)=lower(:entityKey) OR lower(alias)=lower(:entityKey))
                  AND (:includeInactive=true OR active=true)
                ORDER BY active DESC, created_at DESC LIMIT :limit
                """, p));
        result.put("canonicalEntities", jdbc.queryForList("""
                SELECT id, dataset_id, entity_type, entity_key, display_name, identity_json, attribute_json,
                       status, active, updated_at
                FROM rag_canonical_entity
                WHERE project_id=:projectId AND version_id=:versionId
                  AND (:entityType IS NULL OR entity_type=:entityType)
                  AND lower(entity_key)=lower(:entityKey)
                  AND (:includeInactive=true OR active=true)
                ORDER BY active DESC, updated_at DESC LIMIT :limit
                """, p));
        result.put("knowledgeNodes", jdbc.queryForList("""
                SELECT id, parent_id, document_id, topic, node_type, node_key, title, summary,
                       left(raw_text, 8000) AS raw_text, structured_json, metadata_json, active, status, updated_at
                FROM rag_knowledge_node
                WHERE project_id=:projectId AND version_id=:versionId
                  AND (:entityType IS NULL OR node_type=:entityType)
                  AND (lower(node_key)=lower(:entityKey) OR lower(title)=lower(:entityKey))
                  AND (:includeInactive=true OR active=true)
                ORDER BY active DESC, depth, sort_order, updated_at DESC LIMIT :limit
                """, p));
        result.put("dialogRules", dialogRules(p));
        result.put("pricingRules", pricingRules(p));
        result.put("overrideRules", overrideRules(p));
        result.put("canonicalFacts", canonicalFacts(p, LocalDate.now()));
        result.put("semanticSources", jdbc.queryForList("""
                SELECT id, source_table, source_id, source_kind, domain_key, entity_type, entity_key,
                       title, left(content, 8000) AS content, aliases, keywords, metadata_json, active, updated_at
                FROM rag_semantic_memory
                WHERE project_id=:projectId AND version_id=:versionId
                  AND (:entityType IS NULL OR entity_type=:entityType)
                  AND lower(coalesce(entity_key,''))=lower(:entityKey)
                  AND (:includeInactive=true OR active=true)
                ORDER BY active DESC, updated_at DESC LIMIT :limit
                """, p));
        result.put("exists", result.values().stream().filter(Collection.class::isInstance)
                .map(Collection.class::cast).anyMatch(c -> !c.isEmpty()));
        return result;
    }

    public Map<String, Object> effectiveRules(RagAgentToolContext context,
                                               String entityType,
                                               String entityKey,
                                               LocalDate effectiveDate,
                                               List<String> ruleTypes,
                                               boolean includeInactive) {
        LocalDate date = effectiveDate == null ? LocalDate.now() : effectiveDate;
        MapSqlParameterSource p = scope(context)
                .addValue("entityType", nullable(entityType))
                .addValue("entityKey", StringUtils.hasText(entityKey) ? entityKey.trim() : null)
                .addValue("includeInactive", includeInactive)
                .addValue("ruleTypes", upperList(ruleTypes))
                .addValue("effectiveDate", date)
                .addValue("limit", 500);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("effectiveDate", date);
        result.put("entityType", nullable(entityType));
        result.put("entityKey", nullable(entityKey));
        result.put("dialogRules", dialogRules(p));
        result.put("pricingRules", pricingRules(p));
        result.put("overrideRules", overrideRules(p));
        result.put("canonicalFacts", canonicalFacts(p, date));
        result.put("precedence", List.of(
                "명시적 override rule",
                "유효기간이 맞는 canonical fact",
                "우선순위가 높은 dialog rule",
                "구조화 pricing rule",
                "일반 knowledge node"
        ));
        return result;
    }

    public Map<String, Object> orderFlow(RagAgentToolContext context,
                                         String entityType,
                                         String entityKey,
                                         String purpose,
                                         boolean includeInactive) {
        MapSqlParameterSource p = scope(context)
                .addValue("entityType", nullable(entityType))
                .addValue("entityKey", nullable(entityKey))
                .addValue("purpose", nullable(purpose))
                .addValue("includeInactive", includeInactive);
        List<Map<String, Object>> canonicalFlows = jdbc.queryForList("""
                SELECT id, dataset_id, flow_key, purpose, question_flow_json, validation_json,
                       condition_json, active, updated_at
                FROM rag_canonical_dialog_flow
                WHERE project_id=:projectId AND version_id=:versionId
                  AND (:includeInactive=true OR active=true)
                  AND (:purpose IS NULL OR lower(coalesce(purpose,'')) LIKE lower('%' || :purpose || '%'))
                ORDER BY active DESC, updated_at DESC
                LIMIT 100
                """, p);
        List<Map<String, Object>> rules = jdbc.queryForList("""
                SELECT id, topic, rule_key, rule_type, entity_type, entity_key, step_key, field_name,
                       priority, condition_json, action_json, validation_json, pricing_json,
                       confidence, active, updated_at
                FROM rag_dialog_rule
                WHERE project_id=:projectId AND version_id=:versionId
                  AND (:entityType IS NULL OR entity_type=:entityType)
                  AND (:entityKey IS NULL OR entity_key='' OR lower(entity_key)=lower(:entityKey))
                  AND (:includeInactive=true OR active=true)
                ORDER BY priority ASC, step_key ASC, updated_at DESC
                LIMIT 500
                """, p);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("entityType", nullable(entityType));
        result.put("entityKey", nullable(entityKey));
        result.put("purpose", nullable(purpose));
        result.put("canonicalFlows", canonicalFlows);
        result.put("dialogRules", rules);
        result.put("flowFound", !canonicalFlows.isEmpty() || !rules.isEmpty());
        return result;
    }

    public Map<String, Object> validateOrderState(RagAgentToolContext context,
                                                   String entityType,
                                                   String entityKey,
                                                   Map<String, Object> orderState,
                                                   LocalDate effectiveDate) {
        Map<String, Object> safeState = orderState == null ? Map.of() : new LinkedHashMap<>(orderState);
        Map<String, Object> flow = orderFlow(context, entityType, entityKey, null, false);
        Map<String, Object> rules = effectiveRules(context, entityType, entityKey, effectiveDate, List.of(), false);
        Set<String> requiredFields = new LinkedHashSet<>();
        List<Map<String, Object>> conflicts = new ArrayList<>();
        collectRequiredFields(flow, requiredFields);
        collectRequiredFields(rules, requiredFields);
        List<String> missing = requiredFields.stream()
                .filter(field -> !hasValue(safeState.get(field)))
                .toList();
        validateDimensions(rules, safeState, conflicts);
        validateOverrideRules(rules, safeState, conflicts);
        boolean valid = missing.isEmpty() && conflicts.isEmpty();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("entityType", nullable(entityType));
        result.put("entityKey", nullable(entityKey));
        result.put("orderState", safeState);
        result.put("requiredFields", List.copyOf(requiredFields));
        result.put("missingFields", missing);
        result.put("conflicts", conflicts);
        result.put("valid", valid);
        result.put("nextFields", missing.stream().limit(5).toList());
        result.put("flow", flow);
        result.put("effectiveRules", rules);
        persistOrderSnapshot(context, safeState, result);
        return result;
    }

    public Map<String, Object> compareCandidates(RagAgentToolContext context,
                                                  List<Map<String, Object>> candidateRefs,
                                                  int limitPerSection) {
        if (candidateRefs == null || candidateRefs.isEmpty()) {
            throw new IllegalArgumentException("비교할 candidateRefs가 필요합니다.");
        }
        List<Map<String, Object>> comparisons = new ArrayList<>();
        for (Map<String, Object> candidate : candidateRefs.stream().limit(10).toList()) {
            String type = nullable(candidate.get("entityType"));
            String key = nullable(candidate.get("entityKey"));
            if (!StringUtils.hasText(key)) continue;
            Map<String, Object> bundle = entityContextBundle(context, type, key, false, limitPerSection);
            Map<String, Object> one = new LinkedHashMap<>();
            one.put("entityType", type);
            one.put("entityKey", key);
            one.put("exists", bundle.get("exists"));
            one.put("counts", sectionCounts(bundle));
            one.put("bundle", bundle);
            comparisons.add(one);
        }
        return Map.of(
                "candidateCount", comparisons.size(),
                "candidates", comparisons,
                "guidance", "차이가 명확하지 않으면 사용자에게 식별 가능한 규격·시리즈·옵션을 확인하십시오."
        );
    }

    private List<Map<String, Object>> fuzzyEntityCandidates(RagAgentToolContext context, String expression, String entityType, int limit) {
        MapSqlParameterSource p = scope(context)
                .addValue("expression", expression)
                .addValue("entityType", nullable(entityType))
                .addValue("limit", limit);
        return jdbc.queryForList("""
                WITH candidates AS (
                    SELECT 'ALIAS' AS source_kind, entity_type, entity_key, alias AS display_value,
                           greatest(similarity(lower(alias), lower(:expression)), word_similarity(lower(:expression), lower(alias))) AS confidence
                    FROM rag_entity_alias
                    WHERE project_id=:projectId AND version_id=:versionId AND active=true
                      AND (:entityType IS NULL OR entity_type=:entityType)
                    UNION ALL
                    SELECT 'AGENT_ALIAS', entity_type, entity_id, alias_text,
                           greatest(similarity(lower(alias_text), lower(:expression)),
                                    word_similarity(lower(:expression), lower(alias_text)),
                                    similarity(lower(normalized_alias), lower(:expression)))
                    FROM rag_agent_entity_alias
                    WHERE project_id=:projectId AND (version_id IS NULL OR version_id=:versionId) AND active=true
                      AND (:entityType IS NULL OR entity_type=:entityType)
                    UNION ALL
                    SELECT 'CANONICAL_ENTITY', entity_type, entity_key, coalesce(display_name, entity_key),
                           greatest(similarity(lower(coalesce(display_name, entity_key)), lower(:expression)),
                                    similarity(lower(entity_key), lower(:expression)))
                    FROM rag_canonical_entity
                    WHERE project_id=:projectId AND version_id=:versionId AND active=true
                      AND (:entityType IS NULL OR entity_type=:entityType)
                    UNION ALL
                    SELECT 'KNOWLEDGE_NODE', node_type, node_key, coalesce(title, node_key),
                           greatest(similarity(lower(coalesce(title, node_key)), lower(:expression)),
                                    similarity(lower(node_key), lower(:expression)))
                    FROM rag_knowledge_node
                    WHERE project_id=:projectId AND version_id=:versionId AND active=true
                      AND (:entityType IS NULL OR node_type=:entityType)
                    UNION ALL
                    SELECT 'SEMANTIC_MEMORY', coalesce(entity_type,'DYNAMIC'), coalesce(entity_key,title), title,
                           greatest(similarity(lower(title), lower(:expression)),
                                    similarity(lower(coalesce(entity_key,'')), lower(:expression)))
                    FROM rag_semantic_memory
                    WHERE project_id=:projectId AND version_id=:versionId AND active=true
                      AND (:entityType IS NULL OR entity_type=:entityType)
                )
                SELECT source_kind, entity_type, entity_key, display_value,
                       round(max(confidence)::numeric, 4) AS confidence,
                       count(*) AS evidence_count
                FROM candidates
                WHERE confidence > 0
                GROUP BY source_kind, entity_type, entity_key, display_value
                ORDER BY confidence DESC, evidence_count DESC, display_value
                LIMIT :limit
                """, p);
    }

    private List<Map<String, Object>> lexicalEntityCandidates(RagAgentToolContext context, String expression, String entityType, int limit) {
        String pattern = "%" + expression + "%";
        MapSqlParameterSource p = scope(context)
                .addValue("expression", expression)
                .addValue("pattern", pattern)
                .addValue("entityType", nullable(entityType))
                .addValue("limit", limit);
        return jdbc.queryForList("""
                WITH candidates AS (
                    SELECT 'ALIAS' AS source_kind, entity_type, entity_key, alias AS display_value,
                           CASE WHEN lower(alias)=lower(:expression) THEN 1.0 ELSE 0.75 END AS confidence
                    FROM rag_entity_alias
                    WHERE project_id=:projectId AND version_id=:versionId AND active=true
                      AND (:entityType IS NULL OR entity_type=:entityType)
                      AND (lower(alias) LIKE lower(:pattern) OR lower(entity_key) LIKE lower(:pattern))
                    UNION ALL
                    SELECT 'AGENT_ALIAS', entity_type, entity_id, alias_text,
                           CASE WHEN lower(alias_text)=lower(:expression) OR lower(normalized_alias)=lower(:expression) THEN 1.0 ELSE 0.78 END
                    FROM rag_agent_entity_alias
                    WHERE project_id=:projectId AND (version_id IS NULL OR version_id=:versionId) AND active=true
                      AND (:entityType IS NULL OR entity_type=:entityType)
                      AND (lower(alias_text) LIKE lower(:pattern) OR lower(normalized_alias) LIKE lower(:pattern) OR lower(entity_id) LIKE lower(:pattern))
                    UNION ALL
                    SELECT 'CANONICAL_ENTITY', entity_type, entity_key, coalesce(display_name,entity_key),
                           CASE WHEN lower(entity_key)=lower(:expression) OR lower(coalesce(display_name,''))=lower(:expression) THEN 1.0 ELSE 0.70 END
                    FROM rag_canonical_entity
                    WHERE project_id=:projectId AND version_id=:versionId AND active=true
                      AND (:entityType IS NULL OR entity_type=:entityType)
                      AND (lower(entity_key) LIKE lower(:pattern) OR lower(coalesce(display_name,'')) LIKE lower(:pattern))
                    UNION ALL
                    SELECT 'KNOWLEDGE_NODE', node_type, node_key, coalesce(title,node_key),
                           CASE WHEN lower(node_key)=lower(:expression) THEN 0.95 ELSE 0.65 END
                    FROM rag_knowledge_node
                    WHERE project_id=:projectId AND version_id=:versionId AND active=true
                      AND (:entityType IS NULL OR node_type=:entityType)
                      AND (lower(node_key) LIKE lower(:pattern) OR lower(coalesce(title,'')) LIKE lower(:pattern))
                )
                SELECT source_kind, entity_type, entity_key, display_value, confidence, 1 AS evidence_count
                FROM candidates ORDER BY confidence DESC, display_value LIMIT :limit
                """, p);
    }

    private List<Map<String, Object>> dialogRules(MapSqlParameterSource p) {
        return jdbc.queryForList("""
                SELECT id, topic, rule_key, rule_type, entity_type, entity_key, step_key, field_name,
                       priority, condition_json, action_json, validation_json, pricing_json,
                       confidence, active, updated_at
                FROM rag_dialog_rule
                WHERE project_id=:projectId AND version_id=:versionId
                  AND (:entityType IS NULL OR entity_type=:entityType)
                  AND (:entityKey IS NULL OR entity_key='' OR lower(entity_key)=lower(:entityKey))
                  AND (:includeInactive=true OR active=true)
                  AND (cardinality(CAST(:ruleTypes AS text[]))=0 OR upper(rule_type)=ANY(CAST(:ruleTypes AS text[])))
                ORDER BY active DESC, priority ASC, updated_at DESC LIMIT :limit
                """, ensureRuleParams(p));
    }

    private List<Map<String, Object>> pricingRules(MapSqlParameterSource p) {
        return jdbc.queryForList("""
                SELECT id, entity_type, entity_key, option_field, option_value,
                       base_width, base_height, base_depth, base_price,
                       width_step, width_step_price, height_step, height_step_price,
                       depth_step, depth_step_price, min_width, max_width, currency,
                       reason, confidence, active, updated_at
                FROM rag_structured_pricing_rule
                WHERE project_id=:projectId AND version_id=:versionId
                  AND (:entityType IS NULL OR entity_type=:entityType)
                  AND (:entityKey IS NULL OR lower(entity_key)=lower(:entityKey))
                  AND (:includeInactive=true OR active=true)
                ORDER BY active DESC, confidence DESC, updated_at DESC LIMIT :limit
                """, ensureRuleParams(p));
    }

    private List<Map<String, Object>> overrideRules(MapSqlParameterSource p) {
        return jdbc.queryForList("""
                SELECT id, entity_type, entity_key, field_name, rule_type, rule_value,
                       reason, confidence, active, updated_at
                FROM rag_structured_override_rule
                WHERE project_id=:projectId AND version_id=:versionId
                  AND (:entityType IS NULL OR entity_type=:entityType)
                  AND (:entityKey IS NULL OR lower(entity_key)=lower(:entityKey))
                  AND (:includeInactive=true OR active=true)
                  AND (cardinality(CAST(:ruleTypes AS text[]))=0 OR upper(rule_type)=ANY(CAST(:ruleTypes AS text[])))
                ORDER BY active DESC, confidence DESC, updated_at DESC LIMIT :limit
                """, ensureRuleParams(p));
    }

    private List<Map<String, Object>> canonicalFacts(MapSqlParameterSource p, LocalDate date) {
        p.addValue("effectiveDate", date);
        return jdbc.queryForList("""
                SELECT id, dataset_id, entity_id, entity_type, subject_key, subject_name,
                       fact_type, fact_key, factor_json, value_json, effective_from, effective_to,
                       status, active, confidence, source_table_id, source_row_id, source_json, updated_at
                FROM rag_canonical_fact
                WHERE project_id=:projectId AND version_id=:versionId
                  AND (:entityType IS NULL OR entity_type=:entityType)
                  AND (:entityKey IS NULL OR lower(subject_key)=lower(:entityKey))
                  AND (:includeInactive=true OR active=true)
                  AND (effective_from IS NULL OR effective_from <= :effectiveDate)
                  AND (effective_to IS NULL OR effective_to >= :effectiveDate)
                  AND (cardinality(CAST(:ruleTypes AS text[]))=0 OR upper(fact_type)=ANY(CAST(:ruleTypes AS text[])))
                ORDER BY active DESC, confidence DESC, effective_from DESC NULLS LAST, updated_at DESC
                LIMIT :limit
                """, ensureRuleParams(p));
    }

    private MapSqlParameterSource ensureRuleParams(MapSqlParameterSource p) {
        if (!p.hasValue("entityType")) p.addValue("entityType", null);
        if (!p.hasValue("entityKey")) p.addValue("entityKey", null);
        if (!p.hasValue("includeInactive")) p.addValue("includeInactive", false);
        if (!p.hasValue("ruleTypes")) p.addValue("ruleTypes", List.of());
        if (!p.hasValue("limit")) p.addValue("limit", 100);
        return p;
    }

    private void collectRequiredFields(Object value, Set<String> requiredFields) {
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> e : map.entrySet()) {
                String key = String.valueOf(e.getKey());
                Object child = e.getValue();
                if (List.of("requiredFields", "required_fields", "required", "필수항목").contains(key)
                        && child instanceof Collection<?> collection) {
                    for (Object field : collection) if (field != null && StringUtils.hasText(String.valueOf(field))) {
                        requiredFields.add(String.valueOf(field).trim());
                    }
                }
                if (List.of("fieldName", "field_name").contains(key) && child != null
                        && Boolean.TRUE.equals(map.get("required"))) {
                    requiredFields.add(String.valueOf(child).trim());
                }
                collectRequiredFields(child, requiredFields);
            }
        } else if (value instanceof Collection<?> collection) {
            for (Object item : collection) collectRequiredFields(item, requiredFields);
        }
    }

    @SuppressWarnings("unchecked")
    private void validateDimensions(Map<String, Object> rules, Map<String, Object> state, List<Map<String, Object>> conflicts) {
        Object pricing = rules.get("pricingRules");
        if (!(pricing instanceof List<?> rows)) return;
        for (Object rowObject : rows) {
            if (!(rowObject instanceof Map<?, ?> row)) continue;
            validateMinMax("width", firstValue(state, "width", "W", "넓이", "폭"), row.get("min_width"), row.get("max_width"), row, conflicts);
        }
    }

    private void validateOverrideRules(Map<String, Object> rules, Map<String, Object> state, List<Map<String, Object>> conflicts) {
        Object overrides = rules.get("overrideRules");
        if (!(overrides instanceof List<?> rows)) return;
        for (Object rowObject : rows) {
            if (!(rowObject instanceof Map<?, ?> row)) continue;
            String field = nullable(row.get("field_name"));
            String type = nullable(row.get("rule_type"));
            String expected = nullable(row.get("rule_value"));
            if (!StringUtils.hasText(field) || !StringUtils.hasText(type)) continue;
            Object actual = state.get(field);
            if (actual == null) continue;
            if (List.of("FORBIDDEN", "DISALLOW", "EXCLUDE", "금지").contains(type.toUpperCase())
                    && String.valueOf(actual).equalsIgnoreCase(expected)) {
                conflicts.add(Map.of(
                        "field", field,
                        "actual", actual,
                        "ruleType", type,
                        "ruleValue", expected,
                        "reason", row.get("reason") == null ? "금지 조건과 충돌합니다." : row.get("reason")
                ));
            }
        }
    }

    private void validateMinMax(String field, Object actualValue, Object minValue, Object maxValue,
                                Map<?, ?> source, List<Map<String, Object>> conflicts) {
        BigDecimal actual = decimal(actualValue, null);
        BigDecimal min = decimal(minValue, null);
        BigDecimal max = decimal(maxValue, null);
        if (actual == null) return;
        if (min != null && actual.compareTo(min) < 0) {
            conflicts.add(Map.of("field", field, "actual", actual, "minimum", min, "source", copy(source)));
        }
        if (max != null && actual.compareTo(max) > 0) {
            conflicts.add(Map.of("field", field, "actual", actual, "maximum", max, "source", copy(source)));
        }
    }

    private Object firstValue(Map<String, Object> state, String... keys) {
        for (String key : keys) if (state.containsKey(key) && hasValue(state.get(key))) return state.get(key);
        return null;
    }

    private boolean hasValue(Object value) {
        if (value == null) return false;
        if (value instanceof String s) return StringUtils.hasText(s);
        if (value instanceof Collection<?> c) return !c.isEmpty();
        if (value instanceof Map<?, ?> m) return !m.isEmpty();
        return true;
    }

    private Map<String, Object> sectionCounts(Map<String, Object> bundle) {
        Map<String, Object> counts = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : bundle.entrySet()) {
            if (e.getValue() instanceof Collection<?> c) counts.put(e.getKey(), c.size());
        }
        return counts;
    }

    private void persistResolution(RagAgentToolContext context,
                                   String expression,
                                   String entityType,
                                   List<Map<String, Object>> candidates,
                                   Map<String, Object> result) {
        try {
            jdbc.update("""
                    INSERT INTO rag_agent_entity_resolution(
                        id, run_id, project_id, version_id, session_id, source_scope,
                        input_expression, entity_type_hint, candidates_json,
                        resolution_status, selected_entity_type, selected_entity_key, confidence
                    ) VALUES (
                        :id, :runId, :projectId, :versionId, :sessionId, :sourceScope,
                        :expression, :entityType, CAST(:candidates AS jsonb),
                        :status, :selectedType, :selectedKey, :confidence
                    )
                    """, scope(context)
                    .addValue("id", UUID.randomUUID())
                    .addValue("runId", context.runId())
                    .addValue("sessionId", context.sessionId())
                    .addValue("sourceScope", context.sourceScope())
                    .addValue("expression", expression)
                    .addValue("entityType", nullable(entityType))
                    .addValue("candidates", RagJsonUtils.toJson(objectMapper, candidates))
                    .addValue("status", Boolean.TRUE.equals(result.get("resolved")) ? "RESOLVED" : "AMBIGUOUS")
                    .addValue("selectedType", Boolean.TRUE.equals(result.get("resolved")) ? candidates.get(0).get("entity_type") : null)
                    .addValue("selectedKey", Boolean.TRUE.equals(result.get("resolved")) ? candidates.get(0).get("entity_key") : null)
                    .addValue("confidence", candidates.isEmpty() ? BigDecimal.ZERO : decimal(candidates.get(0).get("confidence"), BigDecimal.ZERO)));
        } catch (Exception ignored) {
            // 메타 감사 저장 실패는 본 도구 조회를 막지 않습니다.
        }
    }

    private void persistOrderSnapshot(RagAgentToolContext context,
                                      Map<String, Object> orderState,
                                      Map<String, Object> validation) {
        if (context.sessionId() == null) return;
        try {
            jdbc.update("""
                    INSERT INTO rag_agent_order_state_snapshot(
                        id, run_id, project_id, version_id, session_id, source_scope,
                        order_state_json, validation_json, missing_fields_json, conflicts_json, valid
                    ) VALUES (
                        :id, :runId, :projectId, :versionId, :sessionId, :sourceScope,
                        CAST(:state AS jsonb), CAST(:validation AS jsonb),
                        CAST(:missing AS jsonb), CAST(:conflicts AS jsonb), :valid
                    )
                    """, scope(context)
                    .addValue("id", UUID.randomUUID())
                    .addValue("runId", context.runId())
                    .addValue("sessionId", context.sessionId())
                    .addValue("sourceScope", context.sourceScope())
                    .addValue("state", RagJsonUtils.toJson(objectMapper, orderState))
                    .addValue("validation", RagJsonUtils.toJson(objectMapper, validation))
                    .addValue("missing", RagJsonUtils.toJson(objectMapper, validation.getOrDefault("missingFields", List.of())))
                    .addValue("conflicts", RagJsonUtils.toJson(objectMapper, validation.getOrDefault("conflicts", List.of())))
                    .addValue("valid", Boolean.TRUE.equals(validation.get("valid"))));
        } catch (Exception ignored) {
        }
    }

    private MapSqlParameterSource scope(RagAgentToolContext context) {
        return new MapSqlParameterSource()
                .addValue("projectId", context.projectId())
                .addValue("versionId", context.versionId());
    }

    private List<String> upperList(List<String> values) {
        if (values == null) return List.of();
        return values.stream().filter(StringUtils::hasText).map(v -> v.trim().toUpperCase()).distinct().toList();
    }

    private String nullable(Object value) {
        if (value == null) return null;
        String text = String.valueOf(value).trim();
        return StringUtils.hasText(text) && !"null".equalsIgnoreCase(text) ? text : null;
    }

    private BigDecimal decimal(Object value, BigDecimal fallback) {
        if (value == null) return fallback;
        if (value instanceof BigDecimal bd) return bd;
        if (value instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try { return new BigDecimal(String.valueOf(value)); }
        catch (Exception e) { return fallback; }
    }

    private Map<String, Object> copy(Map<?, ?> raw) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : raw.entrySet()) result.put(String.valueOf(e.getKey()), e.getValue());
        return result;
    }
}
