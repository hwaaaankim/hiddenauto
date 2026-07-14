package com.dev.HiddenBATHAuto.rag.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** OpenAI Responses API strict function tool 정의입니다. */
public final class RagAgentToolDefinitionFactory {

    private RagAgentToolDefinitionFactory() {}

    private static final Set<String> CHAT_BLOCKED_TOOLS = Set.of(
            "describe_table",
            "list_table_relationships",
            "get_table_statistics",
            "query_database",
            "preview_change_impact",
            "create_change_set",
            "get_change_set"
    );

    private static final List<ToolMeta> TOOL_META = List.of(
            meta("submit_request_plan", "CONTROL", false, "요청 의도·필요 근거·변경 위험을 GPT가 먼저 계획합니다."),
            meta("get_agent_capabilities", "CONTROL", false, "현재 Agent 도구와 권한 계약을 확인합니다."),
            meta("get_database_overview", "SCHEMA", false, "RAG DB 테이블과 범위 정책을 확인합니다."),
            meta("get_knowledge_inventory", "KNOWLEDGE", false, "저장된 지식 영역과 실제 row 수를 조사합니다."),
            meta("search_database_catalog", "SCHEMA", false, "업무 용어로 테이블·컬럼·스키마 노트를 찾습니다."),
            meta("search_semantic_memory", "KNOWLEDGE", false, "임베딩/FTS/별칭 기반 후보를 찾습니다."),
            meta("search_knowledge_sources", "KNOWLEDGE", false, "원문과 source row를 어휘 기반으로 폭넓게 찾습니다."),
            meta("get_document_context", "KNOWLEDGE", false, "문서 원문과 인접 청크를 조회합니다."),
            meta("resolve_entity_reference", "ENTITY", false, "오타·별칭·유사 표현을 엔티티 후보로 해석합니다."),
            meta("get_entity_context_bundle", "ENTITY", false, "하나의 엔티티와 연결된 규칙·가격·원문을 묶어 조회합니다."),
            meta("get_effective_rules", "RULE", false, "현재 날짜/지정일에 적용되는 규칙을 통합 조회합니다."),
            meta("get_order_flow", "ORDER", false, "주문 질문 순서·조건부 분기·검증 흐름을 조회합니다."),
            meta("validate_order_state", "ORDER", false, "현재 주문 답변의 누락·충돌·다음 질문을 검증합니다."),
            meta("compare_entity_candidates", "ENTITY", false, "복수 후보의 실제 근거 차이를 비교합니다."),
            meta("describe_table", "SCHEMA", false, "테이블 컬럼·PK·샘플을 확인합니다."),
            meta("list_table_relationships", "SCHEMA", false, "FK 관계를 확인합니다."),
            meta("get_table_statistics", "SCHEMA", false, "row 수와 상태 분포를 확인합니다."),
            meta("query_database", "DATABASE", false, "현재 프로젝트/버전으로 제한된 SELECT/WITH를 실행합니다."),
            meta("find_canonical_price_candidates", "PRICE", false, "가격 엔티티와 규칙 후보를 찾습니다."),
            meta("calculate_order_price", "PRICE", false, "확정 입력을 결정론적 Java 가격계산기로 계산합니다."),
            meta("simulate_price_scenarios", "PRICE", false, "복수 주문 조건을 같은 계산기로 비교합니다."),
            meta("preview_change_impact", "MUTATION", false, "변경 전에 대상·FK·참조 영향을 확인합니다."),
            meta("get_conversation_memory", "MEMORY", false, "현재 세션 working memory를 읽습니다."),
            meta("update_conversation_memory", "MEMORY", true, "GPT가 확정한 대화 문맥만 세션 메모리에 갱신합니다."),
            meta("get_conversation_history", "MEMORY", false, "최근 세션 대화를 조회합니다."),
            meta("create_change_set", "MUTATION", true, "검증된 저장·수정·삭제 계획을 보류 또는 트랜잭션 적용합니다."),
            meta("get_change_set", "MUTATION", false, "변경계획과 적용 상태를 조회합니다."),
            meta("get_agent_run_audit", "CONTROL", false, "Agent 실행의 도구·SQL·변경·답변 출처를 범위 제한해 확인합니다."),
            meta("submit_final_answer", "CONTROL", false, "GPT가 작성한 최종 사용자 답변을 제출합니다.")
    );

