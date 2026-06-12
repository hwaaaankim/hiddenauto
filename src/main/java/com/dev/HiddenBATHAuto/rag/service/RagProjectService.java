package com.dev.HiddenBATHAuto.rag.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.dev.HiddenBATHAuto.rag.dto.RagProjectCreateRequest;
import com.dev.HiddenBATHAuto.rag.repository.RagRepository;

@Service
public class RagProjectService {

    private final RagRepository repository;
    private final OpenAiRagClient openAiClient;

    public RagProjectService(RagRepository repository, OpenAiRagClient openAiClient) {
        this.repository = repository;
        this.openAiClient = openAiClient;
    }

    public List<Map<String, Object>> findProjects() {
        return repository.findProjects();
    }

    public Map<String, Object> detailProject(UUID projectId) {
        return repository.detailProject(projectId);
    }

    public Map<String, Object> version(UUID versionId) {
        return repository.findVersion(versionId)
                .orElseThrow(() -> new IllegalArgumentException("버전을 찾을 수 없습니다."));
    }

    @Transactional("ragTransactionManager")
    public Map<String, Object> createProject(RagProjectCreateRequest request) {
        if (request == null || !StringUtils.hasText(request.title())) {
            throw new IllegalArgumentException("프로젝트 타이틀을 입력해 주세요.");
        }

        UUID projectId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        Map<String, Object> project = repository.createProject(
                projectId,
                request.title().trim(),
                request.description(),
                openAiClient.chatModel(),
                openAiClient.embeddingModel()
        );
        Map<String, Object> version = repository.createVersion(
                versionId,
                projectId,
                1,
                request.title().trim() + " v1",
                request.learningDirection()
        );
        repository.updateVersionSynthesis(
                versionId,
                "아직 학습이 시작되지 않았습니다. 관리자 학습 대화에서 프로세스 뼈대부터 입력하세요.",
                RagLearningSchemaFactory.emptyProcess(request.title().trim(), request.learningDirection()),
                RagLearningSchemaFactory.emptyPricing(),
                RagLearningSchemaFactory.emptyConstraints(),
                RagLearningSchemaFactory.emptyValidation()
        );

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("project", project);
        result.put("version", repository.findVersion(versionId).orElse(version));
        return result;
    }

    @Transactional("ragTransactionManager")
    public Map<String, Object> createNewVersion(UUID projectId, String learningDirection) {
        Map<String, Object> project = repository.findProject(projectId)
                .orElseThrow(() -> new IllegalArgumentException("프로젝트를 찾을 수 없습니다."));
        int versionNo = repository.findNextVersionNo(projectId);
        UUID versionId = UUID.randomUUID();
        String title = project.get("title") + " v" + versionNo;
        Map<String, Object> version = repository.createVersion(versionId, projectId, versionNo, title, learningDirection);
        repository.updateVersionSynthesis(
                versionId,
                "새 버전 초안입니다.",
                RagLearningSchemaFactory.emptyProcess(String.valueOf(project.get("title")), learningDirection),
                RagLearningSchemaFactory.emptyPricing(),
                RagLearningSchemaFactory.emptyConstraints(),
                RagLearningSchemaFactory.emptyValidation()
        );
        return repository.findVersion(versionId).orElse(version);
    }

    @Transactional("ragTransactionManager")
    public Map<String, Object> publish(UUID projectId, UUID versionId) {
        Map<String, Object> version = repository.findVersion(versionId)
                .orElseThrow(() -> new IllegalArgumentException("버전을 찾을 수 없습니다."));
        if (!projectId.equals(version.get("project_id"))) {
            throw new IllegalArgumentException("프로젝트와 버전이 일치하지 않습니다.");
        }
        repository.publishVersion(projectId, versionId);
        return repository.findVersion(versionId)
                .orElseThrow(() -> new IllegalArgumentException("발행된 버전을 다시 찾을 수 없습니다."));
    }
}
