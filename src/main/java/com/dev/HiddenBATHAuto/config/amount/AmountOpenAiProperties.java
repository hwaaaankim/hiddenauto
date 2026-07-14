package com.dev.HiddenBATHAuto.config.amount;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "hiddenbath.rag.openai")
public class AmountOpenAiProperties {

    private String apiKey = "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";
    private String baseUrl = "https://api.openai.com";
    private String chatModel = "gpt-5.5";
    private String reasoningEffort = "medium";
    private int readTimeoutSeconds = 180;
}
