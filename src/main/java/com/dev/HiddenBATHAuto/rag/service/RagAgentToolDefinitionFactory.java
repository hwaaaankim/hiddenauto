package com.dev.HiddenBATHAuto.rag.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** OpenAI Responses API에 전달할 strict function tool 정의입니다. */
public final class RagAgentToolDefinitionFactory {

    private RagAgentToolDefinitionFactory() {
    }

    public static List<Map<String, Object>> tools() {
        List<Map<String, Object>> tools = new ArrayList<>();
        tools.add(function(
                "get_database_overview",
                "현재 연결된 RAG PostgreSQL의 DB 종류, 버전, 스키마, 전체 rag_* 테이블 목록, 테이블 설명, 예상 row 수를 확인합니다. 사용자의 표현이 어느 테이블을 뜻하는지 모를 때 가장 먼저 사용합니다.",
                object(Map.of(), List.of())
        ));
        tools.add(function(
                "search_database_catalog",
                "사용자 표현과 관련된 테이블, 컬럼, DB 주석, GPT 스키마 설명 사전을 이름과 의미 기준으로 검색합니다. 예: 제품, 색상, 가격, 발주, 옵션, 사이즈.",
                object(linkedMap(
                        "query", string("찾을 업무 용어 또는 키워드", 500),
                        "objectTypes", array(enumString("TABLE", "COLUMN", "SCHEMA_NOTE"), 1, 3),
                        "limit", integer(1, 200, "최대 결과 수")
                ), List.of("query", "objectTypes", "limit"))
        ));
        tools.add(function(
                "describe_table",
                "선택한 rag_* 테이블의 모든 컬럼 타입, null/default, PK/FK, 주석, 인덱스, 제약조건, 프로젝트/버전 범위 여부, 실제 샘플 row를 조회합니다.",
                object(linkedMap(
                        "schemaName", string("스키마명. 현재는 public 사용", 100),
                        "tableName", string("설명할 rag_* 테이블명", 200),
                        "sampleLimit", integer(0, 20, "반환할 실제 샘플 row 수")
                ), List.of("schemaName", "tableName", "sampleLimit"))
        ));
        tools.add(function(
                "list_table_relationships",
                "외래키 기준으로 테이블이 참조하는 대상과 이 테이블을 참조하는 다른 테이블을 모두 조회합니다.",
                object(linkedMap(
                        "schemaName", string("스키마명. 현재는 public 사용", 100),
                        "tableName", nullableString("특정 테이블명. null이면 전체 rag_* 관계", 200)
                ), List.of("schemaName", "tableName"))
        ));
        tools.add(function(
                "get_table_statistics",
                "테이블의 예상 또는 정확한 row 수, 프로젝트/버전 범위 row 수, active/status 분포를 확인합니다. 전체 데이터 설명이나 변경 영향 판단에 사용합니다.",
                object(linkedMap(
                        "schemaName", string("스키마명. 현재는 public 사용", 100),
                        "tableName", string("통계를 확인할 rag_* 테이블명", 200),
                        "exactCount", bool("true면 정확한 count를 계산합니다. 큰 테이블에서는 필요한 경우에만 사용")
                ), List.of("schemaName", "tableName", "exactCount"))
        ));
        tools.add(function(
                "query_database",
                "GPT가 직접 작성한 PostgreSQL SELECT/WITH SQL을 실행합니다. 업무 row는 project/version 범위가 강제된 rag_agent_view.rag_* 보안 뷰만 읽을 수 있고, 허용된 메타데이터와 row 제한을 Java가 검증합니다.",
                object(linkedMap(
                        "purpose", string("이 SQL이 필요한 이유", 2000),
                        "sql", string("SELECT 또는 WITH 단일 SQL. 업무 row는 rag_agent_view.rag_* 사용", 50000),
                        "paramsJson", string("SQL named parameter 값 JSON object. 사용자 값은 p1~p50 사용", 100000),
                        "maxRows", integer(1, 1000, "반환할 최대 row 수")
                ), List.of("purpose", "sql", "paramsJson", "maxRows"))
        ));
        tools.add(function(
                "create_change_set",
                "DB 조회로 대상과 영향이 확인된 뒤 단건 INSERT/단건 UPDATE/단건 SOFT DELETE/단건 DELETE 변경계획을 생성합니다. Java가 SQL과 targetId 소속을 검증하고 forceSave 및 확인 필요 여부에 따라 트랜잭션 적용 또는 보류합니다.",
                changeSetSchema()
        ));
        tools.add(function(
                "get_change_set",
                "생성된 변경계획의 항목, 검증 상태, 적용 상태, 오류를 다시 확인합니다.",
                object(linkedMap(
                        "changeSetId", string("UUID 형식 변경계획 ID", 36)
                ), List.of("changeSetId"))
        ));
        tools.add(function(
                "submit_final_answer",
                "도구 탐색과 필요한 변경계획 생성이 끝난 뒤 사용자에게 보낼 최종 응답을 제출합니다. 모든 요청은 반드시 이 도구로 종료합니다.",
                finalAnswerSchema()
        ));
        return List.copyOf(tools);
    }

