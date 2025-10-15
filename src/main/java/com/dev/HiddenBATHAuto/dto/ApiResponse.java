package com.dev.HiddenBATHAuto.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiResponse<T> {
	private boolean success;
	private String message;
	private T data;

	public static <T> ApiResponse<T> ok(T data) {
		return ApiResponse.<T>builder().success(true).data(data).build();
	}

	public static <T> ApiResponse<T> fail(String msg, T data) {
		return ApiResponse.<T>builder().success(false).message(msg).data(data).build();
	}
}