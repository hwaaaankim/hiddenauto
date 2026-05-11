package com.dev.HiddenBATHAuto.constant;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.notification.solapi")
public class SolapiProperties {

    private boolean enabled;

    private String baseUrl = "https://api.solapi.com";

    private String apiKey;

    private String apiSecret;

    private String pfId;

    private String defaultFrom;

    private String webhookSecret;

    private boolean strict = true;

    private boolean allowDuplicates = false;

    private int requestTimeoutSeconds = 10;
}