package com.dev.HiddenBATHAuto.rag.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * GPT SQL Agent가 사용하는 Structured Outputs 스키마입니다.
 * 업무별 enum 도구가 아니라, GPT가 직접 읽기 SQL과 변경계획(ChangeSet)을 생성하도록 고정 출력형만 제한합니다.
 */
public final class RagAgentSchemaFactory {

    private RagAgentSchemaFactory() {
    }

    public static Map<String, Object> agentStepSchema() {
        Map<String, Object> string = Map.of("type", "string");
        Map<String, Object> number = Map.of("type", "number");
        Map<String, Object> bool = Map.of("type", "boolean");

        Map<String, Object> readSqlRequest = object(Map.of(
                "requestId", string,
                "reason", string,
                "sql", string,
                "paramsJson", string,
                "expectedResult", string
        ), List.of("requestId", "reason", "sql", "paramsJson", "expectedResult"));

        Map<String, Object> changeItem = object(Map.of(
                "operation", string,
                "targetTable", string,
                "targetId", string,
                "writeSql", string,
                "paramsJson", string,
                "beforeJson", string,
                "afterJson", string,
                "reason", string,
                "impact", string
        ), List.of("operation", "targetTable", "targetId", "writeSql", "paramsJson", "beforeJson", "afterJson", "reason", "impact"));

        Map<String, Object> changeSet = object(Map.of(
                "title", string,
                "summary", string,
                "confidence", number,
                "requiresConfirmation", bool,
                "conflictReportJson", string,
                "items", array(changeItem)
        ), List.of("title", "summary", "confidence", "requiresConfirmation", "conflictReportJson", "items"));

        return object(Map.of(
                "status", string,
                "confidence", number,
                "thoughtSummary", string,
                "answer", string,
                "requiresUserConfirmation", bool,
                "riskNotes", array(string),
                "readSqlRequests", array(readSqlRequest),
                "changeSet", changeSet
        ), List.of("status", "confidence", "thoughtSummary", "answer", "requiresUserConfirmation", "riskNotes", "readSqlRequests", "changeSet"));
    }

    private static Map<String, Object> object(Map<String, Object> properties, List<String> required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        schema.put("properties", properties);
        schema.put("required", required);
        return schema;
    }

    private static Map<String, Object> array(Map<String, Object> itemSchema) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "array");
        schema.put("items", itemSchema);
        return schema;
    }
}
