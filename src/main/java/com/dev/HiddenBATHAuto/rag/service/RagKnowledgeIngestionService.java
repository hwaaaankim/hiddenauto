package com.dev.HiddenBATHAuto.rag.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.dev.HiddenBATHAuto.rag.repository.RagRepository;
import com.dev.HiddenBATHAuto.rag.util.RagJsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class RagKnowledgeIngestionService {

    private static final int PROMPT_TEXT_LIMIT = 36_000;
    private static final int CHUNK_SIZE = 1_800;
    private static final int CHUNK_OVERLAP = 180;

    private final RagRepository repository;
    private final OpenAiRagClient ai;
    private final RagFileKnowledgeParser parser;
    private final RagFileStorageService storageService;
    private final ObjectMapper objectMapper;

    public RagKnowledgeIngestionService(RagRepository repository,
                                        OpenAiRagClient ai,
                                        RagFileKnowledgeParser parser,
                                        RagFileStorageService storageService,
                                        ObjectMapper objectMapper) {
        this.repository = repository;
        this.ai = ai;
        this.parser = parser;
        this.storageService = storageService;
        this.objectMapper = objectMapper;
    }

    @Transactional(transactionManager = "ragTransactionManager")
    public Map<String, Object> ingestLearningInput(UUID sessionId,
                                                   String message,
                                                   List<MultipartFile> files,
                                                   boolean forceSave) {
        Map<String, Object> session = repository.findLearningSession(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("학습 세션을 찾을 수 없습니다."));
        UUID projectId = (UUID) session.get("project_id");
        UUID versionId = (UUID) session.get("version_id");
        String topic = resolveTopic(session);
        return ingest(projectId, versionId, sessionId, topic, message, files, forceSave, true, "LEARNING_CONVERSATION");
    }

    public Map<String, Object> analyzeChatFilesOnly(UUID projectId,
                                                    UUID versionId,
                                                    UUID sessionId,
                                                    String message,
                                                    List<MultipartFile> files) {
        return ingest(projectId, versionId, sessionId, "CHAT_ATTACHMENT", message, files, false, false, "CHAT_ATTACHMENT");
    }

    private Map<String, Object> ingest(UUID projectId,
                                       UUID versionId,
                                       UUID ownerSessionId,
                                       String topic,
                                       String message,
                                       List<MultipartFile> files,
                                       boolean forceSave,
                                       boolean persistAsKnowledge,
                                       String channel) {
        String cleanMessage = message == null ? "" : message.trim();
        List<MultipartFile> safeFiles = files == null ? List.of() : files.stream()
                .filter(f -> f != null && !f.isEmpty())
                .toList();

        if (!StringUtils.hasText(cleanMessage) && safeFiles.isEmpty()) {
            throw new IllegalArgumentException("학습할 메시지 또는 파일이 필요합니다.");
        }

        Map<String, Object> version = repository.findVersion(versionId)
                .orElseThrow(() -> new IllegalArgumentException("버전을 찾을 수 없습니다."));

        List<Map<String, Object>> assets = new ArrayList<>();
        List<RagUploadedKnowledgeDocument> parsedDocuments = new ArrayList<>();
        for (MultipartFile file : safeFiles) {
            Map<String, Object> asset = storageService.saveAsset(
                    projectId,
                    versionId,
                    persistAsKnowledge ? "LEARNING_FILE" : "CHAT_ATTACHMENT",
                    ownerSessionId == null ? null : ownerSessionId.toString(),
                    file,
                    cleanMessage
            );
            assets.add(asset);
            parsedDocuments.add(parser.parse(file));
        }

        String rawInputText = buildRawInputText(cleanMessage, parsedDocuments);
        List<Map<String, Object>> retrieved = retrieveRelated(projectId, versionId, rawInputText, 8);
        Map<String, Object> analysis = analyzeRelevanceAndKnowledge(version, topic, cleanMessage, parsedDocuments, retrieved, forceSave, persistAsKnowledge);
        boolean requiresClarification = RagJsonUtils.boolValue(analysis, "requiresClarification", false);
        boolean shouldPersist = RagJsonUtils.boolValue(analysis, "shouldPersist", persistAsKnowledge && hasKnowledgeText(parsedDocuments, cleanMessage));

        if (persistAsKnowledge) {
            repository.insertLearningMessage(UUID.randomUUID(), ownerSessionId, projectId, versionId, "user", renderLearningUserMessage(cleanMessage, parsedDocuments));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("analysis", analysis);
        result.put("assets", assets);
        result.put("retrieved", retrieved);
        result.put("rawInputText", RagJsonUtils.truncate(rawInputText, PROMPT_TEXT_LIMIT));
        result.put("parsedFiles", parsedDocuments.stream().map(this::fileMeta).toList());
        result.put("requiresClarification", requiresClarification && !forceSave);
        result.put("shouldPersist", shouldPersist && !requiresClarification || forceSave);
        result.put("topic", topic);
        result.put("channel", channel);

        if (requiresClarification && !forceSave) {
            if (persistAsKnowledge) {
                repository.updateLearningSessionPendingResolution(ownerSessionId, analysis);
                String answer = String.valueOf(analysis.getOrDefault("clarificationQuestion", "자료의 의미를 확정해야 저장할 수 있습니다. 어떤 기준으로 학습하면 되는지 알려주세요."));
                repository.insertLearningMessage(UUID.randomUUID(), ownerSessionId, projectId, versionId, "assistant", answer);
                result.put("answer", answer);
                result.put("saveStatus", "지식 저장: 저장 보류");
                result.put("saveMessage", "기존 지식과 충돌되거나 확인이 필요한 내용이 있어 아직 벡터DB에 저장하지 않았습니다.");
            } else {
                result.put("answer", String.valueOf(analysis.getOrDefault("clarificationQuestion", "첨부 파일과 요청 내용의 관계가 불명확합니다. 어떤 목적으로 올린 자료인지 알려주세요.")));
            }
            return result;
        }

        if (!persistAsKnowledge) {
            result.put("answer", String.valueOf(analysis.getOrDefault("answer", "첨부 파일을 현재 상담 문맥으로만 분석했습니다.")));
            result.put("saveStatus", "지식 저장: 저장 생략");
            result.put("saveMessage", "대화 로그는 저장되었지만 재사용 가능한 새 지식이 아니어서 지식 저장은 생략했습니다.");
            return result;
        }

        String normalizedKnowledgeText = String.valueOf(analysis.getOrDefault("normalizedKnowledgeText", rawInputText));
        if (!StringUtils.hasText(normalizedKnowledgeText)) {
            normalizedKnowledgeText = rawInputText;
        }

        Map<String, Object> mergedVersion = mergeIntoVersion(version, topic, normalizedKnowledgeText, analysis);
        Map<String, Object> updatedVersion = repository.updateVersionSynthesis(
                versionId,
                String.valueOf(mergedVersion.getOrDefault("summary", "학습 완료")),
                mergedVersion.getOrDefault("processJson", Map.of()),
                mergedVersion.getOrDefault("pricingJson", Map.of()),
                mergedVersion.getOrDefault("constraintsJson", Map.of()),
                mergedVersion.getOrDefault("validationReportJson", Map.of())
        );

        UUID documentId = UUID.randomUUID();
        String sourceType = parsedDocuments.isEmpty() ? "TEXT_MESSAGE" : combinedSourceType(parsedDocuments);
        String title = titleForDocument(topic, cleanMessage, parsedDocuments);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("channel", channel);
        metadata.put("topic", topic);
        metadata.put("message", cleanMessage);
        metadata.put("files", parsedDocuments.stream().map(this::fileMeta).toList());
        metadata.put("analysis", analysis);
        metadata.put("createdAt", LocalDateTime.now().toString());

        repository.insertDocument(documentId, projectId, versionId, topic, sourceType, title, firstFilename(parsedDocuments), normalizedKnowledgeText, metadata);
        insertChunks(projectId, versionId, documentId, topic, normalizedKnowledgeText, metadata);

        repository.clearLearningSessionPendingResolution(ownerSessionId);
        String answer = String.valueOf(analysis.getOrDefault("answer", "학습 내용을 기존 지식과 병합해 저장했습니다."));
        repository.insertLearningMessage(UUID.randomUUID(), ownerSessionId, projectId, versionId, "assistant", answer);

        result.put("answer", answer);
        result.put("version", updatedVersion);
        result.put("saveStatus", "지식 저장: 저장됨");
        result.put("saveMessage", "이번 대화에서 재사용 가능한 지식을 분석·병합하여 벡터DB와 현재 버전에 반영했습니다.");
        return result;
    }

    private Map<String, Object> analyzeRelevanceAndKnowledge(Map<String, Object> version,
                                                             String topic,
                                                             String message,
                                                             List<RagUploadedKnowledgeDocument> docs,
                                                             List<Map<String, Object>> retrieved,
                                                             boolean forceSave,
                                                             boolean persistAsKnowledge) {
        String systemPrompt = """
                당신은 욕실가구/제품 발주 프로세스와 가격계산 RAG 학습 검수자입니다.
                사용자는 텍스트, 파일, 또는 텍스트+파일을 함께 보낼 수 있습니다.
                반드시 JSON 객체만 반환하세요.

                중요한 원칙:
                1) 엑셀은 고정 양식이 아닙니다. 제품명/사이즈/색상/가격 같은 컬럼명이 항상 있다고 가정하지 마세요.
                2) 엑셀 파서는 원본 격자, 1차원 테이블 후보, 2차원 교차표 후보를 제공합니다.
                   - 1차원 후보: 헤더와 각 행 값의 조합입니다.
                   - 2차원 후보: 행축 값 × 열축 값 => 교차 셀 값의 조합입니다.
                3) 어떤 후보가 실제 의미인지 사용자 메시지, 파일명, 시트명, 기존 지식, 검색 근거를 함께 보고 판단하세요.
                4) 예: “A 시리즈 가격표”라는 메시지와 교차표가 함께 오면 행축/열축/셀값을 A 시리즈의 규격·색상·가격·가능 여부 후보로 해석할 수 있습니다.
                5) 메시지와 파일의 관계가 불명확하거나 기존 지식과 충돌하면 저장하지 말고 clarificationQuestion을 작성하세요.
                6) 단순 질문/잡담/상담 첨부이면 shouldPersist=false로 둡니다.
                7) 저장 가능한 경우 normalizedKnowledgeText에는 확정 지식만 한국어로 구조화하세요. 불확실한 후보는 "확인 필요"로 분리하세요.

                반환 필드:
                {
                  "relevance":"RELATED|UNRELATED|UNCERTAIN",
                  "requiresClarification": boolean,
                  "shouldPersist": boolean,
                  "clarificationQuestion":"",
                  "answer":"",
                  "topic":"",
                  "sourceType":"TEXT|EXCEL_STRUCTURED_GRID|MIXED|CHAT_ATTACHMENT",
                  "detectedFacts":[],
                  "detectedRelations":[],
                  "oneDimensionalInterpretation":{},
                  "twoDimensionalInterpretation":{},
                  "conflicts":[],
                  "uncertainPoints":[],
                  "normalizedKnowledgeText":""
                }
                """;

        String userPrompt = """
                [학습 주제]
                %s

                [저장 강제 여부]
                %s

                [전역 지식으로 저장하는 입력인지]
                %s

                [현재 버전 요약]
                %s

                [현재 가격 JSON]
                %s

                [현재 제약 JSON]
                %s

                [검색된 근거]
                %s

                [사용자 메시지]
                %s

                [업로드 파일에서 추출한 텍스트]
                %s
                """.formatted(
                topic,
                forceSave,
                persistAsKnowledge,
                RagJsonUtils.truncate(String.valueOf(version.get("summary")), 4_000),
                RagJsonUtils.truncate(String.valueOf(version.get("pricing_json")), 8_000),
                RagJsonUtils.truncate(String.valueOf(version.get("constraints_json")), 8_000),
                RagJsonUtils.truncate(RagJsonUtils.pretty(objectMapper, retrieved), 8_000),
                message,
                RagJsonUtils.truncate(docsToPromptText(docs), PROMPT_TEXT_LIMIT)
        );

        try {
            JsonNode node = RagJsonUtils.extractObjectNode(objectMapper, ai.responseJson(systemPrompt, userPrompt));
            return RagJsonUtils.toMap(objectMapper, node.toString());
        } catch (Exception e) {
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("relevance", docs.isEmpty() ? "UNCERTAIN" : "RELATED");
            fallback.put("requiresClarification", false);
            fallback.put("shouldPersist", persistAsKnowledge && hasKnowledgeText(docs, message));
            fallback.put("answer", "AI 검수 응답 생성 중 문제가 있었지만, 추출 가능한 내용을 기준으로 저장을 진행했습니다. 오류: " + e.getMessage());
            fallback.put("topic", topic);
            fallback.put("sourceType", docs.isEmpty() ? "TEXT" : combinedSourceType(docs));
            fallback.put("normalizedKnowledgeText", buildRawInputText(message, docs));
            fallback.put("analysisError", e.getMessage());
            return fallback;
        }
    }

    private Map<String, Object> mergeIntoVersion(Map<String, Object> version,
                                                 String topic,
                                                 String newKnowledgeText,
                                                 Map<String, Object> analysis) {
        String systemPrompt = """
                당신은 RAG 지식 병합기입니다. JSON 객체만 반환하세요.
                새 지식을 기존 process_json/pricing_json/constraints_json과 병합합니다.
                삭제 지시가 아닌 이상 기존 지식을 제거하지 말고, 새 지식과 충돌하면 validationReportJson.conflicts에 남기세요.
                엑셀 지식은 고정 컬럼명으로 가정하지 말고 1차원 테이블 또는 2차원 교차표에서 확정된 관계를 보존하세요.
                가격표라면 pricingJson 안에 제품/시리즈/규격/색상/옵션/가격/가능여부/가산조건 관계가 검색 가능한 구조로 남아야 합니다.
                가격표가 아니라 프로세스나 제약표라면 processJson 또는 constraintsJson에 행축·열축·값의 의미가 유지되도록 병합하세요.
                반환 필드:
                {
                  "summary":"",
                  "processJson":{},
                  "pricingJson":{},
                  "constraintsJson":{},
                  "validationReportJson":{}
                }
                """;
        String userPrompt = """
                [주제]
                %s

                [기존 summary]
                %s

                [기존 process_json]
                %s

                [기존 pricing_json]
                %s

                [기존 constraints_json]
                %s

                [기존 validation_report_json]
                %s

                [새 입력 분석]
                %s

                [새 확정 지식]
                %s
                """.formatted(
                topic,
                RagJsonUtils.truncate(String.valueOf(version.get("summary")), 4_000),
                RagJsonUtils.truncate(String.valueOf(version.get("process_json")), 12_000),
                RagJsonUtils.truncate(String.valueOf(version.get("pricing_json")), 12_000),
                RagJsonUtils.truncate(String.valueOf(version.get("constraints_json")), 12_000),
                RagJsonUtils.truncate(String.valueOf(version.get("validation_report_json")), 8_000),
                RagJsonUtils.truncate(RagJsonUtils.pretty(objectMapper, analysis), 8_000),
                RagJsonUtils.truncate(newKnowledgeText, PROMPT_TEXT_LIMIT)
        );
        try {
            JsonNode node = RagJsonUtils.extractObjectNode(objectMapper, ai.responseJson(systemPrompt, userPrompt));
            return RagJsonUtils.toMap(objectMapper, node.toString());
        } catch (Exception e) {
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("summary", StringUtils.hasText(String.valueOf(version.get("summary")))
                    ? String.valueOf(version.get("summary")) + "\n" + topic + " 학습 추가됨"
                    : topic + " 학습 추가됨");
            fallback.put("processJson", RagJsonUtils.toMap(objectMapper, version.get("process_json")));
            Map<String, Object> pricing = RagJsonUtils.toMap(objectMapper, version.get("pricing_json"));
            pricing.put("lastRawKnowledge", newKnowledgeText);
            fallback.put("pricingJson", pricing);
            fallback.put("constraintsJson", RagJsonUtils.toMap(objectMapper, version.get("constraints_json")));
            fallback.put("validationReportJson", Map.of("mergeFallback", true, "error", e.getMessage(), "analysis", analysis));
            return fallback;
        }
    }

    private void insertChunks(UUID projectId,
                              UUID versionId,
                              UUID documentId,
                              String topic,
                              String text,
                              Map<String, Object> metadata) {
        List<String> chunks = chunk(text, CHUNK_SIZE, CHUNK_OVERLAP);
        for (int i = 0; i < chunks.size(); i++) {
            String chunk = chunks.get(i);
            Map<String, Object> chunkMeta = new LinkedHashMap<>(metadata);
            chunkMeta.put("chunkNo", i + 1);
            try {
                String vectorLiteral = RagRepository.toVectorLiteral(ai.embedding(chunk));
                repository.insertChunk(UUID.randomUUID(), documentId, projectId, versionId, i + 1, topic, chunk, chunkMeta, vectorLiteral);
            } catch (Exception e) {
                chunkMeta.put("embeddingError", e.getMessage());
                repository.insertChunkWithoutEmbedding(UUID.randomUUID(), documentId, projectId, versionId, i + 1, topic, chunk, chunkMeta);
            }
        }
    }

    private List<Map<String, Object>> retrieveRelated(UUID projectId, UUID versionId, String rawInputText, int limit) {
        if (!StringUtils.hasText(rawInputText)) return List.of();
        try {
            String vectorLiteral = RagRepository.toVectorLiteral(ai.embedding(RagJsonUtils.truncate(rawInputText, 4_000)));
            return repository.searchChunks(projectId, versionId, vectorLiteral, limit);
        } catch (Exception e) {
            return List.of(Map.of("retrieveError", e.getMessage()));
        }
    }

    private String buildRawInputText(String message, List<RagUploadedKnowledgeDocument> docs) {
        StringBuilder sb = new StringBuilder();
        if (StringUtils.hasText(message)) {
            sb.append("[사용자 메시지]\n").append(message.trim()).append("\n\n");
        }
        if (docs != null) {
            for (RagUploadedKnowledgeDocument doc : docs) {
                sb.append("[파일: ").append(doc.originalFilename()).append("]\n")
                        .append(doc.rawText()).append("\n\n");
            }
        }
        return sb.toString().trim();
    }

    private String docsToPromptText(List<RagUploadedKnowledgeDocument> docs) {
        if (docs == null || docs.isEmpty()) return "첨부 파일 없음";
        StringBuilder sb = new StringBuilder();
        for (RagUploadedKnowledgeDocument doc : docs) {
            sb.append("파일명: ").append(doc.originalFilename()).append('\n')
                    .append("sourceType: ").append(doc.sourceType()).append('\n')
                    .append("metadata: ").append(RagJsonUtils.pretty(objectMapper, doc.metadata())).append('\n')
                    .append(doc.rawText()).append("\n\n");
        }
        return sb.toString();
    }

    private String renderLearningUserMessage(String message, List<RagUploadedKnowledgeDocument> docs) {
        StringBuilder sb = new StringBuilder();
        if (StringUtils.hasText(message)) sb.append(message.trim());
        if (docs != null && !docs.isEmpty()) {
            if (sb.length() > 0) sb.append("\n\n");
            sb.append("첨부 파일:\n");
            for (RagUploadedKnowledgeDocument doc : docs) {
                sb.append("- ").append(doc.originalFilename()).append(" (").append(doc.sourceType()).append(")\n");
            }
        }
        return sb.toString();
    }

    private boolean hasKnowledgeText(List<RagUploadedKnowledgeDocument> docs, String message) {
        if (StringUtils.hasText(message) && message.length() > 12) return true;
        return docs != null && docs.stream().anyMatch(d -> StringUtils.hasText(d.rawText()) && !"UNSUPPORTED_FILE".equals(d.sourceType()));
    }

    private String combinedSourceType(List<RagUploadedKnowledgeDocument> docs) {
        if (docs == null || docs.isEmpty()) return "TEXT_MESSAGE";
        boolean excel = docs.stream().anyMatch(d -> d.sourceType().startsWith("EXCEL"));
        boolean text = docs.stream().anyMatch(d -> d.sourceType().contains("TEXT"));
        if (excel && text) return "MIXED_EXCEL_TEXT";
        if (excel) return "EXCEL_STRUCTURED_GRID";
        if (text) return "TEXT_FILE";
        return "UPLOADED_FILE";
    }

    private String titleForDocument(String topic, String message, List<RagUploadedKnowledgeDocument> docs) {
        if (docs != null && !docs.isEmpty()) {
            return topic + " / " + docs.get(0).originalFilename();
        }
        if (StringUtils.hasText(message)) {
            return topic + " / " + RagJsonUtils.truncate(message.replaceAll("\\s+", " "), 40);
        }
        return topic;
    }

    private String firstFilename(List<RagUploadedKnowledgeDocument> docs) {
        return docs == null || docs.isEmpty() ? null : docs.get(0).originalFilename();
    }

    private Map<String, Object> fileMeta(RagUploadedKnowledgeDocument doc) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("originalFilename", doc.originalFilename());
        meta.put("contentType", doc.contentType());
        meta.put("sourceType", doc.sourceType());
        meta.put("metadata", doc.metadata());
        return meta;
    }

    private List<String> chunk(String text, int chunkSize, int overlap) {
        if (!StringUtils.hasText(text)) return List.of();
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(text.length(), start + chunkSize);
            chunks.add(text.substring(start, end));
            if (end == text.length()) break;
            start = Math.max(0, end - overlap);
        }
        return chunks;
    }

    private String resolveTopic(Map<String, Object> session) {
        String topic = RagJsonUtils.stringValue(session, "topic");
        if (StringUtils.hasText(topic)) return topic;
        String title = RagJsonUtils.stringValue(session, "title");
        return StringUtils.hasText(title) ? title : "대화형 지식 학습";
    }
}
