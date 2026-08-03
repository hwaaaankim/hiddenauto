package com.dev.HiddenBATHAuto.handler;

import java.util.List;

import lombok.Getter;

@Getter
public class AmountItemMasterSyncValidationException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	private final List<String> details;

	public AmountItemMasterSyncValidationException(List<String> details) {
		super("엑셀 데이터 검증에 실패했습니다.");
		this.details = List.copyOf(details);
	}
}