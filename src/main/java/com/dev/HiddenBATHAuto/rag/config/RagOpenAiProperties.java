package com.dev.HiddenBATHAuto.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "hiddenbath.rag.openai")
public class RagOpenAiProperties {

    private String apiKey = "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";
    private String baseUrl = "https://api.openai.com";
    private String chatModel = "gpt-5.5";
    private String embeddingModel = "text-embedding-3-small";
    private String reasoningEffort = "medium";
    private int readTimeoutSeconds = 300;
    private int retryCount = 1;

    /** 한 번의 사용자 요청에서 허용할 실제 OpenAI function tool 반복 횟수입니다. */
    private int agentMaxToolTurns = 20;

    /** Responses API 한 차수에서 허용할 최대 출력 토큰 수입니다. */
    private int agentMaxOutputTokens = 16000;

    /** Agent 실패 시 기존 고정 의미분류/저장 흐름으로 내려갈지 여부입니다. 기본은 false입니다. */
    private boolean agentLegacyFallbackEnabled = false;

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
    private int agentMaxAffectedRowsPerStatement = 10000;

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
    public boolean isAgentLegacyFallbackEnabled() { return agentLegacyFallbackEnabled; }
    public void setAgentLegacyFallbackEnabled(boolean agentLegacyFallbackEnabled) { this.agentLegacyFallbackEnabled = agentLegacyFallbackEnabled; }
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
