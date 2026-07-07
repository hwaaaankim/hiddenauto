package com.dev.HiddenBATHAuto.orderExcelUpload.support;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ResolvedExternalAddress {
    private final boolean resolved;
    private final String source;
    private final String zipCode;
    private final String doName;
    private final String siName;
    private final String guName;
    private final String roadAddress;
    private final String jibunAddress;
}
