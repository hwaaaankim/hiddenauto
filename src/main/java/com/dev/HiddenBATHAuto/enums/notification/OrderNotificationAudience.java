package com.dev.HiddenBATHAuto.enums.notification;

/**
 * 변경 이벤트가 생성된 뒤 웹 알림을 누구에게 만들지 결정합니다.
 */
public enum OrderNotificationAudience {
    /** 해당 오더와 실제 업무 연관이 있는 관리/생산/출고/배송 사용자 */
    RELATED_USERS,
    /** Task.managedBy 한 명만 */
    MANAGED_BY_ONLY,
    /** 이력만 기록하고 알림은 만들지 않음 */
    NONE
}
