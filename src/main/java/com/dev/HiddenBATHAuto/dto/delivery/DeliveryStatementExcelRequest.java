package com.dev.HiddenBATHAuto.dto.delivery;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeliveryStatementExcelRequest {

    /**
     * SITE: 현장명세서, PARCEL: 택배명세서
     */
    private String statementType;

    /**
     * 화면 체크박스에서 선택된 오더 ID 목록입니다.
     * 전달 순서를 그대로 엑셀 출력 순서로 사용합니다.
     */
    private List<Long> orderIds;
}