    public static List<Map<String, Object>> tools() {
        List<Map<String, Object>> tools = new ArrayList<>();
        tools.add(function("submit_request_plan",
                "모든 요청의 첫 도구입니다. GPT가 의도, 필요한 DB/RAG 조사, 주문 검증, 가격계산, 변경 위험을 계획합니다.",
                requestPlanSchema()));
        tools.add(function("get_agent_capabilities",
                "현재 배포된 DB Tool Agent의 도구 범위, GPT-only 답변 계약, 읽기/쓰기 책임을 확인합니다.",
                object(linkedMap(
                        "categories", array(enumString("CONTROL","SCHEMA","DATABASE","KNOWLEDGE","ENTITY","RULE","ORDER","PRICE","MEMORY","MUTATION"), 0, 10)
                ), List.of("categories"))));
        tools.add(function("get_database_overview",
                "현재 RAG DB의 테이블 목록, 범위 보안 정책, 사용 가능한 스키마 정보를 확인합니다.",
                object(Map.of(), List.of())));
        tools.add(function("get_knowledge_inventory",
                "제품·가격·주문·대화규칙·문서·구조화표·정본 등 실제 저장 데이터의 row 수와 샘플을 조사합니다.",
                object(linkedMap(
                        "domains", array(enumString("PRODUCT","PRICE","ORDER","DIALOG","FILE","STRUCTURED","KNOWLEDGE","OTHER"), 0, 20),
                        "exactCounts", bool("정확한 row 수 계산 여부"),
                        "includeSamples", bool("영역별 샘플 포함 여부"),
                        "sampleLimit", integer(0, 20, "영역별 최대 샘플 수")
                ), List.of("domains","exactCounts","includeSamples","sampleLimit"))));
        tools.add(function("search_database_catalog",
                "사용자 표현이 어느 테이블·컬럼·스키마 설명과 연관되는지 찾습니다.",
                object(linkedMap(
                        "query", string("검색할 업무 용어", 1, 4000),
                        "objectTypes", array(enumString("TABLE","COLUMN","RELATIONSHIP","NOTE","VIEW"), 0, 10),
                        "limit", integer(1, 200, "최대 결과 수")
                ), List.of("query","objectTypes","limit"))));
        tools.add(function("search_semantic_memory",
                "오타·별칭·유사 표현을 임베딩/FTS/pg_trgm으로 검색합니다. 후보 인덱스이므로 변경 전 원본을 재확인해야 합니다.",
                object(linkedMap(
                        "query", string("의미 검색문", 1, 8000),
                        "domains", array(enumString("PRODUCT","PRICE","ORDER","DIALOG","FILE","STRUCTURED","KNOWLEDGE","OTHER"), 0, 20),
                        "sourceKinds", array(string("source kind", 1, 100), 0, 30),
                        "limit", integer(1, 100, "최대 결과 수"),
                        "minimumScore", number(0, 1, "최소 점수"),
                        "includeInactive", bool("비활성 후보 포함 여부")
                ), List.of("query","domains","sourceKinds","limit","minimumScore","includeInactive"))));
        tools.add(function("search_knowledge_sources",
                "semantic index가 없거나 원문 확인이 필요할 때 제목·본문·별칭·키워드를 직접 검색합니다.",
                object(linkedMap(
                        "query", string("원문 검색어", 1, 8000),
                        "domains", array(string("domain", 1, 100), 0, 20),
                        "sourceKinds", array(string("source kind", 1, 100), 0, 30),
                        "limit", integer(1, 100, "최대 결과 수"),
                        "includeInactive", bool("비활성 원문 포함 여부")
                ), List.of("query","domains","sourceKinds","limit","includeInactive"))));
        tools.add(function("get_document_context",
                "업로드 문서의 메타, 원문 미리보기, 검색어가 있는 청크의 앞뒤 문맥을 조회합니다.",
                object(linkedMap(
                        "documentId", string("rag_document UUID", 36, 36),
                        "searchText", nullableString("문서 안에서 찾을 표현. 전체 시작부면 null", 4000),
                        "beforeChunks", integer(0, 20, "앞쪽 청크 수"),
                        "afterChunks", integer(0, 20, "뒤쪽 청크 수"),
                        "maxCharacters", integer(1000, 100000, "반환 최대 글자 수")
                ), List.of("documentId","searchText","beforeChunks","afterChunks","maxCharacters"))));
        tools.add(function("resolve_entity_reference",
                "사용자 표현을 제품·규칙·시리즈·옵션 등의 실제 엔티티 후보로 해석하고 모호성을 판정합니다.",
                object(linkedMap(
                        "expression", string("사용자가 말한 표현", 1, 4000),
                        "entityType", nullableString("알고 있는 엔티티 종류. 모르면 null", 200),
                        "limit", integer(1, 30, "후보 수"),
                        "minimumConfidence", number(0, 1, "최소 신뢰도")
                ), List.of("expression","entityType","limit","minimumConfidence"))));
        tools.add(function("get_entity_context_bundle",
                "확정 또는 후보 엔티티에 연결된 별칭·정본·노드·대화규칙·가격규칙·override·fact·semantic 원문을 통합 조회합니다.",
                object(linkedMap(
                        "entityType", nullableString("엔티티 종류", 200),
                        "entityKey", string("엔티티 키", 1, 500),
                        "includeInactive", bool("비활성 자료 포함 여부"),
                        "limitPerSection", integer(1, 100, "각 영역 최대 결과 수")
                ), List.of("entityType","entityKey","includeInactive","limitPerSection"))));
        tools.add(function("get_effective_rules",
                "지정 엔티티와 날짜에 실제 적용되는 dialog/pricing/override/canonical fact를 한 번에 확인합니다.",
                object(linkedMap(
                        "entityType", nullableString("엔티티 종류", 200),
                        "entityKey", nullableString("엔티티 키. 전역 규칙이면 null", 500),
                        "effectiveDate", nullableString("YYYY-MM-DD. 오늘 기준이면 null", 10),
                        "ruleTypes", array(string("필터할 rule/fact type", 1, 120), 0, 30),
                        "includeInactive", bool("비활성 규칙 포함 여부")
                ), List.of("entityType","entityKey","effectiveDate","ruleTypes","includeInactive"))));
        tools.add(function("get_order_flow",
                "제품 또는 목적별 주문 질문 순서, 조건부 질문, 입력 검증, 가격 연결 흐름을 조회합니다.",
                object(linkedMap(
                        "entityType", nullableString("엔티티 종류", 200),
                        "entityKey", nullableString("제품/시리즈 키", 500),
                        "purpose", nullableString("주문 목적 또는 flow 설명", 500),
                        "includeInactive", bool("비활성 흐름 포함 여부")
                ), List.of("entityType","entityKey","purpose","includeInactive"))));
        tools.add(function("validate_order_state",
                "현재까지 받은 주문 JSON을 저장된 흐름과 유효 규칙에 대조해 누락 필드·사이즈/옵션 충돌·다음 질문을 계산합니다.",
                object(linkedMap(
                        "entityType", nullableString("엔티티 종류", 200),
                        "entityKey", nullableString("제품/시리즈 키", 500),
                        "orderStateJson", string("현재 주문 답변 JSON object 문자열", 2, 150000),
                        "effectiveDate", nullableString("YYYY-MM-DD. 오늘 기준이면 null", 10)
                ), List.of("entityType","entityKey","orderStateJson","effectiveDate"))));
        tools.add(function("compare_entity_candidates",
                "수정·삭제·상담 대상 후보가 여러 개일 때 각 후보의 실제 규칙·가격·원문 차이를 비교합니다.",
                object(linkedMap(
                        "candidateRefsJson", string("[{\"entityType\":\"PRODUCT\",\"entityKey\":\"...\"}] JSON 배열", 2, 50000),
                        "limitPerSection", integer(1, 30, "후보별 영역 최대 결과 수")
                ), List.of("candidateRefsJson","limitPerSection"))));
        tools.add(function("describe_table",
                "테이블 컬럼, PK, 인덱스, 범위 정책, 제한된 샘플을 조회합니다.",
                object(linkedMap(
                        "schemaName", string("스키마명", 1, 100),
                        "tableName", string("rag_* 테이블명", 1, 200),
                        "sampleLimit", integer(0, 20, "샘플 row 수")
                ), List.of("schemaName","tableName","sampleLimit"))));
        tools.add(function("list_table_relationships",
                "테이블의 외래키 방향과 연관 테이블을 조회합니다.",
                object(linkedMap(
                        "schemaName", string("스키마명", 1, 100),
                        "tableName", nullableString("특정 테이블명. 전체면 null", 200)
                ), List.of("schemaName","tableName"))));
        tools.add(function("get_table_statistics",
                "테이블 전체/현재 범위 row 수와 active/status 분포를 조회합니다.",
                object(linkedMap(
                        "schemaName", string("스키마명", 1, 100),
                        "tableName", string("rag_* 테이블명", 1, 200),
                        "exactCount", bool("정확 count 여부")
                ), List.of("schemaName","tableName","exactCount"))));
        tools.add(function("query_database",
                "GPT가 작성한 PostgreSQL SELECT/WITH 단일 SQL을 실행합니다. 업무 row는 rag_agent_view.rag_* 뷰만 읽고 Java가 범위와 SQL을 검증합니다.",
                object(linkedMap(
                        "purpose", string("조회 이유", 1, 4000),
                        "sql", string("SELECT 또는 WITH 단일 SQL", 1, 50000),
                        "paramsJson", string("p1~p50 named parameter JSON object", 2, 100000),
                        "maxRows", integer(1, 1000, "최대 row 수")
                ), List.of("purpose","sql","paramsJson","maxRows"))));
        tools.add(function("find_canonical_price_candidates",
                "제품·별칭·규격과 관련된 정본/구조화 가격 후보를 찾습니다.",
                object(linkedMap(
                        "query", string("제품/옵션/규격 검색문", 1, 8000),
                        "entityType", nullableString("엔티티 종류", 200),
                        "limit", integer(1, 50, "후보 수")
                ), List.of("query","entityType","limit"))));
        tools.add(function("calculate_order_price",
                "단일 주문의 최종 가격을 결정론적 Java 계산기로 산출합니다. GPT는 숫자를 추정하지 않습니다.",
                object(linkedMap(
                        "answersJson", string("확정된 주문 답변 JSON object", 2, 150000)
                ), List.of("answersJson"))));
        tools.add(function("simulate_price_scenarios",
                "여러 규격·옵션·수량 시나리오를 같은 결정론적 계산기로 비교합니다.",
                object(linkedMap(
                        "scenariosJson", string("[{\"label\":\"...\",\"answers\":{...}}] JSON 배열", 2, 300000),
                        "stopOnError", bool("한 시나리오 실패 시 중단 여부")
                ), List.of("scenariosJson","stopOnError"))));
        tools.add(function("preview_change_impact",
                "저장·수정·삭제 전 대상 row와 inbound FK 참조 수를 조사하고 soft delete/확인 필요성을 판단합니다.",
                object(linkedMap(
                        "targetTable", string("public rag_* 테이블명", 1, 200),
                        "targetId", string("대상 UUID", 36, 36),
                        "operation", enumString("UPDATE","SOFT_DELETE","DELETE","REPLACE"),
                        "sampleLimit", integer(0, 20, "참조 row 샘플 수")
                ), List.of("targetTable","targetId","operation","sampleLimit"))));
        tools.add(function("get_conversation_memory",
                "현재 세션에서 확정된 제품·규격·옵션·의도 working memory를 읽습니다.",
                object(Map.of(), List.of())));
        tools.add(function("update_conversation_memory",
                "사용자가 확정한 대화 문맥만 MERGE/REPLACE하고, 초기화 요청은 CLEAR합니다. 영구 학습 저장이 아닙니다.",
                object(linkedMap(
                        "mode", enumString("MERGE","REPLACE","CLEAR"),
                        "memoryJson", string("세션 working memory JSON object. CLEAR면 {}", 2, 100000),
                        "confidence", number(0, 1, "문맥 확정 신뢰도"),
                        "reason", string("갱신 근거", 1, 5000)
                ), List.of("mode","memoryJson","confidence","reason"))));
        tools.add(function("get_conversation_history",
                "현재 세션의 최근 대화를 시간순으로 조회합니다.",
                object(linkedMap(
                        "limit", integer(1, 100, "메시지 수"),
                        "includeSystem", bool("SYSTEM 이벤트 포함 여부")
                ), List.of("limit","includeSystem"))));
        tools.add(function("create_change_set",
                "대상·유사 후보·영향을 조사한 뒤 단건 또는 제한된 변경계획을 생성합니다. Java가 SQL·범위·트랜잭션을 검증합니다.",
                changeSetSchema()));
        tools.add(function("get_change_set",
                "생성된 변경계획의 항목, 검증, 적용, 오류 상태를 확인합니다.",
                object(linkedMap("changeSetId", string("변경계획 UUID", 36, 36)), List.of("changeSetId"))));
        tools.add(function("get_agent_run_audit",
                "현재 또는 지정 Agent 실행의 도구 호출·SQL·ChangeSet·GPT 답변 출처를 현재 프로젝트/버전/세션 범위에서 확인합니다.",
                object(linkedMap(
                        "runId", nullableString("조회할 Agent run UUID. 현재 실행이면 null", 36),
                        "includeToolArguments", bool("도구 인자 포함 여부"),
                        "includeToolResults", bool("도구 결과 포함 여부"),
                        "limit", integer(1, 100, "각 감사 목록 최대 건수")
                ), List.of("runId","includeToolArguments","includeToolResults","limit"))));
        tools.add(function("submit_final_answer",
                "모든 조사와 필요한 변경계획이 끝난 뒤 GPT가 작성한 사용자용 한국어 답변을 제출합니다. 빈 answer는 거부됩니다.",
                finalAnswerSchema()));
        return List.copyOf(tools);
    }

