package com.dev.HiddenBATHAuto.rag.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.dev.HiddenBATHAuto.rag.config.RagOpenAiProperties;
import com.dev.HiddenBATHAuto.rag.service.RagSemanticSourceDocumentFactory.SemanticDocument;
import com.dev.HiddenBATHAuto.rag.util.RagJsonUtils;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 원본 row 변경 queue를 소비해 텍스트 메타데이터와 embedding을 통합 인덱스에 반영합니다.
 * 다중 인스턴스에서는 FOR UPDATE SKIP LOCKED와 lock_token으로 중복 반영을 방지합니다.
 */
@Service
public class RagSemanticIndexWorker {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final RagSemanticSourceDocumentFactory documentFactory;
    private final OpenAiRagClient openAiClient;
    private final RagOpenAiProperties properties;
    private final AtomicBoolean scheduledRunning = new AtomicBoolean(false);

    public RagSemanticIndexWorker(
            @Qualifier("ragJdbcTemplate") NamedParameterJdbcTemplate jdbc,
            ObjectMapper objectMapper,
            RagSemanticSourceDocumentFactory documentFactory,
            OpenAiRagClient openAiClient,
            RagOpenAiProperties properties) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.documentFactory = documentFactory;
        this.openAiClient = openAiClient;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${hiddenbath.rag.openai.semantic-worker-delay-ms:5000}")
    public void scheduledProcess() {
        if (!properties.isSemanticWorkerEnabled()) return;
        if (!scheduledRunning.compareAndSet(false, true)) return;
        try {
            processAvailable(properties.getSemanticWorkerBatchSize());
        } catch (Exception ignored) {
            // 개별 항목 오류는 queue에 저장됩니다. scheduler 스레드를 종료시키지 않습니다.
        } finally {
            scheduledRunning.set(false);
        }
    }

