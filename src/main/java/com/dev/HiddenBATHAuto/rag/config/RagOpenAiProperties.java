package com.dev.HiddenBATHAuto.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "hiddenbath.rag.openai")
public class RagOpenAiProperties {

    private String apiKey = "";
    private String baseUrl = "https://api.openai.com";
    private String chatModel = "gpt-5.6";
    private String embeddingModel = "text-embedding-3-large";
    private String reasoningEffort = "medium";
    private int readTimeoutSeconds = 300;
    private int retryCount = 1;

    /** 한 번의 사용자 요청에서 허용할 실제 OpenAI function tool 반복 횟수입니다. */
    private int agentMaxToolTurns = 30;

    /** Responses API 한 차수에서 허용할 최대 출력 토큰 수입니다. */
    private int agentMaxOutputTokens = 16000;

    /** query_database 도구의 기본 최대 row 수입니다. */
    private int agentDefaultReadRows = 200;

    /** query_database 도구가 요청할 수 있는 절대 최대 row 수입니다. */
    private int agentHardMaxReadRows = 1000;

    /** query_database 단일 조회의 DB 실행 제한 시간(초)입니다. */
    private int agentQueryTimeoutSeconds = 60;

    /** 모델에 반환하는 단일 도구 결과 JSON 최대 글자 수입니다. */
    private int agentMaxToolOutputChars = 60000;

    /** 모델 입력에 포함할 파일 미리보기 최대 글자 수입니다. */
    private int agentMaxFilePreviewChars = 40000;

    /** query_database/create_change_set에서 허용할 단일 SQL 최대 글자 수입니다. */
    private int agentMaxSqlChars = 50000;

    /** named parameter JSON 최대 글자 수입니다. */
    private int agentMaxParamsChars = 100000;

    /** 한 ChangeSet에 포함할 수 있는 최대 변경 항목 수입니다. */
    private int agentMaxChangeItems = 100;

    /** 한 SQL 문장이 변경할 수 있는 최대 row 수입니다. 초과 시 전체 트랜잭션을 롤백합니다. */
    private int agentMaxAffectedRowsPerStatement = 1;

    /** Agent가 도구 호출 없이 같은 상태를 반복할 때 복구로 전환하는 임계값입니다. */
    private int agentNoProgressLimit = 3;

    /** Tool loop 종료 후 GPT 복구답변을 다시 시도할 횟수입니다. */
    private int agentRecoveryAttempts = 2;

    /** Semantic Memory worker 실행 여부입니다. */
    private boolean semanticWorkerEnabled = true;

    /** Semantic queue worker 주기(ms)입니다. */
    private long semanticWorkerDelayMs = 5000L;

    /** 한 worker 주기에서 가져올 queue 수입니다. */
    private int semanticWorkerBatchSize = 10;

    /** Semantic queue 항목의 최대 시도 횟수입니다. */
    private int semanticWorkerMaxAttempts = 6;

    /** PROCESSING 잠금 만료 시간(초)입니다. */
    private int semanticWorkerLockTimeoutSeconds = 300;

    /** 임베딩에 전달할 semantic 본문 최대 글자 수입니다. */
    private int semanticEmbeddingInputChars = 4500;

    /** 모든 임베딩 호출의 예상 토큰 상한입니다. 문자 제한과 함께 적용합니다. */
    private int semanticEmbeddingEstimatedTokenLimit = 7000;

    /** semantic memory content에 저장할 최대 글자 수입니다. */
    private int semanticContentMaxChars = 60000;

    /** 질의 임베딩을 사용할지 여부입니다. 장애 시 FTS/pg_trgm으로 자동 폴백합니다. */
    private boolean semanticQueryEmbeddingEnabled = true;

    /** 기본 semantic 검색 결과 수입니다. */
    private int semanticDefaultSearchLimit = 15;

    /** semantic 검색 절대 최대 결과 수입니다. */
    private int semanticHardSearchLimit = 100;

    /** text-embedding-3-large를 dimensions=1536으로 축소하여 기존 DB vector(1536)와 호환합니다. */
    private int semanticEmbeddingDimensions = 1536;


    /** Agent 요청 전체 입력의 안전 문자 예산입니다. 토큰 초과 방지를 위해 초과 항목은 압축합니다. */
    private int agentMaxContextChars = 220000;

    /** 배포된 도구 계약 버전입니다. */
    private String agentCapabilityVersion = "V4-20260714";

    /** 최근 대화 중 원문으로 유지할 최대 건수입니다. */
    private int agentRecentMessageLimit = 12;

    /** tool loop 실패 시 GPT 무도구 최종답변 복구를 허용합니다. */
    private boolean agentTextRecoveryEnabled = true;

