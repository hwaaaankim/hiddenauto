package com.dev.HiddenBATHAuto.rag.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.dev.HiddenBATHAuto.rag.config.RagOpenAiProperties;
import com.dev.HiddenBATHAuto.rag.service.RagConversationalLearningService.RagLearningProgressListener;
import com.dev.HiddenBATHAuto.rag.util.RagJsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 긴 입력/대용량 파일용 적응형 트리 학습 해석기입니다.
 *
 * 이 클래스의 목표는 "큰 입력을 한 번에 GPT로 최종 병합"하지 않는 것입니다.
 *
 * 처리 방식:
 * 1) 원문을 의미 단위 세그먼트로 나눕니다.
 * 2) 각 세그먼트는 제한 시간 안에 처리 가능한 leaf 노드로 재귀 분할합니다.
 * 3) GPT 해석이 실패한 leaf는 실패로 버리지 않고 더 작게 쪼갭니다.
 * 4) 더 이상 쪼갤 수 없는 leaf는 원문 보존 노드로 저장 가능한 packet을 만듭니다.
 * 5) 최종 병합은 GPT에게 맡기지 않고 서버가 결정론적으로 트리/JSON을 조립합니다.
 *
 * 따라서 일부 GPT 호출이 timeout 나더라도 전체 작업은 실패하지 않고,
 * 성공한 해석 + 원문 보존 leaf + 미확정 질문이 하나의 knowledgeTreeJson으로 남습니다.
 */
@Service
public class RagLearningChunkedCognitiveService {

    /**
     * leaf가 계속 실패할 때 최종적으로 여기까지 줄입니다.
     * 너무 작게 자르면 의미가 사라지므로 문단/문장 단위가 유지되는 최소값으로 둡니다.
     */
    private static final int MIN_LEAF_CHARS = 260;

    /** GPT 1회 호출에 넣는 원문 최대 크기입니다. 300초를 늘리는 대신 이 크기를 작게 유지합니다. */
    private static final int HARD_MAX_LEAF_CHARS = 2800;

    /** 재귀 분할 최대 깊이입니다. 실패하면 이 깊이 안에서 계속 더 작게 쪼갭니다. */
    private static final int MAX_RECURSION_DEPTH = 12;

    private static final int MAX_PACKET_KNOWLEDGE_TEXT = 5000;

    private final OpenAiRagClient openAi;
    private final ObjectMapper objectMapper;
    private final RagOpenAiProperties properties;

