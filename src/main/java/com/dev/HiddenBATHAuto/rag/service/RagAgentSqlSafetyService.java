package com.dev.HiddenBATHAuto.rag.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.dev.HiddenBATHAuto.rag.config.RagOpenAiProperties;

/**
 * GPT DB Tool Agent가 작성한 SQL을 실행 전에 검증합니다.
 *
 * <p>핵심 정책:</p>
 * <ul>
 *   <li>직접 조회는 SELECT/WITH만 허용합니다.</li>
 *   <li>조회 대상은 public.rag_*, information_schema, 제한된 pg_catalog만 허용합니다.</li>
 *   <li>프로젝트/버전 범위 컬럼이 있는 각 RAG 테이블 별칭마다 범위 조건을 강제합니다.</li>
 *   <li>변경은 ChangeSet을 통해 INSERT/UPDATE/DELETE만 허용합니다.</li>
 *   <li>세션/감사/Agent 제어 테이블은 GPT가 직접 변경할 수 없습니다.</li>
 *   <li>물리 DELETE는 id = :targetId 단건만 허용합니다.</li>
 * </ul>
 *
 * <p>이 검증기는 SQL 파서 대체물이 아니라 방어 계층입니다. DB 계정 권한, 읽기 전용
 * 트랜잭션, statement timeout, ChangeSet 승인 정책과 함께 사용해야 합니다.</p>
 */
@Service
public class RagAgentSqlSafetyService {

    private static final int DEFAULT_MAX_ROWS = 200;

    private static final String SQL_IDENTIFIER = "\\\"?[a-zA-Z_][a-zA-Z0-9_]*\\\"?";
    private static final String QUALIFIED_SQL_IDENTIFIER = "(?:" + SQL_IDENTIFIER + "\\.)?" + SQL_IDENTIFIER;

    private static final Pattern TABLE_PATTERN = Pattern.compile(
            "(?i)\\b(?:from|join)\\s+(" + QUALIFIED_SQL_IDENTIFIER + ")"
    );

    /** FROM/JOIN 테이블과 선택적 별칭을 함께 추출합니다. */
    private static final Pattern TABLE_REFERENCE_PATTERN = Pattern.compile(
            "(?i)\\b(?:from|join)\\s+(" + QUALIFIED_SQL_IDENTIFIER + ")"
                    + "(?:\\s+(?:as\\s+)?(" + SQL_IDENTIFIER + "))?"
    );

    private static final Pattern CTE_PATTERN = Pattern.compile(
            "(?i)(?:\\bwith\\s+(?:recursive\\s+)?|,)\\s*(" + SQL_IDENTIFIER + ")\\s+as\\s*\\("
    );

    private static final Pattern UPDATE_TARGET_PATTERN = Pattern.compile(
            "(?i)^\\s*update\\s+(" + QUALIFIED_SQL_IDENTIFIER + ")"
    );
    private static final Pattern INSERT_TARGET_PATTERN = Pattern.compile(
            "(?i)^\\s*insert\\s+into\\s+(" + QUALIFIED_SQL_IDENTIFIER + ")"
    );
    private static final Pattern DELETE_TARGET_PATTERN = Pattern.compile(
            "(?i)^\\s*delete\\s+from\\s+(" + QUALIFIED_SQL_IDENTIFIER + ")"
    );

    private static final Pattern PARAM_PATTERN = Pattern.compile("(?<!:):([A-Za-z][A-Za-z0-9_]*)");
    private static final Pattern LIMIT_PATTERN = Pattern.compile("(?i)\\blimit\\s+(\\d+)");
    private static final Pattern DOLLAR_QUOTE_PATTERN = Pattern.compile("\\$[A-Za-z0-9_]*\\$");

    private static final Pattern READ_DANGEROUS_PATTERN = Pattern.compile(
            "(?i)\\b(insert|update|delete|drop|truncate|alter|create|grant|revoke|copy|call|do|execute|merge|"
                    + "vacuum|refresh|reindex|listen|notify|reset|into)\\b"
    );

    private static final Pattern READ_LOCK_PATTERN = Pattern.compile(
            "(?i)\\bfor\\s+(?:update|no\\s+key\\s+update|share|key\\s+share)\\b"
    );

    private static final Pattern WRITE_DANGEROUS_PATTERN = Pattern.compile(
            "(?i)\\b(drop|truncate|alter|create|grant|revoke|copy|call|do|execute|merge|"
                    + "vacuum|refresh|reindex|listen|notify|reset)\\b"
    );

    private static final Pattern BLOCKED_FUNCTION_PATTERN = Pattern.compile(
            "(?i)\\b(?:pg_sleep|pg_read_file|pg_read_binary_file|pg_ls_dir|pg_stat_file|"
                    + "lo_import|lo_export|dblink|dblink_connect|dblink_exec|"
                    + "postgres_fdw_get_connections|pg_terminate_backend|pg_cancel_backend|"
                    + "set_config|current_setting)\\s*\\("
    );

    /** 정규식 범위검증 우회를 막기 위해 자유 SQL에서 논리 분기/집합 연산을 보수적으로 차단합니다. */
    private static final Pattern SCOPE_BYPASS_PATTERN = Pattern.compile(
            "(?i)\\b(?:or|not|union|intersect|except)\\b"
    );

    private static final Set<String> ALLOWED_INFORMATION_SCHEMA_TABLES = Set.of(
            "schemata", "tables", "columns", "views", "sequences",
            "table_constraints", "key_column_usage", "constraint_column_usage",
            "referential_constraints", "check_constraints", "triggers", "routines", "parameters"
    );

    private static final Set<String> ALLOWED_PG_CATALOG_TABLES = Set.of(
            "pg_class", "pg_attribute", "pg_description", "pg_namespace", "pg_index", "pg_indexes",
            "pg_constraint", "pg_extension", "pg_stat_user_tables", "pg_tables", "pg_views",
            "pg_type", "pg_enum"
    );

