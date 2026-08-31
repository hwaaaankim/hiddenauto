package com.dev.HiddenBATHAuto.enums.notification;

/** AS 전용 로깅/알림 정책에서 사용하는 안정적인 업무 행동 분류입니다. */
public enum AsNotificationAction {
    REQUEST_CREATED("AS 신청"),
    CUSTOMER_UPDATE("고객 AS 수정"),
    CUSTOMER_CANCEL("고객 AS 취소"),
    DETAIL_UPDATE("주요내용 변경"),
    STATUS_IN_PROGRESS("진행중 변경"),
    STATUS_CANCELED("취소처리"),
    STATUS_CHANGE("상태 변경"),
    HANDLER_CHANGE("담당자 변경"),
    VISIT_SCHEDULE_UPDATE("방문일정 변경"),
    COMPLETE("완료처리"),
    INTERNAL_UPDATE("담당자 메모·첨부 변경"),
    DELETE("AS 삭제");

    private final String label;

    AsNotificationAction(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
