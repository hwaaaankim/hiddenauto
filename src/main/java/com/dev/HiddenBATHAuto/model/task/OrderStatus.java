package com.dev.HiddenBATHAuto.model.task;

public enum OrderStatus {
	REQUESTED,         // 고객이 발주함
    CONFIRMED,         // 관리자 승인
    PRODUCTION_READY,  // 생산팀 할당됨
    PRODUCTION_DONE,   // 생산 완료
    DELIVERY_READY,    // 배송팀 할당됨
    DELIVERY_DONE,     // 배송 완료
    COMPLETED          // 최종 완료
}