    private static final Set<String> BLOCKED_META_TABLES = Set.of(
            "pg_authid", "pg_auth_members", "pg_shadow", "pg_user", "pg_roles", "pg_seclabel",
            "pg_db_role_setting", "pg_settings", "pg_hba_file_rules"
    );

    /** 별칭이 없는데 뒤의 SQL 키워드가 별칭처럼 정규식에 잡히는 것을 제거합니다. */
    private static final Set<String> SQL_KEYWORDS = Set.of(
            "where", "join", "left", "right", "full", "inner", "outer", "cross", "on", "using",
            "group", "order", "limit", "offset", "fetch", "union", "intersect", "except", "having",
            "window", "for", "returning", "set", "values"
    );

    /** GPT가 직접 수정해서는 안 되는 시스템/세션/감사/실행제어 테이블입니다. */
    private static final Set<String> PROTECTED_WRITE_TABLES = Set.of(
            "rag_project", "rag_project_version",
            "rag_learning_session", "rag_learning_message", "rag_learning_job", "rag_learning_job_file",
            "rag_learning_job_log", "rag_chat_session", "rag_chat_message", "rag_inquiry", "rag_reset_event",
            "rag_interaction_event", "rag_conversation_working_memory",
            "rag_agent_run", "rag_agent_sql_query", "rag_agent_change_set",
            "rag_agent_change_item", "rag_agent_file_stage", "rag_agent_tool_call", "rag_agent_schema_note",
            "rag_canonical_job", "rag_canonical_job_log", "rag_canonical_nl_parse_log",
            "rag_canonical_quote_log", "rag_gpt_final_answer_log", "rag_semantic_resolution_event",
            "rag_canonical_change_event", "rag_canonical_quality_issue", "rag_knowledge_query_cache",
            "rag_asset"
    );

    private final RagAgentSchemaService schemaService;
    private final RagOpenAiProperties properties;

    public RagAgentSqlSafetyService(RagAgentSchemaService schemaService,
                                    RagOpenAiProperties properties) {
        this.schemaService = schemaService;
        this.properties = properties;
    }

    public ValidatedSql validateReadSql(String sql) {
        return validateReadSql(sql, DEFAULT_MAX_ROWS, "LEARNING");
    }

    public ValidatedSql validateReadSql(String sql, int requestedMaxRows) {
        return validateReadSql(sql, requestedMaxRows, "LEARNING");
    }

    public ValidatedSql validateReadSql(String sql, int requestedMaxRows, String sourceScope) {
        String normalized = normalize(sql);
        ensureSingleStatement(normalized);
        ensureNoComment(normalized);
        ensureNoDollarQuote(normalized);
        ensureReadOnly(normalized);

        TableAccess access = inspectReadTables(normalized);
        ensureReadAccessPolicy(access.ragTableRefs(), sourceScope);
        ensureScopeForReferencedTables(normalized, access.ragTableRefs());
        ensureSpecialAndIndirectScope(normalized, access.ragTableRefs());
        ensureAllowedParams(normalized, allowedReadParams());

        return new ValidatedSql(wrapLimit(normalized, requestedMaxRows), access.warnings());
    }

    public ValidatedSql validateWriteSql(String sql) {
        String normalized = normalize(sql);
        ensureSingleStatement(normalized);
        ensureNoComment(normalized);
        ensureNoDollarQuote(normalized);

        String lower = normalized.toLowerCase(Locale.ROOT);
        if (!(lower.startsWith("update ") || lower.startsWith("insert into ") || lower.startsWith("delete from "))) {
            throw new IllegalArgumentException("GPT 변경 SQL은 UPDATE, INSERT INTO, DELETE FROM만 허용합니다.");
        }

        String masked = maskStringLiterals(normalized);
        if (WRITE_DANGEROUS_PATTERN.matcher(masked).find()) {
            throw new IllegalArgumentException("DDL/권한/실행 계열 위험 키워드가 포함되어 변경 SQL을 차단했습니다.");
        }
        if (BLOCKED_FUNCTION_PATTERN.matcher(masked).find()) {
            throw new IllegalArgumentException("파일/외부접속/세션제어 계열 PostgreSQL 함수는 변경 SQL에서 허용하지 않습니다.");
        }
        if (SCOPE_BYPASS_PATTERN.matcher(masked).find()) {
            throw new IllegalArgumentException("변경 SQL에서는 OR/NOT/UNION/INTERSECT/EXCEPT를 허용하지 않습니다.");
        }

        String target = normalizedTableName(writeTarget(normalized));
        if (!StringUtils.hasText(target) || !target.startsWith("rag_") || !schemaService.tableExists(target)) {
            throw new IllegalArgumentException("변경은 존재하는 public.rag_* 테이블만 허용합니다: " + target);
        }
        if (PROTECTED_WRITE_TABLES.contains(target)) {
            throw new IllegalArgumentException("Agent가 직접 변경할 수 없는 시스템/세션/감사 테이블입니다: " + target);
        }

        ensureNoWriteTargetAlias(normalized, target);
        if ((lower.startsWith("update ") || lower.startsWith("delete from "))
                && Pattern.compile("(?i)\b(?:from|join|using)\b").matcher(masked).find()) {
            throw new IllegalArgumentException("Agent UPDATE/DELETE는 FROM/JOIN/USING 없이 단일 대상 테이블만 변경할 수 있습니다.");
        }
        List<RagTableRef> sourceRefs = ensureOnlyRagTablesInWrite(normalized);
        ensureScopeForReferencedTables(normalized, sourceRefs);
        if (RagAgentDataAccessPolicy.indirectScopeRule(target).isPresent()) {
            ensureIndirectWriteScope(normalized, target);
        } else {
            ensureWriteScope(normalized, target);
        }
        ensureAllowedParams(normalized, allowedWriteParams());

        if (lower.startsWith("insert into ")) {
            ensureInsertValuesOnly(normalized);
        }
        if (lower.startsWith("update ")) {
            ensureWhere(normalized, "UPDATE");
        }
        if (lower.startsWith("delete from ")) {
            ensureWhere(normalized, "DELETE");
            ensureDeleteIsSingleTarget(normalized);
        }

        return new ValidatedSql(normalized, List.of());
    }