    private static Map<String, Object> changeSetSchema() {
        Map<String, Object> item = object(linkedMap(
                "operation", enumString("READ_ONLY_NOTE", "INSERT_KNOWLEDGE_NODE", "INSERT_SQL", "UPDATE_SQL", "SOFT_DELETE_SQL", "DELETE_SQL"),
                "targetTable", string("대상 public.rag_* 테이블명. INSERT_KNOWLEDGE_NODE는 rag_knowledge_node", 200),
                "targetId", nullableString("대상 UUID. 단건 DELETE는 반드시 지정", 36),
                "writeSql", string("실행할 INSERT/UPDATE/DELETE SQL. INSERT_KNOWLEDGE_NODE/READ_ONLY_NOTE는 빈 문자열 가능", 50000),
                "paramsJson", string("named parameter JSON object", 100000),
                "beforeJson", string("기존 상태 또는 조회 근거 JSON 문자열", 100000),
                "afterJson", string("변경 후 상태 또는 저장할 지식 JSON 문자열", 100000),
                "reason", string("변경 이유", 10000),
                "impact", string("연관 데이터와 예상 영향", 10000)
        ), List.of("operation", "targetTable", "targetId", "writeSql", "paramsJson", "beforeJson", "afterJson", "reason", "impact"));

        return object(linkedMap(
                "title", string("변경계획 제목", 500),
                "summary", string("변경 내용 요약", 20000),
                "confidence", number(0, 1, "변경 판단 신뢰도"),
                "requiresConfirmation", bool("가격/삭제/대량교체/대상 불명확 등 사용자 확인 필요 여부"),
                "conflictReportJson", string("충돌 및 중복 검사 결과 JSON 문자열", 100000),
                "items", array(item, 0, 100)
        ), List.of("title", "summary", "confidence", "requiresConfirmation", "conflictReportJson", "items"));
    }

    private static Map<String, Object> finalAnswerSchema() {
        Map<String, Object> evidence = object(linkedMap(
                "sourceType", enumString("DATABASE", "SCHEMA", "FILE", "CONVERSATION", "CHANGE_SET", "INFERENCE"),
                "objectName", string("근거 테이블, 컬럼, 파일 또는 변경계획 이름", 500),
                "detail", string("사용자에게 공개 가능한 근거 요약", 5000)
        ), List.of("sourceType", "objectName", "detail"));

        return object(linkedMap(
                "status", enumString("READY_TO_ANSWER", "NEED_CLARIFICATION", "BLOCKED", "ERROR"),
                "answer", string("한국어 존댓말 최종 답변", 50000),
                "confidence", number(0, 1, "최종 답변 신뢰도"),
                "requiresClarification", bool("사용자 추가 확인 필요 여부"),
                "changeSetId", nullableString("변경계획이 있으면 UUID, 없으면 null", 36),
                "evidence", array(evidence, 0, 50),
                "riskNotes", array(string("사용자에게 알려야 할 위험 또는 주의사항", 5000), 0, 20)
        ), List.of("status", "answer", "confidence", "requiresClarification", "changeSetId", "evidence", "riskNotes"));
    }

    private static Map<String, Object> function(String name, String description, Map<String, Object> parameters) {
        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("type", "function");
        tool.put("name", name);
        tool.put("description", description);
        tool.put("parameters", parameters);
        tool.put("strict", true);
        return tool;
    }

    private static Map<String, Object> object(Map<String, Object> properties, List<String> required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", required);
        schema.put("additionalProperties", false);
        return schema;
    }

    private static Map<String, Object> string(String description) {
        return linkedMap("type", "string", "description", description);
    }

    private static Map<String, Object> string(String description, int maxLength) {
        return linkedMap("type", "string", "maxLength", maxLength, "description", description);
    }

    private static Map<String, Object> nullableString(String description) {
        return linkedMap("type", List.of("string", "null"), "description", description);
    }

    private static Map<String, Object> nullableString(String description, int maxLength) {
        return linkedMap("type", List.of("string", "null"), "maxLength", maxLength, "description", description);
    }

    private static Map<String, Object> bool(String description) {
        return linkedMap("type", "boolean", "description", description);
    }

    private static Map<String, Object> integer(int min, int max, String description) {
        return linkedMap("type", "integer", "minimum", min, "maximum", max, "description", description);
    }

    private static Map<String, Object> number(double min, double max, String description) {
        return linkedMap("type", "number", "minimum", min, "maximum", max, "description", description);
    }

    private static Map<String, Object> enumString(String... values) {
        return linkedMap("type", "string", "enum", List.of(values));
    }

    private static Map<String, Object> array(Map<String, Object> itemSchema) {
        return linkedMap("type", "array", "items", itemSchema);
    }

    private static Map<String, Object> array(Map<String, Object> itemSchema, int minItems, int maxItems) {
        return linkedMap("type", "array", "items", itemSchema, "minItems", minItems, "maxItems", maxItems);
    }

    private static Map<String, Object> linkedMap(Object... values) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < values.length; i += 2) {
            map.put(String.valueOf(values[i]), values[i + 1]);
        }
        return map;
    }
}
