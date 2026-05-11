package com.dev.HiddenBATHAuto.dto.notification;

import java.util.Map;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SolapiMessage {

    private String to;

    private String from;

    private String type;

    private String text;

    private SolapiKakaoOptions kakaoOptions;

    private Map<String, String> customFields;
}