    public RagLearningChunkedCognitiveService(OpenAiRagClient openAi,
                                              ObjectMapper objectMapper,
                                              RagOpenAiProperties properties) {
        this.openAi = openAi;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public boolean shouldUseChunking(String userMessage, String attachmentText) {
        int length = safeLength(userMessage) + safeLength(attachmentText);
        if (StringUtils.hasText(attachmentText)) return true;
        return length >= Math.max(800, properties.getAdaptiveChunkThresholdChars());
    }

    public Map<String, Object> interpret(Map<String, Object> currentVersion,
                                         List<Map<String, Object>> retrievedChunks,
                                         List<Map<String, Object>> conversationHistory,
                                         Map<String, Object> pendingResolution,
                                         String userMessage,
                                         String attachmentFilename,
                                         String attachmentText,
                                         String deterministicExpansionHint,
                                         boolean forceSave) {
        return interpret(currentVersion, retrievedChunks, conversationHistory, pendingResolution,
                userMessage, attachmentFilename, attachmentText, deterministicExpansionHint, forceSave, null);
    }

    public Map<String, Object> interpret(Map<String, Object> currentVersion,
                                         List<Map<String, Object>> retrievedChunks,
                                         List<Map<String, Object>> conversationHistory,
                                         Map<String, Object> pendingResolution,
                                         String userMessage,
                                         String attachmentFilename,
                                         String attachmentText,
                                         String deterministicExpansionHint,
                                         boolean forceSave,
                                         RagLearningProgressListener progressListener) {
        String fullInput = buildInput(userMessage, attachmentFilename, attachmentText);
        if (!StringUtils.hasText(fullInput)) {
            return emptyResult(currentVersion, "해석할 입력이 비어 있습니다.");
        }

        int targetLeafChars = Math.min(HARD_MAX_LEAF_CHARS, Math.max(1600, properties.getAdaptiveChunkChars()));
        int maxLeafChunks = Math.max(20, properties.getAdaptiveMaxLeafChunks());
        int groupSize = Math.max(2, Math.min(6, properties.getAdaptiveGroupSize()));

        notify(progressListener, "GPT_INTERPRETING", 32,
                "입력을 의미 단위로 나누고, 실패 시 더 작게 재분할하는 트리형 학습을 시작합니다.");

        List<Segment> seedSegments = splitToSegments(fullInput, targetLeafChars, maxLeafChunks);
        notify(progressListener, "GPT_INTERPRETING", 34,
                "1차 의미 세그먼트 " + seedSegments.size() + "개를 생성했습니다.");

        List<Map<String, Object>> leafPackets = new ArrayList<>();
        int[] leafCounter = new int[]{0};
        for (int i = 0; i < seedSegments.size(); i++) {
            Segment segment = seedSegments.get(i);
            int progress = 34 + (int) Math.floor(((i + 1) * 22.0) / Math.max(1, seedSegments.size()));
            notify(progressListener, "GPT_INTERPRETING", progress,
                    "세그먼트 " + (i + 1) + "/" + seedSegments.size() + " 해석 중입니다. 실패하면 자동으로 더 작게 나눕니다.");
            leafPackets.addAll(interpretAdaptive(segment, targetLeafChars, 1, leafCounter));
        }

        notify(progressListener, "GPT_INTERPRETING", 58,
                "leaf 해석 결과 " + leafPackets.size() + "개를 상위 트리 노드로 병합하고 있습니다.");

        List<Map<String, Object>> topPackets = reducePackets(leafPackets, groupSize, progressListener);

        notify(progressListener, "GPT_INTERPRETING", 70,
                "최종 GPT 대형 병합 없이 서버가 트리/주문 프로세스/가격계산 JSON을 조립하고 있습니다.");

        return assembleFinal(currentVersion, retrievedChunks, conversationHistory, pendingResolution,
                userMessage, attachmentFilename, topPackets, deterministicExpansionHint, forceSave,
                seedSegments.size(), leafPackets.size(), fullInput.length());
    }

    private List<Map<String, Object>> interpretAdaptive(Segment segment,
                                                        int targetLeafChars,
                                                        int depth,
                                                        int[] leafCounter) {
        String text = segment.text();
        if (!StringUtils.hasText(text)) return List.of();

        int nextTarget = Math.max(MIN_LEAF_CHARS, Math.min(targetLeafChars, HARD_MAX_LEAF_CHARS));
        if (text.length() > nextTarget) {
            List<Segment> smaller = splitToSegments(text, Math.max(MIN_LEAF_CHARS, nextTarget / 2), 5000);
            if (smaller.size() > 1 && depth <= MAX_RECURSION_DEPTH) {
                List<Map<String, Object>> result = new ArrayList<>();
                for (int i = 0; i < smaller.size(); i++) {
                    Segment child = smaller.get(i).withPath(segment.path() + "/" + (i + 1));
                    result.addAll(interpretAdaptive(child, Math.max(MIN_LEAF_CHARS, nextTarget / 2), depth + 1, leafCounter));
                }
                return result;
            }
        }

        try {
            leafCounter[0]++;
            return List.of(interpretLeafWithAi(leafCounter[0], segment));
        } catch (Exception firstError) {
            // 1차 해석 실패 시 같은 조각을 더 작은 micro JSON으로 한 번 더 시도합니다.
            // 이 단계는 최종 GPT 병합이 아니라 "작은 leaf 하나"만 다루므로, 성공하면 AI_PARSED_MICRO로 승격됩니다.
            try {
                return List.of(interpretLeafWithMicroAi(leafCounter[0], segment, firstError));
            } catch (Exception microError) {
                if (text.length() > MIN_LEAF_CHARS && depth <= MAX_RECURSION_DEPTH) {
                    List<Segment> smaller = splitToSegments(text, Math.max(MIN_LEAF_CHARS, text.length() / 2), 5000);
                    if (smaller.size() > 1) {
                        List<Map<String, Object>> result = new ArrayList<>();
                        for (int i = 0; i < smaller.size(); i++) {
                            Segment child = smaller.get(i).withPath(segment.path() + "/retry" + (i + 1));
                            result.addAll(interpretAdaptive(child, Math.max(MIN_LEAF_CHARS, text.length() / 2), depth + 1, leafCounter));
                        }
                        return result;
                    }
                }
                leafCounter[0]++;
                return List.of(deterministicLeafFallback(leafCounter[0], segment, microError));
            }
        }
    }

    private Map<String, Object> interpretLeafWithAi(int leafNo, Segment segment) {
        String system = """
                당신은 HiddenBATHAuto RAG 학습 트리의 leaf 해석 AI입니다.
                지금 단계는 전체 최종 결론이 아니라, 원문 일부를 작은 지식 노드로 바꾸는 단계입니다.

                원칙:
                - 입력 조각에 실제로 있는 내용만 추출하세요.
                - 가격/범위/가능 여부를 추정하지 마세요.
                - 확정 지식과 확인 필요 지식을 분리하세요.
                - 계산 전에 확인이 필요한 내용은 openQuestions 또는 requiredArtifacts로 남기세요.
                - 저장 자체를 막아야 하는 충돌이 아니라면 clarificationQuestions를 만들지 마세요.
                - JSON 객체 하나만 반환하세요.

                반환 스키마:
                {
                  "packetType":"LEAF_AI",
                  "facts":[],
                  "orderSteps":[],
                  "questionRules":[],
                  "conditionalQuestions":[],
                  "answerTypes":[],
                  "validationRules":[],
                  "pricingRules":[],
                  "pricingOrder":[],
                  "requiredArtifacts":[],
                  "replacementRules":[],
                  "openQuestions":[],
                  "conflictCandidates":[],
                  "nodeProposals":[],
                  "evidence":[],
                  "knowledgeText":"이 leaf에서 저장 가능한 지식 요약"
                }
                """;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("leafNo", leafNo);
        payload.put("path", segment.path());
        payload.put("title", segment.title());
        payload.put("text", segment.text());

        String raw = openAi.responseJson(system, RagJsonUtils.pretty(objectMapper, payload));
        JsonNode node = RagJsonUtils.extractObjectNode(objectMapper, raw);
        Map<String, Object> parsed = RagJsonUtils.toMap(objectMapper, node.toString());
        parsed.put("packetType", "LEAF_AI");
        parsed.put("leafNo", leafNo);
        parsed.put("path", segment.path());
        parsed.put("title", segment.title());
        parsed.put("sourceStatus", "AI_PARSED");
        parsed.putIfAbsent("rawTextPreview", RagJsonUtils.truncate(segment.text(), 1200));
        normalizePacketLists(parsed);
        return parsed;
    }

    private Map<String, Object> interpretLeafWithMicroAi(int leafNo, Segment segment, Exception previousError) {
        String system = """
                당신은 HiddenBATHAuto RAG 학습 트리의 micro leaf 해석 AI입니다.
                이전 full leaf JSON 해석이 실패했으므로 이번에는 더 작고 단순한 JSON만 반환합니다.
                원문에 있는 문장만 분류하고, 추정하지 마세요.
                JSON 객체 하나만 반환하세요.

                반환 스키마:
                {
                  "facts":[],
                  "questionRules":[],
                  "conditionalQuestions":[],
                  "validationRules":[],
                  "pricingRules":[],
                  "requiredArtifacts":[],
                  "openQuestions":[],
                  "evidence":[],
                  "knowledgeText":"짧은 요약"
                }
                """;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("leafNo", leafNo);
        payload.put("path", segment.path());
        payload.put("title", segment.title());
        payload.put("previousError", previousError == null ? "" : previousError.getMessage());
        payload.put("text", segment.text());

        String raw = openAi.responseJson(system, RagJsonUtils.pretty(objectMapper, payload));
        JsonNode node = RagJsonUtils.extractObjectNode(objectMapper, raw);
        Map<String, Object> parsed = RagJsonUtils.toMap(objectMapper, node.toString());
        parsed.put("packetType", "LEAF_AI_MICRO");
        parsed.put("leafNo", leafNo);
        parsed.put("path", segment.path());
        parsed.put("title", segment.title());
        parsed.put("sourceStatus", "AI_PARSED_MICRO");
        parsed.putIfAbsent("rawTextPreview", RagJsonUtils.truncate(segment.text(), 1200));
        parsed.putIfAbsent("orderSteps", List.of());
        parsed.putIfAbsent("answerTypes", List.of());
        parsed.putIfAbsent("pricingOrder", List.of());
        parsed.putIfAbsent("replacementRules", List.of());
        parsed.putIfAbsent("conflictCandidates", List.of());
        parsed.putIfAbsent("nodeProposals", List.of());
        normalizePacketLists(parsed);
        return parsed;
    }

    private Map<String, Object> deterministicLeafFallback(int leafNo, Segment segment, Exception error) {
        Map<String, Object> packet = new LinkedHashMap<>();
        packet.put("packetType", "LEAF_DETERMINISTIC_FALLBACK");
        packet.put("leafNo", leafNo);
        packet.put("path", segment.path());
        packet.put("title", segment.title());
        packet.put("sourceStatus", "SERVER_EXTRACTED_NEEDS_AI_RETRY");
        packet.put("interpretationError", String.valueOf(error.getMessage()));

        HeuristicExtraction h = heuristicExtract(segment.text());
        packet.put("facts", h.facts);
        packet.put("orderSteps", h.orderSteps);
        packet.put("questionRules", h.questionRules);
        packet.put("conditionalQuestions", h.conditionalQuestions);
        packet.put("answerTypes", h.answerTypes);
        packet.put("validationRules", h.validationRules);
        packet.put("pricingRules", h.pricingRules);
        packet.put("pricingOrder", h.pricingOrder);
        packet.put("requiredArtifacts", h.requiredArtifacts);
        packet.put("replacementRules", h.replacementRules);
        packet.put("openQuestions", h.openQuestions);
        packet.put("conflictCandidates", List.of());
        packet.put("nodeProposals", List.of(Map.of(
                "nodeType", "RAW_PRESERVED_SEGMENT",
                "title", firstText(segment.title(), "GPT 해석 실패 원문 보존 leaf"),
                "summary", "GPT leaf 해석이 실패해 원문과 휴리스틱 추출 결과를 보존했습니다.",
                "status", "NEEDS_AI_RETRY",
                "retryable", true
        )));
        packet.put("evidence", List.of(RagJsonUtils.truncate(segment.text(), 1800)));
        packet.put("rawTextPreview", RagJsonUtils.truncate(segment.text(), 2400));
        packet.put("knowledgeText", buildDeterministicKnowledgeText(segment, h));
        return packet;
    }

    private List<Map<String, Object>> reducePackets(List<Map<String, Object>> packets,
                                                     int groupSize,
                                                     RagLearningProgressListener progressListener) {
        List<Map<String, Object>> current = packets == null ? new ArrayList<>() : new ArrayList<>(packets);
        int level = 1;
        while (current.size() > groupSize) {
            notify(progressListener, "GPT_INTERPRETING", Math.min(68, 58 + level * 4),
                    "트리 packet을 " + groupSize + "개 단위로 병합합니다. level=" + level + ", packet=" + current.size());
            List<Map<String, Object>> next = new ArrayList<>();
            for (int start = 0; start < current.size(); start += groupSize) {
                int end = Math.min(current.size(), start + groupSize);
                List<Map<String, Object>> group = new ArrayList<>(current.subList(start, end));
                next.add(mergeGroup(level, next.size() + 1, group));
            }
            current = next;
            level++;
        }
        return current;
    }

    private Map<String, Object> mergeGroup(int level, int groupNo, List<Map<String, Object>> group) {
        String system = """
                당신은 HiddenBATHAuto RAG 학습 트리의 중간 병합 AI입니다.
                여러 하위 packet을 하나의 상위 packet으로 압축하되, 지식을 삭제하지 말고 중복만 줄이세요.

                원칙:
                - 확정 지식, 조건부 질문, 검증 규칙, 가격 규칙, 필요 파일, 확인 질문을 구분하세요.
                - 저장을 막지 않는 미확정 계산 질문은 openQuestions로 유지하세요.
                - JSON 객체 하나만 반환하세요.

                반환 스키마는 leaf packet과 동일하며 packetType은 MERGED_AI입니다.
                """;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("level", level);
        payload.put("groupNo", groupNo);
        payload.put("packets", compactPackets(group, 2800));
        try {
            String raw = openAi.responseJson(system, RagJsonUtils.pretty(objectMapper, payload));
            JsonNode node = RagJsonUtils.extractObjectNode(objectMapper, raw);
            Map<String, Object> parsed = RagJsonUtils.toMap(objectMapper, node.toString());
            parsed.put("packetType", "MERGED_AI");
            parsed.put("level", level);
            parsed.put("groupNo", groupNo);
            parsed.put("sourceStatus", "AI_MERGED");
            normalizePacketLists(parsed);
            return parsed;
        } catch (Exception e) {
            return deterministicGroupMerge(level, groupNo, group, e);
        }
    }

    private Map<String, Object> deterministicGroupMerge(int level, int groupNo, List<Map<String, Object>> group, Exception error) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("packetType", "MERGED_DETERMINISTIC_FALLBACK");
        result.put("level", level);
        result.put("groupNo", groupNo);
        result.put("sourceStatus", "DETERMINISTIC_MERGED_AFTER_AI_FAILURE");
        result.put("mergeError", String.valueOf(error.getMessage()));
        for (String key : packetListKeys()) {
            result.put(key, distinctObjects(concatLists(group, key)));
        }
        List<Object> openQuestions = new ArrayList<>(RagJsonUtils.childList(result, "openQuestions"));
        openQuestions.add("중간 병합 AI 실패로 서버가 결정론적으로 병합했습니다. 오류: " + error.getMessage());
        result.put("openQuestions", distinctObjects(openQuestions));
        result.put("knowledgeText", joinKnowledge(group, MAX_PACKET_KNOWLEDGE_TEXT));
        return result;
    }

