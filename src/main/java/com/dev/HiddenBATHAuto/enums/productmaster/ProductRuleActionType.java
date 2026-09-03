package com.dev.HiddenBATHAuto.enums.productmaster;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ProductRuleActionType {
    SHOW_GROUP("그룹 표시"),
    HIDE_GROUP("그룹 숨김"),
    REQUIRE_GROUP("필수로 변경"),
    OPTIONAL_GROUP("선택으로 변경"),
    ENABLE_VALUE("옵션값 허용"),
    DISABLE_VALUE("옵션값 제외"),
    SET_VALUE("옵션값 자동 선택"),
    SET_NUMBER("숫자값 자동 입력"),
    ADD_NOTICE("안내 메시지 표시");

    private final String labelKr;
}