    public static List<Map<String, Object>> toolsForScope(String sourceScope) {
        if (!"CHAT".equalsIgnoreCase(sourceScope)) return tools();
        return tools().stream()
                .filter(tool -> isAllowedForScope(String.valueOf(tool.get("name")), sourceScope))
                .toList();
    }

    public static boolean isAllowedForScope(String toolName, String sourceScope) {
        return !"CHAT".equalsIgnoreCase(sourceScope) || !CHAT_BLOCKED_TOOLS.contains(toolName);
    }

    public static List<Map<String, Object>> capabilitySummary(List<String> categories, String sourceScope) {
        Set<String> filter = categories == null ? Set.of() : categories.stream()
                .map(String::toUpperCase)
                .collect(java.util.stream.Collectors.toSet());
        return TOOL_META.stream()
                .filter(m -> filter.isEmpty() || filter.contains(m.category()))
                .filter(m -> isAllowedForScope(m.name(), sourceScope))
                .map(m -> Map.<String,Object>of(
                        "name", m.name(),
                        "category", m.category(),
                        "writeCapable", m.writeCapable(),
                        "description", m.description()))
                .toList();
    }

    public static Map<String, Object> requestPlanSchema() {
        Map<String, Object> entityHint = object(linkedMap(
                "entityType", nullableString("후보 종류", 200),
                "entityKey", nullableString("확인된 키 또는 사용자 표현", 500),
                "reason", string("조사 이유", 1, 3000)
        ), List.of("entityType","entityKey","reason"));
        return object(linkedMap(
                "intentType", enumString("GENERAL_CONVERSATION","KNOWLEDGE_QUERY","KNOWLEDGE_INVENTORY","ORDER_CONSULTATION","PRICE_CALCULATION","CREATE","UPDATE","DELETE","FILE_LEARNING","SYSTEM_EVENT","MIXED"),
                "userGoal", string("사용자가 원하는 결과", 1, 8000),
                "requiresDatabase", bool("DB 근거 필요 여부"),
                "requiresSemanticSearch", bool("유사 표현/별칭 검색 필요 여부"),
                "requiresEntityResolution", bool("엔티티 후보 해석 필요 여부"),
                "requiresOrderValidation", bool("주문 누락/충돌 검증 필요 여부"),
                "requiresMutation", bool("ChangeSet 필요 여부"),
                "requiresImpactPreview", bool("변경 영향 미리보기 필요 여부"),
                "requiresDeterministicPricing", bool("Java 가격계산 필요 여부"),
                "requiresConversationMemory", bool("세션 메모리 조회/갱신 필요 여부"),
                "ambiguityDetected", bool("대상 또는 조건 모호성"),
                "clarificationQuestion", nullableString("필요한 최소 확인 질문", 8000),
                "targetDomains", array(enumString("PRODUCT","PRICE","ORDER","DIALOG","FILE","STRUCTURED","KNOWLEDGE","MEMORY","SYSTEM"), 0, 20),
                "entityHints", array(entityHint, 0, 30),
                "plannedSteps", array(string("실행 단계", 1, 3000), 1, 40),
                "riskLevel", enumString("LOW","MEDIUM","HIGH","CRITICAL")
        ), List.of("intentType","userGoal","requiresDatabase","requiresSemanticSearch","requiresEntityResolution","requiresOrderValidation","requiresMutation","requiresImpactPreview","requiresDeterministicPricing","requiresConversationMemory","ambiguityDetected","clarificationQuestion","targetDomains","entityHints","plannedSteps","riskLevel"));
    }

