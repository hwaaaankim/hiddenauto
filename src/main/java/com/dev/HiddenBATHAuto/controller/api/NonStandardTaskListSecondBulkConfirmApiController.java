package com.dev.HiddenBATHAuto.controller.api;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dev.HiddenBATHAuto.service.order.OrderStatusService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/management/api/non-standard-task-list-second")
public class NonStandardTaskListSecondBulkConfirmApiController {

	private final OrderStatusService orderStatusService;

	@PostMapping("/bulk-confirm")
	public ResponseEntity<BulkConfirmResponse> bulkConfirm(@RequestBody BulkConfirmRequest request) {
		try {
			int confirmedCount = orderStatusService.bulkConfirmRequestedOrders(
					request != null ? request.orderIds() : null
			);

			return ResponseEntity.ok(new BulkConfirmResponse(
					true,
					confirmedCount + "건의 오더가 승인완료로 변경되었습니다.",
					confirmedCount
			));
		} catch (IllegalArgumentException | IllegalStateException e) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new BulkConfirmResponse(
					false,
					e.getMessage(),
					0
			));
		} catch (Exception e) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new BulkConfirmResponse(
					false,
					"일괄 컨펌 처리 중 오류가 발생했습니다.",
					0
			));
		}
	}

	public record BulkConfirmRequest(List<Long> orderIds) {
	}

	public record BulkConfirmResponse(boolean success, String message, int confirmedCount) {
	}
}
