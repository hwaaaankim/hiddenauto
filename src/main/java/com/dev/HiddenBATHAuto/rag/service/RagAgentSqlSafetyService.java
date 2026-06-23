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

@Service
public class RagAgentSqlSafetyService {

    private static final int DEFAULT_MAX_ROWS = 500;
    private static final int HARD_MAX_ROWS = 1000;
    private static final Pattern TABLE_PATTERN = Pattern.compile("(?i)\\b(?:from|join)\\s+([a-zA-Z_][a-zA-Z0-9_\\.\"]*)");
    private static final Pattern PARAM_PATTERN = Pattern.compile("(?<!:):([A-Za-z][A-Za-z0-9_]*)");
    private static final Pattern LIMIT_PATTERN = Pattern.compile("(?i)\\blimit\\s+(\\d+)");
    private static final Pattern DANGEROUS_PATTERN = Pattern.compile(
            "(?i)\\b(insert|update|delete|drop|truncate|alter|create|grant|revoke|copy|call|do|execute|merge|vacuum|analyze|refresh|reindex|listen|notify)\\b"
    );

    public ValidatedSql validateReadSql(String sql) {
        String normalized = normalize(sql);
        ensureSingleStatement(normalized);
        ensureNoComment(normalized);
        ensureReadOnly(normalized);
        ensureRagTablesOnly(normalized);
        ensureProjectVersionScoped(normalized);
        ensureAllowedParams(normalized, allowedReadParams());
        return new ValidatedSql(wrapLimit(normalized), List.of());
    }

    public ValidatedSql validateWriteSql(String sql) {
        String normalized = normalize(sql);
        ensureSingleStatement(normalized);
        ensureNoComment(normalized);
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (!(lower.startsWith("update ") || lower.startsWith("insert into "))) {
            throw new IllegalArgumentException("GPT 변경 SQL은 UPDATE 또는 INSERT INTO만 허용합니다.");
        }
        if (Pattern.compile("(?i)\\b(delete|drop|truncate|alter|create|grant|revoke|copy|call|do|execute|merge|vacuum|analyze|refresh|reindex)\\b").matcher(normalized).find()) {
            throw new IllegalArgumentException("위험한 SQL 키워드가 포함되어 변경 SQL을 차단했습니다.");
        }
        ensureRagWriteTarget(normalized);
        ensureProjectVersionScoped(normalized);
        if (lower.startsWith("update ") && !lower.contains(" where ")) {
            throw new IllegalArgumentException("UPDATE 변경 SQL에는 WHERE 조건이 필수입니다.");
        }
        ensureAllowedParams(normalized, allowedWriteParams());
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
        return normalized;
    }

    private void ensureSingleStatement(String sql) {
        if (sql.contains(";")) {
            throw new IllegalArgumentException("다중 SQL 문장은 허용하지 않습니다.");
        }
    }

    private void ensureNoComment(String sql) {
        if (sql.contains("--") || sql.contains("/*") || sql.contains("*/")) {
            throw new IllegalArgumentException("SQL 주석은 우회 가능성이 있어 허용하지 않습니다.");
        }
    }

    private void ensureReadOnly(String sql) {
        String lower = sql.toLowerCase(Locale.ROOT);
        if (!(lower.startsWith("select ") || lower.startsWith("with "))) {
            throw new IllegalArgumentException("GPT 직접 SQL은 SELECT/WITH 조회만 허용합니다.");
        }
        if (DANGEROUS_PATTERN.matcher(sql).find()) {
            throw new IllegalArgumentException("조회 SQL에 쓰기/DDL 키워드가 포함되어 차단했습니다.");
        }
    }