    private static Map<String, Object> changeSetSchema() {
        Map<String, Object> item = object(linkedMap(
                "operation", enumString("READ_ONLY_NOTE","INSERT_KNOWLEDGE_NODE","INSERT_SQL","UPDATE_SQL","SOFT_DELETE_SQL","DELETE_SQL"),
                "targetTable", string("대상 public.rag_* 테이블", 1, 200),
                "targetId", nullableString("대상 UUID", 36),
                "writeSql", string("실행 SQL. READ_ONLY_NOTE/INSERT_KNOWLEDGE_NODE는 빈 문자열 가능", 0, 50000),
                "paramsJson", string("named parameter JSON", 2, 100000),
                "beforeJson", string("기존 상태/근거 JSON", 2, 100000),
                "afterJson", string("변경 후/저장 지식 JSON", 2, 100000),
                "reason", string("변경 이유", 1, 10000),
                "impact", string("연관 영향", 1, 10000)
        ), List.of("operation","targetTable","targetId","writeSql","paramsJson","beforeJson","afterJson","reason","impact"));
        return object(linkedMap(
                "title", string("변경계획 제목", 1, 500),
                "summary", string("변경 요약", 1, 20000),
                "confidence", number(0, 1, "판단 신뢰도"),
                "requiresConfirmation", bool("사용자 확인 필요 여부"),
                "conflictReportJson", string("중복/충돌/영향 검사 JSON", 2, 100000),
                "items", array(item, 0, 100)
        ), List.of("title","summary","confidence","requiresConfirmation","conflictReportJson","items"));
    }

