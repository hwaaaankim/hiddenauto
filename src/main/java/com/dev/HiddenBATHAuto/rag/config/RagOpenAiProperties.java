package com.dev.HiddenBATHAuto.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "hiddenbath.rag.openai")
public class RagOpenAiProperties {

    private String apiKey;
    private String baseUrl = "https://api.openai.com";
    private String chatModel = "gpt-4o-mini";
    private String embeddingModel = "text-embedding-3-small";
    private String reasoningEffort;
    /**
     * OpenAI 1회 HTTP 호출 제한 시간입니다.
     * 긴 학습은 이 값을 무작정 늘리는 대신 RagLearningChunkedCognitiveService가 여러 작은 호출로 나눕니다.
     */
    private int readTimeoutSeconds = 300;

    /** 타임아웃/일시 네트워크 오류에 대한 짧은 재시도 횟수입니다. */
    private int retryCount = 1;

    /** 이 글자 수 이상이면 직접 GPT 해석 대신 계층형 청크 해석을 우선 사용합니다. */
    private int adaptiveChunkThresholdChars = 1200;

    /** 계층형 해석의 1차 원문 청크 크기입니다. */
    private int adaptiveChunkChars = 2800;

    /** 문맥 유지를 위한 청크 겹침 크기입니다. */
    private int adaptiveChunkOverlapChars = 220;

    /** 하위 packet 몇 개를 하나의 상위 packet으로 묶을지 결정합니다. */
    private int adaptiveGroupSize = 4;

    /** 단일 작업에서 GPT가 직접 해석할 최대 leaf chunk 수입니다. 초과분은 원문 보존 후 별도 추가 작업으로 처리합니다. */
    private int adaptiveMaxLeafChunks = 480;

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getChatModel() {
        return chatModel;
    }

    public void setChatModel(String chatModel) {
        this.chatModel = chatModel;
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public void setEmbeddingModel(String embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public String getReasoningEffort() {
        return reasoningEffort;
    }

    public void setReasoningEffort(String reasoningEffort) {
        this.reasoningEffort = reasoningEffort;
    }

    public int getReadTimeoutSeconds() {
        return readTimeoutSeconds;
    }

    public void setReadTimeoutSeconds(int readTimeoutSeconds) {
        this.readTimeoutSeconds = Math.max(30, readTimeoutSeconds);
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = Math.max(0, retryCount);
    }

    public int getAdaptiveChunkThresholdChars() {
        return adaptiveChunkThresholdChars;
    }

    public void setAdaptiveChunkThresholdChars(int adaptiveChunkThresholdChars) {
        this.adaptiveChunkThresholdChars = adaptiveChunkThresholdChars;
    }

    public int getAdaptiveChunkChars() {
        return adaptiveChunkChars;
    }

    public void setAdaptiveChunkChars(int adaptiveChunkChars) {
        this.adaptiveChunkChars = adaptiveChunkChars;
    }

    public int getAdaptiveChunkOverlapChars() {
        return adaptiveChunkOverlapChars;
    }

    public void setAdaptiveChunkOverlapChars(int adaptiveChunkOverlapChars) {
        this.adaptiveChunkOverlapChars = adaptiveChunkOverlapChars;
    }

    public int getAdaptiveGroupSize() {
        return adaptiveGroupSize;
    }

    public void setAdaptiveGroupSize(int adaptiveGroupSize) {
        this.adaptiveGroupSize = adaptiveGroupSize;
    }

    public int getAdaptiveMaxLeafChunks() {
        return adaptiveMaxLeafChunks;
    }

    public void setAdaptiveMaxLeafChunks(int adaptiveMaxLeafChunks) {
        this.adaptiveMaxLeafChunks = adaptiveMaxLeafChunks;
    }
}
