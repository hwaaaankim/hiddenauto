package com.dev.HiddenBATHAuto.enums.productmaster;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ProductAttributeRole {
    CATEGORY("대분류"),
    SERIES("시리즈"),
    SUBCATEGORY("중분류"),
    DOOR_TYPE("문 타입"),
    COLOR("색상"),
    SIZE("사이즈"),
    HANDLE("손잡이"),
    BASIN("세면대"),
    OPTION("옵션"),
    GENERAL("일반 속성");

    private final String labelKr;
}
