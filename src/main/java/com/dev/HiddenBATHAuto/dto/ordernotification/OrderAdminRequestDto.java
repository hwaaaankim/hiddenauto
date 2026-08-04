package com.dev.HiddenBATHAuto.dto.ordernotification;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrderAdminRequestDto {
    /** 현재는 선택 UI가 없으므로 비워도 팀별 임시 사유가 자동 적용됩니다. */
    private String reasonCode;
    private String message;
}
