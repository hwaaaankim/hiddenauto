package com.dev.HiddenBATHAuto.rag.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.dev.HiddenBATHAuto.rag.util.RagJsonUtils;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class RagKnowledgeRetrievalService {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final RagStructuredOverrideRuleService overrideRuleService;

    public RagKnowledgeRetrievalService(@Qualifier("ragJdbcTemplate") NamedParameterJdbcTemplate jdbc,
                                        ObjectMapper objectMapper,
                                        RagStructuredOverrideRuleService overrideRuleService) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.overrideRuleService = overrideRuleService;
    }

    public List<Map<String, Object>> findEntityCandidates(UUID projectId, UUID versionId, String message) {
        String clean = message == null ? "" : message;
        Set<String> names = new LinkedHashSet<>();
        names.addAll(findProductNamesFromRows(projectId, versionId, clean));
        names.addAll(findAliases(projectId, versionId, clean));
        names.addAll(findNodeTitles(projectId, versionId, clean));

        List<Map<String, Object>> result = new ArrayList<>();
        for (String name : names) {
            if (!StringUtils.hasText(name)) continue;
            int score = candidateScore(clean, name);
            if (score <= 0 && !clean.contains(name)) continue;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("entityType", "PRODUCT");
            item.put("name", name);
            item.put("score", score);
            item.put("matchedInMessage", clean.contains(name));
            result.add(item);
        }
        result.sort(Comparator.comparingInt((Map<String, Object> m) -> intValue(m.get("score"))).reversed()
                .thenComparing(m -> String.valueOf(m.get("name")).length()));
        if (result.size() > 30) return new ArrayList<>(result.subList(0, 30));
        return result;
    }

    public Map<String, Object> retrieve(UUID projectId, UUID versionId, Map<String, Object> plan, String query) {
        Map<String, Object> result = new LinkedHashMap<>();

        String intent = text(plan.get("intentType"));
        Map<String, Object> retrievalPlan = childMap(plan, "retrievalPlan");
        String queryScope = text(retrievalPlan.get("queryScope"));

        String rawEntityKey = firstNonBlank(
                childText(plan, "primaryEntity", "name"),
                childText(plan, "updateRule", "entityKey")
        );

        int rowLimit = intValue(retrievalPlan.get("rowLimit"), 160);
        boolean includeRelatedAsSuggestion = boolValue(retrievalPlan.get("includeRelatedAsSuggestion"), true);
        boolean includeRelatedInMainAnswer = boolValue(retrievalPlan.get("includeRelatedInMainAnswer"), false);

        /*
         * 중요:
         * GPT Structured Outputs가 전체 조회 문장에 대해 queryScope=ALL_PRODUCTS를 잘 내려주더라도,
         * 동시에 primaryEntity.name에 "모든 제품", "제품", "모든제품정보" 같은 값을 넣을 수 있습니다.
         *
         * 그 값을 실제 제품명으로 사용하면:
         *   제품명 = '모든제품정보'
         * 로 조회되어 결과가 0개가 됩니다.
         *
         * 따라서 queryScope가 전체 범위이거나,
         * 문장 자체가 전체 제품 조회로 보이면 entityKey를 반드시 비워야 합니다.
         */
        boolean globalProductOverview =
                "ALL_PRODUCTS".equals(queryScope)
                        || isGlobalProductOverviewQuery(intent, query);

        String entityKey = rawEntityKey;
        if (globalProductOverview || isGlobalScope(queryScope)) {
            entityKey = "";
        }

        List<Map<String, Object>> exactRows = StringUtils.hasText(entityKey)
                ? findStructuredRowsExact(projectId, versionId, entityKey, rowLimit)
                : List.of();

        List<Map<String, Object>> fallbackRows = exactRows.isEmpty()
                ? (globalProductOverview
                    ? findStructuredRowsAll(projectId, versionId, Math.max(rowLimit, 500))
                    : findStructuredRowsByText(projectId, versionId, entityKey, query, rowLimit))
                : List.of();

        List<Map<String, Object>> sourceRows = exactRows.isEmpty() ? fallbackRows : exactRows;

        List<Map<String, Object>> rules = overrideRuleService.findActiveRules(projectId, versionId, entityKey);
        List<Map<String, Object>> excludedRows = overrideRuleService.findExcludedRows(sourceRows, rules);
        List<Map<String, Object>> effectiveRows = overrideRuleService.applyRulesToRows(sourceRows, rules);

        List<Map<String, Object>> relatedRows = new ArrayList<>();
        if (includeRelatedAsSuggestion && StringUtils.hasText(entityKey)) {
            relatedRows = findRelatedStructuredRows(
                    projectId,
                    versionId,
                    entityKey,
                    includeRelatedInMainAnswer ? rowLimit : 60
            );
        }

        List<Map<String, Object>> artifacts = findArtifacts(projectId, versionId, query, entityKey, 20);
        List<Map<String, Object>> nodes = findKnowledgeNodes(projectId, versionId, query, entityKey, 80);
        List<Map<String, Object>> chunks = findChunks(projectId, versionId, query, entityKey, 20);
        List<Map<String, Object>> assets = findLinkedAssets(projectId, versionId, entityKey, 20);
        List<Map<String, Object>> pricingRules = findPricingRules(
                projectId,
                versionId,
                entityKey,
                globalProductOverview ? 500 : 100
        );
        List<Map<String, Object>> dialogRules = findDialogRules(
                projectId,
                versionId,
                entityKey,
                globalProductOverview ? 500 : 150
        );

        result.put("intentType", intent);
        result.put("entityKey", entityKey);
        result.put("requestedFields", plan.getOrDefault("requestedFields", List.of()));
        result.put("queryScope", queryScope);

        Map<String, Object> debugRetrieval = new LinkedHashMap<>();
        debugRetrieval.put("queryScope", queryScope);
        debugRetrieval.put("rawEntityKey", rawEntityKey);
        debugRetrieval.put("effectiveEntityKey", entityKey);
        debugRetrieval.put("globalProductOverview", globalProductOverview);
        result.put("debugRetrieval", debugRetrieval);

        result.put("exactRows", exactRows);
        result.put("fallbackRows", fallbackRows);
        result.put("effectiveRows", effectiveRows);
        result.put("excludedRows", excludedRows);
        result.put("relatedRows", relatedRows);
        result.put("activeOverrideRules", rules);
        result.put("artifacts", artifacts);
        result.put("knowledgeNodes", nodes);
        result.put("chunks", chunks);
        result.put("assets", assets);
        result.put("pricingRules", pricingRules);
        result.put("dialogRules", dialogRules);
        result.put("availabilitySummary", availabilitySummary(effectiveRows));
        result.put("relatedSummary", relatedSummary(entityKey, relatedRows));

        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("exactRows", exactRows.size());
        counts.put("fallbackRows", fallbackRows.size());
        counts.put("effectiveRows", effectiveRows.size());
        counts.put("excludedRows", excludedRows.size());
        counts.put("relatedRows", relatedRows.size());
        counts.put("rules", rules.size());
        counts.put("artifacts", artifacts.size());
        counts.put("nodes", nodes.size());
        counts.put("chunks", chunks.size());
        counts.put("assets", assets.size());
        counts.put("pricingRules", pricingRules.size());
        counts.put("dialogRules", dialogRules.size());
        result.put("counts", counts);

        return result;
    }

    private List<Map<String, Object>> findDialogRules(UUID projectId, UUID versionId, String entityKey, int limit) {
        String sql = """
                SELECT id, topic, rule_key, rule_type, entity_type, entity_key, step_key, field_name,
                       priority, condition_json::text AS condition_json, action_json::text AS action_json,
                       validation_json::text AS validation_json, pricing_json::text AS pricing_json,
                       confidence, source_message, active, created_at, updated_at
                FROM rag_dialog_rule
                WHERE project_id = :projectId
                  AND version_id = :versionId
                  AND active = true
                  AND (:entityKey = '' OR entity_key = :entityKey OR entity_key = '')
                ORDER BY priority ASC, updated_at DESC
                LIMIT :limit
                """;
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(sql, new MapSqlParameterSource()
                    .addValue("projectId", projectId)
                    .addValue("versionId", versionId)
                    .addValue("entityKey", StringUtils.hasText(entityKey) ? entityKey : "")
                    .addValue("limit", Math.max(1, limit)));
            return rows.stream().map(this::normalizeJsonRow).toList();
        } catch (DataAccessException e) {
            return List.of();
        }
    }

    private Map<String, Object> availabilitySummary(List<Map<String, Object>> rows) {
        Set<String> products = new LinkedHashSet<>();
        Set<String> colors = new LinkedHashSet<>();
        Set<String> sizes = new LinkedHashSet<>();
        Set<String> prices = new LinkedHashSet<>();
        for (Map<String, Object> row : rows == null ? List.<Map<String, Object>>of() : rows) {
            products.add(firstText(row, "제품명", "품목", "productName", "name"));
            colors.add(firstText(row, "색상", "색", "컬러", "color", "Color", "COLOR"));
            sizes.add(firstText(row, "사이즈", "규격", "크기", "size", "Size", "SIZE"));
            prices.add(firstText(row, "금액", "가격", "단가", "price", "Price", "PRICE"));
        }
        products.removeIf(s -> !StringUtils.hasText(s));
        colors.removeIf(s -> !StringUtils.hasText(s));
        sizes.removeIf(s -> !StringUtils.hasText(s));
        prices.removeIf(s -> !StringUtils.hasText(s));
        return Map.of("products", List.copyOf(products), "colors", List.copyOf(colors), "sizes", List.copyOf(sizes), "prices", List.copyOf(prices));
    }

    private Map<String, Object> relatedSummary(String entityKey, List<Map<String, Object>> rows) {
        Set<String> relatedProducts = new LinkedHashSet<>();
        for (Map<String, Object> row : rows == null ? List.<Map<String, Object>>of() : rows) {
            String p = firstText(row, "제품명", "품목", "productName", "name");
            if (StringUtils.hasText(p) && !p.equals(entityKey)) relatedProducts.add(p);
        }
        return Map.of("products", List.copyOf(relatedProducts));
    }

    private List<String> findProductNamesFromRows(UUID projectId, UUID versionId, String message) {
        String sql = """
                SELECT DISTINCT value
                FROM (
                    SELECT NULLIF(row_json->>'제품명', '') AS value
                    FROM rag_structured_table_row r
                    JOIN rag_structured_table t ON t.id = r.table_id
                    JOIN rag_knowledge_artifact a ON a.id = t.artifact_id
                    WHERE a.project_id = :projectId AND a.version_id = :versionId AND a.active = true AND t.active = true
                    UNION
                    SELECT NULLIF(row_json->>'품목', '') AS value
                    FROM rag_structured_table_row r
                    JOIN rag_structured_table t ON t.id = r.table_id
                    JOIN rag_knowledge_artifact a ON a.id = t.artifact_id
                    WHERE a.project_id = :projectId AND a.version_id = :versionId AND a.active = true AND t.active = true
                    UNION
                    SELECT NULLIF(row_json->>'productName', '') AS value
                    FROM rag_structured_table_row r
                    JOIN rag_structured_table t ON t.id = r.table_id
                    JOIN rag_knowledge_artifact a ON a.id = t.artifact_id
                    WHERE a.project_id = :projectId AND a.version_id = :versionId AND a.active = true AND t.active = true
                ) s
                WHERE value IS NOT NULL
                ORDER BY value
                LIMIT 500
                """;
        try {
            List<String> all = jdbc.queryForList(sql, Map.of("projectId", projectId, "versionId", versionId), String.class);
            return rankNames(all, message);
        } catch (DataAccessException e) {
            return List.of();
        }
    }

    private List<String> findAliases(UUID projectId, UUID versionId, String message) {
        String sql = """
                SELECT DISTINCT entity_key AS value
                FROM rag_entity_alias
                WHERE project_id = :projectId AND version_id = :versionId AND active = true
                  AND (:query = '' OR alias ILIKE :like OR entity_key ILIKE :like)
                ORDER BY value
                LIMIT 200
                """;
        try {
            return rankNames(jdbc.queryForList(sql, new MapSqlParameterSource()
                    .addValue("projectId", projectId)
                    .addValue("versionId", versionId)
                    .addValue("query", StringUtils.hasText(message) ? message : "")
                    .addValue("like", "%" + (message == null ? "" : message) + "%"), String.class), message);
        } catch (DataAccessException e) {
            return List.of();
        }
    }

    private List<String> findNodeTitles(UUID projectId, UUID versionId, String message) {
        String keyword = bestKeyword(message);
        if (!StringUtils.hasText(keyword)) return List.of();
        String sql = """
                SELECT DISTINCT COALESCE(NULLIF(node_key,''), NULLIF(title,'')) AS value
                FROM rag_knowledge_node
                WHERE project_id = :projectId AND version_id = :versionId AND active = true
                  AND (title ILIKE :like OR summary ILIKE :like OR raw_text ILIKE :like)
                LIMIT 100
                """;
        try {
            return rankNames(jdbc.queryForList(sql, new MapSqlParameterSource()
                    .addValue("projectId", projectId)
                    .addValue("versionId", versionId)
                    .addValue("like", "%" + keyword + "%"), String.class), message);
        } catch (DataAccessException e) {
            return List.of();
        }
    }

    private List<Map<String, Object>> findStructuredRowsExact(UUID projectId, UUID versionId, String entityKey, int limit) {
        String sql = """
                SELECT a.topic, a.artifact_key, a.semantic_role, a.title, a.original_filename,
                       t.table_key, t.sheet_name, t.header_json::text AS header_json,
                       r.row_no, r.row_json::text AS row_json, r.searchable_text
                FROM rag_knowledge_artifact a
                JOIN rag_structured_table t ON t.artifact_id = a.id AND t.active = true
                JOIN rag_structured_table_row r ON r.table_id = t.id
                WHERE a.project_id = :projectId
                  AND a.version_id = :versionId
                  AND a.active = true
                  AND (
                    r.row_json->>'제품명' = :entityKey OR
                    r.row_json->>'품목' = :entityKey OR
                    r.row_json->>'productName' = :entityKey OR
                    r.row_json->>'name' = :entityKey
                  )
                ORDER BY a.created_at DESC, t.created_at DESC, r.row_no ASC
                LIMIT :limit
                """;
        return queryRows(sql, new MapSqlParameterSource()
                .addValue("projectId", projectId)
                .addValue("versionId", versionId)
                .addValue("entityKey", entityKey)
                .addValue("limit", Math.max(1, limit)));
    }


    private List<Map<String, Object>> findStructuredRowsAll(UUID projectId, UUID versionId, int limit) {
        String sql = """
                SELECT a.topic, a.artifact_key, a.semantic_role, a.title, a.original_filename,
                       t.table_key, t.sheet_name, t.header_json::text AS header_json,
                       r.row_no, r.row_json::text AS row_json, r.searchable_text
                FROM rag_knowledge_artifact a
                JOIN rag_structured_table t ON t.artifact_id = a.id AND t.active = true
                JOIN rag_structured_table_row r ON r.table_id = t.id
                WHERE a.project_id = :projectId
                  AND a.version_id = :versionId
                  AND a.active = true
                ORDER BY a.created_at DESC, t.created_at DESC, r.row_no ASC
                LIMIT :limit
                """;
        return queryRows(sql, new MapSqlParameterSource()
                .addValue("projectId", projectId)
                .addValue("versionId", versionId)
                .addValue("limit", Math.max(1, limit)));
    }

    private List<Map<String, Object>> findStructuredRowsByText(UUID projectId, UUID versionId, String entityKey, String query, int limit) {
        String keyword = StringUtils.hasText(entityKey) ? entityKey : bestKeyword(query);
        String sql = """
                SELECT a.topic, a.artifact_key, a.semantic_role, a.title, a.original_filename,
                       t.table_key, t.sheet_name, t.header_json::text AS header_json,
                       r.row_no, r.row_json::text AS row_json, r.searchable_text
                FROM rag_knowledge_artifact a
                JOIN rag_structured_table t ON t.artifact_id = a.id AND t.active = true
                JOIN rag_structured_table_row r ON r.table_id = t.id
                WHERE a.project_id = :projectId
                  AND a.version_id = :versionId
                  AND a.active = true
                  AND (:keyword IS NULL OR r.searchable_text ILIKE :like OR r.row_json::text ILIKE :like)
                ORDER BY a.created_at DESC, t.created_at DESC, r.row_no ASC
                LIMIT :limit
                """;
        return queryRows(sql, new MapSqlParameterSource()
                .addValue("projectId", projectId)
                .addValue("versionId", versionId)
                .addValue("keyword", StringUtils.hasText(keyword) ? keyword : null)
                .addValue("like", "%" + (keyword == null ? "" : keyword) + "%")
                .addValue("limit", Math.max(1, limit)));
    }

    private List<Map<String, Object>> findRelatedStructuredRows(UUID projectId, UUID versionId, String entityKey, int limit) {
        String sql = """
                SELECT a.topic, a.artifact_key, a.semantic_role, a.title, a.original_filename,
                       t.table_key, t.sheet_name, t.header_json::text AS header_json,
                       r.row_no, r.row_json::text AS row_json, r.searchable_text
                FROM rag_knowledge_artifact a
                JOIN rag_structured_table t ON t.artifact_id = a.id AND t.active = true
                JOIN rag_structured_table_row r ON r.table_id = t.id
                WHERE a.project_id = :projectId
                  AND a.version_id = :versionId
                  AND a.active = true
                  AND r.searchable_text ILIKE :like
                  AND NOT (
                    r.row_json->>'제품명' = :entityKey OR
                    r.row_json->>'품목' = :entityKey OR
                    r.row_json->>'productName' = :entityKey OR
                    r.row_json->>'name' = :entityKey
                  )
                ORDER BY a.created_at DESC, t.created_at DESC, r.row_no ASC
                LIMIT :limit
                """;
        return queryRows(sql, new MapSqlParameterSource()
                .addValue("projectId", projectId)
                .addValue("versionId", versionId)
                .addValue("entityKey", entityKey)
                .addValue("like", "%" + entityKey + "%")
                .addValue("limit", Math.max(1, limit)));
    }

    private List<Map<String, Object>> queryRows(String sql, MapSqlParameterSource params) {
        try {
            return jdbc.queryForList(sql, params).stream().map(this::normalizeJsonRow).toList();
        } catch (DataAccessException e) {
            return List.of(Map.of("error", "구조화 행 조회 실패", "message", e.getMessage()));
        }
    }

    private List<Map<String, Object>> findArtifacts(UUID projectId, UUID versionId, String query, String entityKey, int limit) {
        String keyword = StringUtils.hasText(entityKey) ? entityKey : bestKeyword(query);
        String sql = """
                SELECT id::text, topic, artifact_key, semantic_role, title, original_filename,
                       metadata_json::text AS metadata_json, created_at
                FROM rag_knowledge_artifact
                WHERE project_id = :projectId AND version_id = :versionId AND active = true
                  AND (:keyword IS NULL OR title ILIKE :like OR artifact_key ILIKE :like OR semantic_role ILIKE :like OR metadata_json::text ILIKE :like)
                ORDER BY created_at DESC
                LIMIT :limit
                """;
        return queryGeneric(sql, new MapSqlParameterSource()
                .addValue("projectId", projectId)
                .addValue("versionId", versionId)
                .addValue("keyword", StringUtils.hasText(keyword) ? keyword : null)
                .addValue("like", "%" + (keyword == null ? "" : keyword) + "%")
                .addValue("limit", limit));
    }

    private List<Map<String, Object>> findKnowledgeNodes(UUID projectId, UUID versionId, String query, String entityKey, int limit) {
        String keyword = StringUtils.hasText(entityKey) ? entityKey : bestKeyword(query);
        String sql = """
                SELECT id::text, topic, node_type, node_key, title, summary, raw_text,
                       structured_json::text AS structured_json, metadata_json::text AS metadata_json,
                       interpretation_status, status, created_at
                FROM rag_knowledge_node
                WHERE project_id = :projectId
                  AND version_id = :versionId
                  AND active = true
                  AND (:keyword IS NULL OR title ILIKE :like OR summary ILIKE :like OR raw_text ILIKE :like OR structured_json::text ILIKE :like)
                ORDER BY depth ASC, sort_order ASC, created_at ASC
                LIMIT :limit
                """;
        return queryGeneric(sql, new MapSqlParameterSource()
                .addValue("projectId", projectId)
                .addValue("versionId", versionId)
                .addValue("keyword", StringUtils.hasText(keyword) ? keyword : null)
                .addValue("like", "%" + (keyword == null ? "" : keyword) + "%")
                .addValue("limit", limit));
    }

    private List<Map<String, Object>> findChunks(UUID projectId, UUID versionId, String query, String entityKey, int limit) {
        String keyword = StringUtils.hasText(entityKey) ? entityKey : bestKeyword(query);
        String sql = """
                SELECT c.id::text, c.topic, c.content, c.metadata::text AS metadata,
                       d.title AS document_title, d.original_filename, c.created_at
                FROM rag_chunk c
                JOIN rag_document d ON d.id = c.document_id
                WHERE c.project_id = :projectId
                  AND c.version_id = :versionId
                  AND (:keyword IS NULL OR c.content ILIKE :like OR d.title ILIKE :like OR d.raw_text ILIKE :like)
                ORDER BY c.created_at DESC
                LIMIT :limit
                """;
        return queryGeneric(sql, new MapSqlParameterSource()
                .addValue("projectId", projectId)
                .addValue("versionId", versionId)
                .addValue("keyword", StringUtils.hasText(keyword) ? keyword : null)
                .addValue("like", "%" + (keyword == null ? "" : keyword) + "%")
                .addValue("limit", limit));
    }

    private List<Map<String, Object>> findLinkedAssets(UUID projectId, UUID versionId, String entityKey, int limit) {
        String sql = """
                SELECT a.id::text, a.original_filename, a.content_type, a.file_url,
                       a.asset_kind, a.semantic_caption, a.linked_entity_type, a.linked_entity_key,
                       l.entity_type, l.entity_key, l.display_name, l.confidence, l.created_at
                FROM rag_asset a
                LEFT JOIN rag_entity_asset_link l ON l.asset_id = a.id AND l.status = 'ACTIVE'
                WHERE a.project_id = :projectId AND a.version_id = :versionId
                  AND (:entityKey IS NULL OR a.linked_entity_key = :entityKey OR l.entity_key = :entityKey OR a.semantic_caption ILIKE :like)
                ORDER BY COALESCE(l.created_at, a.created_at) DESC
                LIMIT :limit
                """;
        return queryGeneric(sql, new MapSqlParameterSource()
                .addValue("projectId", projectId)
                .addValue("versionId", versionId)
                .addValue("entityKey", StringUtils.hasText(entityKey) ? entityKey : null)
                .addValue("like", "%" + (entityKey == null ? "" : entityKey) + "%")
                .addValue("limit", limit));
    }


    private List<Map<String, Object>> findPricingRules(UUID projectId, UUID versionId, String entityKey, int limit) {
        String sql = """
                SELECT id::text, entity_type, entity_key, option_field, option_value,
                       base_width, base_height, base_depth, base_price,
                       width_step, width_step_price, height_step, height_step_price,
                       depth_step, depth_step_price, currency, reason, source_message,
                       active, updated_at
                FROM rag_structured_pricing_rule
                WHERE project_id = :projectId
                  AND version_id = :versionId
                  AND active = true
                  AND (:entityKey IS NULL OR entity_key = :entityKey)
                ORDER BY entity_key ASC, option_field ASC, option_value ASC, updated_at DESC
                LIMIT :limit
                """;
        return queryGeneric(sql, new MapSqlParameterSource()
                .addValue("projectId", projectId)
                .addValue("versionId", versionId)
                .addValue("entityKey", StringUtils.hasText(entityKey) ? entityKey : null)
                .addValue("limit", Math.max(1, limit)));
    }

    private List<Map<String, Object>> queryGeneric(String sql, MapSqlParameterSource params) {
        try {
            return jdbc.queryForList(sql, params).stream().map(this::normalizeJsonRow).toList();
        } catch (DataAccessException e) {
            return List.of();
        }
    }

    private Map<String, Object> normalizeJsonRow(Map<String, Object> row) {
        Map<String, Object> copy = new LinkedHashMap<>(row);
        for (String key : List.of("row_json", "header_json", "structured_json", "metadata_json", "metadata", "plan_json", "condition_json", "action_json", "validation_json", "pricing_json")) {
            Object value = copy.get(key);
            if (value != null && value instanceof String s && s.trim().startsWith("{")) {
                copy.put(key, RagJsonUtils.toMap(objectMapper, s));
            }
        }
        Object rowJson = copy.get("row_json");
        if (rowJson instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (e.getKey() != null && !copy.containsKey(String.valueOf(e.getKey()))) {
                    copy.put(String.valueOf(e.getKey()), e.getValue());
                }
            }
        }
        return copy;
    }

    private List<String> rankNames(List<String> names, String message) {
        List<String> clean = names == null ? new ArrayList<>() : names.stream()
                .filter(StringUtils::hasText)
                .distinct()
                .sorted(Comparator.comparingInt((String n) -> candidateScore(message, n)).reversed().thenComparingInt(String::length))
                .toList();
        return clean.size() > 80 ? new ArrayList<>(clean.subList(0, 80)) : clean;
    }

    private int candidateScore(String message, String name) {
        if (!StringUtils.hasText(name)) return 0;
        String msg = message == null ? "" : message;
        if (msg.equals(name)) return 1000;
        if (msg.contains(name)) return 800 - name.length();
        String compactMsg = normalize(msg);
        String compactName = normalize(name);
        if (compactMsg.contains(compactName)) return 700 - compactName.length();
        if (compactName.contains(compactMsg) && compactMsg.length() >= 2) return 400 - compactName.length();
        return 0;
    }


    private boolean isGlobalProductOverviewQuery(String intent, String query) {
        if (!"ASK_KNOWLEDGE_SUMMARY".equals(intent)) return false;

        String compact = query == null ? "" : query.replaceAll("\\s+", "");

        return compact.contains("모든제품")
                || compact.contains("전체제품")
                || compact.contains("제품들의정보")
                || compact.contains("제품의정보")
                || compact.contains("제품정보")
                || compact.contains("상품정보")
                || compact.contains("품목정보")
                || compact.contains("등록된제품")
                || compact.contains("저장된제품")
                || compact.contains("저장된모든제품")
                || compact.contains("올라가있는모든제품");
    }

    private boolean isGlobalScope(String queryScope) {
        return "ALL_PRODUCTS".equals(queryScope)
                || "ALL_KNOWLEDGE".equals(queryScope)
                || "PRICING_RULES".equals(queryScope)
                || "ASSETS".equals(queryScope);
    }	
    
    private String bestKeyword(String query) {
        if (!StringUtils.hasText(query)) return "";
        String cleaned = query.replaceAll("(저장된|데이터|지식|학습된|내용|설명|보여|알려|가능한|가능|색상|색|사이즈|규격|뭐뭐|무엇|어떤|\\?|,|\\.)", " ").trim();
        String[] parts = cleaned.split("\\s+");
        String best = "";
        for (String p : parts) {
            if (p.length() > best.length()) best = p;
        }
        return best;
    }

    private String firstText(Map<String, Object> row, String... keys) {
        if (row == null) return "";
        for (String key : keys) {
            Object v = row.get(key);
            if (v != null && StringUtils.hasText(String.valueOf(v))) return String.valueOf(v).trim();
        }
        return "";
    }

    private String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").trim().toUpperCase(Locale.ROOT);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> childMap(Map<String, Object> map, String key) {
        Object value = map == null ? null : map.get(key);
        if (value instanceof Map<?, ?> m) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (e.getKey() != null) result.put(String.valueOf(e.getKey()), e.getValue());
            }
            return result;
        }
        return new LinkedHashMap<>();
    }

    private String childText(Map<String, Object> map, String child, String key) {
        return text(childMap(map, child).get(key));
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) return value;
        }
        return "";
    }

    private int intValue(Object value) {
        return intValue(value, 0);
    }

    private int intValue(Object value, int fallback) {
        if (value instanceof Number n) return n.intValue();
        try {
            return value == null ? fallback : Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return fallback;
        }
    }

    private boolean boolValue(Object value, boolean fallback) {
        if (value instanceof Boolean b) return b;
        if (value == null) return fallback;
        String s = String.valueOf(value);
        if ("true".equalsIgnoreCase(s) || "1".equals(s) || "Y".equalsIgnoreCase(s)) return true;
        if ("false".equalsIgnoreCase(s) || "0".equals(s) || "N".equalsIgnoreCase(s)) return false;
        return fallback;
    }
}