    private String normalize(String sql) {
        if (!StringUtils.hasText(sql)) {
            throw new IllegalArgumentException("SQL이 비어 있습니다.");
        }
        String normalized = sql.trim();
        if (normalized.endsWith(";")) {
            normalized = normalized.substring(0, normalized.length() - 1).trim();
        }
        if (!StringUtils.hasText(normalized)) {
            throw new IllegalArgumentException("SQL이 비어 있습니다.");
        }
        if (normalized.length() > properties.getAgentMaxSqlChars()) {
            throw new IllegalArgumentException("SQL 길이가 Agent 허용 최대값(" + properties.getAgentMaxSqlChars() + "자)을 초과했습니다.");
        }
        return normalized;
    }

    private void ensureSingleStatement(String sql) {
        if (sql.contains(";")) {
            throw new IllegalArgumentException("다중 SQL 문장은 허용하지 않습니다.");
        }
    }

    private void ensureNoComment(String sql) {
        if (sql.contains("--") || sql.contains("/*") || sql.contains("*/")) {
            throw new IllegalArgumentException("SQL 주석은 안전검증 우회 가능성이 있어 허용하지 않습니다.");
        }
    }

    private void ensureNoDollarQuote(String sql) {
        if (DOLLAR_QUOTE_PATTERN.matcher(sql).find()) {
            throw new IllegalArgumentException("PostgreSQL dollar-quoted 문자열은 안전검증 우회 가능성이 있어 허용하지 않습니다.");
        }
    }

    private void ensureReadOnly(String sql) {
        String lower = sql.toLowerCase(Locale.ROOT);
        if (!(lower.startsWith("select ") || lower.startsWith("with "))) {
            throw new IllegalArgumentException("직접 조회 SQL은 SELECT/WITH만 허용합니다.");
        }

        String masked = maskStringLiterals(sql);
        if (READ_DANGEROUS_PATTERN.matcher(masked).find()) {
            throw new IllegalArgumentException("조회 SQL에 쓰기/DDL/세션변경 키워드가 포함되어 차단했습니다.");
        }
        if (READ_LOCK_PATTERN.matcher(masked).find()) {
            throw new IllegalArgumentException("조회 도구에서는 FOR UPDATE/SHARE 잠금을 사용할 수 없습니다.");
        }
        if (BLOCKED_FUNCTION_PATTERN.matcher(masked).find()) {
            throw new IllegalArgumentException("파일/외부접속/지연/세션제어 계열 PostgreSQL 함수는 조회 도구에서 허용하지 않습니다.");
        }
        if (SCOPE_BYPASS_PATTERN.matcher(masked).find()) {
            throw new IllegalArgumentException("자유 조회 SQL에서는 OR/NOT/UNION/INTERSECT/EXCEPT를 허용하지 않습니다. 여러 조회로 나누어 실행하십시오.");
        }
    }

    private TableAccess inspectReadTables(String sql) {
        Set<String> ctes = extractCtes(sql);
        Set<RagTableRef> ragRefs = new LinkedHashSet<>();
        List<String> warnings = new ArrayList<>();

        Matcher matcher = TABLE_REFERENCE_PATTERN.matcher(maskStringLiterals(sql));
        boolean found = false;
        while (matcher.find()) {
            String raw = matcher.group(1);
            if (!StringUtils.hasText(raw) || raw.startsWith("(")) {
                continue;
            }

            String alias = normalizeAlias(matcher.group(2));
            TableToken token = parseTableToken(raw);
            if (ctes.contains(token.table())) {
                found = true;
                continue;
            }

            found = true;
            if (token.isAgentScopedRagView()) {
                if (!schemaService.tableExists(token.table())) {
                    throw new IllegalArgumentException("대응하는 public RAG 테이블이 없는 Agent 보안 뷰입니다: " + raw);
                }
                String qualifier = StringUtils.hasText(alias) ? alias : token.table();
                ragRefs.add(new RagTableRef(token.table(), qualifier, true));
                continue;
            }
            if (token.isRagTable()) {
                throw new IllegalArgumentException(
                        "GPT 자유 조회는 public 원본 테이블을 직접 읽을 수 없습니다. rag_agent_view."
                                + token.table() + " 보안 뷰를 사용하십시오."
                );
            }
            if (token.isInformationSchemaAllowed()) {
                warnings.add("information_schema 구조 메타데이터 조회 포함: " + token.fullName());
                continue;
            }
            if (token.isInformationSchema()) {
                throw new IllegalArgumentException(
                        "계정/권한 관련 노출을 막기 위해 허용된 information_schema 구조 테이블만 조회할 수 있습니다: "
                                + token.fullName()
                );
            }
            if (token.isPgCatalogAllowed()) {
                warnings.add("pg_catalog 메타데이터 조회 포함: " + token.fullName());
                continue;
            }

            throw new IllegalArgumentException(
                    "허용되지 않은 조회 대상입니다. rag_agent_view.rag_*, information_schema, 제한된 pg_catalog만 허용합니다: " + raw
            );
        }

        if (!found) {
            throw new IllegalArgumentException("조회 대상 테이블을 찾지 못했습니다.");
        }
        return new TableAccess(List.copyOf(ragRefs), List.copyOf(warnings));
    }

    private Set<String> extractCtes(String sql) {
        Set<String> result = new LinkedHashSet<>();
        Matcher matcher = CTE_PATTERN.matcher(maskStringLiterals(sql));
        while (matcher.find()) {
            result.add(stripIdentifierQuotes(matcher.group(1)).toLowerCase(Locale.ROOT));
        }
        return result;
    }