    public static Map<String, Object> finalAnswerSchema() {
        Map<String, Object> evidence = object(linkedMap(
                "sourceType", enumString("DATABASE","SCHEMA","SEMANTIC_MEMORY","DOCUMENT","ENTITY_RESOLUTION","EFFECTIVE_RULE","ORDER_VALIDATION","PRICE_CALCULATOR","FILE","CONVERSATION","CHANGE_SET","INFERENCE"),
                "objectName", string("근거 이름", 1, 500),
                "detail", string("사용자 공개 가능한 근거 요약", 1, 8000)
        ), List.of("sourceType","objectName","detail"));
        return object(linkedMap(
                "status", enumString("READY_TO_ANSWER","NEED_CLARIFICATION","BLOCKED"),
                "responseClass", enumString("GENERAL","KNOWLEDGE","ORDER","PRICE","LEARNING","MUTATION","SYSTEM_EVENT"),
                "answer", string("GPT가 직접 작성한 한국어 존댓말 최종 답변", 1, 50000),
                "confidence", number(0, 1, "최종 신뢰도"),
                "requiresClarification", bool("사용자 확인 필요 여부"),
                "changeSetId", nullableString("변경계획 UUID", 36),
                "evidence", array(evidence, 0, 80),
                "riskNotes", array(string("사용자에게 알릴 주의사항", 1, 8000), 0, 30)
        ), List.of("status","responseClass","answer","confidence","requiresClarification","changeSetId","evidence","riskNotes"));
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

    private static Map<String, Object> string(String description, int maxLength) { return string(description, 0, maxLength); }
    private static Map<String, Object> string(String description, int minLength, int maxLength) {
        return linkedMap("type","string","minLength",minLength,"maxLength",maxLength,"description",description);
    }
    private static Map<String, Object> nullableString(String description, int maxLength) {
        return linkedMap("type",List.of("string","null"),"maxLength",maxLength,"description",description);
    }
    private static Map<String, Object> bool(String description) { return linkedMap("type","boolean","description",description); }
    private static Map<String, Object> integer(int min, int max, String description) { return linkedMap("type","integer","minimum",min,"maximum",max,"description",description); }
    private static Map<String, Object> number(double min, double max, String description) { return linkedMap("type","number","minimum",min,"maximum",max,"description",description); }
    private static Map<String, Object> enumString(String... values) { return linkedMap("type","string","enum",List.of(values)); }
    private static Map<String, Object> array(Map<String, Object> itemSchema, int minItems, int maxItems) { return linkedMap("type","array","items",itemSchema,"minItems",minItems,"maxItems",maxItems); }
    private static Map<String, Object> linkedMap(Object... values) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i=0;i<values.length;i+=2) map.put(String.valueOf(values[i]), values[i+1]);
        return map;
    }
    private static ToolMeta meta(String name, String category, boolean writeCapable, String description) { return new ToolMeta(name,category,writeCapable,description); }
    private record ToolMeta(String name, String category, boolean writeCapable, String description) {}
}
