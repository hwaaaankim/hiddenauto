package com.dev.HiddenBATHAuto.rag.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.dev.HiddenBATHAuto.rag.dto.RagLearningConversationMessageRequest;
import com.dev.HiddenBATHAuto.rag.dto.RagLearningConversationStartRequest;
import com.dev.HiddenBATHAuto.rag.dto.RagLearningMessageRequest;
import com.dev.HiddenBATHAuto.rag.dto.RagLearningSessionStartRequest;
import com.dev.HiddenBATHAuto.rag.dto.RagTextLearningRequest;
import com.dev.HiddenBATHAuto.rag.repository.RagRepository;
import com.dev.HiddenBATHAuto.rag.util.RagJsonUtils;

@Service
public class RagLearningConversationService {

    private final RagRepository repository;
    private final RagKnowledgeIngestionService ingestionService;

    public RagLearningConversationService(RagRepository repository,
                                          RagKnowledgeIngestionService ingestionService) {
        this.repository = repository;
        this.ingestionService = ingestionService;
    }

    @Transactional(transactionManager = "ragTransactionManager")
    public Map<String, Object> start(UUID projectId, UUID versionId, String title, String topic) {
        if (projectId == null) throw new IllegalArgumentException("projectId가 필요합니다.");
        UUID resolvedVersionId = versionId != null
                ? versionId
                : repository.findLatestVersion(projectId)
                .map(v -> (UUID) v.get("id"))
                .orElseThrow(() -> new IllegalArgumentException("학습할 버전을 찾을 수 없습니다."));

        String resolvedTitle = StringUtils.hasText(title) ? title.trim() : "대화형 지식 학습";
        String resolvedTopic = StringUtils.hasText(topic) ? topic.trim() : resolvedTitle;
        UUID sessionId = UUID.randomUUID();
        Map<String, Object> session = repository.createLearningSession(sessionId, projectId, resolvedVersionId, resolvedTitle, resolvedTopic);

        String answer = "대화형 학습을 시작합니다. 제품/발주/가격 규칙을 문장으로 입력하거나, 엑셀·텍스트 파일을 이 대화창에 드래그앤드랍해 주세요. 메시지와 파일을 함께 보내면 둘의 관계를 먼저 검수한 뒤 저장합니다.";
        repository.insertLearningMessage(UUID.randomUUID(), sessionId, projectId, resolvedVersionId, "assistant", answer);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sessionId", sessionId);
        result.put("session", session);
        result.put("answer", answer);
        result.put("saveStatus", "지식 저장: 대기");
        return result;
    }



    /**
     * 신규 DTO 직접 전달 호출부 호환용입니다.
     */
    @Transactional(transactionManager = "ragTransactionManager")
    public Map<String, Object> start(RagLearningConversationStartRequest request) {
        if (request == null) throw new IllegalArgumentException("학습 세션 시작 요청이 비어 있습니다.");
        return start(request.projectId(), request.versionId(), request.title(), request.topic());
    }

    /**
     * 기존 컨트롤러 호환용 메서드입니다.
     * 예전 코드가 service.start(request) 형태로 호출해도 컴파일되도록 유지합니다.
     */
    @Transactional(transactionManager = "ragTransactionManager")
    public Map<String, Object> start(RagLearningSessionStartRequest request) {
        if (request == null) throw new IllegalArgumentException("학습 세션 시작 요청이 비어 있습니다.");
        String topic = firstText(request.topic(), request.domain(), request.title());
        return start(request.projectId(), request.versionId(), request.title(), topic);
    }

    @Transactional(transactionManager = "ragTransactionManager")
    public Map<String, Object> message(UUID sessionId, String message, boolean forceSave) {
        return ingestionService.ingestLearningInput(sessionId, message, List.of(), forceSave);
    }