    private void ensureReadAccessPolicy(List<RagTableRef> refs, String sourceScope) {
        for (RagTableRef ref : refs) {
            if (RagAgentDataAccessPolicy.isChatReadBlocked(ref.table(), sourceScope)) {
                throw new IllegalArgumentException(
                        "소비자 CHAT 범위에서는 내부 관리자/감사/다른 대화 테이블을 자유 SQL로 조회할 수 없습니다: "
                                + ref.table()
                );
            }
        }
    }

    /** 직접 scope 컬럼이 없는 자식 테이블과 project/version 기준 테이블의 범위를 추가 검증합니다. */
    private void ensureSpecialAndIndirectScope(String sql, List<RagTableRef> refs) {
        String lower = maskStringLiterals(sql).toLowerCase(Locale.ROOT).replace("\"", "");
        for (RagTableRef ref : refs) {
            if (ref.scopedView()) {
                continue;
            }
            if ("rag_project".equals(ref.table())
                    && !hasScopePredicate(lower, ref.qualifier(), "id", "projectid", refs.size() == 1)) {
                throw new IllegalArgumentException("rag_project 조회에는 " + ref.qualifier() + ".id = :projectId가 필요합니다.");
            }
            if ("rag_project_version".equals(ref.table())
                    && !hasScopePredicate(lower, ref.qualifier(), "id", "versionid", refs.size() == 1)) {
                throw new IllegalArgumentException("rag_project_version 조회에는 " + ref.qualifier() + ".id = :versionId가 필요합니다.");
            }

            RagAgentDataAccessPolicy.indirectScopeRule(ref.table()).ifPresent(rule -> {
                List<RagTableRef> parents = refs.stream()
                        .filter(candidate -> rule.parentTable().equals(candidate.table()))
                        .toList();
                if (parents.isEmpty()) {
                    throw new IllegalArgumentException(ref.table() + "은 project_id/version_id가 직접 없으므로 "
                            + rule.parentTable() + "과 범위 조인이 필요합니다.");
                }
                boolean joined = parents.stream().anyMatch(parent -> hasColumnEquality(
                        lower,
                        ref.qualifier(), rule.childForeignKey(),
                        parent.qualifier(), rule.parentPrimaryKey()));
                if (!joined) {
                    throw new IllegalArgumentException(ref.table() + " 조회에는 " + ref.qualifier() + "."
                            + rule.childForeignKey() + " = " + rule.parentTable() + "."
                            + rule.parentPrimaryKey() + " 조인이 필요합니다.");
                }
            });
        }
    }

    private boolean hasColumnEquality(String lowerSql,
                                      String leftQualifier,
                                      String leftColumn,
                                      String rightQualifier,
                                      String rightColumn) {
        String left = qualifiedColumnPattern(leftQualifier, leftColumn);
        String right = qualifiedColumnPattern(rightQualifier, rightColumn);
        return Pattern.compile("(?i)" + left + "\\s*=\\s*" + right).matcher(lowerSql).find()
                || Pattern.compile("(?i)" + right + "\\s*=\\s*" + left).matcher(lowerSql).find();
    }

    private String qualifiedColumnPattern(String qualifier, String column) {
        return "(?<![A-Za-z0-9_])" + Pattern.quote(qualifier.toLowerCase(Locale.ROOT))
                + "\\s*\\.\\s*" + Pattern.quote(column.toLowerCase(Locale.ROOT))
                + "(?![A-Za-z0-9_])";
    }

    /**
     * 프로젝트/버전 컬럼이 있는 각 실제 RAG 테이블 별칭마다 범위 조건을 요구합니다.
     * 한 개의 범위 대상 테이블만 있을 때는 비수식 컬럼 조건도 허용합니다.
     */
    private void ensureScopeForReferencedTables(String sql, List<RagTableRef> ragRefs) {
        if (ragRefs == null || ragRefs.isEmpty()) {
            return;
        }

        String lower = maskStringLiterals(sql).toLowerCase(Locale.ROOT).replace("\"", "");
        long projectScopedCount = ragRefs.stream()
                .filter(ref -> !ref.scopedView() && schemaService.hasColumn(ref.table(), "project_id"))
                .count();
        long versionScopedCount = ragRefs.stream()
                .filter(ref -> !ref.scopedView() && schemaService.hasColumn(ref.table(), "version_id"))
                .count();

        for (RagTableRef ref : ragRefs) {
            if (ref.scopedView()) {
                continue;
            }
            if (schemaService.hasColumn(ref.table(), "project_id")
                    && !hasScopePredicate(lower, ref.qualifier(), "project_id", "projectid", projectScopedCount == 1)) {
                throw new IllegalArgumentException(
                        "테이블 " + ref.table() + "(" + ref.qualifier() + ")에 "
                                + ref.qualifier() + ".project_id = :projectId 범위 조건이 필요합니다."
                );
            }
            if (schemaService.hasColumn(ref.table(), "version_id")
                    && !hasScopePredicate(lower, ref.qualifier(), "version_id", "versionid", versionScopedCount == 1)) {
                throw new IllegalArgumentException(
                        "테이블 " + ref.table() + "(" + ref.qualifier() + ")에 "
                                + ref.qualifier() + ".version_id = :versionId 범위 조건이 필요합니다."
                );
            }
        }
    }