    private Map<String, Object> assembleFinal(Map<String, Object> currentVersion,
                                              List<Map<String, Object>> retrievedChunks,
                                              List<Map<String, Object>> conversationHistory,
                                              Map<String, Object> pendingResolution,
                                              String userMessage,
                                              String attachmentFilename,
                                              List<Map<String, Object>> topPackets,
                                              String deterministicExpansionHint,
                                              boolean forceSave,
                                              int seedSegmentCount,
                                              int leafPacketCount,
                                              int inputLength) {
        List<Object> facts = flattenDistinct(topPackets, "facts");
        List<Object> orderSteps = flattenDistinct(topPackets, "orderSteps");
        List<Object> questionRules = flattenDistinct(topPackets, "questionRules");
        List<Object> conditionalQuestions = flattenDistinct(topPackets, "conditionalQuestions");
        List<Object> answerTypes = flattenDistinct(topPackets, "answerTypes");
        List<Object> validationRules = flattenDistinct(topPackets, "validationRules");
        List<Object> pricingRules = flattenDistinct(topPackets, "pricingRules");
        List<Object> pricingOrder = flattenDistinct(topPackets, "pricingOrder");
        List<Object> requiredArtifacts = flattenDistinct(topPackets, "requiredArtifacts");
        List<Object> replacementRules = flattenDistinct(topPackets, "replacementRules");
        List<Object> openQuestions = flattenDistinct(topPackets, "openQuestions");
        List<Object> conflictCandidates = flattenDistinct(topPackets, "conflictCandidates");
        List<Object> nodeProposals = flattenDistinct(topPackets, "nodeProposals");

        List<Object> warnings = collectWarnings(topPackets);
        if (!conflictCandidates.isEmpty()) {
            warnings.add("충돌 후보가 있습니다. 확정 충돌로 단정하지 않고 검토 노드로 저장했습니다.");
        }

        Map<String, Object> processJson = new LinkedHashMap<>();
        processJson.put("schemaType", "ORDER_PROCESS_BUILDER_V3");
        processJson.put("assemblyMode", "ADAPTIVE_TREE_SERVER_ASSEMBLED");
        processJson.put("steps", normalizeSteps(orderSteps));
        processJson.put("questionRules", questionRules);
        processJson.put("conditionalQuestions", conditionalQuestions);
        processJson.put("answerTypes", answerTypes);
        processJson.put("facts", facts);

        Map<String, Object> pricingJson = new LinkedHashMap<>();
        pricingJson.put("schemaType", "ORDER_PRICING_ENGINE_V3");
        pricingJson.put("assemblyMode", "ADAPTIVE_TREE_SERVER_ASSEMBLED");
        pricingJson.put("calculationRules", pricingRules);
        pricingJson.put("calculationOrder", pricingOrder);
        pricingJson.put("excelTables", inferExcelTables(requiredArtifacts));
        pricingJson.put("requiredArtifacts", requiredArtifacts);
        pricingJson.put("openQuestions", openQuestions);

        Map<String, Object> constraintsJson = new LinkedHashMap<>();
        constraintsJson.put("schemaType", "ORDER_CONSTRAINTS_V3");
        constraintsJson.put("assemblyMode", "ADAPTIVE_TREE_SERVER_ASSEMBLED");
        constraintsJson.put("rules", validationRules);
        constraintsJson.put("skipRules", inferSkipRules(conditionalQuestions));
        constraintsJson.put("answerFilterRules", questionRules);
        constraintsJson.put("asPolicyRules", inferAsPolicyRules(validationRules));
        constraintsJson.put("replacementRules", replacementRules);

        applyBathroomCabinetOrderTemplateIfDetected(userMessage, processJson, pricingJson, constraintsJson,
                requiredArtifacts, openQuestions, warnings);
        orderSteps = RagJsonUtils.childList(processJson, "steps");
        pricingRules = RagJsonUtils.childList(pricingJson, "calculationRules");
        pricingOrder = RagJsonUtils.childList(pricingJson, "calculationOrder");
        validationRules = RagJsonUtils.childList(constraintsJson, "rules");

        Map<String, Object> tree = buildKnowledgeTree(userMessage, attachmentFilename, topPackets, nodeProposals,
                processJson, pricingJson, constraintsJson, requiredArtifacts, openQuestions, warnings,
                seedSegmentCount, leafPacketCount, inputLength);

        Map<String, Object> validationReport = new LinkedHashMap<>();
        validationReport.put("status", warnings.isEmpty() ? "OK" : "TREE_SAVED_WITH_RETRYABLE_NODES");
        validationReport.put("warnings", distinctObjects(warnings));
        validationReport.put("assumptions", List.of(
                "최종 대형 GPT 병합은 사용하지 않고 서버가 leaf/packet 결과를 결정론적으로 조립했습니다.",
                "확인 필요 항목은 저장 차단 질문이 아니라 openQuestions/nonBlockingQuestions로 보존했습니다."
        ));
        validationReport.put("resolvedClarifications", List.of());
        validationReport.put("changePlan", buildChangePlan(replacementRules, userMessage));
        validationReport.put("requiredArtifacts", requiredArtifacts);
        validationReport.put("serverCalculationNotes", openQuestions);
        validationReport.put("treeStats", Map.of(
                "seedSegmentCount", seedSegmentCount,
                "leafPacketCount", leafPacketCount,
                "topPacketCount", topPackets == null ? 0 : topPackets.size(),
                "nodeCount", RagJsonUtils.childList(tree, "nodes").size()
        ));
        validationReport.put("readyToPublish", true);
        validationReport.put("retryableNodeCount", countRetryablePackets(topPackets));

        String summary = buildSummary(userMessage, facts, orderSteps, pricingRules, requiredArtifacts, openQuestions, warnings);
        String answer = buildAnswer(processJson, pricingJson, constraintsJson, requiredArtifacts, openQuestions, tree, warnings);
        String knowledgeText = buildKnowledgeText(summary, processJson, pricingJson, constraintsJson, tree, deterministicExpansionHint);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("intent", attachmentFilename == null || attachmentFilename.isBlank() ? "LEARN_TEXT" : "LEARN_MIXED");
        result.put("inputInterpretation", Map.of(
                "userGoal", "대화형 RAG 학습",
                "normalizedUserInput", RagJsonUtils.truncate(userMessage, 5000),
                "isLearningInput", true,
                "isQuestion", false,
                "hasFile", StringUtils.hasText(attachmentFilename),
                "hasPersistableKnowledge", true
        ));
        result.put("requiresUpload", false);
        result.put("requiresClarification", false);
        result.put("shouldPersist", true);
        result.put("answer", answer);
        result.put("clarificationQuestions", List.of());
        result.put("nonBlockingQuestions", openQuestions);
        result.put("conflicts", List.of());
        result.put("conflictCandidates", conflictCandidates);
        result.put("materials", List.of());
        result.put("summary", summary);
        result.put("processJson", processJson);
        result.put("pricingJson", pricingJson);
        result.put("constraintsJson", constraintsJson);
        result.put("validationReportJson", validationReport);
        result.put("knowledgeTreeJson", tree);
        result.put("knowledgeText", knowledgeText);
        result.put("confidence", warnings.isEmpty() ? 0.92 : 0.76);
        result.put("cognitiveEngine", "RagLearningChunkedCognitiveService");
        result.put("cognitiveEngineVersion", "20260612-adaptive-tree-v4-eventual-ai-parse");
        result.put("retrievedContextCount", retrievedChunks == null ? 0 : retrievedChunks.size());
        result.put("historyContextCount", conversationHistory == null ? 0 : conversationHistory.size());
        result.put("pendingResolutionContext", pendingResolution == null ? Map.of() : pendingResolution);
        result.put("forceSave", forceSave);
        return result;
    }


    private void applyBathroomCabinetOrderTemplateIfDetected(String userMessage,
                                                             Map<String, Object> processJson,
                                                             Map<String, Object> pricingJson,
                                                             Map<String, Object> constraintsJson,
                                                             List<Object> requiredArtifacts,
                                                             List<Object> openQuestions,
                                                             List<Object> warnings) {
        if (!isBathroomCabinetOrderLearningInput(userMessage)) return;

        processJson.put("domain", "하부장 비규격 주문 및 가격계산");
        processJson.put("templateApplied", "BATHROOM_CABINET_NON_STANDARD_ORDER_V1");
        processJson.put("steps", bathroomCabinetOrderSteps());
        processJson.put("conditionalQuestions", bathroomCabinetConditionalQuestions());
        processJson.put("answerTypes", bathroomCabinetAnswerTypes());
        processJson.put("questionRules", bathroomCabinetQuestionRules());
        processJson.put("requiredArtifacts", bathroomCabinetRequiredArtifacts());

        pricingJson.put("domain", "하부장 비규격 가격계산");
        pricingJson.put("templateApplied", "BATHROOM_CABINET_NON_STANDARD_PRICING_V1");
        pricingJson.put("calculationOrder", bathroomCabinetPricingOrder());
        pricingJson.put("calculationRules", bathroomCabinetPricingRules());
        pricingJson.put("excelTables", bathroomCabinetExcelTables());
        pricingJson.put("requiredArtifacts", bathroomCabinetRequiredArtifacts());
        pricingJson.put("openQuestions", bathroomCabinetOpenQuestions());

        constraintsJson.put("domain", "하부장 비규격 검증/조건 규칙");
        constraintsJson.put("templateApplied", "BATHROOM_CABINET_NON_STANDARD_CONSTRAINTS_V1");
        constraintsJson.put("rules", bathroomCabinetValidationRules());
        constraintsJson.put("skipRules", bathroomCabinetSkipRules());
        constraintsJson.put("answerFilterRules", bathroomCabinetAnswerFilterRules());
        constraintsJson.put("asPolicyRules", List.of("H가 1200을 초과하는 제작은 가능할 수 있으나 무상 AS 불가 안내가 필요합니다."));
        constraintsJson.put("replacementRules", bathroomCabinetReplacementRules());

        requiredArtifacts.clear();
        requiredArtifacts.addAll(bathroomCabinetRequiredArtifacts());
        openQuestions.clear();
        openQuestions.addAll(bathroomCabinetOpenQuestions());
        warnings.add("하부장 비규격 주문/가격계산 장문 학습 입력이 감지되어 서버 표준 템플릿으로 누락 방지 구조화를 보강했습니다.");
    }

    private boolean isBathroomCabinetOrderLearningInput(String userMessage) {
        if (!StringUtils.hasText(userMessage)) return false;
        String t = userMessage.replaceAll("\\s+", "");
        return containsAny(t, "하부장", "비규격")
                && containsAny(t, "주문프로세스", "가격계산", "주문흐름")
                && containsAny(t, "시리즈", "품목", "W", "D", "H");
    }