    private int adaptiveChunkThresholdChars = 1200;
    private int adaptiveChunkChars = 2800;
    private int adaptiveChunkOverlapChars = 220;
    private int adaptiveGroupSize = 4;
    private int adaptiveMaxLeafChunks = 480;

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getChatModel() { return chatModel; }
    public void setChatModel(String chatModel) { this.chatModel = chatModel; }
    public String getEmbeddingModel() { return embeddingModel; }
    public void setEmbeddingModel(String embeddingModel) { this.embeddingModel = embeddingModel; }
    public String getReasoningEffort() { return reasoningEffort; }
    public void setReasoningEffort(String reasoningEffort) { this.reasoningEffort = reasoningEffort; }
    public int getReadTimeoutSeconds() { return readTimeoutSeconds; }
    public void setReadTimeoutSeconds(int readTimeoutSeconds) { this.readTimeoutSeconds = Math.max(30, readTimeoutSeconds); }
    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int retryCount) { this.retryCount = Math.max(0, retryCount); }
    public int getAgentMaxToolTurns() { return agentMaxToolTurns; }
    public void setAgentMaxToolTurns(int agentMaxToolTurns) { this.agentMaxToolTurns = Math.max(4, Math.min(agentMaxToolTurns, 50)); }
    public int getAgentMaxOutputTokens() { return agentMaxOutputTokens; }
    public void setAgentMaxOutputTokens(int agentMaxOutputTokens) { this.agentMaxOutputTokens = Math.max(1000, Math.min(agentMaxOutputTokens, 128000)); }
    public int getAgentDefaultReadRows() { return agentDefaultReadRows; }
    public void setAgentDefaultReadRows(int agentDefaultReadRows) { this.agentDefaultReadRows = Math.max(1, Math.min(agentDefaultReadRows, 1000)); }
    public int getAgentHardMaxReadRows() { return agentHardMaxReadRows; }
    public void setAgentHardMaxReadRows(int agentHardMaxReadRows) { this.agentHardMaxReadRows = Math.max(10, Math.min(agentHardMaxReadRows, 5000)); }
    public int getAgentQueryTimeoutSeconds() { return agentQueryTimeoutSeconds; }
    public void setAgentQueryTimeoutSeconds(int agentQueryTimeoutSeconds) { this.agentQueryTimeoutSeconds = Math.max(5, Math.min(agentQueryTimeoutSeconds, 300)); }
    public int getAgentMaxToolOutputChars() { return agentMaxToolOutputChars; }
    public void setAgentMaxToolOutputChars(int agentMaxToolOutputChars) { this.agentMaxToolOutputChars = Math.max(10000, agentMaxToolOutputChars); }
    public int getAgentMaxFilePreviewChars() { return agentMaxFilePreviewChars; }
    public void setAgentMaxFilePreviewChars(int agentMaxFilePreviewChars) { this.agentMaxFilePreviewChars = Math.max(5000, agentMaxFilePreviewChars); }
    public int getAgentMaxSqlChars() { return agentMaxSqlChars; }
    public void setAgentMaxSqlChars(int agentMaxSqlChars) { this.agentMaxSqlChars = Math.max(1000, Math.min(agentMaxSqlChars, 500000)); }
    public int getAgentMaxParamsChars() { return agentMaxParamsChars; }
    public void setAgentMaxParamsChars(int agentMaxParamsChars) { this.agentMaxParamsChars = Math.max(1000, Math.min(agentMaxParamsChars, 1000000)); }
    public int getAgentMaxChangeItems() { return agentMaxChangeItems; }
    public void setAgentMaxChangeItems(int agentMaxChangeItems) { this.agentMaxChangeItems = Math.max(1, Math.min(agentMaxChangeItems, 1000)); }
    public int getAgentMaxAffectedRowsPerStatement() { return agentMaxAffectedRowsPerStatement; }
    public void setAgentMaxAffectedRowsPerStatement(int agentMaxAffectedRowsPerStatement) { this.agentMaxAffectedRowsPerStatement = Math.max(1, Math.min(agentMaxAffectedRowsPerStatement, 1000000)); }
    public int getAgentNoProgressLimit() { return agentNoProgressLimit; }
    public void setAgentNoProgressLimit(int agentNoProgressLimit) { this.agentNoProgressLimit = Math.max(1, Math.min(agentNoProgressLimit, 20)); }
    public int getAgentRecoveryAttempts() { return agentRecoveryAttempts; }
    public void setAgentRecoveryAttempts(int agentRecoveryAttempts) { this.agentRecoveryAttempts = Math.max(0, Math.min(agentRecoveryAttempts, 5)); }
    public boolean isSemanticWorkerEnabled() { return semanticWorkerEnabled; }
    public void setSemanticWorkerEnabled(boolean semanticWorkerEnabled) { this.semanticWorkerEnabled = semanticWorkerEnabled; }
    public long getSemanticWorkerDelayMs() { return semanticWorkerDelayMs; }
    public void setSemanticWorkerDelayMs(long semanticWorkerDelayMs) { this.semanticWorkerDelayMs = Math.max(1000L, semanticWorkerDelayMs); }
    public int getSemanticWorkerBatchSize() { return semanticWorkerBatchSize; }
    public void setSemanticWorkerBatchSize(int semanticWorkerBatchSize) { this.semanticWorkerBatchSize = Math.max(1, Math.min(semanticWorkerBatchSize, 200)); }
    public int getSemanticWorkerMaxAttempts() { return semanticWorkerMaxAttempts; }
    public void setSemanticWorkerMaxAttempts(int semanticWorkerMaxAttempts) { this.semanticWorkerMaxAttempts = Math.max(1, Math.min(semanticWorkerMaxAttempts, 50)); }
    public int getSemanticWorkerLockTimeoutSeconds() { return semanticWorkerLockTimeoutSeconds; }
    public void setSemanticWorkerLockTimeoutSeconds(int semanticWorkerLockTimeoutSeconds) { this.semanticWorkerLockTimeoutSeconds = Math.max(30, Math.min(semanticWorkerLockTimeoutSeconds, 3600)); }
    public int getSemanticEmbeddingInputChars() { return semanticEmbeddingInputChars; }
    public void setSemanticEmbeddingInputChars(int semanticEmbeddingInputChars) { this.semanticEmbeddingInputChars = Math.max(1000, Math.min(semanticEmbeddingInputChars, 100000)); }
    public int getSemanticEmbeddingEstimatedTokenLimit() { return semanticEmbeddingEstimatedTokenLimit; }
    public void setSemanticEmbeddingEstimatedTokenLimit(int value) { this.semanticEmbeddingEstimatedTokenLimit = Math.max(1000, Math.min(value, 8000)); }
    public int getSemanticContentMaxChars() { return semanticContentMaxChars; }
    public void setSemanticContentMaxChars(int semanticContentMaxChars) { this.semanticContentMaxChars = Math.max(5000, Math.min(semanticContentMaxChars, 500000)); }
    public boolean isSemanticQueryEmbeddingEnabled() { return semanticQueryEmbeddingEnabled; }
    public void setSemanticQueryEmbeddingEnabled(boolean semanticQueryEmbeddingEnabled) { this.semanticQueryEmbeddingEnabled = semanticQueryEmbeddingEnabled; }
    public int getSemanticDefaultSearchLimit() { return semanticDefaultSearchLimit; }
    public void setSemanticDefaultSearchLimit(int semanticDefaultSearchLimit) { this.semanticDefaultSearchLimit = Math.max(1, Math.min(semanticDefaultSearchLimit, 100)); }
    public int getSemanticHardSearchLimit() { return semanticHardSearchLimit; }
    public void setSemanticHardSearchLimit(int semanticHardSearchLimit) { this.semanticHardSearchLimit = Math.max(10, Math.min(semanticHardSearchLimit, 500)); }
    public int getSemanticEmbeddingDimensions() { return semanticEmbeddingDimensions; }
    public void setSemanticEmbeddingDimensions(int semanticEmbeddingDimensions) { this.semanticEmbeddingDimensions = Math.max(1, semanticEmbeddingDimensions); }
    public int getAgentMaxContextChars() { return agentMaxContextChars; }
    public void setAgentMaxContextChars(int value) { this.agentMaxContextChars = Math.max(40000, Math.min(value, 800000)); }
    public String getAgentCapabilityVersion() { return agentCapabilityVersion; }
    public void setAgentCapabilityVersion(String value) { this.agentCapabilityVersion = value == null ? "V4-20260714" : value; }
    public int getAgentRecentMessageLimit() { return agentRecentMessageLimit; }
    public void setAgentRecentMessageLimit(int value) { this.agentRecentMessageLimit = Math.max(2, Math.min(value, 50)); }
    public boolean isAgentTextRecoveryEnabled() { return agentTextRecoveryEnabled; }
    public void setAgentTextRecoveryEnabled(boolean value) { this.agentTextRecoveryEnabled = value; }
    public int getAdaptiveChunkThresholdChars() { return adaptiveChunkThresholdChars; }
    public void setAdaptiveChunkThresholdChars(int adaptiveChunkThresholdChars) { this.adaptiveChunkThresholdChars = adaptiveChunkThresholdChars; }
    public int getAdaptiveChunkChars() { return adaptiveChunkChars; }
    public void setAdaptiveChunkChars(int adaptiveChunkChars) { this.adaptiveChunkChars = adaptiveChunkChars; }
    public int getAdaptiveChunkOverlapChars() { return adaptiveChunkOverlapChars; }
    public void setAdaptiveChunkOverlapChars(int adaptiveChunkOverlapChars) { this.adaptiveChunkOverlapChars = adaptiveChunkOverlapChars; }
    public int getAdaptiveGroupSize() { return adaptiveGroupSize; }
    public void setAdaptiveGroupSize(int adaptiveGroupSize) { this.adaptiveGroupSize = adaptiveGroupSize; }
    public int getAdaptiveMaxLeafChunks() { return adaptiveMaxLeafChunks; }
    public void setAdaptiveMaxLeafChunks(int adaptiveMaxLeafChunks) { this.adaptiveMaxLeafChunks = adaptiveMaxLeafChunks; }
}