    private boolean hasScopePredicate(String lowerSql,
                                      String qualifier,
                                      String column,
                                      String parameter,
                                      boolean allowUnqualified) {
        String qualifierToken = Pattern.quote(qualifier.toLowerCase(Locale.ROOT));
        String columnToken = Pattern.quote(column.toLowerCase(Locale.ROOT));
        String paramToken = Pattern.quote(":" + parameter.toLowerCase(Locale.ROOT));

        String qualifiedColumn = "(?<![A-Za-z0-9_])" + qualifierToken
                + "\\s*\\.\\s*" + columnToken + "(?![A-Za-z0-9_])";
        Pattern forward = Pattern.compile("(?i)" + qualifiedColumn + "\\s*=\\s*" + paramToken + "(?![A-Za-z0-9_])");
        Pattern reverse = Pattern.compile("(?i)(?<![A-Za-z0-9_])" + paramToken
                + "\\s*=\\s*" + qualifiedColumn);
        if (forward.matcher(lowerSql).find() || reverse.matcher(lowerSql).find()) {
            return true;
        }

        if (!allowUnqualified) {
            return false;
        }

        String unqualifiedColumn = "(?<![A-Za-z0-9_\\.])" + columnToken + "(?![A-Za-z0-9_])";
        Pattern unqualifiedForward = Pattern.compile(
                "(?i)" + unqualifiedColumn + "\\s*=\\s*" + paramToken + "(?![A-Za-z0-9_])"
        );
        Pattern unqualifiedReverse = Pattern.compile(
                "(?i)(?<![A-Za-z0-9_])" + paramToken + "\\s*=\\s*" + unqualifiedColumn
        );
        return unqualifiedForward.matcher(lowerSql).find() || unqualifiedReverse.matcher(lowerSql).find();
    }

    /** 변경 SQL의 FROM/JOIN 대상도 public.rag_*만 허용하고 그 별칭 목록을 반환합니다. */
    private List<RagTableRef> ensureOnlyRagTablesInWrite(String sql) {
        Set<String> ctes = extractCtes(sql);
        Set<RagTableRef> refs = new LinkedHashSet<>();
        Matcher matcher = TABLE_REFERENCE_PATTERN.matcher(maskStringLiterals(sql));

        while (matcher.find()) {
            String raw = matcher.group(1);
            if (!StringUtils.hasText(raw) || raw.startsWith("(")) {
                continue;
            }
            TableToken token = parseTableToken(raw);
            if (ctes.contains(token.table())) {
                continue;
            }
            if (!token.isRagTable() || !schemaService.tableExists(token.table())) {
                throw new IllegalArgumentException(
                        "변경 SQL 내부 조회도 존재하는 public.rag_* 테이블만 허용합니다: " + raw
                );
            }
            String alias = normalizeAlias(matcher.group(2));
            refs.add(new RagTableRef(token.table(), StringUtils.hasText(alias) ? alias : token.table(), false));
        }
        return List.copyOf(refs);
    }

    private void ensureWriteScope(String sql, String targetTable) {
        String lower = maskStringLiterals(sql).toLowerCase(Locale.ROOT).replace("\"", "");
        boolean insert = lower.startsWith("insert into ");
        boolean update = lower.startsWith("update ");

        if (update) {
            int setIndex = lower.indexOf(" set ");
            int whereIndex = lower.lastIndexOf(" where ");
            if (setIndex >= 0 && whereIndex > setIndex) {
                String setClause = lower.substring(setIndex + 5, whereIndex);
                if (Pattern.compile("(?i)\\b(project_id|version_id)\\s*=").matcher(setClause).find()) {
                    throw new IllegalArgumentException(
                            "Agent UPDATE는 project_id/version_id 범위 컬럼을 변경할 수 없습니다."
                    );
                }
            }
            ensureSimpleSingleTargetWhere(sql, targetTable, "UPDATE");
        }

        if (schemaService.hasColumn(targetTable, "project_id")) {
            if (insert) {
                ensureInsertScopeColumn(sql, "project_id", "projectId");
            } else if (!hasScopePredicate(lower, targetTable, "project_id", "projectid", true)) {
                throw new IllegalArgumentException("변경 WHERE 조건에 project_id = :projectId가 필요합니다.");
            }
        }
        if (schemaService.hasColumn(targetTable, "version_id")) {
            if (insert) {
                ensureInsertScopeColumn(sql, "version_id", "versionId");
            } else if (!hasScopePredicate(lower, targetTable, "version_id", "versionid", true)) {
                throw new IllegalArgumentException("변경 WHERE 조건에 version_id = :versionId가 필요합니다.");
            }
        }
    }

    private void ensureSafeUpdateSetClause(String targetTable, String setClause) {
        List<String> assignments = splitTopLevelComma(setClause);
        if (assignments.isEmpty()) throw new IllegalArgumentException("UPDATE SET 절이 비어 있습니다.");
        Set<String> seenColumns = new LinkedHashSet<>();
        for (String assignment : assignments) {
            int equals = assignment.indexOf('=');
            if (equals <= 0 || assignment.indexOf('=', equals + 1) >= 0) {
                throw new IllegalArgumentException("UPDATE SET은 단순 column = value 형식만 허용합니다: " + assignment);
            }
            String column = stripIdentifierQuotes(assignment.substring(0, equals)).trim().toLowerCase(Locale.ROOT);
            String value = assignment.substring(equals + 1).trim();
            if (!column.matches("[a-z_][a-z0-9_]*") || !schemaService.hasColumn(targetTable, column)) {
                throw new IllegalArgumentException("UPDATE 대상에 존재하지 않거나 허용되지 않은 컬럼입니다: " + column);
            }
            if (Set.of("id", "project_id", "version_id").contains(column)) {
                throw new IllegalArgumentException("UPDATE로 식별·범위 컬럼을 변경할 수 없습니다: " + column);
            }
            if (!seenColumns.add(column)) throw new IllegalArgumentException("UPDATE 컬럼이 중복되었습니다: " + column);
            ensureSafeInsertValue(value);
        }
    }

