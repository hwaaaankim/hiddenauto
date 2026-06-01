package com.dev.HiddenBATHAuto.dto.production;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductionOrderCheckResponse {

    private Long orderId;

    private boolean checked;

    /**
     * UNCHECKED / CHECKED / REVISED_AFTER_CHECK
     */
    private String checkState;

    /**
     * 미확인 / 확인 / 재수정
     */
    private String checkStateLabel;

    private String checkedByUsername;

    private String checkedAtText;

    private String message;
}