    private void ensureRagWriteTarget(String sql) {
        Matcher update = Pattern.compile("(?i)^\\s*update\\s+([a-zA-Z_][a-zA-Z0-9_\\.\"]*)").matcher(sql);
        Matcher insert = Pattern.compile("(?i)^\\s*insert\\s+into\\s+([a-zA-Z_][a-zA-Z0-9_\\.\"]*)").matcher(sql);
        String token = null;
        if (update.find()) token = update.group(1);
        if (token == null && insert.find()) token = insert.group(1);
        if (!StringUtils.hasText(token)) {
            throw new IllegalArgumentException("변경 SQL의 대상 테이블을 찾지 못했습니다.");
        }
        String table = token.replace("\"", "").trim();
        if (table.contains(".")) {
            String[] parts = table.split("\\.");
            if (parts.length != 2 || !("public".equalsIgnoreCase(parts[0]))) {
                throw new IllegalArgumentException("rag 전용 public schema 외 테이블 변경은 허용하지 않습니다: " + token);
            }
            table = parts[1];
        }
        if (!table.toLowerCase(Locale.ROOT).startsWith("rag_")) {
            throw new IllegalArgumentException("rag_ 테이블만 변경할 수 있습니다: " + token);
        }
        Matcher matcher = TABLE_PATTERN.matcher(sql);
        while (matcher.find()) {
            String joined = matcher.group(1).replace("\"", "").trim();
            if (joined.startsWith("(")) continue;
            String joinedTable = joined;
            if (joinedTable.contains(".")) {
                String[] parts = joinedTable.split("\\.");
                if (parts.length != 2 || !("public".equalsIgnoreCase(parts[0]))) {
                    throw new IllegalArgumentException("rag 전용 public schema 외 조인 테이블 접근은 허용하지 않습니다: " + joined);
                }
                joinedTable = parts[1];
            }
            if (!joinedTable.toLowerCase(Locale.ROOT).startsWith("rag_")) {
                throw new IllegalArgumentException("rag_ 테이블만 조회/변경할 수 있습니다: " + joined);
            }
        }
    }

    private void ensureRagTablesOnly(String sql) {
        Matcher matcher = TABLE_PATTERN.matcher(sql);
        List<String> found = new ArrayList<>();
        while (matcher.find()) {
            String token = matcher.group(1).replace("\"", "").trim();
            if (token.startsWith("(")) continue;
            String table = token;
            if (table.contains(".")) {
                String[] parts = table.split("\\.");
                if (parts.length != 2 || !("public".equalsIgnoreCase(parts[0]))) {
                    throw new IllegalArgumentException("rag 전용 public schema 외 테이블 접근은 허용하지 않습니다: " + token);
                }
                table = parts[1];
            }
            found.add(table);
            if (!table.toLowerCase(Locale.ROOT).startsWith("rag_")) {
                throw new IllegalArgumentException("rag_ 테이블만 조회/변경할 수 있습니다: " + token);
            }
        }
        if (found.isEmpty()) {
            throw new IllegalArgumentException("조회/변경 대상 rag_ 테이블을 찾지 못했습니다.");
        }
    }

    private void ensureProjectVersionScoped(String sql) {
        String lower = sql.toLowerCase(Locale.ROOT);
        if (!lower.contains("project_id") || !lower.contains(":projectid")) {
            throw new IllegalArgumentException("SQL에는 project_id = :projectId 범위 조건이 필요합니다.");
        }
        if (!lower.contains("version_id") || !lower.contains(":versionid")) {
            throw new IllegalArgumentException("SQL에는 version_id = :versionId 범위 조건이 필요합니다.");
        }
    }

    private void ensureAllowedParams(String sql, Set<String> allowed) {
        Matcher matcher = PARAM_PATTERN.matcher(sql);
        while (matcher.find()) {
            String name = matcher.group(1);
            if (!allowed.contains(name)) {
                throw new IllegalArgumentException("허용되지 않은 SQL 파라미터입니다. :" + name + " 대신 :p1~:p50 또는 지정 파라미터를 사용해야 합니다.");
            }
        }
    }

    private Set<String> allowedReadParams() {
        Set<String> allowed = new LinkedHashSet<>();
        allowed.add("projectId");
        allowed.add("versionId");
        allowed.add("sessionId");
        allowed.add("agentLimit");
        for (int i = 1; i <= 50; i++) allowed.add("p" + i);
        return allowed;
    }

    private Set<String> allowedWriteParams() {
        Set<String> allowed = allowedReadParams();
        allowed.add("targetId");
        return allowed;
    }

    private String wrapLimit(String sql) {
        int max = DEFAULT_MAX_ROWS;
        Matcher matcher = LIMIT_PATTERN.matcher(sql);
        if (matcher.find()) {
            try {
                max = Math.min(Integer.parseInt(matcher.group(1)), HARD_MAX_ROWS);
            } catch (NumberFormatException ignored) {
                max = DEFAULT_MAX_ROWS;
            }
        }
        return "SELECT * FROM (\n" + sql + "\n) agent_limited_result LIMIT " + max;
    }

    public record ValidatedSql(String sql, List<String> warnings) {
    }
}