    /**
     * project_id/version_id가 직접 없는 자식 테이블의 쓰기 구조를 제한합니다.
     * 실제 상위 row의 현재 프로젝트/버전 소속은 Executor가 DB에서 다시 확인합니다.
     */
    private void ensureIndirectWriteScope(String sql, String targetTable) {
        RagAgentDataAccessPolicy.IndirectScopeRule rule = RagAgentDataAccessPolicy.indirectScopeRule(targetTable)
                .orElseThrow(() -> new IllegalArgumentException("간접 범위 규칙이 없습니다: " + targetTable));
        String lower = sql.trim().toLowerCase(Locale.ROOT);
        if (lower.startsWith("insert into ")) {
            String parameter = insertValueParameterForColumn(sql, rule.childForeignKey());
            if (!parameter.matches("p([1-9]|[1-4][0-9]|50)")) {
                throw new IllegalArgumentException(targetTable + " INSERT의 " + rule.childForeignKey()
                        + " 값은 :p1~:p50 named parameter여야 합니다.");
            }
            return;
        }
        ensureWhere(sql, lower.startsWith("delete from ") ? "DELETE" : "UPDATE");
        ensureSimpleSingleTargetWhere(sql, targetTable, lower.startsWith("delete from ") ? "DELETE" : "UPDATE");
    }