    /**
     * 기존 컨트롤러 호환용입니다.
     * 일부 버전은 service.message(sessionId, request) 형태로 호출했습니다.
     */
    @Transactional(transactionManager = "ragTransactionManager")
    public Map<String, Object> message(UUID sessionId, RagLearningMessageRequest request) {
        if (request == null) throw new IllegalArgumentException("학습 메시지 요청이 비어 있습니다.");
        return message(sessionId, firstText(request.message(), request.content(), request.text(), request.learningText()), Boolean.TRUE.equals(request.forceSave()));
    }

    /**
     * 신규 DTO명을 사용하는 컨트롤러 호환용입니다.
     */
    @Transactional(transactionManager = "ragTransactionManager")
    public Map<String, Object> message(UUID sessionId, RagLearningConversationMessageRequest request) {
        if (request == null) throw new IllegalArgumentException("학습 메시지 요청이 비어 있습니다.");
        return message(sessionId, request.message(), Boolean.TRUE.equals(request.forceSave()));
    }


    /**
     * 기존 텍스트 학습 API 호환용 메서드입니다.
     * 예전 컨트롤러가 service.learnText(projectId, versionId, request) 형태로 호출해도
     * 내부적으로 대화형 학습 세션을 만들고 동일한 검수/병합 파이프라인을 사용합니다.
     */
    @Transactional(transactionManager = "ragTransactionManager")
    public Map<String, Object> learnText(UUID projectId, UUID versionId, RagTextLearningRequest request) {
        if (projectId == null) throw new IllegalArgumentException("projectId가 필요합니다.");
        if (request == null) throw new IllegalArgumentException("텍스트 학습 요청이 비어 있습니다.");

        String message = firstText(request.message(), request.content(), request.text(), request.learningText());
        if (!StringUtils.hasText(message)) {
            throw new IllegalArgumentException("학습할 텍스트가 필요합니다.");
        }

        String title = firstText(request.title(), request.topic(), request.domain(), "텍스트 지식 학습");
        String topic = firstText(request.topic(), request.domain(), request.title(), title);
        Map<String, Object> started = start(projectId, versionId, title, topic);
        UUID sessionId = (UUID) started.get("sessionId");
        Map<String, Object> learned = message(sessionId, message, Boolean.TRUE.equals(request.forceSave()));
        learned.put("sessionId", sessionId);
        learned.put("session", started.get("session"));
        return learned;
    }


    /**
     * versionId를 생략하던 기존 호출부 호환용입니다.
     */
    @Transactional(transactionManager = "ragTransactionManager")
    public Map<String, Object> learnText(UUID projectId, RagTextLearningRequest request) {
        return learnText(projectId, null, request);
    }

    /**
     * 기존 엑셀 학습 API 호환용입니다.
     * 일부 컨트롤러는 세션 없이 project/version/title/topic/file만 넘겨 학습했습니다.
     */
    @Transactional(transactionManager = "ragTransactionManager")
    public Map<String, Object> learnExcel(UUID projectId, UUID versionId, String title, String topic, MultipartFile file) {
        if (projectId == null) throw new IllegalArgumentException("projectId가 필요합니다.");
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("학습할 엑셀 파일이 필요합니다.");

        String resolvedTitle = firstText(title, topic, "엑셀 지식 학습");
        String resolvedTopic = firstText(topic, title, "엑셀 지식 학습");
        Map<String, Object> started = start(projectId, versionId, resolvedTitle, resolvedTopic);
        UUID sessionId = (UUID) started.get("sessionId");

        String message = "엑셀 파일 학습 요청";
        if (StringUtils.hasText(resolvedTopic)) {
            message += "\n주제: " + resolvedTopic;
        }
        if (StringUtils.hasText(resolvedTitle) && !resolvedTitle.equals(resolvedTopic)) {
            message += "\n제목: " + resolvedTitle;
        }

        Map<String, Object> learned = file(sessionId, message, List.of(file), false);
        learned.put("sessionId", sessionId);
        learned.put("session", started.get("session"));
        return learned;
    }

    /**
     * versionId를 생략하던 엑셀 학습 호출부 호환용입니다.
     */
    @Transactional(transactionManager = "ragTransactionManager")
    public Map<String, Object> learnExcel(UUID projectId, String title, String topic, MultipartFile file) {
        return learnExcel(projectId, null, title, topic, file);
    }