    private List<Object> bathroomCabinetOrderSteps() {
        List<Object> steps = new ArrayList<>();
        steps.add(step(1, "series", "시리즈 선택", "시리즈를 선택해 주세요.", "SELECT", true, null, null, "시리즈별 품목/색상 가능 자료와 연결"));
        steps.add(step(2, "item", "품목 선택", "품목을 선택해 주세요.", "SELECT", true, null, null, "품목별 사이즈 제한표/기본가격표와 연결"));
        steps.add(step(3, "mountType", "형태 선택", "형태를 선택해 주세요.", "SELECT", true, List.of("다리형", "벽걸이형"), null, "걸레받이 조건부 질문과 연결"));
        steps.add(step(4, "size", "사이즈 입력", "W, D, H 값을 mm 단위로 입력해 주세요.", "SIZE_WDH", true, null, null, "실제 제작값과 가격표 조회용 100단위 올림값을 구분"));
        steps.add(step(5, "color", "색상 선택", "가능한 색상 중 선택해 주세요.", "SELECT", true, null, null, "시리즈+품목별 색상 가능표 필요"));
        steps.add(step(6, "countertop", "상판 선택", "상판을 선택해 주세요.", "SELECT", false, null, "series == '마블시리즈'", "마블시리즈에서만 노출"));
        steps.add(step(7, "basin", "세면대 수량 및 종류", "세면대 수량과 종류를 선택해 주세요.", "OPTION_GROUP", false, null, "series != '마블시리즈'", "마블시리즈가 아닐 때만 노출"));
        steps.add(step(8, "basinPosition", "세면대 위치", "도면 파일을 업로드하거나 좌측/중앙/우측/좌측에서 300mm 같은 텍스트로 입력해 주세요.", "FILE_IMAGE_OR_TEXT", false, null, "series != '마블시리즈' && basin.count > 0", "2차원 좌표 또는 설명형 위치"));
        steps.add(step(9, "doorEnabled", "문 설치 여부", "문을 설치하시겠습니까?", "BOOLEAN", true, List.of("예", "아니오"), null, "문 추가금/문 구성 검증과 연결"));
        steps.add(step(10, "doorConfiguration", "문 구성", "여닫이와 서랍의 개수를 입력해 주세요. 혼합 구성이 가능합니다.", "OPTION_GROUP", false, null, "doorEnabled == true", "W 구간별 여닫이/서랍 최대 개수 검증"));
        steps.add(step(11, "handleEnabled", "손잡이 설치 여부", "손잡이를 설치하시겠습니까?", "BOOLEAN", false, List.of("예", "아니오"), "doorEnabled == true", "손잡이 종류/대상/개수 반복 입력"));
        steps.add(step(12, "handleDetails", "손잡이 종류 및 설치 대상", "어떤 문 또는 서랍에 어떤 손잡이를 몇 개 설치할지 입력해 주세요.", "REPEAT_GROUP", false, null, "handleEnabled == true", "손잡이 가격표 필요"));
        steps.add(step(13, "edgeBandingEnabled", "마구리 설치 여부", "마구리를 설치하시겠습니까?", "BOOLEAN", true, List.of("예", "아니오"), null, "마구리 높이/설치 면과 연결"));
        steps.add(step(14, "edgeBandingHeight", "마구리 높이", "마구리 높이를 1~250mm 범위로 입력해 주세요.", "NUMBER", false, null, "edgeBandingEnabled == true", "150mm 미만 무료, 150mm 이상 길이*100원"));
        steps.add(step(15, "edgeBandingSides", "마구리 설치 면", "설치 면을 선택해 주세요.", "SELECT", false, List.of("좌측", "우측", "후방", "전면", "전좌", "전우", "전좌우", "전좌우후"), "edgeBandingEnabled == true", "설치 면에 따라 W/D 길이 합산"));
        steps.add(step(16, "toeKick", "걸레받이", "걸레받이가 필요하십니까?", "SELECT", false, null, "mountType == '다리형'", "벽걸이형에서는 질문하지 않음"));
        steps.add(step(17, "holeEnabled", "타공 설치 여부", "타공이 필요하십니까?", "BOOLEAN", true, List.of("예", "아니오"), null, "타공 개수/위치와 연결"));
        steps.add(step(18, "holeCount", "타공 개수", "타공 개수를 입력해 주세요.", "NUMBER", false, null, "holeEnabled == true", "1개 무료, 2개부터 추가금 규칙 확인 필요"));
        steps.add(step(19, "holePosition", "타공 위치", "도면 파일을 업로드하거나 텍스트로 위치를 입력해 주세요.", "FILE_IMAGE_OR_TEXT", false, null, "holeEnabled == true", "도면 또는 텍스트 위치"));
        steps.add(step(20, "extraOptions", "기타 옵션", "드라이걸이, 휴지걸이, LED 중 필요한 옵션을 선택해 주세요.", "MULTI_SELECT", false, List.of("드라이걸이", "휴지걸이", "LED"), null, "품목별 노출 여부는 추가 확인 가능"));
        steps.add(step(21, "extraOptionPositions", "기타 옵션 위치", "선택한 옵션의 설치 위치를 상/하/좌/우 중 입력해 주세요.", "REPEAT_GROUP", false, List.of("상", "하", "좌", "우"), "extraOptions not empty", "옵션별 위치 입력"));
        return steps;
    }

