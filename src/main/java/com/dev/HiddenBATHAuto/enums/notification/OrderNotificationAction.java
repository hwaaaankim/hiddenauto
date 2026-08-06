package com.dev.HiddenBATHAuto.enums.notification;

/**
 * 로깅 알림 관리 화면에서 제어하는 발주 행동 종류입니다.
 *
 * <p>operationCode 자체를 설정 키로 사용하면 화면/API 이름이 바뀔 때마다 정책 데이터가 깨질 수 있으므로,
 * 업무 의미가 같은 operationCode를 이 안정적인 분류로 묶습니다.</p>
 */
public enum OrderNotificationAction {
    REGISTER("발주 등록"),
    UPDATE("발주 내용 수정"),
    STATUS_CHANGE("상태 변경"),
    CANCEL_OR_HIDE("취소·업무 비노출"),
    RESTORE_OR_ROLLBACK("업무 재개·단계 되돌림"),
    DELETE("발주·태스크 삭제"),
    CHECK_CONFIRM("조회 확인·재수정 확인"),
    PRODUCTION_COMPLETE("생산완료"),
    DELIVERY_COMPLETE("배송완료"),
    DISPATCH_COMPLETE("출고완료"),
    ADMIN_REQUEST("관리자요청"),
    DELIVERY_HANDLER_CHANGE("배송 담당자 변경"),
    DELIVERY_METHOD_CHANGE("배송수단 변경");

    private final String label;

    OrderNotificationAction(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
