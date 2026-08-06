package com.dev.HiddenBATHAuto.provider.notification;

/**
 * 전화번호 검증 실패를 SOLAPI 네트워크/서버 예외와 구분하기 위한 예외입니다.
 * 원문 전화번호는 메시지에 포함하지 않아 로그에 개인정보가 불필요하게 노출되지 않도록 합니다.
 */
public class InvalidPhoneNumberException extends IllegalArgumentException {

    public InvalidPhoneNumberException(String message) {
        super(message);
    }
}
