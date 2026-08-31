package com.dev.HiddenBATHAuto.dto.asnotification;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AsNotificationPolicyRowDto {
    private String key;
    private String sourceArea;
    private String sourceAreaLabel;
    private String action;
    private String actionLabel;
    private String recipientGroup;
    private String recipientGroupLabel;
    private String description;
    private boolean webEnabled;
    private boolean kakaoEnabled;
    private boolean importantEnabled;
    private boolean webAllowed;
    private boolean kakaoAllowed;
    private boolean importantAllowed;
}
