package com.dev.HiddenBATHAuto.model.task;

public enum OrderStatus {
	REQUESTED,         // 고객이 발주함
    CONFIRMED,         // 관리자 승인, 생산팀 배정
    PRODUCTION_DONE,   // 생산 완료, 배송팀 배정
    DELIVERY_DONE,     // 배송 완료
    CENCELED          // 취소
}