package com.dev.HiddenBATHAuto.dto.notification;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SolapiSendApiResult {

    private int httpStatus;

    private String responseBody;

    private String groupId;

    private String messageId;

    private String statusCode;

    private String statusMessage;
}