    /** INSERT VALUES에서 특정 컬럼에 대응하는 named parameter 이름을 반환합니다. */
    public String insertValueParameterForColumn(String sql, String requiredColumn) {
        InsertParts parts = parseInsertValues(normalize(sql));
        int index = -1;
        for (int i = 0; i < parts.columns().size(); i++) {
            if (requiredColumn.equalsIgnoreCase(stripIdentifierQuotes(parts.columns().get(i)).trim())) {
                index = i;
                break;
            }
        }
        if (index < 0 || index >= parts.values().size()) {
            throw new IllegalArgumentException("INSERT 컬럼 목록에 " + requiredColumn + "이 필요합니다.");
        }
        String value = parts.values().get(index).trim();
        Matcher matcher = Pattern.compile("^:([A-Za-z][A-Za-z0-9_]*)$").matcher(value);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("INSERT의 " + requiredColumn + " 값은 named parameter여야 합니다.");
        }
        return matcher.group(1);
    }

    private void ensureSimpleSingleTargetWhere(String sql, String targetTable, String operationName) {
        String lower = maskStringLiterals(sql).toLowerCase(Locale.ROOT).replace("\"", "");
        int whereIndex = lower.lastIndexOf(" where ");
        if (whereIndex < 0) {
            throw new IllegalArgumentException(operationName + "에는 WHERE 조건이 필요합니다.");
        }
        String whereClause = lower.substring(whereIndex + 7).trim();
        int returningIndex = whereClause.indexOf(" returning ");
        if (returningIndex >= 0) {
            whereClause = whereClause.substring(0, returningIndex).trim();
        }
        if (whereClause.indexOf('(') >= 0 || whereClause.indexOf(')') >= 0
                || Pattern.compile("(?i)\\b(or|not|select|with|exists|join|using|union|intersect|except|case|when|then|else|end)\\b")
                .matcher(whereClause).find()) {
            throw new IllegalArgumentException(operationName + "은 괄호/논리분기/서브쿼리 없이 단순 AND 조건만 허용합니다.");
        }

        boolean idFound = false;
        boolean projectFound = !schemaService.hasColumn(targetTable, "project_id");
        boolean versionFound = !schemaService.hasColumn(targetTable, "version_id");
        for (String rawTerm : whereClause.split("(?i)\\s+and\\s+")) {
            String term = rawTerm.trim().replaceAll("\\s+", "");
            if (term.equals("id=:targetid") || term.equals(":targetid=id")) {
                idFound = true;
            } else if (term.equals("project_id=:projectid") || term.equals(":projectid=project_id")) {
                projectFound = true;
            } else if (term.equals("version_id=:versionid") || term.equals(":versionid=version_id")) {
                versionFound = true;
            } else {
                throw new IllegalArgumentException(operationName
                        + " WHERE에는 id/project_id/version_id의 허용된 단건 조건만 사용할 수 있습니다: "
                        + rawTerm.trim());
            }
        }
        if (!idFound) {
            throw new IllegalArgumentException(operationName + "은 id = :targetId 단건 조건이 필요합니다.");
        }
        if (!projectFound || !versionFound) {
            throw new IllegalArgumentException(operationName + "은 현재 프로젝트/버전 범위 조건이 필요합니다.");
        }
    }

    /** INSERT 컬럼과 VALUES의 같은 위치에 범위 파라미터가 있는지 확인합니다. */
    private void ensureInsertScopeColumn(String sql, String requiredColumn, String requiredParameter) {
        InsertParts parts = parseInsertValues(sql);
        int index = -1;
        for (int i = 0; i < parts.columns().size(); i++) {
            if (requiredColumn.equalsIgnoreCase(stripIdentifierQuotes(parts.columns().get(i)).trim())) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            throw new IllegalArgumentException("INSERT 컬럼 목록에 " + requiredColumn + "이 필요합니다.");
        }
        if (index >= parts.values().size()) {
            throw new IllegalArgumentException("INSERT 컬럼 수와 VALUES 수가 일치하지 않습니다.");
        }
        String expected = ":" + requiredParameter;
        if (!expected.equalsIgnoreCase(parts.values().get(index).trim())) {
            throw new IllegalArgumentException(
                    "INSERT의 " + requiredColumn + " 값은 반드시 " + expected + "여야 합니다."
            );
        }
    }

    private void ensureInsertValuesOnly(String sql) {
        parseInsertValues(sql);
    }

    private InsertParts parseInsertValues(String sql) {
        Matcher targetMatcher = INSERT_TARGET_PATTERN.matcher(sql);
        if (!targetMatcher.find()) {
            throw new IllegalArgumentException("INSERT 대상 테이블을 찾지 못했습니다.");
        }

        int columnsOpen = sql.indexOf('(', targetMatcher.end());
        if (columnsOpen < 0) {
            throw new IllegalArgumentException("INSERT는 명시적인 컬럼 목록이 필요합니다.");
        }
        int columnsClose = findMatchingParenthesis(sql, columnsOpen);
        if (columnsClose < 0) {
            throw new IllegalArgumentException("INSERT 컬럼 목록 괄호가 올바르지 않습니다.");
        }

        Matcher valuesMatcher = Pattern.compile("(?i)\\bvalues\\s*\\(").matcher(sql);
        if (!valuesMatcher.find(columnsClose)) {
            throw new IllegalArgumentException("Agent INSERT는 INSERT ... VALUES (...) 형식만 허용합니다.");
        }
        int valuesOpen = sql.indexOf('(', valuesMatcher.start());
        int valuesClose = findMatchingParenthesis(sql, valuesOpen);
        if (valuesClose < 0) {
            throw new IllegalArgumentException("INSERT VALUES 괄호가 올바르지 않습니다.");
        }

        String tail = sql.substring(valuesClose + 1).trim();
        if (tail.startsWith(",")) {
            throw new IllegalArgumentException("한 ChangeSet 항목에서는 다중 VALUES 행 INSERT를 허용하지 않습니다.");
        }
        if (StringUtils.hasText(tail)) {
            throw new IllegalArgumentException("Agent INSERT는 VALUES 뒤에 RETURNING 또는 추가 SQL을 허용하지 않습니다.");
        }

        List<String> columns = splitTopLevelComma(sql.substring(columnsOpen + 1, columnsClose));
        List<String> values = splitTopLevelComma(sql.substring(valuesOpen + 1, valuesClose));
        if (columns.isEmpty() || columns.size() != values.size()) {
            throw new IllegalArgumentException("INSERT 컬럼 수와 VALUES 수가 일치하지 않습니다.");
        }
        String targetTable = normalizedTableName(targetMatcher.group(1));
        Set<String> seenColumns = new LinkedHashSet<>();
        for (int i = 0; i < columns.size(); i++) {
            String column = stripIdentifierQuotes(columns.get(i)).trim().toLowerCase(Locale.ROOT);
            if (!column.matches("[a-z_][a-z0-9_]*") || !schemaService.hasColumn(targetTable, column)) {
                throw new IllegalArgumentException("INSERT 대상에 존재하지 않거나 허용되지 않은 컬럼입니다: " + column);
            }
            if (!seenColumns.add(column)) {
                throw new IllegalArgumentException("INSERT 컬럼이 중복되었습니다: " + column);
            }
            ensureSafeInsertValue(values.get(i));
        }
        return new InsertParts(columns, values);
    }

    private void ensureSafeInsertValue(String rawValue) {
        String value = rawValue == null ? "" : rawValue.trim();
        if (value.matches("(?i)^:[A-Za-z][A-Za-z0-9_]*$")) return;
        if (value.matches("(?i)^(null|true|false|current_timestamp|now\\(\\)|gen_random_uuid\\(\\))$")) return;
        if (value.matches("^-?[0-9]+(?:\\.[0-9]+)?$")) return;
        if (value.matches("(?i)^cast\\(\\s*:[A-Za-z][A-Za-z0-9_]*\\s+as\\s+"
                + "(?:jsonb|json|uuid|text|varchar|integer|bigint|numeric|boolean|timestamptz|timestamp)\\s*\\)$")) return;
        if (value.matches("(?i)^:[A-Za-z][A-Za-z0-9_]*::"
                + "(?:jsonb|json|uuid|text|varchar|integer|bigint|numeric|boolean|timestamptz|timestamp)$")) return;
        throw new IllegalArgumentException(
                "INSERT VALUES에는 named parameter, NULL/boolean/숫자, 현재시각, gen_random_uuid 또는 안전한 CAST만 허용합니다: "
                        + value);
    }

    private int findMatchingParenthesis(String text, int openIndex) {
        int depth = 0;
        boolean inLiteral = false;
        for (int i = openIndex; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\'') {
                if (inLiteral && i + 1 < text.length() && text.charAt(i + 1) == '\'') {
                    i++;
                    continue;
                }
                inLiteral = !inLiteral;
                continue;
            }
            if (inLiteral) {
                continue;
            }
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private List<String> splitTopLevelComma(String text) {
        List<String> values = new ArrayList<>();
        int depth = 0;
        int start = 0;
        boolean inLiteral = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\'') {
                if (inLiteral && i + 1 < text.length() && text.charAt(i + 1) == '\'') {
                    i++;
                    continue;
                }
                inLiteral = !inLiteral;
                continue;
            }
            if (inLiteral) {
                continue;
            }
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            } else if (c == ',' && depth == 0) {
                values.add(text.substring(start, i).trim());
                start = i + 1;
            }
        }
        values.add(text.substring(start).trim());
        return values.stream().filter(StringUtils::hasText).toList();
    }

    private void ensureNoWriteTargetAlias(String sql, String targetTable) {
        String lower = maskStringLiterals(sql).toLowerCase(Locale.ROOT).replace("\"", "");
        if (lower.startsWith("update ")) {
            Pattern pattern = Pattern.compile(
                    "(?i)^\\s*update\\s+(?:public\\.)?" + Pattern.quote(targetTable)
                            + "\\s+(?:as\\s+)?([a-zA-Z_][a-zA-Z0-9_]*)\\s+set\\b"
            );
            if (pattern.matcher(lower).find()) {
                throw new IllegalArgumentException("Agent UPDATE 대상 테이블 별칭은 허용하지 않습니다.");
            }
        }
        if (lower.startsWith("delete from ")) {
            Pattern pattern = Pattern.compile(
                    "(?i)^\\s*delete\\s+from\\s+(?:public\\.)?" + Pattern.quote(targetTable)
                            + "\\s+(?:as\\s+)?([a-zA-Z_][a-zA-Z0-9_]*)\\s+where\\b"
            );
            if (pattern.matcher(lower).find()) {
                throw new IllegalArgumentException("Agent DELETE 대상 테이블 별칭은 허용하지 않습니다.");
            }
        }
    }

    private String writeTarget(String sql) {
        Matcher update = UPDATE_TARGET_PATTERN.matcher(sql);
        if (update.find()) {
            return update.group(1);
        }
        Matcher insert = INSERT_TARGET_PATTERN.matcher(sql);
        if (insert.find()) {
            return insert.group(1);
        }
        Matcher delete = DELETE_TARGET_PATTERN.matcher(sql);
        if (delete.find()) {
            return delete.group(1);
        }
        return "";
    }

    private void ensureWhere(String sql, String command) {
        if (!Pattern.compile("(?i)\\bwhere\\b").matcher(sql).find()) {
            throw new IllegalArgumentException(command + " SQL에는 WHERE 조건이 필수입니다.");
        }
    }

    private void ensureDeleteIsSingleTarget(String sql) {
        String targetTable = normalizedTableName(writeTarget(sql));
        ensureSimpleSingleTargetWhere(sql, targetTable, "물리 DELETE");
        if (!schemaService.isPrimaryKeyColumn(targetTable, "id")) {
            throw new IllegalArgumentException("물리 DELETE 대상의 id 컬럼이 PRIMARY KEY가 아니므로 차단했습니다: " + targetTable);
        }
    }

    private void ensureAllowedParams(String sql, Set<String> allowed) {
        Matcher matcher = PARAM_PATTERN.matcher(sql);
        while (matcher.find()) {
            String name = matcher.group(1);
            if (!allowed.contains(name)) {
                throw new IllegalArgumentException("허용되지 않은 SQL 파라미터입니다: :" + name);
            }
        }
    }

    private Set<String> allowedReadParams() {
        Set<String> allowed = baseParams();
        allowed.add("agentLimit");
        return allowed;
    }

    private Set<String> allowedWriteParams() {
        Set<String> allowed = baseParams();
        allowed.add("targetId");
        return allowed;
    }

    private Set<String> baseParams() {
        Set<String> allowed = new LinkedHashSet<>();
        allowed.add("projectId");
        allowed.add("versionId");
        allowed.add("sessionId");
        for (int i = 1; i <= 50; i++) {
            allowed.add("p" + i);
        }
        return allowed;
    }

    private String wrapLimit(String sql, int requestedMaxRows) {
        int hardMax = Math.max(1, properties.getAgentHardMaxReadRows());
        int requested = requestedMaxRows <= 0 ? properties.getAgentDefaultReadRows() : requestedMaxRows;
        int safeRequested = Math.max(1, Math.min(requested, hardMax));

        Matcher matcher = LIMIT_PATTERN.matcher(maskStringLiterals(sql));
        if (matcher.find()) {
            int existing;
            try {
                existing = Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException ex) {
                existing = safeRequested;
            }
            int enforced = Math.min(existing, safeRequested);
            if (existing != enforced) {
                return sql.substring(0, matcher.start()) + "LIMIT " + enforced + sql.substring(matcher.end());
            }
            return sql;
        }
        return "SELECT * FROM (" + sql + ") AS agent_safe_query LIMIT " + safeRequested;
    }

    private String normalizeAlias(String alias) {
        if (!StringUtils.hasText(alias)) {
            return "";
        }
        String normalized = stripIdentifierQuotes(alias).toLowerCase(Locale.ROOT);
        return SQL_KEYWORDS.contains(normalized) ? "" : normalized;
    }

    private TableToken parseTableToken(String raw) {
        String cleaned = raw == null ? "" : raw.trim().replace("\"", "").toLowerCase(Locale.ROOT);
        String schema = "";
        String table = cleaned;
        int dot = cleaned.lastIndexOf('.');
        if (dot >= 0) {
            schema = cleaned.substring(0, dot);
            table = cleaned.substring(dot + 1);
        }
        if (BLOCKED_META_TABLES.contains(table)) {
            throw new IllegalArgumentException("계정/권한/서버설정 관련 메타데이터는 조회할 수 없습니다: " + raw);
        }
        return new TableToken(schema, table);
    }

    private String stripIdentifierQuotes(String value) {
        return value == null ? "" : value.replace("\"", "");
    }

    public String writeTargetTable(String sql) {
        String normalized = normalize(sql);
        return normalizedTableName(writeTarget(normalized));
    }

    /** SQL 문자열 리터럴 내부 키워드가 검증 키워드로 오인되지 않도록 공백으로 마스킹합니다. */
    private String maskStringLiterals(String sql) {
        StringBuilder masked = new StringBuilder(sql.length());
        boolean inLiteral = false;
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (c == '\'') {
                masked.append(' ');
                if (inLiteral && i + 1 < sql.length() && sql.charAt(i + 1) == '\'') {
                    masked.append(' ');
                    i++;
                    continue;
                }
                inLiteral = !inLiteral;
                continue;
            }
            masked.append(inLiteral ? ' ' : c);
        }
        return masked.toString();
    }

    private String normalizedTableName(String raw) {
        return parseTableToken(raw).table();
    }

    public record ValidatedSql(String sql, List<String> warnings) {
    }

    private record TableAccess(List<RagTableRef> ragTableRefs, List<String> warnings) {
    }

    private record RagTableRef(String table, String qualifier, boolean scopedView) {
    }

    private record InsertParts(List<String> columns, List<String> values) {
    }

    private record TableToken(String schema, String table) {
        boolean isRagTable() {
            return (schema.isEmpty() || "public".equals(schema)) && table.startsWith("rag_");
        }

        boolean isAgentScopedRagView() {
            return "rag_agent_view".equals(schema) && table.startsWith("rag_");
        }

        boolean isInformationSchema() {
            return "information_schema".equals(schema);
        }

        boolean isInformationSchemaAllowed() {
            return isInformationSchema() && ALLOWED_INFORMATION_SCHEMA_TABLES.contains(table);
        }

        boolean isPgCatalogAllowed() {
            return "pg_catalog".equals(schema) && ALLOWED_PG_CATALOG_TABLES.contains(table);
        }

        String fullName() {
            return schema.isEmpty() ? table : schema + "." + table;
        }
    }
}