    /**
     * 다중 엑셀/파일 학습 호출부 호환용입니다.
     */
    @Transactional(transactionManager = "ragTransactionManager")
    public Map<String, Object> learnExcel(UUID projectId, UUID versionId, String title, String topic, List<MultipartFile> files) {
        if (projectId == null) throw new IllegalArgumentException("projectId가 필요합니다.");
        List<MultipartFile> safeFiles = files == null ? List.of() : files.stream().filter(f -> f != null && !f.isEmpty()).toList();
        if (safeFiles.isEmpty()) throw new IllegalArgumentException("학습할 엑셀 파일이 필요합니다.");

        String resolvedTitle = firstText(title, topic, "엑셀 지식 학습");
        String resolvedTopic = firstText(topic, title, "엑셀 지식 학습");
        Map<String, Object> started = start(projectId, versionId, resolvedTitle, resolvedTopic);
        UUID sessionId = (UUID) started.get("sessionId");
        Map<String, Object> learned = file(sessionId, "엑셀 파일 학습 요청\n주제: " + resolvedTopic, safeFiles, false);
        learned.put("sessionId", sessionId);
        learned.put("session", started.get("session"));
        return learned;
    }

    @Transactional(transactionManager = "ragTransactionManager")
    public Map<String, Object> file(UUID sessionId, String message, List<MultipartFile> files, boolean forceSave) {
        return ingestionService.ingestLearningInput(sessionId, message, files, forceSave);
    }

    /**
     * 단일 파일 호출부 호환용입니다.
     */
    @Transactional(transactionManager = "ragTransactionManager")
    public Map<String, Object> file(UUID sessionId, String message, MultipartFile file, boolean forceSave) {
        return file(sessionId, message, file == null ? List.of() : List.of(file), forceSave);
    }

    @Transactional(transactionManager = "ragTransactionManager")
    public Map<String, Object> resetKnowledge(UUID projectId,
                                              UUID versionId,
                                              UUID sessionId,
                                              String topic,
                                              String reason,
                                              boolean resetWholeVersion) {
        UUID resolvedProjectId = projectId;
        UUID resolvedVersionId = versionId;
        String resolvedTopic = topic;

        if (sessionId != null) {
            Map<String, Object> session = repository.findLearningSession(sessionId)
                    .orElseThrow(() -> new IllegalArgumentException("초기화할 학습 세션을 찾을 수 없습니다."));
            resolvedProjectId = resolvedProjectId == null ? (UUID) session.get("project_id") : resolvedProjectId;
            resolvedVersionId = resolvedVersionId == null ? (UUID) session.get("version_id") : resolvedVersionId;
            if (!StringUtils.hasText(resolvedTopic)) {
                resolvedTopic = RagJsonUtils.stringValue(session, "topic");
                if (!StringUtils.hasText(resolvedTopic)) resolvedTopic = RagJsonUtils.stringValue(session, "title");
            }
        }

        if (resolvedProjectId == null) throw new IllegalArgumentException("projectId가 필요합니다.");
        if (resolvedVersionId == null) throw new IllegalArgumentException("versionId가 필요합니다.");

        Map<String, Object> resetResult = repository.resetKnowledge(
                resolvedProjectId,
                resolvedVersionId,
                resolvedTopic,
                resetWholeVersion,
                reason
        );

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("answer", resetWholeVersion
                ? "선택한 버전의 학습 지식을 초기화했습니다."
                : "선택한 학습 주제의 지식을 초기화했습니다.");
        result.put("reset", resetResult);
        result.put("saveStatus", "지식 저장: 초기화");
        result.put("saveMessage", "문서/청크/학습메시지/파일자산을 삭제하고 초기화 이력을 저장했습니다.");
        return result;
    }
    private String firstText(String... values) {
        if (values == null) return null;
        for (String value : values) {
            if (StringUtils.hasText(value)) return value.trim();
        }
        return null;
    }

}
