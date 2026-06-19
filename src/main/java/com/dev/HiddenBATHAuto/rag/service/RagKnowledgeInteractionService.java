package com.dev.HiddenBATHAuto.rag.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.dev.HiddenBATHAuto.rag.repository.RagRepository;
import com.dev.HiddenBATHAuto.rag.util.RagJsonUtils;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class RagKnowledgeInteractionService {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final RagRepository repository;
    private final RagFileStorageService fileStorageService;
    private final RagSemanticPlannerService plannerService;
    private final RagKnowledgeRetrievalService retrievalService;
    private final RagKnowledgeAnswerService answerService;
    private final RagStructuredOverrideRuleService overrideRuleService;
    private final RagStructuredPricingRuleService pricingRuleService;
    private final RagDialogRuleService dialogRuleService;
    private final RagSemanticOrchestratorService semanticOrchestratorService;

    public RagKnowledgeInteractionService(@Qualifier("ragJdbcTemplate") NamedParameterJdbcTemplate jdbc,
                                          ObjectMapper objectMapper,
                                          RagRepository repository,
                                          RagFileStorageService fileStorageService,
                                          RagSemanticPlannerService plannerService,
                                          RagKnowledgeRetrievalService retrievalService,
                                          RagKnowledgeAnswerService answerService,
                                          RagStructuredOverrideRuleService overrideRuleService,
                                          RagStructuredPricingRuleService pricingRuleService,
                                          RagDialogRuleService dialogRuleService,
                                          RagSemanticOrchestratorService semanticOrchestratorService) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.repository = repository;
        this.fileStorageService = fileStorageService;
        this.plannerService = plannerService;
        this.retrievalService = retrievalService;
        this.answerService = answerService;
        this.overrideRuleService = overrideRuleService;
        this.pricingRuleService = pricingRuleService;
        this.dialogRuleService = dialogRuleService;
        this.semanticOrchestratorService = semanticOrchestratorService;
    }

    public Map<String, Object> routeLearningInput(UUID sessionId, String message, boolean forceSave, List<MultipartFile> files) {
        RagScope scope = findLearningScope(sessionId);
        return route(scope, sessionId, "LEARNING", clean(message), forceSave, safeFiles(files));
    }

    public Map<String, Object> routeChatInput(UUID sessionId, String message, List<MultipartFile> files) {
        RagScope scope = findChatScope(sessionId);
        return route(scope, sessionId, "CHAT", clean(message), false, safeFiles(files));
    }

    public Map<String, Object> summarizeKnowledge(UUID projectId, UUID versionId, String query, UUID sessionId, String sourceScope) {
        String clean = clean(query);
        List<Map<String, Object>> candidates = retrievalService.findEntityCandidates(projectId, versionId, clean);
        Map<String, Object> plan = plannerService.plan(projectId, versionId, nvl(sourceScope, "API"), clean, false, false, false, candidates,
                overrideRuleService.findActiveRules(projectId, versionId, null));
        String plannedIntent = str(plan.get("intentType"));
        if (!"ASK_PRODUCT_AVAILABILITY".equals(plannedIntent) && !"ASK_PRODUCT_PRICE".equals(plannedIntent)) {
            plan.put("intentType", "ASK_KNOWLEDGE_SUMMARY");
        }
        Map<String, Object> retrieved = retrievalService.retrieve(projectId, versionId, plan, clean);
        Map<String, Object> answer = answerService.compose(clean, plan, retrieved);

        Map<String, Object> response = baseResponse(str(plan.get("intentType")), true, "HANDLED", decimal(plan.get("confidence"), new BigDecimal("0.9200")));
        response.put("answer", answer.get("answer"));
        response.put("semanticPlan", plan);
        response.put("retrieved", retrieved);
        response.put("answerMeta", answer);
        response.put("saveStatus", "지식 저장: 조회 응답");
        response.put("saveMessage", "학습 지시가 아니라 저장된 지식 조회로 판정되어 새 지식 저장 없이 답변했습니다.");
        response.put("memory", Map.of("status", "NO_KNOWLEDGE_CHANGE", "saveLabel", "지식 저장: 조회 응답", "message", response.get("saveMessage")));
        saveInteractionEvent(projectId, versionId, sessionId, nvl(sourceScope, "API"), clean, 0, response, plan, retrieved, "저장된 지식 조회");
        return response;
    }

    private Map<String, Object> route(RagScope scope,
                                      UUID sessionId,
                                      String sourceScope,
                                      String message,
                                      boolean forceSave,
                                      List<MultipartFile> files) {
        boolean hasFiles = files != null && !files.isEmpty();
        boolean hasImages = files != null && files.stream().anyMatch(this::isImage);
        boolean hasExcels = files != null && files.stream().anyMatch(this::isExcelLike);

        List<Map<String, Object>> candidates = retrievalService.findEntityCandidates(scope.projectId(), scope.versionId(), message);
        String candidateEntity = candidates.isEmpty() ? null : str(candidates.get(0).get("name"));
        List<Map<String, Object>> rules = new ArrayList<>();
        rules.addAll(overrideRuleService.findActiveRules(scope.projectId(), scope.versionId(), candidateEntity));
        rules.addAll(dialogRuleService.findActiveRules(scope.projectId(), scope.versionId(), candidateEntity));
        RagSemanticOrchestratorService.SemanticResolution semanticResolution = semanticOrchestratorService.resolve(
                scope.projectId(), scope.versionId(), sessionId, sourceScope, message, hasFiles, hasImages, hasExcels, candidates, rules
        );
        Map<String, Object> plan = semanticResolution.plan();
        String intent = str(plan.get("intentType"));
        String actionType = str(childMap(plan, "executionPlan").get("actionType"));

        if (shouldDirectReply(intent, actionType, plan)) {
            Map<String, Object> response = directReplyResponse(intent, actionType, plan);
            response.put("semanticPlan", plan);
            response.put("semanticResolution", semanticResolution.meta());
            response.put("saveStatus", "지식 저장: 저장 안 함");
            response.put("saveMessage", "제품/주문/가격/학습 지식으로 저장할 내용이 아니므로 DB 조회와 지식 저장을 하지 않았습니다.");
            response.put("memory", Map.of("status", "NO_KNOWLEDGE_CHANGE", "saveLabel", response.get("saveStatus"), "message", response.get("saveMessage")));
            saveInteractionEvent(scope.projectId(), scope.versionId(), sessionId, sourceScope, message, files.size(), response, plan, Map.of(), "직접 답변/무관 입력 안내");
            return response;
        }

        if (shouldSaveDialogRules(sourceScope, intent, plan)) {
            String topic = firstNonBlank(childText(plan, "primaryEntity", "name"), "dialog-rules");
            List<Map<String, Object>> savedRules = dialogRuleService.saveRulesFromPlan(scope.projectId(), scope.versionId(), topic, plan, message);
            persistPlannerKnowledgeNode(scope, message, plan, Map.of("dialogRules", savedRules));

            Map<String, Object> response = baseResponse("LEARN_DIALOG_RULES", true, "HANDLED", decimal(plan.get("confidence"), new BigDecimal("0.9000")));
            response.put("answer", savedRules.isEmpty()
                    ? "대화형 주문 규칙으로 해석했지만 저장 가능한 질문흐름/조건/검증/가격식이 충분히 분해되지 않았습니다. 어떤 조건에서 어떤 질문이 나와야 하는지 한 문장씩 더 알려주세요."
                    : "대화형 주문 규칙을 구조화 저장했습니다. 이후 챗봇 주문 흐름, 조건부 질문, 입력 검증, 가격 계산 판단에 이 규칙을 우선 사용합니다.");
            response.put("semanticPlan", plan);
            response.put("semanticResolution", semanticResolution.meta());
            response.put("savedDialogRules", savedRules);
            response.put("saveStatus", savedRules.isEmpty() ? "지식 저장: 대화 규칙 보류" : "지식 저장: 대화 규칙 저장됨");
            response.put("saveMessage", savedRules.isEmpty()
                    ? "dialogRules가 비어 있어 저장하지 않았습니다."
                    : "rag_dialog_rule에 질문흐름/조건/검증/가격식 규칙을 저장했습니다.");
            response.put("memory", Map.of("status", savedRules.isEmpty() ? "WAITING_USER" : "SAVED", "saveLabel", response.get("saveStatus"), "message", response.get("saveMessage")));
            saveInteractionEvent(scope.projectId(), scope.versionId(), sessionId, sourceScope, message, files.size(), response, plan, Map.of("dialogRules", savedRules), "대화형 주문 규칙 저장");
            return response;
        }

        if ("RESET_KNOWLEDGE".equals(intent)) {
            Map<String, Object> response = baseResponse("RESET_KNOWLEDGE", true, "NEEDS_EXPLICIT_RESET_API", decimal(plan.get("confidence"), new BigDecimal("0.9000")));
            response.put("requiresClarification", true);
            response.put("answer", "초기화/삭제 요청으로 해석될 수 있어 자동 실행하지 않았습니다. 데이터 삭제는 되돌리기 어려우므로 화면의 초기화 영역에서 범위와 사유를 확인한 뒤 실행해 주세요.");
            response.put("semanticPlan", plan);
            response.put("saveStatus", "지식 저장: 보류");
            response.put("saveMessage", "초기화/삭제 요청은 명시적인 reset API에서만 처리합니다.");
            saveInteractionEvent(scope.projectId(), scope.versionId(), sessionId, sourceScope, message, files.size(), response, plan, Map.of(), "초기화 안전장치");
            return response;
        }

        if ("LINK_ASSET_TO_ENTITY".equals(intent)) {
            Map<String, Object> response = linkUploadedAssets(scope, sessionId, sourceScope, message, files, plan);
            saveInteractionEvent(scope.projectId(), scope.versionId(), sessionId, sourceScope, message, files.size(), response, plan, Map.of(), "파일/이미지와 지식 엔티티 연결");
            return response;
        }

        if ("UPDATE_PRODUCT_AVAILABILITY_RULE".equals(intent)) {
            Map<String, Object> savedRule = overrideRuleService.saveRuleFromPlan(scope.projectId(), scope.versionId(), plan, message);
            persistPlannerKnowledgeNode(scope, message, plan, savedRule);
            Map<String, Object> retrieved = retrievalService.retrieve(scope.projectId(), scope.versionId(), plan, message);
            Map<String, Object> answer = answerService.compose(message, plan, retrieved);

            Map<String, Object> response = baseResponse("UPDATE_PRODUCT_AVAILABILITY_RULE", true, "HANDLED", decimal(plan.get("confidence"), new BigDecimal("0.9000")));
            response.put("answer", StringUtils.hasText(str(answer.get("answer")))
                    ? "수정 지식을 구조화 규칙으로 반영했습니다.\n\n" + answer.get("answer")
                    : "수정 지식을 구조화 규칙으로 반영했습니다.");
            response.put("semanticPlan", plan);
            response.put("savedRule", savedRule);
            response.put("retrieved", retrieved);
            response.put("answerMeta", answer);
            response.put("saveStatus", "지식 저장: 구조화 수정 규칙 저장됨");
            response.put("saveMessage", "이 입력은 단순 벡터 노드가 아니라 rag_structured_override_rule에 저장되어 조회/주문 검증 시 원본 엑셀보다 우선 적용됩니다.");
            response.put("memory", Map.of("status", "SAVED", "saveLabel", response.get("saveStatus"), "message", response.get("saveMessage")));
            saveInteractionEvent(scope.projectId(), scope.versionId(), sessionId, sourceScope, message, files.size(), response, plan, retrieved, "구조화 override rule 저장");
            return response;
        }

        if ("UPDATE_PRODUCT_PRICING_RULE".equals(intent)) {
            List<Map<String, Object>> savedRules = pricingRuleService.saveRulesFromPlan(scope.projectId(), scope.versionId(), plan, message);
            persistPlannerKnowledgeNode(scope, message, plan, Map.of("pricingRules", savedRules));
            Map<String, Object> response = baseResponse("UPDATE_PRODUCT_PRICING_RULE", true, "HANDLED", decimal(plan.get("confidence"), new BigDecimal("0.9200")));
            response.put("answer", savedRules.isEmpty()
                    ? "가격 규칙으로 해석했지만 저장 가능한 제품/옵션/금액 정보를 충분히 추출하지 못했습니다. 제품명, 옵션값, 기준금액, 기준넓이, 추가금 단위를 확인해 주세요."
                    : "가격 규칙을 구조화 저장했습니다. 이후 가격 질문/견적 계산 시 이 규칙을 사용합니다.");
            response.put("semanticPlan", plan);
            response.put("savedPricingRules", savedRules);
            response.put("saveStatus", savedRules.isEmpty() ? "지식 저장: 가격 규칙 보류" : "지식 저장: 가격 규칙 저장됨");
            response.put("saveMessage", savedRules.isEmpty()
                    ? "가격 규칙 저장에 필요한 값이 부족해 일반 학습으로 넘기지 않고 보류했습니다."
                    : "rag_structured_pricing_rule에 저장했습니다.");
            response.put("memory", Map.of("status", savedRules.isEmpty() ? "WAITING_USER" : "SAVED", "saveLabel", response.get("saveStatus"), "message", response.get("saveMessage")));
            saveInteractionEvent(scope.projectId(), scope.versionId(), sessionId, sourceScope, message, files.size(), response, plan, Map.of("pricingRules", savedRules), "구조화 pricing rule 저장");
            return response;
        }

        if ("ASK_PRODUCT_PRICE".equals(intent)) {
            Map<String, Object> priceResult = pricingRuleService.calculatePrice(scope.projectId(), scope.versionId(), plan, message);

            Map<String, Object> response = baseResponse("ASK_PRODUCT_PRICE", true, "HANDLED", decimal(plan.get("confidence"), new BigDecimal("0.9000")));
            response.put("semanticPlan", plan);
            response.put("semanticResolution", semanticResolution.meta());
            response.put("priceResult", priceResult);
            if (Boolean.TRUE.equals(priceResult.get("calculated"))) {
                response.put("answer", "계산 결과 " + priceResult.getOrDefault("entityKey", "") + " / "
                        + priceResult.getOrDefault("optionValue", "") + " / 넓이 "
                        + priceResult.getOrDefault("width", "") + " 기준 금액은 "
                        + priceResult.getOrDefault("finalPrice", "") + "원입니다.\n"
                        + priceResult.getOrDefault("formulaText", ""));
            } else {
                response.put("answer", "가격 계산을 완료하지 못했습니다. "
                        + priceResult.getOrDefault("reason", "제품/옵션/치수/가격 규칙 중 필요한 값이 부족합니다."));
            }
            response.put("saveStatus", "지식 저장: 조회 응답");
            response.put("saveMessage", "가격 질문으로 판정되어 새 지식 저장 없이 저장된 가격 규칙으로 계산했습니다.");
            response.put("memory", Map.of("status", "NO_KNOWLEDGE_CHANGE", "saveLabel", response.get("saveStatus"), "message", response.get("saveMessage")));
            saveInteractionEvent(scope.projectId(), scope.versionId(), sessionId, sourceScope, message, files.size(), response, plan, priceResult, "가격 계산 조회 응답");
            return response;
        }

        if ("ASK_PRODUCT_AVAILABILITY".equals(intent) || "ASK_KNOWLEDGE_SUMMARY".equals(intent)) {
            Map<String, Object> retrieved = new LinkedHashMap<>(semanticResolution.retrieved() == null || semanticResolution.retrieved().isEmpty()
                    ? retrievalService.retrieve(scope.projectId(), scope.versionId(), plan, message)
                    : semanticResolution.retrieved());
            if (!rules.isEmpty()) retrieved.put("activeSemanticRules", rules);
            Map<String, Object> answer = answerService.compose(message, plan, retrieved);
            Map<String, Object> response = baseResponse(intent, true, "HANDLED", decimal(plan.get("confidence"), new BigDecimal("0.8800")));
            response.put("answer", answer.get("answer"));
            response.put("semanticPlan", plan);
            response.put("semanticResolution", semanticResolution.meta());
            response.put("retrieved", retrieved);
            response.put("answerMeta", answer);
            response.put("saveStatus", "지식 저장: 조회 응답");
            response.put("saveMessage", "학습 지시가 아니라 저장된 지식 조회로 판정되어 새 지식 저장 없이 답변했습니다.");
            response.put("memory", Map.of("status", "NO_KNOWLEDGE_CHANGE", "saveLabel", response.get("saveStatus"), "message", response.get("saveMessage")));
            saveInteractionEvent(scope.projectId(), scope.versionId(), sessionId, sourceScope, message, files.size(), response, plan, retrieved, "semantic planner 조회 응답");
            return response;
        }

        Map<String, Object> response = baseResponse(intent, false,
                "LEARNING".equals(sourceScope) ? "PASS_TO_LEARNING" : "PASS_TO_CHAT",
                decimal(plan.get("confidence"), new BigDecimal("0.6000")));
        response.put("answer", "의미 해석은 완료했으며, 이 입력은 서버의 기존 학습/상담 실행기로 전달합니다.");
        response.put("semanticPlan", plan);
        response.put("semanticResolution", semanticResolution.meta());
        response.put("reason", plan.getOrDefault("reason", "추가 처리가 필요한 입력"));
        saveInteractionEvent(scope.projectId(), scope.versionId(), sessionId, sourceScope, message, files.size(), response, plan, Map.of(), "기존 파이프라인 전달");
        return response;
    }

    private boolean shouldDirectReply(String intent, String actionType, Map<String, Object> plan) {
        if ("GENERAL_CHAT".equals(intent) || "OUT_OF_DOMAIN".equals(intent) || "UNRELATED_INPUT".equals(intent)) return true;
        return "DIRECT_REPLY".equals(actionType) || "UNRELATED_INPUT_GUIDE".equals(actionType);
    }

    private Map<String, Object> directReplyResponse(String intent, String actionType, Map<String, Object> plan) {
        String answer = str(plan.get("directAnswer"));
        if (!StringUtils.hasText(answer)) {
            answer = "UNRELATED_INPUT".equals(intent) || "UNRELATED_INPUT_GUIDE".equals(actionType)
                    ? unrelatedInputGuide()
                    : "제품 옵션 조회, 가격 계산, 주문 상담, 학습 입력 중 필요한 내용을 말씀해 주세요.";
        }
        Map<String, Object> response = baseResponse(intent, true, actionType, decimal(plan.get("confidence"), new BigDecimal("0.9000")));
        response.put("answer", answer);
        response.put("requiresClarification", "UNRELATED_INPUT".equals(intent) || "UNRELATED_INPUT_GUIDE".equals(actionType));
        return response;
    }

    private String unrelatedInputGuide() {
        return "학습 또는 주문 상담과 연관이 없는 말로 이해되어 처리할 수 없습니다. 다음 예시처럼 말씀해 주세요.\n"
                + "- 코지장이 가능한 색상은 뭐가 있어?\n"
                + "- 모든 제품 정보를 보여줘.\n"
                + "- 상부장 비규격 주문 흐름은 시리즈 선택 후 품목을 선택한다.\n"
                + "- 코지장 HB 색상은 500 기준 10만원이고 100 증가당 5천원 추가야.";
    }

    private boolean shouldSaveDialogRules(String sourceScope, String intent, Map<String, Object> plan) {
        String storeAs = str(childMap(plan, "storagePlan").get("storeAs"));
        boolean hasDialogRules = !listOfMaps(plan.get("dialogRules")).isEmpty();
        if ("LEARN_DIALOG_RULES".equals(intent)) return true;
        if ("LEARNING".equals(sourceScope) && hasDialogRules) return true;
        return "DIALOG_RULE".equals(storeAs) || "QUESTION_FLOW".equals(storeAs) || "PRICING_FORMULA".equals(storeAs);
    }

    private List<Map<String, Object>> listOfMaps(Object value) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> m) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (Map.Entry<?, ?> e : m.entrySet()) {
                        if (e.getKey() != null) row.put(String.valueOf(e.getKey()), e.getValue());
                    }
                    result.add(row);
                }
            }
        }
        return result;
    }

    private Map<String, Object> linkUploadedAssets(RagScope scope,
                                                   UUID sessionId,
                                                   String sourceScope,
                                                   String message,
                                                   List<MultipartFile> files,
                                                   Map<String, Object> plan) {
        if (files == null || files.isEmpty()) {
            Map<String, Object> response = baseResponse("LINK_ASSET_TO_ENTITY", true, "NEEDS_FILE", new BigDecimal("0.9000"));
            response.put("requiresClarification", true);
            response.put("answer", "이미지/파일 연결 의도로 해석됐지만 업로드 파일이 없습니다. 연결할 이미지를 함께 업로드해 주세요.");
            return response;
        }
        String entityKey = firstNonBlank(childText(plan, "primaryEntity", "name"), childText(plan, "updateRule", "entityKey"));
        if (!StringUtils.hasText(entityKey)) entityKey = "UNKNOWN_ENTITY";
        List<Map<String, Object>> savedAssets = new ArrayList<>();
        for (MultipartFile file : files) {
            Map<String, Object> asset = fileStorageService.saveAsset(scope.projectId(), scope.versionId(), "KNOWLEDGE_ENTITY", entityKey, file, message);
            updateAssetSemantic(scope.projectId(), scope.versionId(), asset, entityKey, message, plan);
            insertAssetLink(scope.projectId(), scope.versionId(), asset, entityKey, message, plan);
            savedAssets.add(asset);
        }
        persistPlannerKnowledgeNode(scope, message, plan, Map.of("assets", savedAssets));

        Map<String, Object> response = baseResponse("LINK_ASSET_TO_ENTITY", true, "HANDLED", decimal(plan.get("confidence"), new BigDecimal("0.9300")));
        response.put("answer", "업로드 파일을 '" + entityKey + "' 지식과 연결했습니다. 이후 해당 제품/지식을 조회할 때 연결 이미지/파일로 함께 표시됩니다.");
        response.put("entityKey", entityKey);
        response.put("assets", savedAssets);
        response.put("semanticPlan", plan);
        response.put("saveStatus", "지식 저장: 파일 연결됨");
        response.put("saveMessage", "파일/이미지를 지식 엔티티와 연결하고 검색 가능한 지식 노드로 보존했습니다.");
        response.put("memory", Map.of("status", "SAVED", "saveLabel", response.get("saveStatus"), "message", response.get("saveMessage")));
        return response;
    }

    private void updateAssetSemantic(UUID projectId, UUID versionId, Map<String, Object> asset, String entityKey, String message, Map<String, Object> plan) {
        Object assetId = asset.get("id");
        if (assetId == null) return;
        String sql = """
                UPDATE rag_asset
                SET asset_kind = :assetKind,
                    semantic_caption = :caption,
                    linked_entity_type = 'PRODUCT',
                    linked_entity_key = :entityKey,
                    ai_detected_entities_json = CAST(:entities AS jsonb),
                    metadata_json = CAST(:metadata AS jsonb)
                WHERE id = :assetId
                  AND project_id = :projectId
                  AND version_id = :versionId
                """;
        try {
            jdbc.update(sql, new MapSqlParameterSource()
                    .addValue("assetId", assetId)
                    .addValue("projectId", projectId)
                    .addValue("versionId", versionId)
                    .addValue("assetKind", isImageContent(asset) ? "IMAGE" : "FILE")
                    .addValue("caption", message)
                    .addValue("entityKey", entityKey)
                    .addValue("entities", toJson(List.of(Map.of("entityType", "PRODUCT", "name", entityKey))))
                    .addValue("metadata", toJson(Map.of("semanticPlan", plan))));
        } catch (DataAccessException ignored) {
            // 기존 DB 패치가 누락되어도 파일 저장 자체는 살립니다. 단, semantic link 기능은 DB 패치 후 동작합니다.
        }
    }

    private void insertAssetLink(UUID projectId, UUID versionId, Map<String, Object> asset, String entityKey, String message, Map<String, Object> plan) {
        Object assetId = asset.get("id");
        if (assetId == null) return;
        String sql = """
                INSERT INTO rag_entity_asset_link(
                    id, project_id, version_id, entity_type, entity_key, display_name,
                    asset_id, link_source_message, confidence, status, metadata_json, created_at
                ) VALUES (
                    :id, :projectId, :versionId, 'PRODUCT', :entityKey, :displayName,
                    :assetId, :message, :confidence, 'ACTIVE', CAST(:metadata AS jsonb), now()
                )
                """;
        try {
            jdbc.update(sql, new MapSqlParameterSource()
                    .addValue("id", UUID.randomUUID())
                    .addValue("projectId", projectId)
                    .addValue("versionId", versionId)
                    .addValue("entityKey", entityKey)
                    .addValue("displayName", entityKey)
                    .addValue("assetId", assetId)
                    .addValue("message", message)
                    .addValue("confidence", decimal(plan.get("confidence"), new BigDecimal("0.9000")))
                    .addValue("metadata", toJson(Map.of("semanticPlan", plan))));
        } catch (DataAccessException ignored) {
            // 동일 파일 중복 연결 등은 답변 실패로 이어지지 않게 무시합니다.
        }
    }

    private void persistPlannerKnowledgeNode(RagScope scope, String message, Map<String, Object> plan, Object result) {
        String topic = "semantic-planner";
        String title = switch (str(plan.get("intentType"))) {
            case "UPDATE_PRODUCT_AVAILABILITY_RULE" -> "구조화 수정 규칙: " + firstNonBlank(childText(plan, "updateRule", "entityKey"), childText(plan, "primaryEntity", "name"));
            case "LINK_ASSET_TO_ENTITY" -> "엔티티 파일 연결: " + childText(plan, "primaryEntity", "name");
            default -> "semantic planner 지식";
        };
        String rawText = "사용자 입력:\n" + message + "\n\nsemanticPlan:\n" + toJson(plan) + "\n\nresult:\n" + toJson(result);
        UUID docId = UUID.randomUUID();
        try {
            repository.insertDocument(docId, scope.projectId(), scope.versionId(), topic, "SEMANTIC_PLANNER", title, null, rawText,
                    Map.of("semanticPlan", plan, "result", result));
            repository.insertChunkWithoutEmbedding(UUID.randomUUID(), docId, scope.projectId(), scope.versionId(), 1, topic, rawText,
                    Map.of("semanticPlan", plan, "result", result));
            repository.publishVersion(scope.projectId(), scope.versionId());
        } catch (Exception ignored) {
            // 지식 노드 보존 실패가 structured override 저장 결과를 막으면 안 됩니다.
        }
    }

    private RagScope findLearningScope(UUID sessionId) {
        try {
            Map<String, Object> row = jdbc.queryForMap("""
                    SELECT project_id, version_id
                    FROM rag_learning_session
                    WHERE id = :sessionId
                    """, Map.of("sessionId", sessionId));
            return new RagScope((UUID) row.get("project_id"), (UUID) row.get("version_id"));
        } catch (EmptyResultDataAccessException e) {
            throw new IllegalArgumentException("학습 세션을 찾을 수 없습니다.");
        }
    }

    private RagScope findChatScope(UUID sessionId) {
        try {
            Map<String, Object> row = jdbc.queryForMap("""
                    SELECT project_id, version_id
                    FROM rag_chat_session
                    WHERE id = :sessionId
                    """, Map.of("sessionId", sessionId));
            return new RagScope((UUID) row.get("project_id"), (UUID) row.get("version_id"));
        } catch (EmptyResultDataAccessException e) {
            throw new IllegalArgumentException("채팅 세션을 찾을 수 없습니다.");
        }
    }

    private void saveInteractionEvent(UUID projectId,
                                      UUID versionId,
                                      UUID sessionId,
                                      String sourceScope,
                                      String message,
                                      int fileCount,
                                      Map<String, Object> response,
                                      Map<String, Object> plannerJson,
                                      Map<String, Object> retrievedJson,
                                      String reason) {
        String sql = """
                INSERT INTO rag_interaction_event(
                    id, project_id, version_id, session_id, source_scope, user_message, file_count,
                    intent_type, confidence, action_status, answer, reason,
                    extracted_entities_json, retrieved_json, result_json, planner_json, created_at
                ) VALUES (
                    :id, :projectId, :versionId, :sessionId, :sourceScope, :userMessage, :fileCount,
                    :intentType, :confidence, :actionStatus, :answer, :reason,
                    CAST(:entities AS jsonb), CAST(:retrieved AS jsonb), CAST(:result AS jsonb), CAST(:planner AS jsonb), now()
                )
                """;
        try {
            jdbc.update(sql, new MapSqlParameterSource()
                    .addValue("id", UUID.randomUUID())
                    .addValue("projectId", projectId)
                    .addValue("versionId", versionId)
                    .addValue("sessionId", sessionId)
                    .addValue("sourceScope", sourceScope)
                    .addValue("userMessage", message)
                    .addValue("fileCount", fileCount)
                    .addValue("intentType", response.getOrDefault("intentType", response.getOrDefault("intent", "UNKNOWN")))
                    .addValue("confidence", response.getOrDefault("confidence", new BigDecimal("0.0000")))
                    .addValue("actionStatus", response.getOrDefault("actionStatus", "ROUTED"))
                    .addValue("answer", response.get("answer"))
                    .addValue("reason", reason)
                    .addValue("entities", toJson(plannerJson.getOrDefault("primaryEntity", Map.of())))
                    .addValue("retrieved", toJson(retrievedJson))
                    .addValue("result", toJson(response))
                    .addValue("planner", toJson(plannerJson)));
        } catch (DataAccessException e) {
            // 이벤트 저장 실패가 실제 응답을 막으면 안 됩니다.
        }
    }

    private Map<String, Object> baseResponse(String intentType, boolean handled, String actionStatus, BigDecimal confidence) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("handled", handled);
        response.put("intentType", intentType);
        response.put("actionStatus", actionStatus);
        response.put("confidence", confidence);
        response.put("requiresClarification", false);
        response.put("shouldPersist", false);
        return response;
    }

    private List<MultipartFile> safeFiles(List<MultipartFile> files) {
        List<MultipartFile> result = new ArrayList<>();
        if (files == null) return result;
        for (MultipartFile file : files) {
            if (file != null && !file.isEmpty()) result.add(file);
        }
        return result;
    }

    private boolean isImage(MultipartFile file) {
        if (file == null) return false;
        String type = file.getContentType();
        String name = file.getOriginalFilename();
        if (type != null && type.toLowerCase(Locale.ROOT).startsWith("image/")) return true;
        return name != null && name.toLowerCase(Locale.ROOT).matches(".*\\.(png|jpg|jpeg|webp|gif|bmp)$");
    }

    private boolean isExcelLike(MultipartFile file) {
        if (file == null) return false;
        String name = file.getOriginalFilename();
        String type = file.getContentType();
        return (name != null && name.toLowerCase(Locale.ROOT).matches(".*\\.(xlsx|xls|csv)$"))
                || (type != null && type.toLowerCase(Locale.ROOT).contains("spreadsheet"));
    }

    private boolean isImageContent(Map<String, Object> asset) {
        String type = str(asset.get("content_type"));
        String name = str(asset.get("original_filename"));
        return type.toLowerCase(Locale.ROOT).startsWith("image/") || name.toLowerCase(Locale.ROOT).matches(".*\\.(png|jpg|jpeg|webp|gif|bmp)$");
    }

    private String clean(String message) {
        return message == null ? "" : message.trim();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) return value;
        }
        return "";
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
        return str(childMap(map, child).get(key));
    }

    private String toJson(Object value) {
        return RagJsonUtils.toJson(objectMapper, value);
    }

    private String nvl(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private String str(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private BigDecimal decimal(Object value, BigDecimal fallback) {
        if (value instanceof BigDecimal bd) return bd;
        if (value instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try {
            return value == null ? fallback : new BigDecimal(String.valueOf(value));
        } catch (Exception e) {
            return fallback;
        }
    }

    private record RagScope(UUID projectId, UUID versionId) {}
}