    private Map<String, Object> step(int orderNo, String stepKey, String title, String question, String answerType,
                                    boolean required, Object options, String showCondition, String note) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("orderNo", orderNo);
        row.put("stepKey", stepKey);
        row.put("title", title);
        row.put("question", question);
        row.put("answerType", answerType);
        row.put("required", required);
        row.put("options", options == null ? List.of() : options);
        row.put("showCondition", showCondition == null ? "ALWAYS" : showCondition);
        row.put("note", note);
        row.put("source", "BATHROOM_CABINET_TEMPLATE");
        return row;
    }

    private List<Object> bathroomCabinetConditionalQuestions() {
        return List.of(
                rule("상판 질문은 마블시리즈에서만 노출하고, 마블시리즈가 아니면 질문하지 않습니다."),
                rule("세면대 수량/종류/위치 질문은 마블시리즈가 아닐 때만 노출하고, 마블시리즈에서는 질문하지 않습니다."),
                rule("문 구성 질문은 문 설치 여부가 예일 때만 노출합니다."),
                rule("손잡이 종류 및 설치 대상 질문은 손잡이 설치 여부가 예일 때만 노출합니다."),
                rule("걸레받이 질문은 형태가 다리형인 경우에만 노출하고, 벽걸이형이면 질문하지 않습니다."),
                rule("타공 개수/위치 질문은 타공 설치 여부가 예일 때만 노출합니다."),
                rule("기타 옵션 위치 질문은 기타 옵션을 1개 이상 선택한 경우에만 노출합니다.")
        );
    }

    private List<Object> bathroomCabinetAnswerTypes() {
        return List.of(
                rule("시리즈/품목/형태/색상/상판/마구리 설치 면은 선택형입니다."),
                rule("사이즈 W/D/H, 마구리 높이, 타공 개수는 숫자 입력형입니다."),
                rule("상판, 세면대, 걸레받이는 조건부 선택형입니다."),
                rule("세면대 위치와 타공 위치는 파일 업로드 또는 텍스트 입력형입니다."),
                rule("문 구성과 손잡이 대상은 조합/반복 입력형입니다."),
                rule("기타 옵션은 다중 선택형이고 위치는 선택형입니다.")
        );
    }

    private List<Object> bathroomCabinetQuestionRules() {
        return List.of(
                rule("고객 입력값과 가격표 조회값은 분리합니다. W/D/H 실제 제작값은 보존하고, 가격표 조회 시 필요한 값만 100단위 올림 처리합니다."),
                rule("엑셀이 업로드되면 먼저 자료 역할을 기본가격표/사이즈제한표/색상가능표/상판가격표/세면대가격표/손잡이가격표/옵션가격표 중 하나로 판단합니다."),
                rule("엑셀 구조가 1차원 테이블인지 2차원 매트릭스인지 판단하고, 애매하면 저장하지 않고 질문합니다."),
                rule("교체인지 추가인지 애매한 파일은 저장하지 않고 확인 질문을 합니다.")
        );
    }

    private List<Object> bathroomCabinetPricingOrder() {
        return List.of(
                "1. 품목 및 W/D 기준 기본가격 계산",
                "2. H 추가금 계산",
                "3. 마블시리즈인 경우 상판 추가금 계산",
                "4. 마블시리즈가 아닌 경우 세면대 추가금 계산",
                "5. 문 추가금 계산",
                "6. 손잡이 추가금 계산",
                "7. 마구리 추가금 계산",
                "8. 타공 추가금 계산",
                "9. 기타 옵션 추가금 계산",
                "10. 모든 항목 합산"
        );
    }

    private List<Object> bathroomCabinetPricingRules() {
        List<Object> rules = new ArrayList<>();
        rules.add(map("ruleKey", "basePrice", "type", "BASE_TABLE_LOOKUP", "description", "품목별 W-D 2차원 기본가격표에서 가격을 조회합니다.", "roundingPolicy", "W와 D를 100단위 올림", "requiredTable", "BASE_PRICE_MATRIX", "outputKey", "basePrice"));
        rules.add(map("ruleKey", "heightSurcharge", "type", "HEIGHT_SURCHARGE_BY_100_CEIL", "description", "H가 600 이하이면 0원, 600 초과이면 H를 100단위 올림 후 600 초과 100단위마다 5,000원 추가", "baseHeight", 600, "unit", 100, "unitPrice", 5000, "outputKey", "heightSurcharge"));
        rules.add(map("ruleKey", "countertopSurcharge", "type", "MATRIX_LOOKUP", "description", "마블시리즈에서만 선택 상판의 1/2/3단가군별 W-D 상판 가격표로 조회", "requiredTable", "COUNTERTOP_PRICE_MATRIX", "condition", "series == '마블시리즈'", "roundingPolicy", "W/D 100단위 올림 여부 확인 필요", "outputKey", "countertopSurcharge"));
        rules.add(map("ruleKey", "basinSurcharge", "type", "CUSTOM_FORMULA_PENDING_IMPLEMENTATION", "description", "마블시리즈가 아닌 경우 세면대 종류별 추가금을 계산", "requiredTable", "SINK_PRICE_TABLE", "condition", "series != '마블시리즈'", "outputKey", "basinSurcharge"));
        rules.add(map("ruleKey", "doorSurcharge", "type", "FIXED_PER_COUNT", "description", "여닫이 1개당 10,000원, 서랍 1개당 5,000원", "unitPrices", map("swingDoor", 10000, "drawer", 5000), "outputKey", "doorSurcharge"));
        rules.add(map("ruleKey", "handleSurcharge", "type", "HANDLE_PRICE_BY_TYPE", "description", "손잡이 종류별 단가와 설치 개수를 기준으로 계산", "requiredTable", "HANDLE_PRICE_TABLE", "candidatePrices", List.of(10000, 15000, 20000, 25000, 30000), "outputKey", "handleSurcharge"));
        rules.add(map("ruleKey", "edgeBandingSurcharge", "type", "EDGE_BANDING_BY_SIDES", "description", "마구리 높이 150 미만은 무료, 150 이상은 설치 면 기준 길이*100원", "freeUnderHeight", 150, "unitPricePerMm", 100, "sideLengthRules", edgeBandingSideRules(), "outputKey", "edgeBandingSurcharge"));
        rules.add(map("ruleKey", "holeSurcharge", "type", "FREE_COUNT_THEN_UNIT", "description", "타공 1개까지 무료, 2개부터 20,000원 추가. 총액인지 개당인지 확인 필요", "freeCount", 1, "pendingPolicy", "2개 이상 총 20,000원인지 2개째부터 개당 20,000원인지 확인 필요", "outputKey", "holeSurcharge"));
        rules.add(map("ruleKey", "extraOptionSurcharge", "type", "FIXED_AMOUNT_WHEN_SELECTED", "description", "드라이걸이 5,000원, 휴지걸이 5,000원, LED 20,000원", "optionPrices", map("드라이걸이", 5000, "휴지걸이", 5000, "LED", 20000), "outputKey", "extraOptionSurcharge"));
        rules.add(map("ruleKey", "totalPrice", "type", "SUM", "description", "최종 가격 합산", "inputs", List.of("basePrice", "heightSurcharge", "countertopSurcharge", "basinSurcharge", "doorSurcharge", "handleSurcharge", "edgeBandingSurcharge", "holeSurcharge", "extraOptionSurcharge"), "outputKey", "totalPrice"));
        return rules;
    }

    private Map<String, Object> edgeBandingSideRules() {
        return map(
                "좌측", "D", "우측", "D", "후방", "W", "전면", "W",
                "전좌", "W + D", "전우", "W + D", "전좌우", "W + D + D", "전좌우후", "W + D + D + W"
        );
    }

    private List<Object> bathroomCabinetValidationRules() {
        return List.of(
                rule("사이즈 예시 기준은 W 600~1800, D 500~1000, H 600~1200입니다. 정확한 품목별 범위는 사이즈 제한표 엑셀로 확정해야 합니다."),
                rule("H가 1200을 초과하는 제작은 가능할 수 있으나 무상 AS가 불가능하다는 안내가 필요합니다."),
                rule("W 600 미만은 세면대 1개까지 가능합니다."),
                rule("W 600 이상 1000 미만은 세면대 2개까지 가능합니다."),
                rule("W 1000 이상은 세면대 3개까지 가능합니다."),
                rule("W 600 미만은 여닫이 최대 2칸, 서랍 최대 4개까지 가능합니다."),
                rule("W 600 이상 1000 미만은 여닫이 최대 3칸, 서랍 최대 6개까지 가능합니다."),
                rule("W 1000 이상은 여닫이 최대 4칸, 서랍 최대 8개까지 가능합니다."),
                rule("여닫이 1칸 자리에 서랍 1개 또는 2개를 넣을 수 있으므로 혼합식 구성이 가능합니다."),
                rule("마구리 높이는 1~250 범위이며, 150 미만은 무료입니다."),
                rule("기본가격 W/D와 H 추가금 계산은 100단위 올림 정책을 사용합니다.")
        );
    }

    private List<Object> bathroomCabinetSkipRules() {
        return List.of(
                rule("마블시리즈가 아닌 경우 상판 질문을 하지 않습니다."),
                rule("마블시리즈인 경우 세면대 질문을 하지 않습니다."),
                rule("벽걸이형인 경우 걸레받이 질문을 하지 않습니다."),
                rule("문 설치 여부가 아니오이면 문 구성과 손잡이 관련 질문을 생략합니다."),
                rule("타공 설치 여부가 아니오이면 타공 개수/위치 질문을 생략합니다.")
        );
    }

    private List<Object> bathroomCabinetAnswerFilterRules() {
        return List.of(
                rule("색상 선택지는 시리즈+품목별 색상 가능표에 존재하는 값만 허용합니다."),
                rule("상판 선택지는 상판 목록 30개와 1/2/3단가군 매칭 자료에 존재하는 값만 허용합니다."),
                rule("손잡이 선택지는 손잡이 이름+가격 매칭 자료에 존재하는 값만 허용합니다."),
                rule("기타 옵션 위치는 상/하/좌/우 중 하나로 제한합니다."),
                rule("마구리 설치 면은 좌측/우측/후방/전면/전좌/전우/전좌우/전좌우후 중 하나로 제한합니다.")
        );
    }

    private List<Object> bathroomCabinetRequiredArtifacts() {
        return List.of(
                artifact("SIZE_CONSTRAINT_TABLE", "사이즈 제한표", "품목별 W/D/H 최소값, 최대값, 예외조건, AS 가능 여부 규칙"),
                artifact("COLOR_RULE_TABLE", "색상 가능표", "시리즈+품목별 선택 가능한 색상 목록"),
                artifact("BASE_PRICE_MATRIX", "기본 가격 매트릭스", "품목별 W-D 기준 기본가격표"),
                artifact("COUNTERTOP_PRICE_MATRIX", "상판 가격 매트릭스", "마블시리즈 상판 1/2/3단가군별 W-D 가격표"),
                artifact("COUNTERTOP_GROUP_TABLE", "상판 단가군 매칭표", "상판 30개가 1/2/3단가 중 어디에 속하는지 매칭"),
                artifact("SINK_PRICE_TABLE", "세면대 가격표", "세면대 종류별 추가금 규칙"),
                artifact("HANDLE_PRICE_TABLE", "손잡이 가격표", "손잡이 이름과 10,000~30,000원 후보 가격의 정확한 매칭"),
                artifact("OPTION_PRICE_TABLE", "옵션 가격표", "드라이걸이/휴지걸이/LED 외 품목별 기타 옵션 추가 규칙이 있을 경우"),
                artifact("DRAWING_GUIDE_FILE", "도면/위치 입력 가이드", "세면대 위치와 타공 위치를 파일 또는 텍스트로 구조화하는 기준")
        );
    }

    private Map<String, Object> artifact(String semanticRole, String title, String description) {
        return map("semanticRole", semanticRole, "title", title, "description", description, "status", "PENDING_UPLOAD");
    }

    private List<Object> bathroomCabinetExcelTables() {
        List<Object> tables = new ArrayList<>();
        for (Object artifact : bathroomCabinetRequiredArtifacts()) {
            Map<String, Object> row = new LinkedHashMap<>((Map<String, Object>) artifact);
            String role = RagJsonUtils.stringValue(row, "semanticRole");
            row.put("tableShape", role.contains("MATRIX") ? "TWO_DIMENSION_MATRIX" : "ONE_DIMENSION_OR_RELATIONAL_TABLE");
            row.put("activationPolicy", "업로드 후 역할/구조/추가·교체 여부를 확인한 뒤 active 자료로 연결");
            tables.add(row);
        }
        return tables;
    }

    private List<Object> bathroomCabinetOpenQuestions() {
        return List.of(
                "품목별 정확한 W/D/H 가능 범위와 예외조건은 사이즈 제한표 엑셀이 필요합니다.",
                "시리즈+품목별 정확한 색상 가능 목록 자료가 필요합니다.",
                "상판 가격표 조회 시 W/D를 100단위 올림 처리하는지 확인이 필요합니다.",
                "세면대 종류별 가격표 또는 추가금 규칙이 필요합니다.",
                "손잡이 이름과 가격 후보(10,000~30,000원)의 정확한 매칭이 필요합니다.",
                "마구리 설치 면별 길이 산식이 실제 현장 규칙과 일치하는지 확인이 필요합니다.",
                "걸레받이 가격 규칙이 필요합니다.",
                "타공 추가금이 2개 이상 총 20,000원인지, 2개째부터 개당 20,000원인지 확인이 필요합니다.",
                "기타 옵션을 품목별로 물어볼지 전 품목 공통으로 물어볼지 확인이 필요합니다."
        );
    }

    private List<Object> bathroomCabinetReplacementRules() {
        return List.of(
                rule("사용자가 기존 사이즈 관련 엑셀을 새 것으로 교체한다고 말하면 같은 주제의 기존 SIZE_CONSTRAINT_TABLE active 자료를 비활성화하고 새 파일을 active로 사용합니다."),
                rule("사용자가 기존 단가표를 새 것으로 교체한다고 말하면 같은 주제/역할의 기존 가격표 active 자료를 비활성화하고 새 파일을 active로 사용합니다."),
                rule("사용자가 추가 자료라고 말하면 기존 자료를 유지하고 새 자료를 추가 연결합니다."),
                rule("교체인지 추가인지 애매하면 저장하지 않고 반드시 확인 질문을 합니다.")
        );
    }

    private Map<String, Object> rule(String text) {
        return map("rule", text, "source", "BATHROOM_CABINET_TEMPLATE");
    }

    private Map<String, Object> map(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (keyValues == null) return map;
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            map.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return map;
    }

    private Map<String, Object> emptyResult(Map<String, Object> currentVersion, String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("intent", "CLARIFY");
        result.put("requiresUpload", false);
        result.put("requiresClarification", true);
        result.put("shouldPersist", false);
        result.put("answer", message);
        result.put("clarificationQuestions", List.of(message));
        result.put("summary", String.valueOf(currentVersion == null ? "" : currentVersion.getOrDefault("summary", "")));
        result.put("processJson", jsonOrEmpty(currentVersion, "process_json", "ORDER_PROCESS_BUILDER_V3"));
        result.put("pricingJson", jsonOrEmpty(currentVersion, "pricing_json", "ORDER_PRICING_ENGINE_V3"));
        result.put("constraintsJson", jsonOrEmpty(currentVersion, "constraints_json", "ORDER_CONSTRAINTS_V3"));
        result.put("validationReportJson", Map.of("status", "EMPTY_INPUT", "warnings", List.of(message), "readyToPublish", false));
        result.put("knowledgeText", "");
        result.put("confidence", 0.0);
        return result;
    }

    private List<Segment> splitToSegments(String text, int targetLength, int maxSegments) {
        List<Segment> result = new ArrayList<>();
        if (!StringUtils.hasText(text)) return result;
        String normalized = text.trim();
        // maxSegments는 예전에는 초과분을 버리는 하드 컷이었지만, 학습 원문 유실을 만들 수 있습니다.
        // 이제는 최소 안전치로만 쓰고 실제 원문 길이에 따라 충분히 늘려 전체 입력이 트리에 남게 합니다.
        int effectiveMaxSegments = Math.max(maxSegments, normalized.length() / Math.max(1, targetLength) + 100);
        String[] lines = normalized.split("\\R");
        StringBuilder current = new StringBuilder();
        String title = "segment";
        int segmentNo = 1;

        for (String rawLine : lines) {
            String line = rawLine == null ? "" : rawLine;
            String trimmed = line.trim();
            boolean boundary = isSemanticBoundary(trimmed);
            if (boundary && current.length() >= Math.max(300, targetLength / 3)) {
                addSegmentWithLengthSplit(result, title, current.toString(), targetLength, effectiveMaxSegments, segmentNo++);
                current.setLength(0);
                title = boundaryTitle(trimmed, segmentNo);
            } else if (boundary && current.length() == 0) {
                title = boundaryTitle(trimmed, segmentNo);
            }
            current.append(line).append('\n');
            if (current.length() >= targetLength && trimmed.isEmpty()) {
                addSegmentWithLengthSplit(result, title, current.toString(), targetLength, effectiveMaxSegments, segmentNo++);
                current.setLength(0);
                title = "segment-" + segmentNo;
            }
            if (result.size() >= effectiveMaxSegments) break;
        }
        if (current.length() > 0 && result.size() < effectiveMaxSegments) {
            addSegmentWithLengthSplit(result, title, current.toString(), targetLength, effectiveMaxSegments, segmentNo);
        }
        if (result.isEmpty()) result.add(new Segment("segment-1", "1", normalized));
        return result;
    }

    private void addSegmentWithLengthSplit(List<Segment> result,
                                           String title,
                                           String text,
                                           int targetLength,
                                           int maxSegments,
                                           int segmentNo) {
        if (!StringUtils.hasText(text) || result.size() >= maxSegments) return;
        String normalized = text.trim();
        if (normalized.length() <= targetLength) {
            result.add(new Segment(firstText(title, "segment-" + segmentNo), String.valueOf(result.size() + 1), normalized));
            return;
        }
        int start = 0;
        int part = 1;
        int overlap = Math.min(Math.max(80, properties.getAdaptiveChunkOverlapChars()), Math.max(80, targetLength / 8));
        while (start < normalized.length() && result.size() < maxSegments) {
            int end = Math.min(normalized.length(), start + targetLength);
            if (end < normalized.length()) {
                int newline = normalized.lastIndexOf('\n', end);
                if (newline > start + targetLength / 2) end = newline;
                else {
                    int sentence = Math.max(normalized.lastIndexOf("다.", end), normalized.lastIndexOf(".", end));
                    if (sentence > start + targetLength / 2) end = Math.min(normalized.length(), sentence + 1);
                }
            }
            String partText = normalized.substring(start, end).trim();
            if (StringUtils.hasText(partText)) {
                result.add(new Segment(firstText(title, "segment-" + segmentNo) + " / part " + part,
                        String.valueOf(result.size() + 1), partText));
            }
            if (end >= normalized.length()) break;
            start = Math.max(0, end - overlap);
            part++;
        }
    }

    private boolean isSemanticBoundary(String trimmed) {
        if (!StringUtils.hasText(trimmed)) return false;
        if (trimmed.matches("^\\d+[.)].*")) return true;
        if (trimmed.startsWith("첫째") || trimmed.startsWith("둘째") || trimmed.startsWith("셋째") || trimmed.startsWith("넷째")
                || trimmed.startsWith("다섯째") || trimmed.startsWith("여섯째") || trimmed.startsWith("일곱째")
                || trimmed.startsWith("여덟째") || trimmed.startsWith("아홉째")) return true;
        return trimmed.contains("주문 프로세스")
                || trimmed.contains("조건부 질문")
                || trimmed.contains("가격계산")
                || trimmed.contains("입력값 검증")
                || trimmed.contains("자료 교체")
                || trimmed.contains("필요한 엑셀")
                || trimmed.contains("아직 부족")
                || trimmed.contains("챗봇 주문 질문")
                || trimmed.startsWith("그 다음");
    }

    private String boundaryTitle(String trimmed, int segmentNo) {
        if (!StringUtils.hasText(trimmed)) return "segment-" + segmentNo;
        String cleaned = trimmed.replaceAll("\\s+", " ").trim();
        return RagJsonUtils.truncate(cleaned, 80);
    }

    private HeuristicExtraction heuristicExtract(String text) {
        HeuristicExtraction h = new HeuristicExtraction();
        String[] lines = text == null ? new String[0] : text.split("\\R");
        for (String line : lines) {
            String t = line == null ? "" : line.trim();
            if (!StringUtils.hasText(t)) continue;
            if (containsAny(t, "선택", "입력받", "물어본", "질문", "답변 형태")) h.questionRules.add(t);
            if (containsAny(t, "경우에만", "에서만", "아닌 경우", "조건부", "노출", "하지 않는다")) h.conditionalQuestions.add(t);
            if (containsAny(t, "최소", "최대", "가능", "초과", "미만", "검증", "불가능", "무상 AS")) h.validationRules.add(t);
            if (containsAny(t, "가격", "단가", "추가금", "무료", "계산", "원이다", "원", "올림")) h.pricingRules.add(t);
            if (containsAny(t, "첫째", "둘째", "셋째", "넷째", "다섯째", "여섯째", "일곱째", "여덟째", "아홉째")) h.pricingOrder.add(t);
            if (containsAny(t, "엑셀", "파일", "가격표", "제한표", "가능표", "매트릭스", "자료")) h.requiredArtifacts.add(t);
            if (containsAny(t, "교체", "비활성화", "활성 자료", "추가 자료")) h.replacementRules.add(t);
            if (containsAny(t, "불명확", "애매", "부족", "물어봐", "확인", "다를 수")) h.openQuestions.add(t);
            if (containsAny(t, "시리즈", "품목", "형태", "사이즈", "색상", "상판", "세면대", "문", "손잡이", "마구리", "걸레받이", "타공", "기타 옵션")) h.orderSteps.add(t);
            h.facts.add(t);
        }
        h.trimDistinct();
        return h;
    }

    private String buildDeterministicKnowledgeText(Segment segment, HeuristicExtraction h) {
        StringBuilder sb = new StringBuilder();
        sb.append("[원문 보존 leaf]\n");
        sb.append("제목: ").append(segment.title()).append("\n");
        sb.append("경로: ").append(segment.path()).append("\n\n");
        sb.append("휴리스틱 추출 요약\n");
        appendList(sb, "주문/질문 후보", h.questionRules);
        appendList(sb, "조건부 후보", h.conditionalQuestions);
        appendList(sb, "검증 후보", h.validationRules);
        appendList(sb, "가격 후보", h.pricingRules);
        appendList(sb, "필요 자료 후보", h.requiredArtifacts);
        appendList(sb, "확인 필요 후보", h.openQuestions);
        sb.append("\n원문:\n").append(RagJsonUtils.truncate(segment.text(), 2500));
        return sb.toString();
    }

    private List<Map<String, Object>> compactPackets(List<Map<String, Object>> packets, int textLimit) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (packets == null) return result;
        for (Map<String, Object> packet : packets) {
            Map<String, Object> compact = new LinkedHashMap<>();
            compact.put("packetType", packet.get("packetType"));
            compact.put("leafNo", packet.get("leafNo"));
            compact.put("level", packet.get("level"));
            compact.put("groupNo", packet.get("groupNo"));
            compact.put("path", packet.get("path"));
            compact.put("title", packet.get("title"));
            compact.put("sourceStatus", packet.get("sourceStatus"));
            for (String key : packetListKeys()) compact.put(key, packet.getOrDefault(key, List.of()));
            compact.put("knowledgeText", RagJsonUtils.truncate(RagJsonUtils.stringValue(packet, "knowledgeText"), textLimit));
            String error = RagJsonUtils.stringValue(packet, "interpretationError");
            if (StringUtils.hasText(error)) compact.put("interpretationError", error);
            result.add(compact);
        }
        return result;
    }

    private void normalizePacketLists(Map<String, Object> packet) {
        for (String key : packetListKeys()) {
            Object value = packet.get(key);
            if (!(value instanceof List<?>)) packet.put(key, List.of());
        }
        if (!StringUtils.hasText(RagJsonUtils.stringValue(packet, "knowledgeText"))) {
            packet.put("knowledgeText", RagJsonUtils.truncate(RagJsonUtils.stringValue(packet, "rawTextPreview"), 1200));
        }
    }

    private List<String> packetListKeys() {
        return List.of("facts", "orderSteps", "questionRules", "conditionalQuestions", "answerTypes", "validationRules",
                "pricingRules", "pricingOrder", "requiredArtifacts", "replacementRules", "openQuestions",
                "conflictCandidates", "nodeProposals", "evidence");
    }

    private List<Object> flattenDistinct(List<Map<String, Object>> packets, String key) {
        return distinctObjects(concatLists(packets, key));
    }

    private List<Object> concatLists(List<Map<String, Object>> maps, String key) {
        List<Object> result = new ArrayList<>();
        if (maps == null) return result;
        for (Map<String, Object> map : maps) result.addAll(RagJsonUtils.childList(map, key));
        return result;
    }

    private List<Object> distinctObjects(List<?> values) {
        List<Object> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        if (values == null) return result;
        for (Object value : values) {
            if (value == null) continue;
            String key = stableKey(value);
            if (seen.add(key)) result.add(value);
        }
        return result;
    }

    private String stableKey(Object value) {
        if (value == null) return "";
        if (value instanceof String s) return s.trim();
        return RagJsonUtils.pretty(objectMapper, value).trim();
    }

    private List<Object> collectWarnings(List<Map<String, Object>> packets) {
        List<Object> warnings = new ArrayList<>();
        if (packets == null) return warnings;
        for (Map<String, Object> p : packets) {
            String interpretationError = RagJsonUtils.stringValue(p, "interpretationError");
            String mergeError = RagJsonUtils.stringValue(p, "mergeError");
            if (StringUtils.hasText(interpretationError)) warnings.add("leaf 해석 실패 후 원문 보존: " + interpretationError);
            if (StringUtils.hasText(mergeError)) warnings.add("중간 병합 실패 후 서버 병합: " + mergeError);
            String status = RagJsonUtils.stringValue(p, "sourceStatus");
            if (StringUtils.hasText(status) && (status.contains("FAILURE") || status.contains("NEEDS_AI_RETRY"))) warnings.add("일부 노드가 재해석 대기 상태입니다: " + status);
        }
        return distinctObjects(warnings);
    }

    private List<Object> normalizeSteps(List<Object> orderSteps) {
        List<Object> steps = new ArrayList<>();
        int i = 1;
        for (Object step : orderSteps) {
            if (step instanceof Map<?, ?>) {
                steps.add(step);
            } else {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("stepNo", i);
                row.put("title", String.valueOf(step));
                row.put("source", "adaptiveTree");
                steps.add(row);
            }
            i++;
        }
        return steps;
    }

    private List<Object> inferExcelTables(List<Object> requiredArtifacts) {
        List<Object> tables = new ArrayList<>();
        for (Object artifact : requiredArtifacts) {
            String text = String.valueOf(artifact);
            if (!containsAny(text, "엑셀", "가격표", "제한표", "가능표", "매트릭스")) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("status", "PENDING_UPLOAD");
            row.put("description", text);
            if (text.contains("기본") || text.contains("품목")) row.put("semanticRole", "BASE_PRICE_MATRIX");
            else if (text.contains("사이즈")) row.put("semanticRole", "SIZE_LIMIT_TABLE");
            else if (text.contains("색상")) row.put("semanticRole", "COLOR_AVAILABILITY_TABLE");
            else if (text.contains("상판")) row.put("semanticRole", "TOP_PLATE_PRICE_MATRIX");
            else if (text.contains("세면대")) row.put("semanticRole", "BASIN_PRICE_TABLE");
            else if (text.contains("손잡이")) row.put("semanticRole", "HANDLE_PRICE_TABLE");
            else row.put("semanticRole", "UNKNOWN_EXCEL_TABLE");
            tables.add(row);
        }
        return distinctObjects(tables);
    }

    private List<Object> inferSkipRules(List<Object> conditionalQuestions) {
        List<Object> rules = new ArrayList<>();
        for (Object q : conditionalQuestions) {
            String text = String.valueOf(q);
            if (containsAny(text, "하지 않는다", "노출", "나타나지", "경우에만", "에서만")) rules.add(text);
        }
        return distinctObjects(rules);
    }

    private List<Object> inferAsPolicyRules(List<Object> validationRules) {
        List<Object> rules = new ArrayList<>();
        for (Object rule : validationRules) {
            String text = String.valueOf(rule);
            if (containsAny(text, "AS", "무상", "유상")) rules.add(text);
        }
        return distinctObjects(rules);
    }

    private List<Object> buildChangePlan(List<Object> replacementRules, String userMessage) {
        List<Object> plan = new ArrayList<>();
        if (containsAny(userMessage, "교체", "새 것으로", "비활성화")) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("operation", "REPLACE_OR_UPDATE_REQUEST_DETECTED");
            row.put("policy", "같은 주제/역할의 기존 active 노드는 비활성화하고 새 노드를 active로 저장합니다. 범위가 애매한 파일 교체는 REVIEW_REQUIRED로 보류해야 합니다.");
            plan.add(row);
        }
        plan.addAll(replacementRules);
        return distinctObjects(plan);
    }

    private int countRetryablePackets(List<Map<String, Object>> packets) {
        int count = 0;
        if (packets == null) return 0;
        for (Map<String, Object> packet : packets) {
            String status = RagJsonUtils.stringValue(packet, "sourceStatus");
            if (StringUtils.hasText(status) && (status.contains("NEEDS_AI_RETRY") || status.contains("FAILURE"))) count++;
        }
        return count;
    }

    private Map<String, Object> buildKnowledgeTree(String userMessage,
                                                   String attachmentFilename,
                                                   List<Map<String, Object>> topPackets,
                                                   List<Object> nodeProposals,
                                                   Map<String, Object> processJson,
                                                   Map<String, Object> pricingJson,
                                                   Map<String, Object> constraintsJson,
                                                   List<Object> requiredArtifacts,
                                                   List<Object> openQuestions,
                                                   List<Object> warnings,
                                                   int seedSegmentCount,
                                                   int leafPacketCount,
                                                   int inputLength) {
        List<Map<String, Object>> nodes = new ArrayList<>();
        addNode(nodes, null, "ROOT", "root", "학습 루트", "입력 전체를 계층형으로 해석한 루트 노드입니다.",
                Map.of("inputLength", inputLength, "seedSegmentCount", seedSegmentCount, "leafPacketCount", leafPacketCount), "", 0);
        addNode(nodes, "root", "ORDER_PROCESS", "order_process", "주문 프로세스", "주문 단계와 고객 질문 흐름", processJson, "", 1);
        addNode(nodes, "root", "PRICING", "pricing", "가격계산", "가격 계산 항목과 순서", pricingJson, "", 2);
        addNode(nodes, "root", "CONSTRAINTS", "constraints", "검증/조건 규칙", "입력값 검증, 조건부 질문, AS 정책", constraintsJson, "", 3);
        addNode(nodes, "root", "REQUIRED_ARTIFACTS", "required_artifacts", "필요 자료", "추가 업로드가 필요한 엑셀/파일", Map.of("items", requiredArtifacts), "", 4);
        addNode(nodes, "root", "OPEN_QUESTIONS", "open_questions", "확인 필요 항목", "저장은 가능하지만 계산 전 확인해야 하는 질문", Map.of("items", openQuestions), "", 5);
        addNode(nodes, "root", "WARNINGS", "warnings", "학습 경고", "원문 보존 또는 GPT 일부 실패 정보", Map.of("items", warnings), "", 6);

        int order = 10;
        if (topPackets != null) {
            for (Map<String, Object> packet : topPackets) {
                String title = firstText(RagJsonUtils.stringValue(packet, "title"), "packet " + order);
                String nodeKey = "packet_" + order;
                Map<String, Object> structured = new LinkedHashMap<>(packet);
                addNode(nodes, "root", String.valueOf(packet.getOrDefault("packetType", "PACKET")), nodeKey,
                        title, RagJsonUtils.truncate(RagJsonUtils.stringValue(packet, "knowledgeText"), 800), structured,
                        RagJsonUtils.stringValue(packet, "rawTextPreview"), order++);
            }
        }
        for (Object proposal : nodeProposals) {
            Map<String, Object> structured = new LinkedHashMap<>();
            structured.put("proposal", proposal);
            String title;
            if (proposal instanceof Map<?, ?> m) {
                Object titleValue = m.get("title");
                title = titleValue == null ? "node proposal" : String.valueOf(titleValue);
            } else {
                title = String.valueOf(proposal);
            }
            addNode(nodes, "root", "NODE_PROPOSAL", "proposal_" + order, title,
                    RagJsonUtils.truncate(String.valueOf(proposal), 800), structured, "", order++);
        }

        Map<String, Object> tree = new LinkedHashMap<>();
        tree.put("schemaType", "RAG_KNOWLEDGE_TREE_V3");
        tree.put("assemblyMode", "ADAPTIVE_RECURSIVE_TREE");
        tree.put("source", Map.of(
                "attachmentFilename", attachmentFilename == null ? "" : attachmentFilename,
                "userMessagePreview", RagJsonUtils.truncate(userMessage, 2000)
        ));
        tree.put("nodes", nodes);
        return tree;
    }

    private void addNode(List<Map<String, Object>> nodes,
                         String parentKey,
                         String nodeType,
                         String nodeKey,
                         String title,
                         String summary,
                         Object structuredJson,
                         String rawText,
                         int sortOrder) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("nodeKey", nodeKey);
        node.put("parentKey", parentKey);
        node.put("nodeType", nodeType);
        node.put("title", title);
        node.put("summary", summary);
        node.put("structuredJson", structuredJson == null ? Map.of() : structuredJson);
        node.put("rawText", rawText == null ? "" : rawText);
        node.put("sortOrder", sortOrder);
        node.put("active", true);
        String statusText = "";
        if (structuredJson instanceof Map<?, ?> structured) {
            Object sourceStatus = structured.get("sourceStatus");
            if (sourceStatus != null) statusText = String.valueOf(sourceStatus);
            Object interpretationError = structured.get("interpretationError");
            if (interpretationError != null) node.put("retryReason", String.valueOf(interpretationError));
        }
        boolean retryable = statusText.contains("NEEDS_AI_RETRY") || statusText.contains("FAILURE");
        node.put("interpretationStatus", retryable ? "AI_PARSE_PENDING" : "AI_PARSED");
        node.put("retryable", retryable);
        nodes.add(node);
    }

    private String buildSummary(String userMessage,
                                List<Object> facts,
                                List<Object> orderSteps,
                                List<Object> pricingRules,
                                List<Object> requiredArtifacts,
                                List<Object> openQuestions,
                                List<Object> warnings) {
        StringBuilder sb = new StringBuilder();
        sb.append("계층형 학습으로 주문 프로세스/질문/검증/가격계산 규칙을 구조화했습니다.");
        sb.append(" 주문 단계 후보 ").append(orderSteps.size()).append("개, 가격 규칙 후보 ").append(pricingRules.size()).append("개, 필요 자료 ").append(requiredArtifacts.size()).append("개, 확인 필요 항목 ").append(openQuestions.size()).append("개를 추출했습니다.");
        if (!warnings.isEmpty()) sb.append(" 일부 leaf는 GPT 실패 후 재해석 대기 노드로 저장했습니다.");
        if (StringUtils.hasText(userMessage)) sb.append(" 입력 주제: ").append(RagJsonUtils.truncate(userMessage.replaceAll("\\s+", " "), 180));
        return sb.toString();
    }

    private String buildAnswer(Map<String, Object> processJson,
                               Map<String, Object> pricingJson,
                               Map<String, Object> constraintsJson,
                               List<Object> requiredArtifacts,
                               List<Object> openQuestions,
                               Map<String, Object> tree,
                               List<Object> warnings) {
        StringBuilder sb = new StringBuilder();
        sb.append("계층형 트리 학습을 완료했습니다. 최종 대형 GPT 병합에 의존하지 않고 leaf 해석 결과를 서버가 조립해 저장 가능한 구조로 만들었습니다. 실패한 leaf는 더 작게 재분할하고, 끝까지 실패한 조각은 재해석 대기 노드로 보존했습니다.\n\n");
        sb.append("1. 현재까지 확정된 주문 프로세스\n");
        appendObjectList(sb, RagJsonUtils.childList(processJson, "steps"), 12);
        sb.append("\n2. 조건부 질문 목록\n");
        appendObjectList(sb, RagJsonUtils.childList(processJson, "conditionalQuestions"), 12);
        sb.append("\n3. 가격계산 항목과 계산 순서\n");
        appendObjectList(sb, RagJsonUtils.childList(pricingJson, "calculationOrder"), 12);
        if (RagJsonUtils.childList(pricingJson, "calculationOrder").isEmpty()) appendObjectList(sb, RagJsonUtils.childList(pricingJson, "calculationRules"), 12);
        sb.append("\n4. 필요한 엑셀/파일 목록\n");
        appendObjectList(sb, requiredArtifacts, 12);
        sb.append("\n5. 아직 부족하거나 확인이 필요한 질문 목록\n");
        appendObjectList(sb, openQuestions, 20);
        sb.append("\n6. 저장 가능한 구조화 JSON 초안\n");
        sb.append("- processJson.steps: ").append(RagJsonUtils.childList(processJson, "steps").size()).append("개\n");
        sb.append("- pricingJson.calculationRules: ").append(RagJsonUtils.childList(pricingJson, "calculationRules").size()).append("개\n");
        sb.append("- constraintsJson.rules: ").append(RagJsonUtils.childList(constraintsJson, "rules").size()).append("개\n");
        sb.append("- knowledgeTreeJson.nodes: ").append(RagJsonUtils.childList(tree, "nodes").size()).append("개\n");
        if (!warnings.isEmpty()) {
            sb.append("\n[주의] 일부 조각은 GPT timeout/오류 후 재해석 대기 노드로 저장되었습니다. 자동/수동 재해석 작업으로 해당 노드만 더 작게 해석해 AI_PARSED 노드로 승격할 수 있습니다.\n");
        }
        return sb.toString();
    }

    private String buildKnowledgeText(String summary,
                                      Map<String, Object> processJson,
                                      Map<String, Object> pricingJson,
                                      Map<String, Object> constraintsJson,
                                      Map<String, Object> tree,
                                      String deterministicExpansionHint) {
        StringBuilder sb = new StringBuilder();
        sb.append("[계층형 트리 학습 지식]\n");
        sb.append(summary).append("\n\n");
        sb.append("주문 프로세스 JSON:\n").append(RagJsonUtils.pretty(objectMapper, processJson)).append("\n\n");
        sb.append("가격계산 JSON:\n").append(RagJsonUtils.pretty(objectMapper, pricingJson)).append("\n\n");
        sb.append("검증/조건 JSON:\n").append(RagJsonUtils.pretty(objectMapper, constraintsJson)).append("\n\n");
        sb.append("지식 트리 JSON:\n").append(RagJsonUtils.pretty(objectMapper, tree));
        if (StringUtils.hasText(deterministicExpansionHint)) {
            sb.append("\n\n[규칙 확장 힌트]\n").append(deterministicExpansionHint);
        }
        return sb.toString();
    }

    private String joinKnowledge(List<Map<String, Object>> maps, int maxLength) {
        StringBuilder sb = new StringBuilder();
        if (maps != null) {
            for (Map<String, Object> map : maps) {
                String text = RagJsonUtils.stringValue(map, "knowledgeText");
                if (StringUtils.hasText(text)) {
                    if (sb.length() > 0) sb.append("\n\n");
                    sb.append(text.trim());
                }
            }
        }
        return RagJsonUtils.truncate(sb.toString(), maxLength);
    }

    private String buildInput(String userMessage, String attachmentFilename, String attachmentText) {
        StringBuilder sb = new StringBuilder();
        if (StringUtils.hasText(userMessage)) sb.append(userMessage.trim());
        if (StringUtils.hasText(attachmentText)) {
            sb.append("\n\n[첨부파일: ").append(StringUtils.hasText(attachmentFilename) ? attachmentFilename : "이름 없음").append("]\n");
            sb.append(attachmentText.trim());
        }
        return sb.toString();
    }

    private Map<String, Object> jsonOrEmpty(Map<String, Object> map, String key, String schemaType) {
        Map<String, Object> value = RagJsonUtils.toMap(objectMapper, map == null ? null : map.get(key));
        if (value.isEmpty()) value.put("schemaType", schemaType);
        if (schemaType.contains("PROCESS")) value.putIfAbsent("steps", new ArrayList<>());
        if (schemaType.contains("PRICING")) {
            value.putIfAbsent("calculationRules", new ArrayList<>());
            value.putIfAbsent("excelTables", new ArrayList<>());
            value.putIfAbsent("requiredArtifacts", new ArrayList<>());
        }
        if (schemaType.contains("CONSTRAINTS")) {
            value.putIfAbsent("rules", new ArrayList<>());
            value.putIfAbsent("skipRules", new ArrayList<>());
            value.putIfAbsent("answerFilterRules", new ArrayList<>());
            value.putIfAbsent("asPolicyRules", new ArrayList<>());
        }
        return value;
    }

    private void appendList(StringBuilder sb, String title, List<Object> values) {
        if (values == null || values.isEmpty()) return;
        sb.append("- ").append(title).append(":\n");
        for (Object value : values) sb.append("  * ").append(value).append('\n');
    }

    private void appendObjectList(StringBuilder sb, List<Object> values, int limit) {
        if (values == null || values.isEmpty()) {
            sb.append("- 아직 추출된 항목이 없습니다.\n");
            return;
        }
        int count = 0;
        for (Object value : values) {
            if (count >= limit) {
                sb.append("- ... ").append(values.size() - limit).append("개 추가 항목 생략\n");
                break;
            }
            sb.append("- ").append(renderOneLine(value)).append('\n');
            count++;
        }
    }

    private String renderOneLine(Object value) {
        if (value == null) return "";
        if (value instanceof Map<?, ?> map) {
            Object title = firstNonNull(map.get("title"), map.get("question"), map.get("rule"), map.get("description"), map.get("summary"));
            if (title != null) return RagJsonUtils.truncate(String.valueOf(title), 300);
        }
        return RagJsonUtils.truncate(String.valueOf(value).replaceAll("\\s+", " "), 300);
    }

    private Object firstNonNull(Object... values) {
        if (values == null) return null;
        for (Object value : values) if (value != null && StringUtils.hasText(String.valueOf(value))) return value;
        return null;
    }

    private boolean containsAny(String text, String... needles) {
        if (text == null) return false;
        for (String needle : needles) {
            if (needle != null && text.contains(needle)) return true;
        }
        return false;
    }

    private String firstText(String... values) {
        if (values == null) return "";
        for (String v : values) if (StringUtils.hasText(v)) return v.trim();
        return "";
    }

    private void notify(RagLearningProgressListener listener, String status, int progress, String message) {
        if (listener == null) return;
        try {
            listener.update(status, progress, message);
        } catch (Exception ignored) {
            // 진행상태 저장 실패가 실제 해석을 방해하면 안 됩니다.
        }
    }

    private int safeLength(String text) {
        return text == null ? 0 : text.length();
    }

    private record Segment(String title, String path, String text) {
        Segment withPath(String newPath) {
            return new Segment(title, newPath, text);
        }
    }

    private static class HeuristicExtraction {
        private List<Object> facts = new ArrayList<>();
        private List<Object> orderSteps = new ArrayList<>();
        private List<Object> questionRules = new ArrayList<>();
        private List<Object> conditionalQuestions = new ArrayList<>();
        private List<Object> answerTypes = new ArrayList<>();
        private List<Object> validationRules = new ArrayList<>();
        private List<Object> pricingRules = new ArrayList<>();
        private List<Object> pricingOrder = new ArrayList<>();
        private List<Object> requiredArtifacts = new ArrayList<>();
        private List<Object> replacementRules = new ArrayList<>();
        private List<Object> openQuestions = new ArrayList<>();

        private void trimDistinct() {
            facts = distinctStrings(facts);
            orderSteps = distinctStrings(orderSteps);
            questionRules = distinctStrings(questionRules);
            conditionalQuestions = distinctStrings(conditionalQuestions);
            answerTypes = distinctStrings(answerTypes);
            validationRules = distinctStrings(validationRules);
            pricingRules = distinctStrings(pricingRules);
            pricingOrder = distinctStrings(pricingOrder);
            requiredArtifacts = distinctStrings(requiredArtifacts);
            replacementRules = distinctStrings(replacementRules);
            openQuestions = distinctStrings(openQuestions);
        }

        private static List<Object> distinctStrings(List<Object> values) {
            List<Object> result = new ArrayList<>();
            Set<String> seen = new LinkedHashSet<>();
            for (Object value : values) {
                if (value == null) continue;
                String s = String.valueOf(value).trim();
                if (s.length() > 500) s = s.substring(0, 500) + "...";
                if (seen.add(s)) result.add(s);
            }
            return result;
        }
    }
}
