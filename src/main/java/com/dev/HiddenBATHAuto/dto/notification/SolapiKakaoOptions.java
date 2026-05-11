package com.dev.HiddenBATHAuto.dto.notification;

import java.util.Map;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SolapiKakaoOptions {

    private String pfId;

    private String templateId;

    private Boolean disableSms;

    private Map<String, String> variables;
}