    public Map<String, Object> processAvailable(int requestedLimit) {
        int limit = Math.max(1, Math.min(requestedLimit, 500));
        releaseExpiredLocks();
        int claimed = 0;
        int completed = 0;
        int failed = 0;
        int textOnly = 0;
        for (int i = 0; i < limit; i++) {
            Optional<QueueItem> maybeItem = claimOne();
            if (maybeItem.isEmpty()) break;
            claimed++;
            ProcessResult result = processOne(maybeItem.get());
            if (result.completed()) completed++;
            else failed++;
            if (result.textOnly()) textOnly++;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("requestedLimit", limit);
        result.put("claimed", claimed);
        result.put("completed", completed);
        result.put("failed", failed);
        result.put("textOnly", textOnly);
        return result;
    }

    private Optional<QueueItem> claimOne() {
        UUID token = UUID.randomUUID();
        List<Map<String, Object>> rows = jdbc.queryForList("""
                WITH candidate AS (
                    SELECT id
                      FROM rag_semantic_index_queue
                     WHERE status IN ('PENDING','ERROR')
                       AND available_at <= now()
                       AND attempt_count < :maxAttempts
                     ORDER BY priority ASC, available_at ASC, id ASC
                     FOR UPDATE SKIP LOCKED
                     LIMIT 1
                )
                UPDATE rag_semantic_index_queue q
                   SET status = 'PROCESSING',
                       attempt_count = q.attempt_count + 1,
                       locked_at = now(),
                       lock_token = :lockToken,
                       updated_at = now(),
                       last_error = NULL
                  FROM candidate c
                 WHERE q.id = c.id
                RETURNING q.id, q.project_id, q.version_id, q.source_table, q.source_id,
                          q.operation, q.attempt_count, q.lock_token
                """, new MapSqlParameterSource()
                .addValue("maxAttempts", properties.getSemanticWorkerMaxAttempts())
                .addValue("lockToken", token));
        if (rows.isEmpty()) return Optional.empty();
        Map<String, Object> row = rows.get(0);
        return Optional.of(new QueueItem(
                longValue(row.get("id")),
                uuid(row.get("project_id")),
                uuid(row.get("version_id")),
                text(row.get("source_table")),
                uuid(row.get("source_id")),
                text(row.get("operation")),
                intValue(row.get("attempt_count")),
                uuid(row.get("lock_token"))));
    }

    private ProcessResult processOne(QueueItem item) {
        try {
            if ("DELETE".equalsIgnoreCase(item.operation())) {
                deactivateMemory(item, "SOURCE_DELETED");
                complete(item, "SOURCE_DELETED");
                return new ProcessResult(true, false);
            }

            Optional<SemanticDocument> loaded = documentFactory.load(
                    item.projectId(), item.versionId(), item.sourceTable(), item.sourceId());
            if (loaded.isEmpty()) {
                deactivateMemory(item, "SOURCE_NOT_FOUND");
                complete(item, "SOURCE_NOT_FOUND_DEACTIVATED");
                return new ProcessResult(true, false);
            }

            SemanticDocument document = loaded.get();
            if (isCurrent(document)) {
                jdbc.update("""
                        UPDATE rag_semantic_memory
                           SET active = :active, source_updated_at = CAST(NULLIF(:sourceUpdatedAt, '') AS timestamptz),
                               indexed_at = now(), updated_at = now()
                         WHERE project_id = :projectId AND version_id = :versionId
                           AND source_table = :sourceTable AND source_id = :sourceId
                        """, documentParams(document));
                complete(item, "UNCHANGED_HASH");
                return new ProcessResult(true, false);
            }

            if (!openAiClient.hasApiKey()) {
                ensureSourceUnchanged(document);
                upsert(document, List.of(), "ERROR", "OPENAI_API_KEY가 없어 텍스트 인덱스만 생성했습니다.");
                complete(item, "TEXT_ONLY_NO_API_KEY");
                return new ProcessResult(true, true);
            }

            try {
                List<Double> embedding = openAiClient.embedding(documentFactory.embeddingInput(document));
                validateDimensions(embedding);
                ensureSourceUnchanged(document);
                upsert(document, embedding, "READY", null);
                complete(item, "INDEXED");
                return new ProcessResult(true, false);
            } catch (Exception embeddingError) {
                try {
                    ensureSourceUnchanged(document);
                    // 임베딩 API만 실패했고 원본은 그대로이면 lexical/FTS fallback용 텍스트는 유지합니다.
                    upsert(document, List.of(), "ERROR", safeError(embeddingError));
                    fail(item, embeddingError);
                    return new ProcessResult(false, true);
                } catch (Exception sourceChangedError) {
                    // 임베딩 처리 중 원본이 바뀐 경우 이전 내용을 다시 활성화하지 않습니다.
                    markMemoryStale(item, safeError(sourceChangedError));
                    fail(item, sourceChangedError);
                    return new ProcessResult(false, false);
                }
            }
        } catch (Exception e) {
            fail(item, e);
            return new ProcessResult(false, false);
        }
    }

    private boolean isCurrent(SemanticDocument document) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT content_hash, embedding_status, embedding_model, active
                  FROM rag_semantic_memory
                 WHERE project_id = :projectId AND version_id = :versionId
                   AND source_table = :sourceTable AND source_id = :sourceId
                """, new MapSqlParameterSource()
                .addValue("projectId", document.projectId())
                .addValue("versionId", document.versionId())
                .addValue("sourceTable", document.sourceTable())
                .addValue("sourceId", document.sourceId()));
        if (rows.isEmpty()) return false;
        Map<String, Object> row = rows.get(0);
        return document.contentHash().equals(text(row.get("content_hash")))
                && "READY".equals(text(row.get("embedding_status")))
                && openAiClient.embeddingModel().equals(text(row.get("embedding_model")))
                && document.active() == bool(row.get("active"));
    }

    private void upsert(SemanticDocument document,
                        List<Double> embedding,
                        String embeddingStatus,
                        String embeddingError) {
        boolean hasEmbedding = embedding != null && !embedding.isEmpty();
        String vector = hasEmbedding ? vectorLiteral(embedding) : null;
        MapSqlParameterSource params = documentParams(document)
                .addValue("sourceKind", document.sourceKind())
                .addValue("domainKey", document.domainKey())
                .addValue("entityType", document.entityType())
                .addValue("entityKey", document.entityKey())
                .addValue("title", document.title())
                .addValue("content", document.content())
                .addValue("keywordsJson", RagJsonUtils.toJson(objectMapper, document.keywords()))
                .addValue("aliasesJson", RagJsonUtils.toJson(objectMapper, document.aliases()))
                .addValue("metadataJson", RagJsonUtils.toJson(objectMapper, document.metadata()))
                .addValue("contentHash", document.contentHash())
                .addValue("hasEmbedding", hasEmbedding)
                .addValue("embedding", vector)
                .addValue("embeddingModel", hasEmbedding ? openAiClient.embeddingModel() : null)
                .addValue("embeddingStatus", embeddingStatus)
                .addValue("embeddingError", embeddingError);

        jdbc.update("""
                INSERT INTO rag_semantic_memory(
                    id, project_id, version_id, source_table, source_id, source_kind, domain_key,
                    entity_type, entity_key, title, content, keywords, aliases, metadata_json,
                    content_hash, embedding, embedding_model, embedding_status, embedding_error,
                    active, source_updated_at, indexed_at, created_at, updated_at
                ) VALUES (
                    gen_random_uuid(), :projectId, :versionId, :sourceTable, :sourceId, :sourceKind, :domainKey,
                    :entityType, :entityKey, :title, :content,
                    ARRAY(SELECT jsonb_array_elements_text(CAST(:keywordsJson AS jsonb))),
                    ARRAY(SELECT jsonb_array_elements_text(CAST(:aliasesJson AS jsonb))),
                    CAST(:metadataJson AS jsonb), :contentHash,
                    CASE WHEN :hasEmbedding = true THEN CAST(:embedding AS vector) ELSE NULL END,
                    :embeddingModel, :embeddingStatus, :embeddingError, :active,
                    CAST(NULLIF(:sourceUpdatedAt, '') AS timestamptz), now(), now(), now()
                )
                ON CONFLICT (project_id, version_id, source_table, source_id)
                DO UPDATE SET
                    source_kind = EXCLUDED.source_kind,
                    domain_key = EXCLUDED.domain_key,
                    entity_type = EXCLUDED.entity_type,
                    entity_key = EXCLUDED.entity_key,
                    title = EXCLUDED.title,
                    content = EXCLUDED.content,
                    keywords = EXCLUDED.keywords,
                    aliases = EXCLUDED.aliases,
                    metadata_json = EXCLUDED.metadata_json,
                    content_hash = EXCLUDED.content_hash,
                    embedding = EXCLUDED.embedding,
                    embedding_model = EXCLUDED.embedding_model,
                    embedding_status = EXCLUDED.embedding_status,
                    embedding_error = EXCLUDED.embedding_error,
                    active = EXCLUDED.active,
                    source_updated_at = EXCLUDED.source_updated_at,
                    indexed_at = now(),
                    updated_at = now()
                """, params);
    }

    private MapSqlParameterSource documentParams(SemanticDocument document) {
        return new MapSqlParameterSource()
                .addValue("projectId", document.projectId())
                .addValue("versionId", document.versionId())
                .addValue("sourceTable", document.sourceTable())
                .addValue("sourceId", document.sourceId())
                .addValue("active", document.active())
                .addValue("sourceUpdatedAt", document.sourceUpdatedAt() == null ? "" : String.valueOf(document.sourceUpdatedAt()));
    }

    private void deactivateMemory(QueueItem item, String reason) {
        jdbc.update("""
                UPDATE rag_semantic_memory
                   SET active = false,
                       embedding_status = 'STALE',
                       embedding_error = :reason,
                       indexed_at = now(),
                       updated_at = now()
                 WHERE project_id = :projectId AND version_id = :versionId
                   AND source_table = :sourceTable AND source_id = :sourceId
                """, itemParams(item).addValue("reason", reason));
    }

    private void markMemoryStale(QueueItem item, String reason) {
        jdbc.update("""
                UPDATE rag_semantic_memory
                   SET active = false,
                       embedding_status = 'STALE',
                       embedding_error = :reason,
                       updated_at = now()
                 WHERE project_id = :projectId AND version_id = :versionId
                   AND source_table = :sourceTable AND source_id = :sourceId
                """, itemParams(item).addValue("reason", reason));
    }

    private void complete(QueueItem item, String note) {
        int updated = jdbc.update("""
                UPDATE rag_semantic_index_queue
                   SET status = 'DONE', completed_at = now(), updated_at = now(),
                       locked_at = NULL, lock_token = NULL, last_error = NULL, result_note = :note
                 WHERE id = :id AND status = 'PROCESSING' AND lock_token = :lockToken
                """, itemParams(item).addValue("note", note));
        if (updated != 1) {
            // 처리 중 원본 trigger가 동일 queue를 다시 PENDING으로 바꾼 경우, 오래된 결과가 READY로 남지 않게 합니다.
            markMemoryStale(item, "QUEUE_LOCK_TOKEN_CHANGED");
            throw new IllegalStateException("semantic queue 완료 시 lock_token이 일치하지 않습니다. 처리 중 원본이 변경되었을 수 있습니다. queueId=" + item.id());
        }
    }

    private void ensureSourceUnchanged(SemanticDocument expected) {
        Optional<SemanticDocument> latest = documentFactory.load(
                expected.projectId(), expected.versionId(), expected.sourceTable(), expected.sourceId());
        if (latest.isEmpty()) {
            throw new IllegalStateException("embedding 생성 중 원본이 삭제되었습니다: "
                    + expected.sourceTable() + "/" + expected.sourceId());
        }
        SemanticDocument current = latest.get();
        if (!expected.contentHash().equals(current.contentHash()) || expected.active() != current.active()) {
            throw new IllegalStateException("embedding 생성 중 원본이 변경되었습니다. 최신 queue에서 다시 처리합니다: "
                    + expected.sourceTable() + "/" + expected.sourceId());
        }
    }

    private void fail(QueueItem item, Exception error) {
        int maxAttempts = properties.getSemanticWorkerMaxAttempts();
        boolean exhausted = item.attemptCount() >= maxAttempts;
        long delaySeconds = Math.min(3600L, (long) Math.pow(2, Math.max(0, item.attemptCount() - 1)) * 15L);
        try {
            jdbc.update("""
                    UPDATE rag_semantic_index_queue
                       SET status = 'ERROR',
                           available_at = CASE WHEN :exhausted = true THEN 'infinity'::timestamptz
                                               ELSE now() + (:delaySeconds * interval '1 second') END,
                           locked_at = NULL,
                           lock_token = NULL,
                           last_error = :error,
                           result_note = CASE WHEN :exhausted = true THEN 'MAX_ATTEMPTS_EXHAUSTED' ELSE 'RETRY_SCHEDULED' END,
                           updated_at = now()
                     WHERE id = :id AND status = 'PROCESSING' AND lock_token = :lockToken
                    """, itemParams(item)
                    .addValue("exhausted", exhausted)
                    .addValue("delaySeconds", delaySeconds)
                    .addValue("error", safeError(error)));
        } catch (Exception ignored) {
            // 원래 오류를 보존합니다. 다음 잠금 만료 정리에서 복구됩니다.
        }
    }

    private void releaseExpiredLocks() {
        int timeout = properties.getSemanticWorkerLockTimeoutSeconds();
        jdbc.update("""
                UPDATE rag_semantic_index_queue
                   SET status = 'ERROR',
                       available_at = now(),
                       locked_at = NULL,
                       lock_token = NULL,
                       last_error = coalesce(last_error, '') || CASE WHEN coalesce(last_error, '') = '' THEN '' ELSE E'\n' END
                                    || 'PROCESSING lock expired',
                       result_note = 'LOCK_EXPIRED_RETRY',
                       updated_at = now()
                 WHERE status = 'PROCESSING'
                   AND locked_at < now() - (:timeoutSeconds * interval '1 second')
                """, new MapSqlParameterSource("timeoutSeconds", timeout));
    }

    private MapSqlParameterSource itemParams(QueueItem item) {
        return new MapSqlParameterSource()
                .addValue("id", item.id())
                .addValue("projectId", item.projectId())
                .addValue("versionId", item.versionId())
                .addValue("sourceTable", item.sourceTable())
                .addValue("sourceId", item.sourceId())
                .addValue("lockToken", item.lockToken());
    }

    private void validateDimensions(List<Double> embedding) {
        int expected = properties.getSemanticEmbeddingDimensions();
        if (embedding == null || embedding.size() != expected) {
            throw new IllegalStateException("embedding 차원 불일치: expected=" + expected
                    + ", actual=" + (embedding == null ? 0 : embedding.size()));
        }
    }

    private String vectorLiteral(List<Double> vector) {
        StringBuilder sb = new StringBuilder(vector.size() * 10 + 2).append('[');
        for (int i = 0; i < vector.size(); i++) {
            if (i > 0) sb.append(',');
            Double value = vector.get(i);
            if (value == null || value.isNaN() || value.isInfinite()) {
                throw new IllegalArgumentException("embedding에 유효하지 않은 숫자가 있습니다.");
            }
            sb.append(value);
        }
        return sb.append(']').toString();
    }

    private UUID uuid(Object value) {
        if (value instanceof UUID id) return id;
        return UUID.fromString(String.valueOf(value));
    }

    private long longValue(Object value) {
        if (value instanceof Number number) return number.longValue();
        return Long.parseLong(String.valueOf(value));
    }

    private int intValue(Object value) {
        if (value instanceof Number number) return number.intValue();
        return Integer.parseInt(String.valueOf(value));
    }

    private boolean bool(Object value) {
        if (value instanceof Boolean b) return b;
        return value != null && Boolean.parseBoolean(String.valueOf(value));
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String safeError(Exception error) {
        String message = error == null ? "" : error.getMessage();
        if (!StringUtils.hasText(message)) message = error == null ? "unknown" : error.getClass().getSimpleName();
        return message.length() <= 4000 ? message : message.substring(0, 4000) + "...[TRUNCATED]";
    }

    private record QueueItem(
            long id,
            UUID projectId,
            UUID versionId,
            String sourceTable,
            UUID sourceId,
            String operation,
            int attemptCount,
            UUID lockToken) {
    }

    private record ProcessResult(boolean completed, boolean textOnly) {
    }
}
