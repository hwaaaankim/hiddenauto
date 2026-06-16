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

    private String apiKey;
    private String baseUrl = "https://api.openai.com";
    private String chatModel = "gpt-5.5";
    private String reasoningEffort = "medium";
    private int readTimeoutSeconds = 180;
}
