package com.dev.HiddenBATHAuto.dto.notification;

import java.util.List;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SolapiSendRequest {

    private List<SolapiMessage> messages;

    private Boolean strict;

    private Boolean allowDuplicates;

    private Boolean showMessageList;
}