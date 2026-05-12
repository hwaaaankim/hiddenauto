package com.dev.HiddenBATHAuto.enums.process;

public enum ProcessAnswerType {

    /**
     * 여러 개 중 하나 선택
     * - 분기 타입: CHOICE만 허용
     */
    SINGLE_SELECT,

    /**
     * 텍스트 입력
     * - 분기 추가 불가
     */
    TEXT_INPUT,

    /**
     * 숫자 입력
     * - 숫자 필드별 조건 분기 가능
     */
    NUMBER_INPUT,

    /**
     * 파일 등록
     */
    FILE_UPLOAD,

    /**
     * 기존 저장 데이터 호환용
     * 신규 저장에서는 사용하지 않음
     */
    @Deprecated
    MULTI_INPUT
}